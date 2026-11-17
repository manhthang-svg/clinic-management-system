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

// UC-77: thong tin co so kham chua benh (quy tac trong docs/uc-77-clinic-facility.md)
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

    // xem thong tin co so; da co luot kham thi khoa ma co so
    @Override
    @Transactional(readOnly = true)
    public ClinicFacilityResponse getFacility() {
        boolean locked = visitRepository.existsAny();
        return facilityRepository.findCurrent()
                .map(facility -> mapper.toResponse(facility, locked))
                .orElseGet(() -> mapper.unconfigured(locked));
    }

    // khai bao lan dau hoac cap nhat thong tin co so
    @Override
    @Transactional
    public ClinicFacilityResponse saveFacility(StaffPrincipal principal, UpdateClinicFacilityRequest request) {
        Map<String, Object> newValues = values(request);
        boolean locked = visitRepository.existsAny();
        Instant now = clock.instant();
        ClinicFacility facility = facilityRepository.findCurrentForUpdate().orElse(null);

        // chua khai bao: tao moi
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

        // nguoi khac vua sua thi bao loi; da co luot kham thi khong cho doi ma co so
        if (!Objects.equals(facility.getVersion(), request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        if (locked && !facility.getMaCskcb().equals(request.maCskcb())) {
            throw new AppException(ErrorCode.FACILITY_CODE_LOCKED);
        }
        // chi giu cac truong bi doi; khong doi gi thi khong luu, khong ghi audit
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

    // 2 nguoi cung khai bao lan dau thi nguoi sau bi loi trung (chi 1 dong)
    private void save(ClinicFacility facility) {
        try {
            facilityRepository.saveAndFlush(facility);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    // gia tri cac truong de so sanh / ghi audit
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
