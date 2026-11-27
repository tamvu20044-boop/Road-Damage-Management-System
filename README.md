# Phát hiện, định vị và quản lý hư hỏng mặt đường ứng dụng học sâu

Kho mã nguồn **nghiên cứu, huấn luyện và đánh giá** hai mô hình YOLO26n và YOLO26s cho đề tài “Nghiên cứu và xây dựng hệ thống phát hiện, định vị và quản lý hư hỏng mặt đường ứng dụng học sâu” (PBL5: Hệ thống máy tính, Trường Đại học Bách khoa – Đại học Đà Nẵng).


## Dữ liệu nghiên cứu

Ảnh từ RDD2022 (Nhật Bản, Trung Quốc, Ấn Độ) được chuẩn hóa về ba lớp:

| ID | Tên lớp | Nhãn nguồn |
|---:|---|---|
| 0 | `pothole` | D40 |
| 1 | `linear_crack` | D00 + D10 |
| 2 | `alligator_crack` | D20 |

| Tập | Ảnh | Bounding box GT |
|---|---:|---:|
| Train | 10.579 | 21.860 |
| Validation | 2.201 | 4.611 |
| Test | 2.196 | 4.548 |
| **Tổng** | **14.976** | **31.019** |

## Cấu trúc hiện có

```text
.
├── training/                         # 2 notebook LR Finder + 4 notebook huấn luyện
├── evaluation/
│   ├── Test_evaluation.ipynb
│   └── requirements.txt
├── models/
│   ├── YOLO26n.pt
│   └── YOLO26s.pt
├── result/
│   ├── lr_finder/
│   ├── hyperparameter_tuning/
│   ├── YOLO26n/
│   ├── YOLO26s/
│   └── examples/
├── .gitignore
└── README.md
```

Mỗi thư mục chuyên môn có README giải thích tệp thực tế trong đó.

## Quy trình và cấu hình cuối

1. Chuẩn bị dữ liệu và `data.yaml` với đúng ba lớp.
2. Khảo sát vùng LR bằng LR Finder (không dùng trọng số từ LR Finder để tiếp tục fine-tune).
3. Khảo sát năm LR với Linear và so sánh Linear/Cosine tại hai LR được chọn trên tập **Validation**.
4. Trong **bốn notebook Linear/Cosine đang lưu**, `lr0` đều là **`0.000667`**; hai notebook Cosine bật `cos_lr=True`, hai notebook Linear dùng lịch Linear.
5. Cấu hình cuối được lựa chọn để báo cáo kết quả Test là **Linear Scheduler, `lr0=0.000667`**, với checkpoint riêng cho YOLO26n và YOLO26s.
6. Đánh giá cuối cùng trên tập Test, tạo các tệp chỉ số, ma trận 4×4 và phân tích nhiều loại hư hỏng trong ảnh.

**Phân biệt:** `hyperparameter_tuning.csv` ghi cả các LR đã khảo sát (`0.0003`, `0.000667`, `0.001`, `0.0013`, `0.002`), không có nghĩa bốn notebook huấn luyện trong bản lưu trữ dùng năm LR khác nhau. Tên thư mục checkpoint Kaggle có chuỗi `0-00067` chỉ là tên đường dẫn; khi nói đến LR của notebook, ưu tiên giá trị `lr0` trong code.

## Kết quả Test của checkpoint được chọn

| Mô hình | Precision | Recall | mAP50 | mAP50–95 |
|---|---:|---:|---:|---:|
| YOLO26n | 68,05% | 59,46% | 64,94% | 32,82% |
| YOLO26s | 69,16% | 60,77% | 66,45% | 33,45% |

Nguồn số liệu: `result/YOLO26n/metrics/metrics.json`, `result/YOLO26s/metrics/metrics.json`. Giá trị hiển thị được làm tròn; file JSON lưu số ở thang 0–1.

**Hai giao thức khác nhau:** mAP/Precision/Recall trong `metrics.json` từ phép `model.val()` chính thức; ma trận 4×4 và thống kê theo ảnh dùng `conf=0.25`, NMS IoU `0.45`, matching IoU `0.50`. Không suy ngược mAP từ số ô ma trận. Chi tiết tại [evaluation/README.md](evaluation/README.md).

## Hướng dẫn đọc và chạy

- [Training](training/README.md): danh sách sáu notebook, cấu hình và các thí nghiệm.
- [Evaluation](evaluation/README.md): nạp dữ liệu/trọng số, chạy Test và hiểu các ngưỡng.
- [Models](models/README.md): hai checkpoint đã được lưu.
- [Results](result/README.md): các thư mục CSV/JSON/PNG và ý nghĩa kết quả.

Notebook chứa đường dẫn Kaggle của lần chạy gốc; cần thay đổi đường dẫn phù hợp trong môi trường mới. Các output lưu sẵn không thay thế cho việc chạy lại để tái lập.

