package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.IssueSimulatedCertificateRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.Role;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.StaffSigningCertificateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Dev profile CA console: the subscriber is identified by the personal identification number (BR-59-16). */
@ExtendWith(MockitoExtension.class)
class SigningSimulatorConsoleServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-15T03:00:00Z");
    private static final String CITIZEN_ID = "079090000001";

    @Mock private SimulatedSigningAuthority authority;
    @Mock private DigitalSignatureVerifier verifier;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private StaffSigningCertificateRepository certificateRepository;
    private SigningSimulatorConsoleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SigningSimulatorConsoleServiceImpl(authority, verifier, staffAccountRepository,
                certificateRepository, null, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void refusesAnIdentificationNumberThatIsNotTwelveDigits() {
        assertThatThrownBy(() -> service.issue(new IssueSimulatedCertificateRequest("doctor01", "123456")))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(error.getDetails()).containsEntry("citizenId", "TWELVE_DIGITS");
                });
        verify(staffAccountRepository, never()).findByCitizenId(any());
    }

    @Test
    void refusesANumberThatNoAccountHolds() {
        when(staffAccountRepository.findByCitizenId(CITIZEN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(new IssueSimulatedCertificateRequest(" " + CITIZEN_ID + " ", "123456")))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND));
        verifyNoInteractions(authority);
    }

    @Test
    void issuesOnlyToADoctor() {
        StaffAccount receptionist = StaffAccount.create(UUID.randomUUID(), "letan01", "hash", "Lễ tân Bình",
                null, null, null, null, null, null, Role.RECEPTIONIST, null, NOW);
        when(staffAccountRepository.findByCitizenId(CITIZEN_ID)).thenReturn(Optional.of(receptionist));

        assertThatThrownBy(() -> service.issue(new IssueSimulatedCertificateRequest(CITIZEN_ID, "123456")))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getDetails()).containsEntry("citizenId", "NOT_A_DOCTOR"));
        verifyNoInteractions(authority);
    }

    @Test
    void masksTheIdentificationNumberAsTheAccountScreenDoes() {
        assertThat(SigningSimulatorConsoleServiceImpl.mask(CITIZEN_ID)).isEqualTo("0790•••••001");
        assertThat(SigningSimulatorConsoleServiceImpl.mask(null)).isNull();
    }
}
