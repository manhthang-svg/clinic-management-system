package clinic.backend.service;

/**
 * A time-stamp service (Luật GDĐT 2023 Điều 31; NĐ 23/2025 Điều 32), so the signing time does not rest on the
 * clinic server's clock (BR-59-10 step 6). Throws {@code AppException(SIGNING_SERVICE_UNAVAILABLE)} when it does
 * not answer.
 */
public interface TimestampAuthority {

    /** An RFC 3161 time-stamp token (DER) over the SHA-256 of the data. */
    byte[] timestamp(byte[] data);
}
