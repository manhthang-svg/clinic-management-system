package clinic.backend.dto.response;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Body of every error response.
 *
 * <p>{@code details} maps a field to a human-readable message for request validation, and to a stable reason
 * code for business-rule violations (for example {@code newPassword -> SAME_AS_CURRENT}). It is omitted when
 * empty.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ErrorResponse {

    private String code;
    private String message;
    private Map<String, String> details;
    private String path;
    private String requestId;
    private Instant timestamp;
}
