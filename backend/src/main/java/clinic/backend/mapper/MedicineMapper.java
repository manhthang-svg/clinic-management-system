package clinic.backend.mapper;

import clinic.backend.dto.response.MedicineDetailResponse;
import clinic.backend.dto.response.MedicineSummaryResponse;
import clinic.backend.entity.MedicineCatalog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MedicineMapper {

    @Mapping(target = "code", source = "mathuocvattuBv")
    @Mapping(target = "name", source = "tenthuocvattu")
    @Mapping(target = "activeIngredient", source = "hoatchat")
    @Mapping(target = "strength", source = "hamluong")
    @Mapping(target = "unit", source = "donvitinh")
    @Mapping(target = "route", source = "duongdung")
    @Mapping(target = "controlGroup", source = "nhomKiemSoat")
    MedicineSummaryResponse toSummary(MedicineCatalog medicine);

    @Mapping(target = "code", source = "mathuocvattuBv")
    @Mapping(target = "registrationNumber", source = "soDangKy")
    @Mapping(target = "insuranceCode", source = "mathuocvattuByt")
    @Mapping(target = "name", source = "tenthuocvattu")
    @Mapping(target = "activeIngredient", source = "hoatchat")
    @Mapping(target = "strength", source = "hamluong")
    @Mapping(target = "route", source = "duongdung")
    @Mapping(target = "unit", source = "donvitinh")
    @Mapping(target = "controlGroup", source = "nhomKiemSoat")
    MedicineDetailResponse toDetail(MedicineCatalog medicine);
}
