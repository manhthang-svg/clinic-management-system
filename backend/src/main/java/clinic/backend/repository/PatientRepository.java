
package clinic.backend.repository;

import clinic.backend.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByMabenhnhan(String mabenhnhan);

    Optional<Patient> findByCccdSo(String cccdSo);

    Optional<Patient> findBySodienthoai(String sodienthoai);

    boolean existsByMabenhnhan(String mabenhnhan);

    boolean existsByCccdSo(String cccdSo);
}
