package clinic.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.response.MedicalRecordDetailResponse;
import clinic.backend.dto.response.MedicalRecordDetailResponse.ImageAttachment;
import clinic.backend.dto.response.MedicalRecordDetailResponse.ImagingResultDetail;
import clinic.backend.dto.response.MedicalRecordDetailResponse.LabResultDetail;
import clinic.backend.dto.response.MedicalRecordDetailResponse.ParaclinicalOrderItem;
import clinic.backend.dto.response.MedicalRecordDetailResponse.ParaclinicalServiceItem;
import clinic.backend.dto.response.MedicalRecordDetailResponse.TransferItem;
import clinic.backend.dto.response.MedicalRecordDetailResponse.VisitTimelineItem;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.LabResult;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.entity.VisitTransfer;
import clinic.backend.entity.VitalSign;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.mapper.MedicalRecordDetailMapper;
import clinic.backend.repository.ImagingResultImageRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * UC-52 BR-52-02: builds the read-only detail of one medical record. Each response costs a fixed number of
 * round trips (about a dozen), whatever the number of ordered services: the one-per-record parts come in one query,
 * related rows in batches. Runs in the caller's transaction.
 */
@Component
@RequiredArgsConstructor
class MedicalRecordDetailAssembler {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<VisitStatus> ENDED_VISIT_STATUSES =
            EnumSet.of(VisitStatus.COMPLETED, VisitStatus.ABANDONED, VisitStatus.CANCELLED);

    /** A record without any of the one-per-record parts yet. */
    private static final MedicalRecordRepository.DetailParts NO_PARTS = new MedicalRecordRepository.DetailParts() {
        @Override public VitalSign getVitalSign() { return null; }
        @Override public ClinicalExamination getExamination() { return null; }
        @Override public Conclusion getConclusion() { return null; }
        @Override public Prescription getPrescription() { return null; }
        @Override public FollowUpAppointment getFollowUp() { return null; }
    };

    private final MedicalRecordRepository medicalRecordRepository;
    private final RoomRepository roomRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final VisitTransferRepository visitTransferRepository;
    private final ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    private final LabResultRepository labResultRepository;
    private final ImagingResultRepository imagingResultRepository;
    private final ImagingResultImageRepository imagingResultImageRepository;
    private final MedicalRecordDetailMapper mapper;
    private final Clock clock;

    MedicalRecordDetailResponse assemble(MedicalRecordRepository.DetailHeader header, UUID actorAccountId) {
        Objects.requireNonNull(actorAccountId, "actorAccountId");
        MedicalRecord record = header.getRecord();
        Visit visit = header.getVisit();
        Patient patient = header.getPatient();
        StaffAccount doctor = header.getDoctor();
        MedicalRecordRepository.DetailParts parts = medicalRecordRepository.findDetailParts(record.getId())
                .orElse(NO_PARTS);
        List<VisitTransfer> transfers = visit != null
                ? visitTransferRepository.findAllByVisitIdOrderByTransferredAtAsc(visit.getId())
                : List.of();
        List<ParaclinicalOrderDetail> details =
                paraclinicalOrderDetailRepository.findWithOrderByMedicalRecordId(record.getId());

        // One query each for the other rooms and the performers the page names, none when there are none.
        Map<UUID, String> roomNames = new HashMap<>();
        if (header.getRoom() != null) roomNames.put(header.getRoom().getId(), header.getRoom().getRoomName());
        Set<UUID> roomIds = new HashSet<>();
        transfers.forEach(t -> {
            roomIds.add(t.getFromRoomId());
            roomIds.add(t.getToRoomId());
        });
        details.forEach(d -> roomIds.add(d.getRoomId()));
        roomIds.remove(null);
        roomIds.removeAll(roomNames.keySet());
        if (!roomIds.isEmpty()) {
            roomNames.putAll(names(roomRepository.findAllById(roomIds), Room::getId, Room::getRoomName));
        }
        Set<UUID> performerIds = details.stream().map(ParaclinicalOrderDetail::getPerformerAccountId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        // HashMap, not Map.of(): a service without a performer looks up a null key.
        Map<UUID, String> performerNames = performerIds.isEmpty() ? new HashMap<>()
                : names(staffAccountRepository.findAllById(performerIds), StaffAccount::getId, StaffAccount::getFullName);

        PatientHistory history = patientHistory(patient);
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
                patient != null ? age(patient.getNgaysinh()) : null,
                patient != null ? patient.getGioitinh() : null,
                patient != null ? patient.getDiachi() : null,
                patient != null ? patient.getSodienthoai() : null,
                patient != null ? patient.getCccdSo() : null,
                history.drugAllergies(),
                history.summary(),
                visit != null ? visit.getId() : null,
                visit != null ? visit.getSovaovien() : null,
                visit != null ? visit.getQueueDate() : null,
                visit != null ? visit.getThoigianvaovien() : null,
                visit != null ? visit.getRoomId() : null,
                visit != null ? roomNames.get(visit.getRoomId()) : null,
                doctor != null ? doctor.getId() : null,
                doctor != null ? doctorDisplayName(doctor.getAcademicTitle(), doctor.getFullName()) : null,
                visit != null ? visit.getDoituongbnLoai() : null,
                visit != null ? visit.getMabhyt() : null,
                visit != null && visit.isPriority(),
                visit != null ? visit.getPriorityReason() : null,
                visit != null ? visit.getStatus() : null,
                visit != null && visit.getStatus() == VisitStatus.ABANDONED,
                isEditable(record, visit, actorAccountId),
                visit != null ? visit.getLydovaovien() : null,
                transfers.stream().map(t -> toTransfer(t, roomNames)).toList(),
                parts.getVitalSign() != null ? mapper.toVitalSigns(parts.getVitalSign()) : null,
                parts.getExamination() != null ? mapper.toClinicalExam(parts.getExamination()) : null,
                paraclinicalOrders(details, roomNames, performerNames),
                parts.getConclusion() != null ? mapper.toConclusion(parts.getConclusion()) : null,
                parts.getPrescription() != null ? mapper.toPrescription(parts.getPrescription()) : null,
                parts.getFollowUp() != null ? mapper.toFollowUp(parts.getFollowUp()) : null,
                timeline(patient, visit)
        );
    }

