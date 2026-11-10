package clinic.backend.dto.response;

import java.util.UUID;

public record TechnicianPendingItemResponse(
        UUID id,
        Integer queueNumber,
        String orderCode,
        UUID roomId,
        String priority,
        String priorityLabel,
        String patientId,
        String patientName,
        Integer birthYear,
        Integer age,
        String gender,
        String insuranceType,
        String insuranceNumber,
        String cccd,
        String phone,
        String serviceName,
        String serviceCode,
        String clinicalDiagnosis,
        String doctorName,
        String doctorRoom,
        String completedAtTime,
        long completedMinutesAgo,
        String status
) {}

