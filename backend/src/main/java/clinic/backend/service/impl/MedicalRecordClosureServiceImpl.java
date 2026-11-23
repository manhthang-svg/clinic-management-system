package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.request.ConfirmConclusionRequest;
import clinic.backend.dto.response.ConclusionClosureResponse;
import clinic.backend.dto.response.ConclusionConfirmationResponse;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.ExportLog;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.RecordArchive;
import clinic.backend.entity.RecordSignature;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.PrescriptionLienthongStatus;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.ExportLogRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.RecordArchiveRepository;
import clinic.backend.repository.RecordSignatureRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.InsuranceCoverageService;
import clinic.backend.service.MedicalRecordClosureService;
import clinic.backend.service.impl.ArchivePackageBuilder.Closing;
import clinic.backend.service.impl.ArchiveStore.StoredPackage;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-58 Close Medical Record. BR-32: when the Doctor confirms the conclusion the system closes the record and
 * completes the visit in the same action. Everything below runs in one transaction (BR-58-08).
 */
@Service
@RequiredArgsConstructor
public class MedicalRecordClosureServiceImpl implements MedicalRecordClosureService {

    static final String ARCHIVE_NUMBER_PREFIX = "LT-";
    private static final String RECORD_AUDIT_ENTITY = "medical_records";
    private static final String ARCHIVE_AUDIT_ENTITY = "record_archives";

