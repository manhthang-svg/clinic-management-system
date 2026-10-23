package clinic.backend.repository;

import clinic.backend.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    boolean existsByRoomCode(String roomCode);

    Optional<Room> findByRoomCode(String roomCode);

    List<Room> findByDepartmentId(UUID departmentId);
}
