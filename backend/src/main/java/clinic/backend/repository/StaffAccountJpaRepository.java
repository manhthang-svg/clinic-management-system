package clinic.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.StaffAccountEntity;
import clinic.backend.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface StaffAccountJpaRepository extends JpaRepository<StaffAccountEntity, UUID> {

    Optional<StaffAccountEntity> findByUsername(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from StaffAccountEntity account where account.username = :username")
    Optional<StaffAccountEntity> findByUsernameForAuthentication(@Param("username") String username);

    boolean existsByRole(Role role);

    List<StaffAccountEntity> findAllByPasswordHash(String passwordHash);
}
