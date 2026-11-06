package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.DutySchedule;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.DutyShift;
import clinic.backend.enums.Role;
import clinic.backend.repository.DutyScheduleRepository;
import clinic.backend.repository.StaffAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExaminationRoomDutyTest {

    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final UUID DOCTOR_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);
    // 09:00 in Asia/Ho_Chi_Minh, during the morning shift.
    private static final Instant MORNING = Instant.parse("2026-11-06T02:00:00Z");

    @Mock DutyScheduleRepository dutyScheduleRepository;
    @Mock StaffAccountRepository staffAccountRepository;
    @InjectMocks ExaminationRoomDuty duty;

    @Test
    void picksTheShiftARegistrationIsFor() {
        assertThat(ExaminationRoomDuty.registrationShift(LocalTime.of(7, 30))).contains(DutyShift.MORNING);
        assertThat(ExaminationRoomDuty.registrationShift(LocalTime.of(11, 30))).contains(DutyShift.MORNING);
        assertThat(ExaminationRoomDuty.registrationShift(LocalTime.of(12, 15))).contains(DutyShift.AFTERNOON);
        assertThat(ExaminationRoomDuty.registrationShift(LocalTime.of(17, 30))).contains(DutyShift.AFTERNOON);
        assertThat(ExaminationRoomDuty.registrationShift(LocalTime.of(18, 0))).isEmpty();
    }

    @Test
    void returnsTheActiveDoctorScheduledForTheRoomInTheCurrentShift() {
        StaffAccount doctor = account(Role.DOCTOR, AccountStatus.ACTIVE);
        givenSchedule(DutyShift.MORNING, doctor);

        assertThat(duty.onDutyDoctor(ROOM_ID, MORNING)).contains(doctor);
    }

    @Test
    void hasNoDoctorWhenTheShiftHasNoScheduleOrTheStaffIsNotAnActiveDoctor() {
        when(dutyScheduleRepository.findByRoomIdAndWorkDateAndShift(ROOM_ID, TODAY, DutyShift.MORNING))
                .thenReturn(Optional.empty());
        assertThat(duty.onDutyDoctor(ROOM_ID, MORNING)).isEmpty();

        StaffAccount locked = account(Role.DOCTOR, AccountStatus.LOCKED);
        givenSchedule(DutyShift.MORNING, locked);
        assertThat(duty.onDutyDoctor(ROOM_ID, MORNING)).isEmpty();
    }

    @Test
    void hasNoDoctorAfterTheLastShiftOfTheDay() {
        assertThat(duty.onDutyDoctor(ROOM_ID, Instant.parse("2026-11-06T12:00:00Z"))).isEmpty();
        verifyNoInteractions(dutyScheduleRepository);
    }

    private void givenSchedule(DutyShift shift, StaffAccount staff) {
        DutySchedule schedule = mock(DutySchedule.class);
        when(schedule.getStaffAccountId()).thenReturn(DOCTOR_ID);
        when(dutyScheduleRepository.findByRoomIdAndWorkDateAndShift(ROOM_ID, TODAY, shift))
                .thenReturn(Optional.of(schedule));
        when(staffAccountRepository.findById(DOCTOR_ID)).thenReturn(Optional.of(staff));
    }

    private static StaffAccount account(Role role, AccountStatus status) {
        StaffAccount account = mock(StaffAccount.class);
        when(account.getRole()).thenReturn(role);
        when(account.getStatus()).thenReturn(status);
        return account;
    }
}
