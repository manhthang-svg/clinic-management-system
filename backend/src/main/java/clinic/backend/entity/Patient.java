package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A patient profile (export group I). Insurance card data is stored per visit.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "patients", uniqueConstraints = {
        @UniqueConstraint(name = "uq_patients_mabenhnhan", columnNames = "mabenhnhan"),
        @UniqueConstraint(name = "uq_patients_cccd_so", columnNames = "cccd_so")
})
public class Patient extends VersionedEntity {

    /** CV 365 I.8. */
    @Column(name = "mabenhnhan", nullable = false, updatable = false, length = 255)
    private String mabenhnhan;

    /** CV 365 I.11. */
    @Column(name = "cccd_so", length = 12)
    private String cccdSo;

    /** CV 365 I.12. */
    @Column(name = "hochieu_so", length = 15)
    private String hochieuSo;

    /** CV 365 I.13. */
    @Column(name = "hoten", nullable = false, length = 255)
    private String hoten;

    /** CV 365 I.14. */
    @Column(name = "ngaysinh", nullable = false)
    private LocalDate ngaysinh;

    /** CV 365 I.16; 1 male, 2 female, 3 undetermined. */
    @Column(name = "gioitinh", nullable = false)
    private short gioitinh;

    /** CV 365 I.17. */
    @Column(name = "nghenghiep", length = 100)
    private String nghenghiep;

    /** CV 365 I.18. */
    @Column(name = "nghenghiep_ma", length = 5)
    private String nghenghiepMa;

    /** CV 365 I.19. */
    @Column(name = "dantoc", nullable = false, length = 50)
    private String dantoc;

    /** CV 365 I.20. */
    @Column(name = "dantoc_ma", nullable = false, length = 2)
    private String dantocMa;

    /** CV 365 I.21. */
    @Column(name = "ngoaikieu", length = 255)
    private String ngoaikieu;

    /** CV 365 I.22. */
    @Column(name = "ngoaikieu_ma", length = 2)
    private String ngoaikieuMa;

    /** CV 365 I.27. */
    @Column(name = "diachi", nullable = false, length = 1024)
    private String diachi;

    /** CV 365 I.30. */
    @Column(name = "xaphuong", length = 1024)
    private String xaphuong;

    @Column(name = "xaphuong_ma", length = 5)
    private String xaphuongMa;

    /** CV 365 I.33. */
    @Column(name = "tinhthanh", length = 1024)
    private String tinhthanh;

    /** CV 365 I.34. */
    @Column(name = "tinhthanh_ma", length = 3)
    private String tinhthanhMa;

    /** CV 365 I.35. */
    @Column(name = "noilamviec", length = 1024)
    private String noilamviec;

    /** CV 365 I.36. */
    @Column(name = "sodienthoai", length = 15)
    private String sodienthoai;

    /** CV 365 I.38. */
    @Column(name = "yeutorh", length = 5)
    private String yeutorh;

    /** CV 365 I.37. */
    @Column(name = "nhommau", length = 5)
    private String nhommau;

    /** CV 365 I.40. */
    @Column(name = "hotennguoithan", length = 255)
    private String hotennguoithan;

    @Column(name = "nguoithan_quanhe", length = 50)
    private String nguoithanQuanhe;

    /** CV 365 I.41. */
    @Column(name = "diachinguoithan", length = 1024)
    private String diachinguoithan;

    /** CV 365 I.42. */
    @Column(name = "sodienthoainguoithan", length = 15)
    private String sodienthoainguoithan;

    @Column(name = "created_by_account_id", nullable = false)
    private UUID createdByAccountId;


    public Patient(
            UUID id,
            Instant createdAt,
            String mabenhnhan,
            String cccdSo,
            String hochieuSo,
            String hoten,
            LocalDate ngaysinh,
            short gioitinh,
            String nghenghiep,
            String nghenghiepMa,
            String dantoc,
            String dantocMa,
            String ngoaikieu,
            String ngoaikieuMa,
            String diachi,
            String xaphuong,
            String xaphuongMa,
            String tinhthanh,
            String tinhthanhMa,
            String noilamviec,
            String sodienthoai,
            String yeutorh,
            String nhommau,
            String hotennguoithan,
            String nguoithanQuanhe,
            String diachinguoithan,
            String sodienthoainguoithan,
            UUID createdByAccountId
    ) {
        super(id, createdAt);

        this.mabenhnhan = mabenhnhan;
        this.cccdSo = cccdSo;
        this.hochieuSo = hochieuSo;
        this.hoten = hoten;
        this.ngaysinh = ngaysinh;
        this.gioitinh = gioitinh;
        this.nghenghiep = nghenghiep;
        this.nghenghiepMa = nghenghiepMa;
        this.dantoc = dantoc;
        this.dantocMa = dantocMa;
        this.ngoaikieu = ngoaikieu;
        this.ngoaikieuMa = ngoaikieuMa;
        this.diachi = diachi;
        this.xaphuong = xaphuong;
        this.xaphuongMa = xaphuongMa;
        this.tinhthanh = tinhthanh;
        this.tinhthanhMa = tinhthanhMa;
        this.noilamviec = noilamviec;
        this.sodienthoai = sodienthoai;
        this.yeutorh = yeutorh;
        this.nhommau = nhommau;
        this.hotennguoithan = hotennguoithan;
        this.nguoithanQuanhe = nguoithanQuanhe;
        this.diachinguoithan = diachinguoithan;
        this.sodienthoainguoithan = sodienthoainguoithan;
        this.createdByAccountId = createdByAccountId;
    }
}
