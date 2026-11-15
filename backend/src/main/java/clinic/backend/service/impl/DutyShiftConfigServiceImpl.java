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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import clinic.backend.dto.request.DutyShiftConfigRequest;
import clinic.backend.dto.response.DutyShiftConfigResponse;
import clinic.backend.enums.DutyShift;
import clinic.backend.service.DutyShiftConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Triển khai dịch vụ quản lý cấu hình khung giờ ca trực (Ca Sáng & Ca Chiều) theo tuần.
 * 
 * - Cơ chế lưu trữ: Bộ nhớ tạm thời `ConcurrentHashMap` hỗ trợ truy xuất nhanh trong RAM,
 *   đồng thời ghi bền vững xuống file JSON tại đường dẫn `data/duty_shift_config.json`.
 * - Hỗ trợ chuẩn hóa ngày theo Thứ 2 đầu tuần của múi giờ Việt Nam (Asia/Ho_Chi_Minh).
 */
@Slf4j
@Service
public class DutyShiftConfigServiceImpl implements DutyShiftConfigService {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final String DEFAULT_MORNING_START = "08:00";
    public static final String DEFAULT_MORNING_END = "11:30";
    public static final String DEFAULT_AFTERNOON_START = "13:00";
    public static final String DEFAULT_AFTERNOON_END = "17:30";

    private final ObjectMapper objectMapper;
    private final Map<String, DutyShiftConfigResponse> configMap = new ConcurrentHashMap<>();
    private final Path storageFile;

    @Autowired
    public DutyShiftConfigServiceImpl(ObjectMapper objectMapper) {
        this(objectMapper, Paths.get("data", "duty_shift_config.json"));
    }

    /** Stores the configuration in {@code storageFile}; tests pass a temporary file. */
    DutyShiftConfigServiceImpl(ObjectMapper objectMapper, Path storageFile) {
        this.objectMapper = objectMapper;
        this.storageFile = storageFile;
    }

    /**
     * Khởi tạo cấu hình ca trực ngay sau khi Spring Bean được tạo (@PostConstruct).
     * Đọc dữ liệu từ file `data/duty_shift_config.json` (nếu tồn tại) và nạp vào bộ nhớ RAM `configMap`.
     * Nếu không tìm thấy file hoặc gặp lỗi, hệ thống sẽ sử dụng khung giờ mặc định.
     */
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

    /**
     * Ghi đồng bộ toàn bộ dữ liệu cấu hình ca trực từ RAM (`configMap`) xuống file JSON trên đĩa lưu trữ.
     * Tự động tạo thư mục cha (ví dụ `data/`) nếu thư mục chưa tồn tại trên hệ thống tệp.
     */
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

