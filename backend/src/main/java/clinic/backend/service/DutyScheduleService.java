package clinic.backend.service;

import java.time.LocalDate;
import java.util.UUID;
import clinic.backend.dto.request.DutyScheduleSaveRequest;
import clinic.backend.dto.request.WeeklyBatchScheduleRequest;
import clinic.backend.dto.response.DutyScheduleItemResponse;
import clinic.backend.dto.response.PersonalDutyScheduleResponse;
import clinic.backend.dto.response.WeeklyScheduleResponse;

public interface DutyScheduleService {

    WeeklyScheduleResponse getWeeklySchedule(LocalDate startDate, LocalDate endDate);

    WeeklyScheduleResponse getWeeklyScheduleByRoom(UUID roomId, LocalDate startDate, LocalDate endDate);

    PersonalDutyScheduleResponse getPersonalSchedule(UUID accountId, LocalDate startDate, LocalDate endDate);

    DutyScheduleItemResponse saveDuty(DutyScheduleSaveRequest request, UUID currentUserId);

    WeeklyScheduleResponse batchSaveWeeklySchedule(WeeklyBatchScheduleRequest request, UUID currentUserId);

    void deleteDuty(UUID id);
}
