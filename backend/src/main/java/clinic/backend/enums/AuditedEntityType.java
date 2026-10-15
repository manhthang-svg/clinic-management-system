package clinic.backend.enums;

/**
 * Kind of record a business audit event refers to (I1-D35).
 */
public enum AuditedEntityType {
    PATIENT,
    VISIT,
    PAYMENT,
    DEPARTMENT,
    ROOM,
    STAFF_ROOM_ASSIGNMENT
}
