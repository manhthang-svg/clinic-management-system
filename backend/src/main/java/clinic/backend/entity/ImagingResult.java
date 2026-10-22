package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.enums.ImagingResultStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The result of one imaging or functional service (export group VI).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "imaging_results", uniqueConstraints = {
        @UniqueConstraint(name = "uq_imaging_results_order_detail_id", columnNames = "order_detail_id"),
        @UniqueConstraint(name = "uq_imaging_results_sophieu", columnNames = "sophieu")
})
public class ImagingResult extends VersionedEntity {

    @Column(name = "order_detail_id", nullable = false)
    private UUID orderDetailId;

    @Column(name = "performer_account_id", nullable = false)
    private UUID performerAccountId;

    /** CV 365 VI.2.1. */
    @Column(name = "sophieu", nullable = false, updatable = false, length = 100)
    private String sophieu;

    /** CV 365 VI.2.3. */
    @Column(name = "ngayketqua")
    private Instant ngayketqua;

    /** CV 365 VI.2.8. */
    @Column(name = "chandoan")
    private String chandoan;

    /** CV 365 VI.2.13. */
    @Column(name = "kqcls_mota", nullable = false)
    private String kqclsMota;

    /** CV 365 VI.2.11. */
    @Column(name = "kqcls_ketluan", nullable = false)
    private String kqclsKetluan;

    /** CV 365 VI.2.10. */
    @Column(name = "kqcls_denghi")
    private String kqclsDenghi;

    /** CV 365 VI.2.12. */
    @Column(name = "kqcls_linkhinhanh", length = 1024)
    private String kqclsLinkhinhanh;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImagingResultStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;
}
