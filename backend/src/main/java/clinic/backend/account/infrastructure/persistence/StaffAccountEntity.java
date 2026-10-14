package clinic.backend.account.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.account.domain.AccountStatus;
import clinic.backend.account.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "staff_accounts")
class StaffAccountEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "username", nullable = false, updatable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "academic_title")
    private String academicTitle;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "login_blocked_until")
    private Instant loginBlockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // A null version marks a new entity for Spring Data.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected StaffAccountEntity() {
    }

    StaffAccountEntity(
            UUID id,
            String username,
            String passwordHash,
            String fullName,
            String academicTitle,
            String phoneNumber,
            String email,
            Role role,
            AccountStatus status,
            boolean passwordChangeRequired,
            int failedLoginAttempts,
            Instant loginBlockedUntil,
            Instant createdAt,
            Instant updatedAt,
            Long version) {

        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.academicTitle = academicTitle;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.role = role;
        this.status = status;
        this.passwordChangeRequired = passwordChangeRequired;
        this.failedLoginAttempts = failedLoginAttempts;
        this.loginBlockedUntil = loginBlockedUntil;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    UUID getId() {
        return id;
    }

    String getUsername() {
        return username;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getFullName() {
        return fullName;
    }

    String getAcademicTitle() {
        return academicTitle;
    }

    String getPhoneNumber() {
        return phoneNumber;
    }

    String getEmail() {
        return email;
    }

    Role getRole() {
        return role;
    }

    AccountStatus getStatus() {
        return status;
    }

    boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    Instant getLoginBlockedUntil() {
        return loginBlockedUntil;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    Long getVersion() {
        return version;
    }
}
