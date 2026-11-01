package clinic.backend.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import clinic.backend.dto.request.InsuranceVerificationRequest;
import clinic.backend.dto.response.InsuranceVerificationResponse;
import clinic.backend.entity.Patient;
import clinic.backend.service.HealthInsurancePortal;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.PatientRepository;
import clinic.backend.service.InsuranceVerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InsuranceVerificationServiceImpl implements InsuranceVerificationService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final HealthInsurancePortal portal;
    private final PatientRepository patientRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public InsuranceVerificationResponse verify(InsuranceVerificationRequest request) {
        Patient patient = patientRepository.findById(request.patientId())
                .orElseThrow(() -> new AppException(ErrorCode.PATIENT_NOT_FOUND));
        String submittedCode = request.insuranceCode().strip().toUpperCase();
        String socialInsuranceNumber = socialInsuranceNumberOf(submittedCode);
        HealthInsurancePortal.Result card = portal.lookup(socialInsuranceNumber).orElse(null);
        if (card == null) {
            return invalidResponse(submittedCode, socialInsuranceNumber);
        }

        LocalDate today = LocalDate.now(clock.withZone(CLINIC_ZONE));
        if (!"00".equals(card.resultCode()) || card.status() != 1
                || today.isBefore(card.validFrom()) || today.isAfter(card.validTo())) {
            return toResponse("INVALID", submittedCode, card, null, false);
        }
        boolean compareByIdentity = hasText(card.identityNumber()) && hasText(patient.getCccdSo());
        boolean nameMatches = normalizeName(card.fullName()).equalsIgnoreCase(normalizeName(patient.getHoten()));
        boolean dateOfBirthMatches = dateOfBirthMatches(card, patient.getNgaysinh());
        boolean ownerMatches;
        String verificationMethod;
        if (compareByIdentity) {
            verificationMethod = "IDENTITY";
            ownerMatches = card.identityNumber().equals(patient.getCccdSo())
                    && nameMatches && dateOfBirthMatches;
        } else {
            verificationMethod = "DEMOGRAPHIC";
            ownerMatches = nameMatches && dateOfBirthMatches && card.gender() == patient.getGioitinh();
        }
        if (!ownerMatches) {
            return toResponse("MISMATCH", submittedCode, card, verificationMethod,
                    "DEMOGRAPHIC".equals(verificationMethod));
        }

        return toResponse("VALID", submittedCode, card, verificationMethod,
                "DEMOGRAPHIC".equals(verificationMethod));
    }

    public static String socialInsuranceNumberOf(String insuranceCode) {
        String normalized = insuranceCode == null ? "" : insuranceCode.strip().toUpperCase();
        if (normalized.matches("[0-9]{10}")) {
            return normalized;
        }
        if (normalized.matches("[A-Z]{2}[0-9]{13}")) {
            return normalized.substring(5);
        }
        throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED);
    }

    private static boolean dateOfBirthMatches(HealthInsurancePortal.Result card, LocalDate patientDate) {
        String value = card.dateOfBirth();
        if (patientDate == null || value == null || !value.matches("[0-9]{8}")) {
            return false;
        }
        String patient = patientDate.toString().replace("-", "");
        return switch (card.dateOfBirthType()) {
            case 0 -> value.equals(patient);
            case 1 -> value.substring(0, 4).equals(patient.substring(0, 4));
            case 2 -> value.substring(0, 6).equals(patient.substring(0, 6));
            default -> false;
        };
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static InsuranceVerificationResponse invalidResponse(String submittedCode, String socialNumber) {
        return new InsuranceVerificationResponse(
                "INVALID", submittedCode, socialNumber, "NOT_FOUND", false, null, null,
                null, false, null, null, null, null, null, null, null, null, null);
    }

    private static InsuranceVerificationResponse toResponse(
            String verificationStatus, String submittedCode, HealthInsurancePortal.Result card,
            String verificationMethod, boolean manualReviewRequired) {
        return new InsuranceVerificationResponse(
                verificationStatus, submittedCode, card.socialInsuranceNumber(), card.resultCode(), card.status() == 1,
                card.cardNumber(), card.identityNumber(), verificationMethod, manualReviewRequired, card.fullName(),
                displayDateOfBirth(card.dateOfBirth(), card.dateOfBirthType()),
                card.dateOfBirthType(), card.gender(), card.coverageRate(), card.validFrom(), card.validTo(),
                card.primaryCareFacilityCode(), card.primaryCareFacilityName());
    }

    private static String displayDateOfBirth(String value, short type) {
        if (value == null || !value.matches("[0-9]{8}")) return value;
        return switch (type) {
            case 0 -> value.substring(0, 4) + "-" + value.substring(4, 6) + "-" + value.substring(6, 8);
            case 1 -> value.substring(0, 4);
            case 2 -> value.substring(0, 4) + "-" + value.substring(4, 6);
            default -> value;
        };
    }
}
