function isRecord(value) {
    return typeof value === 'object' && value !== null;
}

// The backend ApiResponse is @JsonInclude(NON_NULL): a null `data` is left out of the body, so `data` may be absent.
export function isApiResponse(value) {
    return isRecord(value) && value.success === true;
}

export function isErrorResponse(value) {
    return (isRecord(value) &&
        typeof value.code === 'string' &&
        typeof value.message === 'string');
}
