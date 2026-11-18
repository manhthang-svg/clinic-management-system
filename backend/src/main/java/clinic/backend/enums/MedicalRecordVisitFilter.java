package clinic.backend.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * UC-51 status filter of the medical record search (BR-51-04). Each option is a fixed group of visit statuses.
 */
public enum MedicalRecordVisitFilter {
    /** Every visit that has not ended; a skipped visit keeps its status and stays here. */
    ACTIVE(EnumSet.of(
            VisitStatus.WAITING_FEE_PAYMENT,
            VisitStatus.WAITING,
            VisitStatus.IN_EXAMINATION,
            VisitStatus.WAITING_PARACLINICAL,
            VisitStatus.WAITING_CONCLUSION)),
    COMPLETED(EnumSet.of(VisitStatus.COMPLETED)),
    ABANDONED(EnumSet.of(VisitStatus.ABANDONED)),
    CANCELLED(EnumSet.of(VisitStatus.CANCELLED));

    private final Set<VisitStatus> statuses;

    MedicalRecordVisitFilter(Set<VisitStatus> statuses) {
        this.statuses = statuses;
    }

    public Set<VisitStatus> statuses() {
        return EnumSet.copyOf(statuses);
    }
}
