# Android — Road Report

Mã nguồn ứng dụng Android nằm trong thư mục này, dùng **Kotlin + Jetpack Compose**, CameraX, TensorFlow Lite, Room/SQLite, WorkManager, Mapbox và GPS để nhận diện, định vị và quản lý báo cáo hư hỏng mặt đường.

## Cấu trúc chính

```text
android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/yolo26s_rdd_640.tflite
│       └── java/com/roaddamage/
│           ├── ml/           # TFLite / tiền xử lý / NMS
│           ├── data/         # Cấu trúc dữ liệu và Room
│           ├── network/      # Retrofit API
│           ├── location/     # Vị trí
│           ├── work/         # Đồng bộ nền
│           └── ui/           # Jetpack Compose
├── gradle/wrapper/
├── gradlew / gradlew.bat
├── settings.gradle.kts
└── local.properties.example
```

## Yêu cầu và thiết lập

- Android Studio với Android SDK **35**, JDK **17**; `minSdk=26`, `targetSdk=35` theo `app/build.gradle.kts`.
- Android Emulator hoặc điện thoại Android hỗ trợ các quyền Camera, Vị trí và Internet.
- Backend được cấu hình riêng; xem [`../backend/README.md`](../backend/README.md).

1. Mở **thư mục `app/android/`** trong Android Studio.
2. Sao chép `local.properties.example` thành **`local.properties`**, đặt tại chính thư mục `android/`.
3. Điền các giá trị thực cho `MAPBOX_ACCESS_TOKEN` (token công khai dùng để hiển thị bản đồ), `MAPBOX_DOWNLOADS_TOKEN` (token riêng dùng khi tải dependency Mapbox), `API_BASE_URL`.
4. Sync Gradle và chạy cấu hình `app` trên emulator/điện thoại. Gradle wrapper nằm trong repository; trên Windows có thể dùng `gradlew.bat`.

Mẫu cấu hình (trong `local.properties`, **không commit**):

```properties
MAPBOX_ACCESS_TOKEN=YOUR_PUBLIC_MAPBOX_ACCESS_TOKEN
MAPBOX_DOWNLOADS_TOKEN=YOUR_PRIVATE_MAPBOX_DOWNLOADS_READ_TOKEN
API_BASE_URL=http://10.0.2.2:3000/
```

`10.0.2.2` là địa chỉ host từ Android Emulator. Với **điện thoại thật**, thay bằng địa chỉ Backend mà điện thoại truy cập được (IP LAN/HTTPS); giữ dấu `/` cuối `API_BASE_URL` cho Retrofit. Không đặt Downloads Token vào README, mã nguồn, `BuildConfig` hoặc file được commit.

## Mô hình và nhãn

Model đi kèm: `app/src/main/assets/yolo26s_rdd_640.tflite`; code có logic thử GPU Delegate và dùng CPU nhiều luồng khi GPU không phù hợp, tiền xử lý letterbox 640 × 640 và hậu xử lý NMS theo lớp. Tài liệu này không khẳng định hiệu năng thực tế trước khi chạy kiểm thử thiết bị.

| ID | Nhãn nghiên cứu | Nhãn nguồn |
|---:|---|---|
| 0 | `pothole` | D40 |
| 1 | `linear_crack` | D00 + D10 |
| 2 | `alligator_crack` | D20 |

**Việc cần đồng bộ code:** Hiện phần chú thích/hiển thị trong `ml/YoloDetector.kt` và `ml/RoadDamageDetector.kt` còn gọi lớp `linear_crack` là **D10 / nứt ngang**; tuy nhiên checkpoint nghiên cứu gộp **D00 + D10**. Cần đổi cách hiển thị thành **nứt tuyến tính**, sau khi kiểm tra các vị trí liên quan, trước khi phát hành bản chính thức. Đừng đổi ID lớp nếu không có căn cứ về thứ tự nhãn trong mô hình TFLite.

## Backend và lưu ý triển khai

Retrofit gọi các endpoint như `auth/register`, `auth/login`, `reports` và `reports/{id}/status`. WorkManager hỗ trợ đồng bộ báo cáo chưa gửi; Room cung cấp lưu trữ cục bộ. Khi test, hãy kiểm tra ít nhất đăng ký/đăng nhập, chụp/chọn ảnh, hiển thị dự đoán, GPS, tạo báo cáo, mất mạng và đồng bộ lại.

`AndroidManifest.xml` hiện đặt `android:usesCleartextTraffic="true"`, phù hợp thử nghiệm Backend HTTP trong mạng nội bộ nhưng không nên xem là thiết lập an toàn cho production. Trước khi công khai APK, cần rà soát lại HTTPS, token và thông tin cấu hình nhúng vào ứng dụng.

**Không commit:** `local.properties`, thư mục `.gradle/`, `build/`, APK, keystore và các file chứa token bí mật. Git chỉ giữ tệp `.example` để minh họa.
