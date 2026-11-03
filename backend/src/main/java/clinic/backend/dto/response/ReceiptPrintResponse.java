package clinic.backend.dto.response;

import clinic.backend.enums.VisitDoituongbnLoai;

/**
 * Content of the printed receipt (UC-72, PF-06). {@code payment} carries the receipt, the patient and the lines; a
 * paraclinical line carries its assigned room, location and queue number (BR-28). For an examination fee the visit's
 * room is {@code payment.roomName} and its number is {@code visitQueueNumber}. {@code reprint} marks a copy (AF-01).
 */
public record ReceiptPrintResponse(
        boolean reprint,
        String facilityName,
        String cashierName,
        VisitDoituongbnLoai visitType,
        Short coverageRate,
        Integer visitQueueNumber,
        PaymentResponse payment
) {
}
