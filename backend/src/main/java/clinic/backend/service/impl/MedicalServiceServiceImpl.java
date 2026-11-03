package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.request.CreateMedicalServiceRequest;
import clinic.backend.dto.request.UpdateMedicalServiceRequest;
import clinic.backend.dto.request.UpdateMedicalServicePriceRequest;
import clinic.backend.dto.request.UpdateMedicalServiceStatusRequest;
import clinic.backend.dto.response.CreatedMedicalServiceResponse;
import clinic.backend.dto.response.MedicalServiceDetailResponse;
import clinic.backend.dto.response.MedicalServiceFormOptionsResponse;
import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.MedicalServiceStatusCheckResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PerformingRoomResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.DepartmentType;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ServiceGroupType;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.MedicalServiceMapper;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
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

    private static final List<ParaclinicalOrderDetailStatus> UNFINISHED_ORDER_STATUSES = List.of(
            ParaclinicalOrderDetailStatus.PENDING_PAYMENT,
            ParaclinicalOrderDetailStatus.WAITING,
            ParaclinicalOrderDetailStatus.IN_PROGRESS,
            ParaclinicalOrderDetailStatus.PENDING_RESULT);

    private final MedicalServiceRepository serviceRepository;
    private final DepartmentRepository departmentRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final RoomRepository roomRepository;
    private final ParaclinicalOrderDetailRepository orderDetailRepository;
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
                medicalService, nameOf(serviceGroup), nameOf(department), performingRooms);
    }

    @Override
    @Transactional(readOnly = true)
    public MedicalServiceFormOptionsResponse getFormOptions() {
        return new MedicalServiceFormOptionsResponse(
                List.of(MedicalServiceType.values()),
                serviceGroupRepository.findAllByOrderByServiceGroupNameAsc().stream()
                        .map(serviceMapper::toServiceGroupOption)
                        .toList(),
                departmentRepository.findAllByTypeAndActiveTrueOrderByDepartmentNameAsc(DepartmentType.PARACLINICAL)
                        .stream()
                        .map(serviceMapper::toDepartmentOption)
                        .toList());
    }

    @Override
    @Transactional
    public CreatedMedicalServiceResponse createService(CreateMedicalServiceRequest request) {
        String code = request.code().strip().toUpperCase(Locale.ROOT);
        if (serviceRepository.existsByCode(code)) {
            throw new AppException(ErrorCode.SERVICE_CODE_ALREADY_EXISTS, Map.of("code", "ALREADY_EXISTS"));
        }
        validateAssignment(request);
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
        return new CreatedMedicalServiceResponse(medicalService.getId());
    }

    @Override
    @Transactional
    public MedicalServiceDetailResponse updateService(UUID serviceId, UpdateMedicalServiceRequest request) {
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
        medicalService.updateInformation(name, description, now);
        serviceRepository.saveAndFlush(medicalService);
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
