# Luật Lưu trữ số 33/2024/QH15

| | |
| --- | --- |
| Cơ quan ban hành | Quốc hội |
| Ban hành | 21/06/2024 |
| Hiệu lực | 01/07/2025 (Điều 64 khoản 1) |
| Thay thế | Luật Lưu trữ số 01/2011/QH13 (Điều 64 khoản 2) |
| Sửa đổi | Luật An ninh mạng số 116/2025/QH15, hiệu lực 01/07/2026: thay "an toàn thông tin" bằng "an ninh mạng" (Điều 35, 36) |
| Tình trạng | Còn hiệu lực ngày 17/11/2026, theo văn bản hợp nhất dưới đây |
| Bản gốc | [Luat-33-2024-QH15-Luu-tru-VBHN.pdf](Luat-33-2024-QH15-Luu-tru-VBHN.pdf): văn bản hợp nhất của Văn phòng Quốc hội (34 trang), đăng trên Cổng TTĐT Chính phủ, `datafiles.chinhphu.vn/cpp/files/vbpq/2026/3/34-vbhn-vpqh.pdf`, tải ngày 17/11/2026 |

## Về bản chép này

- Chữ được trích từ lớp văn bản của bản PDF bằng `pypdf`. Lời văn giữ nguyên; chỉ sửa các khoảng trắng mà trích xuất chèn vào giữa chữ. Các chú thích của văn bản hợp nhất được giữ ở dạng ghi chú trong ngoặc.
- Văn bản hợp nhất không thay thế văn bản gốc về giá trị pháp lý. Chỉ chép các điều dự án dùng.

## Các điều khoản dự án đang dựa vào

| Điều, khoản | Nội dung | Quy tắc / quyết định của dự án |
| --- | --- | --- |
| Điều 2 khoản 11 | Dữ liệu chủ của tài liệu lưu trữ | UC-58 BR-58-06 (siêu dữ liệu trong gói lưu trữ) |
| Điều 2 khoản 14, Điều 9 khoản 4 điểm c | Lưu trữ tư; tài liệu của tổ chức kinh tế không phải doanh nghiệp nhà nước là tài liệu lưu trữ tư | Phòng khám tư nhân: xem ghi chú dưới |
| Điều 3 khoản 3, 5; Điều 47 khoản 1 | Luật khác có quy định khác về thời hạn thì theo luật đó; lưu trữ tư do tổ chức quyết định áp dụng, phù hợp với luật này và pháp luật liên quan | Xem ghi chú dưới |
| Điều 15 khoản 3, 4 | Thời hạn tính theo năm, kể từ năm kết thúc công việc; hồ sơ nhiều tài liệu lấy thời hạn dài nhất | BR-44, UC-58 `expires_on` |
| Điều 15 khoản 5, 6 | Bộ quy định thời hạn của ngành (TT 33/2025/TT-BYT); người đứng đầu cơ sở quy định danh mục, thời hạn cụ thể | BR-44; quy chế của phòng khám |
| Điều 16 | Hủy tài liệu hết thời hạn, do người đứng đầu quyết định, hủy toàn bộ, không khôi phục được | `record_archives.status` `PENDING_DISPOSAL`, `DISPOSED` (ngoài phạm vi UC-58) |
| Điều 33 khoản 2 | Tài liệu tạo lập dạng số: xác định nguồn gốc, toàn vẹn, lưu cùng dữ liệu chủ, truy cập được | UC-58 BR-58-06 |
| Điều 36 khoản 2 | Bảo quản tài liệu số: toàn vẹn nội dung và khuôn dạng, xác thực lâu dài, chuyển đổi theo công nghệ; dữ liệu chủ có cùng thời hạn | UC-58 BR-58-06 |

**Ghi chú về phạm vi.** Phòng khám của dự án là phòng khám tư nhân (nhóm xác nhận ngày 17/11/2026). Phòng khám là tổ chức kinh tế, không phải doanh nghiệp nhà nước, nên hồ sơ của phòng khám là **tài liệu lưu trữ tư** (Điều 9 khoản 4 điểm c), không thuộc Phông lưu trữ Nhà nước. Hệ quả:

