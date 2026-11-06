package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import clinic.backend.repository.Icd10Repository;
import clinic.backend.repository.Icd10Repository.SearchableCode;
import clinic.backend.service.impl.Icd10SearchCache.Entry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class Icd10SearchCacheTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 6);
    private static final LocalDate EFFECTIVE = LocalDate.of(2026, 7, 1);

    @Mock
    private Icd10Repository repository;

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-06T02:00:00Z"));
    private Icd10SearchCache cache;

    @BeforeEach
    void setUp() {
        cache = new Icd10SearchCache(repository, clock, Duration.ofHours(1), true);
    }

    @Test
    void ranksExactCodeThenCodePrefixThenNameMatches() {
        when(repository.findSearchable()).thenReturn(List.of(
                code("J18.1", "Viêm phổi thùy, không đặc hiệu", EFFECTIVE, null),
                code("J18.0", "Viêm phế quản phổi, không đặc hiệu", EFFECTIVE, null),
                code("J15.9", "Viêm phổi do vi khuẩn, không đặc hiệu", EFFECTIVE, null),
                code("J18", "Viêm phổi, tác nhân không xác định", EFFECTIVE, null)));

        assertThat(codes(cache.search("J18", DAY, 10))).containsExactly("J18", "J18.0", "J18.1");
        assertThat(codes(cache.search("j180", DAY, 10))).containsExactly("J18.0");
    }

    @Test
    void prefersNamesStartingWithTheWordOverNamesContainingIt() {
        when(repository.findSearchable()).thenReturn(List.of(
                code("A15.0", "Lao phổi, xác nhận bằng xét nghiệm", EFFECTIVE, null),
                code("J18.9", "Viêm phổi, không đặc hiệu", EFFECTIVE, null),
                code("C34.9", "Ung thư phổi", EFFECTIVE, null),
                code("J98.4", "Rối loạn khác của phổi", EFFECTIVE, null)));

        assertThat(codes(cache.search("viêm phổi", DAY, 10))).containsExactly("J18.9");
        assertThat(codes(cache.search("viem phoi", DAY, 10))).containsExactly("J18.9");
        assertThat(codes(cache.search("phoi", DAY, 10))).containsExactly("A15.0", "C34.9", "J18.9", "J98.4");
        assertThat(codes(cache.search("ung", DAY, 10))).containsExactly("C34.9");
    }

    @Test
    void leavesOutCodesNotInEffectOnTheDay() {
        when(repository.findSearchable()).thenReturn(List.of(
                code("U07.1", "COVID-19, đã xác định virus", EFFECTIVE, null),
                code("U07.2", "COVID-19, chưa xác định virus", LocalDate.of(2027, 1, 1), null),
                code("U07.3", "Mã cũ", EFFECTIVE, LocalDate.of(2026, 10, 31))));

        assertThat(codes(cache.search("covid", DAY, 10))).containsExactly("U07.1");
    }

    @Test
    void limitsTheResults() {
        when(repository.findSearchable()).thenReturn(List.of(
                code("A00.0", "Bệnh tả 1", EFFECTIVE, null), code("A00.1", "Bệnh tả 2", EFFECTIVE, null),
                code("A00.9", "Bệnh tả 3", EFFECTIVE, null)));

        assertThat(codes(cache.search("a00", DAY, 2))).containsExactly("A00.0", "A00.1");
        assertThat(cache.search(" . ", DAY, 2)).isEmpty();
    }

    @Test
    void readsTheDatabaseOncePerTtl() {
        when(repository.findSearchable()).thenReturn(List.of(code("J00", "Viêm mũi họng cấp", EFFECTIVE, null)));

        cache.preloadOnStartup();
        cache.search("viem", DAY, 10);
        clock.advance(Duration.ofMinutes(59));
        cache.search("j00", DAY, 10);
        verify(repository, times(1)).findSearchable();

        clock.advance(Duration.ofMinutes(1));
        cache.search("j00", DAY, 10);
        verify(repository, times(2)).findSearchable();
    }

    @Test
    void retriesSoonWhenNoCodesHaveBeenImportedYet() {
        when(repository.findSearchable()).thenReturn(List.of())
                .thenReturn(List.of(code("J00", "Viêm mũi họng cấp", EFFECTIVE, null)));

        assertThat(cache.search("j00", DAY, 10)).isEmpty();
        clock.advance(Duration.ofMinutes(1));

        assertThat(codes(cache.search("j00", DAY, 10))).containsExactly("J00");
    }

    @Test
    void keepsServingThePreviousListWhenTheDatabaseIsUnreachable() {
        when(repository.findSearchable())
                .thenReturn(List.of(code("J00", "Viêm mũi họng cấp", EFFECTIVE, null)))
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        cache.search("j00", DAY, 10);
        clock.advance(Duration.ofHours(2));

        assertThat(codes(cache.search("j00", DAY, 10))).containsExactly("J00");
    }

    @Test
    void failsTheFirstLoadWhenTheDatabaseIsUnreachable() {
        when(repository.findSearchable()).thenThrow(new DataAccessResourceFailureException("connection refused"));

        cache.preloadOnStartup();   // logged, never stops startup
        assertThatThrownBy(() -> cache.search("j00", DAY, 10)).isInstanceOf(DataAccessResourceFailureException.class);
    }

    static SearchableCode code(String code, String name, LocalDate from, LocalDate until) {
        return new SearchableCode() {
            @Override public String getCode() { return code; }
            @Override public String getName() { return name; }
            @Override public LocalDate getEffectiveFrom() { return from; }
            @Override public LocalDate getEffectiveUntil() { return until; }
        };
    }

    private static List<String> codes(List<Entry> entries) {
        return entries.stream().map(Entry::code).toList();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
