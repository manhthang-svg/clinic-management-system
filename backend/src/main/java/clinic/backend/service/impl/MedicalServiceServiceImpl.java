package clinic.backend.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ServiceGroup;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.MedicalServiceMapper;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.service.MedicalServiceService;
import lombok.RequiredArgsConstructor;
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

    private final MedicalServiceRepository serviceRepository;
    private final DepartmentRepository departmentRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final MedicalServiceMapper serviceMapper;

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
