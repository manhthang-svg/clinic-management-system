
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

    // UC-37: ho so trung = cung ho ten (khong phan biet hoa thuong) + cung ngay sinh
    @Query("""
            select p from Patient p
            where lower(trim(p.hoten)) = lower(trim(cast(:name as String))) and p.ngaysinh = :birthDate
            order by p.mabenhnhan
            """)
    List<Patient> findPossibleDuplicates(@Param("name") String name, @Param("birthDate") LocalDate birthDate);

    // UC-35 tim theo loai: CCCD, ho chieu khop dung; ma BN, SDT khop 1 phan
    Page<Patient> findAllByCccdSo(String cccdSo, Pageable pageable);

    Page<Patient> findAllByHochieuSoIgnoreCase(String hochieuSo, Pageable pageable);

    Page<Patient> findAllByMabenhnhanContainingIgnoreCase(String mabenhnhan, Pageable pageable);

    Page<Patient> findAllBySodienthoaiContaining(String sodienthoai, Pageable pageable);

    // tim benh nhan theo the BHYT da dung o cac lan kham truoc (khop dung)
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

    // tim theo ho ten, khong phan biet hoa thuong va dau
    @Query("""
            select p from Patient p
            where lower(function('unaccent', p.hoten))
                  like lower(function('unaccent', concat('%', cast(:name as String), '%')))
            """)
    Page<Patient> searchByName(
            @Param("name") String name,
            Pageable pageable
    );

    // tim tu do o Tong quan le tan: ma BN, ho ten, SDT khop 1 phan; CCCD phai nhap du (khop dung)
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
