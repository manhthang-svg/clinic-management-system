package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "service_lab_parameters")
public class ServiceLabParameter extends VersionedEntity {

    @Column(name = "service_id", nullable = false, updatable = false)
    private UUID serviceId;

    @Column(name = "parameter_id", nullable = false, updatable = false)
    private UUID parameterId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Section printed on the slip, for example XÉT NGHIỆM HUYẾT HỌC. */
    @Column(name = "section_name", length = 100)
    private String sectionName;

    /** Parent group, for example Tổng phân tích tế bào máu ngoại vi. */
    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(name = "is_required", nullable = false)
    private boolean required;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    private ServiceLabParameter(
            UUID id, Instant now, UUID serviceId, UUID parameterId, int displayOrder) {
        super(id, now);
        if (displayOrder < 1) {
            throw new IllegalArgumentException("displayOrder must be positive");
        }
        this.serviceId = Objects.requireNonNull(serviceId, "serviceId");
        this.parameterId = Objects.requireNonNull(parameterId, "parameterId");
        this.displayOrder = displayOrder;
        this.required = true;
        this.active = true;
    }

    public static ServiceLabParameter create(
            UUID id, Instant now, UUID serviceId, UUID parameterId, int displayOrder) {
        return new ServiceLabParameter(id, now, serviceId, parameterId, displayOrder);
    }
}
