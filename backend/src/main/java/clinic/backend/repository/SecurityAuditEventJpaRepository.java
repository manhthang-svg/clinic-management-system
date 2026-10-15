package clinic.backend.repository;

import java.util.UUID;

import clinic.backend.entity.SecurityAuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface SecurityAuditEventJpaRepository extends JpaRepository<SecurityAuditEventEntity, UUID> {
}
