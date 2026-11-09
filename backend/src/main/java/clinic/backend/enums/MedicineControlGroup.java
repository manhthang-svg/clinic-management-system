package clinic.backend.enums;

/**
 * Special-control group of a medicine (Luật Dược 2016). Only {@link #THUONG} can be prescribed, because the
 * system issues only ordinary prescriptions of type c (BR-52).
 */
public enum MedicineControlGroup {
    /** Ordinary medicine. */
    THUONG,
    /** Narcotic; needs prescription type n. */
    GAY_NGHIEN,
    /** Psychotropic; needs prescription type h. */
    HUONG_THAN,
    /** Precursor; needs prescription type h. */
    TIEN_CHAT
}
