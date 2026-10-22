package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One medicine line of a prescription (IV.2.6).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "prescription_details", uniqueConstraints = {
        @UniqueConstraint(name = "uq_prescription_details_machidinh", columnNames = "machidinh")
})
public class PrescriptionDetail extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false, updatable = false)
    private Prescription prescription;

    @Column(name = "medicine_id")
    private UUID medicineId;

    /** CV 365 IV.2.6.1. */
    @Column(name = "machidinh", nullable = false, updatable = false, length = 255)
    private String machidinh;

    /** CV 365 IV.2.6.11. */
    @Column(name = "tenthuocvattu", nullable = false, length = 255)
    private String tenthuocvattu;

    /** CV 365 IV.2.6.7. */
    @Column(name = "mathuocvattu_bv", length = 255)
    private String mathuocvattuBv;

    /** CV 365 IV.2.6.8. */
    @Column(name = "mathuocvattu_byt", length = 255)
    private String mathuocvattuByt;

    /** CV 365 IV.2.6.4. */
    @Column(name = "donvitinh", nullable = false, length = 50)
    private String donvitinh;

    /** CV 365 IV.2.6.5. */
    @Column(name = "nhom", length = 255)
    private String nhom;

    /** CV 365 IV.2.6.6. */
    @Column(name = "loaithuoc")
    private Short loaithuoc;

    /** CV 365 IV.2.6.9. */
    @Column(name = "soluongthuocvattu", nullable = false, precision = 10, scale = 2)
    private BigDecimal soluongthuocvattu;

    /** CV 365 IV.2.6.2. */
    @Column(name = "cachdung", nullable = false, length = 255)
    private String cachdung;

    @Column(name = "lieu_moi_lan", nullable = false, length = 50)
    private String lieuMoiLan;

    @Column(name = "so_lan_ngay", nullable = false)
    private short soLanNgay;

    @Column(name = "so_ngay_dung", nullable = false)
    private short soNgayDung;

    /** CV 365 IV.2.6.3. */
    @Column(name = "ngaykedonthuoc", nullable = false)
    private LocalDate ngaykedonthuoc;

    /** CV 365 IV.2.6.10. */
    @Column(name = "sothutungaydungthuoc")
    private Integer sothutungaydungthuoc;

    /** CV 365 IV.2.6.12. */
    @Column(name = "ylenhthuocvattu_ghichu")
    private String ylenhthuocvattuGhichu;
}
