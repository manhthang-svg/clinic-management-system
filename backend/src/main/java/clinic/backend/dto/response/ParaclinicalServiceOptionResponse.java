package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.MedicalServiceType;

/**
 * An active paraclinical service the Doctor can order (UC-49 step 2), with the Active rooms that perform it and the
 * least busy one preselected (I1-D47, BR-50-01). {@code suggestedRoomId} is null when no Active room performs it.
 */
public record ParaclinicalServiceOptionResponse(
        UUID id,
        String code,
        String name,
        MedicalServiceType type,
        UUID serviceGroupId,
        String serviceGroupName,
        BigDecimal clinicPrice,
        BigDecimal insurancePrice,
        List<RoomOption> rooms,
        UUID suggestedRoomId
) {

    /** An Active paraclinical room of the service's group and its number of waiting services. */
    public record RoomOption(
            UUID roomId,
            String roomCode,
            String roomName,
            String location,
            long waitingCount,
            /** A staff member is on duty in the room in the current shift. */
            boolean onDuty
    ) {
    }
}
