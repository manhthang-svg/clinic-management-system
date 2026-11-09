package clinic.backend.repository;

import java.util.List;
import java.util.UUID;

import clinic.backend.entity.ParaclinicalInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParaclinicalInstrumentRepository extends JpaRepository<ParaclinicalInstrument, UUID> {

    /** UC-67: the instruments a technician can pick in the result form of a room. */
    List<ParaclinicalInstrument> findAllByRoomIdAndActiveTrueOrderByCodeAsc(UUID roomId);
}
