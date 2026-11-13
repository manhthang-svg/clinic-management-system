package clinic.backend.dto.request;

/**
 * UC-90 step 2: sign the prescription version the Doctor previewed.
 *
 * @param version the prescription version shown in the preview (optimistic locking)
 */
public record RequestSignatureRequest(Long version) {
}
