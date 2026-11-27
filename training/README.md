# Training — LR Finder và fine-tuning YOLO26

Thư mục chứa **sáu notebook Kaggle**: hai notebook tìm khoảng Learning Rate và bốn notebook so sánh Linear/Cosine cho YOLO26n, YOLO26s.

## Danh sách notebook và LR trong code

| Notebook | Mô hình | Vai trò | `lr0` ở bước fine-tuning |
|---|---|---|---:|
| `lr-finder-yolo26n.ipynb` | YOLO26n | Quét LR tăng dần | LR thay đổi theo bước; không dùng LR cố định cuối |
| `lr-finder-yolo26s.ipynb` | YOLO26s | Quét LR tăng dần | LR thay đổi theo bước; không dùng LR cố định cuối |
| `yolo26n_linear_lr0_000667.ipynb` | YOLO26n | Linear Scheduler | **0.000667** |
| `yolo26n_cosine_lr0_000667.ipynb` | YOLO26n | Cosine Scheduler | **0.000667** |
| `yolo26s_linear_lr0_000667.ipynb` | YOLO26s | Linear Scheduler | **0.000667** |
| `yolo26s_cosine_lr0_000667.ipynb` | YOLO26s | Cosine Scheduler | **0.000667** |

**Kết luận đã kiểm tra từ bốn notebook:** cả Linear và Cosine, trên cả hai mô hình, **đều dùng cùng LR ban đầu `lr0=0.000667`**. Khác biệt cần so sánh là lịch giảm LR (`cos_lr=True` ở Cosine; Linear không bật chế độ Cosine). Không ghi `0.00067` cho riêng Cosine hay Linear.

## Cấu hình chung trong bốn notebook fine-tuning

- Trọng số đầu vào: `yolo26n.pt` hoặc `yolo26s.pt` pretrained.
- Tập dữ liệu: RDD2022 đã chuẩn hóa thành 3 lớp; `data.yaml` trong đường dẫn Kaggle của notebook.
- `imgsz=640`, `epochs=100`, `patience=15`, `optimizer='AdamW'`, `seed=42`.
- Batch trong notebook: `64` khi có từ hai GPU, còn một GPU là `16`.
- Chỉ so sánh kết quả huấn luyện trên **Validation**; không dùng Test để chọn LR hoặc Scheduler.

## LR Finder và khảo sát LR là hai giai đoạn riêng

LR Finder quét từ `1e-5` đến `3e-2`, tối đa 100 optimizer steps; kết quả ở [`../result/lr_finder/`](../result/lr_finder/). Tên tệp `.txt` lưu theo định dạng CSV (`optimizer_step,lr,loss,ema_loss`).

Bảng tổng hợp [`../result/hyperparameter_tuning/hyperparameter_tuning.csv`](../result/hyperparameter_tuning/hyperparameter_tuning.csv) có **14 dòng**: mỗi mô hình gồm năm lần khảo sát LR với Linear và hai lần so sánh Cosine (LR `0.001` và `0.000667`). Những cấu hình khác trong bảng là **kết quả khảo sát**, không đồng nghĩa phải có notebook riêng của từng lần chạy trong thư mục này.

### Cấu hình được chọn

| Mô hình | Scheduler | Learning Rate | mAP50–95 trên Validation (làm tròn) |
|---|---|---:|---:|
| YOLO26n | **Linear** | **0.000667** | 0.325 |
| YOLO26s | **Linear** | **0.000667** | 0.339 |

Đây là các checkpoint được dùng cho bộ kết quả Test chính thức; hai tệp trọng số có ở [`../models/`](../models/).

## Lịch sử huấn luyện được lưu

| Mô hình | Lịch sử | Số epoch có trong CSV | Epoch tốt nhất theo mAP50–95 Validation |
|---|---|---:|---:|
| YOLO26n | [`../result/YOLO26n/training_curve/results.csv`](../result/YOLO26n/training_curve/results.csv) | 80 | 65 |
| YOLO26s | [`../result/YOLO26s/training_curve/results.csv`](../result/YOLO26s/training_curve/results.csv) | 72 | 57 |

## Chạy lại

1. Gắn dữ liệu Kaggle với cấu trúc Train/Validation/Test và `data.yaml` tương ứng; kiểm tra đường dẫn trong notebook.
2. Chạy LR Finder nếu cần khảo sát dải LR. **Không dùng mô hình đã chạy LR Finder để tiếp tục huấn luyện chính.**
3. Chạy notebook tương ứng từ pretrained, kiểm tra thiết lập GPU/batch thực tế.
4. So sánh chỉ số trên Validation, lưu checkpoint tốt nhất và kết quả CSV/PNG.
5. Chạy [`../evaluation/Test_evaluation.ipynb`](../evaluation/Test_evaluation.ipynb) với hai checkpoint đã chọn để đánh giá Test.

Lưu ý: đường dẫn `best.pt` và output trong notebook là đường dẫn ở Kaggle; không mặc định những đường dẫn đó có trên máy cá nhân.
