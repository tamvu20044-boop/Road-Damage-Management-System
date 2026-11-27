# Result — Kết quả thực nghiệm đã xuất

Thư mục này chứa **dữ liệu và hình ảnh kết quả**, không chứa các notebook tạo ra chúng. Xem [`../training/`](../training/) và [`../evaluation/`](../evaluation/) để đọc mã nguồn.

| Thư mục | Chứa gì |
|---|---|
| [`lr_finder/`](lr_finder/) | Hai ảnh LR Range Test và hai tệp TXT ghi mỗi optimizer step |
| [`hyperparameter_tuning/`](hyperparameter_tuning/) | CSV tổng hợp 14 cấu hình Validation của hai mô hình |
| [`YOLO26n/`](YOLO26n/) | Đường cong huấn luyện, kết quả Test, ma trận 4×4 và phân tích theo ảnh |
| [`YOLO26s/`](YOLO26s/) | Các kết quả tương ứng của YOLO26s |
| [`examples/`](examples/) | Tám ảnh đối chiếu Ground Truth — YOLO26n — YOLO26s |

## Sử dụng kết quả đúng mục đích

- **Validation:** LR Finder, bảng khảo sát LR/Scheduler, đường cong huấn luyện được dùng để lựa chọn mô hình/cấu hình. Các notebook Linear/Cosine có trong repository đều ghi `lr0=0.000667`; CSV khảo sát chứa **thêm** các LR của những thí nghiệm khác.
- **Test:** `metrics.json`, `per_class_ap.csv`, `confusion_matrix_4x4_counts.csv` và `multi_damage/*.csv` chỉ dùng đánh giá và phân tích mô hình đã chọn.
- Chỉ số P/R/mAP của phép Test chính thức không đồng nghĩa với thống kê tại `conf=0.25` và IoU hậu xử lý `0.45`. Xem [`../evaluation/README.md`](../evaluation/README.md).

Các CSV lưu số dạng dấu chấm thập phân để thuận tiện đọc bằng Python. Trong báo cáo có thể chuyển sang dấu phẩy thập phân và phần trăm tùy ngữ cảnh.
