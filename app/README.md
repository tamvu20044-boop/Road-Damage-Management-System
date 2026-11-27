# Ứng dụng Road Damage Management System

Thư mục `app/` chứa **ứng dụng Android** và **Backend** để ghi nhận, định vị và quản lý các báo cáo hư hỏng mặt đường. Mô hình nhận diện chạy **trên điện thoại**; Backend nhận báo cáo và lưu trữ thông tin, ảnh và tọa độ.

## Thành phần

| Thành phần | Công nghệ trong mã nguồn | Vai trò |
|---|---|---|
| [`android/`](android/README.md) | Kotlin, Jetpack Compose, CameraX, TensorFlow Lite, GPS, Room, WorkManager, Mapbox | Thu nhận ảnh, suy luận, lưu cục bộ, hiển thị bản đồ và đồng bộ |
| [`backend/`](backend/README.md) | Node.js, Express, TypeScript, TypeORM, PostgreSQL/PostGIS, MinIO | Xác thực, quản lý báo cáo, ảnh và vị trí |

```text
Android (camera / ảnh)
   -> YOLO26s TFLite (640 × 640, ba lớp)
   -> GPS + Room (báo cáo cục bộ)
   -> WorkManager / REST API
   -> Backend + PostgreSQL/PostGIS + MinIO
   -> Android hiển thị báo cáo / bản đồ
```

**Ba lớp nghiên cứu:** `pothole` (D40), `linear_crack` (D00 + D10), `alligator_crack` (D20). Lớp `linear_crack` là nứt tuyến tính gộp cả D00 và D10, không chỉ riêng nứt ngang. Một số *chú thích và nhãn tiếng Việt trong code Android* vẫn mô tả nhãn này là D10/nứt ngang; cần đồng bộ lại trước khi phát hành chính thức.

## Chạy thử ở môi trường phát triển

1. Cài Docker/Docker Compose; vào `app/backend/`, sao chép `.env.example` thành `.env` và thay **toàn bộ** các giá trị `CHANGE_ME` bằng giá trị riêng. Khởi chạy bằng `docker compose up --build -d` (chi tiết: [Backend README](backend/README.md)).
2. Mở thư mục `app/android/` bằng Android Studio. Tạo `local.properties` từ `local.properties.example`, cấu hình Mapbox token và `API_BASE_URL` rồi Sync Gradle (chi tiết: [Android README](android/README.md)).
3. Chạy ứng dụng trên emulator hoặc điện thoại, cấp quyền Camera/GPS, đăng nhập hoặc đăng ký tài khoản, thử tạo báo cáo và kiểm tra đồng bộ với Backend.

**Địa chỉ Backend:** Android Emulator dùng `http://10.0.2.2:3000/` khi Backend chạy trên máy host. Với điện thoại thật, dùng địa chỉ IP LAN hoặc HTTPS có thể truy cập từ điện thoại; `localhost`/`10.0.2.2` không hoạt động như nhau trên hai môi trường.

## Các tệp mô hình

- Mô hình được giữ trong mã nguồn Android: `android/app/src/main/assets/yolo26s_rdd_640.tflite`.
- Checkpoint nghiên cứu: [`../models/YOLO26s.pt`](../models/YOLO26s.pt) và [`../models/YOLO26n.pt`](../models/YOLO26n.pt). Đây là tệp `.pt` dùng cho nghiên cứu/đánh giá, không phải đường dẫn mô hình Android trực tiếp sử dụng.
- Không commit APK debug, dữ liệu checkpoint trùng hoặc các tệp TFLite bản sao khác. APK để chia sẻ kiểm thử nên đưa vào GitHub Releases sau khi rà soát an toàn.

## Cấu hình và an toàn

- **Không commit** `backend/.env`, `android/local.properties`, mật khẩu cơ sở dữ liệu, JWT secret hay Mapbox Downloads Token.
- File `.env.example` và `local.properties.example` chỉ chứa mẫu cấu hình; không thay placeholder bằng khóa thật ngay trong file mẫu.
- Backend Docker Compose xuất cổng 3000, 5432, 9000 và 9001 trên máy chủ phát triển. Không triển khai trực tiếp ra Internet nếu chưa giới hạn mạng, cấu hình HTTPS, phân quyền và bảo vệ các dịch vụ.
- AndroidManifest hiện cho phép cleartext HTTP để thử nghiệm nội bộ; trước khi phát hành, nên ưu tiên HTTPS và tắt cấu hình này nếu không còn cần.

## Tình trạng kiểm thử

Các README này mô tả cấu trúc và hướng dẫn dựa trên **mã nguồn được cung cấp**. Chưa có bằng chứng từ quy trình build/chạy mới rằng APK được biên dịch lại thành công sau khi làm sạch cấu hình. Cần kiểm tra trên thiết bị và Backend thật trước khi công bố đây là bản triển khai sẵn sàng vận hành.
