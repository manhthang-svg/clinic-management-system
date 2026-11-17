package clinic.backend.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

/**
 * UC-53 BR-53-05, BR-53-06, BR-53-11: renders form 15/BV1 "Bệnh án Ngoại trú" (TT 32/2023/TT-BYT Phụ lục XXVIII)
 * from the frozen archive package of UC-58, field by field as in {@code docs/uc-53-export-medical-record.md}.
 * Every value is escaped; nothing is read from the live tables.
 */
@Component
public class Form15Bv1Renderer {

    /** {@code export_logs.spec_version} of a PDF in this form. */
    public static final String SPEC_VERSION = "TT32/2023-15/BV1";
    /** BR-53-05: a field the package has no key for, because the package was written before the field existed. */
    static final String NOT_IN_PACKAGE = "(không có trong gói lưu trữ)";

    private static final String FONT = "Noto Serif";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final String CANCELLED = "CANCELLED";

    private static final Map<String, String> ORGANS = organLabels();
    private static final Map<String, String> TREATMENT_RESULTS = Map.of(
            "1", "Khỏi", "2", "Đỡ", "3", "Không thay đổi", "4", "Nặng hơn");

    private static final String CSS = """
            @page { size: A4; margin: 12mm 12mm 16mm 18mm;
                    @bottom-center { content: element(footer); } }
            body { font-family: 'Noto Serif'; font-size: 10pt; line-height: 1.3; }
            #footer { position: running(footer); font-size: 7.5pt; font-style: italic; text-align: center; }
            .page-no:before { content: counter(page); }
            .page-total:before { content: counter(pages); }
            table { width: 100%; border-collapse: collapse; }
            td { vertical-align: top; }
            .head td { font-size: 10pt; }
            .title { text-align: center; font-weight: bold; font-size: 14pt; }
            .center { text-align: center; }
            .right { text-align: right; }
            h2 { font-size: 10pt; font-weight: bold; margin: 2mm 0 0.5mm 0; }
            p { margin: 0.3mm 0; }
            .b { font-weight: bold; }
            .i { font-style: italic; }
            .box { display: inline-block; border: 0.6pt solid #000; width: 4mm; height: 4mm; line-height: 4mm;
                   text-align: center; font-size: 8.5pt; margin-left: 0.4mm; vertical-align: middle; }
            .vitals { border: 0.6pt solid #000; padding: 1.5mm; width: 52mm; }
            .summary { border: 0.6pt solid #000; padding: 2.5mm; }
            .films td, .films th { border: 0.6pt solid #000; padding: 0.6mm 1.5mm; font-size: 9.5pt; }
            .sign { margin-top: 3mm; page-break-inside: avoid; }
            .sign td { text-align: center; width: 50%; }
            .space { height: 12mm; }
            .next-page { page-break-before: always; }
            """;

    /** What the export adds that the package does not hold. */
    public record Stamp(String healthDepartment, String exportedBy, Instant exportedAt) {
    }

    public byte[] render(JsonNode archivePackage, Stamp stamp) {
        return pdf(html(archivePackage, stamp));
    }

