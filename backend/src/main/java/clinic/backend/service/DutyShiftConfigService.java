package clinic.backend.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.DutyShiftConfigRequest;
import clinic.backend.dto.response.DutyShiftConfigResponse;
import clinic.backend.enums.DutyShift;

public interface DutyShiftConfigService {

    DutyShiftConfigResponse getConfigForDate(LocalDate date);

    DutyShiftConfigResponse updateConfig(DutyShiftConfigRequest request, UUID currentUserId);

    /** The shift in progress at {@code time} on {@code date} under that week's configured hours (bounds inclusive). */
    Optional<DutyShift> shiftAt(LocalDate date, LocalTime time);

    /**
     * The shift a registration at {@code time} on {@code date} is for. ASSUMPTION: until the end of the morning shift
     * it is the morning shift, from then until the end of the afternoon shift it is the afternoon shift (so a patient
     * registered at lunch waits for the afternoon doctor), and after that no shift remains today.
     */
    Optional<DutyShift> registrationShift(LocalDate date, LocalTime time);
}

