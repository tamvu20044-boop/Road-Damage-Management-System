package com.roaddamage.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Satellite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Delete
import android.location.Geocoder
import java.util.Locale
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import android.graphics.Matrix
import android.media.ExifInterface
import com.roaddamage.BuildConfig
import com.roaddamage.data.AppSettings
import com.roaddamage.data.Detection
import com.roaddamage.data.LocalReport
import com.roaddamage.data.ReportDatabase
import com.roaddamage.data.SettingsStore
import com.roaddamage.data.SyncState
import com.roaddamage.location.LocationTracker
import com.roaddamage.ml.YoloDetector
import com.roaddamage.network.ApiFactory
import com.roaddamage.network.AuthRequest
import com.roaddamage.network.ServerReport
import com.roaddamage.work.ReportSyncWorker
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private enum class Destination { SCAN, MAP, REPORTS, MANAGER, SETTINGS }

private val RoadDamageColors = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F2CF),
    secondary = Color(0xFF386A5A),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFDCE5DE)
)

private fun destinationLabel(destination: Destination) = when (destination) {
    Destination.SCAN -> "Quét"
    Destination.MAP -> "Bản đồ"
    Destination.REPORTS -> "Báo cáo"
    Destination.MANAGER -> "Quản lý"
    Destination.SETTINGS -> "Cài đặt"
}

private data class MapReport(
    val id: String,
    val damageType: String,
    val status: String,
    val sender: String,
    val createdAt: String,
    val latitude: Double,
    val longitude: Double,
    val imageUrl: String? = null,
    val localImage: String? = null
)

private fun statusLabel(status: String) = when (status) {
    "moi_phat_hien" -> "Chưa sửa"
    "dang_xu_ly" -> "Đang sửa"
    "da_sua" -> "Đã sửa"
    else -> status
}

private fun parseDamageTypes(typeStr: String): List<String> {
    return typeStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}

private fun singleDamageTypeName(code: String): String {
    val trimmed = code.trim()
    return when {
        trimmed == "D10" || trimmed.startsWith("D10") -> "Vết nứt ngang"
        trimmed == "D20" || trimmed.startsWith("D20") -> "Nứt da cá sấu"
        trimmed == "D40" || trimmed.startsWith("D40") -> "Ổ gà"
        trimmed == "pothole" -> "Ổ gà"
        trimmed == "linear_crack" -> "Vết nứt ngang"
        trimmed == "alligator_crack" -> "Nứt da cá sấu"
        else -> trimmed
            .removePrefix("D10 - ")
            .removePrefix("D20 - ")
            .removePrefix("D40 - ")
    }
}

private fun damageTypeLabel(type: String): String {
    val types = parseDamageTypes(type)
    if (types.isEmpty()) return type
    return types.joinToString(", ") { singleDamageTypeName(it) }
}

private fun statusColor(status: String) = when (status) {
    "dang_xu_ly" -> "#2E7D32"
    "da_sua" -> "#757575"
    else -> "#D32F2F"
}

/** Makes reports created before MINIO_PUBLIC_URL was corrected visible on a physical phone too. */
private fun phoneReachableImageUrl(url: String): String {
    val apiHost = Uri.parse(BuildConfig.API_BASE_URL).host ?: return url
    return url.replace("://localhost:", "://$apiHost:").replace("://127.0.0.1:", "://$apiHost:")
}

private fun formatDateTime(isoString: String): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(isoString.take(19)) ?: return isoString.take(16).replace("T", " ")
        val outSdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
        outSdf.timeZone = java.util.TimeZone.getDefault()
        outSdf.format(date)
    } catch (e: Exception) {
        isoString.take(16).replace("T", " ")
    }
}

