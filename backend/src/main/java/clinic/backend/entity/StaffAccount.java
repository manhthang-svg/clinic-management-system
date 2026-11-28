package clinic.backend.entity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Gender;
import clinic.backend.enums.PasswordViolation;
import clinic.backend.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Staff account (Report 3).
 *
 * <p>State changes go through the behavior methods below; the entity has no setters. The password hash is an
 * opaque value produced by the application's password hasher.
 */
@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Entity
@Table(name = "staff_accounts")
public class StaffAccount {

    public static final int MAX_CONSECUTIVE_FAILED_LOGINS = 5;
    public static final Duration LOGIN_BLOCK_DURATION = Duration.ofMinutes(15);

    /** Password rules from docs/security.md: 8–64 characters and at most 72 bytes in UTF-8 (BCrypt input limit). */
    public static final int PASSWORD_MINIMUM_LENGTH = 8;
    public static final int PASSWORD_MAXIMUM_LENGTH = 64;
    public static final int PASSWORD_MAXIMUM_UTF8_BYTES = 72;

    /** Login identifier: case-insensitive, stored in lowercase, 4–30 characters of {@code a–z 0–9 . _}. */
    private static final Pattern USERNAME_FORMAT = Pattern.compile("[a-z0-9._]{4,30}");

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

    /** Required for DOCTOR and TECHNICIAN; exported as the performer code (I1-D40). */
    @Column(name = "practice_license_no", length = 255)
    private String practiceLicenseNo;

    /** Prescriber link code from the national prescription system; required to prescribe; the Admin enters it in UC-08 (BR-49, BR-65). */
    @Column(name = "ma_lienthong_bacsi", length = 50)
    private String maLienthongBacsi;

    /**
     * QĐ 808/QĐ-BYT IV, V: when the facility added the Doctor to the national prescription system; null when not
     * added or removed (UC-93, BR-59-17).
     */
    @Column(name = "lienthong_added_at")
    private Instant lienthongAddedAt;

    /** UC-08: personal identification number (12 digits) of a Doctor, kept as staff information. */
    @Column(name = "citizen_id", length = 12)
    private String citizenId;

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

    @Column(name = "creation_submission_id", updatable = false)
    private UUID creationSubmissionId;

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

    /**
     * Creates a new active account whose one-time password must be changed at first login (A01, docs/security.md).
     */
    public static StaffAccount create(
            UUID id,
            String username,
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
            String username,
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

        return create(
                id, username, temporaryPasswordHash, fullName, academicTitle, dateOfBirth, gender, phoneNumber,
                email, address, role, createdByAccountId, null, now);
    }

    public static StaffAccount create(
            UUID id,
            String username,
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
            UUID creationSubmissionId,
            Instant now) {

        return create(
                id, username, temporaryPasswordHash, fullName, academicTitle, null, dateOfBirth, gender,
                phoneNumber, email, address, role, createdByAccountId, creationSubmissionId, true, now);
    }

    public static StaffAccount create(
            UUID id,
            String username,
            String temporaryPasswordHash,
            String fullName,
            String academicTitle,
            String practiceLicenseNo,
            LocalDate dateOfBirth,
            Gender gender,
            String phoneNumber,
            String email,
            String address,
            Role role,
            UUID createdByAccountId,
            UUID creationSubmissionId,
            boolean passwordChangeRequired,
            Instant now) {

        if (username == null || !USERNAME_FORMAT.matcher(username).matches()) {
            throw new IllegalArgumentException("Username does not match the required format");
        }
        return StaffAccount.builder()
                .id(Objects.requireNonNull(id, "id"))
                .username(username)
                .passwordHash(requireText(temporaryPasswordHash, "passwordHash"))
                .fullName(requireText(fullName, "fullName"))
                .academicTitle(academicTitle)
                .practiceLicenseNo(normalizeOptional(practiceLicenseNo))
                .dateOfBirth(dateOfBirth)
                .gender(gender)
                .phoneNumber(phoneNumber)
                .email(email)
                .address(address)
                .role(Objects.requireNonNull(role, "role"))
                .status(AccountStatus.ACTIVE)
                .passwordChangeRequired(passwordChangeRequired)
                .createdByAccountId(createdByAccountId)
                .creationSubmissionId(creationSubmissionId)
                .createdAt(Objects.requireNonNull(now, "now"))
                .updatedAt(now)
                .build();
    }

