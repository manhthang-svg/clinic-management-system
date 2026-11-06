package clinic.backend.service.impl;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import clinic.backend.dto.request.DutyShiftConfigRequest;
import clinic.backend.dto.response.DutyShiftConfigResponse;
import clinic.backend.service.DutyShiftConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DutyShiftConfigServiceImpl implements DutyShiftConfigService {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String DEFAULT_MORNING_START = "08:00";
    public static final String DEFAULT_MORNING_END = "11:30";
    public static final String DEFAULT_AFTERNOON_START = "13:00";
    public static final String DEFAULT_AFTERNOON_END = "17:30";

    private final ObjectMapper objectMapper;
    private final Map<String, DutyShiftConfigResponse> configMap = new ConcurrentHashMap<>();
    private final Path storageFile = Paths.get("data", "duty_shift_config.json");

    @PostConstruct
    public void init() {
        try {
            objectMapper.findAndRegisterModules();
            if (Files.exists(storageFile)) {
                Map<String, DutyShiftConfigResponse> loaded = objectMapper.readValue(
                        storageFile.toFile(),
                        new TypeReference<Map<String, DutyShiftConfigResponse>>() {}
                );
                if (loaded != null) {
                    configMap.putAll(loaded);
                    log.info("Đã tải {} cấu hình khung giờ ca trực từ file lưu trữ", loaded.size());
                }
            }
        } catch (Exception e) {
            log.warn("Không thể tải cấu hình khung giờ ca trực từ file, sử dụng cấu hình mặc định: {}", e.getMessage());
        }
    }

    private void persistToFile() {
        try {
            File file = storageFile.toFile();
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, configMap);
        } catch (Exception e) {
            log.warn("Lỗi khi ghi cấu hình ca trực ra file: {}", e.getMessage());
        }
    }

    private LocalDate toMonday(LocalDate date) {
        if (date == null) {
            date = LocalDate.now(VIETNAM_ZONE);
        }
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Override
    public DutyShiftConfigResponse getConfigForDate(LocalDate date) {
        LocalDate monday = toMonday(date);
        String key = monday.toString();

        DutyShiftConfigResponse config = configMap.get(key);
        if (config != null) {
            return config;
        }

        return DutyShiftConfigResponse.builder()
                .startDate(monday)
                .morningStartTime(DEFAULT_MORNING_START)
                .morningEndTime(DEFAULT_MORNING_END)
                .afternoonStartTime(DEFAULT_AFTERNOON_START)
                .afternoonEndTime(DEFAULT_AFTERNOON_END)
                .build();
    }

    @Override
    public DutyShiftConfigResponse updateConfig(DutyShiftConfigRequest request, UUID currentUserId) {
        LocalDate monday = toMonday(request.getStartDate());

        String morningStartStr = request.getMorningStartTime() != null ? request.getMorningStartTime().trim() : DEFAULT_MORNING_START;
        String morningEndStr = request.getMorningEndTime() != null ? request.getMorningEndTime().trim() : DEFAULT_MORNING_END;
        String afternoonStartStr = request.getAfternoonStartTime() != null ? request.getAfternoonStartTime().trim() : DEFAULT_AFTERNOON_START;
        String afternoonEndStr = request.getAfternoonEndTime() != null ? request.getAfternoonEndTime().trim() : DEFAULT_AFTERNOON_END;

        LocalTime morningStart;
        LocalTime morningEnd;
        LocalTime afternoonStart;
        LocalTime afternoonEnd;

        try {
            morningStart = LocalTime.parse(morningStartStr);
            morningEnd = LocalTime.parse(morningEndStr);
            afternoonStart = LocalTime.parse(afternoonStartStr);
            afternoonEnd = LocalTime.parse(afternoonEndStr);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Định dạng thời gian không hợp lệ. Vui lòng sử dụng định dạng HH:mm");
        }

        // Validate Ca sáng: 00:00 - 11:59
        LocalTime morningMin = LocalTime.of(0, 0);
        LocalTime morningMax = LocalTime.of(11, 59);
        if (morningStart.isBefore(morningMin) || morningStart.isAfter(morningMax)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ bắt đầu ca sáng phải trong khoảng từ 00:00 đến 11:59");
        }
        if (morningEnd.isBefore(morningMin) || morningEnd.isAfter(morningMax)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ kết thúc ca sáng phải trong khoảng từ 00:00 đến 11:59");
        }
        if (!morningStart.isBefore(morningEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ bắt đầu ca sáng phải trước giờ kết thúc ca sáng");
        }

        // Validate Ca chiều: 12:00 - 23:59
        LocalTime afternoonMin = LocalTime.of(12, 0);
        LocalTime afternoonMax = LocalTime.of(23, 59);
        if (afternoonStart.isBefore(afternoonMin) || afternoonStart.isAfter(afternoonMax)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ bắt đầu ca chiều phải trong khoảng từ 12:00 đến 23:59");
        }
        if (afternoonEnd.isBefore(afternoonMin) || afternoonEnd.isAfter(afternoonMax)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ kết thúc ca chiều phải trong khoảng từ 12:00 đến 23:59");
        }
        if (!afternoonStart.isBefore(afternoonEnd)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ bắt đầu ca chiều phải trước giờ kết thúc ca chiều");
        }

        DutyShiftConfigResponse updated = DutyShiftConfigResponse.builder()
                .startDate(monday)
                .morningStartTime(morningStartStr)
                .morningEndTime(morningEndStr)
                .afternoonStartTime(afternoonStartStr)
                .afternoonEndTime(afternoonEndStr)
                .build();

        configMap.put(monday.toString(), updated);
        persistToFile();

        log.info("Đã cập nhật khung giờ ca trực cho tuần {} bởi tài khoản {}: Sáng [{} - {}], Chiều [{} - {}]",
                monday, currentUserId, morningStartStr, morningEndStr, afternoonStartStr, afternoonEndStr);

        return updated;
    }
}
