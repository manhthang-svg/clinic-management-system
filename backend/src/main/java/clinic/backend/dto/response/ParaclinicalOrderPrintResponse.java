package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.VisitDoituongbnLoai;

/**
 * Content of the printed paraclinical order slip (UC-50): one sheet per performing room (BR-50-04), without amounts
 * (BR-50-06) or cancelled services (BR-50-05). {@code reprint} marks a later print (BR-50-08).
 */
public record ParaclinicalOrderPrintResponse(
        UUID orderId,
        String sophieu,
        boolean reprint,
        Instant printedAt,
        Facility facility,
        PatientInfo patient,
        String orderingDepartment,
        String orderingRoom,
        String chandoansobo,
        String mucdochidinh,
        Instant orderedAt,
        String doctorName,
        List<Sheet> sheets
) {

    /** Facility header (UC-77). */
    public record Facility(String tenCskcb, String diachi, String sodienthoai) {
    }

    /** {@code insuranceCardNumber} is set only for an insurance visit. */
    public record PatientInfo(
            String fullName,
            Integer birthYear,
            Integer age,
            String gender,
            String address,
            String patientCode,
            String visitCode,
            VisitDoituongbnLoai visitType,
            String insuranceCardNumber
    ) {
    }

    /** One printed sheet. {@code room} is null only for an unreleased line without a chosen room (AF-03). */
    public record Sheet(int index, int total, Room room, String serviceGroupName, List<Service> services) {
    }

    public record Room(String roomCode, String roomName, String location) {
    }

    public record Service(String machidinh, String tenchidinh, BigDecimal soluong) {
    }
}
