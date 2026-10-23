package clinic.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffAccountRepository extends JpaRepository<StaffAccount, UUID>, JpaSpecificationExecutor<StaffAccount> {

    Optional<StaffAccount> findByUsername(String username);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, UUID id);

    boolean existsByUsername(String username);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmailIgnoreCase(String email);

    Optional<StaffAccount> findByCreationSubmissionId(UUID creationSubmissionId);

    /**
     * Finds and locks an account for the duration of an authentication transaction.
     *
     * <p>The lock serializes password-attempt counter updates for the same account so concurrent
     * failures cannot be lost.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from StaffAccount account where account.username = :username")
    Optional<StaffAccount> findByUsernameForAuthentication(@Param("username") String username);

    boolean existsByRole(Role role);

    Optional<StaffAccount> findByRole(Role role);

    List<StaffAccount> findAllByPasswordHash(String passwordHash);
}
