package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.RecordArchiveResponse;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.RecordArchiveRepository;
import clinic.backend.repository.RecordArchiveRepository.RecordArchiveRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** UC-79 View Record Archive. */
@ExtendWith(MockitoExtension.class)
class RecordArchiveServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-20T03:00:00Z");

    @Mock RecordArchiveRepository recordArchiveRepository;

    private RecordArchiveServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RecordArchiveServiceImpl(recordArchiveRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listsArchivedRecordsWithPatientAge() {
        RecordArchiveRow row = mock(RecordArchiveRow.class);
        when(row.getSoluutru()).thenReturn("LT-2026-0001");
        when(row.getPatientName()).thenReturn("Nguyễn Văn A");
        when(row.getDateOfBirth()).thenReturn(LocalDate.of(1990, 11, 21));
        when(row.getRetentionCategory()).thenReturn(RecordArchiveRetentionCategory.STANDARD);
        when(recordArchiveRepository.searchArchives(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

        PageResponse<RecordArchiveResponse> page = service.searchArchives(null, null, null, null, null, null);

        assertThat(page.content()).singleElement().satisfies(item -> {
            assertThat(item.soluutru()).isEqualTo("LT-2026-0001");
            // Born 21/11/1990: still 35 on 20/11/2026.
            assertThat(item.age()).isEqualTo(35);
        });
        assertThat(page.page().size()).isEqualTo(20);
        assertThat(page.page().totalElements()).isEqualTo(1);
    }

    @Test
    void filtersByTrimmedKeywordCategoryAndDates() {
        when(recordArchiveRepository.searchArchives(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 11, 20);

        service.searchArchives("  BN0001  ", RecordArchiveRetentionCategory.PSYCHIATRIC, from, to, 2, 50);

        verify(recordArchiveRepository).searchArchives(RecordArchiveRetentionCategory.PSYCHIATRIC, from, to,
                "BN0001", PageRequest.of(2, 50));
    }

    @Test
    void blankKeywordSearchesEverything() {
        when(recordArchiveRepository.searchArchives(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchArchives("   ", null, null, null, null, null);

        verify(recordArchiveRepository).searchArchives(isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void cutsKeywordToOneHundredCharacters() {
        when(recordArchiveRepository.searchArchives(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchArchives("x".repeat(150), null, null, null, null, null);

        verify(recordArchiveRepository).searchArchives(isNull(), isNull(), isNull(), eq("x".repeat(100)), any());
    }

    @Test
    void clampsPageSizeToOneHundredAndNegativePageToZero() {
        when(recordArchiveRepository.searchArchives(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.searchArchives(null, null, null, null, -3, 500);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(recordArchiveRepository).searchArchives(any(), any(), any(), any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    void rejectsFromDateAfterToDate() {
        assertThatThrownBy(() -> service.searchArchives(null, null,
                LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 1), null, null))
                .isInstanceOfSatisfying(AppException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID));
        verifyNoInteractions(recordArchiveRepository);
    }

    @Test
    void recordWithoutBirthDateHasNoAge() {
        RecordArchiveRow row = mock(RecordArchiveRow.class);
        when(recordArchiveRepository.searchArchives(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        PageResponse<RecordArchiveResponse> page = service.searchArchives(null, null, null, null, null, null);

        assertThat(page.content().get(0).age()).isNull();
    }
}
