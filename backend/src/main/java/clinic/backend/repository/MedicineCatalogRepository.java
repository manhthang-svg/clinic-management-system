package clinic.backend.repository;

import java.util.List;
import java.util.UUID;

import clinic.backend.entity.MedicineCatalog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface MedicineCatalogRepository
        extends JpaRepository<MedicineCatalog, UUID>, JpaSpecificationExecutor<MedicineCatalog> {

    boolean existsByMathuocvattuBv(String code);

    boolean existsBySoDangKy(String soDangKy);

    boolean existsBySoDangKyAndIdNot(String soDangKy, UUID id);

    /**
     * UC-59: medicines a Doctor can prescribe (active, ordinary group, BR-33, BR-52) whose name, active ingredient
     * or internal code contains the keyword; {@code keyword} is lower-case and wrapped in {@code %}.
     */
    @Query("""
            select medicine from MedicineCatalog medicine
            where medicine.status = clinic.backend.enums.ActivationStatus.ACTIVE
              and medicine.nhomKiemSoat = clinic.backend.enums.MedicineControlGroup.THUONG
              and (lower(medicine.tenthuocvattu) like :keyword
                   or lower(medicine.hoatchat) like :keyword
                   or lower(medicine.mathuocvattuBv) like :keyword)
            order by medicine.tenthuocvattu
            """)
    List<MedicineCatalog> searchPrescribable(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            org.springframework.data.domain.Pageable page);

    /** Units in use, offered by the unit filter of UC-81 (I1-D46). */
    @Query("select distinct medicine.donvitinh from MedicineCatalog medicine order by medicine.donvitinh")
    List<String> findDistinctUnits();
}
