package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import clinic.backend.dto.response.DepartmentStatisticsResponse;
import clinic.backend.dto.response.DiagnosisStatisticsResponse;
import clinic.backend.dto.response.DoctorStatisticsResponse;
import clinic.backend.dto.response.ParaclinicalStatisticsResponse;
import clinic.backend.dto.response.RevenueStatisticsResponse;
import clinic.backend.dto.response.VisitStatisticsResponse;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.StatisticsGroupBy;
import clinic.backend.enums.StatisticsReport;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.StatisticsExportService;
import clinic.backend.service.StatisticsService;
import clinic.backend.service.impl.StatisticsWorkbook.Money;
import clinic.backend.service.impl.StatisticsWorkbook.SheetWriter;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// UC-33: xuat bao cao Excel, so lieu lay tu StatisticsService nen khop voi cac trang thong ke
// file gom 1 sheet thong tin + 1 sheet cho moi bao cao
@Service
@RequiredArgsConstructor
public class StatisticsExportServiceImpl implements StatisticsExportService {

    static final String CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter GENERATED_AT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private static final Map<StatisticsReport, String> REPORT_TITLES = new LinkedHashMap<>();

    static {
        REPORT_TITLES.put(StatisticsReport.VISITS, "Lượt khám");
        REPORT_TITLES.put(StatisticsReport.DOCTORS, "Bác sĩ");
        REPORT_TITLES.put(StatisticsReport.DEPARTMENTS, "Khoa - Phòng");
        REPORT_TITLES.put(StatisticsReport.PARACLINICAL, "Cận lâm sàng");
        REPORT_TITLES.put(StatisticsReport.REVENUE, "Doanh thu");
        REPORT_TITLES.put(StatisticsReport.DIAGNOSES, "Bệnh thường gặp");
    }

    private final StatisticsService statisticsService;
    private final AuditLogService auditLogService;
    private final UuidV7Generator ids;
    private final Clock clock;

