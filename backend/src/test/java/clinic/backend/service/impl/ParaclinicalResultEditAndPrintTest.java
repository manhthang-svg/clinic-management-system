package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.ClinicFacility;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.LabResult;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.ImagingResultImageRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.ResultImageStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** UC-67 Record Paraclinical Result (draft, images) and UC-68 Print Paraclinical Result: the guard rules. */
@ExtendWith(MockitoExtension.class)
class ParaclinicalResultEditAndPrintTest {

    private static final UUID TECHNICIAN_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-18T03:00:00Z");

    @Mock ParaclinicalOrderDetailRepository orderDetailRepository;
    @Mock MedicalServiceRepository medicalServiceRepository;
    @Mock MedicalRecordRepository medicalRecordRepository;
    @Mock VisitRepository visitRepository;
    @Mock PatientRepository patientRepository;
    @Mock LabResultRepository labResultRepository;
    @Mock ImagingResultRepository imagingResultRepository;
    @Mock ImagingResultImageRepository imageRepository;
    @Mock ResultImageStorage resultImageStorage;
    @Mock ClinicFacilityRepository clinicFacilityRepository;
    @Mock AuditLogService auditLogService;
    @Mock Clock clock;
    @InjectMocks ParaclinicalResultServiceImpl service;

    private final UUID detailId = UUID.randomUUID();
    private ParaclinicalOrderDetail detail;

