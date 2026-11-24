package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.config.properties.FollowUpReminderProperties;
import clinic.backend.dto.request.SaveFollowUpAppointmentRequest;
import clinic.backend.dto.response.FollowUpScheduleResponse;
import clinic.backend.dto.response.FollowUpSlipResponse;
import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.ConclusionSecondaryDiagnosis;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.PrescriptionLienthongStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.FollowUpScheduleService;
import clinic.backend.service.impl.ConcludingVisitGuard.Context;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-61, UC-62 (docs/uc-61-follow-up-appointment.md): one follow-up appointment per visit record, made and changed by
 * the Doctor of the open visit after the conclusion is saved, and printed as the Phiếu hẹn khám lại of TT 01/2025
 * Phụ lục V by the Doctor of the visit once the record is closed (PO 23/11/2026). The reception side of BR-34 (arrival, missed, cancellation) is not changed here.
 */
@Service
@RequiredArgsConstructor
public class FollowUpScheduleServiceImpl implements FollowUpScheduleService {

    static final String AUDIT_ENTITY = "follow_up_appointments";
    /** BR-61-03 (ASSUMPTION): "GH-" + the visit code, as the prescription's slip number "DT-" + visit code. */
    static final String SLIP_PREFIX = "GH-";

    private final ConcludingVisitGuard guard;
    private final ClosedRecordPrintGuard printGuard;
    private final FollowUpAppointmentRepository appointmentRepository;
    private final ConclusionRepository conclusionRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final AuditLogService auditLogService;
    private final UuidV7Generator ids;
    private final Clock clock;
    private final FollowUpReminderProperties reminderProperties;

    @Override
    @Transactional
    public FollowUpScheduleResponse get(StaffPrincipal principal, UUID visitId) {
        Context context = guard.require(principal, visitId);
        FollowUpAppointment appointment = appointmentRepository.findByMedicalRecordId(context.record().getId())
                .orElse(null);
        if (appointment != null) {
            // BR-61-07: opening an existing appointment is a medical-record access.
            auditLogService.recordBusinessChange(
                    principal.accountId(), AuditLogAction.VIEW_RECORD, AUDIT_ENTITY, appointment.getId(),
                    context.visit().getSovaovien(), Map.of("visitId", visitId), clock.instant());
        }
        return form(context, appointment);
    }

    @Override
    @Transactional
    public FollowUpScheduleResponse save(
            StaffPrincipal principal, UUID visitId, SaveFollowUpAppointmentRequest request) {
        Context context = guard.require(principal, visitId);
        // EF-03: the slip carries the conclusion's diagnoses (BR-61-05).
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId())
                .orElseThrow(() -> new AppException(ErrorCode.FOLLOW_UP_CONCLUSION_REQUIRED));
        LocalDate visitDate = FollowUpRules.visitDate(context.visit().getThoigianvaovien());
        LocalDate followUpDate = FollowUpRules.requireDate(request.followUpDate(), visitDate);
        String advice = FollowUpRules.advice(request.advice());
        Instant now = clock.instant();

        FollowUpAppointment existing = appointmentRepository.findByMedicalRecordId(context.record().getId())
                .orElse(null);
        if (existing == null) {
            if (request.version() != null) {
                throw new AppException(ErrorCode.CONCURRENT_MODIFICATION); // removed meanwhile
            }
            FollowUpAppointment created = appointmentRepository.save(FollowUpAppointment.schedule(
                    ids.next(), now, context.record().getId(), SLIP_PREFIX + context.visit().getSovaovien(),
                    context.patient().getId(), context.doctor().getId(), conclusion.getMaicd(), followUpDate,
                    advice));
            Map<String, Object> values = auditValues(created);
            values.put("slipNumber", created.getSoGiayHen());
            auditLogService.recordEntityChange(
                    principal.accountId(), created.getId(), AuditLogAction.CREATE, AUDIT_ENTITY, Map.of(), values,
                    now);
            return form(context, created);
        }

