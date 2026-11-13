# Thông tư 13/2025/TT-BYT

Hướng dẫn triển khai hồ sơ bệnh án điện tử.

| | |
| --- | --- |
| Cơ quan ban hành | Bộ Y tế |
| Người ký | KT. Bộ trưởng, Thứ trưởng Nguyễn Tri Thức |
| Ban hành | 06/06/2025 |
| Hiệu lực | 21/07/2025 (Điều 4 khoản 1) |
| Thay thế | TT 46/2018/TT-BYT; Mục VIII Phụ lục I TT 54/2017/TT-BYT (Điều 4 khoản 3) |
| Bản gốc | [13_2025_TT-BYT_660113.doc](13_2025_TT-BYT_660113.doc), do nhóm cung cấp ngày 13/11/2026 |

## Về bản chép này

- Chuyển từ bản .doc bằng Microsoft Word, giữ nguyên lời văn. Bản chép đầy đủ, trừ phần "Nơi nhận".

## Các điều khoản dự án đang dựa vào

| Điều, khoản | Nội dung | Quy tắc / quyết định của dự án |
| --- | --- | --- |
| Điều 1 khoản 1 | Hồ sơ bệnh án điện tử được lập, cập nhật, hiển thị, **ký**, lưu trữ… bằng phương tiện điện tử | `record_signatures` (V10) |
| Điều 3 | Nhân viên y tế, người bệnh, người đại diện ký hoặc xác nhận điện tử theo một trong ba hình thức: chữ ký điện tử hợp pháp; sinh trắc học; hình thức xác nhận khác theo Luật Giao dịch điện tử Điều 22 khoản 4 | `RecordSignatureMethod` (`DIGITAL_SIGNATURE`, `ELECTRONIC_CONFIRMATION`). Áp dụng cho bệnh án nói chung; **đơn thuốc điện tử theo quy định riêng TT 26/2025 Điều 10 (ký số)**, xem [uc-59-e-prescription.md](../uc-59-e-prescription.md) BR-59-10 |
| Điều 4 khoản 2 điểm b | Cơ sở khám bệnh, chữa bệnh không phải bệnh viện hoàn thành bệnh án điện tử chậm nhất 31/12/2026 | Phạm vi đồ án |
| Điều 6 khoản 3 điểm b | Cơ sở ban hành quy chế lập, cập nhật, quản lý, lưu trữ, sử dụng và an toàn thông tin bệnh án điện tử, gồm cả nội dung Điều 3 | Việc của cơ sở, ngoài phần mềm (cùng ý CV 365 mục V.2) |

---

BỘ Y TẾ — CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM, Độc lập - Tự do - Hạnh phúc

Số: 13/2025/TT-BYT — Hà Nội, ngày 06 tháng 6 năm 2025

## THÔNG TƯ HƯỚNG DẪN TRIỂN KHAI HỒ SƠ BỆNH ÁN ĐIỆN TỬ

Căn cứ Luật Khám bệnh, chữa bệnh năm 2023;

Căn cứ Nghị định số 42/2025/NĐ-CP ngày 27 tháng 02 năm 2025 của Chính phủ quy định chức năng, nhiệm vụ, quyền hạn và cơ cấu tổ chức của Bộ Y tế;

Theo đề nghị của Cục trưởng Cục Khoa học công nghệ và Đào tạo, Cục trưởng Cục Quản lý Khám, chữa bệnh, Cục trưởng Cục Quản lý Y, Dược cổ truyền,

Bộ trưởng Bộ Y tế ban hành Thông tư hướng dẫn triển khai hồ sơ bệnh án điện tử.

### Điều 1. Nguyên tắc quản lý, triển khai hồ sơ bệnh án điện tử

1. Hồ sơ bệnh án điện tử là hồ sơ bệnh án quy định tại khoản 17 Điều 2 Luật Khám bệnh, chữa bệnh được lập, cập nhật, hiển thị, ký, lưu trữ, quản lý, sử dụng và khai thác bằng phương tiện điện tử.

