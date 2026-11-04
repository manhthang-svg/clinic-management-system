package clinic.backend.controller;

import java.util.List;

import clinic.backend.dto.response.ApiResponse;
import clinic.backend.dto.response.LabParameterManagementResponse;
import clinic.backend.service.LabReferenceRangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static clinic.backend.config.OpenApiConfig.BEARER_SECURITY_SCHEME;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/lab-parameters")
@Tag(name = "Chỉ số xét nghiệm")
@SecurityRequirement(name = BEARER_SECURITY_SCHEME)
public class LabParameterController {

    private final LabReferenceRangeService service;

    @Operation(summary = "Danh sách chỉ số xét nghiệm kèm lịch sử khoảng tham chiếu")
    @GetMapping
    public ApiResponse<List<LabParameterManagementResponse>> list() {
        return ApiResponse.success(service.listParameters());
    }
}
