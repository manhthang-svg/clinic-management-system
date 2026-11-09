package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
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
    @Column(name = "ketqua", length = 255)
    private String ketqua;

    /** CV 365 VII.2.13.4. */
    @Column(name = "donvi", length = 50)
    private String donvi;

    /** CV 365 VII.2.13.11. */
    @Column(name = "trisothamchieu", length = 255)
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

    @Column(name = "service_lab_parameter_id")
    private UUID serviceLabParameterId;

    @Column(name = "numeric_value")
    private BigDecimal numericValue;

    @Column(name = "qualitative_code", length = 50)
    private String qualitativeCode;

    /** NORMAL, LOW, HIGH or ABNORMAL (V25). */
    @Column(name = "interpretation", length = 30)
    private String interpretation;

    @Column(name = "display_order_snapshot")
    private Integer displayOrderSnapshot;

    @Column(name = "section_name_snapshot", length = 100)
    private String sectionNameSnapshot;

    @Column(name = "group_name_snapshot", length = 100)
    private String groupNameSnapshot;

    /**
     * One entered value as the service writes it: the parameter snapshot, the value as typed, its parsed form and
     * its interpretation against the reference range in force.
     */
    public record Value(
            UUID id, UUID serviceLabParameterId, String code, String name, String unit, String referenceText,
            String text, BigDecimal numericValue, String qualitativeCode, String interpretation,
            int displayOrder, String sectionName, String groupName, String groupLabel,
            UUID instrumentId, String instrumentName) {
    }

    private LabResultItem(UUID id) {
        super(id);
    }

    static LabResultItem of(LabResult labResult, Value value) {
        LabResultItem item = new LabResultItem(value.id());
        item.labResult = Objects.requireNonNull(labResult, "labResult");
        item.serviceLabParameterId = value.serviceLabParameterId();
        item.machiso = value.code();
        item.pkqxnTenxetnghiem = Objects.requireNonNull(value.name(), "name");
        item.ketqua = Objects.requireNonNull(value.text(), "text");
        item.donvi = value.unit();
        item.trisothamchieu = value.referenceText();
        item.numericValue = value.numericValue();
        item.qualitativeCode = value.qualitativeCode();
        item.interpretation = value.interpretation();
        item.ketquabatthuong = value.interpretation() == null ? null
                : "NORMAL".equals(value.interpretation()) ? "0" : "1";
        item.loaixetnghiem = value.groupLabel();
        item.nhom = value.groupName();
        item.displayOrderSnapshot = value.displayOrder();
        item.sectionNameSnapshot = value.sectionName();
        item.groupNameSnapshot = value.groupName();
        item.instrumentId = value.instrumentId();
        item.instrumentNameSnapshot = value.instrumentName();
        return item;
    }

    void markResultedAt(Instant when) {
        this.ngayketqua = when;
    }
}
