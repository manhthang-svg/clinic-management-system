package clinic.backend.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** UC-51 level 2 and AF-01: the selected patient and all of their visits, newest first (BR-51-06). */
public record PatientVisitHistoryResponse(
        Patient patient,
        List<PatientVisitItemResponse> visits
) {

    /** {@code gender} is the {@code patients.gioitinh} code. */
    public record Patient(
            UUID patientId,
            String patientCode,
            String fullName,
            LocalDate birthDate,
            short gender
    ) {
    }
}
