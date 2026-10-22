package clinic.backend.entity;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * An ICD-10 code from the TT 06/2026/TT-BYT list. Loaded from the published list, never edited by users.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "icd10")
public class Icd10 implements Persistable<String> {

    /** CV 365 III.19. */
    @Id
    @Column(name = "maicd", nullable = false, updatable = false, length = 10)
    private String maicd;

    /** CV 365 III.20. */
    @Column(name = "tenicd", nullable = false, length = 255)
    private String tenicd;

    @Column(name = "khong_la_benh_chinh", nullable = false)
    private boolean khongLaBenhChinh;

    @Column(name = "khong_khuyen_khich_benh_chinh", nullable = false)
    private boolean khongKhuyenKhichBenhChinh;

    @Column(name = "chi_nguyen_nhan_tu_vong", nullable = false)
    private boolean chiNguyenNhanTuVong;

    @Column(name = "hieu_luc_tu", nullable = false)
    private LocalDate hieuLucTu;

    @Column(name = "hieu_luc_den")
    private LocalDate hieuLucDen;

    @Column(name = "is_chronic", nullable = false)
    private boolean isChronic;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    @Override
    public String getId() {
        return maicd;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }
}
