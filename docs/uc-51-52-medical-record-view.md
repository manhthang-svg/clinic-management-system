# UC-51, UC-52 — Xem danh sách và chi tiết hồ sơ bệnh án

**Trạng thái:** Bản nháp, chờ nhóm duyệt. Viết ngày 18/11/2026. Các quyết định ở mục [Quyết định](#quyết-định) được chốt theo đề xuất ngày 18/11/2026 nhưng chưa có biên bản của nhóm. Đang triển khai theo từng đợt, xem [Tiến độ triển khai](#tiến-độ-triển-khai). Bảng [Chênh lệch với code hiện tại](#chênh-lệch-với-code-hiện-tại) mô tả code trước khi triển khai.

Đặc tả này viết từ `Business_Rules_v7.xlsx` (sheet quy tắc BR-xx và sheet thông báo MSG-xx). Report 3 §10.1, §10.2 chỉ được dùng ở những chỗ khớp với BR v7. Chỗ hai nguồn khác nhau thì BR v7 thắng, và chênh lệch được ghi ở mục [Chênh lệch so với nguồn](#chênh-lệch-so-với-nguồn). Lựa chọn không có nguồn nào quy định được đánh dấu **ASSUMPTION** hoặc **TBD**.

## Nguồn

**Tài liệu dự án**

- `Business_Rules_v7.xlsx`:
  - quy tắc BR-22, BR-29, BR-32, BR-35, BR-42, BR-120;
  - thông báo MSG01, MSG08, MSG12, MSG13, MSG39, MSG50, MSG80.
- Report 3 §10.1 (UC-51), §10.2 (UC-52).
- [`uc-53-export-medical-record.md`](uc-53-export-medical-record.md): BR-53-09 (API UC-51/52 chỉ dành cho Bác sĩ).
- [`uc-55-view-paraclinical-results.md`](uc-55-view-paraclinical-results.md): BR-55-02 (không hiện kết quả nháp), TBD về tải ảnh kết quả.
- [`state-transitions.md`](state-transitions.md): trạng thái lượt khám và hồ sơ.
- [`permission-matrix.md`](permission-matrix.md), [`security.md`](security.md), [`api-conventions.md`](api-conventions.md).
- Schema: `audit_logs` (V10), `medical_records`, `visits`, `imaging_result_images`.

**Văn bản pháp lý**

- CV 365/TTYQG-GPQLCL mục III.1 điểm đ (ghi vết), điểm g (phân quyền, chống truy cập trái phép) ([`reference/CV-365-2025-TTYQG.md`](reference/CV-365-2025-TTYQG.md)).
- Luật KBCB 15/2023/QH15 Điều 69 khoản 3, 4, 5 ([`reference/Luat-15-2023-QH15-Kham-benh-chua-benh.md`](reference/Luat-15-2023-QH15-Kham-benh-chua-benh.md)).

## Phạm vi

| Use case | Tên | Phạm vi |
| --- | --- | --- |
| UC-51 | View List Medical Record | Bác sĩ tìm bệnh nhân (cấp 1), chọn một bệnh nhân để xem danh sách lượt khám (cấp 2), chọn một lượt để sang UC-52. |
| UC-52 | View Medical Record Detail | Bác sĩ xem hồ sơ của một lượt khám ở chế độ chỉ đọc, chuyển giữa các lượt bằng timeline, mở tệp kết quả CLS, và từ đây xuất PDF (UC-53). |

Ngoài phạm vi:

- in lại bản sao kết luận, đơn thuốc, giấy hẹn từ hồ sơ đã đóng (AF-01 của UC-57, UC-60, UC-62): **TBD**, cần đặc tả riêng (quyết định 10);
- xuất PDF (UC-53), xuất XML/JSON (BR-43, UC-80);
- chỉnh sửa hồ sơ (UC-47, UC-56). UC-52 chỉ dẫn sang màn hình đó;
- màn hình xem audit log cho Admin (BR-42 "Admin xem được"): `security.md` hiện chỉ cho đọc audit qua UC-06, nên đây là **TBD**.

## Cơ sở pháp lý

| Yêu cầu | Văn bản | Cách đáp ứng |
| --- | --- | --- |
| Phân quyền theo vai trò, chống truy cập trái phép | CV 365 III.1.g; BR-120 | Chỉ vai trò `DOCTOR` gọi được các API này (BR-51-01). Tệp kết quả chỉ tải qua backend sau khi kiểm tra quyền (BR-52-06) |
| Ghi vết mọi giao dịch của người dùng | CV 365 III.1.đ; BR-42 | Mỗi lần tìm kiếm, xem danh sách lượt, xem chi tiết, tải tệp đều ghi một dòng `audit_logs` (BR-51-07) |
| Khai thác hồ sơ đang điều trị và hồ sơ đã lưu trữ | Luật KBCB Điều 69 khoản 3, 4 | Bác sĩ của cơ sở được xem hồ sơ của mọi bệnh nhân (BR-35). **ASSUMPTION** như UC-53: quy chế hồ sơ điện tử của phòng khám cho phép trước việc này |
| Người khai thác giữ bí mật, chỉ dùng đúng mục đích | Luật KBCB Điều 69 khoản 5 | Dữ liệu tối thiểu ở danh sách (BR-51-05), audit không chứa dữ liệu định danh hay nội dung y khoa (BR-51-07) |

## Quy tắc nghiệp vụ

### Chung

**BR-51-01 Ai được dùng.** Chỉ vai trò `DOCTOR`, không đang ở trạng thái bắt buộc đổi mật khẩu. Áp dụng với hồ sơ của mọi bệnh nhân (BR-35), không chỉ lượt của bác sĩ đó. Vai trò khác nhận 403 (MSG13). Admin không có quyền (BR-120, BR-53-09). Lễ tân không có quyền: OQ-09 được chốt là **không** (quyết định 1), lễ tân xem lịch sử lượt khám qua UC-36.

**BR-51-02 Không đổi dữ liệu.** UC-51 và UC-52 không thay đổi dữ liệu nghiệp vụ. Ghi audit (BR-51-07) không bị coi là thay đổi dữ liệu.

**BR-51-03 Dữ liệu trong URL.** Không đưa mã bệnh nhân, họ tên, CCCD, số điện thoại, từ khóa tìm kiếm hay nội dung y khoa vào URL của trình duyệt (`security.md`). Bộ lọc giữ trong state của trang, và được gửi lên backend dưới dạng query string của request API. **ASSUMPTION:** UUID của bệnh nhân, lượt khám, hồ sơ (định danh kỹ thuật, không có nghĩa) được phép nằm trong đường dẫn trang và API.

### UC-51 — Danh sách

**BR-51-04 Tìm kiếm và lọc.**

- **Từ khóa:**
  - Sau khi cắt khoảng trắng, từ khóa dài 2–100 ký tự. Ngắn hơn 2 ký tự thì báo MSG132; dài hơn 100 ký tự thì báo MSG08. Để trống thì không lọc theo từ khóa.
  - Khớp một phần (chứa chuỗi), không phân biệt hoa thường, với: mã bệnh nhân, số CCCD, số điện thoại, họ tên. Riêng họ tên so sánh sau khi bỏ dấu.
  - Ngưỡng 2 ký tự là **ASSUMPTION**, nhằm hạn chế dò dữ liệu bằng truy vấn quá rộng.
- **Ngày khám Từ – Đến:**
  - Lọc theo ngày khám của lượt (`visits.queue_date`), tính cả hai đầu mút.
  - Từ ngày sau Đến ngày thì báo MSG50.
  - Có thể chỉ nhập một đầu.
- **Phòng:** phòng khám của lượt (`visits.room_id`). Danh sách chọn gồm mọi phòng khám lâm sàng, kể cả phòng đã ngừng hoạt động. **ASSUMPTION:** người dùng phải tìm được hồ sơ cũ của phòng đã đóng.
- **Chẩn đoán (ICD-10):**
  - Lọc theo chẩn đoán cuối trong kết luận của lượt (`conclusions.maicd`, `conclusions.tenicd`), khớp một phần theo mã hoặc theo tên (quyết định 6).
  - Lượt chưa có kết luận không bao giờ khớp.
- **Trạng thái** (quyết định 4):

  | Lựa chọn | Trạng thái lượt khám |
  | --- | --- |
  | Tất cả | Không lọc |
  | Đang hoạt động | `WAITING_FEE_PAYMENT`, `WAITING`, `IN_EXAMINATION`, `WAITING_PARACLINICAL`, `WAITING_CONCLUSION` (kể cả lượt đang bị bỏ qua) |
  | Hoàn thành | `COMPLETED` |
  | Bỏ khám | `ABANDONED` |
  | Đã hủy | `CANCELLED` |

  API nhận một giá trị trong allow-list `ACTIVE`, `COMPLETED`, `ABANDONED`, `CANCELLED`. Giá trị khác bị từ chối với 400.
- **Cách kết hợp:**
  - Một bệnh nhân khớp khi từ khóa khớp với bệnh nhân **và** có ít nhất một lượt khám thỏa **cùng lúc** mọi bộ lọc theo lượt (ngày, phòng, ICD, trạng thái).
  - Khi không có bộ lọc theo lượt, chỉ cần bệnh nhân có ít nhất một lượt khám.

**BR-51-05 Danh sách bệnh nhân (cấp 1).**

- **Cột hiển thị:** mã bệnh nhân, họ tên, ngày sinh, giới tính, số lượt khám, ngày khám gần nhất, chẩn đoán gần nhất (mã và tên ICD-10).
  - **Không** trả số CCCD và số điện thoại, dù được dùng để tìm. Report 3 bước 4 không liệt kê hai trường này; đây là tối thiểu hóa dữ liệu theo `security.md` (**ASSUMPTION**).
- **Chỉ hiện bệnh nhân có ít nhất một lượt khám** (quyết định 7).
- **Số lượt khám:** tổng số lượt của bệnh nhân, kể cả lượt Đã hủy, không phụ thuộc bộ lọc (**ASSUMPTION**, cho khớp với danh sách cấp 2 vốn hiện mọi lượt).
- **Ngày khám gần nhất:** `queue_date` của lượt mới nhất.
- **Chẩn đoán gần nhất:** chẩn đoán cuối của lượt mới nhất *đã có kết luận*. Không có lượt nào có kết luận thì để trống (**ASSUMPTION**).
- **Sắp xếp:** ngày khám gần nhất giảm dần, rồi họ tên tăng dần, rồi ID bệnh nhân (để thứ tự ổn định khi phân trang).
- **Phân trang** theo `api-conventions.md`: `page` mặc định 0; `size` mặc định 20, tối đa 100; `size` > 100 hoặc < 1 bị từ chối với 400.
- **Không có kết quả:** hiện MSG01 (EF-01).

**BR-51-06 Danh sách lượt khám (cấp 2).**

- **Phạm vi:** mọi lượt của bệnh nhân, kể cả lượt Đã hủy, mới nhất trước (theo thời điểm vào viện, rồi ID). Bộ lọc của cấp 1 không áp dụng ở đây (**ASSUMPTION**: Report 3 bước 6 nói "the list of visits of the patient"). Không phân trang (**ASSUMPTION**: số lượt của một bệnh nhân nhỏ).
- **Cột hiển thị:** số vào viện (mã đăng ký), ngày khám, phòng, bác sĩ (học hàm + họ tên), loại lượt (BHYT / Dịch vụ), chẩn đoán cuối (mã + tên ICD-10), trạng thái.
- **Nhãn trạng thái:**
  - Lấy theo [`state-transitions.md`](state-transitions.md) và `VISIT_STATUS_LABELS` của frontend, mỗi trạng thái một nhãn riêng; không gộp thành "Đang hoạt động".
  - Lượt có cờ bỏ qua hiện nhãn "Đã bỏ qua".
  - Tông màu badge là **TBD** (CLAUDE.md).
- **Lượt chưa có hồ sơ bệnh án** (ví dụ lượt Đã hủy trước khi trả phí): vẫn được liệt kê nhưng không mở được UC-52, và hiện nhãn "Chưa có hồ sơ" (**ASSUMPTION**).
- **AF-01:** từ màn hình khám (UC-47), nút "Lịch sử" mở thẳng cấp 2 của bệnh nhân đang khám. Màn hình phải lấy tiêu đề (mã BN, họ tên) từ dữ liệu thật trả về trong response, không dùng chữ giữ chỗ.
- **Bệnh nhân không tồn tại:** 404, hiện MSG39 ("Không tìm thấy bệnh nhân.").

**BR-51-07 Ghi vết (BR-42, CV 365 III.1.đ).**

- Mỗi thao tác thành công ghi đúng **một** dòng `audit_logs` với `action = VIEW_RECORD` và `outcome = SUCCESS`. Dòng audit được ghi trong cùng transaction với thao tác đọc. Nếu không ghi được audit thì request thất bại (MSG80) và không trả dữ liệu.

  | Thao tác | `entity_type` | `entity_id` | `sovaovien` | `new_value` |
  | --- | --- | --- | --- | --- |
  | Tìm bệnh nhân (cấp 1) | `MEDICAL_RECORD_SEARCH` | trống | trống | `{ "filters": [tên các bộ lọc đã dùng], "resultCount": n }` |
  | Xem danh sách lượt (cấp 2) | `PATIENT` | ID bệnh nhân | trống | `{ "visitCount": n }` |
  | Xem chi tiết (UC-52) | `MEDICAL_RECORD` | ID hồ sơ | số vào viện của lượt | `{ "openedBy": "RECORD_ID" \| "VISIT_ID" }` |
  | Tải tệp kết quả | `IMAGING_RESULT_IMAGE` | ID tệp | số vào viện của lượt | `{ "medicalRecordId": "…" }` |

- `filters` chỉ chứa **tên** bộ lọc (`keyword`, `from`, `to`, `roomId`, `icd10`, `status`), không chứa giá trị (quyết định 13).
- Audit không bao giờ chứa: từ khóa, CCCD, số điện thoại, họ tên, mã bệnh nhân, tên tệp gốc, chẩn đoán hay bất kỳ nội dung y khoa nào (`security.md`, Luật KBCB Điều 69 khoản 5).
- Cột `sovaovien` chứa **số vào viện của lượt khám** (`visits.sovaovien`), không chứa số bệnh án.
- Request bị từ chối (401, 403) không ghi `audit_logs`. Có ghi sự kiện bảo mật cho truy cập bị từ chối hay không là **TBD**.
- Phân trang hoặc đổi bộ lọc là một lần tìm kiếm mới, nên ghi một dòng mới.
- Thời điểm lấy từ `Clock` của ứng dụng.

### UC-52 — Chi tiết

**BR-52-01 Mở hồ sơ.**

- Mở theo ID hồ sơ hoặc theo ID lượt khám.
- Hồ sơ không tồn tại, hoặc lượt chưa có hồ sơ: 404 `MEDICAL_RECORD_NOT_FOUND`, hiện MSG39 ("Không tìm thấy hồ sơ bệnh án.") (EF-01).

**BR-52-02 Nội dung.**

- **Đầu trang (bước 1):**
  - mã bệnh nhân, họ tên, tuổi (tính theo ngày hiện tại, múi giờ Asia/Ho_Chi_Minh), giới tính;
  - **dị ứng thuốc** trong nhãn đỏ, luôn hiển thị; không có thì ghi "Chưa ghi nhận dị ứng thuốc";
  - tiền sử bệnh.
- **Lượt khám đang chọn (bước 2):**
  - số vào viện, ngày khám, phòng, bác sĩ, loại lượt (và số thẻ BHYT nếu là lượt BHYT);
  - ưu tiên và lý do ưu tiên, lý do vào viện và triệu chứng ban đầu;
  - lịch sử chuyển phòng (nếu có), sinh hiệu lúc tiếp nhận (kèm BMI tính từ cân nặng và chiều cao);
  - bệnh sử, khám toàn thân, khám bộ phận, chẩn đoán sơ bộ và các chẩn đoán ICD-10 lúc khám;
  - chỉ định CLS kèm kết quả và tệp (BR-52-05);
  - kết luận (chẩn đoán cuối, hướng điều trị, lời dặn, kết quả điều trị);
  - đơn thuốc (BR-52-07), giấy hẹn tái khám;
  - trạng thái lượt và hồ sơ, thời điểm đóng hồ sơ.
- **API trả mã, frontend hiện nhãn:**
  - Backend trả mã (giới tính `1/2/3`, enum trạng thái, enum loại lượt, cờ bất thường của xét nghiệm), không trả chuỗi tiếng Việt.
  - Frontend đổi mã sang nhãn bằng các hằng số dùng chung (`PATIENT_GENDER_LABELS`, `VISIT_STATUS_LABELS`, …).
- **Hồ sơ đã mở khóa lại** (`medical_records.unlocked_at` có giá trị): hiện dòng thông báo "Hồ sơ đã được mở khóa lại lúc {thời điểm}" (**ASSUMPTION**, quyết định 11). Lý do mở khóa không hiển thị cho đến khi luồng mở khóa được đặc tả (**TBD**).

**BR-52-03 Chỉ đọc và nút Sửa (BR-35, AF-01).**

- Mọi nội dung UC-52 là chỉ đọc.
- Nút "Sửa" chỉ hiện khi đủ **cả ba** điều kiện (quyết định 2):
  - người xem là bác sĩ của lượt (`visits.doctor_account_id`);
  - lượt chưa kết thúc (không phải `COMPLETED`, `ABANDONED`, `CANCELLED`);
  - hồ sơ ở trạng thái `OPEN`.
- Backend tính cờ `editable`; frontend chỉ đọc cờ này.
- Nút Sửa mở màn hình khám (UC-47 / UC-56). Quyền sửa vẫn do các API của UC-47 / UC-56 kiểm tra. Cờ `editable` chỉ để ẩn hoặc hiện nút.

**BR-52-04 Lượt Bỏ khám (AF-02, BR-22).**

- Hiện những dữ liệu đã ghi trước khi lượt chuyển Bỏ khám, kèm nhãn "Bỏ khám – không có kết luận".
- Các phần chưa có dữ liệu ghi "Chưa ghi nhận", không để trống mà không giải thích.

**BR-52-05 Chỉ định và kết quả CLS.**

- **Phạm vi:** liệt kê mọi chỉ định của hồ sơ, kể cả dịch vụ Đã hủy (mang nhãn trạng thái của dịch vụ), để bác sĩ thấy đủ lịch sử (BR-35).
- **Chỉ hiện kết quả đã gửi** (`SUBMITTED`) (quyết định 8; BR-29; BR-55-02). Kết quả nháp không được trả về API. Dịch vụ chưa có kết quả đã gửi ghi "Chưa có kết quả".
- **Kết quả xét nghiệm:** tên xét nghiệm, kết quả, khoảng tham chiếu, đơn vị, cờ bất thường.
- **Kết quả hình ảnh / thăm dò chức năng:** mô tả, kết luận, đề nghị, danh sách tệp (ID tệp, loại tệp, chú thích). Response **không** chứa địa chỉ lưu trữ của tệp (`imaging_result_images.hinhanh`).
- Trường `kqcls_linkhinhanh` (link hình ảnh, CV 365) có được hiển thị hay không là **TBD**, vì đó cũng là một địa chỉ tệp.

**BR-52-06 Tải tệp kết quả (bước 4, quyết định 9).**

- **Chỉ tải qua backend.** Mỗi lần mở tệp, trình duyệt gọi endpoint tải tệp của backend. Backend lần lượt:
  1. kiểm tra BR-51-01;
  2. kiểm tra tệp thuộc một kết quả **đã gửi** của một dịch vụ trong **chính hồ sơ** trên đường dẫn; nếu không thì trả 404 `RESULT_IMAGE_NOT_FOUND` (MSG39 "Không tìm thấy tệp kết quả.");
  3. đọc tệp từ kho lưu trữ, ghi audit (BR-51-07), rồi trả nội dung tệp.
- **Header phản hồi:**
  - `Content-Type` là `mime_type` đã lưu.
  - `Content-Disposition: inline` với tên tệp do hệ thống đặt, dạng `KQ-{số phiếu}-{thứ tự}.{đuôi}`. Không dùng tên tệp gốc, vì tên gốc có thể chứa thông tin cá nhân (**ASSUMPTION**).
  - `Cache-Control: no-store`.
- **Lỗi kho lưu trữ:** không đọc được tệp thì trả `FILE_STORAGE_UNAVAILABLE` (502), hoặc `FILE_STORAGE_NOT_CONFIGURED` (503) khi kho chưa được cấu hình; hiện MSG80 và quay lại bước 3 (EF-02). Hai mã này đã có từ UC-67, nên không thêm mã mới.
- **Phía frontend:** hiển thị tệp bằng object URL tạm (`URL.createObjectURL`) và thu hồi khi đóng xem trước. Không lưu tệp vào storage của trình duyệt.
- **Kho lưu trữ:**
  - Backend chỉ tải tệp từ host của kho đã cấu hình (Cloudinary), để chống SSRF. `ResultImageStorage` có thêm thao tác đọc tệp.
- **Giới hạn còn lại (TBD):**
  - Quyết định 16/11/2026 (ghi trong `CloudinaryResultImageStorage`) chọn *public delivery URL* với public id khó đoán. Quy tắc này ngăn địa chỉ tệp đến tay trình duyệt, nhưng ai đã có địa chỉ thì vẫn mở được tệp trực tiếp trên Cloudinary.
  - Muốn chặn hoàn toàn thì phải chuyển sang *authenticated delivery*, tức là thay đổi quyết định 16/11/2026, và cần nhóm quyết định lại.
- UC-55 có thể dùng lại cơ chế này cho TBD "image download" của nó, với phạm vi quyền của UC-55.

**BR-52-07 Đơn thuốc và in lại.**

- Hiện đơn thuốc (mã đơn, trạng thái liên thông, thuốc, cách dùng, lời dặn, ngày tái khám).
- Các nút in lại bản sao (kết luận, đơn thuốc, giấy hẹn) **bị ẩn** cho đến khi có đặc tả riêng (quyết định 10). UC-52 không dùng `window.print()` (BR-53-09).
- Nút "Xuất hồ sơ bệnh án (PDF)" theo UC-53 (BR-53-02).

**BR-52-08 Timeline lượt khám (bước 3).**

> **Cách hiển thị (19/11/2026):** danh sách gọn, mỗi lượt một dòng (ngày, số vào viện, phòng, chẩn đoán cuối, trạng thái bằng chữ thường), không dùng thẻ hay badge màu. Lượt đang xem có nền nhạt, vạch trái và chữ "Đang xem".

- Timeline liệt kê mọi lượt của bệnh nhân, cùng thứ tự và cùng quy tắc như BR-51-06, và đánh dấu lượt đang xem.
- Chọn một lượt khác thì mở UC-52 của lượt đó và ghi một dòng audit mới.
- Lượt chưa có hồ sơ không chọn được.
- Khi người dùng chuyển lượt nhanh, frontend hủy request cũ (`AbortController`) để kết quả của lượt trước không ghi đè lượt sau.

## Luồng

### UC-51

**Luồng chính**

1. Bác sĩ chọn "Hồ sơ bệnh án" trên menu.
2. Hệ thống hiện ô tìm kiếm (mã BN, CCCD, họ tên, số điện thoại) và các bộ lọc: Ngày khám Từ – Đến, Phòng, Chẩn đoán (ICD-10), Trạng thái (BR-51-04). Hệ thống tải trang đầu của danh sách bệnh nhân với bộ lọc trống.
3. Bác sĩ nhập từ khóa và/hoặc bộ lọc rồi bấm "Tìm kiếm". Nút hiện "Đang tìm…" trong lúc chờ.
4. Hệ thống kiểm tra BR-51-04, hiện danh sách bệnh nhân (BR-51-05) và ghi audit (BR-51-07).
5. Bác sĩ chọn một bệnh nhân.
6. Hệ thống hiện danh sách lượt khám của bệnh nhân (BR-51-06) và ghi audit.
7. Bác sĩ chọn một lượt có hồ sơ. Chuyển sang UC-52.

**Luồng thay thế**

- **AF-01 (bước 1): mở từ lượt đang khám.** Từ UC-47, bác sĩ bấm "Lịch sử". Hệ thống mở bước 6 cho bệnh nhân của lượt đó.
- **AF-02 (bước 3): xóa bộ lọc.** Bác sĩ bấm "Xóa bộ lọc". Hệ thống đặt lại bộ lọc và tải lại trang đầu (bước 4).

**Luồng ngoại lệ**

- **EF-01 (bước 4):** không có kết quả: MSG01. Quay lại bước 3.
- **EF-02 (bước 3–4):** bộ lọc sai: MSG50 (ngày), MSG132 hoặc MSG08 (từ khóa), hiện dưới trường tương ứng. Request không được gửi nếu frontend đã phát hiện lỗi.
- **EF-03 (bất kỳ bước nào):** không phải Bác sĩ: MSG13. Phiên hết hạn: MSG12. Lỗi hệ thống: MSG80. Use case kết thúc.
- **EF-04 (bước 6):** bệnh nhân không tồn tại: MSG39.

**Hậu điều kiện:** Không có dữ liệu nghiệp vụ nào bị thay đổi. Mỗi lần tìm kiếm và mỗi lần xem danh sách lượt có đúng một dòng audit.

### UC-52

**Tiền điều kiện:** Bác sĩ đã chọn một lượt ở UC-51, UC-34 hoặc từ UC-47.

**Luồng chính**

1. Hệ thống tải hồ sơ, ghi audit, hiện đầu trang (BR-52-02).
2. Hệ thống hiện lượt khám đang chọn (BR-52-02, BR-52-05, BR-52-07).
3. Bác sĩ có thể chuyển sang lượt khác bằng timeline (BR-52-08).
4. Bác sĩ bấm một tệp kết quả. Hệ thống kiểm tra quyền, ghi audit và hiện tệp (BR-52-06).
5. Hệ thống hiện thao tác "Xuất hồ sơ bệnh án (PDF)" (UC-53).

**Luồng thay thế**

- **AF-01 (bước 2): lượt hiện tại chưa xong của chính bác sĩ.** Hệ thống hiện nút "Sửa" (BR-52-03). Tiếp tục bước 3.
- **AF-02 (bước 2): lượt Bỏ khám.** Hệ thống hiện dữ liệu kèm nhãn "Bỏ khám – không có kết luận" (BR-52-04). Tiếp tục bước 3.

**Luồng ngoại lệ**

- **EF-01 (bước 1):** hồ sơ không tồn tại: MSG39. Use case kết thúc.
- **EF-02 (bước 4):** không tải được tệp: MSG80 (tệp không tồn tại hoặc không thuộc hồ sơ: MSG39). Quay lại bước 3.
- **EF-03 (bất kỳ bước nào):** phiên hết hạn: MSG12. Không phải Bác sĩ: MSG13. Use case kết thúc.

**Hậu điều kiện:** Hồ sơ được hiển thị ở chế độ chỉ đọc (trừ trường hợp AF-01). Mỗi lần xem chi tiết và mỗi lần tải tệp có đúng một dòng audit.

## API

Mọi endpoint yêu cầu `hasRole('DOCTOR') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')`. Response JSON dùng `ApiResponse`; danh sách phân trang dùng `PageResponse` (`api-conventions.md`).

| Method | Path | Hợp đồng |
| --- | --- | --- |
| `GET` | `/api/v1/medical-records/patients` | Query: `keyword`, `from`, `to` (ISO date), `roomId`, `icd10`, `status` (`ACTIVE` \| `COMPLETED` \| `ABANDONED` \| `CANCELLED`), `page`, `size`. Trả trang các mục BR-51-05 |
| `GET` | `/api/v1/medical-records/patients/{patientId}/visits` | Trả danh sách BR-51-06 (mỗi mục có `visitId`, `medicalRecordId` có thể null, `status`, `skipped`) |
| `GET` | `/api/v1/medical-records/{medicalRecordId}` | Trả chi tiết BR-52-02 |
| `GET` | `/api/v1/medical-records/by-visit/{visitId}` | Như trên, mở theo lượt khám |
| `GET` | `/api/v1/medical-records/{medicalRecordId}/result-files/{fileId}` | **Mới.** Trả nội dung tệp (BR-52-06), không bọc `ApiResponse` |
| — | `GET /api/v1/medical-records` (danh sách phẳng) | **Bỏ.** Không có trong UC-51, frontend không dùng, không ghi audit |
| — | `POST /api/v1/medical-records/paraclinical-files/{imageId}/access-log` | **Bỏ.** Việc ghi vết chuyển vào endpoint tải tệp |

| Mã lỗi | HTTP | Thông báo |
| --- | --- | --- |
| `REQUEST_VALIDATION_FAILED` (có sẵn) | 400 | MSG50 / MSG132 / MSG08; tên trường trong `details` |
| `PATIENT_NOT_FOUND` (có sẵn) | 404 | MSG39 |
| `MEDICAL_RECORD_NOT_FOUND` (**mới**) | 404 | MSG39 |
| `RESULT_IMAGE_NOT_FOUND` (có sẵn) | 404 | MSG39 |
| `FILE_STORAGE_UNAVAILABLE`, `FILE_STORAGE_NOT_CONFIGURED` (có sẵn, UC-67) | 502, 503 | MSG80 |
| `FORBIDDEN` (`ErrorCode.ACCESS_DENIED`), `UNAUTHORIZED` (có sẵn) | 403, 401 | MSG13, MSG12 |

Frontend ánh xạ `errorResponse.code` / `status` sang các thông báo trên và không bao giờ hiện nguyên `message` của backend (CLAUDE.md).

## Thông báo

| Mã | Nội dung | Nguồn |
| --- | --- | --- |
| MSG01 | Không tìm thấy kết quả. | BR v7 |
| MSG08 | Vượt quá độ dài tối đa {max_length} ký tự. (`max_length` = 100) | BR v7 |
| MSG12 | Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại. | BR v7 |
| MSG13 | Bạn không có quyền thực hiện thao tác này. | BR v7 |
| MSG39 | Không tìm thấy {record_type}. ("bệnh nhân", "hồ sơ bệnh án", "tệp kết quả") | BR v7 |
| MSG50 | Ngày bắt đầu không được sau ngày kết thúc. | BR v7 |
| MSG80 | Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên. | BR v7 |
| **MSG132** | Vui lòng nhập ít nhất 2 ký tự để tìm kiếm. | **Mới** (18/11/2026, **ASSUMPTION**). Người phụ trách cần bổ sung vào sheet thông báo |

## Schema và cấu hình

- **Không thay đổi schema.** `audit_logs` (V10) đã có `action = VIEW_RECORD`; `entity_id` và `sovaovien` cho phép null; `entity_type` là chuỗi tự do (≤ 50 ký tự).
- Không có property mới. Host được phép tải tệp lấy từ cấu hình Cloudinary hiện có.
- Không thêm dependency.

## Chênh lệch so với nguồn

| Nguồn ghi | Đặc tả này | Lý do |
| --- | --- | --- |
| R3 UC-52 EF-01: MSG41 | MSG39 | Trong BR v7, MSG41 là thông báo quy tắc mật khẩu |
| R3 UC-51 EF-03, UC-52 EF-02: MSG99 | MSG80 | Trong R3, MSG99 là "chưa được phân công phòng", không phải lỗi hệ thống; UC-53 cũng dùng MSG80 |
| R3 UC-51 bước 2: Trạng thái gồm Completed, Abandoned, Active | Thêm "Đã hủy" | `state-transitions.md`: Đã hủy là trạng thái kết thúc riêng (quyết định 4) |
| R3 UC-52 bước 4: "checks the Doctor's access before every download" | Tải qua backend, nhưng địa chỉ Cloudinary vẫn là public delivery | Quyết định 16/11/2026; chặn hoàn toàn là **TBD** (BR-52-06) |
| R3 UC-52 bước 2: "paraclinical orders with results" | Chỉ hiện kết quả đã gửi | BR-29; BR-55-02 (quyết định 8) |
| BR-42: log kèm "giá trị cũ/mới" | Thao tác xem không có giá trị cũ/mới; audit chỉ chứa ID và số lượng | Không có thay đổi; `security.md` cấm lưu dữ liệu định danh và y khoa |
| BR-42: "Admin xem được log" | Ngoài phạm vi | `security.md` chỉ cho đọc audit qua UC-06; **TBD** |
| `business-context.md` OQ-09: lễ tân có được xem hồ sơ không | Không | Quyết định 1; cần cập nhật OQ-09 khi bản này được duyệt |

## Quyết định

Chốt theo đề xuất ngày 18/11/2026; **cần nhóm xác nhận**.

| # | Quyết định |
| --- | --- |
| 1 | OQ-09: lễ tân không xem hồ sơ bệnh án; chỉ xem lịch sử lượt khám (UC-36) |
| 2 | Nút Sửa: bác sĩ của lượt + lượt chưa kết thúc + hồ sơ `OPEN` |
| 3 | Đặc tả mô tả hệ thống đích, kèm danh sách chênh lệch với code |
| 4 | Bộ lọc trạng thái: Đang hoạt động / Hoàn thành / Bỏ khám / Đã hủy |
| 5 | Từ khóa: khớp một phần, không phân biệt hoa thường, họ tên bỏ dấu; tối thiểu 2 ký tự (**ASSUMPTION**) |
| 6 | Lọc ICD-10 theo chẩn đoán cuối, theo mã hoặc tên |
| 7 | Chỉ hiện bệnh nhân có lượt khám; sắp theo ngày khám gần nhất; trang 20, tối đa 100 |
| 8 | Chỉ hiện kết quả CLS đã gửi |
| 9 | Tải tệp kết quả qua backend, không trả địa chỉ tệp gốc |
| 10 | In lại bản sao tách sang đặc tả riêng; UC-52 ẩn các nút in |
| 11 | Hồ sơ đã mở khóa lại: hiện dòng thông báo kèm thời điểm (**ASSUMPTION**) |
| 12 | Ghi audit cho 4 thao tác: tìm kiếm, xem danh sách lượt, xem chi tiết, tải tệp |
| 13 | Audit chỉ chứa ID, số lượng, tên bộ lọc; không chứa giá trị định danh |
| 14 | Đặc tả viết bằng tiếng Việt |
| 15 | Trạng thái: bản nháp, chờ nhóm duyệt |

## Chênh lệch với code hiện tại

Đối chiếu ngày 18/11/2026 (nhánh `Manh-Thang`).

| # | Code hiện tại | Đặc tả | Quy tắc |
| --- | --- | --- | --- |
| G1 | Audit lưu nguyên `keyword`, `patientCode`, tên tệp gốc; cột `sovaovien` chứa số bệnh án | Chỉ tên bộ lọc, ID, số lượng; `sovaovien` là số vào viện | BR-51-07 |
| G2 | Audit bị bỏ qua khi `actorAccountId` null; dùng `Instant.now()` | Luôn ghi; lỗi ghi audit làm request thất bại; dùng `Clock` | BR-51-07 |
| G3 | Response chi tiết trả `hinhanh` (địa chỉ Cloudinary); ghi vết qua `access-log` do client gọi | Endpoint tải tệp mới; bỏ `access-log` | BR-52-06 |
| G4 | Trả cả kết quả CLS nháp | Chỉ `SUBMITTED` | BR-52-05 |
| G5 | `CANCELLED` mang nhãn "Đang hoạt động" (backend) hoặc "Đang khám" (timeline frontend); thiếu bộ lọc "Đã hủy" | Nhãn riêng từng trạng thái; thêm bộ lọc | BR-51-04, BR-51-06 |
| G6 | `status` nhận chuỗi tự do, SQL có nhánh so khớp trực tiếp | Allow-list 4 giá trị | BR-51-04 |
| G7 | Trả CCCD, số điện thoại ở danh sách cấp 1 | Không trả | BR-51-05 |
| G8 | Backend trả nhãn tiếng Việt (giới tính "Khác"/"Chưa xác định", trạng thái, loại lượt) | Trả mã; frontend đổi sang nhãn | BR-52-02 |
| G9 | `GET /medical-records` (danh sách phẳng) | Bỏ | API |
| G10 | `size` > 100 bị kẹp về 100; keyword > 100 bị cắt | 400 với MSG08 | BR-51-04, BR-51-05 |
| G11 | Không có ngưỡng độ dài tối thiểu của từ khóa | 2 ký tự, MSG132 | BR-51-04 |
| G12 | Nút "In lại" gọi `window.print()` | Ẩn | BR-52-07 |
| G13 | AF-01 hiện chữ giữ chỗ "Bệnh nhân đang khám" / "BN..." | Dữ liệu thật | BR-51-06 |
| G14 | Frontend hiện nguyên `message` của backend | Ánh xạ mã lỗi sang MSG | API |
| G15 | Không phân biệt lượt chưa có hồ sơ | Nhãn "Chưa có hồ sơ", không mở được | BR-51-06 |
| G16 | Trang chi tiết không hủy request cũ khi chuyển lượt | `AbortController` | BR-52-08 |
| G17 | Có cả `MedicalRecordDetailModal` và `MedicalRecordDetailPage` cho UC-52 | Một màn hình UC-52 duy nhất (**ASSUMPTION**: giữ trang, bỏ modal) | — |
| G18 | Lỗi 404 dùng `NOT_FOUND` chung | `MEDICAL_RECORD_NOT_FOUND` | API |

## Tiến độ triển khai

| Đợt | Nội dung | Điểm chênh lệch đã xử lý | Trạng thái |
| --- | --- | --- | --- |
| 1 | Tuân thủ: audit, kết quả nháp, trạng thái, bộ lọc, mã lỗi, AF-01, bỏ modal và nút in | G1, G2, G4, G5, G6, G7, G9, G10, G11, G12, G13, G14, G15, G16, G17, G18; G8 cho danh sách cấp 1 và cấp 2 | Xong 18/11/2026. `MedicalRecordApiIT` chỉ chạy được khi có Docker (CI) |
| 2 | Tải tệp kết quả qua backend | G3 | Xong 18/11/2026 |
| 3 | Gọn code và tối ưu: màn chi tiết trả mã; đọc theo lô (hết N+1); `MedicalRecordDetailAssembler` và MapStruct `MedicalRecordDetailMapper`; tách UC-80 sang `MedicalRecordDataExportService`; thông báo mở khóa lại | G8 (chi tiết) | Xong 18/11/2026 |

**Còn lại:**
- Bộ lọc phòng vẫn chỉ gồm phòng đang hoạt động (`GET /rooms/active-examination`). Muốn gồm cả phòng đã ngừng hoạt động (BR-51-04, **ASSUMPTION**) thì cần một endpoint mới; chờ nhóm xác nhận giả định này.
- `MedicalRecordApiIT` chưa chạy trên máy phát triển vì không có Docker; CI sẽ chạy.
- UC-55 (`ParaclinicalResultServiceImpl`) vẫn trả địa chỉ Cloudinary của ảnh; ngoài phạm vi đặc tả này.

## Kế hoạch triển khai

**Backend** (không đổi schema)

1. `ErrorCode`: thêm `MEDICAL_RECORD_NOT_FOUND`. Thêm enum `MedicalRecordVisitFilter` (`ACTIVE`, `COMPLETED`, `ABANDONED`, `CANCELLED`) và dùng nó cho tham số `status`.
2. `MedicalRecordController`:
   - bỏ `GET /medical-records` và `POST …/access-log`;
   - thêm `GET /{medicalRecordId}/result-files/{fileId}`;
   - bỏ các kiểm tra `jwt != null`.
3. `MedicalRecordServiceImpl`:
   - kiểm tra đầu vào theo BR-51-04 và BR-51-05;
   - ghi audit theo BR-51-07 qua `Clock`;
   - trả mã thay cho nhãn tiếng Việt;
   - lọc kết quả `SUBMITTED`;
   - bỏ `hinhanh` khỏi response;
   - bỏ các overload không có actor;
   - tách việc dựng chi tiết thành các hàm nhỏ hoặc MapStruct mapper (AGENTS.md);
   - đọc theo lô (`findAllById`, `…In`) để số truy vấn không tăng theo số dịch vụ CLS.
4. `MedicalRecordRepository`:
   - bỏ CCCD và số điện thoại khỏi projection cấp 1;
   - lấy chẩn đoán gần nhất từ lượt mới nhất có kết luận;
   - lọc trạng thái theo enum;
   - trả cờ bỏ qua ở projection cấp 2.
5. `ResultImageStorage`: thêm thao tác đọc tệp, chỉ cho phép host Cloudinary đã cấu hình.
6. Tách `exportMedicalRecordData` (UC-80) ra khỏi `MedicalRecordServiceImpl` vào service riêng (ngoài phạm vi về hành vi, chỉ để làm gọn class).

**Frontend**

1. `medicalRecordApi`: thêm `downloadResultFile` (dùng `apiClient.download`); bỏ `recordFileAccessLog`.
2. `MedicalRecordsPage`, `PatientVisitListTable`, `MedicalRecordsFilter`:
   - thêm bộ lọc Đã hủy;
   - kiểm tra MSG50, MSG132, MSG08 phía client;
   - đổi nhãn bằng `VISIT_STATUS_LABELS` và `PATIENT_GENDER_LABELS`;
   - lấy tiêu đề AF-01 từ dữ liệu thật;
   - ánh xạ mã lỗi sang MSG;
   - sửa 3 lỗi ESLint `react-hooks/set-state-in-effect`.
3. `MedicalRecordDetailPage`:
   - xem tệp qua object URL;
   - ẩn các nút in lại;
   - dùng `AbortController`;
   - sửa nhãn trạng thái timeline;
   - hiện thông báo mở khóa lại.
4. Bỏ `MedicalRecordDetailModal`; chọn lượt ở cấp 2 thì điều hướng sang trang chi tiết.

**Kiểm thử**

- **Unit test service:**
  - lọc trạng thái (gồm Đã hủy, lượt bị bỏ qua);
  - kết hợp bộ lọc trên cùng một lượt;
  - ngưỡng từ khóa;
  - `size` > 100;
  - nội dung audit không chứa từ khóa hay mã bệnh nhân;
  - kết quả nháp bị ẩn;
  - cờ `editable`;
  - tệp không thuộc hồ sơ thì trả 404.
- **Integration test (Testcontainers):**
  - truy vấn cấp 1 và cấp 2 trên PostgreSQL (tìm họ tên bỏ dấu, chẩn đoán gần nhất);
  - 403 cho Admin, Lễ tân;
  - một dòng `audit_logs` cho mỗi thao tác.
- **Frontend:**
  - bộ lọc Đã hủy;
  - thông báo lỗi theo mã;
  - lượt chưa có hồ sơ không mở được;
  - không còn nút in lại;
  - mở tệp gọi endpoint tải tệp.

**Việc kèm theo khi bản này được duyệt**

- Cập nhật OQ-09 trong `business-context.md` và ghi chú Lễ tân trong `permission-matrix.md`.
- Bổ sung MSG132 vào sheet thông báo của `Business_Rules_v7.xlsx`.
- Ghi lại quyết định về *authenticated delivery* (BR-52-06), vì quyết định 16/11/2026 hiện chỉ nằm trong comment code.
