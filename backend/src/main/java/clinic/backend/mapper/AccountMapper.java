package clinic.backend.mapper;

import clinic.backend.dto.response.AccountDetailResponse;
import clinic.backend.dto.response.AccountSummaryResponse;
import clinic.backend.dto.response.AllowedAccountActionsResponse;
import clinic.backend.entity.StaffAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(target = "roomId", ignore = true)
    @Mapping(target = "roomCode", ignore = true)
    @Mapping(target = "roomName", source = "roomName")
    @Mapping(target = "departmentName", source = "departmentName")
    AccountSummaryResponse toSummary(StaffAccount account, String roomName, String departmentName);

    @Mapping(target = "roomName", source = "roomName")
    @Mapping(target = "departmentName", source = "departmentName")
    @Mapping(target = "allowedActions", source = "allowedActions")
    AccountDetailResponse toDetail(
            StaffAccount account,
            String roomName,
            String departmentName,
            AllowedAccountActionsResponse allowedActions);
}
