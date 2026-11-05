package clinic.backend.repository;

import java.util.List;

import clinic.backend.entity.Icd10;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface Icd10Repository extends JpaRepository<Icd10, String> {

    @Query("""
            select code from Icd10 code
            where code.isActive = true
              and (lower(code.maicd) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(function('unaccent', code.tenicd))
                      like lower(function('unaccent', concat('%', cast(:keyword as String), '%'))))
            order by code.maicd
            """)
    List<Icd10> searchActive(@Param("keyword") String keyword, Pageable pageable);
}
