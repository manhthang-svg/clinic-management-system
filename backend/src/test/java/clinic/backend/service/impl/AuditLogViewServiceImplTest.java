package clinic.backend.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import clinic.backend.dto.response.AuditLogDetailResponse;
import clinic.backend.dto.response.AuditLogItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.entity.AuditLog;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.Role;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.AuditLogRepository;
import clinic.backend.repository.StaffAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogViewServiceImplTest {

    private static final Instant OCCURRED_AT = Instant.parse("2026-11-20T03:15:00Z");

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private StaffAccountRepository staffAccountRepository;

    private AuditLogViewServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditLogViewServiceImpl(auditLogRepository, staffAccountRepository);
    }

    @Test
    @DisplayName("search: trả về trang nhật ký, gắn tên đăng nhập, họ tên và vai trò của người thực hiện")
    void searchMapsRowsWithActorNames() {
        StaffAccount admin = account(Role.ADMIN, "admin.an", "Nguyễn Văn An");
        AuditLog log = AuditLog.businessChange(UUID.randomUUID(), admin.getId(), AuditLogAction.UPDATE,
                "patients", UUID.randomUUID(), "VV001", OCCURRED_AT, "{\"name\":\"x\"}", "req-1");
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));
        when(staffAccountRepository.findAllById(Set.of(admin.getId()))).thenReturn(List.of(admin));

        PageResponse<AuditLogItemResponse> result =
                service.search(null, null, null, null, null, null, 0, 20);

        assertThat(result.page().totalElements()).isEqualTo(1);
        AuditLogItemResponse item = result.content().get(0);
        assertThat(item.id()).isEqualTo(log.getId());
        assertThat(item.actorUsername()).isEqualTo("admin.an");
        assertThat(item.actorFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(item.actorRole()).isEqualTo(Role.ADMIN);
        assertThat(item.action()).isEqualTo(AuditLogAction.UPDATE);
        assertThat(item.entityType()).isEqualTo("patients");
        assertThat(item.sovaovien()).isEqualTo("VV001");
        assertThat(item.outcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    @DisplayName("search: dòng do tác vụ hệ thống ghi không có người thực hiện và không tra tài khoản")
    void searchLeavesSystemActorEmpty() {
        AuditLog log = AuditLog.systemChange(UUID.randomUUID(), AuditLogAction.UPDATE, "visits",
                UUID.randomUUID(), "VV002", OCCURRED_AT, "{}");
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(log), PageRequest.of(0, 20), 1));

        PageResponse<AuditLogItemResponse> result =
                service.search(null, null, null, null, null, null, 0, 20);

        assertThat(result.content().get(0).actorAccountId()).isNull();
        assertThat(result.content().get(0).actorUsername()).isNull();
        verify(staffAccountRepository, never()).findAllById(anyIterable());
    }

    @Test
    @DisplayName("search: sắp xếp mới nhất trước, kích thước trang bị giới hạn 1..100, trang âm thành 0")
    void searchClampsPagingAndSortsNewestFirst() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        PageResponse<AuditLogItemResponse> result =
                service.search(null, null, null, null, null, null, -3, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort())
                .isEqualTo(Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")));
        assertThat(result.page().number()).isZero();
        assertThat(result.page().size()).isEqualTo(100);
    }

    @Test
    @DisplayName("search: mặc định trang 0, 20 dòng khi không truyền page/size")
    void searchUsesDefaultPaging() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.search(null, null, null, null, null, null, null, null);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("search: từ ngày sau đến ngày thì báo lỗi tham số và không truy vấn")
    void searchRejectsReversedDateRange() {
        assertThatThrownBy(() -> service.search(null, null, null, null,
                LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 1), 0, 20))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID);
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("search: cùng một ngày cho cả từ và đến là hợp lệ")
    void searchAcceptsSingleDay() {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<AuditLogItemResponse> result = service.search("  admin  ", AuditLogAction.PRINT,
                " payments ", AuditOutcome.SUCCESS, LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 22), 0, 20);

        assertThat(result.content()).isEmpty();
    }

    @Test
    @DisplayName("normalize: bỏ khoảng trắng thừa, rỗng thành null, cắt tối đa 100 ký tự")
    void normalizeKeyword() {
        assertThat(AuditLogViewServiceImpl.normalize(null)).isNull();
        assertThat(AuditLogViewServiceImpl.normalize("   ")).isNull();
        assertThat(AuditLogViewServiceImpl.normalize("  Nguyễn   Văn  ")).isEqualTo("Nguyễn Văn");
        assertThat(AuditLogViewServiceImpl.normalize("a".repeat(150))).hasSize(100);
    }

    @Test
    @DisplayName("getDetail: trả giá trị cũ, mới, trường thay đổi và thông tin người thực hiện")
    void detailReturnsOldAndNewValues() {
        StaffAccount admin = account(Role.ADMIN, "admin.an", "Nguyễn Văn An");
        UUID facilityId = UUID.randomUUID();
        AuditLog log = AuditLog.facilityChanged(UUID.randomUUID(), admin.getId(), facilityId,
                AuditLogAction.UPDATE, OCCURRED_AT, new String[] {"name"},
                "{\"name\":\"Cũ\"}", "{\"name\":\"Mới\"}", "req-9");
        when(auditLogRepository.findById(log.getId())).thenReturn(Optional.of(log));
        when(staffAccountRepository.findById(admin.getId())).thenReturn(Optional.of(admin));

        AuditLogDetailResponse detail = service.getDetail(log.getId());

        assertThat(detail.entityType()).isEqualTo("clinic_facility");
        assertThat(detail.entityId()).isEqualTo(facilityId);
        assertThat(detail.changedFields()).containsExactly("name");
        assertThat(detail.oldValue()).isEqualTo("{\"name\":\"Cũ\"}");
        assertThat(detail.newValue()).isEqualTo("{\"name\":\"Mới\"}");
        assertThat(detail.correlationId()).isEqualTo("req-9");
        assertThat(detail.actorFullName()).isEqualTo("Nguyễn Văn An");
    }

    @Test
    @DisplayName("getDetail: người thực hiện đã không còn tìm thấy thì để trống thông tin tài khoản")
    void detailToleratesMissingActor() {
        UUID actorId = UUID.randomUUID();
        AuditLog log = AuditLog.recordRead(UUID.randomUUID(), actorId, "patients", null, null,
                OCCURRED_AT, "{}", null);
        when(auditLogRepository.findById(log.getId())).thenReturn(Optional.of(log));
        when(staffAccountRepository.findById(actorId)).thenReturn(Optional.empty());

        AuditLogDetailResponse detail = service.getDetail(log.getId());

        assertThat(detail.actorAccountId()).isEqualTo(actorId);
        assertThat(detail.actorUsername()).isNull();
        assertThat(detail.action()).isEqualTo(AuditLogAction.VIEW_RECORD);
    }

    @Test
    @DisplayName("getDetail: không có dòng nhật ký thì báo AUDIT_LOG_NOT_FOUND")
    void detailNotFound() {
        UUID id = UUID.randomUUID();
        when(auditLogRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(id))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.AUDIT_LOG_NOT_FOUND);
    }

    @Test
    @DisplayName("listEntityTypes: trả danh sách loại đối tượng từ repository")
    void listEntityTypes() {
        when(auditLogRepository.findDistinctEntityTypes()).thenReturn(List.of("patients", "payments"));

        assertThat(service.listEntityTypes()).containsExactly("patients", "payments");
    }

    private static StaffAccount account(Role role, String username, String fullName) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return StaffAccount.builder()
                .id(UUID.randomUUID())
                .username(username)
                .passwordHash("hash")
                .fullName(fullName)
                .phoneNumber("0912345678")
                .role(role)
                .status(AccountStatus.ACTIVE)
                .passwordChangeRequired(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
