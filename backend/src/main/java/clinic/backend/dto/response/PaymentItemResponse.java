package clinic.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentItemResponse(
        UUID id,
        UUID serviceId,
        String serviceCode,
        String tenchidinh,
        BigDecimal soluong,
        BigDecimal dongia,
        BigDecimal thanhtien,
        BigDecimal bhytChitra,
        BigDecimal benhnhanphaitra,
        String roomName,
        String roomLocation,
        Integer queueNumber
) {
    @JsonProperty("serviceName")
    public String serviceName() {
        return tenchidinh;
    }

    @JsonProperty("quantity")
    public BigDecimal quantity() {
        return soluong;
    }

    @JsonProperty("unitPrice")
    public BigDecimal unitPrice() {
        return dongia;
    }

    @JsonProperty("totalAmount")
    public BigDecimal totalAmount() {
        return thanhtien;
    }

    @JsonProperty("patientAmount")
    public BigDecimal patientAmount() {
        return benhnhanphaitra;
    }
}

