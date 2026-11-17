package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Department;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.RoomType;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
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
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParaclinicalOrderPrintServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    private static final UUID DOCTOR_ID = UUID.randomUUID();

    @Mock private ParaclinicalOrderRepository paraclinicalOrderRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private MedicalServiceRepository medicalServiceRepository;
    @Mock private ServiceGroupRepository serviceGroupRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private ClinicFacilityRepository clinicFacilityRepository;

    private ParaclinicalOrderPrintServiceImpl service;
    private Visit visit;
    private ParaclinicalOrder order;

    @BeforeEach
    void setUp() {
        service = new ParaclinicalOrderPrintServiceImpl(
                paraclinicalOrderRepository, medicalRecordRepository, visitRepository, patientRepository,
                roomRepository, departmentRepository, medicalServiceRepository, serviceGroupRepository,
                staffAccountRepository, clinicFacilityRepository,
                Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh")));

        visit = new Visit(UUID.randomUUID(), NOW, "DK0000000001", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), NOW, VisitDoituongbnLoai.DV, null, null, null, "Đau bụng", false, null,
                LocalDate.of(2026, 11, 10), 1, VisitStatus.WAITING, VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        visit.startExamination(DOCTOR_ID, NOW);
        MedicalRecord record = new MedicalRecord(UUID.randomUUID(), NOW, visit.getId(), "BA-1", "NGOAITRU",
                MedicalRecordStatus.OPEN, NOW, DOCTOR_ID);
        order = new ParaclinicalOrder(UUID.randomUUID(), NOW, "CD0000000042", record.getId(), DOCTOR_ID,
                UUID.randomUUID(), "Theo dõi đau bụng", (short) 2, null, "Phòng khám Nội 1",
                BigDecimal.valueOf(480000), ParaclinicalOrderStatus.PENDING_PAYMENT);

        lenient().when(paraclinicalOrderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        lenient().when(medicalRecordRepository.findById(record.getId())).thenReturn(Optional.of(record));
        lenient().when(visitRepository.findById(visit.getId())).thenReturn(Optional.of(visit));
    }

    @Test
    void printsOneSheetPerChosenRoomWithItsLocationAndWithoutCancelledServices() {
        Room ultrasoundRoom = room("SA1", "Phòng siêu âm 1", "Tầng 2, khu B");
        Room labRoom = room("XN1", "Phòng xét nghiệm", "Tầng 1");
        addDetail("CD0000000042-01", "Siêu âm ổ bụng", ultrasoundRoom, ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
        addDetail("CD0000000042-02", "Tổng phân tích máu", labRoom, ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
        addDetail("CD0000000042-03", "Glucose máu", labRoom, ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
        addDetail("CD0000000042-04", "Siêu âm tuyến giáp", ultrasoundRoom, ParaclinicalOrderDetailStatus.CANCELLED);
        stubHeader();

        var slip = service.preparePrint(DOCTOR_ID, order.getId());

        assertThat(slip.sophieu()).isEqualTo("CD0000000042");
        assertThat(slip.reprint()).isFalse();
        assertThat(slip.chandoansobo()).isEqualTo("Theo dõi đau bụng");
        assertThat(slip.mucdochidinh()).isEqualTo("Thường");
        assertThat(slip.orderingRoom()).isEqualTo("Phòng khám Nội 1");
        assertThat(slip.orderingDepartment()).isEqualTo("Khoa Nội");
        assertThat(slip.doctorName()).isEqualTo("BS. Trần Văn B");
        assertThat(slip.facility().tenCskcb()).isEqualTo("Phòng khám Đa khoa A");
        assertThat(slip.patient().fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(slip.patient().age()).isEqualTo(46);
        assertThat(slip.patient().insuranceCardNumber()).isNull();
        // BR-50-04: sheets ordered by room code; BR-50-05: the cancelled ultrasound is left out
        assertThat(slip.sheets())
                .extracting(s -> s.index(), s -> s.total(), s -> s.room().roomName(), s -> s.room().location())
                .containsExactly(
                        Tuple.tuple(1, 2, "Phòng siêu âm 1", "Tầng 2, khu B"),
                        Tuple.tuple(2, 2, "Phòng xét nghiệm", "Tầng 1"));
        assertThat(slip.sheets().get(0).services()).extracting(s -> s.tenchidinh())
                .containsExactly("Siêu âm ổ bụng");
        assertThat(slip.sheets().get(1).services()).extracting(s -> s.machidinh())
                .containsExactly("CD0000000042-02", "CD0000000042-03");

    }

    @Test
    void printsTheServiceGroupWhenAnOldLineHasNoChosenRoom() {
        UUID groupId = UUID.randomUUID();
        MedicalService medicalService = mock(MedicalService.class);
        when(medicalService.getServiceGroupId()).thenReturn(groupId);
        ServiceGroup group = mock(ServiceGroup.class);
        when(group.getServiceGroupName()).thenReturn("Chẩn đoán hình ảnh");
        ParaclinicalOrderDetail detail = addDetail("CD0000000042-01", "Siêu âm ổ bụng", null,
                ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
        when(medicalServiceRepository.findById(detail.getServiceId())).thenReturn(Optional.of(medicalService));
        when(serviceGroupRepository.findById(groupId)).thenReturn(Optional.of(group));
        stubHeader();

        var sheet = service.preparePrint(DOCTOR_ID, order.getId()).sheets().getFirst();

        assertThat(sheet.room()).isNull();
        assertThat(sheet.serviceGroupName()).isEqualTo("Chẩn đoán hình ảnh");
    }

    @Test
    void refusesAnOrderWhoseServicesWereAllCancelled() {
        addDetail("CD0000000042-01", "Siêu âm ổ bụng", null, ParaclinicalOrderDetailStatus.CANCELLED);

        assertThatThrownBy(() -> service.preparePrint(DOCTOR_ID, order.getId()))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARACLINICAL_ORDER_NOT_PRINTABLE));
    }

    @Test
    void refusesAnotherDoctor() {
        assertThatThrownBy(() -> service.preparePrint(UUID.randomUUID(), order.getId()))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void reportsAnUnknownOrder() {
        assertThatThrownBy(() -> service.preparePrint(DOCTOR_ID, UUID.randomUUID()))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.PARACLINICAL_ORDER_NOT_FOUND));
    }

    private ParaclinicalOrderDetail addDetail(
            String machidinh, String name, Room room, ParaclinicalOrderDetailStatus status) {
        ParaclinicalOrderDetail detail = new ParaclinicalOrderDetail(
                UUID.randomUUID(), NOW, order, UUID.randomUUID(), machidinh, NOW, name, null, null,
                BigDecimal.ONE, BigDecimal.valueOf(160000), BigDecimal.valueOf(160000), BigDecimal.ZERO,
                BigDecimal.valueOf(160000), room == null ? null : room.getId(),
                ParaclinicalOrderDetailStatus.PENDING_PAYMENT);
        if (status == ParaclinicalOrderDetailStatus.CANCELLED) {
            detail.cancelByDoctor(DOCTOR_ID, "Bệnh nhân từ chối", NOW);
        } else if (status == ParaclinicalOrderDetailStatus.WAITING) {
            detail.releaseTo(room == null ? null : room.getId(), 1, NOW);
        }
        order.addDetail(detail);
        if (room != null) {
            lenient().when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        }
        return detail;
    }

    private Room room(String code, String name, String location) {
        return new Room(UUID.randomUUID(), NOW, UUID.randomUUID(), UUID.randomUUID(), code, name, location,
                RoomType.PARACLINICAL, ActivationStatus.ACTIVE);
    }

    /** Facility, patient, ordering room, department and Doctor shown on every sheet. */
    private void stubHeader() {
        ClinicFacility facility = mock(ClinicFacility.class);
        when(facility.getTenCskcb()).thenReturn("Phòng khám Đa khoa A");
        when(clinicFacilityRepository.findCurrent()).thenReturn(Optional.of(facility));

        Patient patient = mock(Patient.class);
        when(patient.getHoten()).thenReturn("Nguyễn Văn A");
        when(patient.getNgaysinh()).thenReturn(LocalDate.of(1980, 5, 1));
        when(patient.getGioitinh()).thenReturn((short) 1);
        when(patientRepository.findById(visit.getPatientId())).thenReturn(Optional.of(patient));

        UUID departmentId = UUID.randomUUID();
        Room examinationRoom = new Room(visit.getRoomId(), NOW, departmentId, null, "PK1", "Phòng khám Nội 1",
                "Tầng 1", RoomType.EXAMINATION, ActivationStatus.ACTIVE);
        lenient().when(roomRepository.findById(visit.getRoomId())).thenReturn(Optional.of(examinationRoom));
        Department department = mock(Department.class);
        when(department.getDepartmentName()).thenReturn("Khoa Nội");
        when(departmentRepository.findById(departmentId)).thenReturn(Optional.of(department));

        StaffAccount doctor = mock(StaffAccount.class);
        when(doctor.getAcademicTitle()).thenReturn("BS.");
        when(doctor.getFullName()).thenReturn("Trần Văn B");
        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
    }
}