        if (!Objects.equals(request.version(), existing.getVersion())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        if (!existing.isScheduled()) {
            throw new AppException(ErrorCode.FOLLOW_UP_APPOINTMENT_NOT_SCHEDULED);
        }
        Map<String, Object> before = auditValues(existing);
        boolean changed = !Objects.equals(existing.getFollowUpDate(), followUpDate)
                || !Objects.equals(existing.getLoidanbacsi(), advice)
                || !Objects.equals(existing.getMaicd(), conclusion.getMaicd());
        if (!changed) {
            return form(context, existing); // BR-61-07: no audit row for an unchanged save
        }
        existing.reschedule(followUpDate, advice, conclusion.getMaicd(), now);
        FollowUpAppointment saved = appointmentRepository.saveAndFlush(existing);
        recordUpdate(principal, saved, before, auditValues(saved), now);
        return form(context, saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FollowUpSlipResponse printPreview(StaffPrincipal principal, UUID appointmentId) {
        FollowUpAppointment appointment = scheduledAppointment(appointmentId);
        Context context = printGuard.require(principal, visitId(appointment));
        return slip(context, appointment, appointment.getPrintedAt() != null, null, null);
    }

    @Override
    @Transactional
    public FollowUpSlipResponse preparePrint(StaffPrincipal principal, UUID appointmentId) {
        FollowUpAppointment appointment = scheduledAppointment(appointmentId);
        Context context = printGuard.require(principal, visitId(appointment));
        Instant now = clock.instant();
        boolean reprint = appointment.getPrintedAt() != null;
        if (!reprint) {
            appointment.markPrinted(now);
            appointmentRepository.save(appointment);
        }
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("slipNumber", appointment.getSoGiayHen());
        audit.put("reprint", reprint);
        auditLogService.recordBusinessChange(
                principal.accountId(), AuditLogAction.PRINT, AUDIT_ENTITY, appointment.getId(),
                context.visit().getSovaovien(), audit, now);
        return slip(context, appointment, reprint, now, displayName(context.doctor()));
    }

    /** UC-62 EF: only a scheduled appointment is printed (MSG128). */
    private FollowUpAppointment scheduledAppointment(UUID appointmentId) {
        FollowUpAppointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLLOW_UP_APPOINTMENT_NOT_FOUND));
        if (!appointment.isScheduled()) {
            throw new AppException(ErrorCode.FOLLOW_UP_APPOINTMENT_NOT_SCHEDULED);
        }
        return appointment;
    }

