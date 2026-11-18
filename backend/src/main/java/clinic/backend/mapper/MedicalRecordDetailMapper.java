package clinic.backend.mapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import clinic.backend.dto.response.MedicalRecordDetailResponse.ClinicalExamDetail;
import clinic.backend.dto.response.MedicalRecordDetailResponse.ConclusionDetail;
import clinic.backend.dto.response.MedicalRecordDetailResponse.DiagnosisItem;
import clinic.backend.dto.response.MedicalRecordDetailResponse.FollowUpDetail;
import clinic.backend.dto.response.MedicalRecordDetailResponse.ImageAttachment;
import clinic.backend.dto.response.MedicalRecordDetailResponse.ImagingResultDetail;
import clinic.backend.dto.response.MedicalRecordDetailResponse.LabItem;
import clinic.backend.dto.response.MedicalRecordDetailResponse.LabResultDetail;
import clinic.backend.dto.response.MedicalRecordDetailResponse.PrescriptionDetail;
import clinic.backend.dto.response.MedicalRecordDetailResponse.PrescriptionMedicineItem;
import clinic.backend.dto.response.MedicalRecordDetailResponse.VitalSignsItem;
import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.ClinicalExaminationDiagnosis;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.ImagingResultImage;
import clinic.backend.entity.LabResult;
import clinic.backend.entity.LabResultItem;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.VitalSign;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** UC-52 BR-52-02: the parts of a medical record detail that map one entity to one DTO. */
@Mapper(componentModel = "spring")
public interface MedicalRecordDetailMapper {

    @Mapping(target = "bmi", expression = "java(bmi(source.getCannang(), source.getChieucao()))")
    VitalSignsItem toVitalSigns(VitalSign source);

    @Mapping(target = "initialDiagnosis", source = "chandoansobo")
    @Mapping(target = "processOfIllness", source = "quatrinhbenhly")
    @Mapping(target = "generalExamination", source = "khambenhToanthan")
    @Mapping(target = "partExamination", source = "khambenhBophan")
    ClinicalExamDetail toClinicalExam(ClinicalExamination source);

    @Mapping(target = "icdCode", source = "maicd")
    @Mapping(target = "icdName", source = "tenicd")
    @Mapping(target = "isPrimary", expression = "java(source.getThuTu() == 1)")
    DiagnosisItem toDiagnosis(ClinicalExaminationDiagnosis source);

    @Mapping(target = "labResultId", source = "id")
    @Mapping(target = "ticketNumber", source = "sophieu")
    @Mapping(target = "diagnosis", source = "chandoan")
    @Mapping(target = "notes", source = "ghichu")
    LabResultDetail toLabResult(LabResult source);

    @Mapping(target = "testIndexName", source = "pkqxnTenxetnghiem")
    @Mapping(target = "testValue", source = "ketqua")
    @Mapping(target = "normalRange", source = "trisothamchieu")
    @Mapping(target = "unit", source = "donvi")
    @Mapping(target = "abnormal", expression = "java(\"1\".equals(source.getKetquabatthuong()))")
    LabItem toLabItem(LabResultItem source);

    @Mapping(target = "imagingResultId", source = "source.id")
    @Mapping(target = "ticketNumber", source = "source.sophieu")
    @Mapping(target = "description", source = "source.kqclsMota")
    @Mapping(target = "conclusion", source = "source.kqclsKetluan")
    @Mapping(target = "recommendation", source = "source.kqclsDenghi")
    @Mapping(target = "externalLink", source = "source.kqclsLinkhinhanh")
    @Mapping(target = "images", source = "images")
    ImagingResultDetail toImagingResult(ImagingResult source, List<ImageAttachment> images);

    /** BR-52-05: no storage address and no uploaded file name. */
    @Mapping(target = "imageId", source = "id")
    ImageAttachment toImage(ImagingResultImage source);

    @Mapping(target = "diagnosis", source = "chandoan")
    @Mapping(target = "icdCode", source = "maicd")
    @Mapping(target = "icdName", source = "tenicd")
    @Mapping(target = "treatmentPlan", source = "huongdieutri")
    @Mapping(target = "doctorAdvice", source = "loidanbacsi")
    @Mapping(target = "treatmentResult", source = "ketquadieutri")
    ConclusionDetail toConclusion(Conclusion source);

    @Mapping(target = "prescriptionId", source = "id")
    @Mapping(target = "prescriptionCode", source = "madonthuoc")
    @Mapping(target = "ticketNumber", source = "sophieu")
    @Mapping(target = "status", source = "lienthongStatus")
    @Mapping(target = "notes", source = "luuY")
    @Mapping(target = "advice", source = "loiDan")
    @Mapping(target = "followUpDate", source = "ngayTaiKham")
    @Mapping(target = "items", source = "details")
    PrescriptionDetail toPrescription(Prescription source);

    @Mapping(target = "medicineName", source = "tenthuocvattu")
    @Mapping(target = "activeIngredient", source = "hoatchat")
    @Mapping(target = "strength", source = "hamluong")
    @Mapping(target = "unit", source = "donvitinh")
    @Mapping(target = "quantity", source = "soluongthuocvattu")
    @Mapping(target = "usageInstructions", source = "cachdung")
    @Mapping(target = "timesPerDay", source = "soLanNgay")
    @Mapping(target = "durationDays", source = "soNgayDung")
    @Mapping(target = "dosePerTime", source = "lieuMoiLan")
    PrescriptionMedicineItem toPrescriptionItem(clinic.backend.entity.PrescriptionDetail source);

    @Mapping(target = "appointmentId", source = "id")
    @Mapping(target = "appointmentNumber", source = "soGiayHen")
    @Mapping(target = "doctorAdvice", source = "loidanbacsi")
    FollowUpDetail toFollowUp(FollowUpAppointment source);

    /** kg / m², one decimal; empty when a measure is missing. */
    default BigDecimal bmi(BigDecimal weight, BigDecimal heightCentimeters) {
        if (weight == null || heightCentimeters == null || heightCentimeters.signum() <= 0) {
            return null;
        }
        BigDecimal heightMetres = heightCentimeters.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        return weight.divide(heightMetres.multiply(heightMetres), 1, RoundingMode.HALF_UP);
    }
}
