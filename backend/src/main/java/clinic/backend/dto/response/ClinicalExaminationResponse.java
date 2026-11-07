package clinic.backend.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.ClinicalExaminationNextStep;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;

public record ClinicalExaminationResponse(
        UUID visitId,
        UUID medicalRecordId,
        UUID examinationId,
        Long version,
        String visitCode,
        int queueNumber,
        VisitStatus visitStatus,
        Instant registeredAt,
        Instant examinationStartedAt,
        VisitDoituongbnLoai visitType,
        String insuranceCardNumber,
        UUID patientId,
        String patientCode,
        String patientName,
        LocalDate patientDateOfBirth,
        String patientGender,
        String occupation,
        String ethnicity,
        String address,
        UUID roomId,
        String roomCode,
        String roomName,
        UUID doctorAccountId,
        String doctorName,
        String reasonForVisit,
        String presentIllness,
        String generalFinding,
        VitalSignResponse vitalSigns,
        String organFinding,
        String preliminaryDiagnosis,
        Icd10OptionResponse preliminaryDiagnosisIcd10,
        String treatmentCarePerformed,
        List<MedicalHistoryItemResponse> medicalHistory,
        List<ClinicalExaminationNextStep> availableNextSteps,
        Instant updatedAt) {
}
