package clinic.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.MedicineControlGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        @UniqueConstraint(name = "uq_prescription_details_machidinh", columnNames = "machidinh"),
        @UniqueConstraint(name = "uq_prescription_details_medicine", columnNames = {"prescription_id", "medicine_id"})
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
    @Column(name = "mathuocvattu_bv", length = 50)
    private String mathuocvattuBv;

    /** CV 365 IV.2.6.8. */
    @Column(name = "mathuocvattu_byt", length = 255)
    private String mathuocvattuByt;

    /** Snapshot of the catalog's registration number. */
    @Column(name = "so_dang_ky", nullable = false, length = 20)
    private String soDangKy;

    /** Snapshot of the catalog's active ingredient. */
    @Column(name = "hoatchat", nullable = false, length = 500)
    private String hoatchat;

    @Column(name = "hamluong", nullable = false)
    private String hamluong;

    @Column(name = "duongdung", nullable = false)
    private String duongdung;

    @Enumerated(EnumType.STRING)
    @Column(name = "nhom_kiem_soat", nullable = false, length = 20)
    private MedicineControlGroup nhomKiemSoat;

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

    /** UC-59: one medicine line; the catalog fields are copied so later catalog changes do not alter it. */
    public PrescriptionDetail(
            UUID id, java.time.Instant createdAt, Prescription prescription, String machidinh, MedicineCatalog medicine,
            BigDecimal quantity, String dosePerTime, short timesPerDay, short days, String usage, String note,
            LocalDate prescribedOn) {
        super(id, createdAt);
        this.prescription = prescription;
        this.machidinh = machidinh;
        this.medicineId = medicine.getId();
        this.tenthuocvattu = medicine.getTenthuocvattu();
        this.mathuocvattuBv = medicine.getMathuocvattuBv();
        this.mathuocvattuByt = medicine.getMathuocvattuByt();
        this.soDangKy = medicine.getSoDangKy();
        this.hoatchat = medicine.getHoatchat();
        this.hamluong = medicine.getHamluong();
        this.duongdung = medicine.getDuongdung();
        this.nhomKiemSoat = medicine.getNhomKiemSoat();
        this.donvitinh = medicine.getDonvitinh();
        this.nhom = medicine.getNhom();
        this.loaithuoc = medicine.getLoaithuoc();
        this.soluongthuocvattu = quantity;
        this.lieuMoiLan = dosePerTime;
        this.soLanNgay = timesPerDay;
        this.soNgayDung = days;
        this.cachdung = usage;
        this.ylenhthuocvattuGhichu = note;
        this.ngaykedonthuoc = prescribedOn;
        // An outpatient prescription exports day number 1 (see the column comment in V8).
        this.sothutungaydungthuoc = 1;
    }
}
