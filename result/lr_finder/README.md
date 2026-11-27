# LR Finder — Dữ liệu từng bước và hình vẽ

| Tệp | Nội dung |
|---|---|
| `lr_finder_YOLO26n.png` | Đồ thị LR Range Test YOLO26n |
| `lr_finder_YOLO26s.png` | Đồ thị LR Range Test YOLO26s |
| `lr_finder_step_results_YOLO26n.txt` | 100 optimizer steps của YOLO26n |
| `lr_finder_step_results_YOLO26s.txt` | 100 optimizer steps của YOLO26s |

Hai tệp `.txt` **thực chất là CSV**, với header:

```text
optimizer_step,lr,loss,ema_loss
```

- `optimizer_step`: thứ tự cập nhật optimizer (không nhất thiết là số mini-batch).
- `lr`: Learning Rate tại bước tương ứng.
- `loss`: Training Loss quan sát được.
- `ema_loss`: Loss làm mượt bằng Exponential Moving Average.

LR được quét tăng theo cấp số nhân từ `1e-5` đến `3e-2` (tối đa 100 bước) nhằm xác định **vùng LR đáng khảo sát**, không tự động chứng minh một LR duy nhất là tốt nhất. Kết quả chính thức được so sánh tiếp trên Validation tại [`../hyperparameter_tuning/`](../hyperparameter_tuning/).

Đọc dữ liệu bằng Pandas dù tên tệp là `.txt`:

```python
import pandas as pd
n = pd.read_csv('lr_finder_step_results_YOLO26n.txt')
s = pd.read_csv('lr_finder_step_results_YOLO26s.txt')
```

Các notebook tạo ra kết quả: [`../../training/lr-finder-yolo26n.ipynb`](../../training/lr-finder-yolo26n.ipynb) và [`../../training/lr-finder-yolo26s.ipynb`](../../training/lr-finder-yolo26s.ipynb).
