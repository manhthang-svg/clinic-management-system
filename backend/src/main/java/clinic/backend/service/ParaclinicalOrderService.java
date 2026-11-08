package clinic.backend.service;

import clinic.backend.dto.request.CancelParaclinicalServiceRequest;
import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
import clinic.backend.dto.response.ParaclinicalCancellationResponse;
import clinic.backend.dto.response.ParaclinicalOrderResponse;
import clinic.backend.dto.response.ParaclinicalServiceOptionResponse;

import java.util.List;
import java.util.UUID;

public interface ParaclinicalOrderService {

    List<ParaclinicalServiceOptionResponse> listOrderableServices();

    ParaclinicalOrderResponse createOrder(UUID doctorAccountId, CreateParaclinicalOrderRequest request);

    /** Orders of one visit, oldest first; only the Doctor of that visit may read them. */
    List<ParaclinicalOrderResponse> listOrdersByVisit(UUID doctorAccountId, UUID visitId);

    ParaclinicalOrderResponse getOrderById(UUID orderId);

    /**
     * BR-25, BR-41: the Doctor of the visit cancels one service the patient refused. Allowed while no Technician
     * accepted it and its receipt is not paid; the receipt is recalculated.
     */
    ParaclinicalCancellationResponse cancelService(
            UUID doctorAccountId, UUID orderDetailId, CancelParaclinicalServiceRequest request);
}