    /**
     * Normalizes user input; returns empty when the input can never be a valid username.
     */
    public static Optional<String> normalizeUsername(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String normalized = input.strip().toLowerCase(Locale.ROOT);
        if (!USERNAME_FORMAT.matcher(normalized).matches()) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    /**
     * Checks a raw password against the password policy in docs/security.md.
     */
    public static Optional<PasswordViolation> checkPasswordPolicy(String rawPassword) {
        if (rawPassword == null || rawPassword.codePointCount(0, rawPassword.length()) < PASSWORD_MINIMUM_LENGTH) {
            return Optional.of(PasswordViolation.TOO_SHORT);
        }
        if (rawPassword.codePointCount(0, rawPassword.length()) > PASSWORD_MAXIMUM_LENGTH
                || exceedsPasswordByteLimit(rawPassword)) {
            return Optional.of(PasswordViolation.TOO_LONG);
        }
        return Optional.empty();
    }

    public static boolean exceedsPasswordByteLimit(String rawPassword) {
        return rawPassword.getBytes(StandardCharsets.UTF_8).length > PASSWORD_MAXIMUM_UTF8_BYTES;
    }

    /**
     * Password login is temporarily refused after repeated failures. This is not the LOCKED status.
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

    /**
     * Replaces a forgotten password with an operator-issued one-time password (docs/security.md, Admin Recovery).
     * This deliberately does not change the account's ACTIVE/LOCKED status.
     */
    public void resetPasswordForRecovery(String temporaryPasswordHash, Instant now) {
        replacePasswordAfterAdminReset(temporaryPasswordHash, true, now);
    }

    /**
     * Replaces the password with a permanent password agreed between the Admin and the account owner.
     * Unlike a generated temporary password, it does not force another password change at next login.
     */
    public void resetPasswordWithoutRequiredChange(String permanentPasswordHash, Instant now) {
        replacePasswordAfterAdminReset(permanentPasswordHash, false, now);
    }

    private void replacePasswordAfterAdminReset(
            String newPasswordHash, boolean requirePasswordChange, Instant now) {
        passwordHash = requireText(newPasswordHash, "newPasswordHash");
        passwordChangeRequired = requirePasswordChange;
        failedLoginAttempts = 0;
        loginBlockedUntil = null;
        passwordChangedAt = Objects.requireNonNull(now, "now");
        updatedAt = now;
    }

    /** Updates the two owner-editable profile fields (UC-4 / BR-04). */
    public void updateContact(String newPhoneNumber, String newEmail, Instant now) {
        phoneNumber = requireText(newPhoneNumber, "phoneNumber");
        email = normalizeOptional(newEmail);
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public void updatePersonalInformation(
            String newFullName,
            String newAcademicTitle,
            String newPracticeLicenseNo,
            LocalDate newDateOfBirth,
            Gender newGender,
            String newPhoneNumber,
            String newEmail,
            String newAddress,
            Instant now) {
        fullName = requireText(newFullName, "fullName");
        academicTitle = normalizeOptional(newAcademicTitle);
        practiceLicenseNo = normalizeOptional(newPracticeLicenseNo);
        dateOfBirth = newDateOfBirth;
        gender = newGender;
        phoneNumber = requireText(newPhoneNumber, "phoneNumber");
        email = normalizeOptional(newEmail);
        address = normalizeOptional(newAddress);
        updatedAt = Objects.requireNonNull(now, "now");
    }

    /** UC-93 (QĐ 808 IV): the facility added the Doctor to the national prescription system. */
    public void markAddedToNationalPrescriptionSystem(Instant now) {
        lienthongAddedAt = Objects.requireNonNull(now, "now");
        updatedAt = now;
    }

    /** UC-93 (QĐ 808 V): the facility removed the Doctor from the national prescription system. */
    public void markRemovedFromNationalPrescriptionSystem(Instant now) {
        lienthongAddedAt = null;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    /** BR-49, BR-65: sets or clears the prescriber link code issued by the national prescription system. */
    public void updateMaLienthongBacsi(String newMaLienthongBacsi, Instant now) {
        maLienthongBacsi = normalizeOptional(newMaLienthongBacsi);
        updatedAt = Objects.requireNonNull(now, "now");
    }

    /** UC-08: sets or clears the Doctor's personal identification number (12 digits). */
    public void updateCitizenId(String newCitizenId, Instant now) {
        citizenId = normalizeOptional(newCitizenId);
        updatedAt = Objects.requireNonNull(now, "now");
    }

    /** I1-D47: the professional information a Doctor or Technician role requires, set with the role change. */
    public void updateProfessionalInformation(String newAcademicTitle, String newPracticeLicenseNo, Instant now) {
        academicTitle = requireText(newAcademicTitle, "academicTitle").strip();
        practiceLicenseNo = requireText(newPracticeLicenseNo, "practiceLicenseNo").strip();
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public void changeRole(Role newRole, Instant now) {
        if (role == Role.ADMIN || newRole == Role.ADMIN || role == newRole) {
            throw new IllegalArgumentException("Role transition is not allowed");
        }
        role = Objects.requireNonNull(newRole, "newRole");
        updatedAt = Objects.requireNonNull(now, "now");
    }

    public void lock(String reason, UUID actorAccountId, Instant now) {
        if (role == Role.ADMIN || status != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account cannot be locked");
        }
        status = AccountStatus.LOCKED;
        lockReason = requireText(reason, "reason");
        lockedAt = Objects.requireNonNull(now, "now");
        lockedByAccountId = Objects.requireNonNull(actorAccountId, "actorAccountId");
        updatedAt = now;
    }

    /** Manual unlock deliberately preserves the independent 15-minute sign-in block (UC-10). */
    public void unlock(Instant now) {
        if (role == Role.ADMIN || status != AccountStatus.LOCKED) {
            throw new IllegalStateException("Account cannot be unlocked");
        }
        status = AccountStatus.ACTIVE;
        lockReason = null;
        lockedAt = null;
        lockedByAccountId = null;
        updatedAt = Objects.requireNonNull(now, "now");
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String toString() {
        return "StaffAccount[id=" + id + ", username=" + username + "]";
    }
}