    // dich vu (xet nghiem hoac hinh anh) cua 1 chi dinh; chua co ket qua
    private void serviceOfType(MedicalServiceType type, String name) {
        detail = mock(ParaclinicalOrderDetail.class);
        ParaclinicalOrder order = mock(ParaclinicalOrder.class);
        MedicalService medicalService = mock(MedicalService.class);
        MedicalRecord record = mock(MedicalRecord.class);
        UUID serviceId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        when(orderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        lenient().when(detail.getId()).thenReturn(detailId);
        when(detail.getServiceId()).thenReturn(serviceId);
        when(detail.getOrder()).thenReturn(order);
        when(medicalServiceRepository.findById(serviceId)).thenReturn(Optional.of(medicalService));
        when(medicalService.getType()).thenReturn(type);
        lenient().when(medicalService.getTenchidinh()).thenReturn(name);
        when(order.getMedicalRecordId()).thenReturn(recordId);
        when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(record.getVisitId()).thenReturn(visitId);
        Visit visit = mock(Visit.class);
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        lenient().when(visit.getPatientId()).thenReturn(UUID.randomUUID());
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private void labService() {
        serviceOfType(MedicalServiceType.LAB, "Glucose máu");
    }

    private void imagingService() {
        serviceOfType(MedicalServiceType.IMAGING, "Siêu âm ổ bụng tổng quát");
    }

    private LabResult submittedLabResult() {
        LabResult result = mock(LabResult.class);
        when(result.isSubmitted()).thenReturn(true);
        when(labResultRepository.findByOrderDetailId(detailId)).thenReturn(Optional.of(result));
        return result;
    }

    private void performedBy(UUID technicianId, boolean awaitsResult) {
        when(detail.getPerformerAccountId()).thenReturn(technicianId);
        lenient().when(detail.awaitsResultFrom(TECHNICIAN_ID)).thenReturn(awaitsResult);
    }

    private static void assertCode(Throwable thrown, ErrorCode code) {
        assertThat(thrown).isInstanceOfSatisfying(AppException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(code));
    }

    // ---- UC-67: luu nhap / anh ----

    @Test
    void refusesToReadAnotherTechniciansResultBeforeLoadingTheForm() {
        ParaclinicalOrderDetail assignedDetail = mock(ParaclinicalOrderDetail.class);
        when(orderDetailRepository.findById(detailId)).thenReturn(Optional.of(assignedDetail));
        when(assignedDetail.getPerformerAccountId()).thenReturn(UUID.randomUUID());

        assertCode(catchThrowable(() -> service.get(TECHNICIAN_ID, detailId)), ErrorCode.ACCESS_DENIED);
        verifyNoInteractions(medicalServiceRepository, patientRepository, labResultRepository, imagingResultRepository);
    }

    @Test
    void refusesToEditAResultThatWasAlreadySubmitted() {
        labService();
        when(detail.getPerformerAccountId()).thenReturn(TECHNICIAN_ID);
        submittedLabResult();

        assertCode(catchThrowable(() -> service.saveDraft(TECHNICIAN_ID, detailId, null)),
                ErrorCode.RESULT_ALREADY_SUBMITTED);
        verify(labResultRepository, never()).save(any());
    }

    @Test
    void refusesADraftFromATechnicianWhoIsNotThePerformer() {
        labService();
        performedBy(UUID.randomUUID(), true);

        assertCode(catchThrowable(() -> service.saveDraft(TECHNICIAN_ID, detailId, null)), ErrorCode.ACCESS_DENIED);
    }

    @Test
    void refusesADraftWhenTheServiceDoesNotAwaitAResult() {
        labService();
        performedBy(TECHNICIAN_ID, false);

        assertCode(catchThrowable(() -> service.saveDraft(TECHNICIAN_ID, detailId, null)),
                ErrorCode.RESULT_NOT_EDITABLE);
    }

    @Test
    void refusesAnImageOnALabResult() {
        labService();
        performedBy(TECHNICIAN_ID, true);

        Throwable thrown = catchThrowable(() -> service.uploadImage(
                TECHNICIAN_ID, detailId, "a.png", "image/png", new byte[] {1, 2, 3}, null));

        assertCode(thrown, ErrorCode.RESULT_IMAGE_INVALID);
        assertThat(((AppException) thrown).getDetails()).containsEntry("file", "LAB_RESULT");
    }

    @Test
    void refusesAFileThatIsNotReallyAJpgOrPng() {
        imagingService();
        performedBy(TECHNICIAN_ID, true);
        when(resultImageStorage.isConfigured()).thenReturn(true);

        // khai la PNG nhung noi dung khong phai PNG
        Throwable thrown = catchThrowable(() -> service.uploadImage(
                TECHNICIAN_ID, detailId, "a.png", "image/png", "not a picture".getBytes(), null));

        assertCode(thrown, ErrorCode.RESULT_IMAGE_INVALID);
        assertThat(((AppException) thrown).getDetails()).containsEntry("file", "TYPE");
        verify(resultImageStorage, never()).upload(any(), any(), any());
    }

    @Test
    void refusesAnEmptyFile() {
        imagingService();
        performedBy(TECHNICIAN_ID, true);
        when(resultImageStorage.isConfigured()).thenReturn(true);

        Throwable thrown = catchThrowable(() -> service.uploadImage(
                TECHNICIAN_ID, detailId, "a.png", "image/png", new byte[0], null));

        assertCode(thrown, ErrorCode.RESULT_IMAGE_INVALID);
        assertThat(((AppException) thrown).getDetails()).containsEntry("file", "EMPTY");
    }

    @Test
    void refusesAnImageWhenTheStorageIsNotConfigured() {
        imagingService();
        performedBy(TECHNICIAN_ID, true);
        when(resultImageStorage.isConfigured()).thenReturn(false);

        assertCode(catchThrowable(() -> service.uploadImage(
                        TECHNICIAN_ID, detailId, "a.png", "image/png", new byte[] {1}, null)),
                ErrorCode.FILE_STORAGE_NOT_CONFIGURED);
    }

    @Test
    void refusesToDeleteAnImageOfAResultThatDoesNotExist() {
        imagingService();
        performedBy(TECHNICIAN_ID, true);

        assertCode(catchThrowable(() -> service.deleteImage(TECHNICIAN_ID, detailId, UUID.randomUUID())),
                ErrorCode.RESULT_IMAGE_NOT_FOUND);
    }

    @Test
    void refusesToDeleteAnImageOfAnotherResult() {
        imagingService();
        performedBy(TECHNICIAN_ID, true);
        ImagingResult result = mock(ImagingResult.class);
        UUID resultId = UUID.randomUUID();
        when(result.getId()).thenReturn(resultId);
        when(imagingResultRepository.findByOrderDetailId(detailId)).thenReturn(Optional.of(result));
        UUID imageId = UUID.randomUUID();
        when(imageRepository.findByIdAndImagingResultId(imageId, resultId)).thenReturn(Optional.empty());

        assertCode(catchThrowable(() -> service.deleteImage(TECHNICIAN_ID, detailId, imageId)),
                ErrorCode.RESULT_IMAGE_NOT_FOUND);
        verify(imageRepository, never()).delete(any());
    }

    // ---- UC-68: in phieu ket qua ----

    @Test
    void refusesToPrintAResultThatIsNotSubmitted() {
        labService();
        when(detail.getPerformerAccountId()).thenReturn(TECHNICIAN_ID);

        assertCode(catchThrowable(() -> service.recordPrint(TECHNICIAN_ID, detailId)), ErrorCode.RESULT_NOT_SUBMITTED);
    }

    @Test
    void refusesToPrintForATechnicianWhoIsNotThePerformer() {
        labService();
        when(detail.getPerformerAccountId()).thenReturn(UUID.randomUUID());

        assertCode(catchThrowable(() -> service.recordPrint(TECHNICIAN_ID, detailId)), ErrorCode.ACCESS_DENIED);
        verify(labResultRepository, never()).findByOrderDetailId(detailId);
    }

    @Test
    void refusesToPrintBeforeTheClinicFacilityIsConfigured() {
        labService();
        submittedLabResult();
        when(detail.getPerformerAccountId()).thenReturn(TECHNICIAN_ID);
        when(clinicFacilityRepository.findCurrent()).thenReturn(Optional.empty());

        assertCode(catchThrowable(() -> service.recordPrint(TECHNICIAN_ID, detailId)),
                ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED);
    }

    @Test
    void countsThePrintAndLogsIt() {
        labService();
        LabResult result = submittedLabResult();
        when(detail.getPerformerAccountId()).thenReturn(TECHNICIAN_ID);
        when(detail.getMachidinh()).thenReturn("CD0001");
        when(clinicFacilityRepository.findCurrent()).thenReturn(Optional.of(mock(ClinicFacility.class)));

        // sau khi ghi nhan, service tai lai phieu de tra ve; phan dung phieu khong thuoc test nay
        catchThrowable(() -> service.recordPrint(TECHNICIAN_ID, detailId));

        verify(result).recordPrint(NOW);
        verify(labResultRepository).save(result);
        verify(auditLogService).recordBusinessChange(eq(TECHNICIAN_ID), eq(AuditLogAction.PRINT), any(),
                eq(detailId), eq("CD0001"), eq(Map.of("action", "PRINT_RESULT")), eq(NOW));
    }

    private static Throwable catchThrowable(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        return org.assertj.core.api.Assertions.catchThrowable(call);
    }
}
