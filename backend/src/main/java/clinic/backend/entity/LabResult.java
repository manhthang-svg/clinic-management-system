package clinic.backend.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.LabResultStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The result form of one lab service (export group VII).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "lab_results", uniqueConstraints = {
        @UniqueConstraint(name = "uq_lab_results_order_detail_id", columnNames = "order_detail_id"),
        @UniqueConstraint(name = "uq_lab_results_sophieu", columnNames = "sophieu")
})
public class LabResult extends VersionedEntity {

    @Column(name = "order_detail_id", nullable = false)
    private UUID orderDetailId;

    @Column(name = "performer_account_id", nullable = false)
    private UUID performerAccountId;

    /** CV 365 VII.2.1. */
    @Column(name = "sophieu", nullable = false, updatable = false, length = 100)
    private String sophieu;

    /** CV 365 VII.2.4. */
    @Column(name = "chandoan")
    private String chandoan;

    /** CV 365 VII.2.6. */
    @Column(name = "ghichu")
    private String ghichu;

    /** CV 365 VII.2.7, V.2.8. */
    @Column(name = "giolaymau")
    private Instant giolaymau;

    /** CV 365 V.2.12. */
    @Column(name = "sample_collected_by_account_id")
    private UUID sampleCollectedByAccountId;

    /** CV 365 VII.2.9. */
    @Column(name = "masobenhpham", length = 255)
    private String masobenhpham;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LabResultStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** Prints so far; the second and later prints are marked as a copy (UC-68). */
    @Column(name = "print_count", nullable = false)
    private int printCount;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "labResult", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LabResultItem> items = new ArrayList<>();

    public List<LabResultItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
