package clinic.backend.enums;

/**
 * Printed result slip of a paraclinical service (UC-67, UC-68): LAB is the value table (17/BV2); the others are
 * descriptive forms of imaging and functional tests.
 */
public enum ResultForm {
    LAB,
    ULTRASOUND,
    XRAY,
    CT,
    MRI,
    ENDOSCOPY,
    ECG
}
