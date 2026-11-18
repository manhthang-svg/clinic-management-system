package clinic.backend.service.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.request.ExportMedicalRecordDataRequest;
import clinic.backend.dto.response.ExportMedicalRecordDataResponse;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.ExportLog;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.RecordArchive;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ExportFormat;
import clinic.backend.enums.ExportLogPurpose;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.ExportLogRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RecordArchiveRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.MedicalRecordDataExportService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** UC-80 / BR-43: XML or JSON export of a medical record in the CV 365 structure. */
@Service
@RequiredArgsConstructor
public class MedicalRecordDataExportServiceImpl implements MedicalRecordDataExportService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final MedicalRecordRepository medicalRecordRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final ConclusionRepository conclusionRepository;
    private final RecordArchiveRepository recordArchiveRepository;
    private final ExportLogRepository exportLogRepository;
    private final ArchivePackageBuilder archivePackageBuilder;
    private final AuditLogService auditLogService;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional
    public ExportMedicalRecordDataResponse exportMedicalRecordData(
            UUID medicalRecordId,
            ExportMedicalRecordDataRequest request,
            UUID actorAccountId) {
        if (medicalRecordId == null) {
            throw new AppException(ErrorCode.NOT_FOUND, Map.of("id", "Mã hồ sơ bệnh án không được để trống"));
        }
        if (request == null || request.format() == null) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, Map.of("format", "Định dạng xuất không được để trống"));
        }

        MedicalRecord record = medicalRecordRepository.findById(medicalRecordId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, Map.of("id", "Hồ sơ bệnh án không tồn tại")));

        if (record.getStatus() == MedicalRecordStatus.OPEN) {
            throw new AppException(ErrorCode.EXPORT_RECORD_NOT_CLOSED, Map.of("status", "Chỉ xuất được hồ sơ bệnh án đã đóng."));
        }

        RecordArchive archive = recordArchiveRepository.findByMedicalRecordId(record.getId()).orElse(null);
        if (archive == null) {
            throw new AppException(ErrorCode.EXPORT_RECORD_NOT_CLOSED, Map.of("archive", "Hồ sơ chưa có gói lưu trữ điện tử."));
        }

        ExportLogPurpose purpose = request.purpose() != null ? request.purpose() : ExportLogPurpose.LIEN_THONG;
        String recipient = request.recipient() == null || request.recipient().isBlank() ? null : request.recipient().strip();
        if (recipient == null && (purpose == ExportLogPurpose.CUNG_CAP_NGUOI_BENH || purpose == ExportLogPurpose.CO_QUAN_CO_THAM_QUYEN)) {
            throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "recipient", "reason", "REQUIRED"));
        }
        if (recipient != null && recipient.length() > 255) {
            throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "recipient", "reason", "TOO_LONG"));
        }

        Visit visit = visitRepository.findById(record.getVisitId()).orElse(null);
        Patient patient = visit != null ? patientRepository.findById(visit.getPatientId()).orElse(null) : null;
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(record.getId()).orElse(null);
        UUID doctorAccountId = record.getClosedByAccountId() != null ? record.getClosedByAccountId() : actorAccountId;
        StaffAccount doctor = staffAccountRepository.findById(doctorAccountId).orElse(null);

        String sovaovien = visit != null ? visit.getSovaovien() : record.getSoba();
        String soluutru = record.getSoluutru() != null && !record.getSoluutru().isBlank()
                ? record.getSoluutru()
                : archive.getSoluutru();
        Instant closedAt = record.getClosedAt() != null ? record.getClosedAt() : clock.instant();

        RecordArchiveRetentionCategory retentionCategory = archive.getRetentionCategory() != null ? archive.getRetentionCategory() : RecordArchiveRetentionCategory.STANDARD;
        LocalDate expiresOn = archive.getExpiresOn() != null ? archive.getExpiresOn() : LocalDate.of(closedAt.atZone(CLINIC_ZONE).getYear() + 10, 12, 31);

        ArchivePackageBuilder.Closing closing = new ArchivePackageBuilder.Closing(
                visit, patient, record, conclusion, doctor, soluutru, closedAt, retentionCategory, expiresOn
        );

        byte[] content;
        String contentType;
        String extension;

        if (request.format() == ExportFormat.XML) {
            content = archivePackageBuilder.buildXml(closing);
            contentType = "application/xml; charset=UTF-8";
            extension = "xml";
        } else {
            content = archivePackageBuilder.build(closing);
            contentType = "application/json; charset=UTF-8";
            extension = "json";
        }

        String sha256;
        try {
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot compute SHA-256", e);
        }

        UUID exportLogId = ids.next();
        ExportLog log = ExportLog.dataExport(
                exportLogId,
                record.getId(),
                sovaovien,
                request.format(),
                purpose,
                recipient,
                null,
                sha256,
                "CV365/2025",
                actorAccountId,
                clock.instant()
        );
        exportLogRepository.save(log);

        if (actorAccountId != null) {
            auditLogService.recordBusinessChange(
                    actorAccountId,
                    AuditLogAction.EXPORT,
                    "MEDICAL_RECORD",
                    record.getId(),
                    record.getSoba(),
                    Map.of(
                            "action", "EXPORT_MEDICAL_RECORD_DATA",
                            "format", request.format().name(),
                            "purpose", purpose.name(),
                            "sha256", sha256,
                            "exportLogId", exportLogId.toString()
                    ),
                    clock.instant()
            );
        }

        String fileName = "HoSoBenhAn_" + record.getSoba() + "_" + sovaovien + "." + extension;
        return new ExportMedicalRecordDataResponse(
                fileName,
                contentType,
                content,
                sha256,
                request.format(),
                request.purpose()
        );
    }
}