    private final ConcludingVisitGuard guard;
    private final VisitRepository visitRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ConclusionRepository conclusionRepository;
    private final ParaclinicalOrderRepository paraclinicalOrderRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final FollowUpAppointmentRepository followUpRepository;
    private final ExportLogRepository exportLogRepository;
    private final RecordArchiveRepository recordArchiveRepository;
    private final RecordSignatureRepository recordSignatureRepository;
    private final InsuranceCoverageService insuranceCoverageService;
    private final ArchivePackageBuilder packageBuilder;
    private final ArchiveStore archiveStore;
    private final AuditLogService auditLogService;
    private final UuidV7Generator ids;
    private final java.time.Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ConclusionClosureResponse closureCheck(StaffPrincipal principal, UUID visitId) {
        Context context = guard.require(principal, visitId);
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId()).orElse(null);
        int pending = pendingParaclinical(context.record());
        Prescription prescription = prescriptionRepository.findByMedicalRecordId(context.record().getId())
                .orElse(null);
        boolean followUp = followUpRepository.findByMedicalRecordId(context.record().getId())
                .filter(FollowUpAppointment::isScheduled).isPresent();
        boolean prescriptionOk = prescription == null
                || prescription.getLienthongStatus() == PrescriptionLienthongStatus.ACCEPTED;
        return new ConclusionClosureResponse(
                conclusion != null, conclusion == null ? null : conclusion.getVersion(), pending,
                prescription == null ? null : prescription.getLienthongStatus().name(), followUp,
                conclusion != null && pending == 0 && prescriptionOk);
    }

    @Override
    @Transactional
    public ConclusionConfirmationResponse confirm(
            StaffPrincipal principal, UUID visitId, ConfirmConclusionRequest request) {
        // Lock the visit first, so the end-of-day job, a paraclinical cancellation or a second click wait (BR-58-08).
        visitRepository.findByIdForUpdate(visitId);
        Context context = guard.require(principal, visitId);
        RecordArchiveRetentionCategory category = retentionCategory(request);
        Visit visit = context.visit();
        MedicalRecord record = context.record();

        // BR-58-01, in order; BR-58-02.
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(record.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CLOSE_CONCLUSION_REQUIRED));
        if (request == null || request.conclusionVersion() == null
                || !request.conclusionVersion().equals(conclusion.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        int pending = pendingParaclinical(record);
        if (pending > 0) {
            throw new AppException(ErrorCode.CLOSE_PARACLINICAL_RESULTS_PENDING,
                    Map.of("count", Integer.toString(pending)));
        }
        Prescription prescription = prescriptionRepository.findByMedicalRecordId(record.getId()).orElse(null);
        if (prescription != null && prescription.getLienthongStatus() != PrescriptionLienthongStatus.ACCEPTED) {
            throw new AppException(ErrorCode.PRESCRIPTION_NOT_ACCEPTED,
                    Map.of("lienthongStatus", prescription.getLienthongStatus().name()));
        }

        Instant now = clock.instant();
        String oldVisitStatus = visit.getStatus().name();
        // BR-58-03: the last repricing; the rate is fixed once the visit is completed.
        insuranceCoverageService.recalculate(visit);

        String soluutru = ARCHIVE_NUMBER_PREFIX + visit.getSovaovien();
        if (recordArchiveRepository.existsBySoluutru(soluutru)) {
            throw new IllegalStateException("Archive number already used: " + soluutru);
        }
        LocalDate closingDate = LocalDate.ofInstant(now, RetentionRules.CLINIC_ZONE);
        conclusion.discharge(closingDate, now);
        conclusionRepository.save(conclusion);
        visit.complete(now);
        visitRepository.save(visit);
        record.close(principal.accountId(), soluutru, now);
        MedicalRecord closed = medicalRecordRepository.saveAndFlush(record);

        // BR-58-06: package, store, export log and archive row.
        LocalDate expiresOn = RetentionRules.expiresOn(now, category.retentionYears());
        byte[] content = packageBuilder.build(new Closing(
                visit, context.patient(), closed, conclusion, context.doctor(), soluutru, now, category, expiresOn));
        StoredPackage stored = archiveStore.store(closingDate.getYear(), soluutru, content);
        ExportLog exportLog = exportLogRepository.save(ExportLog.archivePackage(
                ids.next(), closed.getId(), visit.getSovaovien(), stored.uri(), stored.sha256(),
                ArchivePackageBuilder.SPEC_VERSION, principal.accountId(), now));
        RecordArchive archive = recordArchiveRepository.save(RecordArchive.archive(
                ids.next(), closed.getId(), soluutru, exportLog.getId(), stored.uri(), stored.sha256(), category,
                expiresOn, now));

        // BR-58-09: the Doctor's electronic confirmation, tied to the package hash.
        recordSignatureRepository.save(RecordSignature.staffElectronicConfirmation(
                ids.next(), now, closed.getId(), closed.getVersion(), stored.sha256(), principal.accountId(),
                ArchivePackageBuilder.staffName(context.doctor())));

        // BR-58-07. The audit service needs the same fields on both sides; a field that had no value is null.
        Map<String, Object> oldValues = new LinkedHashMap<>();
        oldValues.put("status", "OPEN");
        oldValues.put("closedAt", null);
        oldValues.put("soluutru", null);
        oldValues.put("visitStatus", oldVisitStatus);
        oldValues.put("retentionCategory", null);
        oldValues.put("retentionYears", null);
        Map<String, Object> newValues = new LinkedHashMap<>();
        newValues.put("status", closed.getStatus().name());
        newValues.put("closedAt", now.toString());
        newValues.put("soluutru", soluutru);
        newValues.put("visitStatus", visit.getStatus().name());
        newValues.put("retentionCategory", category.name());
        newValues.put("retentionYears", category.retentionYears());
        auditLogService.recordEntityChange(principal.accountId(), closed.getId(), AuditLogAction.LOCK,
                RECORD_AUDIT_ENTITY, oldValues, newValues, now);
        Map<String, Object> exportValues = new LinkedHashMap<>();
        exportValues.put("soluutru", soluutru);
        exportValues.put("packageUri", stored.uri());
        exportValues.put("packageSha256", stored.sha256());
        exportValues.put("expiresOn", expiresOn.toString());
        auditLogService.recordBusinessChange(principal.accountId(), AuditLogAction.EXPORT, ARCHIVE_AUDIT_ENTITY,
                archive.getId(), visit.getSovaovien(), exportValues, now);

        // UC-57, UC-60, UC-62 are printed once the record is closed (PO 23/11/2026): what there is to print.
        UUID followUpAppointmentId = followUpRepository.findByMedicalRecordId(closed.getId())
                .filter(FollowUpAppointment::isScheduled)
                .map(FollowUpAppointment::getId)
                .orElse(null);
        return new ConclusionConfirmationResponse(visit.getId(), visit.getSovaovien(), visit.getStatus().name(),
                now, soluutru, category.name(), category.retentionYears(), expiresOn,
                prescription == null ? null : prescription.getId(), followUpAppointmentId);
    }

    /** BR-30 as counted in I1-D44: services neither cancelled nor completed. */
    private int pendingParaclinical(MedicalRecord record) {
        return (int) paraclinicalOrderRepository.findAllByMedicalRecordId(record.getId()).stream()
                .flatMap(order -> order.getDetails().stream())
                .filter(detail -> detail.getStatus() != ParaclinicalOrderDetailStatus.CANCELLED
                        && detail.getStatus() != ParaclinicalOrderDetailStatus.COMPLETED)
                .count();
    }

    private static RecordArchiveRetentionCategory retentionCategory(ConfirmConclusionRequest request) {
        String value = request == null ? null : request.retentionCategory();
        if (value == null || value.isBlank()) {
            return RecordArchiveRetentionCategory.STANDARD;
        }
        try {
            return RecordArchiveRetentionCategory.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new AppException(ErrorCode.CLOSE_INVALID,
                    Map.of("field", "retentionCategory", "reason", "INVALID"));
        }
    }
}
