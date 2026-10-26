
package clinic.backend.repository;

import clinic.backend.entity.Room;
import clinic.backend.enums.ActivationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    boolean existsByRoomCode(String code);

    List<Room> findAllByServiceGroupIdAndStatusOrderByRoomCodeAsc(
            UUID serviceGroupId, ActivationStatus status);
}