@Composable
private fun <T> AppDropdown(
    label: String,
    selectedValue: T,
    items: List<Pair<T, String>>,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val displayLabel = items.firstOrNull { it.first == selectedValue }?.second ?: selectedValue?.toString() ?: ""

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(2.dp))
        }
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        displayLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                items.forEach { (itemVal, itemLabel) ->
                    DropdownMenuItem(
                        text = { Text(itemLabel) },
                        onClick = {
                            expanded = false
                            onValueChange(itemVal)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RoadDamageApp() {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    var token by remember { mutableStateOf(settings.token()) }
    var role by remember { mutableStateOf(settings.role()) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var targetMapPoint by remember { mutableStateOf<Point?>(null) }
    var targetReportId by remember { mutableStateOf<String?>(null) }

    if (token == null) {
        AuthScreen { accessToken, userEmail, userRole ->
            settings.saveSession(accessToken, userEmail, userRole)
            token = accessToken
            role = userRole
        }
        return
    }

    if (pendingPhoto != null) {
        ReportDetailsScreen(photo = pendingPhoto!!, settings = settings, onDone = { pendingPhoto = null }, onRetake = { pendingPhoto?.delete(); pendingPhoto = null })
        return
    }

    var destination by remember { mutableStateOf(Destination.SCAN) }
    MaterialTheme(colorScheme = RoadDamageColors) {
        Scaffold(
            bottomBar = {
                if (destination != Destination.SCAN) NavigationBar {
                    Destination.entries.filter { it != Destination.MANAGER || role == "manager" }.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = {
                                if (item != Destination.MAP) {
                                    targetMapPoint = null
                                    targetReportId = null
                                }
                                destination = item
                            },
                            icon = {
                                val icon = when (item) {
                                    Destination.SCAN -> Icons.Default.CameraAlt
                                    Destination.MAP -> Icons.Default.Map
                                    Destination.REPORTS -> Icons.Default.Assignment
                                    Destination.MANAGER -> Icons.Default.AdminPanelSettings
                                    Destination.SETTINGS -> Icons.Default.Settings
                                }
                                Icon(icon, contentDescription = destinationLabel(item))
                            },
                            label = { Text(destinationLabel(item)) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (destination) {
                    Destination.SCAN -> CameraCaptureScreen(
                        onPhotoCaptured = { pendingPhoto = it },
                        onOpenMap = {
                            targetMapPoint = null
                            targetReportId = null
                            destination = Destination.MAP
                        },
                        onOpenReports = { destination = Destination.REPORTS }
                    )
                    Destination.MAP -> MapScreen(
                        token = token!!,
                        role = role,
                        targetPoint = targetMapPoint,
                        targetReportId = targetReportId,
                        onClearTarget = {
                            targetMapPoint = null
                            targetReportId = null
                        }
                    )
                    Destination.REPORTS -> ReportsScreen(token!!, role)
                    Destination.MANAGER -> ManagerScreen(
                        token = token!!,
                        onNavigateToMap = { lat, lng, reportId ->
                            targetMapPoint = Point.fromLngLat(lng, lat)
                            targetReportId = reportId
                            destination = Destination.MAP
                        }
                    )
                    Destination.SETTINGS -> SettingsScreen(settings) {
                        settings.clearSession()
                        token = null
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthScreen(onAuthenticated: (String, String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var registerMode by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    MaterialTheme(colorScheme = RoadDamageColors) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
            Text(if (registerMode) "Tạo tài khoản" else "Road Report", style = MaterialTheme.typography.headlineMedium)
            Text("Máy chủ: ${BuildConfig.API_BASE_URL}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu (tối thiểu 8 ký tự)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(16.dp))
            Button(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = {
                if (email.isBlank() || password.length < 8) {
                    error = "Vui lòng nhập email hợp lệ và mật khẩu ít nhất 8 ký tự."
                    return@Button
                }
                busy = true
                error = null
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            val api = ApiFactory.create(BuildConfig.API_BASE_URL)
                            if (registerMode) api.register(AuthRequest(email.trim(), password)) else api.login(AuthRequest(email.trim(), password))
                        }
                    }
                    busy = false
                    val response = result.getOrNull()
                    if (response?.isSuccessful == true && response.body() != null) {
                        val session = response.body()!!
                        onAuthenticated(session.accessToken, session.user.email, session.user.role)
                    } else {
                        error = if (result.isFailure) "Không thể kết nối máy chủ ${BuildConfig.API_BASE_URL}. Vui lòng kiểm tra Wi-Fi." else "Đăng nhập thất bại. Kiểm tra lại email và mật khẩu."
                    }
                }
            }) { Text(if (busy) "Vui lòng chờ…" else if (registerMode) "Đăng ký" else "Đăng nhập") }
            TextButton(onClick = { registerMode = !registerMode; error = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (registerMode) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký ngay")
            }
        }
    }
}

private fun processAndCompressCapturedImage(file: File): File {
    try {
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        val originalBitmap = BitmapFactory.decodeFile(file.absolutePath)
        if (originalBitmap != null) {
            val orientedBitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                val rotated = android.graphics.Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                if (rotated != originalBitmap) originalBitmap.recycle()
                rotated
            } else {
                originalBitmap
            }
            java.io.FileOutputStream(file).use { out ->
                orientedBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
            }
            orientedBitmap.recycle()
        }
    } catch (e: Exception) {
        android.util.Log.e("RoadDamage", "Could not process image orientation or compression", e)
    }
    return file
}

@Composable
private fun CameraCaptureScreen(onPhotoCaptured: (File) -> Unit, onOpenMap: () -> Unit, onOpenReports: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var status by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCameraPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) status = "Cần cấp quyền camera để chụp ảnh."
    }
    LaunchedEffect(Unit) { if (!hasCameraPermission) permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasCameraPermission) CameraPreview(lifecycle, imageCapture, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Cấp quyền camera") } }
        Row(Modifier.align(Alignment.TopCenter).padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Road Report", color = Color.White, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = onOpenMap) { Text("Bản đồ", color = Color.White) }
            TextButton(onClick = onOpenReports) { Text("Báo cáo", color = Color.White) }
        }
        status?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)) }
        Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Hướng camera về phía hư hại mặt đường", color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
            Button(enabled = hasCameraPermission, shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(78.dp), onClick = {
                status = "Đang chụp ảnh…"
                val imageFile = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
                imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(imageFile).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exception: ImageCaptureException) { status = "Không thể chụp ảnh: ${exception.message}" }
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) = onPhotoCaptured(processAndCompressCapturedImage(imageFile))
                })
            }) { Box(Modifier.size(58.dp).clip(CircleShape).background(Color.White)) }
        }
    }
}

