package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.RecordSignature;
import clinic.backend.enums.RecordSignatureDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordSignatureRepository extends JpaRepository<RecordSignature, UUID> {

    Optional<RecordSignature> findBySigningRequestId(UUID signingRequestId);

    /** The latest signature of a document (the one sent with its latest submission). */
    Optional<RecordSignature> findFirstByDocumentTypeAndDocumentIdOrderByCreatedAtDesc(
            RecordSignatureDocumentType documentType, UUID documentId);
}
