package clinic.backend.service.impl;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.request.UpdatePatientRequest;
import clinic.backend.entity.Patient;
import clinic.backend.repository.PatientRepository;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PatientServiceImplTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UuidV7Generator ids;

    private PatientServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new PatientServiceImpl(patientRepository, ids);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsPatientWithNormalizedOptionalFieldsAndAuthenticatedCreator() {
        authenticateAs(ACCOUNT_ID);
        when(ids.next()).thenReturn(PATIENT_ID);
        when(patientRepository.existsByCccdSo("012345678901")).thenReturn(false);
        when(patientRepository.save(any(Patient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createPatient(createRequest(
                " Nguyễn Văn A ",
                " 012345678901 ",
                "  Nghề nghiệp  ",
                "  0901234567  "));

        assertThat(response.id()).isEqualTo(PATIENT_ID);
        assertThat(response.hoten()).isEqualTo(" Nguyễn Văn A ");
        assertThat(response.cccdSo()).isEqualTo("012345678901");
        assertThat(response.nghenghiep()).isEqualTo("Nghề nghiệp");
        assertThat(response.sodienthoai()).isEqualTo("0901234567");
        assertThat(response.email()).isEqualTo("patient@example.com");
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    void rejectsDuplicateCitizenIdBeforeCreatingPatient() {
        authenticateAs(ACCOUNT_ID);
        when(patientRepository.existsByCccdSo("012345678901")).thenReturn(true);

        assertThatThrownBy(() -> service.createPatient(createRequest(
                "Nguyễn Văn A", "012345678901", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CCCD đã tồn tại");

        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void rejectsStalePatientVersionBeforeUpdating() {
        Patient patient = mock(Patient.class);
        when(patient.getVersion()).thenReturn(1L);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.updatePatient(
                PATIENT_ID,
                updateRequest(patient.getVersion() + 1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã được cập nhật bởi người khác");

        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void searchesWithTrimmedKeywordAndMapsResults() {
        Patient patient = patient();
        when(patientRepository.search(
                org.mockito.ArgumentMatchers.eq("Nguyen"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(patient)));

        var response = service.listPatients(
                "  Nguyen  ",
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(response).hasSize(1);
        assertThat(response.getContent().getFirst().id()).isEqualTo(PATIENT_ID);
        verify(patientRepository).search(
                org.mockito.ArgumentMatchers.eq("Nguyen"),
                org.mockito.ArgumentMatchers.any());
    }

    private void authenticateAs(UUID accountId) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(accountId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private Patient patient() {
        return new Patient(
                PATIENT_ID,
                java.time.Instant.parse("2026-10-28T01:00:00Z"),
                "BN1234567",
                "012345678901",
                null,
                "Nguyen Van A",
                LocalDate.of(1990, 1, 1),
                (short) 1,
                null,
                null,
                "Kinh",
                "01",
                null,
                null,
                "Ha Noi",
                null,
                null,
                null,
                null,
                null,
                "0901234567",
                "patient@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                ACCOUNT_ID);
    }

    private CreatePatientRequest createRequest(
            String name,
            String citizenId,
            String occupation,
            String phone) {
        return new CreatePatientRequest(
                name,
                LocalDate.of(1990, 1, 1),
                (short) 1,
                citizenId,
                null,
                occupation,
                null,
                "Kinh",
                "01",
                null,
                null,
                "Ha Noi",
                null,
                null,
                null,
                null,
                null,
                phone,
                "patient@example.com",
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private UpdatePatientRequest updateRequest(Long version) {
        return new UpdatePatientRequest(
                "Nguyen Van B",
                LocalDate.of(1990, 1, 1),
                (short) 1,
                "012345678901",
                null,
                null,
                null,
                "Kinh",
                "01",
                null,
                null,
                "Ha Noi",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                version);
    }
}
