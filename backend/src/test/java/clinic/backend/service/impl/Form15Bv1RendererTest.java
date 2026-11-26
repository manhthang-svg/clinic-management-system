package clinic.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** UC-53 BR-53-05, BR-53-06, BR-53-11. */
class Form15Bv1RendererTest {

    private static final Instant EXPORTED = Instant.parse("2026-11-18T02:15:00Z");
    private static final Form15Bv1Renderer.Stamp STAMP =
            new Form15Bv1Renderer.Stamp("Sở Y tế Hà Nội", "BS Trần Bình", EXPORTED);

    private final Form15Bv1Renderer renderer = new Form15Bv1Renderer();
    private final ObjectMapper json = new ObjectMapper();

    private JsonNode archive(String extensions, String orders, String imaging, String labs) throws Exception {
        return json.readTree("""
                {"DuLieuChu":{"tencskcb":"Phòng khám Đa khoa An Bình","soluutru":"LT-DK0000000001",
                  "thoidiemdonghoso":"202611171030"},
                 "HoSoBenhAn":{
                  "ThongTinBenhNhan":{"soba":"BA-DK0000000001","tenkhoa":"Khoa Khám bệnh","hoten":"NGUYỄN VĂN <AN>",
                    "ngaysinh":"19900101","tuoi":36,"gioitinh":2,"doituongbn_loai":"BHYT","mabhyt":"DN4010112345678",
                    "denngaybhyt":"20261231","thoigianvaovien":"202611170805","noigioithieu_loai":"CO_SO_Y_TE",
                    "tiensubenhtatcuabanthan":[{"thongtin_noidung":"Tăng huyết áp","loaitiensu":"PERSONAL"},
                      {"thongtin_noidung":"Penicillin","loaitiensu":"DRUG_ALLERGY"}],
                    "tiensubenhtatcuagiadinh":"Bố đái tháo đường"},
                  "ThongTinVaoVien":{"bacsikhambenh":"BS Lê Khám","denkhambenhluc":"202611170810",
                    "lydovaovien":"Sốt, ho","chandoansobo":"Viêm phế quản?","mach":88,"nhietdo":"38.5",
                    "khambenh_caccoquan":{"hohap":[{"dauchung":"Ran ẩm đáy phổi phải"}]}},
                  "ThongTinDieuTri":{"bacsidieutri":"BS Lê Khám","ngayravien":"20261117","chandoan":"Viêm phổi",
                    "maicd":"J18","tenicd":"Viêm phổi, không đặc hiệu","tenicd_khac":["Tăng huyết áp"],
                    "maicd_khac":["I10"],"huongdieutri":"Kháng sinh đường uống",
                    "tinhtrangravien":{"ketquadieutri":2,"loidanbacsi":"Tái khám nếu sốt"}},
                  "YLenhThuocVatTu":{"danhsach":[{"danhsach":[{"tenthuocvattu":"Amoxicillin 500mg",
                    "soluongthuocvattu":"21","donvitinh":"Viên","cachdung":"Uống 3 lần/ngày"}]}]},
                  "PhieuChiDinh":{"danhsach":[{"danhsach":%s}]},
                  "KetQuaChanDoanHinhAnh":{"danhsach":%s},
                  "KetQuaXetNghiem":{"danhsach":%s}},
                 "extensions":%s}
                """.formatted(orders, imaging, labs, extensions));
    }

    private JsonNode fullArchive() throws Exception {
        return archive("""
                        {"khambenh_bophan":"Phổi ran ẩm","daxuly":"Hạ sốt","lichhentaikham":{"ngayhen":"20261124"}}""",
                """
                        [{"machidinh":"XQ01","tenchidinh":"X-quang ngực thẳng","trangthai":"COMPLETED"},
                         {"machidinh":"XN01","tenchidinh":"Tổng phân tích tế bào máu","trangthai":"COMPLETED"},
                         {"machidinh":"SA01","tenchidinh":"Siêu âm bụng","trangthai":"CANCELLED"}]""",
                """
                        [{"machidinh":"XQ01","ngayketqua":"202611170930","kqcls_ketluan":"Đám mờ đáy phổi phải"},
                         {"machidinh":"SA01","ngayketqua":"202611170940","kqcls_ketluan":"Không được in"}]""",
                """
                        [{"danhsach":[{"machidinh":"XN01","ngayketqua":"202611170900","pkqxn_tenxetnghiem":"WBC",
                          "ketqua":"15.2","donvi":"G/L","trisothamchieu":"4-10","ketquabatthuong":"1"},
                         {"machidinh":"XN01","pkqxn_tenxetnghiem":"HGB","ketqua":"140","ketquabatthuong":"0"}]}]""");
    }