- Theo Điều 3 khoản 5, phòng khám tự quyết định áp dụng luật này. Theo Điều 47 khoản 1, việc lưu trữ phải phù hợp với luật này và "quy định khác của pháp luật có liên quan".
- "Quy định khác" ở đây là Luật KBCB 2023 Điều 69 khoản 2 ([`Luat-15-2023-QH15-Kham-benh-chua-benh.md`](Luat-15-2023-QH15-Kham-benh-chua-benh.md)). Điều này áp dụng cho mọi cơ sở khám bệnh, chữa bệnh, kể cả tư nhân: hồ sơ bệnh án "phải được lưu giữ", và việc lưu trữ "thực hiện theo quy định của pháp luật về lưu trữ". Vì vậy dự án vẫn theo thời hạn của TT 33/2025/TT-BYT, tức 10 năm cho bệnh án ngoại trú, và cách tính thời hạn của Điều 15 khoản 3.
- Các thủ tục chỉ dành cho cơ quan nhà nước không áp dụng: nộp lưu vào lưu trữ lịch sử (Điều 17 đến 19), kho lưu trữ số của Bộ, ngành (Điều 35 khoản 5, 6) và thẩm định trước khi hủy (Điều 16 khoản 3 điểm a). Quyết định hủy hồ sơ hết hạn thuộc người đứng đầu phòng khám; Điều 16 khoản 3 điểm b được áp dụng tương tự (**ASSUMPTION**).

## Chương I. Những quy định chung (trích)

### Điều 2. Giải thích từ ngữ (trích)

4. Tài liệu điện tử là tài liệu tạo lập ở dạng thông điệp dữ liệu.

8. Lưu trữ hiện hành là đơn vị, bộ phận thực hiện lưu trữ đối với tài liệu lưu trữ của cơ quan, tổ chức.

11. Dữ liệu chủ của tài liệu lưu trữ là dữ liệu chứa thông tin cơ bản để mô tả về tài liệu lưu trữ và đặc tính của tài liệu lưu trữ, làm cơ sở để tham chiếu, đồng bộ giữa các cơ sở dữ liệu tài liệu lưu trữ hoặc các tập dữ liệu tài liệu lưu trữ khác nhau.

14. Lưu trữ tư là lưu trữ của cá nhân, gia đình, dòng họ, cộng đồng, tổ chức không phải là hội quần chúng do Đảng, Nhà nước giao nhiệm vụ.

### Điều 3. Áp dụng Luật Lưu trữ và pháp luật có liên quan (trích)

1. Việc lưu trữ tài liệu thuộc Phông lưu trữ Nhà nước Việt Nam, tài liệu lưu trữ tư có giá trị đặc biệt phải tuân thủ quy định của Luật này, trừ trường hợp quy định tại khoản 2 và khoản 3 Điều này.

3. Trường hợp luật khác có quy định khác về thời hạn lưu trữ và trách nhiệm quản lý tài liệu lưu trữ tại lưu trữ hiện hành thì thực hiện theo quy định của luật đó.

5. Tổ chức, cá nhân, gia đình, dòng họ, cộng đồng quyết định việc áp dụng quy định của Luật này đối với lưu trữ tài liệu lưu trữ tư không thuộc trường hợp quy định tại khoản 1 Điều này.

## Chương II. Quản lý tài liệu lưu trữ (trích)

### Điều 9. Phông lưu trữ quốc gia Việt Nam (trích)

1. Phông lưu trữ quốc gia Việt Nam bao gồm toàn bộ tài liệu lưu trữ của nước Việt Nam, không phụ thuộc vào thời gian hình thành, nơi bảo quản, kỹ thuật ghi tin và vật mang tin. Phông lưu trữ quốc gia Việt Nam gồm Phông lưu trữ Đảng Cộng sản Việt Nam, Phông lưu trữ Nhà nước Việt Nam và tài liệu lưu trữ tư.

3. Phông lưu trữ Nhà nước Việt Nam gồm toàn bộ tài liệu lưu trữ hình thành trong quá trình hoạt động của cơ quan nhà nước, đơn vị sự nghiệp công lập, doanh nghiệp nhà nước, đơn vị thuộc lực lượng vũ trang nhân dân; nhân vật lịch sử, tiêu biểu và tài liệu lưu trữ khác được hình thành qua các thời kỳ lịch sử của đất nước, bao gồm: …

4. Tài liệu lưu trữ tư bao gồm:

a) Tài liệu lưu trữ hình thành trong hoạt động của cá nhân, gia đình, dòng họ, cộng đồng;

b) Tài liệu lưu trữ hình thành trong hoạt động của tổ chức xã hội, tổ chức xã hội - nghề nghiệp không thuộc trường hợp quy định tại điểm d khoản 3 Điều này;

c) Tài liệu lưu trữ hình thành trong hoạt động của tổ chức phi chính phủ, tổ chức kinh tế không phải là doanh nghiệp nhà nước.

### Điều 15. Thời hạn lưu trữ hồ sơ, tài liệu

1. Thời hạn lưu trữ hồ sơ, tài liệu bao gồm lưu trữ vĩnh viễn và lưu trữ có thời hạn.

