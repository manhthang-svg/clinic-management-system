package clinic.backend.repository;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import clinic.backend.entity.AuditLog;
import clinic.backend.enums.AuditLogAction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(
            String entityType, UUID entityId, Pageable pageable);

    boolean existsByActionAndEntityTypeAndEntityId(
            AuditLogAction action, String entityType, UUID entityId);
}
