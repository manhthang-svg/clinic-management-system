# Luật Giao dịch điện tử số 20/2023/QH15

| | |
| --- | --- |
| Cơ quan ban hành | Quốc hội khóa XV, kỳ họp thứ 5 |
| Người ký | Chủ tịch Quốc hội Vương Đình Huệ |
| Thông qua | 22/06/2023 |
| Hiệu lực | 01/07/2024 (Điều 52 khoản 1) |
| Thay thế | Luật Giao dịch điện tử số 51/2005/QH11 (Điều 52 khoản 2) |
| Văn bản quy định chi tiết | NĐ 23/2025/NĐ-CP về chữ ký điện tử và dịch vụ tin cậy ([ND-23-2025-ND-CP.md](ND-23-2025-ND-CP.md)) |
| Bản gốc | [luat20-2023-qh15.pdf](luat20-2023-qh15.pdf) (Công báo số 867 + 868 ngày 30/07/2023, 30 trang), do nhóm cung cấp ngày 13/11/2026 |

## Về bản chép này

- **Bản trích**, không phải toàn văn. Chỉ chép các điều dự án dẫn tới: Điều 3 (một số khoản), 8–13, 22, 23, 28, 31, 33, 52. Toàn văn xem bản PDF gốc.
- Chữ lấy từ lớp text của bản PDF Công báo, đã sửa các khoảng trắng chèn giữa chữ (ví dụ "m ạng" → "mạng"). Lời văn giữ nguyên.
- Số trang Công báo (bản PDF) ghi dạng `[tr. n]`.

## Các điều khoản dự án đang dựa vào

| Điều, khoản | Nội dung | Quy tắc / quyết định của dự án |
| --- | --- | --- |
| Điều 3 khoản 11, 12, 15 | Định nghĩa chữ ký điện tử, chữ ký số (khóa không đối xứng; xác thực, toàn vẹn, chống chối bỏ), dấu thời gian | Thuật ngữ trong [uc-59-e-prescription.md](../uc-59-e-prescription.md) BR-59-10 |
| Điều 10 | Thông điệp dữ liệu có giá trị như bản gốc khi toàn vẹn kể từ khi khởi tạo và truy cập được ở dạng hoàn chỉnh | Lưu nguyên dữ liệu đã ký (BR-59-10) |
| Điều 12 khoản 2 | Văn bản giấy chuyển từ thông điệp dữ liệu: nội dung toàn vẹn; thông tin xác định hệ thống và chủ quản lưu bản gốc để tra cứu; ký hiệu riêng xác nhận đã chuyển đổi và thông tin người chuyển đổi | Bản in đơn thuốc (UC-60, BR-59-13). Chi tiết do Chính phủ quy định (khoản 4) — **TBD**, văn bản chưa có trong `docs/reference/` |
| Điều 13 khoản 1 | Lưu trữ thông điệp dữ liệu: truy cập được; lưu đúng khuôn dạng khởi tạo, gửi, nhận; xác định được nguồn gốc, người gửi, người nhận, thời gian | `record_signatures`, `prescription_submissions` (BR-59-10, BR-59-12) |
| Điều 22 khoản 1 điểm b | Chữ ký số công cộng, bảo đảm bởi chứng thư chữ ký số công cộng | Bác sĩ của phòng khám tư ký bằng chữ ký số công cộng (BR-59-10) |
| Điều 22 khoản 3 | Sáu điều kiện của chữ ký số, trong đó dữ liệu tạo chữ ký chỉ thuộc sự kiểm soát của người ký khi ký và phải được bảo đảm bởi chứng thư chữ ký số | Hệ thống không giữ khóa bí mật của bác sĩ (BR-59-10) |
| Điều 22 khoản 4 | Hình thức xác nhận khác không phải chữ ký điện tử thực hiện theo quy định khác của pháp luật | TT 13/2025 Điều 3 khoản 3 dựa vào khoản này; xác nhận bằng PIN thuộc loại này, không phải chữ ký số |
| Điều 23 khoản 2 | Chữ ký số có giá trị pháp lý tương đương chữ ký tay trên văn bản giấy | TT 26/2025 Điều 10 (đơn điện tử có giá trị như đơn giấy) |
| Điều 28, 31 | Dịch vụ cấp dấu thời gian là dịch vụ tin cậy; dấu thời gian tạo dưới dạng chữ ký số | Dấu thời gian khi ký đơn (BR-59-10, khuyến nghị) |

---

[tr. 1]

QUỐC HỘI — Luật số: 20/2023/QH15

CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM, Độc lập - Tự do - Hạnh phúc

## LUẬT GIAO DỊCH ĐIỆN TỬ

Căn cứ Hiến pháp nước Cộng hòa xã hội chủ nghĩa Việt Nam;

Quốc hội ban hành Luật Giao dịch điện tử.

### Chương I. NHỮNG QUY ĐỊNH CHUNG

### Điều 3. Giải thích từ ngữ (trích)

Trong Luật này, các từ ngữ dưới đây được hiểu như sau:

4. Thông điệp dữ liệu là thông tin được tạo ra, được gửi, được nhận, được lưu trữ bằng phương tiện điện tử.

[tr. 2]

5. Chứng thư điện tử là giấy phép, giấy chứng nhận, chứng chỉ, văn bản xác nhận, văn bản chấp thuận khác do cơ quan, tổ chức có thẩm quyền phát hành dưới dạng dữ liệu điện tử.

11. Chữ ký điện tử là chữ ký được tạo lập dưới dạng dữ liệu điện tử gắn liền hoặc kết hợp một cách lô gíc với thông điệp dữ liệu để xác nhận chủ thể ký và khẳng định sự chấp thuận của chủ thể đó đối với thông điệp dữ liệu.

12. Chữ ký số là chữ ký điện tử sử dụng thuật toán khóa không đối xứng, gồm khóa bí mật và khóa công khai, trong đó khóa bí mật được dùng để ký số và khóa công khai được dùng để kiểm tra chữ ký số. Chữ ký số bảo đảm tính xác thực, tính toàn vẹn và tính chống chối bỏ nhưng không bảo đảm tính bí mật của thông điệp dữ liệu.

13. Chứng thư chữ ký điện tử là thông điệp dữ liệu nhằm xác nhận cơ quan, tổ chức, cá nhân được chứng thực là người ký chữ ký điện tử. Chứng thư chữ ký điện tử đối với chữ ký số được gọi là chứng thư chữ ký số.

14. Dịch vụ chứng thực chữ ký số là dịch vụ do tổ chức cung cấp dịch vụ chứng thực chữ ký số cung cấp để xác thực chủ thể ký số trên thông điệp dữ liệu, bảo đảm tính chống chối bỏ của chủ thể ký với thông điệp dữ liệu và bảo đảm tính toàn vẹn của thông điệp dữ liệu được ký.

15. Dấu thời gian là dữ liệu điện tử gắn với thông điệp dữ liệu cho phép xác định thời gian của thông điệp dữ liệu đó tồn tại ở một thời điểm cụ thể.

### Chương II. THÔNG ĐIỆP DỮ LIỆU (trích)

### Điều 8. Giá trị pháp lý của thông điệp dữ liệu

Thông tin trong thông điệp dữ liệu không bị phủ nhận giá trị pháp lý chỉ vì thông tin đó được thể hiện dưới dạng thông điệp dữ liệu.

### Điều 9. Thông điệp dữ liệu có giá trị như văn bản

1. Trường hợp pháp luật yêu cầu thông tin phải được thể hiện bằng văn bản thì thông điệp dữ liệu được xem là đáp ứng yêu cầu nếu thông tin trong thông điệp dữ liệu đó có thể truy cập và sử dụng được để tham chiếu.

[tr. 5]

2. Trường hợp pháp luật yêu cầu văn bản phải được công chứng, chứng thực thì thông điệp dữ liệu được xem là đáp ứng yêu cầu nếu được công chứng theo quy định của pháp luật về công chứng; chứng thực theo quy định của Luật này và pháp luật về chứng thực.

### Điều 10. Thông điệp dữ liệu có giá trị như bản gốc

Thông điệp dữ liệu được sử dụng và có giá trị như bản gốc khi đáp ứng đủ các yêu cầu sau đây:

1. Thông tin trong thông điệp dữ liệu được bảo đảm toàn vẹn kể từ khi được khởi tạo lần đầu tiên dưới dạng một thông điệp dữ liệu hoàn chỉnh.

Thông tin trong thông điệp dữ liệu được xem là toàn vẹn khi thông tin đó chưa bị thay đổi, trừ những thay đổi về hình thức phát sinh trong quá trình gửi, lưu trữ hoặc hiển thị thông điệp dữ liệu;

2. Thông tin trong thông điệp dữ liệu có thể truy cập và sử dụng được dưới dạng hoàn chỉnh.

### Điều 11. Thông điệp dữ liệu có giá trị dùng làm chứng cứ

