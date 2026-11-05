package clinic.backend.service;

/**
 * End-of-day job (BR-22, docs/state-transitions.md section 8): closes the visits of earlier days that are not
 * finished.
 */
public interface VisitEndOfDayService {

    /**
     * Cancels visits still waiting for the examination fee and abandons every other unfinished visit of an earlier
     * day, together with their unpaid receipts, unperformed paraclinical services and open records. Each visit is
     * closed in its own transaction; a visit that cannot be closed now is left for the next run.
     */
    Result closeUnfinishedVisits();

    /** Visits cancelled, visits abandoned, and visits left for the next run. */
    record Result(int cancelled, int abandoned, int deferred) {

        public int total() {
            return cancelled + abandoned + deferred;
        }
    }
}
