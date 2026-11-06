package clinic.backend.service.impl;

import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.dto.request.DutyShiftConfigRequest;
import clinic.backend.dto.response.DutyShiftConfigResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DutyShiftConfigServiceImplTest {

    private DutyShiftConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        service = new DutyShiftConfigServiceImpl(mapper);
    }

    @Test
    @DisplayName("Returns default configuration when no custom config exists")
    void testGetDefaultConfig() {
        LocalDate date = LocalDate.of(2026, 11, 6); // Friday
        DutyShiftConfigResponse config = service.getConfigForDate(date);

        assertThat(config.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 2)); // Monday of that week
        assertThat(config.getMorningStartTime()).isEqualTo("08:00");
        assertThat(config.getMorningEndTime()).isEqualTo("11:30");
        assertThat(config.getAfternoonStartTime()).isEqualTo("13:00");
        assertThat(config.getAfternoonEndTime()).isEqualTo("17:30");
    }

    @Test
    @DisplayName("Updates valid shift config successfully")
    void testUpdateValidConfig() {
        DutyShiftConfigRequest request = DutyShiftConfigRequest.builder()
                .startDate(LocalDate.of(2026, 11, 6))
                .morningStartTime("07:00")
                .morningEndTime("11:45")
                .afternoonStartTime("13:15")
                .afternoonEndTime("18:00")
                .build();

        DutyShiftConfigResponse result = service.updateConfig(request, UUID.randomUUID());

        assertThat(result.getMorningStartTime()).isEqualTo("07:00");
        assertThat(result.getMorningEndTime()).isEqualTo("11:45");
        assertThat(result.getAfternoonStartTime()).isEqualTo("13:15");
        assertThat(result.getAfternoonEndTime()).isEqualTo("18:00");

        DutyShiftConfigResponse fetched = service.getConfigForDate(LocalDate.of(2026, 11, 5));
        assertThat(fetched.getMorningStartTime()).isEqualTo("07:00");
    }

    @Test
    @DisplayName("Rejects morning time after 11:59")
    void testRejectMorningAfterLimit() {
        DutyShiftConfigRequest request = DutyShiftConfigRequest.builder()
                .startDate(LocalDate.of(2026, 11, 2))
                .morningStartTime("08:00")
                .morningEndTime("12:30")
                .afternoonStartTime("13:00")
                .afternoonEndTime("17:30")
                .build();

        assertThatThrownBy(() -> service.updateConfig(request, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("00:00 đến 11:59");
    }

    @Test
    @DisplayName("Rejects morning start time >= end time")
    void testRejectMorningStartAfterEnd() {
        DutyShiftConfigRequest request = DutyShiftConfigRequest.builder()
                .startDate(LocalDate.of(2026, 11, 2))
                .morningStartTime("10:00")
                .morningEndTime("09:00")
                .afternoonStartTime("13:00")
                .afternoonEndTime("17:30")
                .build();

        assertThatThrownBy(() -> service.updateConfig(request, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Giờ bắt đầu ca sáng phải trước giờ kết thúc");
    }

    @Test
    @DisplayName("Rejects afternoon start before 12:00")
    void testRejectAfternoonBeforeLimit() {
        DutyShiftConfigRequest request = DutyShiftConfigRequest.builder()
                .startDate(LocalDate.of(2026, 11, 2))
                .morningStartTime("08:00")
                .morningEndTime("11:30")
                .afternoonStartTime("11:45")
                .afternoonEndTime("17:30")
                .build();

        assertThatThrownBy(() -> service.updateConfig(request, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("12:00 đến 23:59");
    }

    @Test
    @DisplayName("Rejects afternoon start time >= end time")
    void testRejectAfternoonStartAfterEnd() {
        DutyShiftConfigRequest request = DutyShiftConfigRequest.builder()
                .startDate(LocalDate.of(2026, 11, 2))
                .morningStartTime("08:00")
                .morningEndTime("11:30")
                .afternoonStartTime("18:00")
                .afternoonEndTime("17:30")
                .build();

        assertThatThrownBy(() -> service.updateConfig(request, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Giờ bắt đầu ca chiều phải trước giờ kết thúc");
    }
}