@Composable
private fun ReportDetailsScreen(photo: File, settings: SettingsStore, onDone: () -> Unit, onRetake: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { ReportDatabase.getInstance(context).reports() }
    var detections by remember { mutableStateOf<List<Detection>>(emptyList()) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var detectionMessage by remember { mutableStateOf("Đang phân tích hình ảnh…") }
    var modelDiagnostic by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val originalBitmap = remember(photo) { BitmapFactory.decodeFile(photo.absolutePath) }
    var displayBitmap by remember { mutableStateOf(originalBitmap) }
    val confidenceThreshold = remember { settings.get().confidenceThreshold }

    LaunchedEffect(photo) {
        val analysis = withContext(Dispatchers.IO) {
            runCatching {
                YoloDetector(context).use { detector ->
                    detector.load()
                    val detectedList = detector.detect(originalBitmap, confidenceThreshold)
                    val annotated = if (detectedList.isNotEmpty()) {
                        YoloDetector.drawDetectionsOnBitmap(originalBitmap, detectedList)
                    } else {
                        originalBitmap
                    }
                    if (annotated != originalBitmap) {
                        try {
                            java.io.FileOutputStream(photo).use { out ->
                                annotated.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("RoadDamage", "Could not save annotated image", e)
                        }
                    }
                    Triple(detectedList, detector.lastDiagnostic, annotated)
                }
            }
        }
        val result = analysis.getOrNull()
        modelError = analysis.exceptionOrNull()?.let { failure -> failure.message ?: failure.javaClass.simpleName }
        val detectedList = result?.first.orEmpty()
        detections = detectedList
        modelDiagnostic = result?.second
        if (result?.third != null) {
            displayBitmap = result.third
        }

        detectionMessage = if (detectedList.isNotEmpty()) {
            "AI đã phát hiện ${detectedList.size} vị trí hư hại:"
        } else {
            "Không phát hiện hư hại mặt đường. Hãy chụp lại gần hơn và rõ bề mặt đường."
        }

        if (modelError != null) {
            detectionMessage = "Mô hình không thể phân tích ảnh: $modelError."
        } else if (result == null) {
            detectionMessage = "Mô hình đã chạy nhưng không phát hiện hư hại nào đạt ngưỡng ${(confidenceThreshold * 100).toInt()}%. Thử chụp gần hơn và đủ sáng."
        }
    }
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Xác nhận báo cáo", style = MaterialTheme.typography.headlineSmall)
        Image(
            bitmap = displayBitmap.asImageBitmap(),
            contentDescription = "Ảnh đường đã khoanh vùng hư hại",
            modifier = Modifier.fillMaxWidth().height(280.dp).padding(top = 12.dp)
        )
        Text(detectionMessage, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)

        if (detections.isEmpty() && modelError == null) {
            modelDiagnostic?.let { Text(it, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall) }
        }

        detections.forEach { result ->
            val labelText = singleDamageTypeName(result.label)
            Text("• $labelText: ${(result.confidence * 100).toInt()}%", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.titleMedium)
        }

        if (detections.isNotEmpty()) {
            modelDiagnostic?.let { Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall) }
        }

        val distinctDamageCodes = detections.map { it.label }.distinct()
        val combinedTypes = distinctDamageCodes.joinToString(", ")

        if (distinctDamageCodes.size > 1) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    "⚡ Ảnh này phát hiện ${distinctDamageCodes.size} loại hư hại. Tất cả được lưu chung vào 1 điểm báo cáo duy nhất với các tag: ${distinctDamageCodes.joinToString(", ") { singleDamageTypeName(it) }}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Button(enabled = detections.isNotEmpty(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp), onClick = {
            status = "Đang lấy tọa độ GPS…"
            LocationTracker(context).currentLocation { lat, lng ->
                if (lat == null || lng == null) { status = "Không lấy được vị trí GPS. Vui lòng bật vị trí và thử lại ngoài trời."; return@currentLocation }
                val boxesJson = buildString {
                    append("[")
                    detections.forEachIndexed { idx, d ->
                        if (idx > 0) append(",")
                        append("{\"label\":\"${d.label}\",\"confidence\":${d.confidence},\"left\":${d.left},\"top\":${d.top},\"right\":${d.right},\"bottom\":${d.bottom}}")
                    }
                    append("]")
                }
                val bestConf = detections.maxOf { it.confidence }
                status = "Đang lưu và tải lên máy chủ…"
                scope.launch {
                    val newReport = LocalReport(
                        UUID.randomUUID().toString(),
                        combinedTypes,
                        bestConf,
                        lat,
                        lng,
                        null,
                        photo.absolutePath,
                        boxesJson,
                        System.currentTimeMillis(),
                        SyncState.PENDING_SYNC
                    )
                    withContext(Dispatchers.IO) { dao.insert(newReport) }
                    val ok = com.roaddamage.work.ReportSyncHelper.syncReport(context, newReport)
                    if (ok) {
                        status = "Tải lên thành công! Đã lưu điểm báo cáo (${damageTypeLabel(combinedTypes)}) lên máy chủ."
                    } else {
                        status = "Đã lưu vào máy. Đang xếp hàng đồng bộ ngầm khi có mạng."
                        com.roaddamage.work.ReportSyncWorker.enqueue(context, settings.get().wifiOnly)
                    }
                }
            }
        }) {
            Text("Lưu báo cáo")
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onRetake) { Text("Chụp lại ảnh") }
        status?.let { Text(it, Modifier.padding(top = 12.dp)) }
        if (status?.contains("thành công") == true || status?.contains("Đã lưu") == true) {
            TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Hoàn tất") }
        }
    }
}

