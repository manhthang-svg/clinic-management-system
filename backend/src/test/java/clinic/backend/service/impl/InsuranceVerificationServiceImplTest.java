package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.InsuranceVerificationRequest;
import clinic.backend.entity.Patient;
import clinic.backend.repository.PatientRepository;
import clinic.backend.service.HealthInsurancePortal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsuranceVerificationServiceImplTest {
    private static final UUID PATIENT_ID = UUID.randomUUID();
    private static final String IDENTITY_NUMBER = "079218000104";

    @Mock
    private HealthInsurancePortal portal;
    @Mock
    private PatientRepository patientRepository;

    private InsuranceVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InsuranceVerificationServiceImpl(
                portal, patientRepository,
                Clock.fixed(Instant.parse("2026-11-01T06:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void verifiesTenDigitSocialInsuranceNumberAndUsesPortalCoverage() {
        Patient patient = patient("  Nguyễn   Hoài Băng Tâm ", LocalDate.of(2018, 7, 14));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(portal.lookup("0000000104")).thenReturn(Optional.of(result(
                "0000000104", "HS4560000000104", "Nguyễn Hoài Băng Tâm", "20180714", (short) 0, (short) 80)));

        var response = service.verify(new InsuranceVerificationRequest(PATIENT_ID, "0000000104"));

        assertThat(response.status()).isEqualTo("VALID");
        assertThat(response.socialInsuranceNumber()).isEqualTo("0000000104");
        assertThat(response.coverageRate()).isEqualTo((short) 80);
        assertThat(response.verificationMethod()).isEqualTo("IDENTITY");
        assertThat(response.manualReviewRequired()).isFalse();
    }

    @Test
    void extractsLastTenDigitsFromLegacyCard() {
        assertThat(InsuranceVerificationServiceImpl.socialInsuranceNumberOf("HS4560000000104"))
                .isEqualTo("0000000104");
    }

    @Test
    void returnsPortalDataWhenPatientDoesNotMatch() {
        Patient patient = mock(Patient.class);
        when(patient.getCccdSo()).thenReturn("079299999999");
        when(patient.getHoten()).thenReturn("Người Khác");
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(portal.lookup("0000000104")).thenReturn(Optional.of(result(
                "0000000104", "HS4560000000104", "Nguyễn Hoài Băng Tâm", "20180714", (short) 0, (short) 80)));

        var response = service.verify(new InsuranceVerificationRequest(PATIENT_ID, "HS4560000000104"));

        assertThat(response.status()).isEqualTo("MISMATCH");
        assertThat(response.fullName()).isEqualTo("Nguyễn Hoài Băng Tâm");
        assertThat(response.dateOfBirth()).isEqualTo("2018-07-14");
    }

    @Test
    void rejectsSameNameAndDateOfBirthWhenIdentityNumberDoesNotMatch() {
        Patient patient = patient("Nguyễn Hoài Băng Tâm", LocalDate.of(2018, 7, 14), "079218999999");
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(portal.lookup("0000000104")).thenReturn(Optional.of(result(
                "0000000104", "HS4560000000104", "Nguyễn Hoài Băng Tâm", "20180714", (short) 0, (short) 80)));

        var response = service.verify(new InsuranceVerificationRequest(PATIENT_ID, "0000000104"));

        assertThat(response.status()).isEqualTo("MISMATCH");
        assertThat(response.identityNumber()).isEqualTo(IDENTITY_NUMBER);
    }

    @Test
    void comparesOnlyYearForYearOnlyPortalDate() {
        Patient patient = patient("Nguyễn Hoài Băng Tâm", LocalDate.of(2018, 12, 31));
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(portal.lookup("0000000104")).thenReturn(Optional.of(result(
                "0000000104", "HS4560000000104", "Nguyễn Hoài Băng Tâm", "20180000", (short) 1, (short) 80)));

        assertThat(service.verify(new InsuranceVerificationRequest(PATIENT_ID, "0000000104")).status())
                .isEqualTo("VALID");
    }

    @Test
    void fallsBackToDemographicsAndRequiresManualReviewWhenIdentityIsMissing() {
        Patient patient = patient("Nguyễn Hoài Băng Tâm", LocalDate.of(2018, 7, 14), null);
        when(patient.getGioitinh()).thenReturn((short) 2);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(portal.lookup("0000000104")).thenReturn(Optional.of(result(
                "0000000104", "HS4560000000104", null,
                "Nguyễn Hoài Băng Tâm", "20180714", (short) 0, (short) 2, (short) 80)));

        var response = service.verify(new InsuranceVerificationRequest(PATIENT_ID, "0000000104"));

        assertThat(response.status()).isEqualTo("VALID");
        assertThat(response.verificationMethod()).isEqualTo("DEMOGRAPHIC");
        assertThat(response.manualReviewRequired()).isTrue();
    }

    @Test
    void rejectsDemographicFallbackWhenGenderDoesNotMatch() {
        Patient patient = patient("Nguyễn Hoài Băng Tâm", LocalDate.of(2018, 7, 14), null);
        when(patient.getGioitinh()).thenReturn((short) 1);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(portal.lookup("0000000104")).thenReturn(Optional.of(result(
                "0000000104", "HS4560000000104", null,
                "Nguyễn Hoài Băng Tâm", "20180714", (short) 0, (short) 2, (short) 80)));

        assertThat(service.verify(new InsuranceVerificationRequest(PATIENT_ID, "0000000104")).status())
                .isEqualTo("MISMATCH");
    }

    private static Patient patient(String name, LocalDate dateOfBirth) {
        return patient(name, dateOfBirth, IDENTITY_NUMBER);
    }

    private static Patient patient(String name, LocalDate dateOfBirth, String identityNumber) {
        Patient patient = mock(Patient.class);
        if (identityNumber != null) {
            when(patient.getCccdSo()).thenReturn(identityNumber);
        }
        when(patient.getHoten()).thenReturn(name);
        when(patient.getNgaysinh()).thenReturn(dateOfBirth);
        return patient;
    }

    private static HealthInsurancePortal.Result result(
            String socialNumber, String cardNumber, String name, String dob, short dobType, short coverage) {
        return result(socialNumber, cardNumber, IDENTITY_NUMBER, name, dob, dobType, (short) 2, coverage);
    }

    private static HealthInsurancePortal.Result result(
            String socialNumber, String cardNumber, String identityNumber, String name, String dob,
            short dobType, short gender, short coverage) {
        return new HealthInsurancePortal.Result(
                socialNumber, cardNumber, identityNumber, "00", (short) 1, name, dob, dobType, gender,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "56158", "Phòng khám ĐKKV Bãi Dài", coverage);
    }
}
