package clinic.backend.mapper;

import java.util.List;

import clinic.backend.dto.response.MedicalServiceDetailResponse;
import clinic.backend.dto.response.MedicalServiceSummaryResponse;
import clinic.backend.dto.response.PerformingRoomResponse;
import clinic.backend.dto.response.DepartmentOptionResponse;
import clinic.backend.dto.response.ServiceGroupOptionResponse;
import clinic.backend.entity.Department;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.Room;
import clinic.backend.entity.ServiceGroup;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MedicalServiceMapper {

    @Mapping(target = "name", source = "service.tenchidinh")
    @Mapping(target = "serviceGroupName", source = "serviceGroupName")
    @Mapping(target = "departmentName", source = "departmentName")
    MedicalServiceSummaryResponse toSummary(
            MedicalService service, String serviceGroupName, String departmentName);

    @Mapping(target = "name", source = "service.tenchidinh")
    @Mapping(target = "serviceGroupName", source = "serviceGroupName")
    @Mapping(target = "departmentName", source = "departmentName")
    @Mapping(target = "performingRooms", source = "performingRooms")
    MedicalServiceDetailResponse toDetail(
            MedicalService service,
            String serviceGroupName,
            String departmentName,
            List<PerformingRoomResponse> performingRooms);

    @Mapping(target = "code", source = "roomCode")
    @Mapping(target = "name", source = "roomName")
    PerformingRoomResponse toPerformingRoom(Room room);

    @Mapping(target = "code", source = "serviceGroupCode")
    @Mapping(target = "name", source = "serviceGroupName")
    @Mapping(target = "departmentId", source = "departmentId")
    ServiceGroupOptionResponse toServiceGroupOption(ServiceGroup serviceGroup);

    @Mapping(target = "code", source = "departmentCode")
    @Mapping(target = "name", source = "departmentName")
    DepartmentOptionResponse toDepartmentOption(Department department);
}
