package clinic.backend.service;

import clinic.backend.dto.request.CreateVisitRequest;
import clinic.backend.dto.response.RoomQueueStatusResponse;
import clinic.backend.dto.response.DoctorQueueResponse;
import clinic.backend.dto.response.VisitResponse;
import clinic.backend.dto.response.VisitFeePreviewResponse;
import clinic.backend.dto.response.VisitTicketResponse;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.DoctorQueueTab;
import clinic.backend.enums.VisitStatus;
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

    DoctorQueueResponse getDoctorQueue(
            StaffPrincipal principal,
            DoctorQueueTab tab,
            String keyword,
            Boolean priority,
            VisitDoituongbnLoai visitType,
            VisitStatus interruptedStage,
            int page,
            int size);

    VisitResponse startExamination(StaffPrincipal principal, UUID visitId);

    VisitResponse skipVisit(StaffPrincipal principal, UUID visitId);

    VisitResponse acceptSkippedVisit(StaffPrincipal principal, UUID visitId);

    List<RoomQueueStatusResponse> getRoomQueueStatus(LocalDate date);

    VisitTicketResponse getTicket(UUID visitId);

    VisitTicketResponse prepareTicketPrint(StaffPrincipal principal, UUID visitId);
}
