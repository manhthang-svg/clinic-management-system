package clinic.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.StaffAccountEntity;
import clinic.backend.enums.Role;
import clinic.backend.model.StaffAccount;
import clinic.backend.model.Username;
import clinic.backend.repository.StaffAccountRepository;
import org.springframework.stereotype.Repository;

@Repository
class JpaStaffAccountRepository implements StaffAccountRepository {

    private final StaffAccountJpaRepository repository;

    JpaStaffAccountRepository(StaffAccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StaffAccount> findById(UUID id) {
        return repository.findById(id).map(JpaStaffAccountRepository::toDomain);
    }

    @Override
    public Optional<StaffAccount> findByUsername(Username username) {
        return repository.findByUsername(username.value()).map(JpaStaffAccountRepository::toDomain);
    }

    @Override
    public Optional<StaffAccount> findByUsernameForAuthentication(Username username) {
        return repository.findByUsernameForAuthentication(username.value())
                .map(JpaStaffAccountRepository::toDomain);
    }

    @Override
    public boolean existsWithRole(Role role) {
        return repository.existsByRole(role);
    }

    @Override
    public List<StaffAccount> findAllByPasswordHash(String passwordHash) {
        return repository.findAllByPasswordHash(passwordHash).stream()
                .map(JpaStaffAccountRepository::toDomain)
                .toList();
    }

    @Override
    public StaffAccount save(StaffAccount account) {
        return toDomain(repository.saveAndFlush(toEntity(account)));
    }

    private static StaffAccountEntity toEntity(StaffAccount account) {
        return new StaffAccountEntity(
                account.id(),
                account.username().value(),
                account.passwordHash(),
                account.fullName(),
                account.academicTitle(),
                account.dateOfBirth(),
                account.gender(),
                account.phoneNumber(),
                account.email(),
                account.address(),
                account.role(),
                account.status(),
                account.passwordChangeRequired(),
                account.failedLoginAttempts(),
                account.loginBlockedUntil(),
                account.createdByAccountId(),
                account.lastLoginAt(),
                account.passwordChangedAt(),
                account.lockReason(),
                account.lockedAt(),
                account.lockedByAccountId(),
                account.createdAt(),
                account.updatedAt(),
                account.version());
    }

    private static StaffAccount toDomain(StaffAccountEntity entity) {
        return StaffAccount.restore(
                entity.getId(),
                new Username(entity.getUsername()),
                entity.getPasswordHash(),
                entity.getFullName(),
                entity.getAcademicTitle(),
                entity.getDateOfBirth(),
                entity.getGender(),
                entity.getPhoneNumber(),
                entity.getEmail(),
                entity.getAddress(),
                entity.getRole(),
                entity.getStatus(),
                entity.isPasswordChangeRequired(),
                entity.getFailedLoginAttempts(),
                entity.getLoginBlockedUntil(),
                entity.getCreatedByAccountId(),
                entity.getLastLoginAt(),
                entity.getPasswordChangedAt(),
                entity.getLockReason(),
                entity.getLockedAt(),
                entity.getLockedByAccountId(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }
}
