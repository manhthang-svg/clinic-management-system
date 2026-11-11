package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.request.SaveParaclinicalResultRequest;
import clinic.backend.dto.response.ParaclinicalResultResponse;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.LabParameter;
import clinic.backend.entity.LabReferenceRange;
import clinic.backend.entity.LabResult;
import clinic.backend.entity.LabResultItem;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalInstrument;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.ServiceLabParameter;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.InstrumentResultType;
import clinic.backend.enums.LabParameterValueType;
import clinic.backend.enums.LabReferenceType;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ResultForm;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabParameterRepository;
import clinic.backend.repository.LabReferenceRangeRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalInstrumentRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.ServiceLabParameterRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.ParaclinicalProgressService;
import clinic.backend.service.ParaclinicalResultService;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-67 Record Paraclinical Result and the print counter of UC-68. Results are typed by hand (SW-04); the
 * instrument only records which machine ran them (V54). Only the technician who performed the service edits its
 * result, and a submitted result is locked (BR-29). Images are not handled yet (TBD: file storage).
 */
@Service
@RequiredArgsConstructor
public class ParaclinicalResultServiceImpl implements ParaclinicalResultService {

    private static final String AUDIT_ENTITY = "paraclinical_order_details";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMERGENCY = "cấp cứu";

    private final ParaclinicalOrderDetailRepository orderDetailRepository;
    private final LabResultRepository labResultRepository;
    private final ImagingResultRepository imagingResultRepository;
    private final MedicalServiceRepository medicalServiceRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final ServiceLabParameterRepository serviceLabParameterRepository;
    private final LabParameterRepository labParameterRepository;
    private final LabReferenceRangeRepository labReferenceRangeRepository;
    private final ParaclinicalInstrumentRepository instrumentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final ParaclinicalProgressService paraclinicalProgressService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ParaclinicalResultResponse get(UUID technicianAccountId, UUID orderDetailId) {
        return toResponse(load(orderDetailId), technicianAccountId);
    }

    @Override
    @Transactional
    public ParaclinicalResultResponse saveDraft(
            UUID technicianAccountId, UUID orderDetailId, SaveParaclinicalResultRequest request) {
        Context context = load(orderDetailId);
        requireEditableBy(context, technicianAccountId);
        Instant now = clock.instant();
        record(context, technicianAccountId, request, false, now);
        audit(technicianAccountId, context.detail(), "SAVE_RESULT_DRAFT", now);
        return toResponse(load(orderDetailId), technicianAccountId);
    }

    @Override
    @Transactional
    public ParaclinicalResultResponse submit(
            UUID technicianAccountId, UUID orderDetailId, SaveParaclinicalResultRequest request) {
        Context context = load(orderDetailId);
        requireEditableBy(context, technicianAccountId);
        Instant now = clock.instant();
        validateForSubmit(context, request);
        record(context, technicianAccountId, request, true, now);

        // Step 9: the service is Completed; step 10: the visit waits for conclusion once every service is done.
        ParaclinicalOrderDetail detail = context.detail();
        detail.completeWithResult(technicianAccountId, now);
        orderDetailRepository.save(detail);
        context.visit().ifPresent(paraclinicalProgressService::moveToConclusionIfDone);
        audit(technicianAccountId, detail, "SUBMIT_RESULT", now);
        return toResponse(load(orderDetailId), technicianAccountId);
    }

