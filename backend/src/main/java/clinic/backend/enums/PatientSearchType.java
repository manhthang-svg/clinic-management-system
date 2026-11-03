package clinic.backend.enums;

/**
 * Search types of UC-35 Search Patient. ASSUMPTION: every type except NAME is an exact match, since AF-04
 * treats a citizen ID, passport or patient code as identifying one profile (BR-12).
 */
public enum PatientSearchType {
    /** 12 digits. */
    CITIZEN_ID,
    /** At most 15 letters or digits, case-insensitive. */
    PASSPORT,
    /** Case-insensitive. */
    PATIENT_CODE,
    /** 10 digits. */
    PHONE,
    /** 2 to 100 characters; contains-match ignoring case and Vietnamese accents. */
    NAME,
    /** 15 letters or digits; cards used in previous visits. */
    INSURANCE_CARD
}
