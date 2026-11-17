package clinic.backend.dto.response;

/**
 * UC-58 close check: what "Xác nhận kết luận" needs (BR-58-01). Records nothing.
 *
 * @param conclusionSaved          a conclusion is saved (UC-56)
 * @param conclusionVersion        its version, to send back with the confirmation; null when not saved
 * @param pendingParaclinicalCount services that are neither cancelled nor completed (BR-30, MSG25)
 * @param prescriptionStatus       {@code lienthong_status} of the visit's prescription; null when there is none
 * @param followUpScheduled        the visit has a scheduled follow-up appointment (never blocks)
 * @param canClose                 every condition holds
 */
public record ConclusionClosureResponse(
        boolean conclusionSaved,
        Long conclusionVersion,
        int pendingParaclinicalCount,
        String prescriptionStatus,
        boolean followUpScheduled,
        boolean canClose
) {
}
