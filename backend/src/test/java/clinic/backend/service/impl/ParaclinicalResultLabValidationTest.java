package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.dto.request.SaveParaclinicalResultRequest;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.ImagingResultImage;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ImagingResultImageRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-67: the sample received time of a lab result cannot be in the future. */
@ExtendWith(MockitoExtension.class)
class ParaclinicalResultLabValidationTest {

    private static final UUID TECHNICIAN_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-11-23T03:00:00Z");

    @Mock ParaclinicalOrderDetailRepository orderDetailRepository;
    @Mock MedicalServiceRepository medicalServiceRepository;
    @Mock MedicalRecordRepository medicalRecordRepository;
    @Mock VisitRepository visitRepository;
    @Mock PatientRepository patientRepository;
    @Mock LabResultRepository labResultRepository;
    @Mock ImagingResultRepository imagingResultRepository;
    @Mock ImagingResultImageRepository imageRepository;
    @Mock Clock clock;
    @InjectMocks ParaclinicalResultServiceImpl service;

    private final UUID detailId = UUID.randomUUID();

    @BeforeEach
    void labServicePerformedByTheTechnician() {
        ParaclinicalOrderDetail detail = mock(ParaclinicalOrderDetail.class);
        ParaclinicalOrder order = mock(ParaclinicalOrder.class);
        MedicalService medicalService = mock(MedicalService.class);
        MedicalRecord record = mock(MedicalRecord.class);
        Visit visit = mock(Visit.class);
        UUID serviceId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();
        when(orderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        lenient().when(detail.getId()).thenReturn(detailId);
        when(detail.getServiceId()).thenReturn(serviceId);
        when(detail.getOrder()).thenReturn(order);
        when(medicalServiceRepository.findById(serviceId)).thenReturn(Optional.of(medicalService));
        when(medicalService.getType()).thenReturn(MedicalServiceType.LAB);
        lenient().when(medicalService.getTenchidinh()).thenReturn("Glucose máu");
        when(order.getMedicalRecordId()).thenReturn(recordId);
        when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(record.getVisitId()).thenReturn(visitId);
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        UUID patientId = UUID.randomUUID();
        lenient().when(visit.getPatientId()).thenReturn(patientId);
        lenient().when(patientRepository.findById(patientId)).thenReturn(Optional.of(mock(Patient.class)));
        when(detail.getPerformerAccountId()).thenReturn(TECHNICIAN_ID);
        when(detail.awaitsResultFrom(TECHNICIAN_ID)).thenReturn(true);
        lenient().when(clock.instant()).thenReturn(NOW);
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    private static SaveParaclinicalResultRequest received(Instant receivedAt) {
        return new SaveParaclinicalResultRequest(receivedAt, null, "", null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("Giờ nhận mẫu ở tương lai thì không lưu được, kể cả lưu nháp")
    void refusesASampleReceivedInTheFuture() {
        Throwable thrown = catchThrowable(() -> service.saveDraft(TECHNICIAN_ID, detailId,
                received(NOW.plusSeconds(3600))));

        assertThat(thrown).isInstanceOfSatisfying(AppException.class, error -> {
            assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
            assertThat(error.getDetails()).containsKey("receivedAt");
        });
        verify(labResultRepository, never()).save(any());
    }
}
