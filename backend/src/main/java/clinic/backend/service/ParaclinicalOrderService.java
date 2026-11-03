package clinic.backend.service;

import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
import clinic.backend.dto.response.ParaclinicalOrderResponse;
import clinic.backend.dto.response.ParaclinicalServiceOptionResponse;

import java.util.List;
import java.util.UUID;

public interface ParaclinicalOrderService {

    List<ParaclinicalServiceOptionResponse> listOrderableServices();

    ParaclinicalOrderResponse createOrder(UUID doctorAccountId, CreateParaclinicalOrderRequest request);

    ParaclinicalOrderResponse getOrderById(UUID orderId);
}
