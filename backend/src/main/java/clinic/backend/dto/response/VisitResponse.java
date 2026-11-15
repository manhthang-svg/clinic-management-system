
package clinic.backend.dto.response;

import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VisitResponse(
        UUID id,
        String sovaovien,
        UUID patientId,
        String patientName,
        String patientCode,
        String patientDob,
        String patientGender,
        String patientPhone,
        UUID roomId,
        String roomName,
        UUID doctorAccountId,
        UUID registeredByAccountId,
        Instant thoigianvaovien,
        Instant denkhambenhluc,
        VisitDoituongbnLoai doituongbnLoai,
        String mabhyt,
        LocalDate tungaybhyt,
        LocalDate denngaybhyt,
        String noidangkykcbbd,
        String lydotiepnhan,
        String noigioithieuLoai,
        String chandoancuanoigioithieu,
        String lydovaovien,
        boolean isPriority,
        String priorityReason,
        LocalDate queueDate,
        Integer queueNumber,
        VisitStatus status,
        boolean isSkipped,
        Instant skippedAt,
        Instant completedAt
) {
}

