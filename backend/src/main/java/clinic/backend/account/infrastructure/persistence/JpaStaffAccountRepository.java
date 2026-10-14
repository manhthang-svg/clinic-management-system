package clinic.backend.account.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.account.application.port.StaffAccountRepository;
import clinic.backend.account.domain.Role;
import clinic.backend.account.domain.StaffAccount;
import clinic.backend.account.domain.Username;
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
    public boolean existsWithRole(Role role) {
        return repository.existsByRole(role);
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
                account.phoneNumber(),
                account.email(),
                account.role(),
                account.status(),
                account.passwordChangeRequired(),
                account.failedLoginAttempts(),
                account.loginBlockedUntil(),
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
                entity.getPhoneNumber(),
                entity.getEmail(),
                entity.getRole(),
                entity.getStatus(),
                entity.isPasswordChangeRequired(),
                entity.getFailedLoginAttempts(),
                entity.getLoginBlockedUntil(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion());
    }
}
