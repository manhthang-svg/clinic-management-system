package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One vital-sign measurement (II.18-II.23, III.30).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "vital_signs")
public class VitalSign extends CreatedOnlyEntity {

    @Column(name = "medical_record_id", nullable = false)
    private UUID medicalRecordId;

    @Column(name = "is_initial", nullable = false)
    private boolean isInitial;

    /** CV 365 II.18, III.30.1. */
    @Column(name = "mach")
    private Short mach;

    /** CV 365 II.19, III.30.2. */
    @Column(name = "nhietdo", precision = 4, scale = 1)
    private BigDecimal nhietdo;

    /** CV 365 II.20, III.30.5. */
    @Column(name = "nhiptho")
    private Short nhiptho;

    /** CV 365 II.21, III.30.3. */
    @Column(name = "huyetap_tamthu")
    private Short huyetapTamthu;

    /** CV 365 II.22, III.30.4. */
    @Column(name = "huyetap_tamtruong")
    private Short huyetapTamtruong;

    /** CV 365 II.23, III.30.6. */
    @Column(name = "cannang", precision = 5, scale = 2)
    private BigDecimal cannang;

    /** CV 365 II.11, III.30.7. */
    @Column(name = "chieucao", precision = 5, scale = 1)
    private BigDecimal chieucao;

    @Column(name = "spo2")
    private Short spo2;

    /** CV 365 III.30.8. */
    @Column(name = "thoidiem", nullable = false)
    private Instant thoidiem;

    @Column(name = "measured_by_account_id", nullable = false)
    private UUID measuredByAccountId;

    public VitalSign(
            UUID id,
            Instant createdAt,
            UUID medicalRecordId,
            boolean initial,
            Short pulse,
            BigDecimal temperature,
            Short respiratoryRate,
            Short systolic,
            Short diastolic,
            BigDecimal weight,
            BigDecimal height,
            Short spo2,
            Instant measuredAt,
            UUID measuredByAccountId) {
        super(id, createdAt);
        this.medicalRecordId = medicalRecordId;
        this.isInitial = initial;
        this.mach = pulse;
        this.nhietdo = temperature;
        this.nhiptho = respiratoryRate;
        this.huyetapTamthu = systolic;
        this.huyetapTamtruong = diastolic;
        this.cannang = weight;
        this.chieucao = height;
        this.spo2 = spo2;
        this.thoidiem = measuredAt;
        this.measuredByAccountId = measuredByAccountId;
    }
}
