package clinic.backend.dto.response;

import java.util.UUID;

import clinic.backend.enums.ServiceGroupType;

public record ServiceGroupOptionResponse(
        UUID id,
        String code,
        String name,
        ServiceGroupType type,
        UUID departmentId) {

    public ServiceGroupOptionResponse(UUID id, String code, String name, ServiceGroupType type) {
        this(id, code, name, type, null);
    }
}
