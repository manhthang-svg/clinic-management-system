package clinic.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.DutySchedule;
import clinic.backend.enums.DutyShift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DutyScheduleRepository extends JpaRepository<DutySchedule, UUID> {

    // lich theo tuan
    List<DutySchedule> findByWorkDateBetweenOrderByWorkDateAscShiftAsc(LocalDate startDate, LocalDate endDate);

    // lay lich theo phong de xac ding ktv phong hien tai
    List<DutySchedule> findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(UUID roomId, LocalDate startDate, LocalDate endDate);

    // lay lich cua nhan vien theo ngay
    List<DutySchedule> findByStaffAccountIdAndWorkDate(UUID staffAccountId, LocalDate workDate);

    // kiem tra trung lich
    Optional<DutySchedule> findByWorkDateAndShiftAndStaffAccountId(LocalDate workDate, DutyShift shift, UUID staffAccountId);

    // kiem tra 1 phong chi 1 nguoi truc
    Optional<DutySchedule> findByRoomIdAndWorkDateAndShift(UUID roomId, LocalDate workDate, DutyShift shift);

    // Lịch trực của mọi phòng trong một ca (một truy vấn thay vì một truy vấn mỗi phòng)
    List<DutySchedule> findByWorkDateAndShift(LocalDate workDate, DutyShift shift);

    // Kiểm tra trùng lịch nhân viên
    boolean existsByWorkDateAndShiftAndStaffAccountIdAndIdNot(LocalDate workDate, DutyShift shift, UUID staffAccountId, UUID id);

    // Kiểm tra phòng đã có người trực chưa
    boolean existsByRoomIdAndWorkDateAndShiftAndIdNot(UUID roomId, LocalDate workDate, DutyShift shift, UUID id);

    // Kiểm tra phòng có ca trực nào trong tương lai (sau ngày chỉ định)
    boolean existsByRoomIdAndWorkDateAfter(UUID roomId, LocalDate workDate);

    /** The staff member's duty shifts on or after the given date; while there are any, their room cannot change. */
    long countByStaffAccountIdAndWorkDateGreaterThanEqual(UUID staffAccountId, LocalDate workDate);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT d.roomId FROM DutySchedule d WHERE d.roomId IS NOT NULL AND d.workDate > :workDate")
    java.util.Set<UUID> findRoomIdsWithDutyScheduleAfter(@org.springframework.data.repository.query.Param("workDate") LocalDate workDate);
}
