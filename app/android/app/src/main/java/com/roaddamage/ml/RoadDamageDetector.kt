package com.roaddamage.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Data class representing a detected road damage according to ANDROID_SINGLE_MODEL_INTEGRATION_GUIDE.md.
 */
data class RoadDamage(
    val classId: Int,             // 0: pothole (D40), 1: linear_crack (D10), 2: alligator_crack (D20)
    val className: String,         // pothole, linear_crack, alligator_crack
    val classVietnamese: String,   // Ổ gà, Vết nứt ngang, Nứt da cá sấu
    val confidence: Float,         // Confidence score [0.0 .. 1.0]
    val pixelBox: RectF,           // Coordinates on original photo (x1, y1, x2, y2)
    val normalizedBox: RectF,      // Normalized [0.0 .. 1.0] coordinates for Cloud sync
    val severity: String           // Severity: LOW, MEDIUM, HIGH
)

data class LetterboxInfo(
    val letterboxedBitmap: Bitmap,
    val scale: Float,
    val padX: Float,
    val padY: Float
)

object LetterboxUtil {
    fun applyLetterbox(sourceBitmap: Bitmap, targetSize: Int = 640): LetterboxInfo {
        val srcW = sourceBitmap.width.toFloat()
        val srcH = sourceBitmap.height.toFloat()

        val scale = minOf(targetSize / srcW, targetSize / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale

        val padX = (targetSize - scaledW) / 2f
        val padY = (targetSize - scaledH) / 2f

        val outputBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)

        // YOLO standard gray padding RGB (114, 114, 114)
        canvas.drawColor(Color.rgb(114, 114, 114))

        val dstRect = RectF(padX, padY, padX + scaledW, padY + scaledH)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(sourceBitmap, null, dstRect, paint)

        return LetterboxInfo(
            letterboxedBitmap = outputBitmap,
            scale = scale,
            padX = padX,
            padY = padY
        )
    }
}

object TensorUtil {
    /**
     * Converts a [640, 640] Bitmap to a Float32 ByteBuffer formatted as NHWC [1, 640, 640, 3].
     */
    fun bitmapToByteBuffer(bitmap: Bitmap, inputSize: Int = 640): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        byteBuffer.rewind()

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in intValues) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        byteBuffer.rewind()
        return byteBuffer
    }
}

/**
 * Production-ready Road Damage Detector using Single-Model YOLO26s (640x640) TFLite.
 */
