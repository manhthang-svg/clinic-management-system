package clinic.backend.repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import clinic.backend.entity.StaffRoomAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffRoomAssignmentRepository extends JpaRepository<StaffRoomAssignment, UUID> {

    Optional<StaffRoomAssignment> findByStaffAccountIdAndRemovedAtIsNull(UUID staffAccountId);

    List<StaffRoomAssignment> findAllByStaffAccountIdInAndRemovedAtIsNull(Collection<UUID> staffAccountIds);
}
