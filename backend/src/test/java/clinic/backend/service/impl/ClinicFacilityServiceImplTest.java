package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.UpdateClinicFacilityRequest;
import clinic.backend.dto.response.ClinicFacilityResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.FacilityOrganizationForm;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.ClinicFacilityMapper;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClinicFacilityServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");
    private static final StaffPrincipal ADMIN = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @Mock ClinicFacilityRepository facilityRepository;
    @Mock VisitRepository visitRepository;
    @Mock AuditLogService auditLogService;
    @Mock UuidV7Generator ids;

    private ClinicFacilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClinicFacilityServiceImpl(facilityRepository, visitRepository, auditLogService,
                Mappers.getMapper(ClinicFacilityMapper.class), ids, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reportsAnUnconfiguredFacility() {
        when(facilityRepository.findCurrent()).thenReturn(Optional.empty());

        ClinicFacilityResponse response = service.getFacility();

        assertThat(response.configured()).isFalse();
        assertThat(response.maCskcb()).isNull();
        assertThat(response.maCskcbLocked()).isFalse();
    }

    @Test
    void declaresTheFacilityOnFirstSaveAndStripsText() {
        UUID id = UUID.randomUUID();
        when(facilityRepository.findCurrentForUpdate()).thenReturn(Optional.empty());
        when(ids.next()).thenReturn(id);

        ClinicFacilityResponse response = service.saveFacility(ADMIN,
                request("79001", "  PK Đa khoa An Bình ", "02812345678", FacilityOrganizationForm.PHONG_KHAM_DA_KHOA,
                        null));

        assertThat(response.configured()).isTrue();
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.tenCskcb()).isEqualTo("PK Đa khoa An Bình");
        verify(facilityRepository).saveAndFlush(any(ClinicFacility.class));
        verify(auditLogService).recordFacilityChange(
                eq(ADMIN.accountId()), eq(id), eq(AuditLogAction.CREATE), eq(Map.of()), anyMap(), eq(NOW));
    }

    @Test
    void rejectsAFirstSaveThatCarriesAVersion() {
        when(facilityRepository.findCurrentForUpdate()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveFacility(ADMIN, request("79001", "PK", "02812345678",
                FacilityOrganizationForm.PHONG_KHAM_DA_KHOA, 0L)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
        verify(facilityRepository, never()).saveAndFlush(any());
    }

    @Test
    void auditsOnlyTheChangedFields() {
        ClinicFacility facility = existing("79001", 3L);
        when(facilityRepository.findCurrentForUpdate()).thenReturn(Optional.of(facility));

        service.saveFacility(ADMIN, request("79001", "PK Đa khoa An Bình", "0901234567",
                FacilityOrganizationForm.PHONG_KHAM_DA_KHOA, 3L));

        assertThat(facility.getSodienthoai()).isEqualTo("0901234567");
        assertThat(facility.getUpdatedAt()).isEqualTo(NOW);
        verify(auditLogService).recordFacilityChange(ADMIN.accountId(), facility.getId(), AuditLogAction.UPDATE,
                Map.of("sodienthoai", "02812345678"), Map.of("sodienthoai", "0901234567"), NOW);
    }

    @Test
    void locksTheFacilityCodeOnceAVisitExists() {
        ClinicFacility facility = existing("79001", 3L);
        when(facilityRepository.findCurrentForUpdate()).thenReturn(Optional.of(facility));
        when(visitRepository.existsAny()).thenReturn(true);

        assertThatThrownBy(() -> service.saveFacility(ADMIN, request("79002", "PK Đa khoa An Bình", "02812345678",
                FacilityOrganizationForm.PHONG_KHAM_DA_KHOA, 3L)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FACILITY_CODE_LOCKED));
        assertThat(facility.getMaCskcb()).isEqualTo("79001");
        verify(facilityRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void updatesOtherFieldsWhileTheCodeIsLocked() {
        ClinicFacility facility = existing("79001", 3L);
        when(facilityRepository.findCurrentForUpdate()).thenReturn(Optional.of(facility));
        when(visitRepository.existsAny()).thenReturn(true);

        ClinicFacilityResponse response = service.saveFacility(ADMIN, request("79001", "PK Đa khoa An Bình",
                "02812345678", FacilityOrganizationForm.PHONG_KHAM_CHUYEN_KHOA, 3L));

        assertThat(response.maCskcbLocked()).isTrue();
        assertThat(facility.getHinhThucToChuc()).isEqualTo(FacilityOrganizationForm.PHONG_KHAM_CHUYEN_KHOA);
    }

    @Test
    void changesTheCodeBeforeTheFirstVisit() {
        ClinicFacility facility = existing("79001", 3L);
        when(facilityRepository.findCurrentForUpdate()).thenReturn(Optional.of(facility));

        service.saveFacility(ADMIN, request("79002", "PK Đa khoa An Bình", "02812345678",
                FacilityOrganizationForm.PHONG_KHAM_DA_KHOA, 3L));

        assertThat(facility.getMaCskcb()).isEqualTo("79002");
    }

    @Test
    void rejectsAStaleVersion() {
        when(facilityRepository.findCurrentForUpdate()).thenReturn(Optional.of(existing("79001", 3L)));

        assertThatThrownBy(() -> service.saveFacility(ADMIN, request("79001", "PK", "02812345678",
                FacilityOrganizationForm.PHONG_KHAM_DA_KHOA, 2L)))
                .isInstanceOfSatisfying(AppException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
    }

    @Test
    void doesNothingWhenNothingChanged() {
        ClinicFacility facility = existing("79001", 3L);
        when(facilityRepository.findCurrentForUpdate()).thenReturn(Optional.of(facility));

        service.saveFacility(ADMIN, request("79001", "PK Đa khoa An Bình", "02812345678",
                FacilityOrganizationForm.PHONG_KHAM_DA_KHOA, 3L));

        verify(facilityRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    private static UpdateClinicFacilityRequest request(
            String code, String name, String phone, FacilityOrganizationForm form, Long version) {
        return new UpdateClinicFacilityRequest(
                code, name, "1 Lê Lợi, Phường Sài Gòn, TP. Hồ Chí Minh", phone, "01234/HCM-GPHĐ", form, version);
    }

    private static ClinicFacility existing(String code, long version) {
        ClinicFacility facility = new ClinicFacility(UUID.randomUUID(), code, "PK Đa khoa An Bình",
                "1 Lê Lợi, Phường Sài Gòn, TP. Hồ Chí Minh", "02812345678", "01234/HCM-GPHĐ",
                FacilityOrganizationForm.PHONG_KHAM_DA_KHOA, Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(facility, "version", version);
        return facility;
    }
}
