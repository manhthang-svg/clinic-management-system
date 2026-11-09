package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.dto.request.CreateMedicineRequest;
import clinic.backend.dto.request.UpdateMedicineRequest;
import clinic.backend.dto.request.UpdateMedicineStatusRequest;
import clinic.backend.dto.response.CreatedMedicineResponse;
import clinic.backend.dto.response.MedicineDetailResponse;
import clinic.backend.dto.response.MedicineFormOptionsResponse;
import clinic.backend.dto.response.MedicineSummaryResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.entity.MedicineCatalog;
import clinic.backend.entity.MedicineCatalog.CatalogEntry;
import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicineControlGroup;
import clinic.backend.enums.MedicineRoute;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.MedicineMapper;
import clinic.backend.repository.MedicineCatalogRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.MedicineCatalogService;
import clinic.backend.utils.UuidV7Generator;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MedicineCatalogServiceImpl implements MedicineCatalogService {

    private static final String MEDICINE_ENTITY = "medicine_catalog";

    private final MedicineCatalogRepository medicineRepository;
    private final AuditLogService auditLogService;
    private final MedicineMapper medicineMapper;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MedicineSummaryResponse> listMedicines(
            String keyword,
            String unit,
            String route,
            MedicineControlGroup controlGroup,
            ActivationStatus status,
            int page,
            int size,
            String sortField,
            boolean descending) {
        validatePage(page, size);
        Sort.Direction direction = descending ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort sort = Sort.by(direction, sortProperty(sortField)).and(Sort.by(Sort.Direction.ASC, "id"));
        Page<MedicineCatalog> result = medicineRepository.findAll(
                specification(keyword, collapse(unit), collapse(route), controlGroup, status),
                PageRequest.of(page, size, sort));
        return new PageResponse<>(
                result.getContent().stream().map(medicineMapper::toSummary).toList(),
                new PageResponse.PageMetadata(
                        result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages()));
    }

    @Override
    @Transactional(readOnly = true)
    public MedicineDetailResponse getMedicine(UUID medicineId) {
        return medicineMapper.toDetail(findMedicine(medicineId));
    }

    @Override
    @Transactional(readOnly = true)
    public MedicineFormOptionsResponse getFormOptions() {
        return new MedicineFormOptionsResponse(
                Arrays.stream(MedicineRoute.values()).map(MedicineRoute::label).toList(),
                medicineRepository.findDistinctUnits());
    }

    @Override
    @Transactional
    public CreatedMedicineResponse createMedicine(StaffPrincipal principal, CreateMedicineRequest request) {
        CatalogEntry entry = entry(request.code().strip().toUpperCase(Locale.ROOT), request.registrationNumber(),
                request.insuranceCode(), request.name(), request.activeIngredient(), request.strength(),
                request.route(), request.unit(), request.controlGroup());
        if (medicineRepository.existsByMathuocvattuBv(entry.code())) {
            throw new AppException(ErrorCode.MEDICINE_CODE_ALREADY_EXISTS, Map.of("code", "ALREADY_EXISTS"));
        }
        if (medicineRepository.existsBySoDangKy(entry.soDangKy())) {
            throw registrationTaken();
        }

        Instant now = clock.instant();
        MedicineCatalog medicine = new MedicineCatalog(ids.next(), now, entry);
        try {
            medicineRepository.saveAndFlush(medicine);
        } catch (DataIntegrityViolationException exception) {
            // Another Admin saved the same code or registration number meanwhile; the failed flush aborts the
            // transaction, so the violated constraint is read from the error.
            String cause = String.valueOf(exception.getMostSpecificCause().getMessage());
            throw cause.contains("uq_medicine_catalog_so_dang_ky")
                    ? registrationTaken()
                    : new AppException(ErrorCode.MEDICINE_CODE_ALREADY_EXISTS, Map.of("code", "ALREADY_EXISTS"));
        }
        auditLogService.recordEntityChange(principal.accountId(), medicine.getId(), AuditLogAction.CREATE,
                MEDICINE_ENTITY, Map.of(), values(medicine.toEntry(), medicine.getStatus()), now);
        return new CreatedMedicineResponse(medicine.getId());
    }

    @Override
    @Transactional
    public MedicineDetailResponse updateMedicine(
            StaffPrincipal principal, UUID medicineId, UpdateMedicineRequest request) {
        MedicineCatalog medicine = findMedicine(medicineId);
        CatalogEntry before = medicine.toEntry();
        CatalogEntry entry = entry(before.code(), request.registrationNumber(), request.insuranceCode(),
                request.name(), request.activeIngredient(), request.strength(), request.route(), request.unit(),
                request.controlGroup());
        if (entry.equals(before)) {
            return medicineMapper.toDetail(medicine);
        }
        if (!Objects.equals(medicine.getVersion(), request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        if (medicineRepository.existsBySoDangKyAndIdNot(entry.soDangKy(), medicineId)) {
            throw registrationTaken();
        }

        Instant now = clock.instant();
        medicine.update(entry, now);
        try {
            medicineRepository.saveAndFlush(medicine);
        } catch (DataIntegrityViolationException exception) {
            // Another Admin took the registration number meanwhile.
            throw registrationTaken();
        }
        Map<String, Object> oldValues = values(before, null);
        Map<String, Object> newValues = values(entry, null);
        oldValues.keySet().removeIf(key -> Objects.equals(oldValues.get(key), newValues.get(key)));
        newValues.keySet().retainAll(oldValues.keySet());
        auditLogService.recordEntityChange(principal.accountId(), medicine.getId(), AuditLogAction.UPDATE,
                MEDICINE_ENTITY, oldValues, newValues, now);
        return medicineMapper.toDetail(medicine);
    }

    @Override
    @Transactional
    public MedicineDetailResponse updateStatus(
            StaffPrincipal principal, UUID medicineId, UpdateMedicineStatusRequest request) {
        MedicineCatalog medicine = findMedicine(medicineId);
        if (medicine.getStatus() == request.status()) {
            return medicineMapper.toDetail(medicine);
        }
        if (!Objects.equals(medicine.getVersion(), request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }

        Instant now = clock.instant();
        ActivationStatus previous = medicine.getStatus();
        medicine.changeStatus(request.status(), now);
        medicineRepository.saveAndFlush(medicine);
        auditLogService.recordEntityChange(principal.accountId(), medicine.getId(), AuditLogAction.UPDATE,
                MEDICINE_ENTITY, Map.of("status", previous), Map.of("status", request.status()), now);
        return medicineMapper.toDetail(medicine);
    }

    private MedicineCatalog findMedicine(UUID medicineId) {
        return medicineRepository.findById(medicineId)
                .orElseThrow(() -> new AppException(ErrorCode.MEDICINE_NOT_FOUND));
    }

    /** Normalizes the fields (spaces collapsed, empty optional value as null) and checks the route list. */
    private CatalogEntry entry(
            String code, String registrationNumber, String insuranceCode, String name, String activeIngredient,
            String strength, String route, String unit, MedicineControlGroup controlGroup) {
        String routeLabel = collapse(route);
        if (MedicineRoute.fromLabel(routeLabel).isEmpty()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("route", "NOT_IN_ROUTE_LIST"));
        }
        String insurance = collapse(insuranceCode);
        return new CatalogEntry(code, collapse(registrationNumber), insurance == null || insurance.isEmpty()
                ? null : insurance, collapse(name), collapse(activeIngredient), collapse(strength), routeLabel,
                collapse(unit), controlGroup);
    }

    private static String collapse(String value) {
        return value == null ? null : String.join(" ", value.strip().split("\\s+"));
    }

    private static AppException registrationTaken() {
        return new AppException(ErrorCode.MEDICINE_REGISTRATION_NUMBER_ALREADY_EXISTS,
                Map.of("registrationNumber", "ALREADY_EXISTS"));
    }

    private static Map<String, Object> values(CatalogEntry entry, ActivationStatus status) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", entry.code());
        values.put("registrationNumber", entry.soDangKy());
        values.put("insuranceCode", entry.maThuocBhyt());
        values.put("name", entry.name());
        values.put("activeIngredient", entry.hoatchat());
        values.put("strength", entry.hamluong());
        values.put("route", entry.duongdung());
        values.put("unit", entry.donvitinh());
        values.put("controlGroup", entry.nhomKiemSoat());
        if (status != null) {
            values.put("status", status);
        }
        return values;
    }

    private static Expression<String> searchable(CriteriaBuilder cb, Expression<String> value) {
        return cb.lower(cb.function("unaccent", String.class, value));
    }

    // The keyword is matched literally: % and _ typed by the Admin are not wildcards.
    private static String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Specification<MedicineCatalog> specification(
            String keyword, String unit, String route, MedicineControlGroup controlGroup, ActivationStatus status) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            // I1-D46: contains-match ignoring case and Vietnamese accents (unaccent, V11).
            if (keyword != null && !keyword.isBlank()) {
                Expression<String> term = searchable(cb, cb.literal("%" + escapeLike(collapse(keyword)) + "%"));
                predicate = cb.and(predicate, cb.or(
                        cb.like(searchable(cb, root.get("tenthuocvattu")), term, '\\'),
                        cb.like(searchable(cb, root.get("hoatchat")), term, '\\'),
                        cb.like(searchable(cb, root.get("hamluong")), term, '\\'),
                        cb.like(searchable(cb, root.get("mathuocvattuBv")), term, '\\'),
                        cb.like(searchable(cb, root.get("soDangKy")), term, '\\')));
            }
            if (unit != null && !unit.isEmpty()) {
                predicate = cb.and(predicate, cb.equal(root.get("donvitinh"), unit));
            }
            if (route != null && !route.isEmpty()) {
                predicate = cb.and(predicate, cb.equal(root.get("duongdung"), route));
            }
            if (controlGroup != null) {
                predicate = cb.and(predicate, cb.equal(root.get("nhomKiemSoat"), controlGroup));
            }
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            return predicate;
        };
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || (size != 10 && size != 20 && size != 50)) {
            throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID,
                    Map.of("page", "MUST_BE_NON_NEGATIVE", "size", "ALLOWED_VALUES_10_20_50"));
        }
    }

    private static String sortProperty(String requested) {
        return switch (requested == null ? "name" : requested) {
            case "name" -> "tenthuocvattu";
            case "code" -> "mathuocvattuBv";
            case "activeIngredient" -> "hoatchat";
            case "status" -> "status";
            default -> throw new AppException(ErrorCode.REQUEST_PARAMETER_INVALID, Map.of("sort", "NOT_ALLOWED"));
        };
    }
}
