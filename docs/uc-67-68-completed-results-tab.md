# Đặc tả yêu cầu: Bổ sung Tab "Đã hoàn thành" trên màn hình Kết quả cận lâm sàng (UC-67, UC-68)

**Trạng thái:** Đề xuất đặc tả  
**Vai trò thực hiện:** Kỹ thuật viên cận lâm sàng (`TECHNICIAN`)  
**Màn hình liên quan:** Danh sách kết quả cận lâm sàng (`/technician/results` — `ParaclinicalResultListPage.jsx`)  
**Tài liệu tham chiếu:** UC-67 (Nhập & gửi kết quả CLS), UC-68 (In phiếu kết quả CLS), `permission-matrix.md`

---

## 1. Mục tiêu & Cấu trúc Tab

Mở rộng màn hình **Danh sách chờ nhập kết quả** để KTV có thể tra cứu lại các ca đã hoàn thành tại phòng mình phụ trách và hỗ trợ in lại phiếu kết quả (bản sao) khi bệnh nhân quay lại xin cấp lại.

Màn hình gồm **2 tab**:
- **Tab 1: Chờ nhập kết quả (mặc định):** Giữ nguyên toàn bộ logic, bộ lọc và thao tác hiện tại (lọc các ca trạng thái `PENDING_RESULT` tại phòng).
- **Tab 2: Đã hoàn thành:** Lọc và hiển thị các chỉ định dịch vụ có trạng thái `COMPLETED` thuộc phòng KTV đang phụ trách (chỉ lấy dữ liệu trong phòng đó, không lấy chéo sang phòng khác).

---

## 2. Chi tiết Giao diện & Bộ lọc của Tab "Đã hoàn thành"

### 2.1. Thanh tìm kiếm & Bộ lọc

| Thành phần | Mô tả & Hành vi |
| --- | --- |
| **Ô tìm kiếm** | Tìm kiếm không phân biệt hoa/thường và hỗ trợ bỏ dấu tiếng Việt theo các trường: **Mã BN**, **Mã phiếu chỉ định**, **Họ tên bệnh nhân**, **CCCD**, **Số thẻ BHYT** hoặc **Tên dịch vụ**. |
| **Khoảng thời gian** | - Mặc định: **Hôm nay** (tính theo múi giờ `Asia/Ho_Chi_Minh`).<br>- Cung cấp các nút chọn nhanh: **Hôm nay** / **7 ngày qua** / **Chọn ngày** (`Từ ngày` – `Đến ngày`) để tra cứu các ca cũ khi bệnh nhân quay lại xin in lại phiếu. |
| **Phòng khám chỉ định** | Dropdown lọc theo phòng khám lâm sàng đã gửi chỉ định (tùy chọn, mặc định: `Tất cả phòng khám`). |
| **KTV thực hiện** | Dropdown gọn giao diện gồm các lựa chọn:<br>- **Tất cả KTV trong phòng** *(Mặc định)*<br>- **`<Họ tên> (Tôi)`** *(Đưa tài khoản KTV đang đăng nhập lên đầu danh sách ngay dưới mục Tất cả)*<br>- Các KTV khác đang thuộc phòng<br>- *(Mở rộng)* Các KTV cũ từng có kết quả trong khoảng thời gian đang lọc nếu có lịch sử thay đổi nhân sự phòng. |

### 2.2. Bảng danh sách kết quả đã hoàn thành

- **Thứ tự sắp xếp mặc định:** Theo **Thời gian gửi KQ mới nhất lên đầu** (`submittedAt DESC`).
- **Phân trang:** Áp dụng phân trang chuẩn như các danh sách tra cứu của hệ thống.

| Cột hiển thị | Nội dung |
| --- | --- |
| **Mã phiếu / Mã BN** | Mã dòng chỉ định (`service_order_code`) và Mã bệnh nhân. |
| **Bệnh nhân** | Họ tên, Năm sinh, Giới tính, Đối tượng (`BHYT` / `Dịch vụ`). |
| **Dịch vụ** | Tên dịch vụ cận lâm sàng đã thực hiện. |
| **KTV thực hiện** | Họ tên KTV đã thực hiện và gửi kết quả. |
| **Thời gian gửi KQ** | Thời điểm gửi kết quả định dạng `dd/MM/yyyy HH:mm` (giờ `Asia/Ho_Chi_Minh`). |
| **Thao tác** | Gồm 2 hành động:<br>1. **Xem kết quả**<br>2. **In lại (bản sao)** |

---

## 3. Quy tắc phân quyền thao tác (Backend & Frontend)

