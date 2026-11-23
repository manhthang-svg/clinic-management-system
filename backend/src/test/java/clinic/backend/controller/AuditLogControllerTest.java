package clinic.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.AuditLogDetailResponse;
import clinic.backend.dto.response.AuditLogItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.AuditOutcome;
import clinic.backend.service.AuditLogViewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private AuditLogViewService auditLogViewService;

    private AuditLogController controller;

    @BeforeEach
    void setUp() {
        controller = new AuditLogController(auditLogViewService);
    }

    @Test
    @DisplayName("search: chuyển nguyên các bộ lọc xuống service và bọc kết quả trong ApiResponse")
    void searchDelegatesToService() {
        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 22);
        PageResponse<AuditLogItemResponse> page =
                new PageResponse<>(List.of(), new PageResponse.PageMetadata(1, 50, 0, 0));
        when(auditLogViewService.search("admin", AuditLogAction.UPDATE, "patients", AuditOutcome.SUCCESS,
                from, to, 1, 50)).thenReturn(page);

        ApiResponse<PageResponse<AuditLogItemResponse>> response = controller.search(
                "admin", AuditLogAction.UPDATE, "patients", AuditOutcome.SUCCESS, from, to, 1, 50);

        assertThat(response.getData()).isSameAs(page);
    }

    @Test
    @DisplayName("detail: trả chi tiết một dòng nhật ký theo id")
    void detailDelegatesToService() {
        UUID id = UUID.randomUUID();
        AuditLogDetailResponse detail = new AuditLogDetailResponse(id, null, null, null, null, null,
                AuditLogAction.PRINT, "payments", null, null, AuditOutcome.SUCCESS, null, null, "{}",
                null, null, null);
        when(auditLogViewService.getDetail(id)).thenReturn(detail);

        assertThat(controller.detail(id).getData()).isSameAs(detail);
    }

    @Test
    @DisplayName("entityTypes: trả danh sách loại đối tượng cho bộ lọc")
    void entityTypesDelegatesToService() {
        when(auditLogViewService.listEntityTypes()).thenReturn(List.of("patients"));

        assertThat(controller.entityTypes().getData()).containsExactly("patients");
    }
}
