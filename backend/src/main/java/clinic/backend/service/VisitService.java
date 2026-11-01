package clinic.backend.service;

import clinic.backend.dto.request.CreateVisitRequest;
import clinic.backend.dto.response.RoomQueueStatusResponse;
import clinic.backend.dto.response.VisitResponse;
import clinic.backend.dto.response.VisitFeePreviewResponse;
import clinic.backend.dto.response.VisitTicketResponse;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.security.user.StaffPrincipal;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface VisitService {

    VisitResponse createVisit(StaffPrincipal principal, CreateVisitRequest request);

    VisitResponse getActiveVisit(UUID patientId);

    VisitFeePreviewResponse previewFee(
            UUID patientId,
            VisitDoituongbnLoai visitType,
            String insuranceCardNumber);

    List<VisitResponse> getRoomQueue(UUID roomId, LocalDate date);

    VisitResponse startExamination(UUID visitId);

    List<RoomQueueStatusResponse> getRoomQueueStatus(LocalDate date);

    VisitTicketResponse getTicket(UUID visitId);

    VisitTicketResponse prepareTicketPrint(StaffPrincipal principal, UUID visitId);
}
