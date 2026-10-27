package clinic.backend.dto.response;

import java.util.List;

import clinic.backend.enums.MedicalServiceType;

public record MedicalServiceFormOptionsResponse(
        List<MedicalServiceType> types,
        List<ServiceGroupOptionResponse> serviceGroups,
        List<DepartmentOptionResponse> departments) {
}
