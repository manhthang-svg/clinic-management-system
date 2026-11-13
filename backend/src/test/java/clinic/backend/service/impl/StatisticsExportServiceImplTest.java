package clinic.backend.service.impl;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import clinic.backend.dto.response.DepartmentStatisticsResponse;
import clinic.backend.dto.response.DiagnosisStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse;
import clinic.backend.dto.response.RevenueStatisticsResponse;
import clinic.backend.dto.response.VisitStatisticsResponse;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.PaymentMethod;
import clinic.backend.enums.PaymentType;
import clinic.backend.enums.StatisticsGroupBy;
import clinic.backend.enums.StatisticsReport;
import clinic.backend.exceptions.AppException;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.StatisticsExportService.ExportedFile;
import clinic.backend.service.StatisticsService;
import clinic.backend.utils.UuidV7Generator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** UC-33 Export Report. */
@ExtendWith(MockitoExtension.class)
class StatisticsExportServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-12T03:30:00Z");
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 2);
    private static final StaffPrincipal ADMIN = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @Mock StatisticsService statisticsService;
    @Mock AuditLogService auditLogService;
    @Mock UuidV7Generator ids;

    private StatisticsExportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StatisticsExportServiceImpl(statisticsService, auditLogService, ids,
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));
        lenient().when(ids.next()).thenReturn(UUID.randomUUID());
        VisitStatisticsResponse.VisitCounts day1 = new VisitStatisticsResponse.VisitCounts(5, 4, 1, 0, 0, 3, 2, 1, 1);
        VisitStatisticsResponse.VisitCounts day2 = new VisitStatisticsResponse.VisitCounts(0, 0, 0, 0, 0, 0, 0, 0, 0);
        lenient().when(statisticsService.getVisitStatistics(any(), any(), any())).thenReturn(new VisitStatisticsResponse(
                FROM, TO, StatisticsGroupBy.DAY, day1,
                List.of(new VisitStatisticsResponse.VisitPeriod(FROM, day1), new VisitStatisticsResponse.VisitPeriod(TO, day2))));
        lenient().when(statisticsService.getDoctorStatistics(FROM, TO)).thenReturn(new DoctorStatisticsResponse(
                FROM, TO, 5, 0, List.of(new DoctorStatisticsResponse.DoctorRow(
                        UUID.randomUUID(), "BSCKII Trần Văn An", true, 5, 5, 4, 1, 0, 0))));
        lenient().when(statisticsService.getDepartmentStatistics(FROM, TO)).thenReturn(new DepartmentStatisticsResponse(
                FROM, TO, 5, 0, List.of()));
        lenient().when(statisticsService.getParaclinicalStatistics(FROM, TO)).thenReturn(new ParaclinicalStatisticsResponse(
                FROM, TO, 3, 2,
                List.of(new ParaclinicalStatisticsResponse.TypeCount(MedicalServiceType.LAB, 3, 2)),
                List.of(), List.of(), List.of()));
        RevenueStatisticsResponse.Amounts paid = new RevenueStatisticsResponse.Amounts(
                2, new BigDecimal("450000"), new BigDecimal("100000"), new BigDecimal("350000"));
        lenient().when(statisticsService.getRevenueStatistics(eq(FROM), eq(TO), any())).thenReturn(new RevenueStatisticsResponse(
                FROM, TO, StatisticsGroupBy.DAY, paid,
                List.of(new RevenueStatisticsResponse.TypeAmounts(PaymentType.EXAMINATION_FEE, paid)),
                List.of(new RevenueStatisticsResponse.MethodAmounts(PaymentMethod.CASH, paid)),
                List.of(new RevenueStatisticsResponse.PeriodAmounts(FROM, paid),
                        new RevenueStatisticsResponse.PeriodAmounts(TO, RevenueStatisticsResponse.Amounts.zero()))));
        lenient().when(statisticsService.getDiagnosisStatistics(eq(FROM), eq(TO), any())).thenReturn(
                new DiagnosisStatisticsResponse(FROM, TO, 10, 4, 4, 0, 1,
                        List.of(new DiagnosisStatisticsResponse.DiagnosisRow("J06.9", "Nhiễm trùng hô hấp trên cấp", 4, 3)),
                        List.of(), List.of()));
    }

    private static XSSFWorkbook open(ExportedFile file) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(file.content()));
    }

    private static List<String> sheetNames(XSSFWorkbook workbook) {
        List<String> names = new ArrayList<>();
        workbook.forEach(sheet -> names.add(sheet.getSheetName()));
        return names;
    }

    /** The first row whose first cell is {@code label}. */
    private static Row rowStartingWith(Sheet sheet, String label) {
        for (Row row : sheet) {
            if (row.getCell(0) != null && label.equals(row.getCell(0).toString())) {
                return row;
            }
        }
        throw new AssertionError("No row starting with " + label + " in " + sheet.getSheetName());
    }

    @Test
    void exportsEveryReportWhenNoneIsChosen() throws Exception {
        ExportedFile file = service.exportExcel(ADMIN, null, null, Set.of(), null, null);

        assertThat(file.fileName()).isEqualTo("bao-cao-thong-ke_2026-11-01_2026-11-02.xlsx");
        assertThat(file.contentType()).isEqualTo(StatisticsExportServiceImpl.CONTENT_TYPE);
        try (XSSFWorkbook workbook = open(file)) {
            assertThat(sheetNames(workbook)).containsExactly(
                    "Thông tin", "Lượt khám", "Bác sĩ", "Khoa - Phòng", "Cận lâm sàng", "Doanh thu", "Bệnh thường gặp");
            Sheet cover = workbook.getSheet("Thông tin");
            assertThat(cover.getRow(0).getCell(0).getStringCellValue()).isEqualTo("BÁO CÁO THỐNG KÊ");
            assertThat(rowStartingWith(cover, "Từ ngày").getCell(1).getStringCellValue()).isEqualTo("01/11/2026");
            assertThat(rowStartingWith(cover, "Thời điểm xuất").getCell(1).getStringCellValue()).isEqualTo("10:30 12/11/2026");
        }
    }

    @Test
    void writesNumbersAsNumbersAndSeparatesInsuranceFromPatientMoney() throws Exception {
        ExportedFile file = service.exportExcel(ADMIN, FROM, TO,
                Set.of(StatisticsReport.VISITS, StatisticsReport.REVENUE, StatisticsReport.DIAGNOSES), null, null);

        try (XSSFWorkbook workbook = open(file)) {
            assertThat(sheetNames(workbook)).containsExactly("Thông tin", "Lượt khám", "Doanh thu", "Bệnh thường gặp");
            Row visitDay = rowStartingWith(workbook.getSheet("Lượt khám"), "01/11/2026");
            assertThat(visitDay.getCell(1).getNumericCellValue()).isEqualTo(5);
            assertThat(rowStartingWith(workbook.getSheet("Lượt khám"), "Tổng").getCell(2).getNumericCellValue()).isEqualTo(4);

            Sheet revenue = workbook.getSheet("Doanh thu");
            Row revenueTotal = rowStartingWith(revenue, "Tổng");
            assertThat(revenueTotal.getCell(2).getNumericCellValue()).isEqualTo(100000);
            assertThat(revenueTotal.getCell(3).getNumericCellValue()).isEqualTo(350000);
            assertThat(revenueTotal.getCell(4).getNumericCellValue()).isEqualTo(450000);
            assertThat(revenueTotal.getCell(4).getCellStyle().getDataFormatString()).contains("₫");
            assertThat(rowStartingWith(revenue, "Phí khám").getCell(1).getNumericCellValue()).isEqualTo(2);
            assertThat(rowStartingWith(revenue, "Tiền mặt").getCell(3).getNumericCellValue()).isEqualTo(350000);

            Row topDiagnosis = rowStartingWith(workbook.getSheet("Bệnh thường gặp"), "1.0");
            assertThat(topDiagnosis.getCell(1).getStringCellValue()).isEqualTo("J06.9");
            assertThat(topDiagnosis.getCell(3).getNumericCellValue()).isEqualTo(4);
        }
        verify(statisticsService, never()).getDoctorStatistics(any(), any());
    }

    @Test
    void recordsTheExportInTheAuditLog() {
        service.exportExcel(ADMIN, FROM, TO, Set.of(StatisticsReport.DOCTORS), null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> values = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).recordBusinessChange(eq(ADMIN.accountId()), eq(AuditLogAction.EXPORT),
                eq("statistics_reports"), any(UUID.class), isNull(), values.capture(), eq(NOW));
        assertThat(values.getValue()).containsEntry("format", "XLSX")
                .containsEntry("from", "2026-11-01")
                .containsEntry("reports", List.of("DOCTORS"));
    }

    @Test
    void doesNotExportOrAuditAnInvalidPeriod() {
        when(statisticsService.getVisitStatistics(any(), any(), any()))
                .thenThrow(new AppException(ErrorCode.REQUEST_PARAMETER_INVALID));

        assertThatThrownBy(() -> service.exportExcel(ADMIN, TO, FROM, Set.of(), null, null))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(auditLogService);
    }
}
