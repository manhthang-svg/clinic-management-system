package clinic.backend.service.impl;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

/**
 * Dịch vụ xử lý nghiệp vụ xuất dữ liệu hồ sơ bệnh án (UC-80 / BR-43).
 * Xuất hồ sơ bệnh án điện tử đã đóng sang định dạng XML hoặc JSON tuân thủ theo
 * cấu trúc quy chuẩn của Công văn 365/TTYQG-GPQLCL và Luật Lưu trữ 2024.
 *
 * <p>Quy trình nghiệp vụ bao gồm:
 * 1. Kiểm tra tính hợp lệ của tham số yêu cầu và quyền/trạng thái hồ sơ bệnh án (phải là CLOSED).
 * 2. Xác thực thông tin gói lưu trữ điện tử (RecordArchive) và thông tin người nhận theo mục đích xuất.
 * 3. Tái tạo ngữ cảnh Closing và ủy thác cho ArchivePackageBuilder tạo dữ liệu XML/JSON.
 * 4. Tính toán mã băm toàn vẹn SHA-256 cho nội dung file xuất.
 * 5. Lưu nhật ký xuất dữ liệu (ExportLog) và ghi vết kiểm toán (AuditLog) theo quy định bảo mật y tế.
 * 6. Đóng gói kết quả trả về gồm tên file, kiểu MIME, mảng byte dữ liệu, mã SHA-256 và định dạng xuất.
 */
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

    /**
     * Thực hiện xuất dữ liệu hồ sơ bệnh án điện tử đã đóng sang định dạng XML hoặc JSON chuẩn CV 365.
     *
     * @param medicalRecordId Định danh UUID của hồ sơ bệnh án cần xuất dữ liệu
     * @param request DTO chứa thông tin yêu cầu xuất: định dạng (XML/JSON), mục đích xuất (liên thông, cấp người bệnh...), người nhận
     * @param actorAccountId UUID của tài khoản nhân viên y tế thực hiện thao tác xuất
     * @return DTO {@link ExportMedicalRecordDataResponse} chứa mảng byte dữ liệu, tên file chuẩn, mã băm SHA-256, Content-Type
     * @throws AppException Nếu hồ sơ không tồn tại, hồ sơ chưa đóng, thiếu gói lưu trữ, hoặc thông tin người nhận không hợp lệ
     * @throws IllegalStateException Nếu hệ thống gặp sự cố khi tính toán mã băm SHA-256
     */
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

        Visit visit = visitRepository.findById(record.getVisitId()).orElse(null);
        Patient patient = visit != null ? patientRepository.findById(visit.getPatientId()).orElse(null) : null;
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(record.getId()).orElse(null);
        UUID doctorAccountId = record.getClosedByAccountId() != null ? record.getClosedByAccountId() : actorAccountId;
        StaffAccount doctor = staffAccountRepository.findById(doctorAccountId).orElse(null);

        ExportLogPurpose purpose = request.purpose() != null ? request.purpose() : ExportLogPurpose.LIEN_THONG;
        String rawRecipient = request.recipient() == null || request.recipient().isBlank() ? null : request.recipient().strip();
        String resolvedRecipient;

        if (purpose == ExportLogPurpose.CUNG_CAP_NGUOI_BENH) {
            String recipientType = request.recipientType() != null && !request.recipientType().isBlank()
                    ? request.recipientType().strip().toUpperCase()
                    : (rawRecipient != null ? "LEGACY" : "SELF");

            if ("SELF".equals(recipientType)) {
                String name = patient != null && patient.getHoten() != null && !patient.getHoten().isBlank()
                        ? patient.getHoten()
                        : (request.recipientName() != null ? request.recipientName().strip() : null);
                String cccd = patient != null && patient.getCccdSo() != null && !patient.getCccdSo().isBlank()
                        ? patient.getCccdSo()
                        : (request.recipientCccd() != null ? request.recipientCccd().strip() : null);

                if (name == null || name.isBlank()) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "recipientName", "reason", "REQUIRED"));
                }
                if (cccd != null && !cccd.isBlank() && !cccd.matches("^\\d{12}$")) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "recipientCccd", "reason", "INVALID_CCCD"));
                }
                resolvedRecipient = name + (cccd != null && !cccd.isBlank() ? " (CCCD: " + cccd + ")" : "");
            } else if ("REPRESENTATIVE".equals(recipientType)) {
                String name = request.recipientName() != null ? request.recipientName().strip() : null;
                String cccd = request.recipientCccd() != null ? request.recipientCccd().strip() : null;
                LocalDate docDate = request.documentDate();

                if (name == null || name.isBlank()) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "recipientName", "reason", "REQUIRED"));
                }
                if (name.length() > 255) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "recipientName", "reason", "TOO_LONG"));
                }
                if (cccd == null || !cccd.matches("^\\d{12}$")) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "recipientCccd", "reason", "INVALID_CCCD"));
                }
                LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
                if (docDate != null && docDate.isAfter(today)) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "documentDate", "reason", "FUTURE_DATE"));
                }
                resolvedRecipient = "Người đại diện: " + name + " (CCCD: " + cccd + ")"
                        + (docDate != null ? " - Ngày VB: " + docDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) : "");
            } else if ("LEGACY".equals(recipientType)) {
                resolvedRecipient = rawRecipient;
            } else {
                throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "recipientType", "reason", "INVALID_TYPE"));
            }
        } else if (purpose == ExportLogPurpose.LIEN_THONG) {
            String facilityName = request.transferFacilityName() != null && !request.transferFacilityName().isBlank()
                    ? request.transferFacilityName().strip()
                    : rawRecipient;

            if (facilityName == null || facilityName.isBlank()) {
                throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "transferFacilityName", "reason", "REQUIRED"));
            }
            if (facilityName.length() > 255) {
                throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "transferFacilityName", "reason", "TOO_LONG"));
            }
            String code = request.transferFacilityCode() != null ? request.transferFacilityCode().strip() : null;
            resolvedRecipient = facilityName + (code != null && !code.isBlank() ? " (Mã: " + code + ")" : "");
        } else if (purpose == ExportLogPurpose.LUU_TRU) {
            resolvedRecipient = "Lưu trữ nội bộ";
        } else if (purpose == ExportLogPurpose.CO_QUAN_CO_THAM_QUYEN) {
            if (request.authorityName() != null && !request.authorityName().isBlank()) {
                if (request.authorityType() == null || request.authorityType().isBlank()) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "authorityType", "reason", "REQUIRED"));
                }
                String authName = request.authorityName().strip();
                if (authName.length() > 255) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "authorityName", "reason", "TOO_LONG"));
                }
                String docNum = request.documentNumber() != null ? request.documentNumber().strip() : null;
                if (docNum == null || docNum.isBlank()) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "documentNumber", "reason", "REQUIRED"));
                }
                if (docNum.length() > 100) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "documentNumber", "reason", "TOO_LONG"));
                }
                LocalDate docDate = request.documentDate();
                if (docDate == null) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "documentDate", "reason", "REQUIRED"));
                }
                LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
                if (docDate.isAfter(today)) {
                    throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "documentDate", "reason", "FUTURE_DATE"));
                }
                String rawType = request.authorityType().strip();
                String typeLabel;
                if ("CONG_AN".equals(rawType)) {
                    typeLabel = "Công an";
                } else if ("TOA_AN".equals(rawType)) {
                    typeLabel = "Tòa án";
                } else if ("VIEN_KIEM_SAT".equals(rawType)) {
                    typeLabel = "Viện kiểm sát";
                } else if ("THANH_TRA".equals(rawType)) {
                    typeLabel = "Thanh tra";
                } else if ("KHAC".equals(rawType)) {
                    String other = request.authorityTypeOther() != null ? request.authorityTypeOther().strip() : null;
                    if (other == null || other.isBlank()) {
                        throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "authorityTypeOther", "reason", "REQUIRED"));
                    }
                    if (other.length() > 100) {
                        throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "authorityTypeOther", "reason", "TOO_LONG"));
                    }
                    typeLabel = other;
                } else {
                    typeLabel = rawType;
                }
                String notes = request.documentNotes() != null ? request.documentNotes().strip() : null;
                resolvedRecipient = "[" + typeLabel + "] " + authName + " - Số: " + docNum + " - Ngày: "
                        + docDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + (notes != null && !notes.isBlank() ? " (" + notes + ")" : "");
            } else if (rawRecipient != null && !rawRecipient.isBlank()) {
                resolvedRecipient = rawRecipient;
            } else {
                throw new AppException(ErrorCode.EXPORT_INVALID, Map.of("field", "authorityName", "reason", "REQUIRED"));
            }
        } else {
            resolvedRecipient = "Lưu trữ nội bộ";
        }

        if (resolvedRecipient != null && resolvedRecipient.length() > 255) {
            resolvedRecipient = resolvedRecipient.substring(0, 255);
        }
        String recipient = resolvedRecipient;

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
