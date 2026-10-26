
package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.OutboxEventStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "outbox_events",
        indexes = {
                @Index(
                        name = "idx_outbox_events_status_created",
                        columnList = "status, created_at"
                )
        }
)
public class OutboxEvent extends VersionedEntity {

    // Tên loại sự kiện, ví dụ: PaymentSucceeded.
    @Column(
            name = "event_type",
            nullable = false,
            length = 100,
            updatable = false
    )
    private String eventType;

    // Loại đối tượng phát sinh sự kiện, ví dụ: Payment.
    @Column(
            name = "aggregate_type",
            nullable = false,
            length = 100,
            updatable = false
    )
    private String aggregateType;

    // ID của đối tượng phát sinh sự kiện.
    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    // Nội dung sự kiện được lưu dưới dạng chuỗi JSON.
    @Column(
            name = "payload",
            nullable = false,
            columnDefinition = "text",
            updatable = false
    )
    private String payload;

    // Trạng thái gửi sự kiện.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    // Số lần thử gửi sự kiện.
    @Column(name = "attempts", nullable = false)
    private int attempts;

    // Thời điểm gửi thành công.
    @Column(name = "published_at")
    private Instant publishedAt;

    // Thời điểm được phép thử gửi lại.
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    // Lỗi của lần gửi gần nhất.
    @Column(name = "last_error", length = 2000)
    private String lastError;

    public static OutboxEvent create(
            String eventType,
            String aggregateType,
            UUID aggregateId,
            String payload
    ) {
        if (eventType == null || eventType.isBlank()
                || aggregateType == null || aggregateType.isBlank()
                || aggregateId == null
                || payload == null || payload.isBlank()) {
            throw new IllegalArgumentException(
                    "Thông tin sự kiện không hợp lệ"
            );
        }

        OutboxEvent event = new OutboxEvent();
        event.eventType = eventType;
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.payload = payload;
        event.status = OutboxEventStatus.PENDING;
        event.attempts = 0;

        return event;
    }

    // Kiểm tra sự kiện đã đến lúc được gửi hay chưa.
    public boolean canPublish() {
        return status == OutboxEventStatus.PENDING
                && (nextAttemptAt == null
                || !nextAttemptAt.isAfter(Instant.now()));
    }

    // Đánh dấu sự kiện đã được RabbitMQ xác nhận gửi thành công.
    public void markPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.nextAttemptAt = null;
        this.lastError = null;
    }

    // Ghi nhận gửi thất bại và lên lịch thử lại.
    public void markFailed(String error, Instant retryAt) {
        if (status != OutboxEventStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ sự kiện đang chờ gửi mới được ghi nhận gửi thất bại"
            );
        }

        if (retryAt == null) {
            throw new IllegalArgumentException(
                    "Thời điểm thử gửi lại không được để trống"
            );
        }

        this.attempts++;
        this.lastError = error;
        this.nextAttemptAt = retryAt;
        this.status = OutboxEventStatus.PENDING;
    }
}
