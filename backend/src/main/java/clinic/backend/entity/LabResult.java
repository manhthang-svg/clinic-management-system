package clinic.backend.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    private LabResult(UUID id, Instant now) {
        super(id, now);
    }

    /** UC-67: the result form is created on the first save, by the technician who performed the service. */
    public static LabResult start(
            UUID id, Instant now, UUID orderDetailId, UUID performerAccountId, String sophieu, String chandoan) {
        LabResult result = new LabResult(id, now);
        result.orderDetailId = Objects.requireNonNull(orderDetailId, "orderDetailId");
        result.performerAccountId = Objects.requireNonNull(performerAccountId, "performerAccountId");
        result.sophieu = Objects.requireNonNull(sophieu, "sophieu");
        result.chandoan = chandoan;
        result.status = LabResultStatus.DRAFT;
        return result;
    }

    public boolean isSubmitted() {
        return status == LabResultStatus.SUBMITTED;
    }

    /**
     * UC-67 steps 3 and AF-01: replaces the entered values while the result is a draft. Only values that were
     * entered are kept; the remark goes to ghichu ("Nhận xét" on the 17/BV2 slip).
     */
    public void record(Instant receivedAt, String remark, List<LabResultItem.Value> values, Instant now) {
        requireDraft();
        this.giolaymau = receivedAt;
        this.ghichu = remark;
        items.clear();
        values.forEach(value -> items.add(LabResultItem.of(this, value)));
        touch(now);
    }

    /** UC-67 step 9: locks the result (BR-29); the result time of every value is the submission time. */
    public void submit(Instant now) {
        requireDraft();
        status = LabResultStatus.SUBMITTED;
        submittedAt = now;
        items.forEach(item -> item.markResultedAt(now));
        touch(now);
    }

    /** UC-68: counts prints so that the second and later ones are marked as a copy. */
    public void recordPrint(Instant now) {
        if (!isSubmitted()) {
            throw new IllegalStateException("Only a submitted result can be printed");
        }
        printCount++;
        touch(now);
    }

    private void requireDraft() {
        if (isSubmitted()) {
            throw new IllegalStateException("A submitted result cannot be edited");
        }
    }
}