### 3.1. Xem kết quả (`Read-only`)
- **Phạm vi:** Mọi KTV thuộc phòng đều có quyền bấm **Xem kết quả** đối với các ca `COMPLETED` của phòng mình.
- **Giao diện chi tiết:**
  - Mở ở chế độ **chỉ đọc (Read-only)**.
  - Hiển thị thông báo **`MSG57`** (kết quả đã gửi và khóa, không thể chỉnh sửa).
  - **Ẩn** hoàn toàn các nút **Lưu nháp** và **Gửi kết quả**.

### 3.2. In lại (bản sao)
- **Điều kiện chủ sở hữu (UC-68 EF-02):**
  - Chỉ hiển thị / kích hoạt nút **In lại (bản sao)** cho **chính KTV đã thực hiện ca đó** (`performing_technician_id == currentUserId`).
  - Đối với các ca do **KTV khác trong cùng phòng** thực hiện (ví dụ: KTV ca chiều xem lại ca của KTV ca sáng): nút **In lại** bị ẩn hoặc vô hiệu hóa (`disabled`) kèm tooltip: **`"Chỉ người thực hiện dịch vụ mới in phiếu kết quả"`**.
  - **Không ràng buộc ca trực (Ngoài ca trực):** KTV ca sáng đăng nhập vào buổi chiều vẫn được phép xem và bấm **In lại (bản sao)** cho các ca do chính họ đã hoàn thành (không bị chặn bởi trạng thái hết ca trực, miễn là tài khoản vẫn `Active` và thuộc vai trò `TECHNICIAN`).
- **Hành vi khi in lại (UC-68 AF-01):**
  - Mẫu phiếu in hiển thị rõ chữ **`"BẢN SAO"`** (do đã từng in hoặc là bản in lại sau khi hoàn thành).
  - Hệ thống bắt buộc ghi nhận nhật ký kiểm toán vào bảng `audit_logs` với `action = 'PRINT'` gắn với dòng chỉ định (`paraclinical_order_details`).
  - Backend phải kiểm tra lại quyền `performing_technician_id == currentUserId` trước khi cho phép chuẩn bị dữ liệu in / ghi log (không chỉ chặn ở Frontend).

---

## 4. Yêu cầu kỹ thuật & Kiểm thử (Checklist triển khai)

### Backend
- [ ] Bổ sung API lấy danh sách kết quả đã hoàn thành của phòng (hoặc mở rộng endpoint `/api/v1/technician/pending-results` / thêm endpoint `/api/v1/technician/completed-results`) nhận các tham số: `roomId`, `search`, `fromDate`, `toDate`, `doctorRoom`, `performerId`, phân trang `page`, `size`.
- [ ] Giới hạn truy vấn trong phạm vi `roomId` mà KTV hiện tại được phân công / phụ trách, chỉ lấy `status = COMPLETED`, sắp xếp `submittedAt DESC`.
- [ ] Trả về danh sách `performers` (KTV thực hiện) xuất hiện trong khoảng thời gian lọc hoặc thuộc phòng để đổ vào dropdown lọc.
- [ ] Ở API xem chi tiết kết quả & in lại (UC-68):
  - Cho phép KTV cùng phòng xem read-only.
  - Chỉ cho phép `performing_technician_id == currentUserId` thực hiện lệnh in lại (`POST .../print`), bỏ qua kiểm tra đang trong ca trực đối với hành động xem/in lại của chính người thực hiện; từ chối nếu khác người thực hiện (`UC-68 EF-02`).
  - Ghi `audit_logs` với `action = 'PRINT'` mỗi lần in lại.

### Frontend
- [ ] Thêm thanh chuyển tab **Chờ nhập kết quả** (mặc định) và **Đã hoàn thành** trên `ParaclinicalResultListPage.jsx`.
- [ ] Xây dựng bộ lọc cho Tab **Đã hoàn thành**: ô tìm kiếm, chọn nhanh khoảng ngày (`Hôm nay`, `7 ngày qua`, `Chọn ngày`), dropdown phòng khám chỉ định, dropdown `KTV thực hiện` (đưa `(Tôi)` lên đầu).
- [ ] Bảng hiển thị đầy đủ 6 cột, định dạng `dd/MM/yyyy HH:mm`.
- [ ] Xử lý nút **Xem kết quả** (chế độ chỉ đọc + `MSG57`) và nút **In lại (bản sao)** (chỉ bật khi `performerId === currentUserId`, ngược lại disabled kèm tooltip `"Chỉ người thực hiện dịch vụ mới in phiếu kết quả"`).
- [ ] Bổ sung unit test cho Frontend (lọc, phân quyền nút In lại, hiển thị tab) và Backend (lọc phòng, lọc ngày, lọc KTV, chặn KTV khác in lại, ghi audit `PRINT`, cho phép in lại ngoài ca trực).
