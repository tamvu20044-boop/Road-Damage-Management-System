# Models — Hai checkpoint đã chọn

| Tệp | Mô hình | Lịch huấn luyện được chọn | LR ban đầu |
|---|---|---|---:|
| [`YOLO26n.pt`](YOLO26n.pt) | YOLO26n | Linear | **0.000667** |
| [`YOLO26s.pt`](YOLO26s.pt) | YOLO26s | Linear | **0.000667** |

Hai tệp `.pt` là checkpoint tốt nhất được lưu từ các lần huấn luyện đã chọn để đánh giá Test. Tệp gốc trên Kaggle đã được tải về và **chỉ đổi tên** khi đưa vào thư mục này; việc đổi tên không sửa trọng số trong file.

Tên thư mục Kaggle từng có chuỗi `0-00067`. Chuỗi đó là một phần **tên đường dẫn**, không phải cách xác định giá trị `lr0` trong code. Trong các notebook fine-tuning lưu ở `training/`, LR ban đầu thống nhất là `0.000667`.

Ví dụ nạp trọng số:

```python
from ultralytics import YOLO
model_n = YOLO('models/YOLO26n.pt')
model_s = YOLO('models/YOLO26s.pt')
```

Để tái lập kết quả, dùng đúng nhãn, dữ liệu và giao thức đánh giá mô tả ở [`../evaluation/README.md`](../evaluation/README.md). Không sử dụng trọng số sinh ra từ phép LR Finder thay cho các checkpoint này.

**Lưu ý Git:** `.gitignore` có quy tắc `*.pt`, vì vậy cần chủ động thêm ngoại lệ hoặc `git add -f` để Git theo dõi hai checkpoint nếu được phép công bố.
