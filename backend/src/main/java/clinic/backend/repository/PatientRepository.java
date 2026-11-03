
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

    // UC-35 typed searches.
    Page<Patient> findAllByCccdSo(String cccdSo, Pageable pageable);

    Page<Patient> findAllByMabenhnhanIgnoreCase(String mabenhnhan, Pageable pageable);

    Page<Patient> findAllBySodienthoai(String sodienthoai, Pageable pageable);

    Page<Patient> findAllByHochieuSoIgnoreCase(String hochieuSo, Pageable pageable);

    /** Patients whose previous visits used the insurance card. */
    @Query("""
            select p from Patient p
            where exists (
                select v.id from Visit v
                where v.patientId = p.id and upper(v.mabhyt) = upper(cast(:card as String))
            )
            """)
    Page<Patient> searchByInsuranceCard(
            @Param("card") String card,
            Pageable pageable
    );

    /** Name search ignoring case and Vietnamese accents (unaccent extension, V11). */
    @Query("""
            select p from Patient p
            where lower(function('unaccent', p.hoten))
                  like lower(function('unaccent', concat('%', cast(:name as String), '%')))
            """)
    Page<Patient> searchByName(
            @Param("name") String name,
            Pageable pageable
    );

    /** Free search of the reception dashboard, without a search type. */
    @Query("""
            select p from Patient p
            where lower(p.mabenhnhan) like lower(concat('%', cast(:keyword as String), '%'))
               or lower(function('unaccent', p.hoten))
                  like lower(function('unaccent', concat('%', cast(:keyword as String), '%')))
               or lower(coalesce(p.cccdSo, '')) like lower(concat('%', cast(:keyword as String), '%'))
               or lower(coalesce(p.sodienthoai, '')) like lower(concat('%', cast(:keyword as String), '%'))
            """)
    Page<Patient> search(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