    /** BR-52-03: the Doctor of the visit, a visit that has not ended, an open record. */
    private static boolean isEditable(MedicalRecord record, Visit visit, UUID actorAccountId) {
        return visit != null
                && actorAccountId.equals(visit.getDoctorAccountId())
                && !ENDED_VISIT_STATUSES.contains(visit.getStatus())
                && record.getStatus() == MedicalRecordStatus.OPEN;
    }

    private record PatientHistory(List<String> drugAllergies, String summary) {
    }

    /** Drug allergies for the red label; every other history entry joined into one summary. */
    private PatientHistory patientHistory(Patient patient) {
        if (patient == null) {
            return new PatientHistory(List.of(), "");
        }
        List<String> allergies = new ArrayList<>();
        List<String> others = new ArrayList<>();
        for (MedicalHistory entry : medicalHistoryRepository.findByPatientIdOrderByCreatedAtAsc(patient.getId())) {
            (entry.getHistoryType() == MedicalHistoryType.DRUG_ALLERGY ? allergies : others)
                    .add(entry.getThongtinNoidung());
        }
        return new PatientHistory(allergies, String.join("; ", others));
    }

    private Integer age(LocalDate dateOfBirth) {
        return dateOfBirth != null
                ? Period.between(dateOfBirth, LocalDate.now(clock.withZone(CLINIC_ZONE))).getYears()
                : null;
    }

    private static TransferItem toTransfer(VisitTransfer transfer, Map<UUID, String> roomNames) {
        return new TransferItem(
                transfer.getId(),
                transfer.getFromRoomId(),
                roomNames.get(transfer.getFromRoomId()),
                transfer.getToRoomId(),
                roomNames.get(transfer.getToRoomId()),
                transfer.getReason(),
                transfer.getTransferredAt());
    }