@Composable
private fun ScanScreen(settings: SettingsStore) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val dao = remember { ReportDatabase.getInstance(context).reports() }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var hasPermissions by remember { mutableStateOf(hasCameraAndLocationPermission(context)) }
    var damageType by remember { mutableStateOf("D40") }
    var confidence by remember { mutableFloatStateOf(.75f) }
    var status by remember { mutableStateOf("Chọn loại hư hại, sau đó chụp ảnh để gửi báo cáo.") }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermissions = hasCameraAndLocationPermission(context)
        if (!hasPermissions) status = "Cần cấp quyền camera và vị trí."
    }

    LaunchedEffect(Unit) { if (!hasPermissions) permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    Column(Modifier.fillMaxSize()) {
        if (hasPermissions) {
            CameraPreview(lifecycle, imageCapture, Modifier.height(300.dp))
        } else {
            Box(Modifier.height(300.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Cấp quyền camera và vị trí") }
            }
        }
        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Báo cáo hư hại thủ công", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                listOf("D10", "D20", "D40").forEach { type -> FilterChip(selected = damageType == type, onClick = { damageType = type }, label = { Text(damageTypeLabel(type)) }) }
            }
            Text("Độ tin cậy: ${(confidence * 100).toInt()}%", Modifier.padding(top = 8.dp))
            Slider(confidence, { confidence = it }, valueRange = .45f..1f)
            Button(enabled = hasPermissions, modifier = Modifier.fillMaxWidth(), onClick = {
                status = "Đang chụp ảnh…"
                val imageFile = File(context.cacheDir, "${UUID.randomUUID()}.jpg")
                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(imageFile).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onError(exception: ImageCaptureException) { status = "Không thể chụp ảnh: ${exception.message}" }
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            processAndCompressCapturedImage(imageFile)
                            status = "Đang lấy tọa độ GPS…"
                            LocationTracker(context).currentLocation { lat, lng ->
                                if (lat == null || lng == null) { status = "Đã chụp ảnh nhưng không lấy được GPS. Vui lòng bật vị trí và thử lại ngoài trời."; return@currentLocation }
                                status = "Đang tải báo cáo lên máy chủ…"
                                scope.launch {
                                    val newReport = LocalReport(
                                        UUID.randomUUID().toString(),
                                        damageType,
                                        confidence,
                                        lat,
                                        lng,
                                        null,
                                        imageFile.absolutePath,
                                        "[]",
                                        System.currentTimeMillis(),
                                        SyncState.PENDING_SYNC
                                    )
                                    withContext(Dispatchers.IO) { dao.insert(newReport) }
                                    val ok = com.roaddamage.work.ReportSyncHelper.syncReport(context, newReport)
                                    if (ok) {
                                        status = "Báo cáo đã tải lên máy chủ thành công!"
                                    } else {
                                        status = "Đã lưu vào máy. Đang đồng bộ ngầm khi có mạng."
                                        com.roaddamage.work.ReportSyncWorker.enqueue(context, settings.get().wifiOnly)
                                    }
                                }
                            }
                        }
                    }
                )
            }) { Text("Chụp và lưu báo cáo") }
            Text(status, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            Text("Đây là chế độ báo cáo thủ công. Chế độ tự động sử dụng AI nhận diện trên camera.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CameraPreview(lifecycle: androidx.lifecycle.LifecycleOwner, capture: ImageCapture, modifier: Modifier) {
    AndroidView(factory = { viewContext ->
        PreviewView(viewContext).also { previewView ->
            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
            providerFuture.addListener({
                val provider = providerFuture.get()
                provider.unbindAll()
                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }, capture)
            }, ContextCompat.getMainExecutor(viewContext))
        }
    }, modifier = modifier.fillMaxWidth())
}

