package clinic.backend.repository;

import java.util.UUID;

import clinic.backend.entity.MedicineCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicineCatalogRepository extends JpaRepository<MedicineCatalog, UUID> {
}
