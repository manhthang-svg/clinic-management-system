package clinic.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import clinic.backend.dto.response.MedicalRecordDetailResponse;
import clinic.backend.dto.response.MedicalRecordPatientItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PatientVisitHistoryResponse;
import clinic.backend.dto.response.PatientVisitItemResponse;
import clinic.backend.dto.response.ResultFileResponse;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.ImagingResultImage;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordVisitFilter;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ImagingResultImageRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.MedicalRecordService;
import clinic.backend.service.ResultImageStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-51 View List Medical Record, UC-52 View Medical Record Detail (docs/uc-51-52-medical-record-view.md). Every
 * read writes one {@code VIEW_RECORD} audit row in the same transaction (BR-51-07); the reads are not
 * {@code readOnly} for that reason.
 */
@Service
@RequiredArgsConstructor
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_TEXT_FILTER_LENGTH = 100;

    // BR-51-07: audit entity types of the UC-51/UC-52 reads.
    private static final String AUDIT_SEARCH = "MEDICAL_RECORD_SEARCH";
    private static final String AUDIT_PATIENT = "PATIENT";
    private static final String AUDIT_MEDICAL_RECORD = "MEDICAL_RECORD";
    private static final String AUDIT_RESULT_FILE = "IMAGING_RESULT_IMAGE";

    private final MedicalRecordRepository medicalRecordRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final ImagingResultRepository imagingResultRepository;
    private final ImagingResultImageRepository imagingResultImageRepository;
    private final ResultImageStorage resultImageStorage;
    private final MedicalRecordDetailAssembler detailAssembler;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public PageResponse<MedicalRecordPatientItemResponse> searchPatients(
            String keyword,
            LocalDate from,
            LocalDate to,
            UUID roomId,
            String icd10,
            MedicalRecordVisitFilter status,
            int page,
            int size,
            UUID actorAccountId
    ) {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        String search = normalizeKeyword(keyword);
        String icd = trimToNull(icd10);
        if (icd != null && icd.length() > MAX_TEXT_FILTER_LENGTH) {
            throw invalid("icd10", "TOO_LONG");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("from", "AFTER_TO");
        }
        if (page < 0) {
            throw invalid("page", "OUT_OF_RANGE");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw invalid("size", "OUT_OF_RANGE");
        }

        Set<VisitStatus> statuses = status != null ? status.statuses() : EnumSet.allOf(VisitStatus.class);
        Page<MedicalRecordRepository.PatientMedicalRecordSummaryRow> rows = medicalRecordRepository.searchPatientsSummary(
                likePattern(search), from, to, roomId,
                statuses.stream().map(Enum::name).toList(),
                likePattern(icd), PageRequest.of(page, size));

        List<MedicalRecordPatientItemResponse> items = rows.getContent().stream()
                .map(row -> new MedicalRecordPatientItemResponse(
                        row.getPatientId(),
                        row.getPatientCode(),
                        row.getFullName(),
                        row.getBirthDate(),
                        row.getGender(),
                        row.getTotalVisits() != null ? row.getTotalVisits() : 0L,
                        row.getLastVisitDate(),
                        row.getLastDiagnosisIcd(),
                        row.getLastDiagnosisName()))
                .toList();

        // BR-51-07: the names of the filters used, never their values.
        List<String> usedFilters = new ArrayList<>();
        if (search != null) usedFilters.add("keyword");
        if (from != null) usedFilters.add("from");
        if (to != null) usedFilters.add("to");
        if (roomId != null) usedFilters.add("roomId");
        if (icd != null) usedFilters.add("icd10");
        if (status != null) usedFilters.add("status");
        auditLogService.recordRead(actorAccountId, AUDIT_SEARCH, null, null,
                Map.of("filters", usedFilters, "resultCount", rows.getTotalElements()), clock.instant());

        return new PageResponse<>(items,
                new PageResponse.PageMetadata(page, size, rows.getTotalElements(), rows.getTotalPages()));
    }

    @Override
    @Transactional
    public PatientVisitHistoryResponse getPatientVisits(UUID patientId, UUID actorAccountId) {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new AppException(ErrorCode.PATIENT_NOT_FOUND));

        List<PatientVisitItemResponse> visits = medicalRecordRepository.findVisitsByPatientId(patientId).stream()
                .map(row -> new PatientVisitItemResponse(
                        row.getVisitId(),
                        row.getMedicalRecordId(),
                        row.getRegistrationCode(),
                        row.getRegisteredAt(),
                        row.getVisitDate(),
                        row.getRoomName(),
                        MedicalRecordDetailAssembler.doctorDisplayName(row.getAcademicTitle(), row.getDoctorName()),
                        row.getDoituongbnLoai(),
                        row.getFinalDiagnosisIcd(),
                        row.getFinalDiagnosisName(),
                        row.getVisitStatus(),
                        Boolean.TRUE.equals(row.getSkipped())))
                .toList();

        auditLogService.recordRead(actorAccountId, AUDIT_PATIENT, patientId, null,
                Map.of("visitCount", visits.size()), clock.instant());

        return new PatientVisitHistoryResponse(
                new PatientVisitHistoryResponse.Patient(
                        patient.getId(),
                        patient.getMabenhnhan(),
                        patient.getHoten(),
                        patient.getNgaysinh(),
                        patient.getGioitinh()),
                visits);
    }

    @Override
    @Transactional
    public MedicalRecordDetailResponse getMedicalRecordDetail(UUID id, UUID actorAccountId) {
        MedicalRecordRepository.DetailHeader header = medicalRecordRepository.findDetailHeaderById(id)
                .orElseThrow(() -> new AppException(ErrorCode.MEDICAL_RECORD_NOT_FOUND));
        return openDetail(header, actorAccountId, "RECORD_ID");
    }

    @Override
    @Transactional
    public MedicalRecordDetailResponse getMedicalRecordByVisitId(UUID visitId, UUID actorAccountId) {
        MedicalRecordRepository.DetailHeader header = medicalRecordRepository.findDetailHeaderByVisitId(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDICAL_RECORD_NOT_FOUND));
        return openDetail(header, actorAccountId, "VISIT_ID");
    }

    @Override
    @Transactional
    public ResultFileResponse downloadResultFile(UUID medicalRecordId, UUID fileId, UUID actorAccountId) {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        MedicalRecord record = medicalRecordRepository.findById(medicalRecordId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDICAL_RECORD_NOT_FOUND));
        ImagingResultImage image = imagingResultImageRepository.findById(fileId)
                .orElseThrow(() -> new AppException(ErrorCode.RESULT_IMAGE_NOT_FOUND));
        // BR-52-06: the file must belong to a submitted result of a service ordered in this very record.
        ImagingResult imaging = imagingResultRepository.findById(image.getImagingResultId())
                .filter(ImagingResult::isSubmitted)
                .filter(result -> paraclinicalOrderDetailRepository.findById(result.getOrderDetailId())
                        .map(detail -> medicalRecordId.equals(detail.getOrder().getMedicalRecordId()))
                        .orElse(false))
                .orElseThrow(() -> new AppException(ErrorCode.RESULT_IMAGE_NOT_FOUND));

        byte[] content = resultImageStorage.download(image.getHinhanh());

        String sovaovien = visitRepository.findById(record.getVisitId()).map(Visit::getSovaovien).orElse(null);
        auditLogService.recordRead(actorAccountId, AUDIT_RESULT_FILE, fileId, sovaovien,
                Map.of("medicalRecordId", medicalRecordId.toString()), clock.instant());
        return new ResultFileResponse(resultFileName(imaging, image), image.getMimeType(), content);
    }

    /** BR-52-01 and BR-51-07: one audit row per opening, written before the record is returned. */
    private MedicalRecordDetailResponse openDetail(
            MedicalRecordRepository.DetailHeader header, UUID actorAccountId, String openedBy) {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        auditLogService.recordRead(actorAccountId, AUDIT_MEDICAL_RECORD, header.getRecord().getId(),
                header.getVisit() != null ? header.getVisit().getSovaovien() : null,
                Map.of("openedBy", openedBy), clock.instant());
        return detailAssembler.assemble(header, actorAccountId);
    }

    /** BR-52-06: KQ-{số phiếu}-{thứ tự}.{đuôi}; the uploaded name may carry personal data, so it is never used. */
    private static String resultFileName(ImagingResult imaging, ImagingResultImage image) {
        String slip = imaging.getSophieu().replaceAll("[^A-Za-z0-9_-]", "_");
        int order = image.getDisplayOrder() != null ? image.getDisplayOrder() : 1;
        String extension = switch (image.getMimeType()) {
            case "image/png" -> "png";
            case "application/pdf" -> "pdf";
            default -> "jpg";
        };
        return "KQ-" + slip + "-" + order + "." + extension;
    }

    /** BR-51-04: trimmed; empty means no keyword filter. */
    private static String normalizeKeyword(String keyword) {
        String search = trimToNull(keyword);
        if (search == null) {
            return null;
        }
        if (search.length() < MIN_KEYWORD_LENGTH) {
            throw invalid("keyword", "TOO_SHORT");
        }
        if (search.length() > MAX_TEXT_FILTER_LENGTH) {
            throw invalid("keyword", "TOO_LONG");
        }
        return search;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Contains-match, lower-case; % and _ typed by the user are matched literally. */
    private static String likePattern(String value) {
        if (value == null) {
            return null;
        }
        String escaped = value.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static AppException invalid(String field, String reason) {
        return new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, Map.of(field, reason));
    }
}
