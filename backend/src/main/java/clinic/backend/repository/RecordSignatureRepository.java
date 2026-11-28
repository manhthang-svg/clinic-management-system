package clinic.backend.repository;

import java.util.UUID;

import clinic.backend.entity.RecordSignature;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordSignatureRepository extends JpaRepository<RecordSignature, UUID> {
}
