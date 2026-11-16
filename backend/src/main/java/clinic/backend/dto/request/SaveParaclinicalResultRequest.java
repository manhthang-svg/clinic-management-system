package clinic.backend.dto.request;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * UC-67 steps 3 and 5: the result form as the technician filled it, for a draft or a submission.
 * Lab results use {@code receivedAt}, {@code values} (lab parameter code → value as typed) and {@code conclusion}
 * ("Nhận xét"). Imaging and functional results use the descriptive fields. Fields of the other kind are ignored.
 * ASSUMPTION: the length limits follow BR-99 free-text limits until the SRS states them for UC-67.
 */
public record SaveParaclinicalResultRequest(
        Instant receivedAt,

        @Size(max = 200, message = "Tối đa 200 chỉ số")
        Map<@NotBlank @Size(max = 50) String, @Size(max = 255, message = "Kết quả không được vượt quá 255 ký tự") String> values,

        @Size(max = 2000, message = "Kết luận không được vượt quá 2.000 ký tự")
        String conclusion,

        @Size(max = 4000, message = "Kỹ thuật không được vượt quá 4.000 ký tự")
        String technique,

        @Size(max = 8000, message = "Mô tả không được vượt quá 8.000 ký tự")
        String description,

        @Size(max = 50, message = "Tối đa 50 mục mô tả")
        Map<@NotBlank @Size(max = 50) String, @Size(max = 2000, message = "Mỗi mục mô tả không được vượt quá 2.000 ký tự") String> sections,

        @Size(max = 1000, message = "Lời dặn không được vượt quá 1.000 ký tự")
        String recommendation,

        @Size(max = 1024, message = "Link xem ảnh không được vượt quá 1.024 ký tự")
        @Pattern(regexp = "^$|^https?://\\S+$", message = "Link xem ảnh không đúng định dạng")
        String imageLink,

        @Size(max = 255, message = "Tên bác sĩ đọc kết quả không được vượt quá 255 ký tự")
        String readingDoctor,

        UUID instrumentId,

        // chu thich anh: id anh -> chu thich (anh da tai len rieng qua /images)
        @Size(max = 10, message = "Tối đa 10 ảnh")
        Map<UUID, @Size(max = 255, message = "Chú thích ảnh không được vượt quá 255 ký tự") String> imageCaptions
) {
}
