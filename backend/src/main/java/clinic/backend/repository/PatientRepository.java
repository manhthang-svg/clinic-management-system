
package clinic.backend.repository;

import clinic.backend.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByMabenhnhan(String mabenhnhan);

    Optional<Patient> findByCccdSo(String cccdSo);

    Optional<Patient> findBySodienthoai(String sodienthoai);

    boolean existsByMabenhnhan(String mabenhnhan);

    boolean existsByCccdSo(String cccdSo);

    boolean existsByCccdSoAndIdNot(String cccdSo, UUID id);

    @Query("""
            select p from Patient p
            where lower(p.mabenhnhan) like lower(concat('%', :keyword, '%'))
               or lower(p.hoten) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(p.cccdSo, '')) like lower(concat('%', :keyword, '%'))
               or lower(coalesce(p.sodienthoai, '')) like lower(concat('%', :keyword, '%'))
            """)
    Page<Patient> search(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
