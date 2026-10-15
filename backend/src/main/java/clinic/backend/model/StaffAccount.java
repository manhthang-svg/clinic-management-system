package clinic.backend.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Gender;
import clinic.backend.enums.Role;

/**
 * Staff account aggregate (docs/use-cases/staff-account.md).
 *
 * <p>The password hash is an opaque value produced by the application's password hasher.
 */
public final class StaffAccount {

    public static final int MAX_CONSECUTIVE_FAILED_LOGINS = 5;
    public static final Duration LOGIN_BLOCK_DURATION = Duration.ofMinutes(15);

    private final UUID id;
    private final Username username;
    private String passwordHash;
    private String fullName;
    private String academicTitle;
    private LocalDate dateOfBirth;
    private Gender gender;
    private String phoneNumber;
    private String email;
    private String address;
    private Role role;
    private AccountStatus status;
    private boolean passwordChangeRequired;
    private int failedLoginAttempts;
    private Instant loginBlockedUntil;
    private final UUID createdByAccountId;
    private Instant lastLoginAt;
    private Instant passwordChangedAt;
    private String lockReason;
    private Instant lockedAt;
    private UUID lockedByAccountId;
    private final Instant createdAt;
    private Instant updatedAt;
    private final Long version;

    private StaffAccount(
            UUID id,
            Username username,
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

        this.id = Objects.requireNonNull(id, "id");
        this.username = Objects.requireNonNull(username, "username");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.fullName = requireText(fullName, "fullName");
        this.academicTitle = academicTitle;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.address = address;
        this.role = Objects.requireNonNull(role, "role");
        this.status = Objects.requireNonNull(status, "status");
        this.passwordChangeRequired = passwordChangeRequired;
        if (failedLoginAttempts < 0) {
            throw new IllegalArgumentException("failedLoginAttempts must not be negative");
        }
        this.failedLoginAttempts = failedLoginAttempts;
        this.loginBlockedUntil = loginBlockedUntil;
        this.createdByAccountId = createdByAccountId;
        this.lastLoginAt = lastLoginAt;
        this.passwordChangedAt = passwordChangedAt;
        this.lockReason = lockReason;
        this.lockedAt = lockedAt;
        this.lockedByAccountId = lockedByAccountId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
    }

    /**
     * Creates a new active account whose one-time password must be changed at first login (A01, AQ-01).
     */
    public static StaffAccount create(
            UUID id,
            Username username,
            String temporaryPasswordHash,
            String fullName,
            String academicTitle,
            String phoneNumber,
            String email,
            Role role,
            Instant now) {

        return create(
                id, username, temporaryPasswordHash, fullName, academicTitle, null, null, phoneNumber, email, null,
                role, null, now);
    }

    public static StaffAccount create(
            UUID id,
            Username username,
            String temporaryPasswordHash,
            String fullName,
            String academicTitle,
            LocalDate dateOfBirth,
            Gender gender,
            String phoneNumber,
            String email,
            String address,
            Role role,
            UUID createdByAccountId,
            Instant now) {

        return new StaffAccount(
                id, username, temporaryPasswordHash, fullName, academicTitle, dateOfBirth, gender, phoneNumber, email,
                address, role, AccountStatus.ACTIVE, true, 0, null, createdByAccountId, null, null, null, null, null,
                now, now, null);
    }

    public static StaffAccount restore(
            UUID id,
            Username username,
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

        return new StaffAccount(
                id, username, passwordHash, fullName, academicTitle, null, null, phoneNumber, email, null, role, status,
                passwordChangeRequired, failedLoginAttempts, loginBlockedUntil, null, null, null, null, null, null,
                createdAt, updatedAt, version);
    }

    public static StaffAccount restore(
            UUID id,
            Username username,
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

        return new StaffAccount(
                id, username, passwordHash, fullName, academicTitle, dateOfBirth, gender, phoneNumber, email, address,
                role, status, passwordChangeRequired, failedLoginAttempts, loginBlockedUntil, createdByAccountId,
                lastLoginAt, passwordChangedAt, lockReason, lockedAt, lockedByAccountId, createdAt, updatedAt, version);
    }

    /**
     * Password login is temporarily refused after repeated failures (AQ-04). This is not the LOCKED status.
     */
    public boolean isLoginBlocked(Instant now) {
        return loginBlockedUntil != null && now.isBefore(loginBlockedUntil);
    }

    /**
     * A locked account cannot log in or refresh a session (BR-01).
     */
    public boolean canAuthenticate() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * Records a wrong password.
     *
     * @return {@code true} when this failure started a login block
     */
    public boolean registerFailedLogin(Instant now) {
        failedLoginAttempts++;
        updatedAt = now;
        if (failedLoginAttempts >= MAX_CONSECUTIVE_FAILED_LOGINS) {
            failedLoginAttempts = 0;
            loginBlockedUntil = now.plus(LOGIN_BLOCK_DURATION);
            return true;
        }
        return false;
    }

    public void registerSuccessfulLogin(Instant now) {
        clearFailedLoginAttempts(now);
        lastLoginAt = now;
        updatedAt = now;
    }

    public void clearFailedLoginAttempts(Instant now) {
        failedLoginAttempts = 0;
        loginBlockedUntil = null;
        updatedAt = now;
    }

    /**
     * Replaces the password and clears "password change required" (UC-3). The caller has verified the
     * current password and the policy.
     */
    public void changePassword(String newPasswordHash, Instant now) {
        passwordHash = requireText(newPasswordHash, "newPasswordHash");
        passwordChangeRequired = false;
        passwordChangedAt = now;
        updatedAt = now;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public UUID id() {
        return id;
    }

    public Username username() {
        return username;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String fullName() {
        return fullName;
    }

    public String academicTitle() {
        return academicTitle;
    }

    public LocalDate dateOfBirth() {
        return dateOfBirth;
    }

    public Gender gender() {
        return gender;
    }

    public String phoneNumber() {
        return phoneNumber;
    }

    public String email() {
        return email;
    }

    public String address() {
        return address;
    }

    public Role role() {
        return role;
    }

    public AccountStatus status() {
        return status;
    }

    public boolean passwordChangeRequired() {
        return passwordChangeRequired;
    }

    public int failedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant loginBlockedUntil() {
        return loginBlockedUntil;
    }

    public UUID createdByAccountId() {
        return createdByAccountId;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public Instant passwordChangedAt() {
        return passwordChangedAt;
    }

    public String lockReason() {
        return lockReason;
    }

    public Instant lockedAt() {
        return lockedAt;
    }

    public UUID lockedByAccountId() {
        return lockedByAccountId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }
}
