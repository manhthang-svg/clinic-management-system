package clinic.backend.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.config.properties.ArchiveProperties;
import clinic.backend.config.properties.FacilityPrintProperties;
import clinic.backend.dto.request.ExportMedicalRecordRequest;
import clinic.backend.entity.ExportLog;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.RecordArchive;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.ExportFormat;
import clinic.backend.enums.ExportLogPurpose;
import clinic.backend.enums.ExportLogStatus;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ExportLogRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.RecordArchiveRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.MedicalRecordExportService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** UC-53 BR-53-02 to BR-53-08. */
@ExtendWith(MockitoExtension.class)
class MedicalRecordExportServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-18T02:15:00Z");
    private static final byte[] PACKAGE = "{\"DuLieuChu\":{\"soluutru\":\"LT-DK0000000001\"}}"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] PDF = "%PDF-1.7 test".getBytes(StandardCharsets.US_ASCII);

    @Mock private MedicalRecordRepository medicalRecordRepository;
    @Mock private RecordArchiveRepository recordArchiveRepository;
    @Mock private VisitRepository visitRepository;
    @Mock private StaffAccountRepository staffAccountRepository;
    @Mock private ExportLogRepository exportLogRepository;
    @Mock private ExportFailureRecorder failureRecorder;
    @Mock private Form15Bv1Renderer renderer;
    @Mock private AuditLogService auditLogService;

    @TempDir Path root;

    private final StaffPrincipal doctor = new StaffPrincipal(UUID.randomUUID(), UUID.randomUUID());
    private MedicalRecordExportServiceImpl service;
    private MedicalRecord record;

    @BeforeEach
    void setUp() throws Exception {
        Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new MedicalRecordExportServiceImpl(medicalRecordRepository, recordArchiveRepository,
                visitRepository, staffAccountRepository, exportLogRepository, failureRecorder,
                new ArchiveStore(new ArchiveProperties(root)), renderer, new FacilityPrintProperties("Sở Y tế Hà Nội"),
                auditLogService, new UuidV7Generator(clock, new SecureRandom()), clock);
        record = new MedicalRecord(UUID.randomUUID(), NOW, UUID.randomUUID(), "BA-DK0000000001", "NGOAITRU",
                MedicalRecordStatus.LOCKED, NOW, UUID.randomUUID());
        Files.createDirectories(root.resolve("2026"));
        Files.write(root.resolve("2026/LT-DK0000000001.json"), PACKAGE);
    }

    private RecordArchive archive(String sha256) {
        return RecordArchive.archive(UUID.randomUUID(), record.getId(), "LT-DK0000000001", UUID.randomUUID(),
                "2026/LT-DK0000000001.json", sha256, RecordArchiveRetentionCategory.STANDARD,
                LocalDate.of(2036, 12, 31), NOW);
    }

    private void closedRecord(String sha256) {
        when(medicalRecordRepository.findById(record.getId())).thenReturn(Optional.of(record));
        when(recordArchiveRepository.findByMedicalRecordId(record.getId())).thenReturn(Optional.of(archive(sha256)));
        when(visitRepository.findById(record.getVisitId())).thenReturn(Optional.empty());
    }

    private AppException export(String purpose, String recipient) {
        return (AppException) org.assertj.core.api.Assertions.catchThrowable(() ->
                service.exportPdf(doctor, record.getId(), new ExportMedicalRecordRequest(purpose, recipient)));
    }

    @Test
    void refusesAMissingOrUnknownPurpose() {
        assertThat(export(null, null).getDetails()).isEqualTo(Map.of("field", "purpose", "reason", "REQUIRED"));
        assertThat(export("KHAC", null).getDetails()).isEqualTo(Map.of("field", "purpose", "reason", "INVALID"));
        assertThat(export("KHAC", null).getErrorCode()).isEqualTo(ErrorCode.EXPORT_INVALID);
    }

    @Test
    void requiresTheRecipientForThePatientAndAuthoritiesAndLimitsItsLength() {
        assertThat(export("CUNG_CAP_NGUOI_BENH", "  ").getDetails())
                .isEqualTo(Map.of("field", "recipient", "reason", "REQUIRED"));
        assertThat(export("CO_QUAN_CO_THAM_QUYEN", null).getDetails())
                .isEqualTo(Map.of("field", "recipient", "reason", "REQUIRED"));
        assertThat(export("LUU_TRU", "x".repeat(201)).getDetails())
                .isEqualTo(Map.of("field", "recipient", "reason", "TOO_LONG"));
    }

    @Test
    void refusesARecordThatIsNotClosed() {
        MedicalRecord open = new MedicalRecord(record.getId(), NOW, record.getVisitId(), "BA-1", "NGOAITRU",
                MedicalRecordStatus.OPEN, NOW, UUID.randomUUID());
        when(medicalRecordRepository.findById(record.getId())).thenReturn(Optional.of(open));

        assertThat(export("LUU_TRU", null).getErrorCode()).isEqualTo(ErrorCode.EXPORT_RECORD_NOT_CLOSED);
        verify(recordArchiveRepository, never()).findByMedicalRecordId(any());
    }

    @Test
    void aPackageThatNoLongerMatchesItsHashIsLoggedAsAFailedExport() {
        closedRecord("0".repeat(64));

        assertThat(export("LUU_TRU", null).getErrorCode()).isEqualTo(ErrorCode.EXPORT_FAILED);

        ArgumentCaptor<ExportLog> failure = ArgumentCaptor.forClass(ExportLog.class);
        verify(failureRecorder).record(failure.capture());
        assertThat(failure.getValue().getStatus()).isEqualTo(ExportLogStatus.FAILED);
        assertThat(failure.getValue().getErrorMessage()).isEqualTo("PACKAGE_HASH_MISMATCH");
        verify(renderer, never()).render(any(), any());
        verify(exportLogRepository, never()).save(any());
    }

    @Test
    void storesTheFormLogsTheExportAndKeepsTheRecipientOutOfTheAudit() throws Exception {
        closedRecord(ArchiveStore.sha256(PACKAGE));
        when(renderer.render(any(), any())).thenReturn(PDF);

        MedicalRecordExportService.ExportedFile file = service.exportPdf(doctor, record.getId(),
                new ExportMedicalRecordRequest("cung_cap_nguoi_benh", " Nguyễn Thị B, đơn ngày 18/11/2026 "));

        assertThat(file.fileName()).isEqualTo("BA-LT-DK0000000001-202611180915.pdf");
        assertThat(file.content()).isEqualTo(PDF);
        ArgumentCaptor<ExportLog> saved = ArgumentCaptor.forClass(ExportLog.class);
        verify(exportLogRepository).save(saved.capture());
        ExportLog log = saved.getValue();
        assertThat(log.getExportFormat()).isEqualTo(ExportFormat.PDF);
        assertThat(log.getPurpose()).isEqualTo(ExportLogPurpose.CUNG_CAP_NGUOI_BENH);
        assertThat(log.getRecipient()).isEqualTo("Nguyễn Thị B, đơn ngày 18/11/2026");
        assertThat(log.getStatus()).isEqualTo(ExportLogStatus.SUCCESS);
        assertThat(log.getSpecVersion()).isEqualTo("TT32/2023-15/BV1");
        assertThat(log.getFileUri()).isEqualTo("exports/2026/" + log.getId() + ".pdf");
        Path stored = root.resolve(log.getFileUri());
        assertThat(Files.readAllBytes(stored)).isEqualTo(PDF);
        assertThat(Files.isWritable(stored)).isFalse();
        assertThat(log.getFileSha256()).isEqualTo(ArchiveStore.sha256(PDF));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> values = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).recordBusinessChange(eq(doctor.accountId()), eq(AuditLogAction.EXPORT),
                eq("MEDICAL_RECORD"), eq(record.getId()), eq("BA-DK0000000001"), values.capture(), eq(NOW));
        assertThat(values.getValue()).containsOnlyKeys("exportLogId", "format", "purpose", "specVersion",
                "fileSha256");
        assertThat(values.getValue().values()).noneMatch(value -> value.toString().contains("Nguyễn"));
    }

    @Test
    void aRenderingErrorIsLoggedAndNothingIsStored() throws Exception {
        closedRecord(ArchiveStore.sha256(PACKAGE));
        when(renderer.render(any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.exportPdf(doctor, record.getId(),
                new ExportMedicalRecordRequest("LUU_TRU", null)))
                .isInstanceOf(AppException.class)
                .extracting(exception -> ((AppException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EXPORT_FAILED);
        verify(auditLogService, never()).recordBusinessChange(any(), any(), anyString(), any(), any(), anyMap(),
                any());
        assertThat(Files.exists(root.resolve("exports"))).isFalse();
    }
}
