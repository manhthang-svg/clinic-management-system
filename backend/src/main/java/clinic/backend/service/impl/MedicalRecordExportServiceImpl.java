package clinic.backend.service.impl;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import clinic.backend.config.properties.FacilityPrintProperties;
import clinic.backend.dto.request.ExportMedicalRecordRequest;
import clinic.backend.entity.ExportLog;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.RecordArchive;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ExportFormat;
import clinic.backend.enums.ExportLogPurpose;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ExportLogRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.RecordArchiveRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.MedicalRecordExportService;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-53: PDF of a closed medical record in form 15/BV1, built only from its frozen archive package
 * (docs/uc-53-export-medical-record.md BR-53-01 to BR-53-08).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MedicalRecordExportServiceImpl implements MedicalRecordExportService {

    static final int RECIPIENT_MAX_LENGTH = 200;
    private static final String AUDIT_ENTITY = "MEDICAL_RECORD";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final Set<ExportLogPurpose> RECIPIENT_REQUIRED =
            Set.of(ExportLogPurpose.CUNG_CAP_NGUOI_BENH, ExportLogPurpose.CO_QUAN_CO_THAM_QUYEN);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MedicalRecordRepository medicalRecordRepository;
    private final RecordArchiveRepository recordArchiveRepository;
    private final VisitRepository visitRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final ExportLogRepository exportLogRepository;
    private final ExportFailureRecorder failureRecorder;
    private final ArchiveStore archiveStore;
    private final Form15Bv1Renderer renderer;
    private final FacilityPrintProperties facilityPrintProperties;
    private final AuditLogService auditLogService;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional
    public ExportedFile exportPdf(StaffPrincipal principal, UUID medicalRecordId, ExportMedicalRecordRequest request) {
        ExportLogPurpose purpose = purpose(request == null ? null : request.purpose());
        String recipient = recipient(purpose, request.recipient());

        MedicalRecord record = medicalRecordRepository.findById(medicalRecordId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND));
        RecordArchive archive = record.getStatus() == MedicalRecordStatus.OPEN ? null
                : recordArchiveRepository.findByMedicalRecordId(record.getId()).orElse(null);
        if (archive == null) {
            throw new AppException(ErrorCode.EXPORT_RECORD_NOT_CLOSED);
        }
        String sovaovien = visitRepository.findById(record.getVisitId())
                .map(Visit::getSovaovien)
                .orElse(archive.getSoluutru());
        Instant now = clock.instant();
        Failure failure = reason -> failed(principal, record, sovaovien, purpose, recipient, now, reason);

        JsonNode archivePackage = readPackage(archive, failure);
        String exporter = staffAccountRepository.findById(principal.accountId())
                .map(ArchivePackageBuilder::staffName)
                .orElse("");
        byte[] pdf;
        try {
            pdf = renderer.render(archivePackage, new Form15Bv1Renderer.Stamp(
                    facilityPrintProperties.healthDepartment(), exporter, now));
        } catch (RuntimeException exception) {
            log.error("UC-53 rendering failed [medicalRecordId={}]", record.getId(), exception);
            throw failure.fail("RENDER_FAILED");
        }

        UUID exportId = ids.next();
        ArchiveStore.StoredPackage stored;
        try {
            stored = archiveStore.storeExport(now.atZone(CLINIC_ZONE).getYear(), exportId, "pdf", pdf);
        } catch (RuntimeException exception) {
            log.error("UC-53 storing the PDF failed [medicalRecordId={}]", record.getId(), exception);
            throw failure.fail("STORE_FAILED");
        }
        exportLogRepository.save(ExportLog.pdfExport(exportId, record.getId(), sovaovien, purpose, recipient,
                stored.uri(), stored.sha256(), Form15Bv1Renderer.SPEC_VERSION, principal.accountId(), now));

        // security.md: identifiers and codes only; never the recipient's text or medical content.
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("exportLogId", exportId.toString());
        values.put("format", ExportFormat.PDF.name());
        values.put("purpose", purpose.name());
        values.put("specVersion", Form15Bv1Renderer.SPEC_VERSION);
        values.put("fileSha256", stored.sha256());
        auditLogService.recordBusinessChange(principal.accountId(), AuditLogAction.EXPORT, AUDIT_ENTITY,
                record.getId(), record.getSoba(), values, now);

        String fileName = "BA-" + archive.getSoluutru() + "-" + FILE_STAMP.format(now.atZone(CLINIC_ZONE)) + ".pdf";
        return new ExportedFile(fileName, pdf);
    }

    /** BR-53-04: the package must be readable and match the SHA-256 recorded at closing. */
    private JsonNode readPackage(RecordArchive archive, Failure failure) {
        byte[] content;
        try {
            content = archiveStore.read(archive.getPackageUri());
        } catch (UncheckedIOException | IllegalArgumentException exception) {
            log.error("UC-53 archive package unreadable [archiveId={}]", archive.getId(), exception);
            throw failure.fail("PACKAGE_UNREADABLE");
        }
        if (!ArchiveStore.sha256(content).equals(archive.getPackageSha256())) {
            log.error("UC-53 archive package hash mismatch [archiveId={}]", archive.getId());
            throw failure.fail("PACKAGE_HASH_MISMATCH");
        }
        try {
            return JSON.readTree(content);
        } catch (IOException exception) {
            log.error("UC-53 archive package is not JSON [archiveId={}]", archive.getId(), exception);
            throw failure.fail("PACKAGE_INVALID");
        }
    }

    private static ExportLogPurpose purpose(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("purpose", "REQUIRED");
        }
        try {
            return ExportLogPurpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid("purpose", "INVALID");
        }
    }

    /** BR-53-03. */
    private static String recipient(ExportLogPurpose purpose, String value) {
        String recipient = value == null || value.isBlank() ? null : value.strip();
        if (recipient == null && RECIPIENT_REQUIRED.contains(purpose)) {
            throw invalid("recipient", "REQUIRED");
        }
        if (recipient != null && recipient.length() > RECIPIENT_MAX_LENGTH) {
            throw invalid("recipient", "TOO_LONG");
        }
        return recipient;
    }

    private static AppException invalid(String field, String reason) {
        return new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", field, "reason", reason));
    }

    /** EF-03: logs the failure in its own transaction and returns the error to throw. */
    private AppException failed(StaffPrincipal principal, MedicalRecord record, String sovaovien,
                                ExportLogPurpose purpose, String recipient, Instant now, String reason) {
        failureRecorder.record(ExportLog.failed(ids.next(), record.getId(), sovaovien, ExportFormat.PDF, purpose,
                recipient, Form15Bv1Renderer.SPEC_VERSION, reason, principal.accountId(), now));
        return new AppException(ErrorCode.EXPORT_FAILED);
    }

    @FunctionalInterface
    private interface Failure {
        AppException fail(String reason);
    }
}
