package clinic.backend.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.ExportMedicalRecordDataRequest;
import clinic.backend.dto.response.ExportMedicalRecordDataResponse;
import clinic.backend.entity.ExportLog;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.RecordArchive;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ExportFormat;
import clinic.backend.enums.ExportLogPurpose;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicalExaminationRepository;
import clinic.backend.repository.ConclusionRepository;
import clinic.backend.repository.ExportLogRepository;
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
import clinic.backend.repository.RecordArchiveRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.repository.VisitTransferRepository;
import clinic.backend.repository.VitalSignRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRecordExportServiceTest {

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
    @Mock private ArchivePackageBuilder archivePackageBuilder;
    @Mock private ExportLogRepository exportLogRepository;
    @Mock private RecordArchiveRepository recordArchiveRepository;
    @Mock private UuidV7Generator ids;

    private Clock clock;
    private MedicalRecordServiceImpl service;

    private static String sha256Hex(byte[] data) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new MedicalRecordServiceImpl(
                medicalRecordRepository,
                visitRepository,
                patientRepository,
                roomRepository,
                staffAccountRepository,
                clinicalExaminationRepository,
                conclusionRepository,
                followUpAppointmentRepository,
                medicalHistoryRepository,
                vitalSignRepository,
                visitTransferRepository,
                paraclinicalOrderRepository,
                paraclinicalOrderDetailRepository,
                labResultRepository,
                imagingResultRepository,
                imagingResultImageRepository,
                prescriptionRepository,
                auditLogService,
                clock,
                archivePackageBuilder,
                exportLogRepository,
                recordArchiveRepository,
                ids
        );
    }

    @Test
    @DisplayName("Xác nhận tính toàn vẹn SHA-256 và log đầy đủ khi xuất JSON liên thông")
    void exportJson_sha256IntegrityAndFullLogging() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorAccountId = UUID.randomUUID();
        UUID exportLogId = UUID.randomUUID();

        MedicalRecord record = mock(MedicalRecord.class);
        when(record.getId()).thenReturn(recordId);
        when(record.getSoba()).thenReturn("BA-2026-00099");
        when(record.getVisitId()).thenReturn(visitId);
        when(record.getSoluutru()).thenReturn("LT-DK2026-001");
        when(record.getClosedAt()).thenReturn(Instant.parse("2026-11-17T14:30:00Z"));
        when(record.getStatus()).thenReturn(MedicalRecordStatus.LOCKED);

        RecordArchive mockArchive = mock(RecordArchive.class);
        when(recordArchiveRepository.findByMedicalRecordId(recordId)).thenReturn(Optional.of(mockArchive));

        Visit visit = mock(Visit.class);
        when(visit.getSovaovien()).thenReturn("DK2026-001");
        when(visit.getPatientId()).thenReturn(patientId);

        Patient patient = mock(Patient.class);

        when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(ids.next()).thenReturn(exportLogId);

        byte[] jsonPayload = "{\"HoSoBenhAn\":{\"ThongTinBenhNhan\":{\"hoten\":\"NGUYEN VAN A\"}}}".getBytes(StandardCharsets.UTF_8);
        String expectedSha256 = sha256Hex(jsonPayload);
        when(archivePackageBuilder.build(any())).thenReturn(jsonPayload);

        ExportMedicalRecordDataRequest request = ExportMedicalRecordDataRequest.builder()
                .format(ExportFormat.JSON)
                .purpose(ExportLogPurpose.LIEN_THONG)
                .recipient("Cổng Tiếp nhận BHYT Quốc gia")
                .build();

        ExportMedicalRecordDataResponse response = service.exportMedicalRecordData(recordId, request, doctorAccountId);

        // 1. Kiểm tra tính toàn vẹn SHA-256
        assertThat(response.fileName()).isEqualTo("HoSoBenhAn_BA-2026-00099_DK2026-001.json");
        assertThat(response.contentType()).isEqualTo("application/json; charset=UTF-8");
        assertThat(response.content()).isEqualTo(jsonPayload);
        assertThat(response.fileSha256()).isEqualTo(expectedSha256);

        // 2. Kiểm tra log đầy đủ trong export_logs
        ArgumentCaptor<ExportLog> exportLogCaptor = ArgumentCaptor.forClass(ExportLog.class);
        verify(exportLogRepository).save(exportLogCaptor.capture());
        ExportLog savedLog = exportLogCaptor.getValue();

        assertThat(savedLog.getId()).isEqualTo(exportLogId);
        assertThat(savedLog.getMedicalRecordId()).isEqualTo(recordId);
        assertThat(savedLog.getSovaovien()).isEqualTo("DK2026-001");
        assertThat(savedLog.getExportFormat()).isEqualTo(ExportFormat.JSON);
        assertThat(savedLog.getPurpose()).isEqualTo(ExportLogPurpose.LIEN_THONG);
        assertThat(savedLog.getRecipient()).isEqualTo("Cổng Tiếp nhận BHYT Quốc gia");
        assertThat(savedLog.getFileSha256()).isEqualTo(expectedSha256);
        assertThat(savedLog.getSpecVersion()).isEqualTo("CV365/2025");
        assertThat(savedLog.getExportedByAccountId()).isEqualTo(doctorAccountId);
        assertThat(savedLog.getExportedAt()).isEqualTo(clock.instant());

        // 3. Kiểm tra audit log nghiệp vụ
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> auditDetailsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).recordBusinessChange(
                eq(doctorAccountId),
                eq(AuditLogAction.EXPORT),
                eq("MEDICAL_RECORD"),
                eq(recordId),
                eq("BA-2026-00099"),
                auditDetailsCaptor.capture(),
                eq(clock.instant())
        );
        Map<String, Object> details = auditDetailsCaptor.getValue();
        assertThat(details.get("action")).isEqualTo("EXPORT_MEDICAL_RECORD_DATA");
        assertThat(details.get("format")).isEqualTo("JSON");
        assertThat(details.get("purpose")).isEqualTo("LIEN_THONG");
        assertThat(details.get("sha256")).isEqualTo(expectedSha256);
        assertThat(details.get("exportLogId")).isEqualTo(exportLogId.toString());
    }

    @Test
    @DisplayName("Xác nhận tính toàn vẹn SHA-256 và log đầy đủ khi xuất XML cho Cơ quan có thẩm quyền")
    void exportXml_sha256IntegrityAndFullLogging() throws Exception {
        UUID recordId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID doctorAccountId = UUID.randomUUID();
        UUID exportLogId = UUID.randomUUID();

        MedicalRecord record = mock(MedicalRecord.class);
        when(record.getId()).thenReturn(recordId);
        when(record.getSoba()).thenReturn("BA-2026-00100");
        when(record.getVisitId()).thenReturn(visitId);
        when(record.getSoluutru()).thenReturn("LT-DK2026-002");
        when(record.getClosedAt()).thenReturn(Instant.parse("2026-11-17T14:45:00Z"));
        when(record.getStatus()).thenReturn(MedicalRecordStatus.LOCKED);

        RecordArchive mockArchive = mock(RecordArchive.class);
        when(recordArchiveRepository.findByMedicalRecordId(recordId)).thenReturn(Optional.of(mockArchive));

        Visit visit = mock(Visit.class);
        when(visit.getSovaovien()).thenReturn("DK2026-002");
        when(visit.getPatientId()).thenReturn(patientId);

        Patient patient = mock(Patient.class);

        when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(ids.next()).thenReturn(exportLogId);

        byte[] xmlPayload = "<HoSoBenhAn><ThongTinBenhNhan><hoten>TRAN THI B</hoten></ThongTinBenhNhan></HoSoBenhAn>".getBytes(StandardCharsets.UTF_8);
        String expectedSha256 = sha256Hex(xmlPayload);
        when(archivePackageBuilder.buildXml(any())).thenReturn(xmlPayload);

        ExportMedicalRecordDataRequest request = ExportMedicalRecordDataRequest.builder()
                .format(ExportFormat.XML)
                .purpose(ExportLogPurpose.CO_QUAN_CO_THAM_QUYEN)
                .recipient("Tòa án nhân dân Thành phố")
                .build();

        ExportMedicalRecordDataResponse response = service.exportMedicalRecordData(recordId, request, doctorAccountId);

        // 1. Kiểm tra tính toàn vẹn SHA-256
        assertThat(response.fileName()).isEqualTo("HoSoBenhAn_BA-2026-00100_DK2026-002.xml");
        assertThat(response.contentType()).isEqualTo("application/xml; charset=UTF-8");
        assertThat(response.content()).isEqualTo(xmlPayload);
        assertThat(response.fileSha256()).isEqualTo(expectedSha256);

        // 2. Kiểm tra log đầy đủ trong export_logs
        ArgumentCaptor<ExportLog> exportLogCaptor = ArgumentCaptor.forClass(ExportLog.class);
        verify(exportLogRepository).save(exportLogCaptor.capture());
        ExportLog savedLog = exportLogCaptor.getValue();

        assertThat(savedLog.getId()).isEqualTo(exportLogId);
        assertThat(savedLog.getMedicalRecordId()).isEqualTo(recordId);
        assertThat(savedLog.getSovaovien()).isEqualTo("DK2026-002");
        assertThat(savedLog.getExportFormat()).isEqualTo(ExportFormat.XML);
        assertThat(savedLog.getPurpose()).isEqualTo(ExportLogPurpose.CO_QUAN_CO_THAM_QUYEN);
        assertThat(savedLog.getRecipient()).isEqualTo("Tòa án nhân dân Thành phố");
        assertThat(savedLog.getFileSha256()).isEqualTo(expectedSha256);
        assertThat(savedLog.getSpecVersion()).isEqualTo("CV365/2025");
        assertThat(savedLog.getExportedByAccountId()).isEqualTo(doctorAccountId);
        assertThat(savedLog.getExportedAt()).isEqualTo(clock.instant());

        // 3. Kiểm tra audit log
        verify(auditLogService).recordBusinessChange(
                eq(doctorAccountId),
                eq(AuditLogAction.EXPORT),
                eq("MEDICAL_RECORD"),
                eq(recordId),
                eq("BA-2026-00100"),
                any(),
                eq(clock.instant())
        );
    }

    @Test
    @DisplayName("Ném lỗi NOT_FOUND khi không truyền medicalRecordId hoặc không tìm thấy hồ sơ")
    void export_notFound_throwsException() {
        UUID doctorAccountId = UUID.randomUUID();
        ExportMedicalRecordDataRequest request = ExportMedicalRecordDataRequest.builder()
                .format(ExportFormat.JSON)
                .build();

        // Null medicalRecordId
        assertThatThrownBy(() -> service.exportMedicalRecordData(null, request, doctorAccountId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        // Không tồn tại trong DB
        UUID nonexistentId = UUID.randomUUID();
        when(medicalRecordRepository.findById(nonexistentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exportMedicalRecordData(nonexistentId, request, doctorAccountId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);

        verify(exportLogRepository, never()).save(any());
        verify(auditLogService, never()).recordBusinessChange(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ném lỗi REQUEST_PARAMETER_INVALID khi request hoặc format bị null")
    void export_invalidRequest_throwsException() {
        UUID recordId = UUID.randomUUID();
        UUID doctorAccountId = UUID.randomUUID();

        // Request null
        assertThatThrownBy(() -> service.exportMedicalRecordData(recordId, null, doctorAccountId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID);

        // Format null
        ExportMedicalRecordDataRequest requestNoFormat = ExportMedicalRecordDataRequest.builder()
                .format(null)
                .build();

        assertThatThrownBy(() -> service.exportMedicalRecordData(recordId, requestNoFormat, doctorAccountId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID);

        verify(exportLogRepository, never()).save(any());
    }
}
