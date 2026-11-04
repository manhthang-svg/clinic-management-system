package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.request.CreateMedicalServiceRequest;
import clinic.backend.dto.request.LabParameterSelectionRequest;
import clinic.backend.dto.request.UpdateMedicalServiceRequest;
import clinic.backend.dto.request.UpdateMedicalServicePriceRequest;
import clinic.backend.dto.request.UpdateMedicalServiceStatusRequest;
import clinic.backend.dto.response.CreatedMedicalServiceResponse;
import clinic.backend.dto.response.MedicalServiceDetailResponse;
import clinic.backend.dto.response.MedicalServiceFormOptionsResponse;
import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.MedicalServiceStatusCheckResponse;
import clinic.backend.dto.response.LabParameterOptionResponse;
import clinic.backend.dto.response.LabReferenceRangeResponse;
import clinic.backend.dto.response.ServiceLabParameterResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PerformingRoomResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.LabParameter;
import clinic.backend.entity.LabReferenceRange;
import clinic.backend.entity.ServiceLabParameter;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ServiceGroupType;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.MedicalServiceMapper;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.LabParameterRepository;
import clinic.backend.repository.LabReferenceRangeRepository;
import clinic.backend.repository.ServiceLabParameterRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.MedicalServiceService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
public class MedicalServiceServiceImpl implements MedicalServiceService {

    private static final String SERVICE_ENTITY = "services";

    private static final List<ParaclinicalOrderDetailStatus> UNFINISHED_ORDER_STATUSES = List.of(
            ParaclinicalOrderDetailStatus.PENDING_PAYMENT,
            ParaclinicalOrderDetailStatus.WAITING,
            ParaclinicalOrderDetailStatus.IN_PROGRESS,
            ParaclinicalOrderDetailStatus.PENDING_RESULT);

    private final MedicalServiceRepository serviceRepository;
    private final DepartmentRepository departmentRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final LabParameterRepository labParameterRepository;
    private final LabReferenceRangeRepository labReferenceRangeRepository;
    private final ServiceLabParameterRepository serviceLabParameterRepository;
    private final RoomRepository roomRepository;
    private final ParaclinicalOrderDetailRepository orderDetailRepository;
    private final AuditLogService auditLogService;
    private final MedicalServiceMapper serviceMapper;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public MedicalServiceDetailResponse getService(UUID serviceId) {
        MedicalService medicalService = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        return toDetail(medicalService);
    }

