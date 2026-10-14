package clinic.backend.account.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.account.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

interface StaffAccountJpaRepository extends JpaRepository<StaffAccountEntity, UUID> {

    Optional<StaffAccountEntity> findByUsername(String username);

    boolean existsByRole(Role role);
}
