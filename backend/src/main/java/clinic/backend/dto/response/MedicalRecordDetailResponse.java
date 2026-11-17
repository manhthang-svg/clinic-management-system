package clinic.backend.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import clinic.backend.enums.MedicalRecordStatus;

public record MedicalRecordDetailResponse(
        UUID medicalRecordId,
        String recordNumber,
        String recordType,
        String storageNumber,
        MedicalRecordStatus status,
        Instant openedAt,
        Instant closedAt,
        Instant unlockedAt,

        // A. Thông tin tổng quan Bệnh nhân (Patient Header)
        UUID patientId,
        String patientCode,
        String patientName,
        LocalDate dateOfBirth,
        Integer age,
        String gender,
        String address,
        String phoneNumber,
        String citizenId,
        List<String> drugAllergies,
        String medicalHistorySummary,

        // B. Chi tiết Lượt khám được chọn (Selected Visit Detail)
        UUID visitId,
        String visitCode,
        LocalDate visitDate,
        Instant registeredAt,
        UUID roomId,
        String roomName,
        UUID doctorAccountId,
        String doctorName,
        String visitType,
        String insuranceCardNumber,
        boolean isPriority,
        String priorityReason,
        String visitStatus,
        boolean isAbandoned,
        boolean isEditable,
        String reasonForVisit,

        // Lịch sử chuyển phòng (Transfers)
        List<TransferItem> transfers,

        // Chỉ số sinh hiệu (Vital Signs)
        VitalSignsItem vitalSigns,

        // Khám lâm sàng
        ClinicalExamDetail clinicalExam,

        // Chỉ định & Kết quả Cận lâm sàng
        List<ParaclinicalOrderItem> paraclinicalOrders,

        // Chẩn đoán & Kết luận điều trị
        ConclusionDetail conclusion,

        // Đơn thuốc (Prescription)
        PrescriptionDetail prescription,

        // Lịch hẹn tái khám (Follow-up appointment)
        FollowUpDetail followUpAppointment,

        // C. Thanh điều hướng Lịch sử khám (Visit Timeline)
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
            String status,
            Instant orderedAt,
            List<ParaclinicalServiceItem> services
    ) {}

    public record ParaclinicalServiceItem(
            UUID orderDetailId,
            String orderCode,
            String serviceName,
            String category,
            String roomName,
            String status,
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
            String abnormalEvaluation
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

    public record ImageAttachment(
            UUID imageId,
            String imageUrl,
            String fileName,
            String mimeType,
            String caption
    ) {}

    public record ConclusionDetail(
            String diagnosis,
            String icdCode,
            String icdName,
            String treatmentPlan,
            String doctorAdvice,
            Short treatmentResult,
            Instant concludedAt
    ) {}

    public record PrescriptionDetail(
            UUID prescriptionId,
            String prescriptionCode,
            String ticketNumber,
            String status,
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
            String status
    ) {}

    public record VisitTimelineItem(
            UUID visitId,
            UUID medicalRecordId,
            String visitCode,
            LocalDate visitDate,
            String roomName,
            String doctorName,
            String visitStatus,
            boolean isCurrent,
            String diagnosisIcd,
            String diagnosisName
    ) {}
}
