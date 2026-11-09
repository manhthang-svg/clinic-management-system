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

    // lay lich theo phong
    List<DutySchedule> findByRoomIdAndWorkDateBetweenOrderByWorkDateAscShiftAsc(UUID roomId, LocalDate startDate, LocalDate endDate);

    // lay lich cua nhan vien theo ngay
    List<DutySchedule> findByStaffAccountIdAndWorkDate(UUID staffAccountId, LocalDate workDate);

    // kiem tra trung lich
    Optional<DutySchedule> findByWorkDateAndShiftAndStaffAccountId(LocalDate workDate, DutyShift shift, UUID staffAccountId);

    // kiem tra 1 phong chi 1 nguoi truc
    Optional<DutySchedule> findByRoomIdAndWorkDateAndShift(UUID roomId, LocalDate workDate, DutyShift shift);

    // Kiểm tra trùng lịch nhân viên
    boolean existsByWorkDateAndShiftAndStaffAccountIdAndIdNot(LocalDate workDate, DutyShift shift, UUID staffAccountId, UUID id);

    // Kiểm tra phòng đã có người trực chưa
    boolean existsByRoomIdAndWorkDateAndShiftAndIdNot(UUID roomId, LocalDate workDate, DutyShift shift, UUID id);
}
