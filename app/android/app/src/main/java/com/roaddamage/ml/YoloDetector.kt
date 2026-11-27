package com.roaddamage.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.roaddamage.data.Detection
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Raw TFLite YOLO runner for YOLO26s (640x640) model with D40 (pothole), D10 (linear_crack), and D20 (alligator_crack).
 *
 * Implements the single-model architecture specified in ANDROID_SINGLE_MODEL_INTEGRATION_GUIDE.md:
 * - Direct MappedByteBuffer loading to prevent OOM
 * - Hardware acceleration (GpuDelegate with CPU 4-thread fallback)
 * - Letterbox preprocessing with RGB normalization [0..1]
 * - Output shape [1, 7, 8400] decoding
 * - Class-Aware Non-Maximum Suppression (NMS)
 */
class YoloDetector(private val context: Context) : AutoCloseable {
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    var isGpuAccelerated: Boolean = false
        private set

    // YOLO class indices from the trained model:
    // 0: pothole -> D40
    // 1: linear_crack -> D10
    // 2: alligator_crack -> D20
    private val labels = arrayOf("D40", "D10", "D20")
    private val classVietnamese = arrayOf("Ổ gà", "Vết nứt ngang", "Nứt da cá sấu")

    var lastDiagnostic: String = ""
        private set

    fun load() {
        val modelBuffer = loadModelBuffer()
        var loadedInterpreter: Interpreter? = null
        var delegate: GpuDelegate? = null

        // 1. Try GPU acceleration first if supported by the device
        try {
            val compatList = CompatibilityList()
            if (compatList.isDelegateSupportedOnThisDevice) {
                val d = GpuDelegate(compatList.bestOptionsForThisDevice)
                delegate = d
                val gpuOptions = Interpreter.Options().apply {
                    addDelegate(d)
                }
                loadedInterpreter = Interpreter(modelBuffer, gpuOptions)
                gpuDelegate = delegate
                isGpuAccelerated = true
            }
        } catch (_: Throwable) {
            // GPU delegate rejected model (e.g. dynamic-sized tensor) -> fallback cleanly to CPU
            delegate?.close()
            gpuDelegate = null
            loadedInterpreter = null
            isGpuAccelerated = false
        }

        // 2. High-performance fallback: 4 CPU threads + XNNPACK
        if (loadedInterpreter == null) {
            val cpuOptions = Interpreter.Options().apply {
                setNumThreads(4)
                setUseXNNPACK(true)
            }
            loadedInterpreter = Interpreter(modelBuffer, cpuOptions)
            isGpuAccelerated = false
        }

        interpreter = loadedInterpreter
    }

