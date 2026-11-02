# Font dùng cho xuất PDF

| File | Nguồn | Giấy phép |
|---|---|---|
| `NotoSans-Regular.ttf` | [Noto Fonts](https://github.com/notofonts/notofonts.github.io) | SIL Open Font License 1.1 |
| `NotoSans-Bold.ttf` | [Noto Fonts](https://github.com/notofonts/notofonts.github.io) | SIL Open Font License 1.1 |

## Vì sao phải nhúng font?

Thư viện OpenPDF chỉ có sẵn 14 font cơ bản (Helvetica, Times...) và các font này
**không chứa ký tự tiếng Việt có dấu**. Nếu không nhúng font, mọi chữ như "Phiếu nhập
kho" sẽ in ra thành "Phi?u nh?p kho" hoặc mất dấu hoàn toàn.

Noto Sans phủ đầy đủ bảng chữ Latin mở rộng, bao gồm toàn bộ nguyên âm có dấu của
tiếng Việt (ă â ê ô ơ ư và các dấu thanh). Lớp `PdfFontProvider` nạp hai file này với
chế độ `IDENTITY_H` (mã hóa Unicode 2 byte) để hiển thị đúng.