    /** BR-52-05: every ordered service; a result only once it is submitted (BR-29). */
    private List<ParaclinicalOrderItem> paraclinicalOrders(
            List<ParaclinicalOrderDetail> details,
            Map<UUID, String> roomNames,
            Map<UUID, String> performerNames) {
        if (details.isEmpty()) {
            return List.of();
        }
        List<UUID> detailIds = ids(details, ParaclinicalOrderDetail::getId);
        Map<UUID, LabResult> labs = detailIds.isEmpty() ? Map.of()
                : labResultRepository.findWithItemsByOrderDetailIdIn(detailIds).stream()
                        .filter(LabResult::isSubmitted)
                        .collect(Collectors.toMap(LabResult::getOrderDetailId, Function.identity()));
        Map<UUID, ImagingResult> imagings = detailIds.isEmpty() ? Map.of()
                : imagingResultRepository.findByOrderDetailIdIn(detailIds).stream()
                        .filter(ImagingResult::isSubmitted)
                        .collect(Collectors.toMap(ImagingResult::getOrderDetailId, Function.identity()));
        Map<UUID, List<ImageAttachment>> images = imagings.isEmpty() ? Map.of()
                : imagingResultImageRepository
                        .findByImagingResultIdInOrderByDisplayOrderAsc(ids(imagings.values(), ImagingResult::getId))
                        .stream()
                        .collect(Collectors.groupingBy(image -> image.getImagingResultId(), LinkedHashMap::new,
                                Collectors.mapping(mapper::toImage, Collectors.toList())));

        Map<UUID, ParaclinicalOrder> orders = new LinkedHashMap<>();
        Map<UUID, List<ParaclinicalServiceItem>> servicesByOrder = new LinkedHashMap<>();
        for (ParaclinicalOrderDetail detail : details) {
            orders.putIfAbsent(detail.getOrder().getId(), detail.getOrder());
            LabResult lab = labs.get(detail.getId());
            ImagingResult imaging = imagings.get(detail.getId());
            LabResultDetail labDetail = lab != null ? mapper.toLabResult(lab) : null;
            ImagingResultDetail imagingDetail = imaging != null
                    ? mapper.toImagingResult(imaging, images.getOrDefault(imaging.getId(), List.of()))
                    : null;
            String resultSummary = imaging != null ? imaging.getKqclsKetluan() : lab != null ? lab.getChandoan() : null;
            servicesByOrder.computeIfAbsent(detail.getOrder().getId(), id -> new ArrayList<>())
                    .add(new ParaclinicalServiceItem(
                            detail.getId(),
                            detail.getMachidinh(),
                            detail.getTenchidinh(),
                            detail.getLoaichidinh(),
                            roomNames.get(detail.getRoomId()),
                            detail.getStatus(),
                            detail.getSoluong(),
                            detail.getDongia(),
                            detail.getThanhtien(),
                            performerNames.get(detail.getPerformerAccountId()),
                            detail.getPerformedAt(),
                            resultSummary,
                            labDetail,
                            imagingDetail));
        }
        return orders.values().stream()
                .map(order -> new ParaclinicalOrderItem(
                        order.getId(),
                        order.getSophieu(),
                        order.getStatus(),
                        order.getCreatedAt(),
                        servicesByOrder.getOrDefault(order.getId(), List.of())))
                .toList();
    }

    /** BR-52-08: every visit of the patient, newest first, the shown one marked. */
    private List<VisitTimelineItem> timeline(Patient patient, Visit visit) {
        if (patient == null) {
            return List.of();
        }
        return medicalRecordRepository.findVisitsByPatientId(patient.getId()).stream()
                .map(row -> new VisitTimelineItem(
                        row.getVisitId(),
                        row.getMedicalRecordId(),
                        row.getRegistrationCode(),
                        row.getVisitDate(),
                        row.getRoomName(),
                        doctorDisplayName(row.getAcademicTitle(), row.getDoctorName()),
                        row.getVisitStatus(),
                        visit != null && row.getVisitId().equals(visit.getId()),
                        row.getFinalDiagnosisIcd(),
                        row.getFinalDiagnosisName()))
                .toList();
    }

    static String doctorDisplayName(String academicTitle, String fullName) {
        if (fullName == null || academicTitle == null || academicTitle.isBlank()) {
            return fullName;
        }
        return academicTitle.strip() + " " + fullName;
    }

    /** Id to display name; unlike {@code Collectors.toMap}, an empty name does not fail. */
    private static <T> Map<UUID, String> names(List<T> rows, Function<T, UUID> id, Function<T, String> name) {
        Map<UUID, String> names = new HashMap<>();
        rows.forEach(row -> names.put(id.apply(row), name.apply(row)));
        return names;
    }

    private static <T> List<UUID> ids(Collection<T> items, Function<T, UUID> id) {
        return items.stream().map(id).toList();
    }
}
