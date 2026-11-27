# Evaluation — Đánh giá cuối cùng trên Test

Thư mục có `Test_evaluation.ipynb` và `requirements.txt`. Notebook đánh giá **cả YOLO26n và YOLO26s** trên bộ Test đã chuẩn hóa gồm 2.196 ảnh.

## Đầu vào

- Dataset RDD2022 đã chuẩn hóa, đúng ánh xạ nhãn: `0=pothole`, `1=linear_crack`, `2=alligator_crack`.
- `data.yaml` trỏ tới tập Test; notebook dùng đường dẫn riêng của môi trường Kaggle ban đầu, vì vậy cần kiểm tra và điều chỉnh trước khi chạy lại.
- Hai checkpoint tại [`../models/YOLO26n.pt`](../models/YOLO26n.pt) và [`../models/YOLO26s.pt`](../models/YOLO26s.pt), được chọn từ **Linear, `lr0=0.000667`**. Những chuỗi tên thư mục Kaggle có `0-00067` trong metadata là đường dẫn tệp gốc, không thay thế tham số `lr0` đã ghi trong notebook huấn luyện.

## Phân biệt các phép đánh giá

| Phép đánh giá | Thiết lập | File đầu ra |
|---|---|---|
| Test chính thức | `model.val()` không ghi đè `conf`/`iou` trong lời gọi này | `result/<mô hình>/metrics/metrics.json`, `per_class_ap.csv` |
| Ma trận 4×4 | `conf=0.25`, NMS IoU `0.45`, `nms=True`, `agnostic_nms=False` | `result/<mô hình>/confusion_matrix/` |
| Phân tích theo ảnh | Dự đoán với `conf=0.25`, NMS IoU `0.45`; ghép **cùng lớp** với GT tại IoU ≥ `0.50` | `result/<mô hình>/multi_damage/` |

**NMS IoU ≠ matching IoU.** NMS dùng loại dự đoán chồng lấp; matching dùng xác định khung dự đoán có khớp GT ở phép phân tích hay không. Các số TP/FP/FN trong ma trận/CSV không dùng để tính ngược các chỉ số mAP chính thức bằng phép chia đơn giản.

## Số lượng và xử lý nhãn

- Tập Test: **2.196 ảnh, 4.548 bounding box GT**.
- Trong phép thống kê ma trận 4×4, một bounding box GT trùng hoàn toàn được loại khỏi số đếm; tổng GT tính cho ma trận là **4.547**. Đây là phần xử lý riêng của ma trận, không thay số nhãn Test gốc.
- Các số trong `metrics.json` ở thang `0–1`, khi hiển thị theo % nhân 100.

## Cách chạy lại

1. Chuẩn bị dữ liệu và nạp hai checkpoint phù hợp, kiểm tra mọi đường dẫn (`DATA_YAML`, `MODEL_PATH`, thư mục output).
2. Chạy các cell theo thứ tự trong `Test_evaluation.ipynb`; phần YOLO26n có thể phụ thuộc biến/đầu ra từ các cell thiết lập trước đó.
3. Xuất và lưu `metrics`, ma trận, CSV theo ảnh và hình minh họa.
4. Đối chiếu với dữ liệu gốc trong [`../result/`](../result/).

**Không dùng tập Test để lựa chọn lại LR, Scheduler hoặc checkpoint.**
