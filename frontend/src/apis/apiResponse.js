function isRecord(value) {
    return typeof value === 'object' && value !== null;
}

export function isApiResponse(value) {
    return isRecord(value) && value.success === true && 'data' in value;
}

export function isErrorResponse(value) {
    return (isRecord(value) &&
        typeof value.code === 'string' &&
        typeof value.message === 'string');
}
