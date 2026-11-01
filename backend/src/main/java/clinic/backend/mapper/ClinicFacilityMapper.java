package clinic.backend.mapper;

import clinic.backend.dto.response.ClinicFacilityResponse;
import clinic.backend.entity.ClinicFacility;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClinicFacilityMapper {

    @Mapping(target = "configured", constant = "true")
    @Mapping(target = "maCskcbLocked", source = "maCskcbLocked")
    ClinicFacilityResponse toResponse(ClinicFacility facility, boolean maCskcbLocked);

    /** The facility has not been declared yet. */
    default ClinicFacilityResponse unconfigured(boolean maCskcbLocked) {
        return new ClinicFacilityResponse(false, null, null, null, null, null, null, null,
                maCskcbLocked, null, null);
    }
}