@Composable
private fun MapScreen(
    token: String,
    role: String,
    targetPoint: Point? = null,
    targetReportId: String? = null,
    onClearTarget: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { ReportDatabase.getInstance(context).reports() }
    val localReports by dao.observeAll().collectAsState(initial = emptyList())
    var serverReports by remember { mutableStateOf<List<ServerReport>>(emptyList()) }
    var serverMessage by remember { mutableStateOf("Đang tải báo cáo...") }
    var selected by remember { mutableStateOf<MapReport?>(null) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenLocalPath by remember { mutableStateOf<String?>(null) }
    var mapViewInstance by remember { mutableStateOf<MapView?>(null) }
    var hasInitiallyCentered by remember { mutableStateOf(false) }
    var isSatellite by remember { mutableStateOf(false) }
    var annotationManager by remember { mutableStateOf<com.mapbox.maps.plugin.annotation.generated.CircleAnnotationManager?>(null) }
    val markerByAnnotation = remember { mutableMapOf<String, MapReport>() }

    fun setMapStyle(satellite: Boolean) {
        isSatellite = satellite
        val styleUri = if (satellite) Style.SATELLITE_STREETS else Style.STANDARD
        mapViewInstance?.mapboxMap?.loadStyle(styleUri) {
            val manager = mapViewInstance?.annotations?.createCircleAnnotationManager()
            manager?.addClickListener { annotation -> markerByAnnotation[annotation.id]?.let { selected = it }; true }
            annotationManager = manager
        }
    }

    fun loadServerReports() = scope.launch {
        val result = runCatching { withContext(Dispatchers.IO) { ApiFactory.create(BuildConfig.API_BASE_URL).reports("Bearer $token") } }
        val response = result.getOrNull()
        if (response?.isSuccessful == true) {
            serverReports = response.body()?.items.orEmpty()
            serverMessage = "${serverReports.size} báo cáo từ máy chủ"
        } else {
            serverMessage = "Máy chủ ngoại tuyến - hiển thị báo cáo trên máy."
        }
    }
    LaunchedEffect(Unit) { loadServerReports() }

    val serverMarkers = serverReports.mapNotNull { report ->
        val point = report.location?.coordinates
        if (point == null || point.size < 2) null else MapReport(
            id = "server-${report.id}", damageType = report.damageType, status = report.status,
            sender = report.user?.email ?: "Người dùng", createdAt = report.createdAt,
            longitude = point[0], latitude = point[1], imageUrl = report.imageUrl
        )
    }
    val localMarkers = localReports
        .filter { local ->
            serverReports.none { s ->
                s.damageType == local.damageType &&
                s.location?.coordinates?.getOrNull(0) == local.longitude &&
                s.location?.coordinates?.getOrNull(1) == local.latitude
            }
        }
        .map { local -> MapReport("local-${local.id}", local.damageType, "moi_phat_hien", "Thiết bị này (chờ tải lên)", java.util.Date(local.createdAt).toString(), local.latitude, local.longitude, localImage = local.imagePath) }
    val markers = serverMarkers + localMarkers

    LaunchedEffect(markers, annotationManager) {
        val manager = annotationManager ?: return@LaunchedEffect
        manager.deleteAll()
        markerByAnnotation.clear()

        markers.forEach { report ->
            val annotation = manager.create(
                CircleAnnotationOptions().withPoint(Point.fromLngLat(report.longitude, report.latitude))
                    .withCircleRadius(10.0).withCircleColor(statusColor(report.status))
                    .withCircleStrokeColor("#FFFFFF").withCircleStrokeWidth(2.0)
            )
            markerByAnnotation[annotation.id] = report
        }
    }

    LaunchedEffect(markers, targetPoint, mapViewInstance) {
        val mv = mapViewInstance ?: return@LaunchedEffect
        if (targetPoint != null) {
            mv.mapboxMap.setCamera(CameraOptions.Builder().center(targetPoint).zoom(16.5).build())
            if (targetReportId != null) {
                val found = markers.find { it.id == "server-$targetReportId" || it.id == targetReportId }
                if (found != null) {
                    selected = found
                }
            }
            onClearTarget?.invoke()
        } else if (!hasInitiallyCentered && markers.isNotEmpty()) {
            val first = markers.first()
            mv.mapboxMap.setCamera(CameraOptions.Builder().center(Point.fromLngLat(first.longitude, first.latitude)).zoom(16.0).build())
            hasInitiallyCentered = true
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Bản đồ hư hại", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            FilterChip(
                selected = isSatellite,
                onClick = { setMapStyle(!isSatellite) },
                label = { Text(if (isSatellite) "Vệ tinh" else "Bản đồ 2D") },
                leadingIcon = {
                    Icon(
                        if (isSatellite) Icons.Default.Satellite else Icons.Default.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = { loadServerReports() }) { Text("Làm mới") }
        }
        Text("Đỏ: Chưa sửa  |  Xanh: Đang sửa  |  Xám: Đã sửa", style = MaterialTheme.typography.bodySmall)
        Text(serverMessage, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall)
        AndroidView(
            factory = { viewContext ->
                val initCenter = targetPoint ?: markers.firstOrNull()?.let { Point.fromLngLat(it.longitude, it.latitude) } ?: Point.fromLngLat(106.6009, 17.4721)
                MapView(viewContext, MapInitOptions(viewContext, cameraOptions = CameraOptions.Builder().center(initCenter).zoom(if (markers.isEmpty()) 14.0 else 16.0).build())).also { mapView ->
                    mapViewInstance = mapView
                    val initStyle = if (isSatellite) Style.SATELLITE_STREETS else Style.STANDARD
                    mapView.mapboxMap.loadStyle(initStyle) {
                        val manager = mapView.annotations.createCircleAnnotationManager()
                        manager.addClickListener { annotation -> markerByAnnotation[annotation.id]?.let { selected = it }; true }
                        annotationManager = manager
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 12.dp)
        )
        if (markers.isEmpty()) Text("Chụp và gửi báo cáo để hiển thị các điểm trên bản đồ.", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
    }

    selected?.let { report ->
        val isManager = role == "manager" && report.id.startsWith("server-")
        val realId = report.id.removePrefix("server-")
        var updateMsg by remember { mutableStateOf<String?>(null) }
        var isUpdating by remember { mutableStateOf(false) }

        fun doUpdate(newStatus: String) {
            scope.launch {
                isUpdating = true
                updateMsg = "Đang cập nhật..."
                val req = com.roaddamage.network.ReportUpdateRequest(
                    status = newStatus
                )
                val res = runCatching {
                    withContext(Dispatchers.IO) {
                        ApiFactory.create(BuildConfig.API_BASE_URL).updateStatus("Bearer $token", realId, req)
                    }
                }
                isUpdating = false
                if (res.getOrNull()?.isSuccessful == true) {
                    updateMsg = "Cập nhật thành công!"
                    selected = report.copy(
                        status = newStatus
                    )
                    loadServerReports()
                } else {
                    updateMsg = "Cập nhật thất bại. Vui lòng thử lại."
                }
            }
        }

        var showConfirmDelete by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { selected = null },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isManager) {
                        if (showConfirmDelete) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        isUpdating = true
                                        updateMsg = "Đang xóa điểm..."
                                        val res = runCatching {
                                            withContext(Dispatchers.IO) {
                                                ApiFactory.create(BuildConfig.API_BASE_URL).deleteReport("Bearer $token", realId)
                                            }
                                        }
                                        isUpdating = false
                                        if (res.getOrNull()?.isSuccessful == true) {
                                            selected = null
                                            loadServerReports()
                                        } else {
                                            updateMsg = "Xóa thất bại. Vui lòng thử lại."
                                        }
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Xác nhận xóa")
                            }
                        } else {
                            TextButton(
                                onClick = { showConfirmDelete = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Xóa điểm")
                            }
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    TextButton(onClick = { selected = null }) { Text("Đóng") }
                }
            },
            title = { Text("Chi tiết hư hại") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isManager) {
                        Text("Điều chỉnh trạng thái", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        AppDropdown(
                            label = "Trạng thái",
                            selectedValue = report.status,
                            items = listOf(
                                "moi_phat_hien" to "Chưa sửa",
                                "dang_xu_ly" to "Đang sửa",
                                "da_sua" to "Đã sửa"
                            ),
                            onValueChange = { newSt -> doUpdate(newSt) }
                        )
                        updateMsg?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    }
                    Text("Loại hư hại:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        parseDamageTypes(report.damageType).forEach { code ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    singleDamageTypeName(code),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Text("Người báo cáo: ${report.sender}", style = MaterialTheme.typography.bodySmall)
                    Text("Thời gian: ${formatDateTime(report.createdAt)}", style = MaterialTheme.typography.bodySmall)
                    Text("Tọa độ: ${"%.6f".format(report.latitude)}, ${"%.6f".format(report.longitude)}", style = MaterialTheme.typography.bodySmall)
                    report.imageUrl?.let { imgUrl ->
                        val fullUrl = phoneReachableImageUrl(imgUrl)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .clickable {
                                    fullScreenImageUrl = fullUrl
                                    fullScreenLocalPath = null
                                }
                        ) {
                            AsyncImage(
                                model = fullUrl,
                                contentDescription = "Reported road damage",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    "🔍 Phóng to ảnh",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    report.localImage?.let { path ->
                        BitmapFactory.decodeFile(path)?.let { bitmap ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable {
                                        fullScreenLocalPath = path
                                        fullScreenImageUrl = null
                                    }
                            ) {
                                Image(
                                    bitmap.asImageBitmap(),
                                    "Reported road damage",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(6.dp)
                                ) {
                                    Text(
                                        "🔍 Phóng to ảnh",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (fullScreenImageUrl != null || fullScreenLocalPath != null) {
        Dialog(
            onDismissRequest = {
                fullScreenImageUrl = null
                fullScreenLocalPath = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                if (fullScreenImageUrl != null) {
                    AsyncImage(
                        model = fullScreenImageUrl,
                        contentDescription = "Full road damage image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else if (fullScreenLocalPath != null) {
                    BitmapFactory.decodeFile(fullScreenLocalPath)?.let { bmp ->
                        Image(
                            bmp.asImageBitmap(),
                            contentDescription = "Full road damage image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                IconButton(
                    onClick = {
                        fullScreenImageUrl = null
                        fullScreenLocalPath = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportsScreen(token: String, role: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { ReportDatabase.getInstance(context).reports() }
    val localReports by dao.observeAll().collectAsState(initial = emptyList())
    var reports by remember { mutableStateOf<List<ServerReport>>(emptyList()) }
    var message by remember { mutableStateOf("Đang tải danh sách báo cáo từ máy chủ…") }
    fun load() = scope.launch {
        message = "Đang tải dữ liệu và đồng bộ…"
        val syncedCount = withContext(Dispatchers.IO) { com.roaddamage.work.ReportSyncHelper.syncAllPending(context) }
        val result = runCatching { withContext(Dispatchers.IO) { ApiFactory.create(BuildConfig.API_BASE_URL).reports("Bearer $token") } }
        val response = result.getOrNull()
        if (response?.isSuccessful == true) {
            reports = response.body()?.items.orEmpty()
            message = if (syncedCount > 0) "Đã tải lên $syncedCount báo cáo. ${reports.size} báo cáo từ máy chủ." else "${reports.size} báo cáo từ máy chủ."
        } else if (response != null) {
            message = "Máy chủ trả về HTTP ${response.code()}. Đăng xuất và đăng nhập lại nếu là 401."
        } else {
            message = "Không thể kết nối ${BuildConfig.API_BASE_URL}. Kiểm tra Wi-Fi và server."
        }
    }
    LaunchedEffect(Unit) { load() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(if (role == "manager") "Báo cáo quản lý" else "Báo cáo đã gửi", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)); TextButton(onClick = { load() }) { Text("Làm mới & Đồng bộ") } }
        Text(message, style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxSize()) {
            if (localReports.isNotEmpty()) {
                item { Text("Trên thiết bị này", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
                items(localReports, key = { "local-${it.id}" }) { report ->
                    val syncLabel = when (report.syncState) { SyncState.SYNCED -> "Đã tải lên"; SyncState.PENDING_SYNC -> "Đang chờ tải lên"; SyncState.FAILED -> "Lỗi tải lên" }
                    ListItem(
                        headlineContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            parseDamageTypes(report.damageType).forEach { code ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        singleDamageTypeName(code),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    },
                        supportingContent = { Text("$syncLabel · ${(report.confidence * 100).toInt()}%") },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (report.syncState != SyncState.SYNCED) {
                                    OutlinedButton(
                                        onClick = {
                                            scope.launch {
                                                message = "Đang tải lên báo cáo..."
                                                val ok = com.roaddamage.work.ReportSyncHelper.syncReport(context, report)
                                                load()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Tải lên ngay", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            dao.deleteById(report.id)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    )
                    HorizontalDivider()
                }
                item { Text("Báo cáo trên máy chủ", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) }
            }
            items(reports, key = { it.id }) { report ->
                ListItem(
                    headlineContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            parseDamageTypes(report.damageType).forEach { code ->
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        singleDamageTypeName(code),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    },
                    supportingContent = { Text("${statusLabel(report.status)} · ${(report.confidence * 100).toInt()}% · ${report.occurrenceCount} lần") },
                    trailingContent = if (role == "manager") {
                        {
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        message = "Đang xóa báo cáo..."
                                        withContext(Dispatchers.IO) {
                                            ApiFactory.create(BuildConfig.API_BASE_URL).deleteReport("Bearer $token", report.id)
                                        }
                                        load()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else null
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ManagerScreen(
    token: String,
    onNavigateToMap: (lat: Double, lng: Double, reportId: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<ServerReport>>(emptyList()) }
    var selectedStatus by remember { mutableStateOf<String?>(null) }
    var selectedDamageType by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("Đang tải danh sách báo cáo…") }
    var addressMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun load() = scope.launch {
        message = "Đang tải dữ liệu…"
        val result = runCatching { withContext(Dispatchers.IO) { ApiFactory.create(BuildConfig.API_BASE_URL).reports("Bearer $token") } }
        val response = result.getOrNull()
        if (response?.isSuccessful == true) {
            reports = response.body()?.items.orEmpty()
            message = "Có ${reports.size} báo cáo từ máy chủ."
        } else {
            message = "Không thể tải báo cáo. Kiểm tra kết nối máy chủ hoặc đăng nhập lại."
        }
    }

    fun setStatus(id: String, status: String) = scope.launch {
        message = "Đang cập nhật trạng thái…"
        val result = runCatching {
            withContext(Dispatchers.IO) {
                ApiFactory.create(BuildConfig.API_BASE_URL).updateStatus(
                    "Bearer $token",
                    id,
                    com.roaddamage.network.ReportUpdateRequest(status = status)
                )
            }
        }
        if (result.getOrNull()?.isSuccessful == true) {
            message = "Cập nhật thành công!"
            load()
        } else {
            message = "Cập nhật thất bại. Yêu cầu quyền quản lý (manager)."
        }
    }

    LaunchedEffect(Unit) { load() }

    LaunchedEffect(reports) {
        withContext(Dispatchers.IO) {
            val geocoder = try {
                if (Geocoder.isPresent()) Geocoder(context, Locale("vi", "VN")) else null
            } catch (e: Exception) { null }

            reports.forEach { report ->
                val coords = report.location?.coordinates
                if (coords != null && coords.size >= 2 && !addressMap.containsKey(report.id)) {
                    val lng = coords[0]
                    val lat = coords[1]
                    val resolved = try {
                        val list = geocoder?.getFromLocation(lat, lng, 1)
                        val first = list?.firstOrNull()
                        val line = first?.getAddressLine(0)
                        if (!line.isNullOrBlank()) {
                            line
                        } else {
                            val parts = listOfNotNull(
                                first?.thoroughfare ?: first?.subLocality,
                                first?.locality ?: first?.subAdminArea,
                                first?.adminArea
                            ).filter { it.isNotBlank() }
                            if (parts.isNotEmpty()) parts.joinToString(", ")
                            else "Tọa độ: %.5f, %.5f".format(lat, lng)
                        }
                    } catch (e: Exception) {
                        "Tọa độ: %.5f, %.5f".format(lat, lng)
                    }
                    withContext(Dispatchers.Main) {
                        addressMap = addressMap + (report.id to resolved)
                    }
                }
            }
        }
    }

    val visibleReports = reports.filter {
        (selectedStatus == null || it.status == selectedStatus) &&
        (selectedDamageType == null || parseDamageTypes(it.damageType).any { code ->
            val trimmed = code.trim()
            trimmed == selectedDamageType || trimmed.startsWith(selectedDamageType ?: "")
        })
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quản lý hư hại đường", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { load() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Làm mới")
            }
        }
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

        Card(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Bộ lọc tìm kiếm", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppDropdown(
                        label = "Trạng thái",
                        selectedValue = selectedStatus,
                        items = listOf(
                            null to "Tất cả",
                            "moi_phat_hien" to "Chưa sửa",
                            "dang_xu_ly" to "Đang sửa",
                            "da_sua" to "Đã sửa"
                        ),
                        onValueChange = { selectedStatus = it },
                        modifier = Modifier.weight(1f)
                    )
                    AppDropdown(
                        label = "Loại hư hại",
                        selectedValue = selectedDamageType,
                        items = listOf(
                            null to "Tất cả",
                            "D10" to "Vết nứt ngang",
                            "D20" to "Nứt da cá sấu",
                            "D40" to "Ổ gà"
                        ),
                        onValueChange = { selectedDamageType = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Text(
            "Hiển thị ${visibleReports.size} / ${reports.size} vị trí hư hại",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(visibleReports, key = { it.id }) { report ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Header: Damage tags & Occurrences
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                parseDamageTypes(report.damageType).forEach { code ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(
                                            singleDamageTypeName(code),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                            Text(
                                "${(report.confidence * 100).toInt()}% • ${report.occurrenceCount} lần phát hiện",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Reporter Email & Report Time
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = "Người báo cáo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    report.user?.email ?: "Ẩn danh",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AccessTime,
                                    contentDescription = "Thời gian",
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    formatDateTime(report.createdAt),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Address Row
                        val coords = report.location?.coordinates
                        val addressText = addressMap[report.id] ?: if (coords != null && coords.size >= 2) "Tọa độ: %.5f, %.5f".format(coords[1], coords[0]) else "Chưa có vị trí"
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Địa chỉ",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp).padding(top = 2.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                addressText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Status Dropdown and Jump to Map
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppDropdown(
                                label = "Trạng thái",
                                selectedValue = report.status,
                                items = listOf(
                                    "moi_phat_hien" to "Chưa sửa",
                                    "dang_xu_ly" to "Đang sửa",
                                    "da_sua" to "Đã sửa"
                                ),
                                onValueChange = { newStatus -> setStatus(report.id, newStatus) },
                                modifier = Modifier.weight(1f)
                            )

                            if (coords != null && coords.size >= 2) {
                                FilledTonalButton(
                                    onClick = { onNavigateToMap(coords[1], coords[0], report.id) },
                                    modifier = Modifier.padding(top = 16.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("Bản đồ", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            var showDeleteConfirm by remember { mutableStateOf(false) }
                            if (showDeleteConfirm) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            message = "Đang xóa điểm..."
                                            val res = runCatching {
                                                withContext(Dispatchers.IO) {
                                                    ApiFactory.create(BuildConfig.API_BASE_URL).deleteReport("Bearer $token", report.id)
                                                }
                                            }
                                            if (res.getOrNull()?.isSuccessful == true) {
                                                message = "Đã xóa điểm thành công!"
                                                load()
                                            } else {
                                                message = "Xóa điểm thất bại."
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(top = 16.dp),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Xác nhận xóa", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                IconButton(
                                    onClick = { showDeleteConfirm = true },
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Xóa điểm",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: SettingsStore, onLogout: () -> Unit) {
    var value by remember { mutableStateOf(settings.get()) }
    Column(Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState())) {
        Text("Cài đặt", style = MaterialTheme.typography.headlineSmall)
        Text(settings.email(), Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

        Text("Ngưỡng nhận diện hư hại: ${(value.confidenceThreshold * 100).toInt()}%", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
        Text("Mô hình AI chỉ ghi nhận hư hại khi độ tin cậy lớn hơn mức này.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Slider(value.confidenceThreshold, { value = value.copy(confidenceThreshold = it); settings.save(value) }, valueRange = .1f.. .9f)

        SettingToggle("Chỉ đồng bộ khi có Wi-Fi", value.wifiOnly) { value = value.copy(wifiOnly = it); settings.save(value) }

        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth().padding(top = 28.dp)) { Text("Đăng xuất") }
    }
}

@Composable
private fun SettingToggle(title: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(checked, change) }
}

private fun hasCameraAndLocationPermission(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
