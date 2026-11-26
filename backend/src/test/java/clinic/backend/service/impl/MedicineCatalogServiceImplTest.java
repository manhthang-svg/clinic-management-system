package clinic.backend.service.impl;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.MedicineMapper;
import clinic.backend.repository.MedicineCatalogRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

/** UC-81 View Medicine Catalog, UC-82 Create Medicine, UC-83 Update Medicine, UC-84 Activate/Deactivate Medicine. */
@ExtendWith(MockitoExtension.class)
class MedicineCatalogServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");
    private static final StaffPrincipal ADMIN = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());

    @Mock MedicineCatalogRepository medicineRepository;
    @Mock AuditLogService auditLogService;
    @Mock UuidV7Generator ids;

    private MedicineCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MedicineCatalogServiceImpl(medicineRepository, auditLogService,
                Mappers.getMapper(MedicineMapper.class), ids, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // UC-81 View Medicine Catalog

    @Test
    @SuppressWarnings("unchecked")
    void listsMedicinesSortedByNameThenId() {
        MedicineCatalog medicine = medicine(UUID.randomUUID(), 0L);
        when(medicineRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(medicine), Pageable.ofSize(20), 1));

        PageResponse<MedicineSummaryResponse> page =
                service.listMedicines("para", null, null, null, null, 0, 20, null, false);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(medicineRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Direction.ASC, "tenthuocvattu").and(Sort.by(Sort.Direction.ASC, "id")));
        assertThat(page.content()).singleElement()
                .satisfies(item -> assertThat(item.code()).isEqualTo("PARA500"));
        assertThat(page.page().totalElements()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sortsByTheRequestedColumnDescending() {
        when(medicineRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listMedicines(null, null, null, null, ActivationStatus.ACTIVE, 1, 50, "code", true);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(medicineRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
        assertThat(pageable.getValue().getSort().getOrderFor("mathuocvattuBv").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void rejectsPageSizeOutsideTenTwentyFifty() {
        assertThatThrownBy(() -> service.listMedicines(null, null, null, null, null, 0, 30, null, false))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID));
        verifyNoInteractions(medicineRepository);
    }

    @Test
    void rejectsNegativePage() {
        assertThatThrownBy(() -> service.listMedicines(null, null, null, null, null, -1, 20, null, false))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID));
    }

    @Test
    void rejectsSortColumnThatIsNotAllowed() {
        assertThatThrownBy(() -> service.listMedicines(null, null, null, null, null, 0, 20, "price", false))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID));
    }

    @Test
    void showsMedicineDetail() {
        UUID id = UUID.randomUUID();
        when(medicineRepository.findById(id)).thenReturn(Optional.of(medicine(id, 0L)));

        MedicineDetailResponse detail = service.getMedicine(id);

        assertThat(detail.code()).isEqualTo("PARA500");
        assertThat(detail.registrationNumber()).isEqualTo("VD-12345-20");
        assertThat(detail.route()).isEqualTo("Uống");
    }

    @Test
    void detailOfMissingMedicineIsNotFound() {
        UUID id = UUID.randomUUID();
        when(medicineRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMedicine(id)).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEDICINE_NOT_FOUND));
    }

    @Test
    void formOptionsListRoutesAndUnitsInUse() {
        when(medicineRepository.findDistinctUnits()).thenReturn(List.of("Viên", "Ống"));

        MedicineFormOptionsResponse options = service.getFormOptions();

        assertThat(options.routes()).contains("Uống", "Tiêm");
        assertThat(options.units()).containsExactly("Viên", "Ống");
    }

    // UC-82 Create Medicine

    @Test
    void createsActiveMedicineWithUpperCaseCodeAndCollapsedSpaces() {
        UUID id = UUID.randomUUID();
        when(ids.next()).thenReturn(id);

        CreatedMedicineResponse created = service.createMedicine(ADMIN, new CreateMedicineRequest(
                " para500 ", " VD-12345-20 ", "  ", "  Paracetamol   500 mg ", "Paracetamol", "500 mg",
                " Uống ", "Viên", MedicineControlGroup.THUONG));

        assertThat(created.medicineId()).isEqualTo(id);
        ArgumentCaptor<MedicineCatalog> saved = ArgumentCaptor.forClass(MedicineCatalog.class);
        verify(medicineRepository).saveAndFlush(saved.capture());
        CatalogEntry entry = saved.getValue().toEntry();
        assertThat(entry.code()).isEqualTo("PARA500");
        assertThat(entry.soDangKy()).isEqualTo("VD-12345-20");
        assertThat(entry.maThuocBhyt()).isNull();
        assertThat(entry.name()).isEqualTo("Paracetamol 500 mg");
        assertThat(saved.getValue().getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        verify(auditLogService).recordEntityChange(eq(ADMIN.accountId()), eq(id), eq(AuditLogAction.CREATE),
                eq("medicine_catalog"), eq(Map.of()), anyMap(), eq(NOW));
    }

    @Test
    void rejectsExistingMedicineCode() {
        when(medicineRepository.existsByMathuocvattuBv("PARA500")).thenReturn(true);

        assertThatThrownBy(() -> service.createMedicine(ADMIN, createRequest("Uống")))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEDICINE_CODE_ALREADY_EXISTS));
        verify(medicineRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsExistingRegistrationNumber() {
        when(medicineRepository.existsBySoDangKy("VD-12345-20")).thenReturn(true);

        assertThatThrownBy(() -> service.createMedicine(ADMIN, createRequest("Uống")))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.MEDICINE_REGISTRATION_NUMBER_ALREADY_EXISTS));
        verify(medicineRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsRouteOutsideTheRouteList() {
        assertThatThrownBy(() -> service.createMedicine(ADMIN, createRequest("Ngậm dưới lưỡi xyz")))
                .isInstanceOfSatisfying(AppException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(e.getDetails()).containsEntry("route", "NOT_IN_ROUTE_LIST");
                });
        verifyNoInteractions(auditLogService);
    }

    @Test
    void reportsRegistrationNumberTakenByAConcurrentSave() {
        when(ids.next()).thenReturn(UUID.randomUUID());
        when(medicineRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup",
                new SQLException("duplicate key violates uq_medicine_catalog_so_dang_ky")));

        assertThatThrownBy(() -> service.createMedicine(ADMIN, createRequest("Uống")))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.MEDICINE_REGISTRATION_NUMBER_ALREADY_EXISTS));
        verifyNoInteractions(auditLogService);
    }

    // UC-83 Update Medicine

    @Test
    void updatesChangedFieldsAndAuditsOnlyThem() {
        UUID id = UUID.randomUUID();
        MedicineCatalog medicine = medicine(id, 3L);
        when(medicineRepository.findById(id)).thenReturn(Optional.of(medicine));

        MedicineDetailResponse detail = service.updateMedicine(ADMIN, id, updateRequest("Viên nén", 3L));

        assertThat(detail.unit()).isEqualTo("Viên nén");
        verify(medicineRepository).saveAndFlush(medicine);
        verify(auditLogService).recordEntityChange(ADMIN.accountId(), id, AuditLogAction.UPDATE, "medicine_catalog",
                Map.of("unit", "Viên"), Map.of("unit", "Viên nén"), NOW);
    }

    @Test
    void savesNothingWhenNothingChanged() {
        UUID id = UUID.randomUUID();
        when(medicineRepository.findById(id)).thenReturn(Optional.of(medicine(id, 3L)));

        service.updateMedicine(ADMIN, id, updateRequest("Viên", 0L));

        verify(medicineRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsStaleVersionOnUpdate() {
        UUID id = UUID.randomUUID();
        when(medicineRepository.findById(id)).thenReturn(Optional.of(medicine(id, 3L)));

        assertThatThrownBy(() -> service.updateMedicine(ADMIN, id, updateRequest("Viên nén", 2L)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
        verify(medicineRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsRegistrationNumberOfAnotherMedicine() {
        UUID id = UUID.randomUUID();
        when(medicineRepository.findById(id)).thenReturn(Optional.of(medicine(id, 3L)));
        when(medicineRepository.existsBySoDangKyAndIdNot("VD-12345-20", id)).thenReturn(true);

        assertThatThrownBy(() -> service.updateMedicine(ADMIN, id, updateRequest("Viên nén", 3L)))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode())
                        .isEqualTo(ErrorCode.MEDICINE_REGISTRATION_NUMBER_ALREADY_EXISTS));
    }

    @Test
    void updateOfMissingMedicineIsNotFound() {
        UUID id = UUID.randomUUID();
        when(medicineRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMedicine(ADMIN, id, updateRequest("Viên", 0L)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MEDICINE_NOT_FOUND));
    }

    // UC-84 Activate/Deactivate Medicine

    @Test
    void deactivatesMedicineAndAuditsTheStatusChange() {
        UUID id = UUID.randomUUID();
        MedicineCatalog medicine = medicine(id, 1L);
        when(medicineRepository.findById(id)).thenReturn(Optional.of(medicine));

        MedicineDetailResponse detail = service.updateStatus(ADMIN, id,
                new UpdateMedicineStatusRequest(ActivationStatus.INACTIVE, 1L));

        assertThat(detail.status()).isEqualTo(ActivationStatus.INACTIVE);
        verify(auditLogService).recordEntityChange(ADMIN.accountId(), id, AuditLogAction.UPDATE, "medicine_catalog",
                Map.of("status", ActivationStatus.ACTIVE), Map.of("status", ActivationStatus.INACTIVE), NOW);
    }

    @Test
    void sameStatusChangesNothing() {
        UUID id = UUID.randomUUID();
        when(medicineRepository.findById(id)).thenReturn(Optional.of(medicine(id, 1L)));

        service.updateStatus(ADMIN, id, new UpdateMedicineStatusRequest(ActivationStatus.ACTIVE, 0L));

        verify(medicineRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsStaleVersionOnStatusChange() {
        UUID id = UUID.randomUUID();
        when(medicineRepository.findById(id)).thenReturn(Optional.of(medicine(id, 4L)));

        assertThatThrownBy(() -> service.updateStatus(ADMIN, id,
                new UpdateMedicineStatusRequest(ActivationStatus.INACTIVE, 3L)))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));
        verifyNoInteractions(auditLogService);
    }

    private static MedicineCatalog medicine(UUID id, long version) {
        MedicineCatalog medicine = new MedicineCatalog(id, NOW.minusSeconds(86_400), new CatalogEntry("PARA500",
                "VD-12345-20", null, "Paracetamol 500 mg", "Paracetamol", "500 mg", "Uống", "Viên",
                MedicineControlGroup.THUONG));
        ReflectionTestUtils.setField(medicine, "version", version);
        return medicine;
    }

    private static CreateMedicineRequest createRequest(String route) {
        return new CreateMedicineRequest("PARA500", "VD-12345-20", null, "Paracetamol 500 mg", "Paracetamol",
                "500 mg", route, "Viên", MedicineControlGroup.THUONG);
    }

    private static UpdateMedicineRequest updateRequest(String unit, long version) {
        return new UpdateMedicineRequest("VD-12345-20", null, "Paracetamol 500 mg", "Paracetamol", "500 mg",
                "Uống", unit, MedicineControlGroup.THUONG, version);
    }
}
