package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.MedicalServiceType;

/**
 * UC-30 View Paraclinical Service Statistics: the paraclinical services performed in the period (performed time),
 * by service type, service group, service and room. A performed service either awaits its result or has it
 * submitted ({@code withResult}); services not yet performed, cancelled or abandoned are not counted.
 */
public record ParaclinicalStatisticsResponse(
        LocalDate from,
        LocalDate to,
        long performed,
        long withResult,
        List<TypeCount> byType,
        List<GroupCount> byGroup,
        List<ServiceCount> byService,
        List<RoomCount> byRoom) {

    /** LAB (xét nghiệm), IMAGING (chẩn đoán hình ảnh) or FUNCTIONAL (thăm dò chức năng). */
    public record TypeCount(MedicalServiceType type, long performed, long withResult) {
    }

    public record GroupCount(UUID groupId, String groupCode, String groupName, long performed, long withResult) {
    }

    public record ServiceCount(
            UUID serviceId, String serviceCode, String serviceName, MedicalServiceType type, String groupName,
            long performed, long withResult) {
    }

    public record RoomCount(UUID roomId, String roomCode, String roomName, long performed, long withResult) {
    }
}
