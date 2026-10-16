export class ApiError extends Error {
    status;
    errorResponse;
    constructor(message, options = {}) {
        super(message, { cause: options.cause });
        this.name = 'ApiError';
        this.status = options.status;
        this.errorResponse = options.errorResponse;
    }
}
