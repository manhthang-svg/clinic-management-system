package clinic.backend.service.impl;

import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Visit;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The Doctor's read-only view of a submitted paraclinical result ("Danh sách đã chỉ định"). */
@ExtendWith(MockitoExtension.class)
class ParaclinicalResultDoctorViewTest {

    private static final UUID DOCTOR_ID = UUID.randomUUID();

    @Mock ParaclinicalOrderDetailRepository orderDetailRepository;
    @Mock MedicalServiceRepository medicalServiceRepository;
    @Mock MedicalRecordRepository medicalRecordRepository;
    @Mock VisitRepository visitRepository;
    @Mock PatientRepository patientRepository;
    @InjectMocks ParaclinicalResultServiceImpl service;

    private final UUID detailId = UUID.randomUUID();
    private ParaclinicalOrderDetail detail;
    private ParaclinicalOrder order;
    private Visit visit;

    @BeforeEach
    void setUp() {
        detail = mock(ParaclinicalOrderDetail.class);
        order = mock(ParaclinicalOrder.class);
        visit = mock(Visit.class);
        MedicalService medicalService = mock(MedicalService.class);
        MedicalRecord record = mock(MedicalRecord.class);
        UUID serviceId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();
        UUID visitId = UUID.randomUUID();

        when(orderDetailRepository.findById(detailId)).thenReturn(Optional.of(detail));
        when(detail.getServiceId()).thenReturn(serviceId);
        when(detail.getOrder()).thenReturn(order);
        when(medicalServiceRepository.findById(serviceId)).thenReturn(Optional.of(medicalService));
        when(medicalService.getType()).thenReturn(MedicalServiceType.LAB);
        when(order.getMedicalRecordId()).thenReturn(recordId);
        when(medicalRecordRepository.findById(recordId)).thenReturn(Optional.of(record));
        when(record.getVisitId()).thenReturn(visitId);
        when(visitRepository.findById(visitId)).thenReturn(Optional.of(visit));
        lenient().when(visit.getPatientId()).thenReturn(UUID.randomUUID());
    }

    @Test
    void refusesAnotherDoctorsVisit() {
        when(order.getDoctorAccountId()).thenReturn(UUID.randomUUID());
        when(visit.getDoctorAccountId()).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> service.viewForDoctor(DOCTOR_ID, detailId))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }

    @Test
    void refusesAResultThatIsNotSubmittedYet() {
        when(order.getDoctorAccountId()).thenReturn(DOCTOR_ID);
        when(detail.getStatus()).thenReturn(ParaclinicalOrderDetailStatus.PENDING_RESULT);

        assertThatThrownBy(() -> service.viewForDoctor(DOCTOR_ID, detailId))
                .isInstanceOfSatisfying(AppException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.RESULT_NOT_SUBMITTED));
    }
}