    private fun loadModelBuffer(): ByteBuffer {
        val targetNames = listOf("yolo26s_rdd_640.tflite", "road_damage.tflite").distinct()
        for (name in targetNames) {
            try {
                val fileDescriptor = context.assets.openFd(name)
                val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
                return inputStream.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    fileDescriptor.startOffset,
                    fileDescriptor.declaredLength
                )
            } catch (_: Exception) {
                try {
                    val bytes = context.assets.open(name).use { it.readBytes() }
                    val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
                    buffer.put(bytes)
                    buffer.rewind()
                    return buffer
                } catch (_: Exception) {
                    // Continue to next model name
                }
            }
        }
        error("Could not find road damage model in assets: $targetNames")
    }

    fun detect(source: Bitmap, confidenceThreshold: Float = 0.28f): List<Detection> {
        val runner = interpreter ?: error("Call load() before detect()")
        val startTime = System.currentTimeMillis()

        val inputTensor = runner.getInputTensor(0)
        val inputShape = inputTensor.shape()
        val channelsLast = inputShape.size == 4 && inputShape[3] == 3
        val channelsFirst = inputShape.size == 4 && inputShape[1] == 3
        require(channelsLast || channelsFirst) {
            "Expected RGB input in NHWC [1,H,W,3] or NCHW [1,3,H,W], got ${inputShape.contentToString()}"
        }
        require(inputTensor.dataType() == org.tensorflow.lite.DataType.FLOAT32) {
            "Expected a float32 model input, got ${inputTensor.dataType()}"
        }

        val inputHeight = if (channelsFirst) inputShape[2] else inputShape[1]
        val inputWidth = if (channelsFirst) inputShape[3] else inputShape[2]
        require(inputHeight == inputWidth) { "Only square YOLO inputs are supported, got ${inputShape.contentToString()}" }
        val inputSize = inputWidth

        // 1. Letterbox preprocessing (preserves aspect ratio)
        val lbInfo = LetterboxUtil.applyLetterbox(source, inputSize)

        // 2. Format ByteBuffer (Float32, [0.0 .. 1.0])
        val input = ByteBuffer.allocateDirect(1 * 3 * inputSize * inputSize * 4)
            .order(ByteOrder.nativeOrder())
        val pixels = IntArray(inputSize * inputSize)
        lbInfo.letterboxedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        if (channelsFirst) {
            // NCHW [1, 3, 640, 640]
            for (pixel in pixels) input.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            for (pixel in pixels) input.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            for (pixel in pixels) input.putFloat((pixel and 0xFF) / 255.0f)
        } else {
            // NHWC [1, 640, 640, 3]
            for (pixel in pixels) {
                input.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
                input.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
                input.putFloat((pixel and 0xFF) / 255.0f)
            }
        }
        input.rewind()

        // 3. Output shape inference
        val shape = runner.getOutputTensor(0).shape()
        require(shape.size == 3 && shape[0] == 1) { "Expected one YOLO output tensor, got ${shape.contentToString()}" }

        val output = Array(1) { Array(shape[1]) { FloatArray(shape[2]) } }
        runner.run(input, output)

        val inferenceTimeMs = System.currentTimeMillis() - startTime

        val outputChannelsFirst = shape[1] == 4 + labels.size || shape[1] == 5 + labels.size
        val features = if (outputChannelsFirst) shape[1] else shape[2]
        require(features == 4 + labels.size || features == 5 + labels.size) {
            "Expected 7 or 8 YOLO features, got output ${shape.contentToString()}"
        }
        val boxes = if (outputChannelsFirst) shape[2] else shape[1]
        val classOffset = if (features == 5 + labels.size) 5 else 4

        val candidates = mutableListOf<Detection>()
        var highestScore = 0f
        var highestLabel = labels.first()

        val origW = source.width.toFloat()
        val origH = source.height.toFloat()

        for (i in 0 until boxes) {
            fun value(feature: Int) = if (outputChannelsFirst) output[0][feature][i] else output[0][i][feature]
            val objectness = if (classOffset == 5) value(4) else 1f
            var clazz = 0
            var best = 0f

            for (c in labels.indices) {
                val score = value(classOffset + c) * objectness
                if (score > best) {
                    best = score
                    clazz = c
                }
            }

            if (best > highestScore) {
                highestScore = best
                highestLabel = labels[clazz]
            }

            if (best < confidenceThreshold) continue

            val cx = value(0)
            val cy = value(1)
            val w = value(2)
            val h = value(3)
            if (w <= 0f || h <= 0f) continue

            val boxX1 = cx - w / 2f
            val boxY1 = cy - h / 2f
            val boxX2 = cx + w / 2f
            val boxY2 = cy + h / 2f

            // De-scale coordinates back to original image
            val origX1 = maxOf(0f, minOf(origW, (boxX1 - lbInfo.padX) / lbInfo.scale))
            val origY1 = maxOf(0f, minOf(origH, (boxY1 - lbInfo.padY) / lbInfo.scale))
            val origX2 = maxOf(0f, minOf(origW, (boxX2 - lbInfo.padX) / lbInfo.scale))
            val origY2 = maxOf(0f, minOf(origH, (boxY2 - lbInfo.padY) / lbInfo.scale))

            candidates += Detection(
                label = labels[clazz],
                confidence = best,
                left = origX1,
                top = origY1,
                right = origX2,
                bottom = origY2
            )
        }

        val delegateName = if (isGpuAccelerated) "GPU" else "CPU (4 threads)"
        lastDiagnostic = "Inference: ${inferenceTimeMs}ms ($delegateName), Input: ${inputShape.contentToString()}, Output: ${shape.contentToString()}; Top score: $highestLabel ${(highestScore * 100).toInt()}%."

        return classAwareNms(candidates)
    }

    /**
     * Class-Aware Non-Maximum Suppression (IoU >= 0.45).
     * Prevents overlapping distinct damage types (e.g. pothole + crack) from suppressing each other.
     */
    private fun classAwareNms(boxes: List<Detection>, iouThresh: Float = 0.45f): List<Detection> {
        val selected = mutableListOf<Detection>()
        val groupedByLabel = boxes.groupBy { it.label }

        for ((_, group) in groupedByLabel) {
            val sorted = group.sortedByDescending { it.confidence }.toMutableList()
            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                selected.add(best)

                val iterator = sorted.iterator()
                while (iterator.hasNext()) {
                    val current = iterator.next()
                    if (iou(best, current) >= iouThresh) {
                        iterator.remove()
                    }
                }
            }
        }
        return selected
    }

    private fun iou(a: Detection, b: Detection): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
    }

    companion object {
        /**
         * Draws colored bounding boxes and confidence tags on a bitmap for all detected road damages.
         * Colors match the RDD2022 guidelines:
         * - D40 (Pothole): Bright Green
         * - D10 (Linear crack): Vibrant Orange
         * - D20 (Alligator crack): Vibrant Red
         */
        fun drawDetectionsOnBitmap(source: Bitmap, detections: List<Detection>): Bitmap {
            if (detections.isEmpty()) return source
            val output = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
            val canvas = Canvas(output)

            val minDim = minOf(source.width, source.height).toFloat()
            val strokeWidth = maxOf(4f, minDim / 140f)
            val textSize = maxOf(24f, minDim / 28f)

            val boxPaint = Paint().apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                isAntiAlias = true
            }

            val textBgPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            val textPaint = Paint().apply {
                color = Color.WHITE
                this.textSize = textSize
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            for (d in detections) {
                val color = when (d.label) {
                    "D40" -> Color.rgb(0, 230, 118)   // Green: Ổ gà (Pothole)
                    "D10" -> Color.rgb(255, 145, 0)   // Orange: Vết nứt ngang (Linear crack)
                    "D20" -> Color.rgb(255, 23, 68)   // Red: Nứt da cá sấu (Alligator crack)
                    else -> Color.CYAN
                }

                boxPaint.color = color
                textBgPaint.color = color

                // 1. Draw Bounding Box
                canvas.drawRect(d.left, d.top, d.right, d.bottom, boxPaint)

                // 2. Draw Label Tag
                val labelName = when (d.label) {
                    "D40" -> "Ổ gà"
                    "D10" -> "Vết nứt ngang"
                    "D20" -> "Nứt da cá sấu"
                    else -> d.label
                }
                val tagText = "$labelName ${(d.confidence * 100).toInt()}%"
                val textWidth = textPaint.measureText(tagText)
                val textHeight = textSize

                val bgTop = if (d.top - textHeight - 16f >= 0f) {
                    d.top - textHeight - 16f
                } else {
                    d.top
                }
                val bgBottom = bgTop + textHeight + 16f
                val bgRight = minOf(source.width.toFloat(), d.left + textWidth + 18f)

                canvas.drawRect(d.left, bgTop, bgRight, bgBottom, textBgPaint)
                canvas.drawText(tagText, d.left + 8f, bgBottom - 8f, textPaint)
            }

            return output
        }
    }
}
