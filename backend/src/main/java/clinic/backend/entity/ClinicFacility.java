package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.enums.FacilityOrganizationForm;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "hinh_thuc_to_chuc", nullable = false, length = 50)
    private FacilityOrganizationForm hinhThucToChuc;

    // Kept for the schema; the clinic does not send prescriptions, so UC-77 does not edit it.
    @Column(name = "ma_lienthong_donthuoc", length = 50)
    private String maLienthongDonthuoc;

    public ClinicFacility(
            UUID id,
            String maCskcb,
            String tenCskcb,
            String diachi,
            String sodienthoai,
            String soGiayPhepHoatDong,
            FacilityOrganizationForm hinhThucToChuc,
            Instant createdAt) {
        super(id, createdAt);
        assign(maCskcb, tenCskcb, diachi, sodienthoai, soGiayPhepHoatDong, hinhThucToChuc);
    }

    /** UC-77: replaces the licence information; the caller has checked whether the code may change. */
    public void update(
            String maCskcb,
            String tenCskcb,
            String diachi,
            String sodienthoai,
            String soGiayPhepHoatDong,
            FacilityOrganizationForm hinhThucToChuc,
            Instant now) {
        assign(maCskcb, tenCskcb, diachi, sodienthoai, soGiayPhepHoatDong, hinhThucToChuc);
        touch(now);
    }

    private void assign(
            String maCskcb,
            String tenCskcb,
            String diachi,
            String sodienthoai,
            String soGiayPhepHoatDong,
            FacilityOrganizationForm hinhThucToChuc) {
        this.maCskcb = Objects.requireNonNull(maCskcb, "maCskcb");
        this.tenCskcb = Objects.requireNonNull(tenCskcb, "tenCskcb");
        this.diachi = Objects.requireNonNull(diachi, "diachi");
        this.sodienthoai = Objects.requireNonNull(sodienthoai, "sodienthoai");
        this.soGiayPhepHoatDong = Objects.requireNonNull(soGiayPhepHoatDong, "soGiayPhepHoatDong");
        this.hinhThucToChuc = Objects.requireNonNull(hinhThucToChuc, "hinhThucToChuc");
    }
}
