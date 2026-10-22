package clinic.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The single row describing this clinic: facility code, name and licences printed on documents.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "clinic_facility", uniqueConstraints = {
        @UniqueConstraint(name = "uq_clinic_facility_ma_cskcb", columnNames = "ma_cskcb")
})
public class ClinicFacility extends VersionedEntity {

    @Column(name = "ma_cskcb", nullable = false, length = 5)
    private String maCskcb;

    @Column(name = "ten_cskcb", nullable = false, length = 255)
    private String tenCskcb;

    @Column(name = "diachi", nullable = false, length = 1024)
    private String diachi;

    @Column(name = "sodienthoai", nullable = false, length = 15)
    private String sodienthoai;

    @Column(name = "so_giay_phep_hoat_dong", nullable = false, length = 100)
    private String soGiayPhepHoatDong;

    @Column(name = "hinh_thuc_to_chuc", nullable = false, length = 50)
    private String hinhThucToChuc;

    @Column(name = "ma_lienthong_donthuoc", length = 50)
    private String maLienthongDonthuoc;
}
