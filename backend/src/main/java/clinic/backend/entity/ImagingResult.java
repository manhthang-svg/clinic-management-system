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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** "Ky thuat" line of the CT and MRI slips (V32). */
    @Column(name = "kqcls_kythuat")
    private String kqclsKythuat;

    /** Per-organ / per-site fields as a JSON object keyed by field code (V32); kqcls_mota is the printed text. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "kqcls_mota_chitiet")
    private String kqclsMotaChitiet;

    /** Specialist doctor who signs the slip (V32). */
    @Column(name = "reading_doctor_name", length = 255)
    private String readingDoctorName;

    /** Prints so far; the second and later prints are marked as a copy (UC-68). */
    @Column(name = "print_count", nullable = false)
    private int printCount;

    /** IMAGING instrument that performed the service (V54); null while the result is a draft. */
    @Column(name = "instrument_id")
    private UUID instrumentId;

    /** Instrument name when the result was submitted, so later renames do not change the slip (V54). */
    @Column(name = "instrument_name_snapshot", length = 255)
    private String instrumentNameSnapshot;
}
