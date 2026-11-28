package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.request.NationalPrescriberRequest;
import clinic.backend.dto.response.AccountDetailResponse;
import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.Role;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AccountAdministrationService;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.NationalPrescriberService;
import clinic.backend.service.NationalPrescriptionSystem;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UC-93 (BR-59-17, QĐ 808/QĐ-BYT II.2, III, IV, V): the Admin adds a Doctor to the facility on the national
 * prescription system, or removes one, so the Doctor can sign in to send prescriptions. The facility's password is
 * asked only when no facility token is kept and is never stored. The clinic records when the Doctor was added
 * ({@code staff_accounts.lienthong_added_at}) because QĐ 808 has no API to ask. Nothing is written before the
 * national system accepts.
 */
@Service
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN') and !hasAuthority('PASSWORD_CHANGE_REQUIRED')")
public class NationalPrescriberServiceImpl implements NationalPrescriberService {

    private static final String FIELD = "lienthongAddedAt";

    private final StaffAccountRepository accountRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final NationalPrescriptionSystem nationalSystem;
    private final NationalPrescriptionSessions sessions;
    private final AccountAdministrationService accounts;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional
    public AccountDetailResponse addToFacility(
            StaffPrincipal principal, UUID accountId, NationalPrescriberRequest request) {
        return change(principal, accountId, request, true);
    }

    @Override
    @Transactional
    public AccountDetailResponse removeFromFacility(
            StaffPrincipal principal, UUID accountId, NationalPrescriberRequest request) {
        return change(principal, accountId, request, false);
    }

    private AccountDetailResponse change(
            StaffPrincipal principal, UUID accountId, NationalPrescriberRequest request, boolean add) {
        char[] password = request == null ? null : request.facilityPassword();
        try {
            StaffAccount doctor = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
            if (doctor.getRole() != Role.DOCTOR) {
                throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("role", "NOT_APPLICABLE"));
            }
            if (request == null || request.version() == null || !request.version().equals(doctor.getVersion())) {
                throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
            }
            boolean added = doctor.getLienthongAddedAt() != null;
            if (added == add) {
                return accounts.getAccount(principal, accountId); // already done: nothing to send
            }
            String prescriberLinkCode = text(doctor.getMaLienthongBacsi());
            if (prescriberLinkCode == null) {
                throw new AppException(ErrorCode.PRESCRIBER_LINK_CODE_REQUIRED);
            }
            String facilityLinkCode = clinicFacilityRepository.findCurrent()
                    .map(ClinicFacility::getMaLienthongDonthuoc)
                    .map(NationalPrescriberServiceImpl::text)
                    .orElseThrow(() -> new AppException(ErrorCode.FACILITY_LINK_CODE_REQUIRED));

            String token = sessions.facilityToken(facilityLinkCode, password);
            NationalPrescriptionSystem.Answer answer = add
                    ? nationalSystem.addPrescriber(token, prescriberLinkCode)
                    : nationalSystem.removePrescriber(token, prescriberLinkCode);
            if (answer.tokenInvalid()) {
                sessions.forgetFacility(facilityLinkCode);
                throw new AppException(ErrorCode.NATIONAL_PRESCRIPTION_SIGN_IN_REQUIRED);
            }
            if (!answer.accepted()) {
                throw new AppException(ErrorCode.NATIONAL_PRESCRIPTION_REQUEST_REJECTED,
                        Map.of("errors", String.join("\n", answer.errors())));
            }

            Instant now = clock.instant();
            Instant before = doctor.getLienthongAddedAt();
            if (add) {
                doctor.markAddedToNationalPrescriptionSystem(now);
            } else {
                doctor.markRemovedFromNationalPrescriptionSystem(now);
                sessions.forgetPrescriber(facilityLinkCode, prescriberLinkCode);
            }
            accountRepository.saveAndFlush(doctor);
            Map<String, Object> oldValues = new LinkedHashMap<>();
            oldValues.put(FIELD, before == null ? null : before.toString());
            Map<String, Object> newValues = new LinkedHashMap<>();
            newValues.put(FIELD, add ? now.toString() : null);
            auditLogService.recordAccountChange(
                    principal.accountId(), doctor.getId(), AuditLogAction.UPDATE, oldValues, newValues, now);
            return accounts.getAccount(principal, accountId);
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
