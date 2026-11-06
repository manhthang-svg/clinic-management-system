package clinic.backend.service;

import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.dto.request.DutyShiftConfigRequest;
import clinic.backend.dto.response.DutyShiftConfigResponse;

public interface DutyShiftConfigService {

    DutyShiftConfigResponse getConfigForDate(LocalDate date);

    DutyShiftConfigResponse updateConfig(DutyShiftConfigRequest request, UUID currentUserId);
}