1. Thông điệp dữ liệu được dùng làm chứng cứ theo quy định của Luật này và pháp luật về tố tụng.

2. Giá trị dùng làm chứng cứ của thông điệp dữ liệu được xác định căn cứ vào độ tin cậy của cách thức khởi tạo, gửi, nhận hoặc lưu trữ thông điệp dữ liệu; cách thức bảo đảm và duy trì tính toàn vẹn của thông điệp dữ liệu; cách thức xác định người khởi tạo, gửi, nhận thông điệp dữ liệu và các yếu tố phù hợp khác.

### Điều 12. Chuyển đổi hình thức giữa văn bản giấy và thông điệp dữ liệu

1. Thông điệp dữ liệu được chuyển đổi từ văn bản giấy phải đáp ứng đủ các yêu cầu sau đây:

a) Thông tin trong thông điệp dữ liệu được bảo đảm toàn vẹn như văn bản giấy;

b) Thông tin trong thông điệp dữ liệu có thể truy cập và sử dụng được để tham chiếu;

c) Có ký hiệu riêng xác nhận đã được chuyển đổi từ văn bản giấy sang thông điệp dữ liệu và thông tin của cơ quan, tổ chức, cá nhân thực hiện chuyển đổi;

d) Trường hợp văn bản giấy là giấy phép, giấy chứng nhận, chứng chỉ, văn bản xác nhận, văn bản chấp thuận khác do cơ quan, tổ chức có thẩm quyền phát hành

[tr. 6]

thì việc chuyển đổi phải đáp ứng yêu cầu quy định tại các điểm a, b và c khoản này và phải có chữ ký số của cơ quan, tổ chức thực hiện chuyển đổi, trừ trường hợp pháp luật có quy định khác. Hệ thống thông tin phục vụ chuyển đổi phải có tính năng chuyển đổi từ văn bản giấy sang thông điệp dữ liệu.

2. Văn bản giấy được chuyển đổi từ thông điệp dữ liệu phải đáp ứng đủ các yêu cầu sau đây:

a) Thông tin trong văn bản giấy được bảo đảm toàn vẹn như thông điệp dữ liệu;

b) Có thông tin để xác định được hệ thống thông tin và chủ quản hệ thống thông tin tạo lập, gửi, nhận, lưu trữ thông điệp dữ liệu gốc để tra cứu;

c) Có ký hiệu riêng xác nhận đã được chuyển đổi từ thông điệp dữ liệu sang văn bản giấy và thông tin của cơ quan, tổ chức, cá nhân thực hiện chuyển đổi;

d) Trường hợp thông điệp dữ liệu là chứng thư điện tử thì việc chuyển đổi phải đáp ứng yêu cầu quy định tại các điểm a, b và c khoản này và phải có chữ ký, con dấu (nếu có) của cơ quan, tổ chức thực hiện chuyển đổi theo quy định của pháp luật. Hệ thống thông tin phục vụ chuyển đổi phải có tính năng chuyển đổi từ thông điệp dữ liệu sang văn bản giấy.

3. Giá trị pháp lý của văn bản được chuyển đổi theo quy định của pháp luật có liên quan.

4. Chính phủ quy định chi tiết Điều này.

### Điều 13. Hình thức lưu trữ thông điệp dữ liệu

1. Trường hợp pháp luật yêu cầu văn bản, chứng từ, hồ sơ, tài liệu hoặc thông tin phải được lưu trữ thì văn bản, chứng từ, hồ sơ, tài liệu hoặc thông tin đó có thể được lưu trữ dưới dạng thông điệp dữ liệu khi đáp ứng đủ các yêu cầu sau đây:

a) Thông tin trong thông điệp dữ liệu đó có thể truy cập và sử dụng được để tham chiếu;

b) Thông tin trong thông điệp dữ liệu đó được lưu trong chính khuôn dạng được khởi tạo, gửi, nhận hoặc trong khuôn dạng cho phép thể hiện chính xác thông tin đó;

c) Thông điệp dữ liệu đó được lưu trữ theo một cách thức nhất định cho phép xác định nguồn gốc khởi tạo, người gửi, người nhận, thời gian gửi, nhận thông điệp dữ liệu.

[tr. 7]

2. Trừ trường hợp pháp luật có quy định khác, cơ quan, tổ chức, cá nhân được lựa chọn lưu trữ văn bản, chứng từ, hồ sơ, tài liệu hoặc thông tin dưới dạng văn bản giấy hoặc lưu trữ dưới dạng thông điệp dữ liệu khi thông điệp dữ liệu đáp ứng các yêu cầu quy định tại khoản 1 Điều này.

