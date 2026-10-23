package clinic.backend.dto.response;

import java.util.UUID;

import clinic.backend.enums.DepartmentType;

public record DepartmentOptionResponse(
        UUID id,
        String code,
        String name,
        DepartmentType type,
        boolean active) {
}
