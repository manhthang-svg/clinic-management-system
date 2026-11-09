package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import clinic.backend.enums.PrescriptionLienthongStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A prescription (export group IV, TT 26/2025/TT-BYT).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "prescriptions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_prescriptions_sophieu", columnNames = "sophieu"),
        @UniqueConstraint(name = "uq_prescriptions_madonthuoc", columnNames = "madonthuoc"),
        @UniqueConstraint(name = "uq_prescriptions_medical_record_id", columnNames = "medical_record_id")
})
public class Prescription extends VersionedEntity {

    /** CV 365 IV.2.1. */
    @Column(name = "sophieu", nullable = false, updatable = false, length = 100)
    private String sophieu;

    @Column(name = "madonthuoc", nullable = false, updatable = false, length = 14)
    private String madonthuoc;

    /** Always "c", an ordinary prescription (BR-52). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "loaidon", nullable = false, length = 1)
    private String loaidon;

    @Column(name = "medical_record_id", nullable = false)
    private UUID medicalRecordId;

    @Column(name = "doctor_account_id", nullable = false)
    private UUID doctorAccountId;

    /** CV 365 IV.2.4. */
    @Column(name = "chandoansobo")
    private String chandoansobo;

    /** CV 365 IV.2.5; 1 insurance, 2 self-paid. */
    @Column(name = "doituongbn", nullable = false)
    private short doituongbn;

    @Column(name = "prescribed_at", nullable = false)
    private Instant prescribedAt;

    @Column(name = "printed_at")
    private Instant printedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "lienthong_status", nullable = false, length = 20)
    private PrescriptionLienthongStatus lienthongStatus;

    @Column(name = "lienthong_sent_at")
    private Instant lienthongSentAt;

    /** Notes on the prescription (BR-107). */
    @Column(name = "luu_y", length = 2000)
    private String luuY;

    /** Advice on the prescription (BR-107). */
    @Column(name = "loi_dan", length = 2000)
    private String loiDan;

    /** Follow-up date copied from the appointment when sent (BR-54). */
    @Column(name = "ngay_tai_kham")
    private LocalDate ngayTaiKham;

    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescriptionDetail> details = new ArrayList<>();

    /** Conclusion diagnoses copied when sent; thu_tu 1 is the main diagnosis. */
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "prescription", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("thuTu")
    private List<PrescriptionDiagnosis> diagnoses = new ArrayList<>();

    public List<PrescriptionDetail> getDetails() {
        return Collections.unmodifiableList(details);
    }

    public List<PrescriptionDiagnosis> getDiagnoses() {
        return Collections.unmodifiableList(diagnoses);
    }
}
