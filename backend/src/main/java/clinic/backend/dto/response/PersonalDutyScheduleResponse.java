package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.DutyShift;

public record PersonalDutyScheduleResponse(List<Shift> items) {
    public record Shift(UUID id, UUID staffAccountId, LocalDate workDate, DutyShift shift,
                        UUID roomId, String roomCode, String roomName, String startTime, String endTime) {}
}
