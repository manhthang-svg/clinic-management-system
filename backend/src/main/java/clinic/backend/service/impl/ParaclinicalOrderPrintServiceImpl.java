package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.response.ParaclinicalOrderPrintResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.ParaclinicalOrderPrintService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-50: one sheet per performing room with the room and its location (BR-50-04), printed by the Doctor of the order.
 */
@Service
@RequiredArgsConstructor
public class ParaclinicalOrderPrintServiceImpl implements ParaclinicalOrderPrintService {

    private static final String ORDER_AUDIT_ENTITY = "paraclinical_orders";

    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public ParaclinicalOrderPrintResponse preparePrint(UUID doctorAccountId, UUID orderId) {
        Instant now = clock.instant();

        // 1. The order of a visit of this Doctor (BR-50-07, EF-01, EF-02)
        ParaclinicalOrder order = paraclinicalOrderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PARACLINICAL_ORDER_NOT_FOUND));
        MedicalRecord medicalRecord = medicalRecordRepository.findById(order.getMedicalRecordId())
                .orElseThrow(() -> new AppException(ErrorCode.PARACLINICAL_ORDER_NOT_FOUND));
        Visit visit = visitRepository.findById(medicalRecord.getVisitId())
                .orElseThrow(() -> new AppException(ErrorCode.VISIT_NOT_FOUND));
        if (!doctorAccountId.equals(order.getDoctorAccountId()) && !doctorAccountId.equals(visit.getDoctorAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // 2. BR-50-05: cancelled and abandoned services are left out; nothing left means nothing to print (EF-03)
        List<ParaclinicalOrderDetail> printable = order.getDetails().stream()
                .filter(detail -> detail.getStatus() != ParaclinicalOrderDetailStatus.CANCELLED
                        && detail.getStatus() != ParaclinicalOrderDetailStatus.ABANDONED)
                .sorted(Comparator.comparing(ParaclinicalOrderDetail::getMachidinh))
                .toList();
        if (order.getStatus() == ParaclinicalOrderStatus.CANCELLED || printable.isEmpty()) {
            throw new AppException(ErrorCode.PARACLINICAL_ORDER_NOT_PRINTABLE, Map.of("sophieu", order.getSophieu()));
        }

        List<ParaclinicalOrderPrintResponse.Sheet> sheets = buildSheets(printable);

        // 3. BR-50-08: every print is audited; a print after the first one is a reprint
        boolean reprint = auditLogService.hasAction(AuditLogAction.PRINT, ORDER_AUDIT_ENTITY, order.getId());
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("sophieu", order.getSophieu());
        audit.put("sheets", sheets.size());
        audit.put("reprint", reprint);
        auditLogService.recordBusinessChange(
                doctorAccountId, AuditLogAction.PRINT, ORDER_AUDIT_ENTITY, order.getId(), visit.getSovaovien(), audit,
                now);

        Room orderingRoom = roomRepository.findById(visit.getRoomId()).orElse(null);
        String orderingDepartment = orderingRoom == null ? null : departmentRepository
                .findById(orderingRoom.getDepartmentId())
                .map(department -> department.getDepartmentName())
                .orElse(null);
        String doctorName = staffAccountRepository.findById(order.getDoctorAccountId())
                .map(account -> displayName(account.getAcademicTitle(), account.getFullName()))
                .orElse(null);

        return new ParaclinicalOrderPrintResponse(
                order.getId(), order.getSophieu(), reprint, now, facility(), patient(visit),
                orderingDepartment, order.getNoichidinh(), order.getChandoansobo(), order.getMucdochidinh(),
                order.getCreatedAt(), doctorName, sheets);
    }

    /**
     * BR-50-04: one sheet per chosen room, ordered by room code. A line without a room (AF-03) is grouped by its
     * service group and printed last.
     */
    private List<ParaclinicalOrderPrintResponse.Sheet> buildSheets(List<ParaclinicalOrderDetail> printable) {
        Map<String, SheetDraft> drafts = new LinkedHashMap<>();
        for (ParaclinicalOrderDetail detail : printable) {
            Room room = detail.getRoomId() == null ? null : roomRepository.findById(detail.getRoomId()).orElse(null);
            UUID groupId = room != null ? room.getServiceGroupId() : medicalServiceRepository
                    .findById(detail.getServiceId())
                    .map(service -> service.getServiceGroupId())
                    .orElse(null);
            String key = room != null ? "room:" + room.getId() : "group:" + groupId;
            drafts.computeIfAbsent(key, ignored -> new SheetDraft(room, groupName(groupId), new ArrayList<>()))
                    .services()
                    .add(new ParaclinicalOrderPrintResponse.Service(
                            detail.getMachidinh(), detail.getTenchidinh(), detail.getSoluong()));
        }

        List<SheetDraft> ordered = drafts.values().stream()
                .sorted(Comparator
                        .comparing((SheetDraft draft) -> draft.room() == null)
                        .thenComparing(draft -> draft.room() == null ? "" : draft.room().getRoomCode())
                        .thenComparing(draft -> draft.serviceGroupName() == null ? "" : draft.serviceGroupName()))
                .toList();
        List<ParaclinicalOrderPrintResponse.Sheet> sheets = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            SheetDraft draft = ordered.get(i);
            ParaclinicalOrderPrintResponse.Room room = draft.room() == null ? null
                    : new ParaclinicalOrderPrintResponse.Room(
                            draft.room().getRoomCode(), draft.room().getRoomName(), draft.room().getLocation());
            sheets.add(new ParaclinicalOrderPrintResponse.Sheet(
                    i + 1, ordered.size(), room, draft.serviceGroupName(), List.copyOf(draft.services())));
        }
        return sheets;
    }

    private record SheetDraft(
            Room room, String serviceGroupName, List<ParaclinicalOrderPrintResponse.Service> services) {
    }

    private String groupName(UUID groupId) {
        return groupId == null ? null : serviceGroupRepository.findById(groupId)
                .map(group -> group.getServiceGroupName())
                .orElse(null);
    }

    private ParaclinicalOrderPrintResponse.Facility facility() {
        ClinicFacility facility = clinicFacilityRepository.findCurrent()
                .orElseThrow(() -> new AppException(ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED));
        return new ParaclinicalOrderPrintResponse.Facility(
                facility.getTenCskcb(), facility.getDiachi(), facility.getSodienthoai());
    }

    private ParaclinicalOrderPrintResponse.PatientInfo patient(Visit visit) {
        Patient patient = patientRepository.findById(visit.getPatientId())
                .orElseThrow(() -> new AppException(ErrorCode.PATIENT_NOT_FOUND));
        LocalDate birthDate = patient.getNgaysinh();
        Integer age = birthDate == null ? null : Period.between(birthDate, LocalDate.now(clock)).getYears();
        boolean insured = visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT;
        return new ParaclinicalOrderPrintResponse.PatientInfo(
                patient.getHoten(),
                birthDate == null ? null : birthDate.getYear(),
                age,
                gender(patient.getGioitinh()),
                patient.getDiachi(),
                patient.getMabenhnhan(),
                visit.getSovaovien(),
                visit.getDoituongbnLoai(),
                insured ? visit.getMabhyt() : null,
                birthDate);
    }

    private static String gender(short code) {
        return switch (code) {
            case 1 -> "Nam";
            case 2 -> "Nữ";
            default -> "Khác";
        };
    }

    private static String displayName(String academicTitle, String fullName) {
        return academicTitle == null || academicTitle.isBlank() ? fullName : academicTitle.strip() + " " + fullName;
    }
}
