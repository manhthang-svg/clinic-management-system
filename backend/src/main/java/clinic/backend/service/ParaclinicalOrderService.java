package clinic.backend.service;

import clinic.backend.dto.request.CreateParaclinicalOrderRequest;
import clinic.backend.dto.response.ParaclinicalOrderResponse;

import java.util.UUID;

public interface ParaclinicalOrderService {

    ParaclinicalOrderResponse createOrder(CreateParaclinicalOrderRequest request);

    ParaclinicalOrderResponse getOrderById(UUID orderId);
}
