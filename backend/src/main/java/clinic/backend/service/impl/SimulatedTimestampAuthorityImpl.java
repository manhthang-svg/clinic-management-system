package clinic.backend.service.impl;

import clinic.backend.service.TimestampAuthority;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Simulated RFC 3161 time-stamp service (NĐ 23/2025 Điều 32). Replace with a licensed service to go live. */
@Service
@RequiredArgsConstructor
public class SimulatedTimestampAuthorityImpl implements TimestampAuthority {

    private final SimulatedSigningAuthority authority;

    @Override
    public byte[] timestamp(byte[] data) {
        return authority.timestamp(data);
    }
}
