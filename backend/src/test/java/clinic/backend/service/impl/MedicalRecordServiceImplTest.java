package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import clinic.backend.dto.response.MedicalRecordDetailResponse;
import clinic.backend.dto.response.MedicalRecordPatientItemResponse;
import clinic.backend.dto.response.PageResponse;
import clinic.backend.dto.response.PatientVisitHistoryResponse;
import clinic.backend.dto.response.PatientVisitItemResponse;
import clinic.backend.dto.response.ResultFileResponse;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.ImagingResultImage;
import clinic.backend.entity.LabResult;
import clinic.backend.entity.LabResultItem;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.MedicalRecordVisitFilter;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.mapper.MedicalRecordDetailMapperImpl;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.FollowUpAppointmentRepository;
import clinic.backend.repository.ImagingResultImageRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.ParaclinicalOrderRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PrescriptionRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.repository.VisitTransferRepository;
import clinic.backend.repository.VitalSignRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.ResultImageStorage;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** UC-51, UC-52 (docs/uc-51-52-medical-record-view.md). */
@ExtendWith(MockitoExtension.class)
class MedicalRecordServiceImplTest {

    private static final UUID ACTOR = UUID.randomUUID();

    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private ClinicalExaminationRepository clinicalExaminationRepository;
    @Mock private ConclusionRepository conclusionRepository;
    @Mock private FollowUpAppointmentRepository followUpAppointmentRepository;
    @Mock private MedicalHistoryRepository medicalHistoryRepository;
    @Mock private VitalSignRepository vitalSignRepository;
    @Mock private VisitTransferRepository visitTransferRepository;
    @Mock private ParaclinicalOrderRepository paraclinicalOrderRepository;
    @Mock private ParaclinicalOrderDetailRepository paraclinicalOrderDetailRepository;
    @Mock private LabResultRepository labResultRepository;
    @Mock private ImagingResultRepository imagingResultRepository;
    @Mock private ImagingResultImageRepository imagingResultImageRepository;
    @Mock private PrescriptionRepository prescriptionRepository;
    @Mock private AuditLogService auditLogService;
    @Mock private ResultImageStorage resultImageStorage;