    byte[] pdf(String html) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.withProducer("clinic-management-system");
        font(builder, "NotoSerif-Regular.ttf", 400, FontStyle.NORMAL);
        font(builder, "NotoSerif-Bold.ttf", 700, FontStyle.NORMAL);
        font(builder, "NotoSerif-Italic.ttf", 400, FontStyle.ITALIC);
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        return out.toByteArray();
    }

    private void font(PdfRendererBuilder builder, String file, int weight, FontStyle style) {
        builder.useFont(() -> {
            InputStream stream = Form15Bv1Renderer.class.getResourceAsStream("/fonts/" + file);
            if (stream == null) {
                throw new IllegalStateException("Missing font resource " + file);
            }
            return stream;
        }, FONT, weight, style, true);
    }

    /** The XHTML of the form; package-private for tests. */
    String html(JsonNode archivePackage, Stamp stamp) {
        JsonNode meta = archivePackage.path("DuLieuChu");
        JsonNode record = archivePackage.path("HoSoBenhAn");
        JsonNode patient = record.path("ThongTinBenhNhan");
        JsonNode admission = record.path("ThongTinVaoVien");
        JsonNode treatment = record.path("ThongTinDieuTri");
        JsonNode extensions = archivePackage.path("extensions");
        JsonNode discharge = treatment.path("tinhtrangravien");
        String dischargeDate = text(treatment, "ngayravien");

        StringBuilder html = new StringBuilder(16_384);
        html.append("<html><head><meta charset=\"UTF-8\"/><style>").append(CSS).append("</style></head><body>");
        footer(html, meta, stamp);
        header(html, meta, patient, stamp);
        administrative(html, patient, admission);

        html.append("<h2>II. LÝ DO VÀO VIỆN:</h2>").append(para(text(admission, "lydovaovien")));
        html.append("<h2>III. HỎI BỆNH:</h2>");
        html.append("<p><span class=\"b\">1. Quá trình bệnh lý:</span> ")
                .append(multiline(text(admission, "quatrinhbenhly"))).append("</p>");
        html.append("<p><span class=\"b\">2. Tiền sử bệnh:</span></p>");
        html.append("<p>+ Bản thân: ").append(escape(personalHistory(patient))).append("</p>");
        html.append("<p>+ Gia đình: ").append(multiline(text(patient, "tiensubenhtatcuagiadinh"))).append("</p>");

        examination(html, record, admission, treatment, extensions);

        html.append("<table class=\"sign\"><tr><td><p class=\"b\">Giám đốc bệnh viện</p><div class=\"space\"></div>")
                .append("<p>Họ và tên ……………………</p></td><td><p class=\"i\">").append(dayMonthYear(dischargeDate))
                .append("</p><p class=\"b\">Bác sỹ khám bệnh</p><div class=\"space\"></div><p>Họ và tên ")
                .append(escape(text(admission, "bacsikhambenh"))).append("</p></td></tr></table>");

        summary(html, record, admission, treatment, discharge, extensions);
        closingBlock(html, meta, treatment, dischargeDate);
        html.append("</body></html>");
        return html.toString();
    }

    private void footer(StringBuilder html, JsonNode meta, Stamp stamp) {
        html.append("<div id=\"footer\">Bản xuất từ hồ sơ bệnh án điện tử · Số lưu trữ ")
                .append(escape(text(meta, "soluutru"))).append(" · Xuất lúc ")
                .append(escape(STAMP.format(stamp.exportedAt().atZone(CLINIC_ZONE)))).append(" bởi ")
                .append(escape(stamp.exportedBy()))
                .append(" · Trang <span class=\"page-no\"></span>/<span class=\"page-total\"></span></div>");
    }

    private void header(StringBuilder html, JsonNode meta, JsonNode patient, Stamp stamp) {
        html.append("<table class=\"head\"><tr><td style=\"width:34%\"><p>Sở Y tế: ")
                .append(escape(stamp.healthDepartment())).append("</p><p>Bệnh viện: ")
                .append(escape(text(meta, "tencskcb"))).append("</p></td>")
                .append("<td style=\"width:36%\" class=\"center\"><p class=\"title\">BỆNH ÁN NGOẠI TRÚ</p><p>KHOA: ")
                .append(escape(text(patient, "tenkhoa"))).append("</p></td>")
                .append("<td style=\"width:30%\"><p>MS: 15/BV-01</p><p>Số ngoại trú: ")
                .append(escape(text(patient, "soba"))).append("</p><p>Số lưu trữ: ")
                .append(escape(text(meta, "soluutru"))).append("</p></td></tr></table>");
    }

    private void administrative(StringBuilder html, JsonNode patient, JsonNode admission) {
        String gender = text(patient, "gioitinh");
        String payer = text(patient, "doituongbn_loai");
        boolean referred = "CO_SO_Y_TE".equals(text(patient, "noigioithieu_loai"));
        html.append("<h2>I. HÀNH CHÍNH:</h2>");
        html.append("<p>1. Họ và tên <span class=\"i\">(In hoa)</span>: <span class=\"b\">")
                .append(escape(text(patient, "hoten"))).append("</span> &#160; 2. Sinh ngày: ")
                .append(boxes(birthDigits(text(patient, "ngaysinh")), 8)).append(" &#160; Tuổi: ")
                .append(boxes(text(patient, "tuoi"), 2)).append("</p>");
        html.append("<p>3. Giới: 1. Nam ").append(check("1".equals(gender))).append(" &#160; 2. Nữ ")
                .append(check("2".equals(gender))).append(" &#160; 4. Nghề nghiệp: ")
                .append(escape(text(patient, "nghenghiep"))).append(' ')
                .append(boxes(text(patient, "nghenghiep_ma"), 2)).append("</p>");
        html.append("<p>5. Dân tộc: ").append(escape(text(patient, "dantoc"))).append(' ')
                .append(boxes(text(patient, "dantoc_ma"), 2)).append(" &#160; 6. Ngoại kiều: ")
                .append(escape(text(patient, "ngoaikieu"))).append(' ')
                .append(boxes(text(patient, "ngoaikieu_ma"), 2)).append("</p>");
        html.append("<p>7. Địa chỉ: Số nhà, Thôn, phố: ").append(escape(text(patient, "diachi")))
                .append(" &#160; Xã, phường: ").append(escape(text(patient, "xaphuong")))
                .append(" &#160; Huyện (Q, Tx): ").append(boxes("", 2))
                .append(" &#160; Tỉnh, thành phố: ").append(escape(text(patient, "tinhthanh"))).append(' ')
                .append(boxes(text(patient, "tinhthanh_ma"), 2)).append("</p>");
        html.append("<p>8. Nơi làm việc: ").append(escape(text(patient, "noilamviec")))
                .append(" &#160; 9. Đối tượng: 1.BHYT ").append(check("BHYT".equals(payer)))
                .append(" 2.Thu phí ").append(check("DV".equals(payer)))
                .append(" 3.Miễn ").append(check(false)).append(" 4.Khác ").append(check(false)).append("</p>");
        html.append("<p>10. BHYT giá trị đến ").append(dayMonthYear(text(patient, "denngaybhyt")))
                .append(" &#160; Số thẻ BHYT: ").append(escape(text(patient, "mabhyt"))).append("</p>");
        html.append("<p>11. Họ tên, địa chỉ người nhà khi cần báo tin: ")
                .append(escape(join(", ", text(patient, "hotennguoithan"), text(patient, "diachinguoithan"))))
                .append(" &#160; Điện thoại số: ").append(escape(text(patient, "sodienthoainguoithan"))).append("</p>");
        html.append("<p>12. Đến khám bệnh lúc: ").append(timeAndDate(text(admission, "denkhambenhluc"))).append("</p>");
        html.append("<p>13. Chẩn đoán của nơi giới thiệu: ")
                .append(escape(text(admission, "chandoancuanoigioithieu"))).append(" &#160; 1. Y tế ")
                .append(check(referred)).append(" 2. Tự đến ").append(check(!referred)).append("</p>");
    }

    private void examination(
            StringBuilder html, JsonNode record, JsonNode admission, JsonNode treatment, JsonNode extensions) {
        html.append("<h2>IV. KHÁM BỆNH:</h2><table><tr><td>");
        html.append("<p><span class=\"b\">1. Toàn thân:</span> ")
                .append(multiline(text(admission, "khambenh_toanthan"))).append("</p>");
        html.append("<p><span class=\"b\">2. Các bộ phận:</span> ")
                .append(extension(extensions, "khambenh_bophan")).append("</p>");
        for (String line : organLines(admission.path("khambenh_caccoquan"))) {
            html.append("<p>").append(escape(line)).append("</p>");
        }
        html.append("</td><td class=\"vitals\">")
                .append("<p>Mạch ").append(escape(text(admission, "mach"))).append(" lần/ph</p>")
                .append("<p>Nhiệt độ ").append(escape(text(admission, "nhietdo"))).append(" °C</p>")
                .append("<p>Huyết áp ").append(escape(text(admission, "huyetap_tamthu"))).append('/')
                .append(escape(text(admission, "huyetap_tamtruong"))).append(" mmHg</p>")
                .append("<p>Nhịp thở ").append(escape(text(admission, "nhiptho"))).append(" lần/ph</p>")
                .append("<p>Cân nặng ").append(escape(text(admission, "cannang"))).append(" kg</p>")
                .append("</td></tr></table>");
        html.append("<p><span class=\"b\">3. Tóm tắt kết quả cận lâm sàng:</span></p>");
        for (String line : paraclinicalSummary(record)) {
            html.append("<p>").append(escape(line)).append("</p>");
        }
        html.append("<p><span class=\"b\">4. Chẩn đoán ban đầu:</span> ")
                .append(multiline(text(admission, "chandoansobo"))).append("</p>");
        html.append("<p><span class=\"b\">5. Đã xử lý</span> <span class=\"i\">(thuốc, chăm sóc)</span>: ")
                .append(extension(extensions, "daxuly")).append("</p>");
        List<String> medicines = medicineLines(record.path("YLenhThuocVatTu"));
        if (!medicines.isEmpty()) {
            html.append("<p>Thuốc:</p>");
            for (String line : medicines) {
                html.append("<p>- ").append(escape(line)).append("</p>");
            }
        }
        html.append("<p><span class=\"b\">6. Chẩn đoán khi ra viện:</span> ")
                .append(escape(text(treatment, "chandoan"))).append(" &#160; Mã: ")
                .append(boxes(text(treatment, "maicd"), 4)).append("</p>");
        html.append("<p><span class=\"b\">7. Điều trị ngoại trú từ ngày</span> ")
                .append(escape(slashDate(datePart(text(record.path("ThongTinBenhNhan"), "thoigianvaovien")))))
                .append(" <span class=\"b\">đến ngày</span> ").append(escape(slashDate(text(treatment, "ngayravien"))))
                .append("</p>");
    }

    private void summary(StringBuilder html, JsonNode record, JsonNode admission, JsonNode treatment,
                         JsonNode discharge, JsonNode extensions) {
        html.append("<div class=\"next-page\"></div><div class=\"summary\"><p class=\"title\">TỔNG KẾT BỆNH ÁN</p>");
        html.append("<p><span class=\"b\">1. Quá trình bệnh lý và diễn biến lâm sàng:</span> ")
                .append(multiline(join("\n", text(admission, "quatrinhbenhly"), text(treatment, "tomtat"))))
                .append("</p>");
        html.append("<p><span class=\"b\">2. Tóm tắt kết quả xét nghiệm cận lâm sàng có giá trị chẩn đoán:</span></p>");
        for (String line : paraclinicalSummary(record)) {
            html.append("<p>").append(escape(line)).append("</p>");
        }
        String mainName = text(treatment, "tenicd").isEmpty() ? text(treatment, "chandoan") : text(treatment, "tenicd");
        html.append("<p><span class=\"b\">3. Chẩn đoán ra viện:</span></p><p>- Bệnh chính: ")
                .append(escape(mainName)).append(" &#160; Mã: ").append(boxes(text(treatment, "maicd"), 4)).append("</p>");
        html.append("<p>- Bệnh kèm theo <span class=\"i\">(nếu có)</span>:</p>");
        JsonNode names = treatment.path("tenicd_khac");
        JsonNode codes = treatment.path("maicd_khac");
        for (int index = 0; index < names.size(); index++) {
            html.append("<p>&#160; ").append(escape(names.get(index).asText(""))).append(" &#160; Mã: ")
                    .append(boxes(codes.path(index).asText(""), 4)).append("</p>");
        }
        html.append("<p><span class=\"b\">4. Phương pháp điều trị:</span> ")
                .append(multiline(text(treatment, "huongdieutri"))).append("</p>");
        html.append("<p><span class=\"b\">5. Tình trạng người bệnh ra viện:</span> ")
                .append(escape(TREATMENT_RESULTS.getOrDefault(text(discharge, "ketquadieutri"), ""))).append("</p>");
        String followUp = text(extensions.path("lichhentaikham"), "ngayhen");
        html.append("<p><span class=\"b\">6. Hướng điều trị và các chế độ tiếp theo:</span> ")
                .append(multiline(join("\n", text(discharge, "loidanbacsi"),
                        followUp.isEmpty() ? "" : "Hẹn tái khám ngày " + slashDate(followUp))))
                .append("</p></div>");
    }

    private void closingBlock(StringBuilder html, JsonNode meta, JsonNode treatment, String dischargeDate) {
        html.append("<table class=\"sign\"><tr>");
        html.append("<td style=\"width:36%\"><table class=\"films\"><tr><th colspan=\"2\">Hồ sơ, phim, ảnh</th></tr>")
                .append("<tr><th>Loại</th><th>Số tờ</th></tr>");
        for (String kind : List.of("X - quang", "CT Scanner", "Siêu âm", "Xét nghiệm", "Khác……", "Toàn bộ hồ sơ")) {
            html.append("<tr><td style=\"text-align:left\">").append(escape(kind)).append("</td><td></td></tr>");
        }
        html.append("</table></td>");
        html.append("<td style=\"width:28%\"><p class=\"b\">Người giao hồ sơ:</p><div class=\"space\"></div>")
                .append("<p>Họ tên ……………</p><p class=\"b\">Người nhận hồ sơ:</p><div class=\"space\"></div>")
                .append("<p>Họ tên ……………</p></td>");
        html.append("<td style=\"width:36%\"><p class=\"i\">").append(dayMonthYear(dischargeDate))
                .append("</p><p class=\"b\">Bác sỹ điều trị</p><p class=\"i\">")
                .append(escape(confirmation(text(meta, "thoidiemdonghoso")))).append("</p><div class=\"space\"></div>")
                .append("<p>Họ tên ").append(escape(text(treatment, "bacsidieutri"))).append("</p></td>");
        html.append("</tr></table>");
    }

    /** BR-53-11. */
    static List<String> paraclinicalSummary(JsonNode record) {
        Map<String, String> names = new HashMap<>();
        Set<String> active = new HashSet<>();
        for (JsonNode slip : record.path("PhieuChiDinh").path("danhsach")) {
            for (JsonNode line : slip.path("danhsach")) {
                String code = text(line, "machidinh");
                if (!CANCELLED.equals(text(line, "trangthai"))) {
                    active.add(code);
                    names.put(code, text(line, "tenchidinh"));
                } else {
                    names.putIfAbsent(code, text(line, "tenchidinh"));
                }
            }
        }
        if (active.isEmpty()) {
            return List.of("Không chỉ định cận lâm sàng.");
        }
        record Entry(String time, String text) {
        }
        List<Entry> entries = new ArrayList<>();
        for (JsonNode result : record.path("KetQuaChanDoanHinhAnh").path("danhsach")) {
            String code = text(result, "machidinh");
            if (active.contains(code)) {
                entries.add(new Entry(text(result, "ngayketqua"),
                        names.getOrDefault(code, code) + ": " + text(result, "kqcls_ketluan")));
            }
        }
        for (JsonNode result : record.path("KetQuaXetNghiem").path("danhsach")) {
            JsonNode lines = result.path("danhsach");
            String code = lines.isEmpty() ? "" : text(lines.get(0), "machidinh");
            if (!active.contains(code)) {
                continue;
            }
            List<String> abnormal = new ArrayList<>();
            String time = "";
            for (JsonNode line : lines) {
                if (time.isEmpty()) {
                    time = text(line, "ngayketqua");
                }
                if ("1".equals(text(line, "ketquabatthuong"))) {
                    String range = text(line, "trisothamchieu");
                    abnormal.add(join(" ", text(line, "pkqxn_tenxetnghiem"), text(line, "ketqua"), text(line, "donvi"))
                            + (range.isEmpty() ? "" : " (tham chiếu " + range + ")"));
                }
            }
            entries.add(new Entry(time, names.getOrDefault(code, code) + ": "
                    + (abnormal.isEmpty() ? "trong giới hạn bình thường" : String.join("; ", abnormal))));
        }
        if (entries.isEmpty()) {
            return List.of("Không có kết quả cận lâm sàng.");
        }
        return entries.stream()
                .sorted(Comparator.comparing(Entry::time, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(Entry::text)
                .toList();
    }

    private static String personalHistory(JsonNode patient) {
        List<String> allergies = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (JsonNode item : patient.path("tiensubenhtatcuabanthan")) {
            String content = text(item, "thongtin_noidung");
            if (content.isEmpty()) {
                continue;
            }
            if ("DRUG_ALLERGY".equals(text(item, "loaitiensu"))) {
                allergies.add("Dị ứng thuốc: " + content);
            } else {
                others.add(content);
            }
        }
        allergies.addAll(others);
        return String.join("; ", allergies);
    }

    private static List<String> organLines(JsonNode organs) {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : organs.properties()) {
            List<String> findings = new ArrayList<>();
            for (JsonNode item : entry.getValue()) {
                String finding = join(" ", text(item, "dauchung"), text(item, "dauchung_ghichu"));
                if (!finding.isEmpty()) {
                    findings.add(finding);
                }
            }
            if (!findings.isEmpty()) {
                lines.add("- " + ORGANS.getOrDefault(entry.getKey(), entry.getKey()) + ": "
                        + String.join("; ", findings));
            }
        }
        return lines;
    }

    private static List<String> medicineLines(JsonNode prescriptions) {
        List<String> lines = new ArrayList<>();
        for (JsonNode slip : prescriptions.path("danhsach")) {
            for (JsonNode line : slip.path("danhsach")) {
                String quantity = join(" ", text(line, "soluongthuocvattu"), text(line, "donvitinh"));
                lines.add(join(" ", text(line, "tenthuocvattu"), quantity.isEmpty() ? "" : "x " + quantity)
                        + (text(line, "cachdung").isEmpty() ? "" : ". " + text(line, "cachdung")));
            }
        }
        return lines;
    }

    private static String extension(JsonNode extensions, String field) {
        return extensions.has(field) ? multiline(text(extensions, field)) : escape(NOT_IN_PACKAGE);
    }

    private static String confirmation(String closedAt) {
        return closedAt.length() < 12 ? "" : "Đã xác nhận điện tử lúc " + closedAt.substring(8, 10) + ":"
                + closedAt.substring(10, 12) + " " + slashDate(closedAt.substring(0, 8));
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("").strip();
    }

    private static String join(String separator, String... parts) {
        List<String> present = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                present.add(part.strip());
            }
        }
        return String.join(separator, present);
    }

    private static String datePart(String dateTime) {
        return dateTime.length() >= 8 ? dateTime.substring(0, 8) : "";
    }

    /** yyyyMMdd to dd/MM/yyyy; anything else prints blank. */
    static String slashDate(String value) {
        return value.length() == 8 ? value.substring(6, 8) + "/" + value.substring(4, 6) + "/" + value.substring(0, 4) : "";
    }

    private static String birthDigits(String value) {
        return value.length() == 8 ? value.substring(6, 8) + value.substring(4, 6) + value.substring(0, 4) : "";
    }

    private static String dayMonthYear(String value) {
        if (value.length() != 8) {
            return "Ngày …… tháng …… năm ……";
        }
        return "Ngày " + value.substring(6, 8) + " tháng " + value.substring(4, 6) + " năm " + value.substring(0, 4);
    }

    private static String timeAndDate(String value) {
        if (value.length() != 12) {
            return "…… giờ …… phút, ngày …… tháng …… năm ……";
        }
        return value.substring(8, 10) + " giờ " + value.substring(10, 12) + " phút, ngày " + value.substring(6, 8)
                + " tháng " + value.substring(4, 6) + " năm " + value.substring(0, 4);
    }

    private static String boxes(String value, int count) {
        StringBuilder html = new StringBuilder();
        int length = Math.max(count, value.length());
        int padding = length - value.length();
        for (int index = 0; index < length; index++) {
            char character = index < padding ? ' ' : value.charAt(index - padding);
            html.append("<span class=\"box\">").append(character == ' ' ? "&#160;" : escape(String.valueOf(character)))
                    .append("</span>");
        }
        return html.toString();
    }

    private static String check(boolean checked) {
        return "<span class=\"box\">" + (checked ? "X" : "&#160;") + "</span>";
    }

    private static String para(String value) {
        return "<p>" + multiline(value) + "</p>";
    }

    private static String multiline(String value) {
        return escape(value).replace("\r\n", "\n").replace("\n", "<br/>");
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> {
                    // XML 1.0 forbids most control characters; drop them rather than fail the export.
                    if (character >= 0x20 || character == '\n' || character == '\r' || character == '\t') {
                        out.append(character);
                    }
                }
            }
        }
        return out.toString();
    }

    private static Map<String, String> organLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("tuanhoan", "Tuần hoàn");
        labels.put("hohap", "Hô hấp");
        labels.put("tieuhoa", "Tiêu hóa");
        labels.put("thantietnieu", "Thận - Tiết niệu");
        labels.put("thankinh", "Thần kinh");
        labels.put("coxuongkhop", "Cơ - Xương - Khớp");
        labels.put("taimuihong", "Tai - Mũi - Họng");
        labels.put("noitiet_dinhduong_benhlikhac", "Nội tiết, dinh dưỡng và bệnh lý khác");
        return Map.copyOf(labels);
    }
}
