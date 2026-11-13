package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.SigningRequest;
import clinic.backend.enums.RecordSignatureDocumentType;
import clinic.backend.enums.SigningRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SigningRequestRepository extends JpaRepository<SigningRequest, UUID> {

    /** At most one pending request per document (uq_signing_requests_pending). */
    Optional<SigningRequest> findByDocumentTypeAndDocumentIdAndStatus(
            RecordSignatureDocumentType documentType, UUID documentId, SigningRequestStatus status);

    /** Serializes completing a request: the client asks every few seconds and may ask from two tabs. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from SigningRequest request where request.id = :id")
    Optional<SigningRequest> findByIdForUpdate(@Param("id") UUID id);
}