    @Override
    @Transactional
    public ParaclinicalResultResponse recordPrint(UUID technicianAccountId, UUID orderDetailId) {
        Context context = load(orderDetailId);
        Instant now = clock.instant();
        // EF-01: drafts cannot be printed; only the performer prints (permission matrix, BR-29); PF-03 needs the
        // clinic header.
        if (!isSubmitted(context)) {
            throw new AppException(ErrorCode.RESULT_NOT_SUBMITTED);
        }
        if (!technicianAccountId.equals(context.detail().getPerformerAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (clinicFacilityRepository.findCurrent().isEmpty()) {
            throw new AppException(ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED);
        }
        if (context.isLab()) {
            LabResult result = labResultRepository.findByOrderDetailId(orderDetailId)
                    .filter(LabResult::isSubmitted)
                    .orElseThrow(() -> new AppException(ErrorCode.RESULT_NOT_SUBMITTED));
            result.recordPrint(now);
            labResultRepository.save(result);
        } else {
            ImagingResult result = imagingResultRepository.findByOrderDetailId(orderDetailId)
                    .filter(ImagingResult::isSubmitted)
                    .orElseThrow(() -> new AppException(ErrorCode.RESULT_NOT_SUBMITTED));
            result.recordPrint(now);
            imagingResultRepository.save(result);
        }
        auditLogService.recordBusinessChange(technicianAccountId, AuditLogAction.PRINT, AUDIT_ENTITY,
                orderDetailId, context.detail().getMachidinh(), Map.of("action", "PRINT_RESULT"), now);
        return toResponse(load(orderDetailId), technicianAccountId);
    }

    // ---- loading -----------------------------------------------------------------------------------------------

    private record Context(ParaclinicalOrderDetail detail, ParaclinicalOrder order, MedicalService service,
                           ResultForm form, Optional<Visit> visit, Optional<Patient> patient) {

        boolean isLab() {
            return form == ResultForm.LAB;
        }

        InstrumentResultType instrumentType() {
            return isLab() ? InstrumentResultType.LAB : InstrumentResultType.IMAGING;
        }
    }

    private Context load(UUID orderDetailId) {
        ParaclinicalOrderDetail detail = orderDetailRepository.findById(orderDetailId)
                .orElseThrow(() -> new AppException(ErrorCode.PARACLINICAL_SERVICE_NOT_FOUND));
        MedicalService service = medicalServiceRepository.findById(detail.getServiceId())
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        ParaclinicalOrder order = detail.getOrder();
        Optional<Visit> visit = medicalRecordRepository.findById(order.getMedicalRecordId())
                .flatMap(record -> visitRepository.findById(record.getVisitId()));
        Optional<Patient> patient = visit.flatMap(found -> patientRepository.findById(found.getPatientId()));
        return new Context(detail, order, service, resultFormOf(service), visit, patient);
    }

    /**
     * ASSUMPTION / TBD: the service catalog has no "result form" field yet, so the slip follows the service type
     * and keywords of the service name; an unknown imaging or functional service uses the plain descriptive slip.
     */
    static ResultForm resultFormOf(MedicalService service) {
        if (service.getType() == MedicalServiceType.LAB) {
            return ResultForm.LAB;
        }
        String name = fold(service.getTenchidinh());
        if (name.contains("sieu am")) {
            return ResultForm.ULTRASOUND;
        }
        if (name.contains("noi soi")) {
            return ResultForm.ENDOSCOPY;
        }
        if (name.contains("dien tim") || name.contains("dien tam do") || name.matches(".*\\becg\\b.*")) {
            return ResultForm.ECG;
        }
        if (name.contains("cong huong tu") || name.matches(".*\\bmri\\b.*")) {
            return ResultForm.MRI;
        }
        if (name.contains("cat lop") || name.matches(".*\\bct\\b.*")) {
            return ResultForm.CT;
        }
        return ResultForm.XRAY;
    }

    private static String fold(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT).replace('đ', 'd');
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // ---- rules -------------------------------------------------------------------------------------------------

    /** EF-03 / EF-04: only the performer edits, only before submission, only once the service was accepted. */
    private void requireEditableBy(Context context, UUID technicianAccountId) {
        if (isSubmitted(context)) {
            throw new AppException(ErrorCode.RESULT_ALREADY_SUBMITTED);
        }
        ParaclinicalOrderDetail detail = context.detail();
        if (!technicianAccountId.equals(detail.getPerformerAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (!detail.awaitsResultFrom(technicianAccountId)) {
            throw new AppException(ErrorCode.RESULT_NOT_EDITABLE);
        }
    }

    private boolean isSubmitted(Context context) {
        UUID detailId = context.detail().getId();
        return context.isLab()
                ? labResultRepository.findByOrderDetailId(detailId).map(LabResult::isSubmitted).orElse(false)
                : imagingResultRepository.findByOrderDetailId(detailId).map(ImagingResult::isSubmitted).orElse(false);
    }

    /** Step 6: every required lab value is present and numeric values are numbers; descriptive results need text. */
    private void validateForSubmit(Context context, SaveParaclinicalResultRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (context.isLab()) {
            Map<String, String> values = Objects.requireNonNullElse(request.values(), Map.of());
            for (LabRow row : labRows(context)) {
                String value = trim(values.get(row.parameter().getCode()));
                if (value == null) {
                    if (row.link().isRequired()) {
                        errors.put("value:" + row.parameter().getCode(), "Vui lòng nhập kết quả");
                    }
                } else if (row.parameter().getValueType() == LabParameterValueType.NUMERIC && parseNumber(value) == null) {
                    errors.put("value:" + row.parameter().getCode(), "Kết quả phải là số");
                }
            }
        } else {
            boolean hasSections = request.sections() != null
                    && request.sections().values().stream().anyMatch(text -> trim(text) != null);
            if (trim(request.description()) == null && !hasSections) {
                errors.put("description", "Vui lòng nhập mô tả kết quả");
            }
            if (List.of(ResultForm.CT, ResultForm.MRI).contains(context.form()) && trim(request.technique()) == null) {
                errors.put("technique", "Vui lòng nhập kỹ thuật");
            }
        }
        if (trim(request.conclusion()) == null) {
            errors.put("conclusion", "Vui lòng nhập kết luận");
        }
        if (!errors.isEmpty()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, errors);
        }
    }

    private Optional<ParaclinicalInstrument> instrumentFor(Context context, UUID instrumentId) {
        if (instrumentId == null) {
            return Optional.empty();
        }
        ParaclinicalInstrument instrument = instrumentRepository.findById(instrumentId)
                .filter(found -> found.usableFor(context.detail().getRoomId(), context.instrumentType()))
                .orElseThrow(() -> new AppException(ErrorCode.INSTRUMENT_UNAVAILABLE,
                        Map.of("instrumentId", "Máy thực hiện đã chọn hiện không còn khả dụng")));
        return Optional.of(instrument);
    }

    // ---- writing -----------------------------------------------------------------------------------------------

    private void record(Context context, UUID technicianAccountId, SaveParaclinicalResultRequest request,
                        boolean submit, Instant now) {
        ParaclinicalOrderDetail detail = context.detail();
        Optional<ParaclinicalInstrument> instrument = instrumentFor(context, request.instrumentId());
        UUID instrumentId = instrument.map(ParaclinicalInstrument::getId).orElse(null);
        String instrumentName = instrument.map(ParaclinicalInstrument::getName).orElse(null);
        String diagnosis = context.order().getChandoansobo();

        if (context.isLab()) {
            LabResult result = labResultRepository.findByOrderDetailId(detail.getId())
                    .orElseGet(() -> LabResult.start(ids.next(), now, detail.getId(), technicianAccountId,
                            detail.getMachidinh(), diagnosis));
            result.record(request.receivedAt(), trim(request.conclusion()),
                    labValues(context, request, instrumentId, instrumentName), now);
            if (submit) {
                result.submit(now);
            }
            labResultRepository.save(result);
        } else {
            ImagingResult result = imagingResultRepository.findByOrderDetailId(detail.getId())
                    .orElseGet(() -> ImagingResult.start(ids.next(), now, detail.getId(), technicianAccountId,
                            detail.getMachidinh(), diagnosis));
            result.record(new ImagingResult.Content(
                    trim(request.technique()), trim(request.description()), sectionsJson(request.sections()),
                    trim(request.conclusion()), trim(request.recommendation()), trim(request.imageLink()),
                    trim(request.readingDoctor()), instrumentId, instrumentName), now);
            if (submit) {
                result.submit(now);
            }
            imagingResultRepository.save(result);
        }
    }

    private List<LabResultItem.Value> labValues(Context context, SaveParaclinicalResultRequest request,
                                                UUID instrumentId, String instrumentName) {
        Map<String, String> values = Objects.requireNonNullElse(request.values(), Map.of());
        String groupLabel = serviceGroupRepository.findById(Objects.requireNonNullElse(
                        context.service().getServiceGroupId(), new UUID(0, 0)))
                .map(group -> group.getServiceGroupName()).orElse(null);
        List<LabResultItem.Value> rows = new ArrayList<>();
        for (LabRow row : labRows(context)) {
            String text = trim(values.get(row.parameter().getCode()));
            if (text == null) {
                continue;
            }
            BigDecimal number = row.parameter().getValueType() == LabParameterValueType.NUMERIC ? parseNumber(text) : null;
            String qualitative = row.parameter().getValueType() == LabParameterValueType.QUALITATIVE ? text : null;
            rows.add(new LabResultItem.Value(
                    ids.next(), row.link().getId(), row.parameter().getCode(), row.parameter().getName(),
                    row.parameter().getUnit(), referenceText(row.range()), text, number, qualitative,
                    interpret(row.parameter(), row.range(), text), row.link().getDisplayOrder(),
                    row.link().getSectionName(), Objects.requireNonNullElse(row.link().getGroupName(),
                    context.service().getTenchidinh()), groupLabel, instrumentId, instrumentName));
        }
        return rows;
    }

    private String sectionsJson(Map<String, String> sections) {
        if (sections == null || sections.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sections);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write the result sections", exception);
        }
    }

    private Map<String, String> sectionsOf(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private void audit(UUID actor, ParaclinicalOrderDetail detail, String action, Instant now) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("action", action);
        values.put("status", detail.getStatus().name());
        auditLogService.recordBusinessChange(actor, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), values, now);
    }

    // ---- lab parameters and reference ranges -------------------------------------------------------------------

    private record LabRow(ServiceLabParameter link, LabParameter parameter, LabReferenceRange range) {
    }

    private List<LabRow> labRows(Context context) {
        List<ServiceLabParameter> links = serviceLabParameterRepository
                .findAllByServiceIdOrderByDisplayOrderAsc(context.service().getId()).stream()
                .filter(ServiceLabParameter::isActive)
                .toList();
        Map<UUID, LabParameter> parameters = labParameterRepository
                .findAllByIdInAndActiveTrue(links.stream().map(ServiceLabParameter::getParameterId).toList())
                .stream().collect(Collectors.toMap(LabParameter::getId, Function.identity()));
        Map<UUID, List<LabReferenceRange>> ranges = labReferenceRangeRepository
                .findAllByParameterIdInAndActiveTrueOrderByEffectiveFromAsc(parameters.keySet()).stream()
                .collect(Collectors.groupingBy(LabReferenceRange::getParameterId));
        LocalDate onDate = LocalDate.ofInstant(context.detail().getNgaychidinh(), CLINIC_ZONE);
        Optional<Patient> patient = context.patient();
        return links.stream()
                .filter(link -> parameters.containsKey(link.getParameterId()))
                .map(link -> new LabRow(link, parameters.get(link.getParameterId()),
                        rangeFor(ranges.getOrDefault(link.getParameterId(), List.of()), onDate, patient)))
                .toList();
    }

    /** The active range in force on the order date that fits the patient's sex and age; the most specific wins. */
    private static LabReferenceRange rangeFor(List<LabReferenceRange> ranges, LocalDate onDate, Optional<Patient> patient) {
        Short sex = patient.map(found -> (short) found.getGioitinh()).orElse(null);
        Long ageDays = patient.map(Patient::getNgaysinh).filter(Objects::nonNull)
                .map(birth -> ChronoUnit.DAYS.between(birth, onDate)).orElse(null);
        return ranges.stream()
                .filter(range -> !range.getEffectiveFrom().isAfter(onDate))
                .filter(range -> range.getEffectiveTo() == null || !range.getEffectiveTo().isBefore(onDate))
                .filter(range -> range.getSex() == null || range.getSex().equals(sex))
                .filter(range -> ageDays == null
                        || (range.getAgeMinDays() == null || ageDays >= range.getAgeMinDays())
                        && (range.getAgeMaxDays() == null || ageDays <= range.getAgeMaxDays()))
                .max(Comparator.comparing((LabReferenceRange range) -> range.getSex() != null)
                        .thenComparing(range -> range.getAgeMinDays() != null || range.getAgeMaxDays() != null)
                        .thenComparing(LabReferenceRange::getEffectiveFrom))
                .orElse(null);
    }

    /** NORMAL, LOW, HIGH or ABNORMAL, as the result form shows it; null when there is nothing to compare. */
    static String interpret(LabParameter parameter, LabReferenceRange range, String text) {
        if (range == null || text == null) {
            return null;
        }
        if (range.getReferenceType() == LabReferenceType.QUALITATIVE) {
            return range.getNormalValues() != null && Arrays.asList(range.getNormalValues()).contains(text)
                    ? "NORMAL" : "ABNORMAL";
        }
        if (parameter.getValueType() != LabParameterValueType.NUMERIC) {
            return null;
        }
        BigDecimal value = parseNumber(text);
        if (value == null) {
            return null;
        }
        BigDecimal low = range.getLowerBound();
        BigDecimal high = range.getUpperBound();
        if (low != null && (range.isLowerInclusive() ? value.compareTo(low) < 0 : value.compareTo(low) <= 0)) {
            return "LOW";
        }
        if (high != null && (range.isUpperInclusive() ? value.compareTo(high) > 0 : value.compareTo(high) >= 0)) {
            return "HIGH";
        }
        return "NORMAL";
    }

    /** Accepts both "6,2" and "6.2". */
    static BigDecimal parseNumber(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.strip().replace(',', '.');
        return normalized.matches("-?\\d+(\\.\\d+)?") ? new BigDecimal(normalized) : null;
    }

    private static String referenceText(LabReferenceRange range) {
        if (range == null) {
            return null;
        }
        return switch (range.getReferenceType()) {
            case RANGE -> plain(range.getLowerBound()) + " - " + plain(range.getUpperBound());
            case UPPER_LIMIT -> "< " + plain(range.getUpperBound());
            case LOWER_LIMIT -> "> " + plain(range.getLowerBound());
            case QUALITATIVE -> String.join(", ", range.getNormalValues());
            case TEXT -> range.getReferenceText();
        };
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    // ---- response ----------------------------------------------------------------------------------------------

    private ParaclinicalResultResponse toResponse(Context context, UUID technicianAccountId) {
        ParaclinicalOrderDetail detail = context.detail();
        ParaclinicalOrder order = context.order();
        boolean isPerformer = technicianAccountId.equals(detail.getPerformerAccountId());
        List<LabRow> rows = context.isLab() ? labRows(context) : List.of();
        int printCount = 0;
        ParaclinicalResultResponse.Result result = null;
        if (context.isLab()) {
            Optional<LabResult> lab = labResultRepository.findByOrderDetailId(detail.getId());
            if (lab.isPresent()) {
                printCount = lab.get().getPrintCount();
                result = labResult(lab.get());
            }
        } else {
            Optional<ImagingResult> imaging = imagingResultRepository.findByOrderDetailId(detail.getId());
            if (imaging.isPresent()) {
                printCount = imaging.get().getPrintCount();
                result = imagingResult(imaging.get());
            }
        }
        List<ParaclinicalResultResponse.SiblingService> siblingServices = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(d -> Objects.equals(detail.getRoomId(), d.getRoomId()))
                .map(d -> {
                    MedicalService s = medicalServiceRepository.findById(d.getServiceId()).orElse(null);
                    return new ParaclinicalResultResponse.SiblingService(
                            d.getId(),
                            order.getSophieu(),
                            d.getTenchidinh(),
                            s != null ? s.getCode() : null,
                            s != null ? resultFormOf(s) : null,
                            statusOf(d),
                            d.getRoomId(),
                            true
                    );
                })
                .toList();
        return new ParaclinicalResultResponse(
                detail.getId(), order.getSophieu(), context.form(), context.service().getType().name(),
                sectionOf(context, rows), context.service().getCode(), detail.getTenchidinh(),
                staffName(order.getDoctorAccountId()), order.getNoichidinh(), null, performingDepartment(detail),
                order.getMucdochidinh() != null && order.getMucdochidinh().toLowerCase(Locale.ROOT).contains(EMERGENCY)
                        ? "EMERGENCY" : "NORMAL",
                patient(context), order.getChandoansobo(),
                context.visit().map(Visit::getLydovaovien).orElse(null),
                context.visit().map(Visit::getLydovaovien).orElse(null),
                context.form() == ResultForm.MRI ? screening(detail) : null,
                detail.getNgaychidinh(), detail.getPerformedAt(), statusOf(detail),
                detail.getPerformerAccountId() == null ? null : new ParaclinicalResultResponse.Performer(
                        detail.getPerformerAccountId(), staffName(detail.getPerformerAccountId())),
                isPerformer,
                rows.stream().map(ParaclinicalResultServiceImpl::parameter).toList(),
                result, printCount, instruments(context),
                context.visit().map(Visit::getSovaovien).orElse(null),
                clinicFacilityRepository.findCurrent()
                        .map(facility -> new ParaclinicalResultResponse.Clinic(
                                facility.getTenCskcb(), facility.getDiachi(), facility.getSodienthoai()))
                        .orElse(null),
                siblingServices);
    }

    private ParaclinicalResultResponse.Result labResult(LabResult lab) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> interpretations = new LinkedHashMap<>();
        lab.getItems().forEach(item -> {
            values.put(item.getMachiso(), item.getKetqua());
            if (item.getInterpretation() != null) {
                interpretations.put(item.getMachiso(), item.getInterpretation());
            }
        });
        LabResultItem first = lab.getItems().stream().findFirst().orElse(null);
        return new ParaclinicalResultResponse.Result(
                lab.getStatus().name(), lab.getUpdatedAt(), lab.getSubmittedAt(), lab.getGiolaymau(), values,
                interpretations, lab.getGhichu(), null, null, null, null, null, null,
                first == null ? null : first.getInstrumentId(), first == null ? null : first.getInstrumentNameSnapshot(),
                List.of());
    }

    private ParaclinicalResultResponse.Result imagingResult(ImagingResult imaging) {
        return new ParaclinicalResultResponse.Result(
                imaging.getStatus().name(), imaging.getUpdatedAt(), imaging.getSubmittedAt(), null, null, null,
                imaging.getKqclsKetluan(), imaging.getKqclsKythuat(), imaging.getKqclsMota(),
                sectionsOf(imaging.getKqclsMotaChitiet()), imaging.getKqclsDenghi(), imaging.getKqclsLinkhinhanh(),
                imaging.getReadingDoctorName(), imaging.getInstrumentId(), imaging.getInstrumentNameSnapshot(),
                List.of());
    }

    private static ParaclinicalResultResponse.Parameter parameter(LabRow row) {
        LabReferenceRange range = row.range();
        ParaclinicalResultResponse.Reference reference = range == null ? null : switch (range.getReferenceType()) {
            case RANGE -> new ParaclinicalResultResponse.Reference("RANGE", range.getLowerBound(), range.getUpperBound(), null, null);
            case UPPER_LIMIT -> new ParaclinicalResultResponse.Reference("UPPER", null, range.getUpperBound(), null, null);
            case LOWER_LIMIT -> new ParaclinicalResultResponse.Reference("LOWER", range.getLowerBound(), null, null, null);
            case QUALITATIVE -> new ParaclinicalResultResponse.Reference("QUALITATIVE", null, null,
                    List.of(range.getNormalValues()), null);
            case TEXT -> new ParaclinicalResultResponse.Reference("TEXT", null, null, null, range.getReferenceText());
        };
        return new ParaclinicalResultResponse.Parameter(
                row.parameter().getCode(), row.parameter().getName(), row.parameter().getUnit(),
                row.parameter().getValueType().name(), row.link().getGroupName(), row.link().isRequired(), reference);
    }

    private String sectionOf(Context context, List<LabRow> rows) {
        if (!context.isLab()) {
            return null;
        }
        return rows.stream().map(row -> row.link().getSectionName()).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> serviceGroupRepository.findById(Objects.requireNonNullElse(
                                context.service().getServiceGroupId(), new UUID(0, 0)))
                        .map(group -> group.getServiceGroupName().toUpperCase(Locale.ROOT))
                        .orElse("XÉT NGHIỆM"));
    }