    // tao file Excel cac bao cao da chon, ghi audit (nen khong de readOnly)
    @Override
    @Transactional
    public ExportedFile exportExcel(StaffPrincipal principal, LocalDate from, LocalDate to,
            Set<StatisticsReport> reports, StatisticsGroupBy groupBy, Integer limit) {
        Set<StatisticsReport> selected = reports == null || reports.isEmpty()
                ? EnumSet.allOf(StatisticsReport.class)
                : EnumSet.copyOf(reports);
        // kiem tra khoang ngay giong cac trang thong ke
        VisitStatisticsResponse visits = statisticsService.getVisitStatistics(from, to, groupBy);
        LocalDate start = visits.from();
        LocalDate end = visits.to();
        Instant now = clock.instant();

        byte[] content;
        try (StatisticsWorkbook workbook = new StatisticsWorkbook()) {
            cover(workbook, start, end, selected, now);
            for (StatisticsReport report : REPORT_TITLES.keySet()) {
                if (!selected.contains(report)) {
                    continue;
                }
                SheetWriter sheet = workbook.sheet(REPORT_TITLES.get(report));
                switch (report) {
                    case VISITS -> visitsSheet(sheet, visits);
                    case DOCTORS -> doctorsSheet(sheet, statisticsService.getDoctorStatistics(start, end));
                    case DEPARTMENTS -> departmentsSheet(sheet, statisticsService.getDepartmentStatistics(start, end));
                    case PARACLINICAL -> paraclinicalSheet(sheet, statisticsService.getParaclinicalStatistics(start, end));
                    case REVENUE -> revenueSheet(sheet, statisticsService.getRevenueStatistics(start, end, groupBy));
                    case DIAGNOSES -> diagnosesSheet(sheet, statisticsService.getDiagnosisStatistics(start, end, limit));
                }
                sheet.fit();
            }
            content = workbook.toBytes();
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("format", "XLSX");
        audit.put("from", start.toString());
        audit.put("to", end.toString());
        audit.put("reports", selected.stream().map(Enum::name).toList());
        auditLogService.recordBusinessChange(
                principal.accountId(), AuditLogAction.EXPORT, "statistics_reports", ids.next(), null, audit, now);

        String fileName = "bao-cao-thong-ke_" + start + "_" + end + ".xlsx";
        return new ExportedFile(fileName, CONTENT_TYPE, content);
    }

    // sheet "Thong tin": tieu de, tu ngay, den ngay, thoi diem xuat, danh sach bao cao
    private static void cover(StatisticsWorkbook workbook, LocalDate start, LocalDate end,
            Set<StatisticsReport> selected, Instant now) {
        SheetWriter sheet = workbook.sheet("Thông tin");
        sheet.title("BÁO CÁO THỐNG KÊ")
                .blank()
                .line("Từ ngày", start)
                .line("Đến ngày", end)
                .line("Thời điểm xuất", GENERATED_AT.format(now.atZone(CLINIC_ZONE)))
                .blank()
                .table("Các báo cáo trong tệp", "Báo cáo");
        REPORT_TITLES.forEach((report, title) -> {
            if (selected.contains(report)) {
                sheet.row(title);
            }
        });
        sheet.fit();
    }

    private static String periodLabel(StatisticsGroupBy groupBy) {
        return groupBy == StatisticsGroupBy.MONTH ? "Tháng" : "Ngày";
    }

    private static Object period(LocalDate period, StatisticsGroupBy groupBy) {
        return groupBy == StatisticsGroupBy.MONTH
                ? String.format("%02d/%d", period.getMonthValue(), period.getYear())
                : period;
    }

    // cac sheet tung bao cao, cung cot voi bang tren trang
    private static void visitsSheet(SheetWriter sheet, VisitStatisticsResponse data) {
        VisitStatisticsResponse.VisitCounts total = data.summary();
        sheet.title("Thống kê lượt khám")
                .line("Từ ngày", data.from()).line("Đến ngày", data.to())
                .blank()
                .table(null, periodLabel(data.groupBy()), "Tổng", "Hoàn thành", "Đang xử lý", "Đã hủy", "Bỏ khám",
                        "BHYT", "Dịch vụ", "Tái khám", "Ưu tiên");
        for (VisitStatisticsResponse.VisitPeriod row : data.series()) {
            VisitStatisticsResponse.VisitCounts c = row.counts();
            sheet.row(period(row.period(), data.groupBy()), c.total(), c.completed(), c.inProgress(), c.cancelled(),
                    c.abandoned(), c.insurance(), c.selfPay(), c.followUp(), c.priority());
        }
        sheet.totalRow("Tổng", total.total(), total.completed(), total.inProgress(), total.cancelled(),
                total.abandoned(), total.insurance(), total.selfPay(), total.followUp(), total.priority());
    }

    private static void doctorsSheet(SheetWriter sheet, DoctorStatisticsResponse data) {
        sheet.title("Thống kê theo bác sĩ")
                .line("Từ ngày", data.from()).line("Đến ngày", data.to())
                .line("Lượt đã khám", data.examinedVisits())
                .line("Lượt chưa có bác sĩ", data.visitsWithoutDoctor())
                .blank()
                .table(null, "Bác sĩ", "Tài khoản", "Lượt khám", "Bệnh nhân", "Hoàn thành", "Đang xử lý", "Bỏ khám",
                        "Đã hủy");
        for (DoctorStatisticsResponse.DoctorRow row : data.doctors()) {
            sheet.row(row.doctorName(), row.active() ? "Hoạt động" : "Đã khóa", row.visits(), row.patients(),
                    row.completed(), row.inProgress(), row.abandoned(), row.cancelled());
        }
    }

    private static void departmentsSheet(SheetWriter sheet, DepartmentStatisticsResponse data) {
        sheet.title("Thống kê theo khoa / phòng")
                .line("Từ ngày", data.from()).line("Đến ngày", data.to())
                .line("Lượt khám", data.visits())
                .line("Dịch vụ cận lâm sàng", data.services())
                .blank()
                .table(null, "Khoa", "Phòng", "Loại", "Lượt khám", "Hoàn thành", "Đang xử lý", "Dịch vụ CLS",
                        "Đã làm", "Đang chờ", "Hủy / bỏ");
        for (DepartmentStatisticsResponse.DepartmentRow department : data.departments()) {
            String type = department.type() == null ? ""
                    : switch (department.type()) {
                        case CLINICAL -> "Lâm sàng";
                        case PARACLINICAL -> "Cận lâm sàng";
                    };
            sheet.totalRow(department.departmentName() + (department.active() ? "" : " (tạm ngưng)"), "", type,
                    department.visits(), department.completedVisits(), null, department.services(),
                    department.performedServices(), null, null);
            for (DepartmentStatisticsResponse.RoomRow room : department.rooms()) {
                sheet.row("", room.roomName() + " (" + room.roomCode() + ")" + (room.active() ? "" : " (tạm ngưng)"),
                        room.type() == null ? "" : switch (room.type()) {
                            case EXAMINATION -> "Phòng khám";
                            case PARACLINICAL -> "Phòng CLS";
                        },
                        room.visits(), room.completedVisits(), room.inProgressVisits(), room.services(),
                        room.performedServices(), room.waitingServices(), room.cancelledServices());
            }
        }
    }

    private static void paraclinicalSheet(SheetWriter sheet, ParaclinicalStatisticsResponse data) {
        sheet.title("Thống kê dịch vụ cận lâm sàng")
                .line("Từ ngày", data.from()).line("Đến ngày", data.to())
                .line("Đã thực hiện", data.performed())
                .line("Đã có kết quả", data.withResult())
                .blank()
                .table("Theo loại dịch vụ", "Loại", "Đã thực hiện", "Đã có kết quả");
        for (ParaclinicalStatisticsResponse.TypeCount row : data.byType()) {
            String type = row.type() == null ? "" : switch (row.type()) {
                case LAB -> "Xét nghiệm";
                case IMAGING -> "Chẩn đoán hình ảnh";
                case FUNCTIONAL -> "Thăm dò chức năng";
                case EXAMINATION -> "Khám bệnh";
            };
            sheet.row(type, row.performed(), row.withResult());
        }
        sheet.blank().table("Theo dịch vụ", "Mã", "Dịch vụ", "Nhóm", "Đã thực hiện", "Đã có kết quả");
        for (ParaclinicalStatisticsResponse.ServiceCount row : data.byService()) {
            sheet.row(row.serviceCode(), row.serviceName(), row.groupName(), row.performed(), row.withResult());
        }
        sheet.blank().table("Theo phòng", "Mã phòng", "Phòng", "Đã thực hiện", "Đã có kết quả");
        for (ParaclinicalStatisticsResponse.RoomCount row : data.byRoom()) {
            sheet.row(row.roomCode(), row.roomName(), row.performed(), row.withResult());
        }
    }

    private static void revenueSheet(SheetWriter sheet, RevenueStatisticsResponse data) {
        RevenueStatisticsResponse.Amounts total = data.summary();
        sheet.title("Thống kê doanh thu (chỉ khoản đã thu)")
                .line("Từ ngày", data.from()).line("Đến ngày", data.to())
                .blank()
                .table(null, periodLabel(data.groupBy()), "Khoản thu", "BHYT chi trả", "Bệnh nhân trả", "Doanh thu");
        for (RevenueStatisticsResponse.PeriodAmounts row : data.series()) {
            RevenueStatisticsResponse.Amounts a = row.amounts();
            sheet.row(period(row.period(), data.groupBy()), a.payments(), new Money(a.insuranceAmount()),
                    new Money(a.patientAmount()), new Money(a.totalAmount()));
        }
        sheet.totalRow("Tổng", total.payments(), new Money(total.insuranceAmount()),
                new Money(total.patientAmount()), new Money(total.totalAmount()));

        sheet.blank().table("Theo loại khoản thu", "Loại", "Khoản thu", "BHYT chi trả", "Bệnh nhân trả", "Doanh thu");
        for (RevenueStatisticsResponse.TypeAmounts row : data.byType()) {
            String type = row.type() == null ? "" : switch (row.type()) {
                case EXAMINATION_FEE -> "Phí khám";
                case PARACLINICAL_FEE -> "Phí cận lâm sàng";
            };
            amountsRow(sheet, type, row.amounts());
        }
        sheet.blank().table("Theo hình thức thanh toán", "Hình thức", "Khoản thu", "BHYT chi trả", "Bệnh nhân trả",
                "Doanh thu");
        for (RevenueStatisticsResponse.MethodAmounts row : data.byMethod()) {
            String method = row.method() == null ? "Chưa ghi hình thức" : switch (row.method()) {
                case CASH -> "Tiền mặt";
                case BANK_TRANSFER -> "Chuyển khoản";
                case CARD -> "Thẻ";
            };
            amountsRow(sheet, method, row.amounts());
        }
    }

    private static void amountsRow(SheetWriter sheet, String label, RevenueStatisticsResponse.Amounts a) {
        sheet.row(label, a.payments(), new Money(a.insuranceAmount()), new Money(a.patientAmount()),
                new Money(a.totalAmount()));
    }

    private static void diagnosesSheet(SheetWriter sheet, DiagnosisStatisticsResponse data) {
        sheet.title("Thống kê bệnh thường gặp (ICD-10)")
                .line("Từ ngày", data.from()).line("Đến ngày", data.to())
                .line("Kết luận", data.conclusions())
                .line("Có mã ICD-10", data.withIcd())
                .line("Mã bệnh khác nhau", data.distinctCodes())
                .blank()
                .table("Bệnh chính (top " + data.limit() + ")", "#", "Mã ICD-10", "Tên bệnh", "Số ca", "Bệnh nhân");
        int rank = 1;
        for (DiagnosisStatisticsResponse.DiagnosisRow row : data.mainDiagnoses()) {
            sheet.row(rank++, row.icdCode(), row.icdName(), row.cases(), row.patients());
        }
        sheet.blank().table("Theo nhóm bệnh", "Nhóm", "Tên nhóm", "Số ca");
        for (DiagnosisStatisticsResponse.CategoryRow row : data.categories()) {
            sheet.row(row.categoryCode(), row.categoryName(), row.cases());
        }
        sheet.blank().table("Bệnh kèm theo", "Mã ICD-10", "Tên bệnh", "Số ca");
        for (DiagnosisStatisticsResponse.SecondaryRow row : data.secondaryDiagnoses()) {
            sheet.row(row.icdCode(), row.icdName(), row.cases());
        }
    }
}
