package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.ConclusionSecondaryDiagnosis;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.Role;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitInsuranceVerifyStatus;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConclusionPrintServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** 10:00 on 03/11/2026 in the clinic. */
    private static final Instant NOW = Instant.parse("2026-11-03T03:00:00Z");
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();

    @Mock private ClosedRecordPrintGuard guard;
    @Mock private ConclusionRepository conclusionRepository;
    @Mock private ClinicFacilityRepository clinicFacilityRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    private ConclusionPrintServiceImpl service;
    private Conclusion conclusion;

    @BeforeEach
    void setUp() {
        service = new ConclusionPrintServiceImpl(guard, conclusionRepository, clinicFacilityRepository,
                roomRepository, staffAccountRepository, Clock.fixed(NOW, ZONE));
        Patient patient = new Patient(
                UUID.randomUUID(), NOW, "BN001", null, null, "Nguyễn Văn An", LocalDate.of(1960, 5, 1),
                (short) 1, null, null, "Kinh", "01", "Việt Nam", "VN", "12 Lê Lợi", null, null,
                "Hà Nội", "001", null, null, null, null, null, null, null, null, null, UUID.randomUUID());
        Visit visit = new Visit(
                VISIT_ID, NOW, "DK0000000123", patient.getId(), ROOM_ID, UUID.randomUUID(), NOW,
                VisitDoituongbnLoai.DV, null, null, null, "Tăng huyết áp", false, null,
                LocalDate.ofInstant(NOW, ZONE), 1, VisitStatus.COMPLETED,
                VisitInsuranceVerifyStatus.NOT_APPLICABLE);
        MedicalRecord record = new MedicalRecord(
                UUID.randomUUID(), NOW, VISIT_ID, "BA-DK0001", "NGOAITRU", MedicalRecordStatus.LOCKED, NOW,
                DOCTOR_ID);
        StaffAccount doctor = StaffAccount.create(
                DOCTOR_ID, "doctor01", "hash", "Bác sĩ An", null, null, null, null, null, null, Role.DOCTOR, null, NOW);
        conclusion = new Conclusion(UUID.randomUUID(), NOW, record.getId());
        conclusion.record(DOCTOR_ID, "Tăng huyết áp độ 1", "I10", "Tăng huyết áp vô căn", "Điều trị ngoại trú",
                "Đỡ", "Ăn nhạt, đo huyết áp mỗi sáng", null, null, NOW);
        conclusion.replaceSecondaryDiagnoses(List.of(new ConclusionSecondaryDiagnosis(
                UUID.randomUUID(), NOW, conclusion, "E11", "Bệnh đái tháo đường type 2", (short) 1)));

        lenient().when(guard.require(any(), eq(VISIT_ID)))
                .thenReturn(new ConcludingVisitGuard.Context(visit, patient, record, doctor));
        lenient().when(conclusionRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.of(conclusion));
        ClinicFacility facility = mock(ClinicFacility.class);
        lenient().when(facility.getTenCskcb()).thenReturn("Phòng khám METPH");
        lenient().when(clinicFacilityRepository.findCurrent()).thenReturn(Optional.of(facility));
        Room room = mock(Room.class);
        lenient().when(room.getRoomName()).thenReturn("Phòng khám Nội 1");
        lenient().when(roomRepository.findById(ROOM_ID)).thenReturn(Optional.of(room));
        lenient().when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
    }

    @Test
    void printsTheConclusionSlip() {
        var slip = service.preparePrint(principal(), VISIT_ID);

        assertThat(slip.visitCode()).isEqualTo("DK0000000123");
        assertThat(slip.facility().name()).isEqualTo("Phòng khám METPH");
        assertThat(slip.patient().patientCode()).isEqualTo("BN001");
        assertThat(slip.patient().gender()).isEqualTo("Nam");
        assertThat(slip.patient().address()).isEqualTo("12 Lê Lợi, Hà Nội");
        assertThat(slip.visitDate()).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(slip.roomName()).isEqualTo("Phòng khám Nội 1");
        assertThat(slip.diagnosis().code()).isEqualTo("I10");
        assertThat(slip.secondaryDiagnoses()).extracting(item -> item.code()).containsExactly("E11");
        assertThat(slip.diagnosisText()).isEqualTo("Tăng huyết áp độ 1");
        assertThat(slip.treatmentPlan()).isEqualTo("Điều trị ngoại trú");
        assertThat(slip.treatmentResult()).isEqualTo("Đỡ");
        assertThat(slip.advice()).isEqualTo("Ăn nhạt, đo huyết áp mỗi sáng");
        assertThat(slip.doctorName()).isEqualTo("Bác sĩ An");
        assertThat(slip.printedAt()).isEqualTo(NOW);
        assertThat(slip.printedBy()).isEqualTo("Bác sĩ An");
    }

    @Test
    void previewsWithoutThePrintTime() {
        var slip = service.printPreview(principal(), VISIT_ID);

        assertThat(slip.printedAt()).isNull();
        assertThat(slip.printedBy()).isNull();
        assertThat(slip.diagnosis().code()).isEqualTo("I10");
    }

    @Test
    void printsOnlyOnceTheRecordIsClosed() {
        when(guard.require(any(), eq(VISIT_ID))).thenThrow(new AppException(ErrorCode.PRINT_RECORD_NOT_CLOSED));

        assertThatThrownBy(() -> service.preparePrint(principal(), VISIT_ID))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRINT_RECORD_NOT_CLOSED));
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
