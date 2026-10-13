package clinic.backend.shared.api.error;

public record ApiFieldError(String field, String code, String message) {
}
