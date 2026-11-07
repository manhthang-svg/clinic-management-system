package clinic.backend.enums;

/** Tabs exposed by the UC-43 Doctor waiting-list read model. */
public enum DoctorQueueTab {
    /** "Danh sách bệnh nhân": visits waiting for or in examination, not skipped. */
    PATIENT_LIST,
    WAITING,
    IN_EXAMINATION,
    WAITING_CONCLUSION,
    SKIPPED
}
