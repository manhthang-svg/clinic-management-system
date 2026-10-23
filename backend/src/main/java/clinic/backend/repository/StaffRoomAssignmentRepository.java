package clinic.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.StaffRoomAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffRoomAssignmentRepository extends JpaRepository<StaffRoomAssignment, UUID> {

    // tim kiem nhan vien tai 1 phong
    Optional<StaffRoomAssignment> findByStaffAccountIdAndRemovedAtIsNull(UUID staffAccountId);

    // tim kiem cho nhieu nhan vien
    List<StaffRoomAssignment> findAllByStaffAccountIdInAndRemovedAtIsNull(Collection<UUID> staffAccountIds);

    // lay toan bo danh sach phan cong chua bi go bo
    List<StaffRoomAssignment> findByRemovedAtIsNull();

    // tim kiem phong co roomId cho nhân viên nào 
    Optional<StaffRoomAssignment> findByRoomIdAndRemovedAtIsNull(UUID roomId);
}
