package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import clinic.backend.dto.request.UpdateClinicFacilityRequest;
import clinic.backend.dto.response.ClinicFacilityResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.ClinicFacilityMapper;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.ClinicFacilityService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-77. Rules BR-77-01 to BR-77-05 are described in docs/uc-77-clinic-facility.md.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
public class ClinicFacilityServiceImpl implements ClinicFacilityService {

    private final ClinicFacilityRepository facilityRepository;
    private final VisitRepository visitRepository;
    private final AuditLogService auditLogService;
    private final ClinicFacilityMapper mapper;
    private final UuidV7Generator ids;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public ClinicFacilityResponse getFacility() {
        boolean locked = visitRepository.existsAny();
        return facilityRepository.findCurrent()
                .map(facility -> mapper.toResponse(facility, locked))
                .orElseGet(() -> mapper.unconfigured(locked));
    }

    @Override
    @Transactional
    public ClinicFacilityResponse saveFacility(StaffPrincipal principal, UpdateClinicFacilityRequest request) {
        Map<String, Object> newValues = values(request);
        boolean locked = visitRepository.existsAny();
        Instant now = clock.instant();
        ClinicFacility facility = facilityRepository.findCurrentForUpdate().orElse(null);

        if (facility == null) {
            if (request.version() != null) throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
            facility = new ClinicFacility(ids.next(), request.maCskcb(), strip(request.tenCskcb()),
                    strip(request.diachi()), request.sodienthoai(), strip(request.soGiayPhepHoatDong()),
                    request.hinhThucToChuc(), now);
            save(facility);
            auditLogService.recordFacilityChange(
                    principal.accountId(), facility.getId(), AuditLogAction.CREATE, Map.of(), newValues, now);
            return mapper.toResponse(facility, locked);
        }

        if (!Objects.equals(facility.getVersion(), request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        if (locked && !facility.getMaCskcb().equals(request.maCskcb())) {
            throw new AppException(ErrorCode.FACILITY_CODE_LOCKED);
        }
        Map<String, Object> oldValues = values(facility);
        oldValues.keySet().removeIf(field -> Objects.equals(oldValues.get(field), newValues.get(field)));
        newValues.keySet().retainAll(oldValues.keySet());
        if (oldValues.isEmpty()) {
            return mapper.toResponse(facility, locked);
        }

        facility.update(request.maCskcb(), strip(request.tenCskcb()), strip(request.diachi()),
                request.sodienthoai(), strip(request.soGiayPhepHoatDong()), request.hinhThucToChuc(), now);
        save(facility);
        auditLogService.recordFacilityChange(
                principal.accountId(), facility.getId(), AuditLogAction.UPDATE, oldValues, newValues, now);
        return mapper.toResponse(facility, locked);
    }

    // A concurrent first declaration hits uq_clinic_facility_single_row.
    private void save(ClinicFacility facility) {
        try {
            facilityRepository.saveAndFlush(facility);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static Map<String, Object> values(ClinicFacility facility) {
        return values(facility.getMaCskcb(), facility.getTenCskcb(), facility.getDiachi(),
                facility.getSodienthoai(), facility.getSoGiayPhepHoatDong(), facility.getHinhThucToChuc().name());
    }

    private static Map<String, Object> values(UpdateClinicFacilityRequest request) {
        return values(request.maCskcb(), strip(request.tenCskcb()), strip(request.diachi()),
                request.sodienthoai(), strip(request.soGiayPhepHoatDong()), request.hinhThucToChuc().name());
    }

    private static Map<String, Object> values(
            String maCskcb, String tenCskcb, String diachi, String sodienthoai,
            String soGiayPhepHoatDong, String hinhThucToChuc) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("maCskcb", maCskcb);
        values.put("tenCskcb", tenCskcb);
        values.put("diachi", diachi);
        values.put("sodienthoai", sodienthoai);
        values.put("soGiayPhepHoatDong", soGiayPhepHoatDong);
        values.put("hinhThucToChuc", hinhThucToChuc);
        return values;
    }

    private static String strip(String value) {
        return value.strip();
    }
}
