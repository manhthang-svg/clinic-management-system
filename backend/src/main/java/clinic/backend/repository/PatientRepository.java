
package clinic.backend.repository;

import clinic.backend.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByMabenhnhan(String mabenhnhan);

    Optional<Patient> findByCccdSo(String cccdSo);

    Optional<Patient> findBySodienthoai(String sodienthoai);

    boolean existsByMabenhnhan(String mabenhnhan);

    boolean existsByCccdSo(String cccdSo);

    boolean existsByCccdSoAndIdNot(String cccdSo, UUID id);

    Optional<Patient> findFirstByHochieuSoIgnoreCase(String hochieuSo);

    /** UC-37 step 7: possible duplicates have the same full name (ignoring case) and date of birth. */
    @Query("""
            select p from Patient p
            where lower(trim(p.hoten)) = lower(trim(cast(:name as String))) and p.ngaysinh = :birthDate
            order by p.mabenhnhan
            """)
    List<Patient> findPossibleDuplicates(@Param("name") String name, @Param("birthDate") LocalDate birthDate);

    // UC-35 typed searches. Numbers read from a document match exactly; what a patient says from memory
    // (patient code, phone) matches any part.
    Page<Patient> findAllByCccdSo(String cccdSo, Pageable pageable);

    Page<Patient> findAllByHochieuSoIgnoreCase(String hochieuSo, Pageable pageable);

    Page<Patient> findAllByMabenhnhanContainingIgnoreCase(String mabenhnhan, Pageable pageable);

    Page<Patient> findAllBySodienthoaiContaining(String sodienthoai, Pageable pageable);

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

    /**
     * Free search of the reception dashboard, without a search type. Patient code, name and phone match a part of
     * the keyword (people do not remember them exactly); the citizen ID is read from the card, so only the full
     * number matches.
     */
    @Query("""
            select p from Patient p
            where lower(p.mabenhnhan) like lower(concat('%', cast(:keyword as String), '%'))
               or lower(function('unaccent', p.hoten))
                  like lower(function('unaccent', concat('%', cast(:keyword as String), '%')))
               or p.cccdSo = cast(:keyword as String)
               or lower(coalesce(p.sodienthoai, '')) like lower(concat('%', cast(:keyword as String), '%'))
            """)
    Page<Patient> search(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
