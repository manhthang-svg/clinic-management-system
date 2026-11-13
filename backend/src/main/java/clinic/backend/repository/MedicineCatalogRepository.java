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

    /** Units in use, offered by the unit filter of UC-81 (I1-D46). */
    @Query("select distinct medicine.donvitinh from MedicineCatalog medicine order by medicine.donvitinh")
    List<String> findDistinctUnits();
}
