package clinic.backend.repository;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.StaffSigningCertificate;
import clinic.backend.enums.SigningCertificateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffSigningCertificateRepository extends JpaRepository<StaffSigningCertificate, UUID> {

    /** At most one active certificate per account (uq_staff_signing_certificates_active). */
    Optional<StaffSigningCertificate> findByStaffAccountIdAndStatus(UUID staffAccountId, SigningCertificateStatus status);

    boolean existsByIssuerDnAndSerialNumber(String issuerDn, String serialNumber);
}
