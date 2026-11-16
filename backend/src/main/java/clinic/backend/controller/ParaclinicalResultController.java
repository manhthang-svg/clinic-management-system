package clinic.backend.controller;

import java.io.IOException;
import java.util.UUID;

import clinic.backend.dto.request.SaveParaclinicalResultRequest;
import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.ParaclinicalResultResponse;
import clinic.backend.security.user.StaffPrincipal;
import clinic.backend.service.ParaclinicalResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * UC-67 Record Paraclinical Result and the print counter of UC-68. The path id is the ordered service
 * (paraclinical order detail), which has at most one result.
 */
@RestController
@RequestMapping("/api/v1/paraclinical-results")
@RequiredArgsConstructor
@Tag(name = "Kết quả cận lâm sàng", description = "Nhập, lưu nháp, trả và in kết quả cận lâm sàng")
public class ParaclinicalResultController {

    private final ParaclinicalResultService paraclinicalResultService;

    @GetMapping("/{orderDetailId}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "UC-67 Xem phiếu nhập kết quả của một chỉ định")
    public ApiResponse<ParaclinicalResultResponse> get(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderDetailId) {
        return ApiResponse.success(paraclinicalResultService.get(StaffPrincipal.from(jwt).accountId(), orderDetailId));
    }

    @PutMapping("/{orderDetailId}/draft")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "UC-67 AF-01 Lưu nháp kết quả")
    public ApiResponse<ParaclinicalResultResponse> saveDraft(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderDetailId,
            @Valid @RequestBody SaveParaclinicalResultRequest request) {
        return ApiResponse.success(paraclinicalResultService.saveDraft(
                StaffPrincipal.from(jwt).accountId(), orderDetailId, request));
    }

    @PostMapping("/{orderDetailId}/commands/submit")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "UC-67 Trả kết quả (khóa kết quả, BR-29)")
    public ApiResponse<ParaclinicalResultResponse> submit(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderDetailId,
            @Valid @RequestBody SaveParaclinicalResultRequest request) {
        return ApiResponse.success(paraclinicalResultService.submit(
                StaffPrincipal.from(jwt).accountId(), orderDetailId, request));
    }

    @PostMapping("/{orderDetailId}/commands/record-print")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "UC-68 Ghi nhận một lần in phiếu kết quả")
    public ApiResponse<ParaclinicalResultResponse> recordPrint(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderDetailId) {
        return ApiResponse.success(paraclinicalResultService.recordPrint(
                StaffPrincipal.from(jwt).accountId(), orderDetailId));
    }

    // tai 1 anh ket qua len Cloudinary (multipart: file, caption)
    @PostMapping(path = "/{orderDetailId}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "UC-67 Đính kèm ảnh kết quả (JPG/PNG, tối đa 10 MB, 10 ảnh)")
    public ApiResponse<ParaclinicalResultResponse> uploadImage(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID orderDetailId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String caption) throws IOException {
        return ApiResponse.success(paraclinicalResultService.uploadImage(
                StaffPrincipal.from(jwt).accountId(), orderDetailId, file.getOriginalFilename(),
                file.getContentType(), file.getBytes(), caption));
    }

    // xoa 1 anh ket qua
    @DeleteMapping("/{orderDetailId}/images/{imageId}")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Operation(summary = "UC-67 Xóa ảnh kết quả chưa gửi")
    public ApiResponse<ParaclinicalResultResponse> deleteImage(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderDetailId, @PathVariable UUID imageId) {
        return ApiResponse.success(paraclinicalResultService.deleteImage(
                StaffPrincipal.from(jwt).accountId(), orderDetailId, imageId));
    }
}
