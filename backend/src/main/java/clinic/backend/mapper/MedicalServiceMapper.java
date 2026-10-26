package clinic.backend.mapper;

import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.entity.MedicalService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MedicalServiceMapper {

    @Mapping(target = "name", source = "service.tenchidinh")
    @Mapping(target = "serviceGroupName", source = "serviceGroupName")
    @Mapping(target = "departmentName", source = "departmentName")
    MedicalServiceSummaryResponse toSummary(
            MedicalService service, String serviceGroupName, String departmentName);
}
