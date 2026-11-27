# Hyperparameter tuning — Kết quả Validation

Tệp [`hyperparameter_tuning.csv`](hyperparameter_tuning.csv) lưu **14 cấu hình khảo sát** và 7 cột:

`Model, LR_Scheduler, Learning_Rate, Precision, Recall, mAP50, mAP50_95`.

Mỗi mô hình có **năm cấu hình Linear** tại `0.0003`, `0.000667`, `0.001`, `0.0013`, `0.002` và **hai cấu hình Cosine** tại `0.000667`, `0.001`.

**Rất quan trọng:** Đây là **bảng tổng hợp của nhiều lần khảo sát**, không phải bằng chứng rằng bốn notebook Linear/Cosine đang lưu sử dụng LR khác nhau. Trong **cả bốn notebook đó, `lr0=0.000667`**. Tên `lr0_000667` trong notebook cũng phản ánh đúng giá trị này.

## So sánh tại LR ban đầu `0.000667`

| Mô hình | Scheduler | Precision | Recall | mAP50 | mAP50–95 |
|---|---|---:|---:|---:|---:|
| YOLO26n | Linear | 0.659 | 0.593 | 0.639 | **0.325** |
| YOLO26n | Cosine | 0.658 | 0.601 | 0.633 | 0.323 |
| YOLO26s | Linear | 0.685 | 0.613 | 0.662 | **0.339** |
| YOLO26s | Cosine | 0.717 | 0.594 | 0.666 | 0.338 |

Theo mAP50–95 trên Validation của **bộ kết quả đang công bố**, nhóm chọn Linear tại LR `0.000667` cho mỗi mô hình. Các giá trị trong CSV đã làm tròn đến ba chữ số sau dấu thập phân ở nhóm chỉ số.

Không sửa lại các dòng khảo sát LR khác thành `0.000667` và không thay thế bằng kết quả Test; Validation dùng để lựa chọn cấu hình, Test chỉ để đánh giá cuối cùng.
