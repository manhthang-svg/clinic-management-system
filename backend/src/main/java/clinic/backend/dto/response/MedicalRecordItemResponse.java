package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import clinic.backend.enums.MedicalRecordStatus;

public record MedicalRecordItemResponse(
        UUID medicalRecordId,
        String recordNumber,
        String recordType,
        MedicalRecordStatus status,
        Instant openedAt,
        Instant closedAt,
        UUID visitId,
        String visitCode,
        LocalDate visitDate,
        UUID patientId,
        String patientCode,
        String patientName,
        LocalDate dateOfBirth,
        Integer age,
        String gender,
        String citizenId,
        String phoneNumber,
        UUID roomId,
        String roomName,
        UUID doctorAccountId,
        String doctorName,
        String icdCode,
        String icdName,
        String diagnosis,
        boolean isLocked
) {}