2. Hồ sơ, tài liệu lưu trữ vĩnh viễn bao gồm: tài liệu lưu trữ có giá trị đặc biệt; hồ sơ, tài liệu về đường lối, chủ trương, chính sách, cương lĩnh, chiến lược; chương trình mục tiêu quốc gia, đề án, dự án quan trọng, trọng điểm quốc gia; hồ sơ, tài liệu lưu trữ vĩnh viễn khác theo quy định của luật có liên quan và của cơ quan có thẩm quyền.

3. Hồ sơ, tài liệu lưu trữ có thời hạn là hồ sơ, tài liệu không thuộc trường hợp quy định tại khoản 2 Điều này.

Thời hạn lưu trữ hồ sơ, tài liệu quy định tại khoản này được tính theo năm, tối thiểu là 02 năm và tối đa là 70 năm kể từ năm kết thúc công việc.

4. Trường hợp hồ sơ có các tài liệu lưu trữ với thời hạn khác nhau thì thời hạn lưu trữ hồ sơ được xác định theo thời hạn của tài liệu có thời hạn lưu trữ dài nhất trong hồ sơ.

5. Bộ, cơ quan ngang Bộ, Tòa án nhân dân tối cao, Viện kiểm sát nhân dân tối cao, Kiểm toán nhà nước quy định cụ thể thời hạn lưu trữ hồ sơ, tài liệu ngành, lĩnh vực thuộc phạm vi quản lý nhà nước sau khi có ý kiến của Bộ Nội vụ.

6. Căn cứ quy định tại các khoản 2, 3 và 5 Điều này, người đứng đầu cơ quan, tổ chức quy định danh mục, thời hạn lưu trữ cụ thể đối với hồ sơ, tài liệu hình thành trong quá trình hoạt động của cơ quan, tổ chức mình.

7. Căn cứ quy định tại khoản 5 và khoản 6 Điều này, người được giao xử lý công việc có trách nhiệm xác định cụ thể thời hạn lưu trữ đối với hồ sơ, tài liệu.

### Điều 16. Hủy tài liệu lưu trữ (trích)

1. Việc hủy tài liệu lưu trữ được thực hiện đối với các tài liệu sau đây:

a) Tài liệu lưu trữ hết thời hạn lưu trữ;

b) Tài liệu lưu trữ trùng lặp.

2. Khi hủy tài liệu lưu trữ phải bảo đảm hủy toàn bộ tài liệu và không thể khôi phục được.

3. Thẩm quyền quyết định hủy tài liệu lưu trữ tại lưu trữ hiện hành được quy định như sau:

…

b) Người đứng đầu cơ quan, tổ chức không thuộc trường hợp nộp tài liệu vào lưu trữ lịch sử quyết định hủy tài liệu lưu trữ theo quy định.

## Chương III, Mục 3. Nghiệp vụ lưu trữ tài liệu điện tử (trích)

### Điều 32. Tài liệu lưu trữ điện tử

1. Tài liệu lưu trữ điện tử bao gồm tài liệu lưu trữ số và các tài liệu lưu trữ điện tử khác.

2. Tài liệu lưu trữ số là tài liệu được tạo lập bằng phương pháp dùng tín hiệu số bao gồm:

a) Tài liệu lưu trữ tạo lập dạng số;

b) Bản số hóa tài liệu lưu trữ.

### Điều 33. Tài liệu lưu trữ tạo lập dạng số

1. Tài liệu lưu trữ tạo lập dạng số là tài liệu được tạo ra, gửi, nhận, lưu trữ và sử dụng ở định dạng số.

2. Tài liệu lưu trữ tạo lập dạng số phải đáp ứng các yêu cầu sau đây:

a) Được xác thực số bởi cơ quan, tổ chức, cá nhân tạo ra tài liệu hoặc có yếu tố xác định được nguồn gốc của tài liệu;

b) Bảo đảm toàn vẹn từ lúc khởi tạo hoàn chỉnh đến trước khi đưa ra sử dụng. Tài liệu được bảo đảm toàn vẹn khi thông tin chưa bị thay đổi, trừ những thay đổi về hình thức phát sinh trong quá trình gửi, nhận, lưu trữ hoặc hiển thị;

c) Được lưu trữ đồng thời với dữ liệu chủ của tài liệu lưu trữ số;

d) Truy cập và sử dụng được dưới dạng hoàn chỉnh.

### Điều 35. Kho lưu trữ số (trích)

