package clinic.backend.service.impl;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.Role;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClosedRecordPrintGuardTest {

    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final UUID VISIT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();

    @Mock private VisitRepository visitRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private MedicalRecordRepository medicalRecordRepository;
    private ClosedRecordPrintGuard guard;
    private StaffAccount doctor;
    private Visit visit;
    private Patient patient;

    @BeforeEach
    void setUp() {
        guard = new ClosedRecordPrintGuard(
                visitRepository, patientRepository, staffAccountRepository, medicalRecordRepository);
        doctor = mock(StaffAccount.class);
        lenient().when(doctor.getId()).thenReturn(DOCTOR_ID);
        lenient().when(doctor.getRole()).thenReturn(Role.DOCTOR);
        lenient().when(doctor.getStatus()).thenReturn(AccountStatus.ACTIVE);
        lenient().when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(doctor));
        visit = mock(Visit.class);
        lenient().when(visit.getDoctorAccountId()).thenReturn(DOCTOR_ID);
        lenient().when(visit.getPatientId()).thenReturn(PATIENT_ID);
        lenient().when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.of(visit));
        patient = mock(Patient.class);
        lenient().when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
    }

    @ParameterizedTest
    @EnumSource(value = MedicalRecordStatus.class, names = {"LOCKED", "ARCHIVED"})
    void letsTheDoctorOfTheVisitPrintAClosedRecordWithoutRoomOrShift(MedicalRecordStatus status) {
        MedicalRecord record = record(status);

        var context = guard.require(principal(), VISIT_ID);

        assertThat(context.visit()).isSameAs(visit);
        assertThat(context.patient()).isSameAs(patient);
        assertThat(context.record()).isSameAs(record);
        assertThat(context.doctor()).isSameAs(doctor);
    }

    @Test
    void refusesWhileTheRecordIsOpen() {
        record(MedicalRecordStatus.OPEN);

        assertError(ErrorCode.PRINT_RECORD_NOT_CLOSED);
    }

    @Test
    void refusesAnotherDoctor() {
        when(visit.getDoctorAccountId()).thenReturn(UUID.randomUUID());

        assertError(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void refusesALockedDoctorAccount() {
        when(doctor.getStatus()).thenReturn(AccountStatus.LOCKED);

        assertError(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void refusesAnUnknownVisit() {
        when(visitRepository.findById(VISIT_ID)).thenReturn(Optional.empty());

        assertError(ErrorCode.VISIT_NOT_FOUND);
    }

    private MedicalRecord record(MedicalRecordStatus status) {
        MedicalRecord record = new MedicalRecord(
                UUID.randomUUID(), NOW, VISIT_ID, "BA-DK0001", "NGOAITRU", status, NOW, DOCTOR_ID);
        when(medicalRecordRepository.findByVisitId(VISIT_ID)).thenReturn(Optional.of(record));
        return record;
    }

    private void assertError(ErrorCode code) {
        assertThatThrownBy(() -> guard.require(principal(), VISIT_ID))
                .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }

    private static StaffPrincipal principal() {
        return new StaffPrincipal(DOCTOR_ID, UUID.randomUUID());
    }
}
