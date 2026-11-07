package clinic.backend.service;

import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
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
}