1. Kho lưu trữ số bao gồm hệ thống hạ tầng kỹ thuật, hệ thống phần mềm, cơ sở dữ liệu tài liệu lưu trữ và tài liệu lưu trữ số. Kho lưu trữ số phải đáp ứng các điều kiện sau đây:

a) Hỗ trợ thực hiện các hoạt động nghiệp vụ lưu trữ tài liệu lưu trữ số; quản lý cơ sở dữ liệu tài liệu lưu trữ;

b) Bảo đảm an ninh mạng (chú thích VBHN: cụm từ "an toàn thông tin" được thay bằng "an ninh mạng" theo điểm a khoản 1 Điều 43 Luật An ninh mạng số 116/2025/QH15, hiệu lực từ 01/07/2026), tính toàn vẹn, xác thực, khả năng truy cập, sử dụng và phát huy giá trị tài liệu lưu trữ;

c) Kết nối để chia sẻ, khai thác theo quy định của pháp luật.

### Điều 36. Thu nộp, bảo quản, sử dụng tài liệu lưu trữ số và hủy tài liệu lưu trữ số hết giá trị (trích)

2. Việc bảo quản tài liệu lưu trữ số được quy định như sau:

a) Tài liệu lưu trữ số phải đáp ứng các tiêu chuẩn, cấu trúc dữ liệu hồ sơ, tài liệu lưu trữ số; bảo đảm tính toàn vẹn, thống nhất, độ xác thực, an toàn và khả năng truy cập; được bảo quản và sử dụng theo phương pháp chuyên môn, nghiệp vụ riêng biệt;

b) Tài liệu lưu trữ số phải bảo quản an toàn, toàn vẹn về nội dung và khuôn dạng, bảo đảm được xác thực lâu dài, khả năng truy cập và được chuyển đổi phù hợp với sự thay đổi của công nghệ, đáp ứng các yêu cầu của pháp luật về an ninh mạng (chú thích VBHN: thay cho "an toàn thông tin mạng", hiệu lực từ 01/07/2026);

c) Thời hạn lưu trữ dữ liệu chủ của tài liệu lưu trữ số như thời hạn lưu trữ của tài liệu lưu trữ số;

d) Tài liệu lưu trữ số được bảo quản trong kho lưu trữ số theo đơn vị là hồ sơ hoặc tài liệu.

3. Việc sử dụng tài liệu lưu trữ số được quy định như sau:

a) Tài liệu lưu trữ số được sử dụng thông qua các hình thức: đọc, cấp bản sao, cung cấp thông tin từ tài liệu lưu trữ số và cơ sở dữ liệu tài liệu lưu trữ;

b) Tài liệu lưu trữ số được sử dụng thông qua bản dành cho người sử dụng. Bản dành cho người sử dụng được chuyển đổi khuôn dạng, điều chỉnh cấu trúc, cách thức hiển thị theo yêu cầu của công tác quản lý và nhu cầu tiếp cận của người sử dụng, bảo đảm nội dung như bản gốc.

4. Việc hủy tài liệu lưu trữ số hết giá trị được quy định như sau:

a) Tài liệu lưu trữ số hết giá trị khi tài liệu đó không có mối liên kết với tài liệu lưu trữ số khác có thời hạn lưu trữ dài hơn trong cùng hệ thống;

b) Khi hủy tài liệu lưu trữ số hết giá trị phải đồng thời hủy toàn bộ dữ liệu chủ của tài liệu đó và tài liệu giấy đã được số hóa (nếu có).

7. Bộ trưởng Bộ Nội vụ quy định chi tiết các khoản 1, 2, 3 và 4 Điều này.

## Chương V. Lưu trữ tư (trích)

### Điều 47. Quản lý lưu trữ tư

1. Lưu trữ tư được tổ chức, thực hiện phù hợp với quy định của Luật này, quy định khác của pháp luật có liên quan và thông lệ quốc tế, phù hợp với điều kiện cụ thể của tổ chức, cá nhân, gia đình, dòng họ, cộng đồng và theo nguyên tắc tự bảo đảm kinh phí hoạt động.

2. Lưu trữ tư để phục vụ hoạt động thực tiễn, nghiên cứu khoa học, lịch sử, gìn giữ, phát huy giá trị truyền thống của gia đình, dòng họ, cộng đồng và các giá trị khác của tài liệu lưu trữ, góp phần bảo tồn và phát huy bản sắc văn hóa dân tộc Việt Nam.

### Điều 49. Quyền và nghĩa vụ của chủ sở hữu tài liệu lưu trữ tư (trích)

1. Sở hữu hợp pháp tài liệu lưu trữ tư.

2. Quản lý, bảo quản an toàn và sử dụng, phát huy hiệu quả giá trị tài liệu lưu trữ tư.
