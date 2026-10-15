package clinic.backend.exceptions;

import java.util.Map;

import clinic.backend.config.RequestCorrelationFilter;
import clinic.backend.dto.response.ErrorResponse;
import clinic.backend.enums.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseFactoryTest {

    @Test
    void createsErrorResponsesWithStableFields() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/resources");
        request.setAttribute(RequestCorrelationFilter.ATTRIBUTE_NAME, "test-request-id");

        ErrorResponse response = ErrorResponseFactory.create(
                ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("fieldName", "The value is invalid."), request);

        assertThat(response.getCode()).isEqualTo("REQUEST_VALIDATION_FAILED");
        assertThat(response.getMessage()).isEqualTo("One or more request fields are invalid.");
        assertThat(response.getPath()).isEqualTo("/api/v1/resources");
        assertThat(response.getRequestId()).isEqualTo("test-request-id");
        assertThat(response.getDetails()).containsExactlyEntriesOf(Map.of("fieldName", "The value is invalid."));
        assertThat(response.getTimestamp()).isNotNull();
    }

    @Test
    void entriesSharingACodeDifferOnlyInTheirMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");

        ErrorResponse response = ErrorResponseFactory.create(ErrorCode.SESSION_INVALID, request);

        assertThat(response.getCode()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getMessage()).isEqualTo("The session is no longer valid. Log in again.");
        assertThat(response.getDetails()).isNull();
        assertThat(response.getRequestId()).isNull();
    }
}
