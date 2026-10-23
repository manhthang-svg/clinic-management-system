package clinic.backend.repository;

import java.util.UUID;
import java.util.Collection;
import clinic.backend.enums.SecurityAuditEventType;
import org.springframework.data.domain.Pageable;
import java.util.List;

import clinic.backend.entity.SecurityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SecurityAuditEventRepository extends JpaRepository<SecurityAuditEvent, UUID> {
    List<SecurityAuditEvent> findByTargetAccountIdAndEventTypeInOrderByOccurredAtDesc(
            UUID targetAccountId, Collection<SecurityAuditEventType> eventTypes, Pageable pageable);
}
