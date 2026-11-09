package clinic.backend.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One measured lab value (VII.2.13).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "lab_result_items")
public class LabResultItem extends UuidEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_result_id", nullable = false, updatable = false)
    private LabResult labResult;

    /** CV 365 VII.2.13.2. */
    @Column(name = "machiso", length = 255)
    private String machiso;

    /** CV 365 VII.2.13.10. */
    @Column(name = "pkqxn_tenxetnghiem", nullable = false, length = 255)
    private String pkqxnTenxetnghiem;

    /** CV 365 VII.2.13.5. */
    @Column(name = "ketqua", nullable = false, length = 255)
    private String ketqua;

    /** CV 365 VII.2.13.4. */
    @Column(name = "donvi", length = 50)
    private String donvi;

    /** CV 365 VII.2.13.11. */
    @Column(name = "trisothamchieu", length = 50)
    private String trisothamchieu;

    /** CV 365 VII.2.13.6; 1 abnormal, 0 normal. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "ketquabatthuong", length = 1)
    private String ketquabatthuong;

    /** CV 365 VII.2.13.7. */
    @Column(name = "loaixetnghiem", length = 255)
    private String loaixetnghiem;

    /** CV 365 VII.2.13.8. */
    @Column(name = "nhom", length = 255)
    private String nhom;

    /** CV 365 VII.2.13.3. */
    @Column(name = "ngayketqua")
    private Instant ngayketqua;

    /** LAB instrument that ran this value (V54); null while the result is a draft. */
    @Column(name = "instrument_id")
    private UUID instrumentId;

    /** Instrument name when the result was submitted, so later renames do not change the slip (V54). */
    @Column(name = "instrument_name_snapshot", length = 255)
    private String instrumentNameSnapshot;
}