2. Việc lập, cập nhật hồ sơ bệnh án điện tử của người bệnh tại cơ sở khám bệnh, chữa bệnh bảo đảm đầy đủ các thông tin theo quy định tại Chương X Thông tư số 32/2023/TT-BYT ngày 31 tháng 12 năm 2023 của Bộ Y tế quy định chi tiết một số điều của Luật Khám bệnh, chữa bệnh.

3. Kết nối thông tin của hồ sơ bệnh án điện tử với số định danh cá nhân của công dân Việt Nam và người nước ngoài đã được cấp tài khoản định danh điện tử theo quy định pháp luật về căn cước.

4. Tuân thủ quy định của pháp luật về khám bệnh, chữa bệnh, dữ liệu, công nghệ thông tin, giao dịch điện tử, an toàn thông tin mạng, an ninh mạng, tiếp cận thông tin, bảo vệ dữ liệu cá nhân, lưu trữ dữ liệu; quy định về quản lý, kết nối và chia sẻ dữ liệu điện tử của cơ quan nhà nước.

### Điều 2. Yêu cầu về công nghệ thông tin triển khai hồ sơ bệnh án điện tử

1. Có hạ tầng công nghệ thông tin của cơ sở khám bệnh, chữa bệnh, tối thiểu gồm máy trạm; hạ tầng kết nối mạng; máy chủ; giải pháp hoặc thiết bị lưu trữ dữ liệu (bao gồm cả lưu trữ dự phòng); có các thiết bị, giải pháp về bảo mật thông tin và các thiết bị có liên quan khác.

2. Có các ứng dụng công nghệ thông tin trong quản lý khám bệnh, chữa bệnh để triển khai hồ sơ bệnh án điện tử.

3. Đáp ứng tiêu chuẩn kỹ thuật về ứng dụng công nghệ thông tin trong cơ quan nhà nước theo quy định của pháp luật về công nghệ thông tin.

4. Sẵn sàng phục hồi thông tin, dữ liệu và khả năng truy xuất trên hồ sơ bệnh án điện tử khi cần thiết để tham khảo, đối chiếu, khai thác, sử dụng trong công tác điều trị, kiểm tra, thanh tra, nghiên cứu khoa học và quản lý y tế.

### Điều 3. Quy định về ký, xác nhận điện tử trong hồ sơ bệnh án điện tử

Nhân viên y tế, người bệnh hoặc người đại diện của người bệnh ký hoặc xác nhận điện tử nội dung liên quan trong hồ sơ bệnh án điện tử theo một trong các hình thức sau:

1. Sử dụng chữ ký điện tử hợp pháp;

2. Sử dụng các kỹ thuật sinh trắc học;

3. Sử dụng các hình thức xác nhận bằng phương tiện điện tử khác theo quy định tại khoản 4 Điều 22 của Luật Giao dịch điện tử.

### Điều 4. Hiệu lực thi hành

1. Thông tư này có hiệu lực thi hành kể từ ngày 21 tháng 7 năm 2025.

2. Lộ trình thực hiện

a) Các cơ sở khám bệnh, chữa bệnh được cấp giấy phép hoạt động theo hình thức tổ chức là bệnh viện triển khai hồ sơ bệnh án điện tử chậm nhất vào ngày 30 tháng 9 năm 2025;

b) Các cơ sở khám bệnh, chữa bệnh khác có người bệnh điều trị nội trú, điều trị ban ngày và điều trị ngoại trú triển khai hồ sơ bệnh án điện tử, hoàn thành chậm nhất vào ngày 31 tháng 12 năm 2026.

3. Các văn bản và quy định sau đây hết hiệu lực kể từ ngày Thông tư này được ban hành

a) Thông tư số 46/2018/TT-BYT ngày 28 tháng 12 năm 2018 của Bộ Y tế quy định hồ sơ bệnh án điện tử;

