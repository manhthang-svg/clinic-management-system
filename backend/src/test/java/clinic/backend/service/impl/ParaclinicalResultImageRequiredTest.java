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

/** UC-67: an imaging or functional result needs at least one uploaded image before it is submitted. */
@ExtendWith(MockitoExtension.class)
class ParaclinicalResultImageRequiredTest {

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
    private MedicalService medicalService;

    @BeforeEach
    void ultrasoundPerformedByTheTechnician() {
        ParaclinicalOrderDetail detail = mock(ParaclinicalOrderDetail.class);
        ParaclinicalOrder order = mock(ParaclinicalOrder.class);
        medicalService = mock(MedicalService.class);
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
        when(medicalService.getType()).thenReturn(MedicalServiceType.IMAGING);
        lenient().when(medicalService.getTenchidinh()).thenReturn("Siêu âm ổ bụng tổng quát");
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

    private static SaveParaclinicalResultRequest request(String conclusion) {
        return request(conclusion, null);
    }

    private static SaveParaclinicalResultRequest request(String conclusion, String imageLink) {
        return new SaveParaclinicalResultRequest(null, null, conclusion, null, "Gan: bình thường", null, null,
                imageLink, null, null, null);
    }

    private ImagingResult draftWithImages(int count) {
        ImagingResult draft = ImagingResult.start(UUID.randomUUID(), NOW, detailId, TECHNICIAN_ID, "PCĐ1", null);
        when(imagingResultRepository.findByOrderDetailId(detailId)).thenReturn(Optional.of(draft));
        List<ImagingResultImage> images = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> ImagingResultImage.uploaded(UUID.randomUUID(), draft.getId(), "https://x/" + i + ".png",
                        i + ".png", "image/png", "sha" + i, TECHNICIAN_ID, NOW, i + 1, null))
                .toList();
        when(imageRepository.findByImagingResultIdOrderByDisplayOrderAsc(draft.getId())).thenReturn(images);
        return draft;
    }

    private static java.util.Map<String, String> details(Throwable thrown) {
        assertThat(thrown).isInstanceOf(AppException.class);
        AppException error = (AppException) thrown;
        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
        return error.getDetails();
    }

    @Test
    @DisplayName("Gửi kết quả siêu âm khi chưa có kết quả nháp (chưa tải ảnh) thì báo thiếu ảnh")
    void refusesAnImagingResultWithoutAnyResultYet() {
        Throwable thrown = catchThrowable(() -> service.submit(TECHNICIAN_ID, detailId, request("Bình thường")));

        assertThat(details(thrown)).containsEntry("images", "Vui lòng tải lên ít nhất 1 ảnh kết quả hoặc nhập link xem ảnh");
        verify(imagingResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gửi kết quả siêu âm khi bản nháp chưa có ảnh nào thì báo thiếu ảnh")
    void refusesAnImagingResultWhoseDraftHasNoImage() {
        draftWithImages(0);

        Throwable thrown = catchThrowable(() -> service.submit(TECHNICIAN_ID, detailId, request("Bình thường")));

        assertThat(details(thrown)).containsKey("images");
        verify(imagingResultRepository, never()).save(any());
    }

    @Test
    @DisplayName("Đã có ảnh thì không báo thiếu ảnh nữa (chỉ báo các lỗi khác)")
    void acceptsTheImagesOnceOneIsUploaded() {
        draftWithImages(1);

        Throwable thrown = catchThrowable(() -> service.submit(TECHNICIAN_ID, detailId, request("")));

        assertThat(details(thrown)).doesNotContainKey("images").containsKey("conclusion");
    }

    @Test
    @DisplayName("Có link xem ảnh (PACS, CT/MRI) thì không cần tải ảnh lên")
    void acceptsAnImageLinkInsteadOfUploadedImages() {
        Throwable thrown = catchThrowable(() -> service.submit(TECHNICIAN_ID, detailId,
                request("", "https://pacs.example/xem/123")));

        assertThat(details(thrown)).doesNotContainKey("images").containsKey("conclusion");
    }

    @Test
    @DisplayName("CT/MRI: thiếu kỹ thuật chụp thì báo lỗi; có kỹ thuật thì không báo")
    void requiresTheTechniqueOfACtOrMriScan() {
        when(medicalService.getTenchidinh()).thenReturn("Chụp CT sọ não không tiêm thuốc cản quang");

        Throwable missing = catchThrowable(() -> service.submit(TECHNICIAN_ID, detailId,
                request("Bình thường", "https://pacs.example/xem/123")));
        assertThat(details(missing)).containsEntry("technique", "Vui lòng nhập kỹ thuật");

        SaveParaclinicalResultRequest withTechnique = new SaveParaclinicalResultRequest(null, null, "", "Chụp xoắn ốc",
                "Nhu mô não bình thường", null, null, "https://pacs.example/xem/123", null, null, null);
        Throwable other = catchThrowable(() -> service.submit(TECHNICIAN_ID, detailId, withTechnique));
        assertThat(details(other)).doesNotContainKey("technique").containsKey("conclusion");
    }
}
