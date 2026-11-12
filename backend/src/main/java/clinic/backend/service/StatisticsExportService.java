package clinic.backend.service;

import java.time.LocalDate;
import java.util.Set;

import clinic.backend.enums.StatisticsGroupBy;
import clinic.backend.enums.StatisticsReport;
import clinic.backend.security.user.StaffPrincipal;

/** UC-33 Export Report: the statistics of UC-27 → UC-32 as an Excel workbook. */
public interface StatisticsExportService {

    /** A generated file: its download name and content. */
    record ExportedFile(String fileName, String contentType, byte[] content) {
    }

    /**
     * Builds one workbook with a cover sheet and one sheet per report for {@code [from, to]} (missing dates default
     * as in the statistics pages; an empty set means every report). {@code groupBy} buckets the visit and revenue
     * series; {@code limit} sizes the diagnosis rankings. Every export is written to the audit log.
     */
    ExportedFile exportExcel(StaffPrincipal principal, LocalDate from, LocalDate to, Set<StatisticsReport> reports,
            StatisticsGroupBy groupBy, Integer limit);
}
