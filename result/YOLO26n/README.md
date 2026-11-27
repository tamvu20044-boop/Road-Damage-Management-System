# YOLO26n — Kết quả huấn luyện và Test

Checkpoint được chọn: [`../../models/YOLO26n.pt`](../../models/YOLO26n.pt). Cấu hình huấn luyện cuối: **Linear Scheduler, `lr0=0.000667`**.

## Hiệu suất Test (2.196 ảnh)

| Precision | Recall | mAP50 | mAP50–95 |
|---:|---:|---:|---:|
| 68,05% | 59,46% | 64,94% | 32,82% |

Nguồn: [`metrics/metrics.json`](metrics/metrics.json). Các giá trị đã làm tròn; dữ liệu gốc trong JSON ở thang 0–1.

## Lịch sử huấn luyện

- [`training_curve/results.csv`](training_curve/results.csv) và [`training_curve/results.png`](training_curve/results.png): **80 epoch** được ghi nhận, mAP50–95 Validation cao nhất ở **epoch 65**.
- Không suy ra mAP Test từ điểm cao nhất trên Validation; đó là hai tập dữ liệu độc lập.

## Ma trận 4×4

- [`confusion_matrix/confusion_matrix_4x4_counts.csv`](confusion_matrix/confusion_matrix_4x4_counts.csv) và [hình PNG](confusion_matrix/confusion_matrix_4x4_counts.png): thêm hàng/cột Background nhằm thể hiện dự đoán thừa và bỏ sót.
- Số trường hợp dự đoán đúng lớp (tổng ba phần tử đường chéo): **2,838**.
- GT bị bỏ sót: **1,655**; dự đoán không ghép được GT: **1,296**. Các số đếm ma trận chỉ thuộc giao thức riêng của phép phân tích này.

## Các tệp đầu ra

| Tệp / thư mục | Ý nghĩa |
|---|---|
| `metrics/per_class_ap.csv` | AP từng lớp |
| `metrics/BoxPR_curve.png` | Đường cong Precision–Recall |
| `metrics/run_metadata.json` | Metadata thực nghiệm và đường dẫn checkpoint gốc |
| `multi_damage/type_count_summary.csv` | Tổng hợp theo 0, 1, 2, 3 loại dự đoán/GT |
| `multi_damage/combination_summary.csv` | Kết quả theo tổ hợp loại hư hỏng |
| `multi_damage/per_image_analysis.csv` | Phân tích từng ảnh |
| `multi_damage/per_class_tp_fp_fn.csv` | Các thống kê TP/FP/FN theo lớp |
| `multi_damage/types_distribution.png`, `combinations.png`, `all_types_localized.png` | Các biểu đồ so sánh |

Kết quả mAP chính thức lấy từ `model.val()` theo thiết lập của notebook; ma trận và phân tích theo ảnh dùng `conf=0.25`, NMS IoU `0.45`, IoU ghép `0.50`. **Không dùng số liệu ma trận để thay thế Precision/Recall/mAP trong bảng Test.**

Ảnh minh họa ba cột GT — YOLO26n — YOLO26s được lưu chung ở [`../examples/`](../examples/).
