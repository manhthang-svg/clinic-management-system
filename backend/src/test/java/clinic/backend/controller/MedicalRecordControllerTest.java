package clinic.backend.controller;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import clinic.backend.dto.request.ExportMedicalRecordDataRequest;
import clinic.backend.dto.response.ExportMedicalRecordDataResponse;
import clinic.backend.dto.response.ResultFileResponse;
import clinic.backend.enums.ExportFormat;
import clinic.backend.enums.ExportLogPurpose;
import clinic.backend.service.MedicalRecordDataExportService;
import clinic.backend.service.MedicalRecordExportService;
import clinic.backend.service.MedicalRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicalRecordControllerTest {

    @Mock
    private MedicalRecordService medicalRecordService;

    @Mock
    private MedicalRecordExportService medicalRecordExportService;

    @Mock
    private MedicalRecordDataExportService medicalRecordDataExportService;

    private MedicalRecordController controller;

    @BeforeEach
    void setUp() {
        controller = new MedicalRecordController(
                medicalRecordService, medicalRecordExportService, medicalRecordDataExportService);
    }

    @Test
    @DisplayName("exportMedicalRecordData: Trả về 200 OK kèm byte array và các header Content-Disposition, X-File-SHA256")
    void exportMedicalRecordData_returnsDownloadResponse() {
        UUID recordId = UUID.randomUUID();
        UUID doctorAccountId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(doctorAccountId.toString());
        when(jwt.getClaimAsString(clinic.backend.security.jwt.AccessTokenClaims.SESSION_ID))
                .thenReturn(sessionId.toString());

        ExportMedicalRecordDataRequest request = ExportMedicalRecordDataRequest.builder()
                .format(ExportFormat.JSON)
                .purpose(ExportLogPurpose.LIEN_THONG)
                .recipient("Bệnh viện Tuyến trên")
                .build();

        byte[] fakeData = "{\"test\":\"data\"}".getBytes(StandardCharsets.UTF_8);
        String fakeSha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        ExportMedicalRecordDataResponse serviceResponse = new ExportMedicalRecordDataResponse(
                "HoSoBenhAn_BA001_DK001.json",
                "application/json; charset=UTF-8",
                fakeData,
                fakeSha256,
                ExportFormat.JSON,
                ExportLogPurpose.LIEN_THONG
        );

        when(medicalRecordDataExportService.exportMedicalRecordData(eq(recordId), eq(request), eq(doctorAccountId)))
                .thenReturn(serviceResponse);

        ResponseEntity<byte[]> responseEntity = controller.exportMedicalRecordData(recordId, request, jwt);

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment; filename=\"HoSoBenhAn_BA001_DK001.json\"");
        assertThat(responseEntity.getHeaders().getFirst("X-File-SHA256"))
                .isEqualTo(fakeSha256);
        assertThat(responseEntity.getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS))
                .contains("Content-Disposition", "X-File-SHA256");
        assertThat(responseEntity.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType("application/json; charset=UTF-8"));
        assertThat(responseEntity.getBody()).isEqualTo(fakeData);

        verify(medicalRecordDataExportService).exportMedicalRecordData(eq(recordId), eq(request), eq(doctorAccountId));
    }

    @Test
    @DisplayName("UC-52 BR-52-06: tệp kết quả trả inline, đúng loại tệp, không lưu cache")
    void downloadResultFile_returnsTheFileInlineWithoutCaching() {
        UUID recordId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID doctorAccountId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(doctorAccountId.toString());
        when(jwt.getClaimAsString(clinic.backend.security.jwt.AccessTokenClaims.SESSION_ID))
                .thenReturn(UUID.randomUUID().toString());
        byte[] content = {1, 2, 3};
        when(medicalRecordService.downloadResultFile(recordId, fileId, doctorAccountId))
                .thenReturn(new ResultFileResponse("KQ-CDHA01-1.png", "image/png", content));

        ResponseEntity<byte[]> response = controller.downloadResultFile(recordId, fileId, jwt);

        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("inline; filename=\"KQ-CDHA01-1.png\"");
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isEqualTo(content);
    }
}