    private Clock clock;
    private MedicalRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-17T04:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        MedicalRecordDetailAssembler assembler = new MedicalRecordDetailAssembler(
                medicalRecordRepository,
                roomRepository,
                staffAccountRepository,
                medicalHistoryRepository,
                visitTransferRepository,
                paraclinicalOrderDetailRepository,
                labResultRepository,
                imagingResultRepository,
                imagingResultImageRepository,
                new MedicalRecordDetailMapperImpl(),
                clock);
        service = new MedicalRecordServiceImpl(
                medicalRecordRepository,
                visitRepository,
                patientRepository,
                paraclinicalOrderDetailRepository,
                imagingResultRepository,
                imagingResultImageRepository,
                resultImageStorage,
                assembler,
                auditLogService,
                clock);
    }

    @Test
    @DisplayName("UC-51 BR-51-04: từ ngày sau đến ngày bị từ chối (MSG50)")
    void searchPatients_fromAfterTo_isRejected() {
        assertInvalid(() -> service.searchPatients(null, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 10),
                null, null, null, 0, 20, ACTOR), "from", "AFTER_TO");
    }

    @Test
    @DisplayName("UC-51 BR-51-04: từ khóa dưới 2 hoặc trên 100 ký tự bị từ chối (MSG132, MSG08)")
    void searchPatients_textLength_isChecked() {
        assertInvalid(() -> service.searchPatients(" a ", null, null, null, null, null, 0, 20, ACTOR),
                "keyword", "TOO_SHORT");
        assertInvalid(() -> service.searchPatients("x".repeat(101), null, null, null, null, null, 0, 20, ACTOR),
                "keyword", "TOO_LONG");
        assertInvalid(() -> service.searchPatients(null, null, null, null, "x".repeat(101), null, 0, 20, ACTOR),
                "icd10", "TOO_LONG");
    }

    @Test
    @DisplayName("UC-51 BR-51-05: page âm hoặc size ngoài 1–100 bị từ chối")
    void searchPatients_paging_isChecked() {
        assertInvalid(() -> service.searchPatients(null, null, null, null, null, null, -1, 20, ACTOR),
                "page", "OUT_OF_RANGE");
        assertInvalid(() -> service.searchPatients(null, null, null, null, null, null, 0, 101, ACTOR),
                "size", "OUT_OF_RANGE");
        assertInvalid(() -> service.searchPatients(null, null, null, null, null, null, 0, 0, ACTOR),
                "size", "OUT_OF_RANGE");
    }

    @Test
    @DisplayName("UC-51: từ khóa được thoát ký tự LIKE, bộ lọc ACTIVE gửi đúng 5 trạng thái, giới tính trả mã")
    void searchPatients_buildsQueryAndMapsRows() {
        UUID patientId = UUID.randomUUID();
        when(medicalRecordRepository.searchPatientsSummary(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(summaryRow(patientId)), PageRequest.of(0, 20), 1));

        PageResponse<MedicalRecordPatientItemResponse> response = service.searchPatients(
                "  Nguyễn_A% ", null, null, null, " J00 ", MedicalRecordVisitFilter.ACTIVE, 0, 20, ACTOR);

        ArgumentCaptor<Collection<String>> statuses = collectionCaptor();
        verify(medicalRecordRepository).searchPatientsSummary(eq("%nguyễn\\_a\\%%"), isNull(), isNull(), isNull(),
                statuses.capture(), eq("%j00%"), eq(PageRequest.of(0, 20)));
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(
                "WAITING_FEE_PAYMENT", "WAITING", "IN_EXAMINATION", "WAITING_PARACLINICAL", "WAITING_CONCLUSION");

        MedicalRecordPatientItemResponse item = response.content().getFirst();
        assertThat(item.patientId()).isEqualTo(patientId);
        assertThat(item.gender()).isEqualTo((short) 1);
        assertThat(item.totalVisits()).isEqualTo(3L);
        assertThat(item.lastDiagnosisIcd()).isEqualTo("J00");
        assertThat(response.page().totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("UC-51: không có bộ lọc trạng thái thì gửi mọi trạng thái, kể cả Đã hủy")
    void searchPatients_withoutStatus_coversEveryStatus() {
        when(medicalRecordRepository.searchPatientsSummary(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.searchPatients(null, null, null, null, null, null, 0, 20, ACTOR);

        ArgumentCaptor<Collection<String>> statuses = collectionCaptor();
        verify(medicalRecordRepository).searchPatientsSummary(isNull(), isNull(), isNull(), isNull(),
                statuses.capture(), isNull(), any());
        assertThat(statuses.getValue()).hasSize(VisitStatus.values().length).contains("CANCELLED");
    }

    @Test
    @DisplayName("UC-51 BR-51-07: audit tìm kiếm chỉ chứa tên bộ lọc và số kết quả, không chứa từ khóa")
    void searchPatients_auditHoldsFilterNamesOnly() {
        when(medicalRecordRepository.searchPatientsSummary(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(summaryRow(UUID.randomUUID())), PageRequest.of(0, 20), 1));

        service.searchPatients("012345678901", LocalDate.of(2026, 11, 1), null, UUID.randomUUID(), null,
                MedicalRecordVisitFilter.COMPLETED, 0, 20, ACTOR);

        Map<String, Object> values = auditValues("MEDICAL_RECORD_SEARCH", null, null);
        assertThat(values).containsOnlyKeys("filters", "resultCount");
        assertThat(values.get("filters")).isEqualTo(List.of("keyword", "from", "roomId", "status"));
        assertThat(values.get("resultCount")).isEqualTo(1L);
        assertThat(values.toString()).doesNotContain("012345678901");
    }

    @Test
    @DisplayName("UC-51 cấp 2: trả bệnh nhân thật và lượt khám dạng mã; audit chỉ có số lượt")
    void getPatientVisits_returnsPatientAndVisits() {
        UUID patientId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        Patient patient = mock(Patient.class);
        when(patient.getId()).thenReturn(patientId);
        when(patient.getMabenhnhan()).thenReturn("BN00000123");
        when(patient.getHoten()).thenReturn("Nguyễn Văn A");
        when(patient.getGioitinh()).thenReturn((short) 2);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(medicalRecordRepository.findVisitsByPatientId(patientId))
                .thenReturn(List.of(visitRow(visitId, VisitStatus.CANCELLED)));

        PatientVisitHistoryResponse result = service.getPatientVisits(patientId, ACTOR);

        assertThat(result.patient().patientCode()).isEqualTo("BN00000123");
        assertThat(result.patient().fullName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.patient().gender()).isEqualTo((short) 2);
        PatientVisitItemResponse item = result.visits().getFirst();
        assertThat(item.medicalRecordId()).isNull();
        assertThat(item.status()).isEqualTo(VisitStatus.CANCELLED);
        assertThat(item.skipped()).isFalse();
        assertThat(item.patientType()).isEqualTo(VisitDoituongbnLoai.BHYT);
        assertThat(item.doctorName()).isEqualTo("BS.CKI Trần Văn B");
        assertThat(item.visitDate()).isEqualTo(LocalDate.of(2026, 11, 15));

        assertThat(auditValues("PATIENT", patientId, null)).containsExactly(Map.entry("visitCount", 1));
    }

    @Test
    @DisplayName("UC-51 EF-04: bệnh nhân không tồn tại trả PATIENT_NOT_FOUND và không ghi audit")
    void getPatientVisits_unknownPatient_isNotFound() {
        UUID patientId = UUID.randomUUID();
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPatientVisits(patientId, ACTOR))
                .isInstanceOf(AppException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PATIENT_NOT_FOUND);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("UC-52 EF-01: hồ sơ không tồn tại trả MEDICAL_RECORD_NOT_FOUND (theo ID và theo lượt)")
    void detail_unknownRecord_isNotFound() {
        UUID id = UUID.randomUUID();
        when(medicalRecordRepository.findDetailHeaderById(id)).thenReturn(Optional.empty());
        when(medicalRecordRepository.findDetailHeaderByVisitId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMedicalRecordDetail(id, ACTOR))
                .extracting("errorCode").isEqualTo(ErrorCode.MEDICAL_RECORD_NOT_FOUND);
        assertThatThrownBy(() -> service.getMedicalRecordByVisitId(id, ACTOR))
                .extracting("errorCode").isEqualTo(ErrorCode.MEDICAL_RECORD_NOT_FOUND);
        verifyNoInteractions(auditLogService);
    }

    @Test
    @DisplayName("UC-52 BR-51-07: audit chi tiết ghi số vào viện của lượt, không phải số bệnh án")
    void detail_auditUsesVisitNumber() {
        Fixture fixture = detailFixture(VisitStatus.COMPLETED, MedicalRecordStatus.LOCKED, ACTOR);

        service.getMedicalRecordByVisitId(fixture.visitId(), ACTOR);

        assertThat(auditValues("MEDICAL_RECORD", fixture.recordId(), "VV0001"))
                .containsExactly(Map.entry("openedBy", "VISIT_ID"));
    }

    @Test
    @DisplayName("UC-52 BR-52-03: chỉ bác sĩ của lượt, lượt chưa kết thúc, hồ sơ OPEN mới có nút Sửa")
    void detail_editableRule() {
        assertThat(editable(VisitStatus.IN_EXAMINATION, MedicalRecordStatus.OPEN, ACTOR)).isTrue();
        assertThat(editable(VisitStatus.IN_EXAMINATION, MedicalRecordStatus.OPEN, UUID.randomUUID())).isFalse();
        assertThat(editable(VisitStatus.CANCELLED, MedicalRecordStatus.OPEN, ACTOR)).isFalse();
        assertThat(editable(VisitStatus.ABANDONED, MedicalRecordStatus.OPEN, ACTOR)).isFalse();
        assertThat(editable(VisitStatus.WAITING_CONCLUSION, MedicalRecordStatus.LOCKED, ACTOR)).isFalse();
    }

    @Test
    @DisplayName("UC-52 BR-52-05: kết quả CLS nháp không được trả về")
    void detail_hidesDraftResults() {
        Fixture fixture = detailFixture(VisitStatus.WAITING_PARACLINICAL, MedicalRecordStatus.OPEN, ACTOR);
        UUID orderId = UUID.randomUUID();
        UUID detailId = UUID.randomUUID();
        ParaclinicalOrder order = mock(ParaclinicalOrder.class);
        lenient().when(order.getId()).thenReturn(orderId);
        ParaclinicalOrderDetail detail = mock(ParaclinicalOrderDetail.class);
        when(detail.getId()).thenReturn(detailId);
        when(detail.getOrder()).thenReturn(order);
        LabResult draftLab = mock(LabResult.class);
        when(draftLab.isSubmitted()).thenReturn(false);
        ImagingResult draftImaging = mock(ImagingResult.class);
        when(draftImaging.isSubmitted()).thenReturn(false);
        when(paraclinicalOrderDetailRepository.findWithOrderByMedicalRecordId(fixture.recordId()))
                .thenReturn(List.of(detail));
        when(labResultRepository.findWithItemsByOrderDetailIdIn(List.of(detailId))).thenReturn(List.of(draftLab));
        when(imagingResultRepository.findByOrderDetailIdIn(List.of(detailId))).thenReturn(List.of(draftImaging));

        MedicalRecordDetailResponse response = service.getMedicalRecordDetail(fixture.recordId(), ACTOR);

        MedicalRecordDetailResponse.ParaclinicalServiceItem item =
                response.paraclinicalOrders().getFirst().services().getFirst();
        assertThat(item.labResult()).isNull();
        assertThat(item.imagingResult()).isNull();
        assertThat(item.resultSummary()).isNull();
    }

    @Test
    @DisplayName("UC-52 BR-52-06: tải tệp của kết quả đã gửi trong hồ sơ, tên tệp do hệ thống đặt, ghi audit")
    void downloadResultFile_servesTheFileAndAuditsIt() {
        Fixture fixture = detailFixture(VisitStatus.COMPLETED, MedicalRecordStatus.LOCKED, ACTOR);
        UUID fileId = resultFile(fixture.recordId(), true);
        when(resultImageStorage.download(STORED_URL)).thenReturn(new byte[] {1, 2, 3});

        ResultFileResponse file = service.downloadResultFile(fixture.recordId(), fileId, ACTOR);

        assertThat(file.fileName()).isEqualTo("KQ-CDHA_2026_01-2.png");
        assertThat(file.mimeType()).isEqualTo("image/png");
        assertThat(file.content()).containsExactly(1, 2, 3);
        assertThat(auditValues("IMAGING_RESULT_IMAGE", fileId, "VV0001"))
                .containsExactly(Map.entry("medicalRecordId", fixture.recordId().toString()));
    }

    @Test
    @DisplayName("UC-52 BR-52-06: tệp của hồ sơ khác hoặc của kết quả nháp trả 404, không tải và không ghi audit")
    void downloadResultFile_refusesFilesOutsideTheRecordOrNotSubmitted() {
        UUID recordId = UUID.randomUUID();
        when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.of(mock(MedicalRecord.class)));
        UUID otherRecordsFile = resultFile(UUID.randomUUID(), true);
        UUID draftFile = resultFile(recordId, false);

        assertThatThrownBy(() -> service.downloadResultFile(recordId, otherRecordsFile, ACTOR))
                .extracting("errorCode").isEqualTo(ErrorCode.RESULT_IMAGE_NOT_FOUND);
        assertThatThrownBy(() -> service.downloadResultFile(recordId, draftFile, ACTOR))
                .extracting("errorCode").isEqualTo(ErrorCode.RESULT_IMAGE_NOT_FOUND);
        verifyNoInteractions(resultImageStorage, auditLogService);
    }

    @Test
    @DisplayName("UC-52 EF-01: tải tệp của hồ sơ không tồn tại trả MEDICAL_RECORD_NOT_FOUND")
    void downloadResultFile_unknownRecord_isNotFound() {
        UUID recordId = UUID.randomUUID();
        when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.downloadResultFile(recordId, UUID.randomUUID(), ACTOR))
                .extracting("errorCode").isEqualTo(ErrorCode.MEDICAL_RECORD_NOT_FOUND);
        verifyNoInteractions(resultImageStorage, auditLogService);
    }

    private static final String STORED_URL = "https://res.cloudinary.com/demo/image/upload/x.png";

    /** One PNG of an imaging result for a service ordered in {@code recordId}. */
    private UUID resultFile(UUID recordId, boolean submitted) {
        UUID fileId = UUID.randomUUID();
        UUID imagingId = UUID.randomUUID();
        UUID orderDetailId = UUID.randomUUID();
        ImagingResultImage image = mock(ImagingResultImage.class);
        lenient().when(image.getImagingResultId()).thenReturn(imagingId);
        lenient().when(image.getHinhanh()).thenReturn(STORED_URL);
        lenient().when(image.getMimeType()).thenReturn("image/png");
        lenient().when(image.getDisplayOrder()).thenReturn(2);
        ImagingResult imaging = mock(ImagingResult.class);
        lenient().when(imaging.isSubmitted()).thenReturn(submitted);
        lenient().when(imaging.getOrderDetailId()).thenReturn(orderDetailId);
        lenient().when(imaging.getSophieu()).thenReturn("CDHA/2026/01");
        ParaclinicalOrder order = mock(ParaclinicalOrder.class);
        lenient().when(order.getMedicalRecordId()).thenReturn(recordId);
        ParaclinicalOrderDetail detail = mock(ParaclinicalOrderDetail.class);
        lenient().when(detail.getOrder()).thenReturn(order);
        lenient().when(imagingResultImageRepository.findById(fileId)).thenReturn(Optional.of(image));
        lenient().when(imagingResultRepository.findById(imagingId)).thenReturn(Optional.of(imaging));
        lenient().when(paraclinicalOrderDetailRepository.findById(orderDetailId)).thenReturn(Optional.of(detail));
        return fileId;
    }

    @Test
    @DisplayName("UC-52: số truy vấn cố định — phần một-một gộp một truy vấn, phần liên quan đọc theo lô")
    void detail_readsRelatedRowsInBatches() {
        Fixture fixture = detailFixture(VisitStatus.COMPLETED, MedicalRecordStatus.LOCKED, ACTOR);
        UUID orderId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID performerId = UUID.randomUUID();
        ParaclinicalOrder order = mock(ParaclinicalOrder.class);
        lenient().when(order.getId()).thenReturn(orderId);
        List<ParaclinicalOrderDetail> details = List.of(
                orderDetail(order, roomId, performerId), orderDetail(order, roomId, performerId),
                orderDetail(order, roomId, performerId));
        List<UUID> detailIds = details.stream().map(ParaclinicalOrderDetail::getId).toList();
        when(paraclinicalOrderDetailRepository.findWithOrderByMedicalRecordId(fixture.recordId())).thenReturn(details);
        when(labResultRepository.findWithItemsByOrderDetailIdIn(detailIds)).thenReturn(List.of());
        when(imagingResultRepository.findByOrderDetailIdIn(detailIds)).thenReturn(List.of());

        MedicalRecordDetailResponse response = service.getMedicalRecordDetail(fixture.recordId(), ACTOR);

        assertThat(response.paraclinicalOrders().getFirst().services()).hasSize(3);
        verify(roomRepository).findAllById(any());
        verify(staffAccountRepository).findAllById(Set.of(performerId));
        verify(medicalRecordRepository).findDetailParts(fixture.recordId());
        verify(paraclinicalOrderDetailRepository).findWithOrderByMedicalRecordId(fixture.recordId());
        verifyNoInteractions(patientRepository, visitRepository, vitalSignRepository, clinicalExaminationRepository,
                conclusionRepository, prescriptionRepository, followUpAppointmentRepository,
                paraclinicalOrderRepository);
        verify(paraclinicalOrderDetailRepository, never()).findByOrderId(any());
        verify(labResultRepository, never()).findByOrderDetailId(any());
        verify(imagingResultRepository, never()).findByOrderDetailId(any());
        verify(roomRepository, never()).findById(any());
    }

    @Test
    @DisplayName("UC-52 BR-52-02: màn chi tiết trả mã (giới tính, đối tượng, trạng thái, cờ bất thường), không trả nhãn")
    void detail_returnsCodesNotLabels() {
        Fixture fixture = detailFixture(VisitStatus.COMPLETED, MedicalRecordStatus.LOCKED, ACTOR);
        UUID orderId = UUID.randomUUID();
        ParaclinicalOrder order = mock(ParaclinicalOrder.class);
        when(order.getId()).thenReturn(orderId);
        ParaclinicalOrderDetail detail = orderDetail(order, null, null);
        UUID detailId = detail.getId();
        when(detail.getStatus()).thenReturn(ParaclinicalOrderDetailStatus.COMPLETED);
        LabResultItem value = mock(LabResultItem.class);
        when(value.getPkqxnTenxetnghiem()).thenReturn("Glucose");
        when(value.getKetquabatthuong()).thenReturn("1");
        LabResult lab = mock(LabResult.class);
        when(lab.isSubmitted()).thenReturn(true);
        when(lab.getOrderDetailId()).thenReturn(detailId);
        when(lab.getItems()).thenReturn(List.of(value));
        when(paraclinicalOrderDetailRepository.findWithOrderByMedicalRecordId(fixture.recordId()))
                .thenReturn(List.of(detail));
        when(labResultRepository.findWithItemsByOrderDetailIdIn(List.of(detailId))).thenReturn(List.of(lab));
        when(fixture.patient().getGioitinh()).thenReturn((short) 2);

        MedicalRecordDetailResponse response = service.getMedicalRecordDetail(fixture.recordId(), ACTOR);

        assertThat(response.gender()).isEqualTo((short) 2);
        assertThat(response.visitType()).isEqualTo(VisitDoituongbnLoai.BHYT);
        assertThat(response.visitStatus()).isEqualTo(VisitStatus.COMPLETED);
        MedicalRecordDetailResponse.ParaclinicalServiceItem service = response.paraclinicalOrders().getFirst()
                .services().getFirst();
        assertThat(service.status()).isEqualTo(ParaclinicalOrderDetailStatus.COMPLETED);
        assertThat(service.labResult().items().getFirst().testIndexName()).isEqualTo("Glucose");
        assertThat(service.labResult().items().getFirst().abnormal()).isTrue();
    }

    private static ParaclinicalOrderDetail orderDetail(ParaclinicalOrder order, UUID roomId, UUID performerId) {
        ParaclinicalOrderDetail detail = mock(ParaclinicalOrderDetail.class);
        UUID id = UUID.randomUUID();
        lenient().when(detail.getId()).thenReturn(id);
        lenient().when(detail.getOrder()).thenReturn(order);
        lenient().when(detail.getRoomId()).thenReturn(roomId);
        lenient().when(detail.getPerformerAccountId()).thenReturn(performerId);
        return detail;
    }

    private boolean editable(VisitStatus visitStatus, MedicalRecordStatus recordStatus, UUID visitDoctorId) {
        Fixture fixture = detailFixture(visitStatus, recordStatus, visitDoctorId);
        return service.getMedicalRecordDetail(fixture.recordId(), ACTOR).isEditable();
    }

    private record Fixture(UUID recordId, UUID visitId, Patient patient) {
    }

    /** A record and its visit; every other part of the record is empty. */
    private Fixture detailFixture(VisitStatus visitStatus, MedicalRecordStatus recordStatus, UUID visitDoctorId) {
        UUID recordId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        MedicalRecord record = mock(MedicalRecord.class);
        lenient().when(record.getId()).thenReturn(recordId);
        lenient().when(record.getVisitId()).thenReturn(visitId);
        lenient().when(record.getStatus()).thenReturn(recordStatus);
        Visit visit = mock(Visit.class);
        lenient().when(visit.getSovaovien()).thenReturn("VV0001");
        lenient().when(visit.getStatus()).thenReturn(visitStatus);
        lenient().when(visit.getDoctorAccountId()).thenReturn(visitDoctorId);
        UUID patientId = UUID.randomUUID();
        lenient().when(visit.getId()).thenReturn(visitId);
        lenient().when(visit.getPatientId()).thenReturn(patientId);
        lenient().when(visit.getDoituongbnLoai()).thenReturn(VisitDoituongbnLoai.BHYT);
        Patient patient = mock(Patient.class);
        lenient().when(patient.getId()).thenReturn(patientId);
        MedicalRecordRepository.DetailHeader header = mock(MedicalRecordRepository.DetailHeader.class);
        lenient().when(header.getRecord()).thenReturn(record);
        lenient().when(header.getVisit()).thenReturn(visit);
        lenient().when(header.getPatient()).thenReturn(patient);
        lenient().when(medicalRecordRepository.findDetailHeaderById(recordId)).thenReturn(Optional.of(header));
        lenient().when(medicalRecordRepository.findDetailHeaderByVisitId(visitId)).thenReturn(Optional.of(header));
        lenient().when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        lenient().when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        return new Fixture(recordId, visitId, patient);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Collection<String>> collectionCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> auditValues(String entityType, UUID entityId, String sovaovien) {
        ArgumentCaptor<Map<String, Object>> values = (ArgumentCaptor) ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).recordRead(eq(ACTOR), eq(entityType), eq(entityId), eq(sovaovien),
                values.capture(), eq(clock.instant()));
        return values.getValue();
    }

    private static void assertInvalid(ThrowingCallable call, String field, String reason) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(AppException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID);
                    assertThat(exception.getDetails()).containsExactly(Map.entry(field, reason));
                });
    }

    private static MedicalRecordRepository.PatientMedicalRecordSummaryRow summaryRow(UUID patientId) {
        return new MedicalRecordRepository.PatientMedicalRecordSummaryRow() {
            @Override public UUID getPatientId() { return patientId; }
            @Override public String getPatientCode() { return "BN00000123"; }
            @Override public String getFullName() { return "Nguyễn Văn A"; }
            @Override public LocalDate getBirthDate() { return LocalDate.of(1990, 5, 20); }
            @Override public Short getGender() { return 1; }
            @Override public Long getTotalVisits() { return 3L; }
            @Override public LocalDate getLastVisitDate() { return LocalDate.of(2026, 11, 15); }
            @Override public String getLastDiagnosisIcd() { return "J00"; }
            @Override public String getLastDiagnosisName() { return "Viêm mũi họng cấp"; }
        };
    }

    private static MedicalRecordRepository.PatientVisitRow visitRow(UUID visitId, VisitStatus status) {
        return new MedicalRecordRepository.PatientVisitRow() {
            @Override public UUID getVisitId() { return visitId; }
            @Override public UUID getMedicalRecordId() { return null; }
            @Override public String getRegistrationCode() { return "DK0000000456"; }
            @Override public Instant getRegisteredAt() { return Instant.parse("2026-11-15T01:00:00Z"); }
            @Override public LocalDate getVisitDate() { return LocalDate.of(2026, 11, 15); }
            @Override public String getRoomName() { return "Phòng Khám Nội 1"; }
            @Override public String getAcademicTitle() { return "BS.CKI"; }
            @Override public String getDoctorName() { return "Trần Văn B"; }
            @Override public VisitDoituongbnLoai getDoituongbnLoai() { return VisitDoituongbnLoai.BHYT; }
            @Override public String getFinalDiagnosisIcd() { return null; }
            @Override public String getFinalDiagnosisName() { return null; }
            @Override public VisitStatus getVisitStatus() { return status; }
            @Override public Boolean getSkipped() { return false; }
        };
    }
}