b) Mục VIII tại Phụ lục I và các tiêu chí đánh giá liên quan đến bệnh án điện tử tại Thông tư số 54/2017/TT-BYT ngày 29 tháng 12 năm 2017 của Bộ trưởng Bộ Y tế ban hành bộ tiêu chí về ứng dụng công nghệ thông tin tại cơ sở khám bệnh, chữa bệnh.

### Điều 5. Điều khoản chuyển tiếp

1. Đối với người bệnh đang điều trị tại cơ sở khám bệnh, chữa bệnh trước thời điểm Thông tư này có hiệu lực và ra viện hoặc kết thúc đợt điều trị ngoại trú sau thời điểm Thông tư này có hiệu lực mà đang sử dụng hồ sơ bệnh án lập bằng giấy thì tiếp tục được áp dụng hồ sơ bệnh án này cho đến khi ra viện hoặc kết thúc đợt điều trị ngoại trú, trừ trường hợp cơ sở khám bệnh, chữa bệnh có thể chuyển thành hồ sơ bệnh án điện tử.

2. Đối với hồ sơ bệnh án đã được lập bằng giấy trước ngày Thông tư này có hiệu lực: căn cứ điều kiện thực tế của cơ sở khám bệnh, chữa bệnh, Thủ trưởng cơ sở khám bệnh, chữa bệnh quyết định việc chuyển đổi hình thức giữa văn bản giấy và thông điệp dữ liệu bảo đảm quy định tại Nghị định số 137/2024/NĐ-CP ngày 23 tháng 10 năm 2024 của Chính phủ quy định về giao dịch điện tử của cơ quan nhà nước và hệ thống thông tin phục vụ giao dịch điện tử.

### Điều 6. Tổ chức thực hiện

1. Trách nhiệm của các đơn vị thuộc Bộ Y tế

a) Cục Khoa học công nghệ và Đào tạo chủ trì, phối hợp với các cơ quan, đơn vị liên quan chỉ đạo, đôn đốc, hướng dẫn, kiểm tra việc thực hiện Thông tư này trên phạm vi toàn quốc;

b) Các Vụ, Cục thuộc Bộ Y tế tổ chức xây dựng, cập nhật, hướng dẫn các nội dung chuyên môn có liên quan để triển khai hồ sơ bệnh án điện tử theo chức năng, nhiệm vụ được giao;

c) Trung tâm Thông tin y tế Quốc gia hướng dẫn kỹ thuật triển khai hồ sơ bệnh án điện tử theo quy định của Thông tư này và các văn bản quy phạm pháp luật khác có liên quan.

2. Trách nhiệm của các bộ, ngành và Ủy ban nhân dân các tỉnh, thành phố trực thuộc Trung ương

a) Chỉ đạo, đôn đốc, hướng dẫn, kiểm tra việc thực hiện Thông tư này theo thẩm quyền quản lý;

b) Bảo đảm nguồn lực cho việc triển khai hồ sơ bệnh án điện tử theo quy định tại Thông tư này đối với các cơ sở thuộc thẩm quyền quản lý (trừ cơ sở khám bệnh, chữa bệnh tư nhân).

3. Trách nhiệm của các cơ sở khám bệnh, chữa bệnh

a) Tổ chức triển khai hồ sơ bệnh án điện tử theo quy định tại Thông tư này và các hướng dẫn do các cơ quan có thẩm quyền ban hành;

b) Xây dựng, ban hành quy chế lập, cập nhật, quản lý, lưu trữ, sử dụng và an toàn thông tin đối với hồ sơ bệnh án điện tử, bao gồm nội dung quy định tại Điều 3 Thông tư này.

Trong quá trình thực hiện nếu có khó khăn, vướng mắc, các cơ quan, tổ chức, cá nhân kịp thời phản ánh về Bộ Y tế (Cục Khoa học công nghệ và Đào tạo) để xem xét, giải quyết./.

KT. BỘ TRƯỞNG — THỨ TRƯỞNG — Nguyễn Tri Thức
