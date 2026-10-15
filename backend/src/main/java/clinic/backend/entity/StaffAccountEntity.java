package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Gender;
import clinic.backend.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "staff_accounts")
public class StaffAccountEntity {

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

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "email")
    private String email;

    @Column(name = "address")
    private String address;

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

    @Column(name = "created_by_account_id", updatable = false)
    private UUID createdByAccountId;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "lock_reason")
    private String lockReason;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by_account_id")
    private UUID lockedByAccountId;

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

    public StaffAccountEntity(
            UUID id,
            String username,
            String passwordHash,
            String fullName,
            String academicTitle,
            LocalDate dateOfBirth,
            Gender gender,
            String phoneNumber,
            String email,
            String address,
            Role role,
            AccountStatus status,
            boolean passwordChangeRequired,
            int failedLoginAttempts,
            Instant loginBlockedUntil,
            UUID createdByAccountId,
            Instant lastLoginAt,
            Instant passwordChangedAt,
            String lockReason,
            Instant lockedAt,
            UUID lockedByAccountId,
            Instant createdAt,
            Instant updatedAt,
            Long version) {

        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.academicTitle = academicTitle;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.address = address;
        this.role = role;
        this.status = status;
        this.passwordChangeRequired = passwordChangeRequired;
        this.failedLoginAttempts = failedLoginAttempts;
        this.loginBlockedUntil = loginBlockedUntil;
        this.createdByAccountId = createdByAccountId;
        this.lastLoginAt = lastLoginAt;
        this.passwordChangedAt = passwordChangedAt;
        this.lockReason = lockReason;
        this.lockedAt = lockedAt;
        this.lockedByAccountId = lockedByAccountId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAcademicTitle() {
        return academicTitle;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public Role getRole() {
        return role;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public boolean isPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLoginBlockedUntil() {
        return loginBlockedUntil;
    }

    public UUID getCreatedByAccountId() {
        return createdByAccountId;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public String getLockReason() {
        return lockReason;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public UUID getLockedByAccountId() {
        return lockedByAccountId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
