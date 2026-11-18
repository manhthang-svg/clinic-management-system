package clinic.backend.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.RecordArchiveResponse;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.enums.RecordArchiveStatus;
import clinic.backend.service.RecordArchiveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordArchiveControllerTest {

    @Mock
    private RecordArchiveService recordArchiveService;

    private RecordArchiveController controller;

    @BeforeEach
    void setUp() {
        controller = new RecordArchiveController(recordArchiveService);
    }

    @Test
    @DisplayName("searchArchives: Chuyển tiếp query parameters xuống service và trả về ApiResponse bọc PageResponse")
    void searchArchives_delegatesToService() {
        PageResponse<RecordArchiveResponse> pageResponse = new PageResponse<>(
                List.of(),
                new PageResponse.PageMetadata(0, 20, 0, 0)
        );

        when(recordArchiveService.searchArchives("BN001", RecordArchiveRetentionCategory.STANDARD, null, null, 0, 20))
                .thenReturn(pageResponse);

        ApiResponse<PageResponse<RecordArchiveResponse>> response = controller.searchArchives(
                "BN001", RecordArchiveRetentionCategory.STANDARD, null, null, 0, 20
        );

        assertThat(response).isNotNull();
        assertThat(response.getData()).isSameAs(pageResponse);
        verify(recordArchiveService).searchArchives("BN001", RecordArchiveRetentionCategory.STANDARD, null, null, 0, 20);
    }
}