3. Nội dung, thời hạn lưu trữ đối với thông điệp dữ liệu được thực hiện theo quy định của pháp luật về lưu trữ và quy định khác của pháp luật có liên quan. Việc lưu trữ thông điệp dữ liệu có giá trị như lưu trữ văn bản giấy.

### Chương III. CHỮ KÝ ĐIỆN TỬ VÀ DỊCH VỤ TIN CẬY (trích)

### Điều 22. Chữ ký điện tử

1. Chữ ký điện tử được phân loại theo phạm vi sử dụng bao gồm:

a) Chữ ký điện tử chuyên dùng là chữ ký điện tử do cơ quan, tổ chức tạo lập, sử dụng riêng cho hoạt động của cơ quan, tổ chức đó phù hợp với chức năng, nhiệm vụ;

b) Chữ ký số công cộng là chữ ký số được sử dụng trong hoạt động công cộng và được bảo đảm bởi chứng thư chữ ký số công cộng;

c) Chữ ký số chuyên dùng công vụ là chữ ký số được sử dụng trong hoạt động công vụ và được bảo đảm bởi chứng thư chữ ký số chuyên dùng công vụ.

[tr. 12]

2. Chữ ký điện tử chuyên dùng phải đáp ứng đủ các yêu cầu sau đây:

a) Xác nhận chủ thể ký và khẳng định sự chấp thuận của chủ thể ký đối với thông điệp dữ liệu;

b) Dữ liệu tạo chữ ký điện tử chuyên dùng chỉ gắn duy nhất với nội dung của thông điệp dữ liệu được chấp thuận;

c) Dữ liệu tạo chữ ký điện tử chuyên dùng chỉ thuộc sự kiểm soát của chủ thể ký tại thời điểm ký;

d) Hiệu lực của chữ ký điện tử chuyên dùng có thể được kiểm tra theo điều kiện do các bên tham gia thỏa thuận.

3. Chữ ký số là chữ ký điện tử đáp ứng đủ các yêu cầu sau đây:

a) Xác nhận chủ thể ký và khẳng định sự chấp thuận của chủ thể ký đối với thông điệp dữ liệu;

b) Dữ liệu tạo chữ ký số chỉ gắn duy nhất với nội dung của thông điệp dữ liệu được chấp thuận;

c) Dữ liệu tạo chữ ký số chỉ thuộc sự kiểm soát của chủ thể ký tại thời điểm ký;

d) Mọi thay đổi đối với thông điệp dữ liệu sau thời điểm ký đều có thể bị phát hiện;

đ) Phải được bảo đảm bởi chứng thư chữ ký số. Trường hợp chữ ký số chuyên dùng công vụ phải được bảo đảm bởi chứng thư chữ ký số của tổ chức cung cấp dịch vụ chứng thực chữ ký số chuyên dùng công vụ. Trường hợp chữ ký số công cộng phải được bảo đảm bởi chứng thư chữ ký số của tổ chức cung cấp dịch vụ chứng thực chữ ký số công cộng;

e) Phương tiện tạo chữ ký số phải bảo đảm dữ liệu tạo chữ ký số không bị tiết lộ, thu thập, sử dụng cho mục đích giả mạo chữ ký; bảo đảm dữ liệu được dùng để tạo chữ ký số chỉ có thể sử dụng một lần duy nhất; không làm thay đổi dữ liệu cần ký.

4. Việc sử dụng các hình thức xác nhận khác bằng phương tiện điện tử để thể hiện sự chấp thuận của chủ thể ký đối với thông điệp dữ liệu mà không phải là chữ ký điện tử thực hiện theo quy định khác của pháp luật có liên quan.

### Điều 23. Giá trị pháp lý của chữ ký điện tử

1. Chữ ký điện tử không bị phủ nhận giá trị pháp lý chỉ vì được thể hiện dưới dạng chữ ký điện tử.

[tr. 13]

2. Chữ ký điện tử chuyên dùng bảo đảm an toàn hoặc chữ ký số có giá trị pháp lý tương đương chữ ký của cá nhân đó trong văn bản giấy.

3. Trường hợp pháp luật quy định văn bản phải được cơ quan, tổ chức xác nhận thì yêu cầu đó được xem là đáp ứng đối với một thông điệp dữ liệu nếu thông điệp dữ liệu đó được ký bằng chữ ký điện tử chuyên dùng bảo đảm an toàn hoặc chữ ký số của cơ quan, tổ chức đó.

