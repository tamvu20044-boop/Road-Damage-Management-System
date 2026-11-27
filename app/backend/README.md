# Backend — Road Damage Management System

Backend dùng **Node.js, Express, TypeScript, TypeORM, PostgreSQL/PostGIS và MinIO** để xác thực người dùng và quản lý dữ liệu/ảnh của báo cáo hư hỏng mặt đường. Mã nguồn API nằm trong `src/`.

## Cấu trúc

```text
backend/
├── src/
│   ├── controllers/
│   ├── routes/
│   ├── models/
│   ├── services/
│   ├── middlewares/
│   ├── scripts/seed-manager.ts
│   └── server.ts
├── package.json
├── package-lock.json
├── tsconfig.json
├── Dockerfile
├── docker-compose.yml
├── minio.Dockerfile
└── .env.example
```

## Chạy bằng Docker Compose (khuyến nghị cho môi trường thử nghiệm)

Yêu cầu: Docker Engine/Desktop có Docker Compose. Mở terminal tại **`app/backend/`**.

1. Sao chép `.env.example` thành `.env` (trên PowerShell: `Copy-Item .env.example .env`).
2. Đặt lại toàn bộ giá trị `CHANGE_ME`, bảo đảm `DATABASE_URL` có đúng user/password/database của PostgreSQL trong cùng `.env`. Với Compose, tên host cơ sở dữ liệu là `postgres`, MinIO là `minio`.
3. Chạy:

```bash
docker compose up --build -d
```

4. Kiểm tra API: `http://localhost:3000/health` (nếu thành công trả về JSON với `status: "ok"`). Có thể xem log bằng `docker compose logs -f api`.
5. Nếu cần tạo tài khoản manager cho môi trường thử nghiệm, sau khi cấu hình `MANAGER_EMAIL` và `MANAGER_PASSWORD` trong `.env` và build xong, chạy:

```bash
docker compose exec api npm run seed:manager
```

Lệnh seed sẽ tạo hoặc cập nhật tài khoản manager có email đã cấu hình; **không đưa email/mật khẩu thật lên GitHub**. Chạy lệnh này chỉ khi bạn thực sự muốn cập nhật tài khoản quản lý.

Dừng các container: `docker compose down`. Không dùng `docker compose down -v` nếu chưa có bản sao lưu, vì tùy chọn `-v` xóa volumes của PostgreSQL/MinIO.

## Chạy trực tiếp bằng Node (khi đã có dịch vụ phụ trợ)

```bash
npm ci
npm run dev
```

Các script trong `package.json`: `dev`, `build`, `start`, `seed:manager`. Với cách chạy không dùng Compose, bạn phải tự cấu hình PostgreSQL/PostGIS và MinIO có thể truy cập được, đồng thời sửa `DATABASE_URL`, `MINIO_ENDPOINT` thích hợp trong file `.env` cục bộ. Cách `npm run start` yêu cầu đã chạy `npm run build` trước.

## Các nhóm API trong mã nguồn

| Đường dẫn | Ý nghĩa |
|---|---|
| `GET /health` | Kiểm tra API |
| `POST /auth/register` | Đăng ký tài khoản |
| `POST /auth/login` | Đăng nhập |
| `POST /reports` | Tạo báo cáo, gửi ảnh và tọa độ (cần xác thực) |
| `GET /reports` | Danh sách, lọc theo vị trí/trạng thái/loại hư hỏng (cần xác thực) |
| `GET /reports/:id` | Chi tiết báo cáo (cần xác thực) |
| `PATCH /reports/:id/status` | Cập nhật trạng thái hoặc loại hư hỏng (vai trò manager) |
| `DELETE /reports/:id` | Xóa báo cáo (cần xác thực) |

Theo `report.routes.ts`, ảnh tải lên cho `POST /reports` được giới hạn **10 MB** và nhận JPEG/PNG. Tên trường dữ liệu, xác thực và biểu mẫu cụ thể nên đối chiếu trực tiếp với các route/controller khi phát triển client.

## Biến môi trường quan trọng

File [`.env.example`](.env.example) chứa các nhóm biến: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DATABASE_URL`, `JWT_SECRET`, `MANAGER_EMAIL`, `MANAGER_PASSWORD`, `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET` và các thiết lập cổng/URL liên quan.

**Không commit `.env` thật**. File Docker Compose trong repository dùng biến môi trường, không chứa mật khẩu máy chủ triển khai. Các cổng 3000, 5432, 9000 và 9001 đang được xuất ra host, vì thế nên chỉ chạy trong mạng phát triển tin cậy hoặc cấu hình lại firewall/binding. Đối với môi trường triển khai công khai cần HTTPS, secret riêng, cơ chế backup, kiểm soát quyền truy cập và kiểm toán bảo mật trước khi sử dụng.