    /**
     * Tiện ích quy chuẩn một ngày bất kỳ về ngày Thứ 2 đầu tuần (theo múi giờ Việt Nam Asia/Ho_Chi_Minh).
     * Ngày Thứ 2 này được dùng làm khóa (key dạng "YYYY-MM-DD") để quản lý và tra cứu cấu hình theo từng tuần.
     *
     * @param date Ngày cần quy chuẩn (nếu null sẽ lấy ngày hôm nay theo giờ VN)
     * @return Ngày Thứ 2 đầu tuần tương ứng
     */
    private LocalDate toMonday(LocalDate date) {
        if (date == null) {
            date = LocalDate.now(VIETNAM_ZONE);
        }
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * Lấy cấu hình khung giờ ca trực (Ca Sáng & Ca Chiều) áp dụng cho tuần chứa ngày `date`.
     * 
     * Logic:
     * 1. Xác định ngày Thứ 2 của tuần chứa `date`.
     * 2. Tìm kiếm trong `configMap` theo khóa Thứ 2 đó.
     * 3. Nếu tuần này đã được Admin tùy chỉnh, trả về cấu hình tùy chỉnh.
     * 4. Nếu chưa được tùy chỉnh, trả về cấu hình mặc định của hệ thống:
     *    - Ca Sáng: 08:00 - 11:30
     *    - Ca Chiều: 13:00 - 17:30
     *
     * @param date Ngày cần tra cứu cấu hình ca trực
     * @return Thông tin khung giờ ca trực áp dụng cho tuần đó
     */
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

    /**
     * Cập nhật và lưu trữ cấu hình khung giờ ca trực cho một tuần cụ thể (Admin thực hiện).
     *
     * Quy tắc nghiệp vụ kiểm tra (Validation):
     * 1. Định dạng giờ phải chuẩn `HH:mm`.
     * 2. Ca sáng: Phải nằm trong khung giờ 00:00 đến 11:59; giờ bắt đầu phải trước giờ kết thúc.
     * 3. Ca chiều: Phải nằm trong khung giờ 12:00 đến 23:59; giờ bắt đầu phải trước giờ kết thúc.
     * 4. Sau khi kiểm tra hợp lệ, lưu vào RAM `configMap` và ghi bền vững xuống file JSON.
     *
     * @param request Thông tin khung giờ mới do Admin thiết lập
     * @param currentUserId ID tài khoản Admin thực hiện (để ghi log kiểm toán)
     * @return Cấu hình khung giờ mới đã được cập nhật thành công
     */
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

    /**
     * Xác định ca trực đang diễn ra tại một mốc thời gian `time` cụ thể trong ngày `date`.
     * Dùng để kiểm tra quyền tác nghiệp của Bác sĩ và KTV tại phòng khám (BR-19, BR-55):
     * Chỉ nhân viên có lịch phân công trong ca trực đang diễn ra mới được gọi khám hoặc làm kỹ thuật.
     *
     * @param date Ngày làm việc
     * @param time Giờ phút cần kiểm tra
     * @return Optional chứa `DutyShift.MORNING`, `DutyShift.AFTERNOON`, hoặc Optional.empty() nếu ngoài giờ
     */
    @Override
    public Optional<DutyShift> shiftAt(LocalDate date, LocalTime time) {
        DutyShiftConfigResponse config = getConfigForDate(date);
        if (within(time, parse(config.getMorningStartTime(), DEFAULT_MORNING_START),
                parse(config.getMorningEndTime(), DEFAULT_MORNING_END))) {
            return Optional.of(DutyShift.MORNING);
        }
        if (within(time, parse(config.getAfternoonStartTime(), DEFAULT_AFTERNOON_START),
                parse(config.getAfternoonEndTime(), DEFAULT_AFTERNOON_END))) {
            return Optional.of(DutyShift.AFTERNOON);
        }
        return Optional.empty();
    }

    /**
     * Xác định ca làm việc mà lượt đăng ký khám của bệnh nhân sẽ được xếp vào.
     *
     * Quy tắc phân bổ (Registration Slot Assignment):
     * 1. Trước hoặc bằng giờ kết thúc ca sáng -> Xếp vào Ca Sáng (MORNING).
     * 2. Từ sau khi kết thúc ca sáng đến trước hoặc bằng giờ kết thúc ca chiều:
     *    Bao gồm cả giờ nghỉ trưa (ví dụ 12:15 bệnh nhân đến quầy làm thủ tục) -> Xếp vào Ca Chiều (AFTERNOON).
     * 3. Sau giờ kết thúc ca chiều -> Không còn ca khám nào trong ngày (Optional.empty()).
     *
     * @param date Ngày tiếp nhận bệnh nhân
     * @param time Thời điểm bệnh nhân đăng ký tại quầy tiếp tân
     * @return Optional chứa ca làm việc tương ứng
     */
    @Override
    public Optional<DutyShift> registrationShift(LocalDate date, LocalTime time) {
        DutyShiftConfigResponse config = getConfigForDate(date);
        if (!time.isAfter(parse(config.getMorningEndTime(), DEFAULT_MORNING_END))) {
            return Optional.of(DutyShift.MORNING);
        }
        if (!time.isAfter(parse(config.getAfternoonEndTime(), DEFAULT_AFTERNOON_END))) {
            return Optional.of(DutyShift.AFTERNOON);
        }
        return Optional.empty();
    }

    /**
     * Kiểm tra một mốc thời gian có nằm trong khoảng thời gian [start, end] (bao gồm cả mốc biên) hay không.
     *
     * @param time  Thời gian cần kiểm tra
     * @param start Thời gian bắt đầu
     * @param end   Thời gian kết thúc
     * @return true nếu start <= time <= end
     */
    private static boolean within(LocalTime time, LocalTime start, LocalTime end) {
        return !time.isBefore(start) && !time.isAfter(end);
    }

    /**
     * Phân tích an toàn chuỗi giờ phút ("HH:mm") sang đối tượng LocalTime.
     * Nếu chuỗi rỗng, null hoặc sai định dạng, hàm sẽ tự động dùng giá trị mặc định `fallback`.
     *
     * @param value    Chuỗi giờ phút đầu vào
     * @param fallback Chuỗi giờ phút dự phòng
     * @return Đối tượng LocalTime hợp lệ
     */
    private static LocalTime parse(String value, String fallback) {
        try {
            return LocalTime.parse(value == null ? fallback : value.trim());
        } catch (Exception e) {
            return LocalTime.parse(fallback);
        }
    }
}
