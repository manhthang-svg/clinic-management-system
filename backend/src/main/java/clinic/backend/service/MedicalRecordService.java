package clinic.backend.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import clinic.backend.dto.response.MedicalRecordDetailResponse;
import clinic.backend.dto.response.MedicalRecordItemResponse;
import clinic.backend.dto.response.MedicalRecordPatientItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PatientVisitItemResponse;
import clinic.backend.enums.MedicalRecordStatus;

public interface MedicalRecordService {

    /**
     * Screen #39 Cấp 1: Tìm kiếm danh sách bệnh nhân khớp bộ lọc (hồ sơ bệnh án).
     */
    PageResponse<MedicalRecordPatientItemResponse> searchPatients(
            LocalDate from,
            LocalDate to,
            String status,
            UUID roomId,
            String icd10,
            String keyword,
            Integer page,
            Integer size,
            UUID actorAccountId
    );

    /**
     * Screen #39 Cấp 2: Xem danh sách lượt khám của một bệnh nhân được chọn.
     */
    List<PatientVisitItemResponse> getPatientVisits(
            UUID patientId,
            UUID actorAccountId
    );

    PageResponse<MedicalRecordItemResponse> getMedicalRecords(
            LocalDate from,
            LocalDate to,
            MedicalRecordStatus status,
            UUID roomId,
            String keyword,
            Integer page,
            Integer size
    );

    MedicalRecordDetailResponse getMedicalRecordDetail(UUID id);

    MedicalRecordDetailResponse getMedicalRecordDetail(UUID id, UUID actorAccountId);

    MedicalRecordDetailResponse getMedicalRecordByVisitId(UUID visitId);

    MedicalRecordDetailResponse getMedicalRecordByVisitId(UUID visitId, UUID actorAccountId);

    /**
     * BR-42, I.3.3: Ghi nhận truy cập tệp đính kèm kết quả cận lâm sàng.
     */
    void recordFileAccess(UUID imageId, UUID actorAccountId);

    /**
     * UC-80 / BR-43: Kết xuất dữ liệu hồ sơ bệnh án dạng XML hoặc JSON theo chuẩn CV 365.
     */
    clinic.backend.dto.response.ExportMedicalRecordDataResponse exportMedicalRecordData(
            UUID medicalRecordId,
            clinic.backend.dto.request.ExportMedicalRecordDataRequest request,
            UUID actorAccountId
    );
}
