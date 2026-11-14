package clinic.backend.service;

import java.time.LocalDate;

import clinic.backend.dto.response.ExaminedPatientListResponse;
import clinic.backend.security.user.StaffPrincipal;

/** UC-34 View Examined Patient List (Doctor). */
public interface ExaminedPatientService {

    /**
     * The visits {@code doctor} examined in {@code [from, to]} (queue date; a missing {@code from} is the first day of
     * the month, a missing {@code to} today; at most 366 days). {@code keyword} matches the patient name (ignoring
     * case and accents) or patient code. {@code page} starts at 0; {@code size} is 1 to 100 (default 20).
     */
    ExaminedPatientListResponse getExaminedPatients(
            StaffPrincipal doctor, LocalDate from, LocalDate to, String keyword, Integer page, Integer size);
}