### Điều 28. Dịch vụ tin cậy

1. Dịch vụ tin cậy bao gồm:

[tr. 16]

a) Dịch vụ cấp dấu thời gian;

b) Dịch vụ chứng thực thông điệp dữ liệu;

c) Dịch vụ chứng thực chữ ký số công cộng.

2. Dịch vụ tin cậy là ngành, nghề đầu tư kinh doanh có điều kiện.

3. Tổ chức cung cấp dịch vụ tin cậy phải có giấy phép kinh doanh dịch vụ do Bộ Thông tin và Truyền thông cấp, trừ dịch vụ chứng thực hợp đồng điện tử trong thương mại. Tổ chức được quyền đăng ký một hoặc các dịch vụ quy định tại khoản 1 Điều này. Thời hạn của giấy phép kinh doanh dịch vụ tin cậy là 10 năm.

Tổ chức cung cấp dịch vụ chứng thực hợp đồng điện tử trong thương mại phải đáp ứng điều kiện hoạt động cung cấp dịch vụ chứng thực hợp đồng điện tử theo quy định của pháp luật về thương mại điện tử và điều kiện kinh doanh dịch vụ tin cậy theo quy định tại Điều 29 của Luật này.

4. Chính phủ quy định chi tiết hoạt động của tổ chức cung cấp dịch vụ tin cậy; quy trình, thủ tục, hồ sơ cấp, gia hạn, thay đổi, cấp lại, tạm đình chỉ, thu hồi giấy phép kinh doanh dịch vụ tin cậy và các nội dung khác quy định tại Điều này.

### Điều 31. Dịch vụ cấp dấu thời gian

[tr. 18]

1. Dịch vụ cấp dấu thời gian là dịch vụ để gắn thông tin về thời gian vào thông điệp dữ liệu.

2. Dấu thời gian được tạo ra dưới dạng chữ ký số.

3. Thời gian được gắn vào thông điệp dữ liệu là thời gian mà tổ chức cung cấp dịch vụ cấp dấu thời gian nhận được thông điệp dữ liệu đó và được chứng thực bởi tổ chức cung cấp dịch vụ cấp dấu thời gian.

4. Nguồn thời gian của tổ chức cung cấp dịch vụ cấp dấu thời gian phải theo quy định của pháp luật về nguồn thời gian chuẩn quốc gia.

### Điều 33. Dịch vụ chứng thực chữ ký số công cộng (trích)

1. Dịch vụ chứng thực chữ ký số công cộng là dịch vụ chứng thực chữ ký số trong hoạt động công cộng.

2. Chứng thư chữ ký số công cộng được cung cấp bởi tổ chức cung cấp dịch vụ chứng thực chữ ký số công cộng theo quy định của Luật này.

3. Tổ chức cung cấp dịch vụ chứng thực chữ ký số công cộng thực hiện các hoạt động sau đây:

a) Phát hành chứng thư chữ ký số công cộng để xác nhận và duy trì trạng thái hiệu lực chứng thư chữ ký số công cộng của chủ thể ký thông điệp dữ liệu;

b) Thu hồi chứng thư chữ ký số công cộng;

c) Kiểm tra hiệu lực chữ ký số công cộng và duy trì trạng thái hiệu lực của chứng thư chữ ký số công cộng; không sử dụng rào cản kỹ thuật, công nghệ để hạn chế việc kiểm tra hiệu lực chữ ký số công cộng;

d) Cung cấp thông tin cần thiết để chứng thực chữ ký số công cộng;

đ) Liên thông với tổ chức cung cấp dịch vụ chứng thực điện tử quốc gia để bảo đảm cho việc kiểm tra hiệu lực chữ ký số công cộng.

### Chương VIII. ĐIỀU KHOẢN THI HÀNH (trích)

### Điều 52. Hiệu lực thi hành

[tr. 30]

1. Luật này có hiệu lực thi hành từ ngày 01 tháng 7 năm 2024.

2. Luật Giao dịch điện tử số 51/2005/QH11 hết hiệu lực kể từ ngày Luật này có hiệu lực thi hành, trừ trường hợp quy định tại Điều 53 của Luật này.

Luật này được Quốc hội nước Cộng hòa xã hội chủ nghĩa Việt Nam khóa XV, kỳ họp thứ 5 thông qua ngày 22 tháng 6 năm 2023.

CHỦ TỊCH QUỐC HỘI — Vương Đình Huệ