    private ParaclinicalResultResponse.Patient patient(Context context) {
        return context.patient().map(found -> {
            Visit visit = context.visit().orElseThrow();
            LocalDate born = found.getNgaysinh();
            Integer age = born == null ? null
                    : Period.between(born, LocalDate.ofInstant(context.detail().getNgaychidinh(), CLINIC_ZONE)).getYears();
            String gender = switch (found.getGioitinh()) {
                case 1 -> "Nam";
                case 2 -> "Nữ";
                default -> "Khác";
            };
            boolean insured = visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT;
            return new ParaclinicalResultResponse.Patient(
                    found.getMabenhnhan(), found.getHoten(), age, gender, found.getGioitinh(), found.getDiachi(),
                    insured ? "BHYT" : "Dịch vụ", insured ? visit.getMabhyt() : null, found.getDantoc(),
                    found.getSodienthoai());
        }).orElse(null);
    }

    private static ParaclinicalResultResponse.Screening screening(ParaclinicalOrderDetail detail) {
        String implant = detail.getHasMetalImplant() == null ? null : detail.getHasMetalImplant() ? "Có" : "Không";
        return new ParaclinicalResultResponse.Screening(implant, detail.getPriorResults());
    }

    private static String statusOf(ParaclinicalOrderDetail detail) {
        return detail.getStatus() == ParaclinicalOrderDetailStatus.PENDING_RESULT ? "PERFORMED" : detail.getStatus().name();
    }

    private String performingDepartment(ParaclinicalOrderDetail detail) {
        if (detail.getRoomId() == null) {
            return null;
        }
        return roomRepository.findById(detail.getRoomId())
                .flatMap(room -> departmentRepository.findById(room.getDepartmentId()))
                .map(department -> department.getDepartmentName())
                .orElse(null);
    }

    private List<ParaclinicalResultResponse.Instrument> instruments(Context context) {
        if (context.detail().getRoomId() == null) {
            return List.of();
        }
        return instrumentRepository.findAllByRoomIdAndActiveTrueOrderByCodeAsc(context.detail().getRoomId()).stream()
                .filter(instrument -> instrument.getResultType() == context.instrumentType())
                .map(instrument -> new ParaclinicalResultResponse.Instrument(
                        instrument.getId(), instrument.getCode(), instrument.getName()))
                .toList();
    }

    private String staffName(UUID accountId) {
        if (accountId == null) {
            return null;
        }
        return staffAccountRepository.findById(accountId)
                .map(account -> account.getAcademicTitle() == null || account.getAcademicTitle().isBlank()
                        ? account.getFullName()
                        : account.getAcademicTitle().strip() + " " + account.getFullName())
                .orElse(null);
    }

    private static String trim(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
