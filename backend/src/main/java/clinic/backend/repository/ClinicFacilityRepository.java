package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.ClinicFacility;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** The table holds at most one row (uq_clinic_facility_single_row). */
public interface ClinicFacilityRepository extends JpaRepository<ClinicFacility, UUID> {

    @Query("select f from ClinicFacility f")
    Optional<ClinicFacility> findCurrent();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ClinicFacility f")
    Optional<ClinicFacility> findCurrentForUpdate();
}