    private UUID visitId(FollowUpAppointment appointment) {
        return medicalRecordRepository.findById(appointment.getMedicalRecordId())
                .map(MedicalRecord::getVisitId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLLOW_UP_APPOINTMENT_NOT_FOUND));
    }

    private FollowUpScheduleResponse form(Context context, FollowUpAppointment appointment) {
        LocalDate visitDate = FollowUpRules.visitDate(context.visit().getThoigianvaovien());
        Conclusion conclusion = conclusionRepository.findByMedicalRecordId(context.record().getId()).orElse(null);
        // AF-03 (BR-59-08): the date that went out with an accepted prescription.
        Prescription accepted = prescriptionRepository.findByMedicalRecordId(context.record().getId())
                .filter(prescription -> prescription.getLienthongStatus() == PrescriptionLienthongStatus.ACCEPTED)
                .orElse(null);
        return new FollowUpScheduleResponse(
                context.visit().getId(),
                visitDate,
                FollowUpRules.minDate(visitDate),
                FollowUpRules.maxDate(visitDate),
                conclusion != null,
                conclusion == null ? List.of() : diagnoses(conclusion),
                appointment == null ? null : new FollowUpScheduleResponse.Appointment(
                        appointment.getId(), appointment.getVersion(), appointment.getSoGiayHen(),
                        appointment.getFollowUpDate(), appointment.getLoidanbacsi(), appointment.getStatus().name(),
                        appointment.getPrintedAt(), appointment.getReminderSentAt()),
                accepted != null,
                accepted == null ? null : accepted.getNgayTaiKham(),
                context.patient().getEmail() != null && !context.patient().getEmail().isBlank(),
                reminderProperties.enabled() ? reminderProperties.daysBefore() : null);
    }

    /** BR-61-06: the slip of TT 01/2025 Phụ lục V with the conclusion's current diagnoses (BR-61-05). */
    private FollowUpSlipResponse slip(
            Context context, FollowUpAppointment appointment, boolean copy, Instant printedAt, String printedBy) {
        ClinicFacility facility = clinicFacilityRepository.findCurrent()
                .orElseThrow(() -> new AppException(ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED));
        Patient patient = context.patient();
        Visit visit = context.visit();
        List<Icd10OptionResponse> diagnoses = conclusionRepository.findByMedicalRecordId(context.record().getId())
                .map(FollowUpScheduleServiceImpl::diagnoses)
                .orElse(List.of());
        boolean insured = visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT && text(visit.getMabhyt()) != null;
        return new FollowUpSlipResponse(
                appointment.getId(),
                appointment.getSoGiayHen(),
                copy,
                printedAt,
                printedBy,
                new FollowUpSlipResponse.Facility(
                        facility.getTenCskcb(), facility.getDiachi(), facility.getSodienthoai()),
                new FollowUpSlipResponse.PatientInfo(
                        patient.getHoten(), gender(patient.getGioitinh()), patient.getNgaysinh(),
                        text(PrescriptionPayloadBuilder.address(patient))),
                insured ? new FollowUpSlipResponse.Insurance(
                        visit.getMabhyt().strip(), visit.getTungaybhyt(), visit.getDenngaybhyt()) : null,
                FollowUpRules.visitDate(visit.getThoigianvaovien()),
                diagnoses.isEmpty() ? null : diagnoses.getFirst(),
                diagnoses.isEmpty() ? List.of() : diagnoses.subList(1, diagnoses.size()),
                appointment.getFollowUpDate(),
                appointment.getLoidanbacsi(),
                staffAccountRepository.findById(appointment.getDoctorAccountId())
                        .map(FollowUpScheduleServiceImpl::displayName)
                        .orElse(null));
    }

    private static List<Icd10OptionResponse> diagnoses(Conclusion conclusion) {
        List<Icd10OptionResponse> items = new ArrayList<>();
        items.add(new Icd10OptionResponse(conclusion.getMaicd(), conclusion.getTenicd()));
        conclusion.getSecondaryDiagnoses().stream()
                .sorted(Comparator.comparingInt(ConclusionSecondaryDiagnosis::getThuTu))
                .forEach(item -> items.add(new Icd10OptionResponse(item.getMaicd(), item.getTenicd())));
        return List.copyOf(items);
    }

    /** BR-61-07: the audited fields; dates as ISO text. */
    private static Map<String, Object> auditValues(FollowUpAppointment appointment) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("followUpDate", appointment.getFollowUpDate().toString());
        values.put("advice", appointment.getLoidanbacsi());
        values.put("maicd", appointment.getMaicd());
        return values;
    }

    /** Only the fields whose value changed, old and new. */
    private void recordUpdate(
            StaffPrincipal principal, FollowUpAppointment saved, Map<String, Object> before,
            Map<String, Object> after, Instant now) {
        Map<String, Object> oldChanged = new LinkedHashMap<>();
        Map<String, Object> newChanged = new LinkedHashMap<>();
        after.forEach((field, value) -> {
            if (!Objects.equals(before.get(field), value)) {
                oldChanged.put(field, before.get(field));
                newChanged.put(field, value);
            }
        });
        if (!newChanged.isEmpty()) {
            auditLogService.recordEntityChange(
                    principal.accountId(), saved.getId(), AuditLogAction.UPDATE, AUDIT_ENTITY, oldChanged, newChanged,
                    now);
        }
    }

    /** Phụ lục V "Giới": the form names Nam and Nữ. */
    private static String gender(short code) {
        return switch (code) {
            case 1 -> "Nam";
            case 2 -> "Nữ";
            default -> null;
        };
    }

    private static String displayName(StaffAccount account) {
        String title = text(account.getAcademicTitle());
        return title == null ? account.getFullName() : title + " " + account.getFullName();
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
