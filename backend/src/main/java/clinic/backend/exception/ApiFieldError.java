package clinic.backend.exception;

public record ApiFieldError(String field, String code, String message) {
}
