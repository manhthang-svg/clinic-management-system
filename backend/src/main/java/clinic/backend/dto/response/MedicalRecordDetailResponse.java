package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.FollowUpAppointmentStatus;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ParaclinicalOrderStatus;
import clinic.backend.enums.PrescriptionLienthongStatus;
import clinic.backend.enums.PriorityReason;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;

/**
 * UC-52 View Medical Record Detail (BR-52-02). Coded values are returned as codes ({@code gender} is the
 * {@code patients.gioitinh} code, statuses are enum names); the client shows their labels.
 */
public record MedicalRecordDetailResponse(
        UUID medicalRecordId,
        String recordNumber,
        String recordType,
        String storageNumber,
        MedicalRecordStatus status,
        Instant openedAt,
        Instant closedAt,
        Instant unlockedAt,

        // Patient header (step 1)
        UUID patientId,
        String patientCode,
        String patientName,
        LocalDate dateOfBirth,
        Integer age,
        Short gender,
        String address,
        String phoneNumber,
        String citizenId,
        List<String> drugAllergies,
        String medicalHistorySummary,

        // Selected visit (step 2)
        UUID visitId,
        String visitCode,
        LocalDate visitDate,
        Instant registeredAt,
        UUID roomId,
        String roomName,
        UUID doctorAccountId,
        String doctorName,
        VisitDoituongbnLoai visitType,
        String insuranceCardNumber,
        boolean isPriority,
        PriorityReason priorityReason,
        VisitStatus visitStatus,
        boolean isAbandoned,
        boolean isEditable,
        String reasonForVisit,
        List<TransferItem> transfers,
        VitalSignsItem vitalSigns,
        ClinicalExamDetail clinicalExam,
        List<ParaclinicalOrderItem> paraclinicalOrders,
        ConclusionDetail conclusion,
        PrescriptionDetail prescription,
        FollowUpDetail followUpAppointment,

        // Visit timeline (step 3)
        List<VisitTimelineItem> visitTimeline
) {
    public record TransferItem(
            UUID transferId,
            UUID fromRoomId,
            String fromRoomName,
            UUID toRoomId,
            String toRoomName,
            String reason,
            Instant transferredAt
    ) {}

    public record VitalSignsItem(
            Short mach,
            BigDecimal nhietdo,
            Short nhiptho,
            Short huyetapTamthu,
            Short huyetapTamtruong,
            BigDecimal cannang,
            BigDecimal chieucao,
            BigDecimal bmi,
            Short spo2,
            Instant thoidiem
    ) {}

    public record ClinicalExamDetail(
            String initialDiagnosis,
            String processOfIllness,
            String generalExamination,
            String partExamination,
            List<DiagnosisItem> diagnoses
    ) {}

    public record DiagnosisItem(
            String icdCode,
            String icdName,
            boolean isPrimary
    ) {}

    public record ParaclinicalOrderItem(
            UUID orderId,
            String orderCode,
            ParaclinicalOrderStatus status,
            Instant orderedAt,
            List<ParaclinicalServiceItem> services
    ) {}

    /** BR-52-05: every ordered service, cancelled ones included; a result only once it is submitted. */
    public record ParaclinicalServiceItem(
            UUID orderDetailId,
            String orderCode,
            String serviceName,
            String category,
            String roomName,
            ParaclinicalOrderDetailStatus status,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            String performerName,
            Instant performedAt,
            String resultSummary,
            LabResultDetail labResult,
            ImagingResultDetail imagingResult
    ) {}

    public record LabResultDetail(
            UUID labResultId,
            String ticketNumber,
            String diagnosis,
            String notes,
            List<LabItem> items
    ) {}

    public record LabItem(
            String testIndexName,
            String testValue,
            String normalRange,
            String unit,
            boolean abnormal
    ) {}

    public record ImagingResultDetail(
            UUID imagingResultId,
            String ticketNumber,
            String description,
            String conclusion,
            String recommendation,
            String externalLink,
            List<ImageAttachment> images
    ) {}

    /** BR-52-05: the file itself is served by {@code GET /medical-records/{id}/result-files/{imageId}}. */
    public record ImageAttachment(
            UUID imageId,
            String mimeType,
            String caption
    ) {}

    public record ConclusionDetail(
            String diagnosis,
            String icdCode,
            String icdName,
            String treatmentPlan,
            String doctorAdvice,
            String treatmentResult,
            Instant concludedAt
    ) {}

    public record PrescriptionDetail(
            UUID prescriptionId,
            String prescriptionCode,
            String ticketNumber,
            PrescriptionLienthongStatus status,
            Instant prescribedAt,
            Instant printedAt,
            String notes,
            String advice,
            LocalDate followUpDate,
            List<PrescriptionMedicineItem> items
    ) {}

    public record PrescriptionMedicineItem(
            UUID medicineId,
            String medicineName,
            String activeIngredient,
            String strength,
            String unit,
            BigDecimal quantity,
            String usageInstructions,
            short timesPerDay,
            short durationDays,
            String dosePerTime
    ) {}

    public record FollowUpDetail(
            UUID appointmentId,
            String appointmentNumber,
            LocalDate followUpDate,
            String doctorAdvice,
            FollowUpAppointmentStatus status
    ) {}

    public record VisitTimelineItem(
            UUID visitId,
            UUID medicalRecordId,
            String visitCode,
            LocalDate visitDate,
            String roomName,
            String doctorName,
            VisitStatus visitStatus,
            boolean isCurrent,
            String diagnosisIcd,
            String diagnosisName
    ) {}
}