class RoadDamageDetector(
    context: Context,
    modelFileName: String = "yolo26s_rdd_640.tflite",
    private val confThreshold: Float = 0.28f,
    private val iouThreshold: Float = 0.45f
) : AutoCloseable {

    private var interpreter: Interpreter
    private var gpuDelegate: GpuDelegate? = null
    var isGpuAccelerated: Boolean = false
        private set
    var lastInferenceTimeMs: Long = 0L
        private set

    val classNames = arrayOf("pothole", "linear_crack", "alligator_crack")
    val classVietnamese = arrayOf("Ổ gà", "Vết nứt ngang", "Nứt da cá sấu")
    val damageCodes = arrayOf("D40", "D10", "D20")

    init {
        val modelBuffer = loadModelBuffer(context, modelFileName)
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
            // GPU delegate rejected model -> fallback cleanly to CPU
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

    private fun loadModelBuffer(context: Context, fileName: String): ByteBuffer {
        // Try requested file first, then fallback to alternate name if needed
        val targetNames = listOf(fileName, "yolo26s_rdd_640.tflite", "road_damage.tflite").distinct()
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
                    // Try next file name
                }
            }
        }
        error("Could not load model asset from names: $targetNames")
    }

    fun detectDamage(photoBitmap: Bitmap, threshold: Float = confThreshold): List<RoadDamage> {
        val startTime = System.currentTimeMillis()
        val origW = photoBitmap.width.toFloat()
        val origH = photoBitmap.height.toFloat()

        // 1. Letterbox 640x640
        val lbInfo = LetterboxUtil.applyLetterbox(photoBitmap, 640)

        // 2. Convert to Float32 ByteBuffer
        val inputBuffer = TensorUtil.bitmapToByteBuffer(lbInfo.letterboxedBitmap, 640)

        // 3. Prepare output tensor: [1, 7, 8400]
        val outputShape = interpreter.getOutputTensor(0).shape()
        val isChannelsFirst = outputShape.size == 3 && outputShape[1] == 7

        val candidates = mutableListOf<RoadDamage>()

        if (isChannelsFirst) {
            val outputArray = Array(1) { Array(7) { FloatArray(8400) } }
            interpreter.run(inputBuffer, outputArray)
            val matrix = outputArray[0]

            for (i in 0 until 8400) {
                val cx = matrix[0][i]
                val cy = matrix[1][i]
                val w = matrix[2][i]
                val h = matrix[3][i]

                val scorePothole = matrix[4][i]
                val scoreLinear = matrix[5][i]
                val scoreAlligator = matrix[6][i]

                var maxScore = scorePothole
                var maxClassId = 0

                if (scoreLinear > maxScore) {
                    maxScore = scoreLinear
                    maxClassId = 1
                }
                if (scoreAlligator > maxScore) {
                    maxScore = scoreAlligator
                    maxClassId = 2
                }

                if (maxScore >= threshold && w > 0f && h > 0f) {
                    val boxX1_640 = cx - w / 2f
                    val boxY1_640 = cy - h / 2f
                    val boxX2_640 = cx + w / 2f
                    val boxY2_640 = cy + h / 2f

                    val origX1 = maxOf(0f, minOf(origW, (boxX1_640 - lbInfo.padX) / lbInfo.scale))
                    val origY1 = maxOf(0f, minOf(origH, (boxY1_640 - lbInfo.padY) / lbInfo.scale))
                    val origX2 = maxOf(0f, minOf(origW, (boxX2_640 - lbInfo.padX) / lbInfo.scale))
                    val origY2 = maxOf(0f, minOf(origH, (boxY2_640 - lbInfo.padY) / lbInfo.scale))

                    val normX1 = origX1 / origW
                    val normY1 = origY1 / origH
                    val normX2 = origX2 / origW
                    val normY2 = origY2 / origH

                    val areaRatio = ((origX2 - origX1) * (origY2 - origY1)) / (origW * origH)
                    val severity = when {
                        maxClassId == 0 && areaRatio > 0.03f -> "HIGH"
                        maxClassId == 2 && areaRatio > 0.05f -> "HIGH"
                        areaRatio > 0.015f -> "MEDIUM"
                        else -> "LOW"
                    }

                    candidates.add(
                        RoadDamage(
                            classId = maxClassId,
                            className = classNames[maxClassId],
                            classVietnamese = classVietnamese[maxClassId],
                            confidence = maxScore,
                            pixelBox = RectF(origX1, origY1, origX2, origY2),
                            normalizedBox = RectF(normX1, normY1, normX2, normY2),
                            severity = severity
                        )
                    )
                }
            }
        } else {
            // In case output tensor is [1, 8400, 7]
            val outputArray = Array(1) { Array(8400) { FloatArray(7) } }
            interpreter.run(inputBuffer, outputArray)
            val matrix = outputArray[0]

            for (i in 0 until 8400) {
                val row = matrix[i]
                val cx = row[0]
                val cy = row[1]
                val w = row[2]
                val h = row[3]

                var maxScore = row[4]
                var maxClassId = 0
                if (row[5] > maxScore) { maxScore = row[5]; maxClassId = 1 }
                if (row[6] > maxScore) { maxScore = row[6]; maxClassId = 2 }

                if (maxScore >= threshold && w > 0f && h > 0f) {
                    val boxX1_640 = cx - w / 2f
                    val boxY1_640 = cy - h / 2f
                    val boxX2_640 = cx + w / 2f
                    val boxY2_640 = cy + h / 2f

                    val origX1 = maxOf(0f, minOf(origW, (boxX1_640 - lbInfo.padX) / lbInfo.scale))
                    val origY1 = maxOf(0f, minOf(origH, (boxY1_640 - lbInfo.padY) / lbInfo.scale))
                    val origX2 = maxOf(0f, minOf(origW, (boxX2_640 - lbInfo.padX) / lbInfo.scale))
                    val origY2 = maxOf(0f, minOf(origH, (boxY2_640 - lbInfo.padY) / lbInfo.scale))

                    val normX1 = origX1 / origW
                    val normY1 = origY1 / origH
                    val normX2 = origX2 / origW
                    val normY2 = origY2 / origH

                    val areaRatio = ((origX2 - origX1) * (origY2 - origY1)) / (origW * origH)
                    val severity = when {
                        maxClassId == 0 && areaRatio > 0.03f -> "HIGH"
                        maxClassId == 2 && areaRatio > 0.05f -> "HIGH"
                        areaRatio > 0.015f -> "MEDIUM"
                        else -> "LOW"
                    }

                    candidates.add(
                        RoadDamage(
                            classId = maxClassId,
                            className = classNames[maxClassId],
                            classVietnamese = classVietnamese[maxClassId],
                            confidence = maxScore,
                            pixelBox = RectF(origX1, origY1, origX2, origY2),
                            normalizedBox = RectF(normX1, normY1, normX2, normY2),
                            severity = severity
                        )
                    )
                }
            }
        }

        lastInferenceTimeMs = System.currentTimeMillis() - startTime
        return applyNMS(candidates, iouThreshold)
    }

    private fun applyNMS(boxes: List<RoadDamage>, iouThresh: Float): List<RoadDamage> {
        val selected = mutableListOf<RoadDamage>()
        val groupedByClass = boxes.groupBy { it.classId }

        for ((_, classBoxes) in groupedByClass) {
            val sorted = classBoxes.sortedByDescending { it.confidence }.toMutableList()

            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                selected.add(best)

                val iterator = sorted.iterator()
                while (iterator.hasNext()) {
                    val current = iterator.next()
                    if (calculateIoU(best.pixelBox, current.pixelBox) >= iouThresh) {
                        iterator.remove()
                    }
                }
            }
        }
        return selected
    }

    private fun calculateIoU(a: RectF, b: RectF): Float {
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
        interpreter.close()
        gpuDelegate?.close()
        gpuDelegate = null
    }
}
