package clinic.backend.account.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SecurityAuditEventJpaRepository extends JpaRepository<SecurityAuditEventEntity, UUID> {
}
