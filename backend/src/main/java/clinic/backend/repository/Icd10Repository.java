package clinic.backend.repository;

import java.time.LocalDate;
import java.util.List;

import clinic.backend.entity.Icd10;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface Icd10Repository extends JpaRepository<Icd10, String> {

    /**
     * Codes that can ever be selected: active and at the most detailed level (TT 06/2026 column 26: a code with
     * more detailed codes is never used itself). Effective dates are checked per search day by the caller.
     */
    @Query("""
            select code.maicd as code, code.tenicd as name,
                   code.hieuLucTu as effectiveFrom, code.hieuLucDen as effectiveUntil
            from Icd10 code
            where code.isActive = true and code.coMaChiTietHon = false
            """)
    List<SearchableCode> findSearchable();

    /** Every code in the catalog, retired ones included, without loading entities. */
    @Query("select code.maicd from Icd10 code")
    List<String> findAllCodes();

    /** The fields the ICD-10 search needs, without loading entities. */
    interface SearchableCode {
        String getCode();

        String getName();

        LocalDate getEffectiveFrom();

        LocalDate getEffectiveUntil();
    }
}