    @Test
    void printsTheTreatmentResultAsFreeTextAndTheFormerCodesOfOlderArchivesAsLabels() {
        // V63: free text, optional; packages archived before V63 still hold the codes 1-4.
        assertThat(Form15Bv1Renderer.treatmentResult("Đỡ sốt, còn ho")).isEqualTo("Đỡ sốt, còn ho");
        assertThat(Form15Bv1Renderer.treatmentResult("2")).isEqualTo("Đỡ");
        assertThat(Form15Bv1Renderer.treatmentResult("")).isEmpty();
        assertThat(Form15Bv1Renderer.treatmentResult(null)).isEmpty();
    }

    @Test
    void fillsTheFormFromThePackageAndEscapesValues() throws Exception {
        String html = renderer.html(fullArchive(), STAMP);

        assertThat(html).contains(
                "Sở Y tế: Sở Y tế Hà Nội", "Bệnh viện: Phòng khám Đa khoa An Bình", "KHOA: Khoa Khám bệnh",
                "MS: 15/BV-01", "Số ngoại trú: BA-DK0000000001", "Số lưu trữ: LT-DK0000000001",
                "NGUYỄN VĂN &lt;AN&gt;",
                "Dị ứng thuốc: Penicillin; Tăng huyết áp", "Bố đái tháo đường",
                "08 giờ 10 phút, ngày 17 tháng 11 năm 2026",
                "- Hô hấp: Ran ẩm đáy phổi phải", "Phổi ran ẩm", "Hạ sốt",
                "Amoxicillin 500mg x 21 Viên. Uống 3 lần/ngày",
                "từ ngày</span> 17/11/2026 <span class=\"b\">đến ngày</span> 17/11/2026",
                "Tình trạng người bệnh ra viện:</span> Đỡ", "Tăng huyết áp", "Hẹn tái khám ngày 24/11/2026",
                "Đã xác nhận điện tử lúc 10:30 17/11/2026",
                "Xuất lúc 09:15 18/11/2026 bởi BS Trần Bình");
        assertThat(html).doesNotContain("NGUYỄN VĂN <AN>", Form15Bv1Renderer.NOT_IN_PACKAGE);
    }

    @Test
    void summarisesParaclinicalResultsInTimeOrderWithoutCancelledServices() throws Exception {
        JsonNode record = fullArchive().path("HoSoBenhAn");

        assertThat(Form15Bv1Renderer.paraclinicalSummary(record)).containsExactly(
                "Tổng phân tích tế bào máu: WBC 15.2 G/L (tham chiếu 4-10)",
                "X-quang ngực thẳng: Đám mờ đáy phổi phải");
    }

    @Test
    void saysWhenNothingWasOrderedOrNothingCameBack() throws Exception {
        assertThat(Form15Bv1Renderer.paraclinicalSummary(archive("{}", "[]", "[]", "[]").path("HoSoBenhAn")))
                .isEqualTo(List.of("Không chỉ định cận lâm sàng."));
        assertThat(Form15Bv1Renderer.paraclinicalSummary(archive("{}",
                "[{\"machidinh\":\"XQ01\",\"tenchidinh\":\"X-quang\",\"trangthai\":\"WAITING\"}]", "[]", "[]")
                .path("HoSoBenhAn")))
                .isEqualTo(List.of("Không có kết quả cận lâm sàng."));
    }

    @Test
    void marksFieldsThatAnOlderPackageDoesNotHold() throws Exception {
        String html = renderer.html(archive("{}", "[]", "[]", "[]"), STAMP);

        assertThat(html).contains("Các bộ phận:</span> " + Form15Bv1Renderer.NOT_IN_PACKAGE,
                "(thuốc, chăm sóc)</span>: " + Form15Bv1Renderer.NOT_IN_PACKAGE);
    }

    @Test
    void rendersAVietnamesePdf() throws Exception {
        byte[] pdf = renderer.render(fullArchive(), STAMP);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            assertThat(text).contains("BỆNH ÁN NGOẠI TRÚ", "NGUYỄN VĂN <AN>", "TỔNG KẾT BỆNH ÁN",
                    "Đám mờ đáy phổi phải", "Trang 1/2");
            assertThat(document.getNumberOfPages()).isEqualTo(2);
        }
    }
}
