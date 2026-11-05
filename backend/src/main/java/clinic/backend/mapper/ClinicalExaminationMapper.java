package clinic.backend.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;

import clinic.backend.dto.response.ExaminationFindingResponse;
import clinic.backend.dto.response.Icd10OptionResponse;
import clinic.backend.dto.response.MedicalHistoryItemResponse;
import clinic.backend.dto.response.VitalSignResponse;
import clinic.backend.entity.ExaminationFinding;
import clinic.backend.entity.Icd10;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.VitalSign;
import clinic.backend.enums.OrganSystem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ClinicalExaminationMapper {

    @Mapping(target = "pulse", source = "mach")
    @Mapping(target = "temperature", source = "nhietdo")
    @Mapping(target = "respiratoryRate", source = "nhiptho")
    @Mapping(target = "systolicBloodPressure", source = "huyetapTamthu")
    @Mapping(target = "diastolicBloodPressure", source = "huyetapTamtruong")
    @Mapping(target = "weight", source = "cannang")
    @Mapping(target = "height", source = "chieucao")
    @Mapping(target = "measuredAt", source = "thoidiem")
    @Mapping(target = "measuredByName", ignore = true)
    @Mapping(target = "bmi", expression = "java(calculateBmi(source.getCannang(), source.getChieucao()))")
    VitalSignResponse toVitalSignResponse(VitalSign source);

    default VitalSignResponse toVitalSignResponse(VitalSign source, String measuredByName) {
        if (source == null) {
            return null;
        }
        VitalSignResponse value = toVitalSignResponse(source);
        return new VitalSignResponse(
                value.pulse(), value.temperature(), value.respiratoryRate(),
                value.systolicBloodPressure(), value.diastolicBloodPressure(),
                value.weight(), value.height(), value.spo2(), value.bmi(),
                value.measuredAt(), value.measuredByAccountId(), measuredByName);
    }

    default ExaminationFindingResponse toFindingResponse(ExaminationFinding source) {
        return new ExaminationFindingResponse(
                OrganSystem.valueOf(source.getCoquan()), source.getDauchung(), source.getDauchungGhichu());
    }

    default MedicalHistoryItemResponse toHistoryResponse(MedicalHistory source) {
        return new MedicalHistoryItemResponse(
                source.getId(), source.getVersion(), source.getHistoryType(), source.getThongtinMa(), source.getThongtinNoidung(),
                source.getThongtinGiatri(), source.getThongtinGhichu());
    }

    default Icd10OptionResponse toIcd10Option(Icd10 source) {
        return source == null ? null : new Icd10OptionResponse(source.getMaicd(), source.getTenicd());
    }

    default BigDecimal calculateBmi(BigDecimal weight, BigDecimal heightCentimeters) {
        if (weight == null || heightCentimeters == null || heightCentimeters.signum() <= 0) {
            return null;
        }
        BigDecimal heightMeters = heightCentimeters.movePointLeft(2);
        return weight.divide(heightMeters.multiply(heightMeters), 1, RoundingMode.HALF_UP);
    }
}
