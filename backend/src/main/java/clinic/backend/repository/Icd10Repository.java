package clinic.backend.repository;

import java.time.LocalDate;
import java.util.List;

import clinic.backend.entity.Icd10;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Icd10Repository extends JpaRepository<Icd10, String> {

    /**
     * Codes that may be selected on {@code day}: active, within their effective dates, and the most detailed
     * level (TT 06/2026 column 26: a code with more detailed codes is never used itself).
     */
    @Query("""
            select code from Icd10 code
            where code.isActive = true
              and code.hieuLucTu <= :day
              and (code.hieuLucDen is null or code.hieuLucDen >= :day)
              and code.coMaChiTietHon = false
              and (lower(code.maicd) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(function('unaccent', code.tenicd))
                      like lower(function('unaccent', concat('%', cast(:keyword as String), '%'))))
            order by code.maicd
            """)
    List<Icd10> searchActive(@Param("keyword") String keyword, @Param("day") LocalDate day, Pageable pageable);
}
