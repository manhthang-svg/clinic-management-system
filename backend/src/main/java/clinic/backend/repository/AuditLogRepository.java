package clinic.backend.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import clinic.backend.entity.AuditLog;
import clinic.backend.enums.AuditLogAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    Page<AuditLog> findByEntityTypeAndEntityIdOrderByOccurredAtDescIdDesc(
            String entityType, UUID entityId, Pageable pageable);

    boolean existsByActionAndEntityTypeAndEntityId(
            AuditLogAction action, String entityType, UUID entityId);

    /** UC-78: the object types that already have entries, for the filter list. */
    @Query("SELECT DISTINCT a.entityType FROM AuditLog a ORDER BY a.entityType")
    List<String> findDistinctEntityTypes();
}
