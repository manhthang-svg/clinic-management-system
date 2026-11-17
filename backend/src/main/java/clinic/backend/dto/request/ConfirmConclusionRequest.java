package clinic.backend.dto.request;

/**
 * UC-58 confirmation of the conclusion, which closes the medical record.
 *
 * @param conclusionVersion  version of the conclusion the Doctor saw (BR-58-02, optimistic locking; required)
 * @param retentionCategory  "Loại hồ sơ lưu trữ" (BR-58-06): STANDARD, WORK_ACCIDENT, TRAFFIC_ACCIDENT or
 *                           PSYCHIATRIC; empty means STANDARD
 */
public record ConfirmConclusionRequest(Long conclusionVersion, String retentionCategory) {
}
