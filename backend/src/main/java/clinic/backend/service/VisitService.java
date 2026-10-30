package clinic.backend.service;

import clinic.backend.dto.request.CreateVisitRequest;
import clinic.backend.dto.response.RoomQueueStatusResponse;
import clinic.backend.dto.response.VisitResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VisitService {

    VisitResponse createVisit(CreateVisitRequest request);

    List<VisitResponse> getRoomQueue(UUID roomId, LocalDate date);

    VisitResponse startExamination(UUID visitId);

    List<RoomQueueStatusResponse> getRoomQueueStatus(LocalDate date);
}
