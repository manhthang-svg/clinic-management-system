package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.response.MedicalRecordDetailResponse;
import clinic.backend.dto.response.MedicalRecordItemResponse;
import clinic.backend.dto.response.MedicalRecordPatientItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PatientVisitItemResponse;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.ImagingResultImage;
import clinic.backend.entity.LabResult;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.PrescriptionDetail;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.entity.VisitTransfer;
import clinic.backend.entity.VitalSign;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.ImagingResultImageRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.repository.VisitTransferRepository;
import clinic.backend.repository.VitalSignRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.MedicalRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicalRecordServiceImpl implements MedicalRecordService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final MedicalRecordRepository medicalRecordRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final ClinicalExaminationRepository clinicalExaminationRepository;
    private final ConclusionRepository conclusionRepository;
    private final FollowUpAppointmentRepository followUpAppointmentRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final VitalSignRepository vitalSignRepository;
    private final VisitTransferRepository visitTransferRepository;
    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final LabResultRepository labResultRepository;
    private final ImagingResultRepository imagingResultRepository;
    private final ImagingResultImageRepository imagingResultImageRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public PageResponse<MedicalRecordPatientItemResponse> searchPatients(
            LocalDate from,
            LocalDate to,
            String status,
            UUID roomId,
            String icd10,
            String keyword,
            Integer page,
            Integer size,
            UUID actorAccountId
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, "Ngày bắt đầu không được sau ngày kết thúc.");
        }

        int pageNum = page != null ? Math.max(0, page) : 0;
        int pageSize = size != null ? Math.min(MAX_PAGE_SIZE, Math.max(1, size)) : DEFAULT_PAGE_SIZE;

        String search = (keyword != null && !keyword.isBlank()) ? keyword.strip() : null;
        if (search != null && search.length() > 100) {
            search = search.substring(0, 100);
        }

        String icd = (icd10 != null && !icd10.isBlank()) ? icd10.strip() : null;
        String st = (status != null && !status.isBlank()) ? status.strip() : null;

        Page<MedicalRecordRepository.PatientMedicalRecordSummaryRow> rows = medicalRecordRepository.searchPatientsSummary(
                from, to, roomId, st, icd, search, PageRequest.of(pageNum, pageSize));

        List<MedicalRecordPatientItemResponse> items = rows.getContent().stream()
                .map(row -> {
                    String gender = "Chưa xác định";
                    if (row.getGender() != null) {
                        gender = switch (row.getGender()) {
                            case 1 -> "Nam";
                            case 2 -> "Nữ";
                            default -> "Chưa xác định";
                        };
                    }

                    return new MedicalRecordPatientItemResponse(
                            row.getPatientId(),
                            row.getPatientCode(),
                            row.getFullName(),
                            row.getBirthDate(),
                            gender,
                            row.getCitizenId(),
                            row.getPhoneNumber(),
                            row.getTotalVisits() != null ? row.getTotalVisits() : 0L,
                            row.getLastVisitDate(),
                            row.getLastDiagnosisIcd(),
                            row.getLastDiagnosisName()
                    );
                })
                .toList();

        if (actorAccountId != null) {
            auditLogService.recordBusinessChange(
                    actorAccountId,
                    AuditLogAction.VIEW_RECORD,
                    "PATIENT_LIST",
                    actorAccountId,
                    "PATIENT_SUMMARY",
                    Map.of(
                            "action", "SEARCH_MEDICAL_RECORD_PATIENTS",
                            "keyword", search != null ? search : "",
                            "totalResults", rows.getTotalElements()
                    ),
                    Instant.now()
            );
        }

        return new PageResponse<>(items,
                new PageResponse.PageMetadata(pageNum, pageSize, rows.getTotalElements(), rows.getTotalPages()));
    }

    @Override
    @Transactional
    public List<PatientVisitItemResponse> getPatientVisits(UUID patientId, UUID actorAccountId) {
        if (patientId == null) {
            return Collections.emptyList();
        }

        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, Map.of("patientId", "Bệnh nhân không tồn tại")));

        List<MedicalRecordRepository.PatientVisitRow> rows = medicalRecordRepository.findVisitsByPatientId(patientId);

        List<PatientVisitItemResponse> items = rows.stream()
                .map(row -> {
                    String doctorDisplayName = row.getDoctorName();
                    if (doctorDisplayName != null && row.getAcademicTitle() != null && !row.getAcademicTitle().isBlank()) {
                        doctorDisplayName = row.getAcademicTitle().strip() + " " + doctorDisplayName;
                    }

                    String visitTypeDisplay = "Dịch vụ";
                    if (row.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT) {
                        visitTypeDisplay = "BHYT";
                    }

                    String statusStr = row.getVisitStatus() != null ? row.getVisitStatus().name() : "WAITING";
                    String statusLabel = switch (statusStr) {
                        case "COMPLETED" -> "Hoàn thành";
                        case "ABANDONED" -> "Bỏ khám";
                        default -> "Đang hoạt động";
                    };

                    return new PatientVisitItemResponse(
                            row.getVisitId(),
                            row.getMedicalRecordId(),
                            row.getRegistrationCode(),
                            row.getRegisteredAt(),
                            row.getRoomName(),
                            doctorDisplayName,
                            visitTypeDisplay,
                            row.getFinalDiagnosisIcd(),
                            row.getFinalDiagnosisName(),
                            statusStr,
                            statusLabel
                    );
                })
                .toList();

        if (actorAccountId != null) {
            auditLogService.recordBusinessChange(
                    actorAccountId,
                    AuditLogAction.VIEW_RECORD,
                    "PATIENT",
                    patientId,
                    patient.getMabenhnhan(),
                    Map.of(
                            "action", "VIEW_PATIENT_VISITS_HISTORY",
                            "patientCode", patient.getMabenhnhan(),
                            "totalVisits", items.size()
                    ),
                    Instant.now()
            );
        }

        return items;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MedicalRecordItemResponse> getMedicalRecords(
            LocalDate from,
            LocalDate to,
            MedicalRecordStatus status,
            UUID roomId,
            String keyword,
            Integer page,
            Integer size
    ) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, "Ngày bắt đầu không được sau ngày kết thúc.");
        }
        int pageNum = page != null ? Math.max(0, page) : 0;
        int pageSize = size != null ? Math.min(MAX_PAGE_SIZE, Math.max(1, size)) : DEFAULT_PAGE_SIZE;

        String search = (keyword != null && !keyword.isBlank()) ? keyword.strip() : null;
        if (search != null && search.length() > 100) {
            search = search.substring(0, 100);
        }

        Page<MedicalRecordRepository.MedicalRecordRow> rows = medicalRecordRepository.findMedicalRecords(
                from, to, status, roomId, search, PageRequest.of(pageNum, pageSize));

        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));

        List<MedicalRecordItemResponse> items = rows.getContent().stream()
                .map(row -> {
                    Integer age = null;
                    if (row.getDateOfBirth() != null) {
                        age = Period.between(row.getDateOfBirth(), today).getYears();
                    }
                    String gender = "Khác";
                    if (row.getGender() != null) {
                        gender = switch (row.getGender()) {
                            case 1 -> "Nam";
                            case 2 -> "Nữ";
                            default -> "Khác";
                        };
                    }
                    String doctorDisplayName = row.getDoctorName();
                    if (doctorDisplayName != null && row.getAcademicTitle() != null && !row.getAcademicTitle().isBlank()) {
                        doctorDisplayName = row.getAcademicTitle().strip() + " " + doctorDisplayName;
                    }

                    boolean isLocked = row.getStatus() == MedicalRecordStatus.LOCKED
                            || row.getStatus() == MedicalRecordStatus.ARCHIVED;

                    return new MedicalRecordItemResponse(
                            row.getMedicalRecordId(),
                            row.getRecordNumber(),
                            row.getRecordType(),
                            row.getStatus(),
                            row.getOpenedAt(),
                            row.getClosedAt(),
                            row.getVisitId(),
                            row.getVisitCode(),
                            row.getQueueDate(),
                            row.getPatientId(),
                            row.getPatientCode(),
                            row.getPatientName(),
                            row.getDateOfBirth(),
                            age,
                            gender,
                            row.getCitizenId(),
                            row.getPhoneNumber(),
                            row.getRoomId(),
                            row.getRoomName(),
                            row.getDoctorAccountId(),
                            doctorDisplayName,
                            row.getIcdCode(),
                            row.getIcdName(),
                            row.getClinicalDiagnosis(),
                            isLocked
                    );
                })
                .toList();

        return new PageResponse<>(items,
                new PageResponse.PageMetadata(pageNum, pageSize, rows.getTotalElements(), rows.getTotalPages()));
    }

    @Override
    @Transactional
    public MedicalRecordDetailResponse getMedicalRecordDetail(UUID id) {
        return getMedicalRecordDetail(id, null);
    }

    @Override
    @Transactional
    public MedicalRecordDetailResponse getMedicalRecordDetail(UUID id, UUID actorAccountId) {
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, Map.of("id", "Hồ sơ bệnh án không tồn tại")));

        if (actorAccountId != null) {
            auditLogService.recordBusinessChange(
                    actorAccountId,
                    AuditLogAction.VIEW_RECORD,
                    "MEDICAL_RECORD",
                    id,
                    record.getSoba(),
                    Map.of("action", "VIEW_MEDICAL_RECORD_DETAIL", "medicalRecordId", id.toString()),
                    Instant.now()
            );
        }

        return buildDetailResponse(record, actorAccountId);
    }

    @Override
    @Transactional
    public MedicalRecordDetailResponse getMedicalRecordByVisitId(UUID visitId) {
        return getMedicalRecordByVisitId(visitId, null);
    }

    @Override
    @Transactional
    public MedicalRecordDetailResponse getMedicalRecordByVisitId(UUID visitId, UUID actorAccountId) {
        MedicalRecord record = medicalRecordRepository.findByVisitId(visitId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, Map.of("visitId", "Hồ sơ bệnh án của lượt khám không tồn tại")));

        if (actorAccountId != null) {
            auditLogService.recordBusinessChange(
                    actorAccountId,
                    AuditLogAction.VIEW_RECORD,
                    "MEDICAL_RECORD",
                    record.getId(),
                    record.getSoba(),
                    Map.of("action", "VIEW_MEDICAL_RECORD_BY_VISIT", "visitId", visitId.toString()),
                    Instant.now()
            );
        }

        return buildDetailResponse(record, actorAccountId);
    }

    @Override
    @Transactional
    public void recordFileAccess(UUID imageId, UUID actorAccountId) {
        if (imageId == null || actorAccountId == null) return;
        ImagingResultImage image = imagingResultImageRepository.findById(imageId).orElse(null);
        String fileName = image != null ? image.getFileName() : imageId.toString();
        auditLogService.recordBusinessChange(
                actorAccountId,
                AuditLogAction.VIEW_RECORD,
                "PARACLINICAL_FILE",
                imageId,
                fileName,
                Map.of("action", "VIEW_PARACLINICAL_RESULT_FILE", "imageId", imageId.toString()),
                Instant.now()
        );
    }

    @Override
    @Transactional
    public void recordExportLog(UUID medicalRecordId, String scope, UUID actorAccountId) {
        if (medicalRecordId == null || actorAccountId == null) return;
        MedicalRecord record = medicalRecordRepository.findById(medicalRecordId).orElse(null);
        String soba = record != null ? record.getSoba() : medicalRecordId.toString();
        auditLogService.recordBusinessChange(
                actorAccountId,
                AuditLogAction.EXPORT,
                "MEDICAL_RECORD",
                medicalRecordId,
                soba,
                Map.of("action", "EXPORT_MEDICAL_RECORD_PDF", "scope", scope != null ? scope : "CURRENT_VISIT"),
                Instant.now()
        );
    }

    private MedicalRecordDetailResponse buildDetailResponse(MedicalRecord record, UUID actorAccountId) {
        Visit visit = visitRepository.findById(record.getVisitId()).orElse(null);
        Patient patient = visit != null ? patientRepository.findById(visit.getPatientId()).orElse(null) : null;
        Room room = visit != null ? roomRepository.findById(visit.getRoomId()).orElse(null) : null;
        StaffAccount doctor = (visit != null && visit.getDoctorAccountId() != null)
                ? staffAccountRepository.findById(visit.getDoctorAccountId()).orElse(null)
                : null;

        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        Integer age = null;
        String gender = "Chưa xác định";
        if (patient != null) {
            if (patient.getNgaysinh() != null) {
                age = Period.between(patient.getNgaysinh(), today).getYears();
            }
            gender = switch (patient.getGioitinh()) {
                case 1 -> "Nam";
                case 2 -> "Nữ";
                default -> "Chưa xác định";
            };
        }

        // Allergies & Medical History
        List<String> drugAllergies = new ArrayList<>();
        StringBuilder historySummary = new StringBuilder();
        if (patient != null) {
            List<MedicalHistory> histories = medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(patient.getId());
            for (MedicalHistory h : histories) {
                if (h.getHistoryType() == MedicalHistoryType.DRUG_ALLERGY) {
                    drugAllergies.add(h.getThongtinNoidung());
                } else {
                    if (!historySummary.isEmpty()) {
                        historySummary.append("; ");
                    }
                    historySummary.append(h.getThongtinNoidung());
                }
            }
        }

        String doctorName = null;
        if (doctor != null) {
            doctorName = doctor.getFullName();
            if (doctor.getAcademicTitle() != null && !doctor.getAcademicTitle().isBlank()) {
                doctorName = doctor.getAcademicTitle().strip() + " " + doctorName;
            }
        }

        // Visit details
        String visitTypeDisplay = "Dịch vụ";
        if (visit != null && visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT) {
            visitTypeDisplay = "BHYT";
        }

        String visitStatusStr = visit != null && visit.getStatus() != null ? visit.getStatus().name() : "WAITING";
        boolean isAbandoned = (visit != null && visit.getStatus() == VisitStatus.ABANDONED);
        boolean isEditable = false;
        if (actorAccountId != null && visit != null) {
            boolean isOwnerDoctor = actorAccountId.equals(visit.getDoctorAccountId());
            boolean isUnfinished = visit.getStatus() != VisitStatus.COMPLETED && visit.getStatus() != VisitStatus.ABANDONED;
            boolean isOpenRecord = record.getStatus() == MedicalRecordStatus.OPEN;
            isEditable = isOwnerDoctor && isUnfinished && isOpenRecord;
        }

        // Transfers
        List<MedicalRecordDetailResponse.TransferItem> transferItems = new ArrayList<>();
        if (visit != null) {
            List<VisitTransfer> transfers = visitTransferRepository.findAllByVisitIdOrderByTransferredAtAsc(visit.getId());
            Map<UUID, String> roomNameCache = new HashMap<>();
            for (VisitTransfer t : transfers) {
                String fromName = roomNameCache.computeIfAbsent(t.getFromRoomId(), id ->
                        roomRepository.findById(id).map(Room::getRoomName).orElse("Phòng không xác định"));
                String toName = roomNameCache.computeIfAbsent(t.getToRoomId(), id ->
                        roomRepository.findById(id).map(Room::getRoomName).orElse("Phòng không xác định"));
                transferItems.add(new MedicalRecordDetailResponse.TransferItem(
                        t.getId(),
                        t.getFromRoomId(),
                        fromName,
                        t.getToRoomId(),
                        toName,
                        t.getReason(),
                        t.getTransferredAt()
                ));
            }
        }

        // Vital Signs
        VitalSign vital = vitalSignRepository.findByMedicalRecordIdAndIsInitialTrue(record.getId()).orElse(null);
        MedicalRecordDetailResponse.VitalSignsItem vitalSignsItem = null;
        if (vital != null) {
            BigDecimal bmi = null;
            if (vital.getCannang() != null && vital.getChieucao() != null && vital.getChieucao().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal heightInM = vital.getChieucao().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                bmi = vital.getCannang().divide(heightInM.multiply(heightInM), 1, RoundingMode.HALF_UP);
            }

            vitalSignsItem = new MedicalRecordDetailResponse.VitalSignsItem(
                    vital.getMach(),
                    vital.getNhietdo(),
                    vital.getNhiptho(),
                    vital.getHuyetapTamthu(),
                    vital.getHuyetapTamtruong(),
                    vital.getCannang(),
                    vital.getChieucao(),
                    bmi,
                    vital.getSpo2(),
                    vital.getThoidiem()
            );
        }

        // Clinical examination
        ClinicalExamination clinicalExam = clinicalExaminationRepository.findByMedicalRecordId(record.getId()).orElse(null);
        MedicalRecordDetailResponse.ClinicalExamDetail examDetail = null;

        if (clinicalExam != null) {
            List<MedicalRecordDetailResponse.DiagnosisItem> examDiagnoses = clinicalExam.getDiagnoses().stream()
                    .map(d -> new MedicalRecordDetailResponse.DiagnosisItem(d.getMaicd(), d.getTenicd(), d.getThuTu() == 1))
                    .toList();

            examDetail = new MedicalRecordDetailResponse.ClinicalExamDetail(
                    clinicalExam.getChandoansobo(),
                    clinicalExam.getQuatrinhbenhly(),
                    clinicalExam.getKhambenhToanthan(),
                    clinicalExam.getKhambenhBophan(),
                    examDiagnoses
            );
        }

        // Paraclinical orders & results
        List<MedicalRecordDetailResponse.ParaclinicalOrderItem> orderItems = new ArrayList<>();
        List<ParaclinicalOrder> orders = paraclinicalOrderRepository.findAllByMedicalRecordId(record.getId());
        Map<UUID, String> staffNameCache = new HashMap<>();
        Map<UUID, String> roomCache = new HashMap<>();

        for (ParaclinicalOrder o : orders) {
            List<ParaclinicalOrderDetail> details = paraclinicalOrderDetailRepository.findByOrderId(o.getId());
            List<MedicalRecordDetailResponse.ParaclinicalServiceItem> serviceItems = new ArrayList<>();

            for (ParaclinicalOrderDetail d : details) {
                String performerName = null;
                if (d.getPerformerAccountId() != null) {
                    performerName = staffNameCache.computeIfAbsent(d.getPerformerAccountId(), id ->
                            staffAccountRepository.findById(id).map(StaffAccount::getFullName).orElse(null));
                }

                String pRoomName = null;
                if (d.getRoomId() != null) {
                    pRoomName = roomCache.computeIfAbsent(d.getRoomId(), id ->
                            roomRepository.findById(id).map(Room::getRoomName).orElse(null));
                }

                MedicalRecordDetailResponse.LabResultDetail labDetail = null;
                MedicalRecordDetailResponse.ImagingResultDetail imagingDetail = null;
                String resultSummary = null;

                // Check Lab Result
                LabResult lab = labResultRepository.findByOrderDetailId(d.getId()).orElse(null);
                if (lab != null) {
                    List<MedicalRecordDetailResponse.LabItem> labItems = lab.getItems().stream()
                            .map(it -> new MedicalRecordDetailResponse.LabItem(
                                    it.getPkqxnTenxetnghiem(),
                                    it.getKetqua(),
                                    it.getTrisothamchieu(),
                                    it.getDonvi(),
                                    "1".equals(it.getKetquabatthuong()) ? "Bất thường" : "Bình thường"
                            ))
                            .toList();
                    labDetail = new MedicalRecordDetailResponse.LabResultDetail(
                            lab.getId(),
                            lab.getSophieu(),
                            lab.getChandoan(),
                            lab.getGhichu(),
                            labItems
                    );
                    resultSummary = lab.getChandoan() != null ? lab.getChandoan() : "Đã có kết quả xét nghiệm";
                }

                // Check Imaging Result
                ImagingResult imaging = imagingResultRepository.findByOrderDetailId(d.getId()).orElse(null);
                if (imaging != null) {
                    List<ImagingResultImage> imageEntities = imagingResultImageRepository
                            .findByImagingResultIdOrderByDisplayOrderAsc(imaging.getId());
                    List<MedicalRecordDetailResponse.ImageAttachment> attachments = imageEntities.stream()
                            .map(img -> new MedicalRecordDetailResponse.ImageAttachment(
                                    img.getId(),
                                    img.getHinhanh(),
                                    img.getFileName(),
                                    img.getMimeType(),
                                    img.getCaption()
                            ))
                            .toList();

                    imagingDetail = new MedicalRecordDetailResponse.ImagingResultDetail(
                            imaging.getId(),
                            imaging.getSophieu(),
                            imaging.getKqclsMota(),
                            imaging.getKqclsKetluan(),
                            imaging.getKqclsDenghi(),
                            imaging.getKqclsLinkhinhanh(),
                            attachments
                    );
                    resultSummary = imaging.getKqclsKetluan();
                }

                serviceItems.add(new MedicalRecordDetailResponse.ParaclinicalServiceItem(
                        d.getId(),
                        d.getMachidinh(),
                        d.getTenchidinh(),
                        d.getLoaichidinh(),
                        pRoomName,
                        d.getStatus() != null ? d.getStatus().name() : "WAITING",
                        d.getSoluong(),
                        d.getDongia(),
                        d.getThanhtien(),
                        performerName,
                        d.getPerformedAt(),
                        resultSummary,
                        labDetail,
                        imagingDetail
                ));
            }

            orderItems.add(new MedicalRecordDetailResponse.ParaclinicalOrderItem(
                    o.getId(),
                    o.getSophieu(),
                    o.getStatus() != null ? o.getStatus().name() : "RELEASED",
                    o.getCreatedAt(),
                    serviceItems
            ));
        }

        // Conclusion
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(record.getId()).orElse(null);
        MedicalRecordDetailResponse.ConclusionDetail conclusionDetail = null;
        if (conclusion != null) {
            conclusionDetail = new MedicalRecordDetailResponse.ConclusionDetail(
                    conclusion.getChandoan(),
                    conclusion.getMaicd(),
                    conclusion.getTenicd(),
                    conclusion.getHuongdieutri(),
                    conclusion.getLoidanbacsi(),
                    conclusion.getKetquadieutri(),
                    conclusion.getConcludedAt()
            );
        }

        // Prescription
        Prescription prescription = prescriptionRepository.findByMedicalRecordId(record.getId()).orElse(null);
        MedicalRecordDetailResponse.PrescriptionDetail prescriptionDetail = null;
        if (prescription != null) {
            List<MedicalRecordDetailResponse.PrescriptionMedicineItem> medItems = prescription.getDetails().stream()
                    .map(pd -> new MedicalRecordDetailResponse.PrescriptionMedicineItem(
                            pd.getMedicineId(),
                            pd.getTenthuocvattu(),
                            pd.getHoatchat(),
                            pd.getHamluong(),
                            pd.getDonvitinh(),
                            pd.getSoluongthuocvattu(),
                            pd.getCachdung(),
                            pd.getSoLanNgay(),
                            pd.getSoNgayDung(),
                            pd.getLieuMoiLan()
                    ))
                    .toList();

            prescriptionDetail = new MedicalRecordDetailResponse.PrescriptionDetail(
                    prescription.getId(),
                    prescription.getMadonthuoc(),
                    prescription.getSophieu(),
                    prescription.getLienthongStatus() != null ? prescription.getLienthongStatus().name() : "NEW",
                    prescription.getPrescribedAt(),
                    prescription.getPrintedAt(),
                    prescription.getLuuY(),
                    prescription.getLoiDan(),
                    prescription.getNgayTaiKham(),
                    medItems
            );
        }

        // Follow up appointment
        FollowUpAppointment appointment = followUpAppointmentRepository.findByMedicalRecordId(record.getId()).orElse(null);
        MedicalRecordDetailResponse.FollowUpDetail followUpDetail = null;
        if (appointment != null) {
            followUpDetail = new MedicalRecordDetailResponse.FollowUpDetail(
                    appointment.getId(),
                    appointment.getSoGiayHen(),
                    appointment.getFollowUpDate(),
                    appointment.getLoidanbacsi(),
                    appointment.getStatus() != null ? appointment.getStatus().name() : "SCHEDULED"
            );
        }

        // Visit Timeline
        List<MedicalRecordDetailResponse.VisitTimelineItem> timelineItems = new ArrayList<>();
        if (patient != null) {
            List<MedicalRecordRepository.PatientVisitRow> pastVisits = medicalRecordRepository.findVisitsByPatientId(patient.getId());
            for (MedicalRecordRepository.PatientVisitRow pvr : pastVisits) {
                LocalDate vDate = pvr.getRegisteredAt() != null
                        ? pvr.getRegisteredAt().atZone(CLINIC_ZONE).toLocalDate()
                        : null;
                String dName = pvr.getDoctorName();
                if (dName != null && pvr.getAcademicTitle() != null && !pvr.getAcademicTitle().isBlank()) {
                    dName = pvr.getAcademicTitle().strip() + " " + dName;
                }

                timelineItems.add(new MedicalRecordDetailResponse.VisitTimelineItem(
                        pvr.getVisitId(),
                        pvr.getMedicalRecordId(),
                        pvr.getRegistrationCode(),
                        vDate,
                        pvr.getRoomName(),
                        dName,
                        pvr.getVisitStatus() != null ? pvr.getVisitStatus().name() : null,
                        visit != null && pvr.getVisitId().equals(visit.getId()),
                        pvr.getFinalDiagnosisIcd(),
                        pvr.getFinalDiagnosisName()
                ));
            }
        }

        return new MedicalRecordDetailResponse(
                record.getId(),
                record.getSoba(),
                record.getLoaiba(),
                record.getSoluutru(),
                record.getStatus(),
                record.getOpenedAt(),
                record.getClosedAt(),
                record.getUnlockedAt(),
                patient != null ? patient.getId() : null,
                patient != null ? patient.getMabenhnhan() : null,
                patient != null ? patient.getHoten() : null,
                patient != null ? patient.getNgaysinh() : null,
                age,
                gender,
                patient != null ? patient.getDiachi() : null,
                patient != null ? patient.getSodienthoai() : null,
                patient != null ? patient.getCccdSo() : null,
                drugAllergies,
                historySummary.toString(),
                visit != null ? visit.getId() : null,
                visit != null ? visit.getSovaovien() : null,
                visit != null ? visit.getQueueDate() : null,
                visit != null ? visit.getThoigianvaovien() : null,
                room != null ? room.getId() : null,
                room != null ? room.getRoomName() : null,
                doctor != null ? doctor.getId() : null,
                doctorName,
                visitTypeDisplay,
                visit != null ? visit.getMabhyt() : null,
                visit != null && visit.isPriority(),
                visit != null && visit.getPriorityReason() != null ? visit.getPriorityReason().name() : null,
                visitStatusStr,
                isAbandoned,
                isEditable,
                visit != null ? visit.getLydovaovien() : null,
                transferItems,
                vitalSignsItem,
                examDetail,
                orderItems,
                conclusionDetail,
                prescriptionDetail,
                followUpDetail,
                timelineItems
        );
    }
}
