package clinic.backend.mapper;

import clinic.backend.dto.response.ProfileResponse;
import clinic.backend.entity.StaffAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProfileMapper {

    @Mapping(target = "roomName", source = "roomName")
    @Mapping(target = "departmentName", source = "departmentName")
    ProfileResponse toProfileResponse(StaffAccount account, String roomName, String departmentName);
}
