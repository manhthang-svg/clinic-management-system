package clinic.backend.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffAccountRepository extends JpaRepository<StaffAccount, UUID>, JpaSpecificationExecutor<StaffAccount> {

    @Query(value = """
            select account.id as id,
                   account.username as username,
                   account.fullName as fullName,
                   account.phoneNumber as phoneNumber,
                   account.email as email,
                   account.role as role,
                   account.status as status,
                   room.id as roomId,
                   room.roomCode as roomCode,
                   room.roomName as roomName,
                   department.departmentName as departmentName,
                   account.lastLoginAt as lastLoginAt,
                   account.passwordChangeRequired as passwordChangeRequired,
                   account.version as version,
                   case when exists (
                       select duty.id from DutySchedule duty
                       where duty.staffAccountId = account.id and duty.workDate >= :today
                   ) then true else false end as hasUpcomingDutySchedule
            from StaffAccount account
            left join StaffRoomAssignment assignment
                   on assignment.staffAccountId = account.id and assignment.removedAt is null
            left join Room room on room.id = assignment.roomId
            left join Department department on department.id = room.departmentId
            where (:searchPattern = '' or
                   lower(function('unaccent',
                       concat(concat(concat(account.username, ' '), account.fullName),
                           concat(concat(' ', coalesce(account.phoneNumber, '')),
                               concat(' ', coalesce(account.email, ''))))))
                   like lower(function('unaccent', :searchPattern)))
              and (:role is null or account.role = :role)
              and (:status is null or account.status = :status)
              and (:departmentId is null or department.id = :departmentId)
            """,
            countQuery = """
            select count(account.id)
            from StaffAccount account
            where (:searchPattern = '' or
                   lower(function('unaccent',
                       concat(concat(concat(account.username, ' '), account.fullName),
                           concat(concat(' ', coalesce(account.phoneNumber, '')),
                               concat(' ', coalesce(account.email, ''))))))
                   like lower(function('unaccent', :searchPattern)))
              and (:role is null or account.role = :role)
              and (:status is null or account.status = :status)
              and (:departmentId is null or exists (
                  select assignment.id
                  from StaffRoomAssignment assignment, Room room
                  where assignment.staffAccountId = account.id
                    and assignment.removedAt is null
                    and room.id = assignment.roomId
                    and room.departmentId = :departmentId
              ))
            """)
    Page<AccountSummaryView> searchSummaries(
            @Param("searchPattern") String searchPattern,
            @Param("role") Role role,
            @Param("departmentId") UUID departmentId,
            @Param("status") AccountStatus status,
            @Param("today") LocalDate today,
            Pageable pageable);

    Optional<StaffAccount> findByUsername(String username);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, UUID id);

    boolean existsByUsername(String username);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPracticeLicenseNo(String practiceLicenseNo);

    boolean existsByPracticeLicenseNoAndIdNot(String practiceLicenseNo, UUID id);

    boolean existsByMaLienthongBacsiAndIdNot(String maLienthongBacsi, UUID id);

    boolean existsByCitizenIdAndIdNot(String citizenId, UUID id);

    /** BR-59-16: the account whose personal identification number a signing certificate names. */
    Optional<StaffAccount> findByCitizenId(String citizenId);

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

    interface AccountSummaryView {
        UUID getId();

        String getUsername();

        String getFullName();

        String getPhoneNumber();

        String getEmail();

        Role getRole();

        AccountStatus getStatus();

        UUID getRoomId();

        String getRoomCode();

        String getRoomName();

        String getDepartmentName();

        Instant getLastLoginAt();

        boolean getPasswordChangeRequired();

        Long getVersion();

        /** The account has a duty shift today or later, so its room cannot be changed. */
        boolean getHasUpcomingDutySchedule();
    }
}