    private MedicalServiceDetailResponse toDetail(MedicalService medicalService) {
        Department department = medicalService.getDepartmentId() == null
                ? null
                : departmentRepository.findById(medicalService.getDepartmentId()).orElse(null);
        ServiceGroup serviceGroup = medicalService.getServiceGroupId() == null
                ? null
                : serviceGroupRepository.findById(medicalService.getServiceGroupId()).orElse(null);
        List<PerformingRoomResponse> performingRooms = medicalService.getServiceGroupId() == null
                ? List.of()
                : roomRepository.findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(
                                medicalService.getServiceGroupId(), ActivationStatus.ACTIVE)
                        .stream()
                        .map(serviceMapper::toPerformingRoom)
                        .toList();
        return serviceMapper.toDetail(
                medicalService, nameOf(serviceGroup), nameOf(department), performingRooms,
                labParametersOf(medicalService));
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalServiceFormOptionsResponse getFormOptions() {
        List<LabParameter> parameters = labParameterRepository.findAllByActiveTrueOrderByNameAsc();
        Map<UUID, List<LabReferenceRange>> rangesByParameter = labReferenceRangeRepository
                .findAllByParameterIdInAndActiveTrueOrderByEffectiveFromAsc(
                        parameters.stream().map(LabParameter::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(LabReferenceRange::getParameterId));
        return new MedicalServiceFormOptionsResponse(
                List.of(MedicalServiceType.values()),
                serviceGroupRepository.findAllByActiveTrueOrderByServiceGroupNameAsc().stream()
                        .map(serviceMapper::toServiceGroupOption)
                        .toList(),
                departmentRepository.findAllByTypeAndActiveTrueOrderByDepartmentNameAsc(DepartmentType.PARACLINICAL)
                        .stream()
                        .map(serviceMapper::toDepartmentOption)
                        .toList(),
                parameters.stream()
                        .map(parameter -> toLabParameterOption(
                                parameter, rangesByParameter.getOrDefault(parameter.getId(), List.of())))
                        .toList());
    }

    @Override
    @Transactional
    public CreatedMedicalServiceResponse createService(
            StaffPrincipal principal, CreateMedicalServiceRequest request) {
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (serviceRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.SERVICE_CODE_ALREADY_EXISTS, Map.of("code", "ALREADY_EXISTS"));
        }
        validateAssignment(request);
        List<LabParameterSelectionRequest> parameterSelections = validateLabParameters(request);
        if (request.type() == MedicalServiceType.EXAMINATION
                && serviceRepository.existsByTypeAndStatus(
                        MedicalServiceType.EXAMINATION, ActivationStatus.ACTIVE)) {
            throw new AppException(ErrorCode.ACTIVE_EXAMINATION_SERVICE_EXISTS);
        }

        Instant now = clock.instant();
        MedicalService medicalService = MedicalService.create(
                ids.next(), now, code, request.name().strip(), request.type(),
                request.departmentId(), request.serviceGroupId(), request.clinicPrice(),
                request.insurancePrice(), normalizeOptional(request.description()));
        try {
            serviceRepository.saveAndFlush(medicalService);
        } catch (DataIntegrityViolationException exception) {
            if (request.type() == MedicalServiceType.EXAMINATION) {
                throw new AppException(ErrorCode.ACTIVE_EXAMINATION_SERVICE_EXISTS);
            }
            throw new AppException(ErrorCode.SERVICE_CODE_ALREADY_EXISTS, Map.of("code", "ALREADY_EXISTS"));
        }
        saveLabParameterMappings(medicalService, parameterSelections, now);
        auditLogService.recordEntityChange(
                principal.accountId(), medicalService.getId(), AuditLogAction.CREATE, SERVICE_ENTITY,
                Map.of(), serviceValues(medicalService, parameterSelections), now);
        return new CreatedMedicalServiceResponse(medicalService.getId());
    }

    @Override
    @Transactional
    public MedicalServiceDetailResponse updateService(
            StaffPrincipal principal, UUID serviceId, UpdateMedicalServiceRequest request) {
        MedicalService medicalService = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        String name = request.name().strip();
        String description = normalizeOptional(request.description());
        boolean unchanged = Objects.equals(medicalService.getTenchidinh(), name)
                && Objects.equals(medicalService.getDescription(), description);
        if (unchanged) {
            return toDetail(medicalService);
        }
        if (!Objects.equals(medicalService.getVersion(), request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        Instant now = clock.instant();
        Map<String, Object> oldValues = changedInformation(medicalService, name, description, true);
        Map<String, Object> newValues = changedInformation(medicalService, name, description, false);
        medicalService.updateInformation(name, description, now);
        serviceRepository.saveAndFlush(medicalService);
        auditLogService.recordEntityChange(
                principal.accountId(), medicalService.getId(), AuditLogAction.UPDATE, SERVICE_ENTITY,
                oldValues, newValues, now);
        return toDetail(medicalService);
    }

    @Override
    @Transactional
    public MedicalServiceDetailResponse updatePrices(UUID serviceId, UpdateMedicalServicePriceRequest request) {
        MedicalService medicalService = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        boolean unchanged = pricesEqual(medicalService.getClinicPrice(), request.clinicPrice())
                && pricesEqual(medicalService.getInsurancePrice(), request.insurancePrice());
        if (unchanged) {
            return toDetail(medicalService);
        }
        if (!Objects.equals(medicalService.getVersion(), request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        Instant now = clock.instant();
        medicalService.updatePrices(request.clinicPrice(), request.insurancePrice(), now);
        serviceRepository.saveAndFlush(medicalService);
        return toDetail(medicalService);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalServiceStatusCheckResponse checkDeactivation(UUID serviceId) {
        MedicalService medicalService = findService(serviceId);
        validateDeactivation(medicalService);
        long unfinishedOrderCount = medicalService.getType() == MedicalServiceType.EXAMINATION
                ? 0
                : orderDetailRepository.countByServiceIdAndStatusIn(serviceId, UNFINISHED_ORDER_STATUSES);
        return new MedicalServiceStatusCheckResponse(
                medicalService.getStatus(), ActivationStatus.INACTIVE, unfinishedOrderCount);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalServiceStatusCheckResponse checkActivation(UUID serviceId) {
        MedicalService medicalService = findService(serviceId);
        validateActivation(medicalService);
        return new MedicalServiceStatusCheckResponse(
                medicalService.getStatus(), ActivationStatus.ACTIVE, 0);
    }

    @Override
    @Transactional
    public MedicalServiceDetailResponse updateStatus(UUID serviceId, UpdateMedicalServiceStatusRequest request) {
        MedicalService medicalService = findService(serviceId);
        if (medicalService.getStatus() == request.status()) {
            return toDetail(medicalService);
        }
        if (!Objects.equals(medicalService.getVersion(), request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        if (request.status() == ActivationStatus.INACTIVE) {
            validateDeactivation(medicalService);
        } else {
            validateActivation(medicalService);
        }

        Instant now = clock.instant();
        medicalService.changeStatus(request.status(), now);
        try {
            serviceRepository.saveAndFlush(medicalService);
        } catch (DataIntegrityViolationException exception) {
            if (medicalService.getType() == MedicalServiceType.EXAMINATION
                    && request.status() == ActivationStatus.ACTIVE) {
                throw new AppException(ErrorCode.ACTIVE_EXAMINATION_SERVICE_EXISTS);
            }
            throw exception;
        }
        return toDetail(medicalService);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MedicalServiceSummaryResponse> listServices(
            String keyword,
            MedicalServiceType type,
            UUID serviceGroupId,
            UUID departmentId,
            ActivationStatus status,
            int page,
            int size,
            String sortField,
            boolean descending) {
        validatePage(page, size);
        Sort.Direction direction = descending ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortProperty(sortField)).and(Sort.by(Sort.Direction.ASC, "id"));
        Page<MedicalService> result = serviceRepository.findAll(
                specification(keyword, type, serviceGroupId, departmentId, status),
                PageRequest.of(page, size, sort));

        Map<UUID, Department> departments = departmentRepository.findAllById(result.getContent().stream()
                        .map(MedicalService::getDepartmentId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Department::getId, Function.identity()));
        Map<UUID, ServiceGroup> groups = serviceGroupRepository.findAllById(result.getContent().stream()
                        .map(MedicalService::getServiceGroupId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ServiceGroup::getId, Function.identity()));

        List<MedicalServiceSummaryResponse> content = result.getContent().stream()
                .map(service -> serviceMapper.toSummary(
                        service,
                        nameOf(groups.get(service.getServiceGroupId())),
                        nameOf(departments.get(service.getDepartmentId()))))
                .toList();
        return new PageResponse<>(content, new PageResponse.PageMetadata(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    private Specification<MedicalService> specification(
            String keyword, MedicalServiceType type, UUID serviceGroupId, UUID departmentId,
            ActivationStatus status) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (keyword != null && !keyword.isBlank()) {
                String term = "%" + keyword.strip().toLowerCase(Locale.ROOT) + "%";
                predicate = cb.and(predicate, cb.or(
                        cb.like(cb.lower(root.get("code")), term),
                        cb.like(cb.lower(root.get("tenchidinh")), term)));
            }
            if (type != null) {
                predicate = cb.and(predicate, cb.equal(root.get("type"), type));
            }
            if (serviceGroupId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("serviceGroupId"), serviceGroupId));
            }
            if (departmentId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("departmentId"), departmentId));
            }
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            return predicate;
        };
    }

    private void validateAssignment(CreateMedicalServiceRequest request) {
        if (request.type() == MedicalServiceType.EXAMINATION) {
            if (request.departmentId() != null || request.serviceGroupId() != null) {
                throw invalidAssignment("EXAMINATION_HAS_NO_DEPARTMENT_OR_GROUP");
            }
            return;
        }
        if (request.departmentId() == null || request.serviceGroupId() == null) {
            throw invalidAssignment("DEPARTMENT_AND_GROUP_REQUIRED");
        }
        Department department = departmentRepository.findById(request.departmentId())
                .orElseThrow(() -> invalidAssignment("DEPARTMENT_NOT_FOUND"));
        if (!department.isActive() || department.getType() != DepartmentType.PARACLINICAL) {
            throw invalidAssignment("DEPARTMENT_NOT_ACTIVE_PARACLINICAL");
        }
        ServiceGroup serviceGroup = serviceGroupRepository.findById(request.serviceGroupId())
                .orElseThrow(() -> invalidAssignment("SERVICE_GROUP_NOT_FOUND"));
        if (!serviceGroup.isActive()) {
            throw invalidAssignment("SERVICE_GROUP_INACTIVE");
        }
        if (serviceGroup.getType() != ServiceGroupType.valueOf(request.type().name())) {
            throw invalidAssignment("SERVICE_GROUP_TYPE_MISMATCH");
        }
        if (serviceGroup.getDepartmentId() != null && !serviceGroup.getDepartmentId().equals(request.departmentId())) {
            throw invalidAssignment("SERVICE_GROUP_DEPARTMENT_MISMATCH");
        }
    }

    private MedicalService findService(UUID serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
    }

    private void validateDeactivation(MedicalService medicalService) {
        if (medicalService.getType() == MedicalServiceType.EXAMINATION
                && !serviceRepository.existsByTypeAndStatusAndIdNot(
                        MedicalServiceType.EXAMINATION, ActivationStatus.ACTIVE, medicalService.getId())) {
            throw new AppException(ErrorCode.LAST_ACTIVE_EXAMINATION_SERVICE);
        }
    }

    private void validateActivation(MedicalService medicalService) {
        if (medicalService.getType() == MedicalServiceType.EXAMINATION) {
            if (serviceRepository.existsByTypeAndStatusAndIdNot(
                    MedicalServiceType.EXAMINATION, ActivationStatus.ACTIVE, medicalService.getId())) {
                throw new AppException(ErrorCode.ACTIVE_EXAMINATION_SERVICE_EXISTS);
            }
            return;
        }
        if (!roomRepository.existsByServiceGroupIdAndStatus(
                medicalService.getServiceGroupId(), ActivationStatus.ACTIVE)) {
            throw new AppException(ErrorCode.SERVICE_GROUP_HAS_NO_ACTIVE_ROOM);
        }
    }

    private AppException invalidAssignment(String reason) {
        return new AppException(ErrorCode.SERVICE_ASSIGNMENT_INVALID, Map.of("assignment", reason));
    }

    private List<LabParameterSelectionRequest> validateLabParameters(CreateMedicalServiceRequest request) {
        List<LabParameterSelectionRequest> selections = request.labParameters() == null
                ? List.of()
                : request.labParameters();
        if (request.type() != MedicalServiceType.LAB) {
            if (!selections.isEmpty()) {
                throw new AppException(ErrorCode.LAB_PARAMETERS_NOT_ALLOWED);
            }
            return List.of();
        }
        if (selections.isEmpty()) {
            throw new AppException(ErrorCode.LAB_PARAMETER_REQUIRED);
        }

        Set<UUID> parameterIds = new HashSet<>();
        Set<Integer> displayOrders = new HashSet<>();
        for (LabParameterSelectionRequest selection : selections) {
            if (!parameterIds.add(selection.parameterId()) || !displayOrders.add(selection.displayOrder())) {
                throw new AppException(ErrorCode.LAB_PARAMETER_ORDER_INVALID);
            }
        }
        for (int expectedOrder = 1; expectedOrder <= selections.size(); expectedOrder++) {
            if (!displayOrders.contains(expectedOrder)) {
                throw new AppException(ErrorCode.LAB_PARAMETER_ORDER_INVALID);
            }
        }
        if (labParameterRepository.findAllByIdInAndActiveTrue(parameterIds).size() != parameterIds.size()) {
            throw new AppException(ErrorCode.LAB_PARAMETER_INVALID);
        }
        return selections.stream()
                .sorted(java.util.Comparator.comparingInt(LabParameterSelectionRequest::displayOrder))
                .toList();
    }

    private void saveLabParameterMappings(
            MedicalService medicalService,
            List<LabParameterSelectionRequest> selections,
            Instant now) {
        if (selections.isEmpty()) {
            return;
        }
        List<ServiceLabParameter> mappings = selections.stream()
                .map(selection -> ServiceLabParameter.create(
                        ids.next(), now, medicalService.getId(),
                        selection.parameterId(), selection.displayOrder()))
                .toList();
        serviceLabParameterRepository.saveAllAndFlush(mappings);
    }

    private List<ServiceLabParameterResponse> labParametersOf(MedicalService medicalService) {
        if (medicalService.getType() != MedicalServiceType.LAB) {
            return List.of();
        }
        List<ServiceLabParameter> mappings =
                serviceLabParameterRepository.findAllByServiceIdOrderByDisplayOrderAsc(medicalService.getId());
        Map<UUID, LabParameter> parameters = labParameterRepository.findAllById(mappings.stream()
                        .map(ServiceLabParameter::getParameterId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(LabParameter::getId, Function.identity()));
        // Reference ranges belong to the parameter (V39); only active ones are shown, as in the form options.
        Map<UUID, List<LabReferenceRange>> rangesByParameter = parameters.isEmpty()
                ? Map.of()
                : labReferenceRangeRepository
                        .findAllByParameterIdInAndActiveTrueOrderByEffectiveFromAsc(parameters.keySet())
                        .stream()
                        .collect(Collectors.groupingBy(LabReferenceRange::getParameterId));
        return mappings.stream()
                .map(mapping -> toServiceLabParameter(
                        mapping,
                        parameters.get(mapping.getParameterId()),
                        rangesByParameter.getOrDefault(mapping.getParameterId(), List.of())))
                .filter(Objects::nonNull)
                .toList();
    }

    private ServiceLabParameterResponse toServiceLabParameter(
            ServiceLabParameter mapping, LabParameter parameter, List<LabReferenceRange> ranges) {
        if (parameter == null) {
            return null;
        }
        return new ServiceLabParameterResponse(
                parameter.getId(), parameter.getCode(), parameter.getName(),
                parameter.getValueType(), parameter.getUnit(), mapping.getDisplayOrder(),
                ranges.stream().map(this::toReferenceRangeResponse).toList());
    }

    private LabParameterOptionResponse toLabParameterOption(
            LabParameter parameter, List<LabReferenceRange> ranges) {
        return new LabParameterOptionResponse(
                parameter.getId(), parameter.getCode(), parameter.getName(),
                parameter.getValueType(), parameter.getUnit(),
                ranges.stream().map(this::toReferenceRangeResponse).toList());
    }

    private LabReferenceRangeResponse toReferenceRangeResponse(LabReferenceRange range) {
        String displayText = range.getReferenceText();
        if (displayText == null) {
            displayText = switch (range.getReferenceType()) {
                case RANGE -> range.getLowerBound().stripTrailingZeros().toPlainString()
                        + " – " + range.getUpperBound().stripTrailingZeros().toPlainString();
                case UPPER_LIMIT -> "< " + range.getUpperBound().stripTrailingZeros().toPlainString();
                case LOWER_LIMIT -> "> " + range.getLowerBound().stripTrailingZeros().toPlainString();
                case QUALITATIVE -> String.join(", ", range.normalValueList());
                case TEXT -> "";
            };
        }
        return new LabReferenceRangeResponse(
                range.getId(), range.getParameterId(), range.getReferenceType(),
                range.getLowerBound(), range.getUpperBound(), range.isLowerInclusive(),
                range.isUpperInclusive(), range.normalValueList(), range.getReferenceText(),
                range.getSex(), range.getAgeMinDays(), range.getAgeMaxDays(), range.getEffectiveFrom(),
                range.getEffectiveTo(), range.getSource(), range.isActive(), range.getVersion(), displayText);
    }

    private Map<String, Object> serviceValues(
            MedicalService service, List<LabParameterSelectionRequest> selections) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", service.getCode());
        values.put("name", service.getTenchidinh());
        values.put("type", service.getType());
        values.put("departmentId", service.getDepartmentId());
        values.put("serviceGroupId", service.getServiceGroupId());
        values.put("clinicPrice", service.getClinicPrice());
        values.put("insurancePrice", service.getInsurancePrice());
        values.put("description", service.getDescription());
        values.put("status", service.getStatus());
        values.put("labParameters", selections);
        return values;
    }

    private Map<String, Object> changedInformation(
            MedicalService service, String name, String description, boolean oldValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!Objects.equals(service.getTenchidinh(), name)) {
            values.put("name", oldValues ? service.getTenchidinh() : name);
        }
        if (!Objects.equals(service.getDescription(), description)) {
            values.put("description", oldValues ? service.getDescription() : description);
        }
        return values;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private boolean pricesEqual(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private void validatePage(int page, int size) {
        if (page < 0 || (size != 10 && size != 20 && size != 50)) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    Map.of("page", "MUST_BE_NON_NEGATIVE", "size", "ALLOWED_VALUES_10_20_50"));
        }
    }

    private String sortProperty(String requested) {
        return switch (requested == null ? "code" : requested) {
            case "code" -> "code";
            case "name" -> "tenchidinh";
            case "type" -> "type";
            case "clinicPrice" -> "clinicPrice";
            case "insurancePrice" -> "insurancePrice";
            case "status" -> "status";
            default -> throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, Map.of("sort", "NOT_ALLOWED"));
        };
    }

    private String nameOf(Department department) {
        return department == null ? null : department.getDepartmentName();
    }

    private String nameOf(ServiceGroup group) {
        return group == null ? null : group.getServiceGroupName();
    }
}
