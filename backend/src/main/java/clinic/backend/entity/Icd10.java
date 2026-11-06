package clinic.backend.entity;

import java.time.LocalDate;
import java.util.Objects;

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
 * An ICD-10 code from the TT 06/2026/TT-BYT list. Loaded from the published list by the icd10-import profile,
 * never edited by users. A code that leaves the list is retired, never deleted, because records refer to it.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "icd10")
public class Icd10 implements Persistable<String> {

    public static final String DAGGER = "DAO_GAM";
    public static final String ASTERISK = "DAU_SAO";

    /** CV 365 III.19. */
    @Id
    @Column(name = "maicd", nullable = false, updatable = false, length = 10)
    private String maicd;

    /** CV 365 III.20. */
    @Column(name = "tenicd", nullable = false, length = 255)
    private String tenicd;

    /** Column 24: never a main diagnosis. */
    @Column(name = "khong_la_benh_chinh", nullable = false)
    private boolean khongLaBenhChinh;

    /** Column 25: preferably not a main diagnosis. */
    @Column(name = "khong_khuyen_khich_benh_chinh", nullable = false)
    private boolean khongKhuyenKhichBenhChinh;

    /** Column 27: only as a cause of death. */
    @Column(name = "chi_nguyen_nhan_tu_vong", nullable = false)
    private boolean chiNguyenNhanTuVong;

    @Column(name = "hieu_luc_tu", nullable = false)
    private LocalDate hieuLucTu;

    @Column(name = "hieu_luc_den")
    private LocalDate hieuLucDen;

    /** A chronic disease that needs follow-up (BR-34); set by the clinic, not by the BYT list. */
    @Column(name = "is_chronic", nullable = false)
    private boolean isChronic;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "ma_cha", length = 10)
    private String maCha;

    /** Column 26: a more detailed code exists and must be used instead. */
    @Column(name = "co_ma_chi_tiet_hon", nullable = false)
    private boolean coMaChiTietHon;

    /** Columns 28 and 29: 1 male only, 2 female only, null for both sexes. */
    @Column(name = "gioi_tinh_ap_dung")
    private Short gioiTinhApDung;

    /** {@link #DAGGER} or {@link #ASTERISK}, from column 18. */
    @Column(name = "ky_hieu", length = 10)
    private String kyHieu;

    @Column(name = "ghi_chu")
    private String ghiChu;

    @Column(name = "van_ban", length = 100)
    private String vanBan;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean isNew = true;

    /** One row of a published ICD-10 list, already validated by the importer. */
    public record CatalogEntry(
            String code, String name, String parentCode, boolean notMainDiagnosis,
            boolean mainDiagnosisDiscouraged, boolean moreDetailedCodeExists, boolean causeOfDeathOnly,
            Short sex, String mark, String note) {
    }

    public static Icd10 fromCatalog(CatalogEntry entry, LocalDate effectiveFrom, String source) {
        Icd10 code = new Icd10();
        code.maicd = Objects.requireNonNull(entry.code(), "code");
        code.hieuLucTu = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        code.apply(entry, source);
        return code;
    }

    /**
     * Updates the row from a newer list. The original effective date and the clinic's chronic flag are kept; a
     * code that comes back after being retired is active again. Returns whether anything changed.
     */
    public boolean updateFromCatalog(CatalogEntry entry, String source) {
        if (matches(entry, source) && isActive && hieuLucDen == null) {
            return false;
        }
        apply(entry, source);
        return true;
    }

    /** The code is no longer in the list: usable until the day before the new list takes effect. */
    public boolean retire(LocalDate newListEffectiveFrom) {
        if (!isActive) {
            return false;
        }
        LocalDate lastDay = newListEffectiveFrom.minusDays(1);
        hieuLucDen = lastDay.isBefore(hieuLucTu) ? hieuLucTu : lastDay;
        isActive = false;
        return true;
    }

    /** Whether the code may be selected for a visit on that day. */
    public boolean isUsableOn(LocalDate day) {
        return isActive && !hieuLucTu.isAfter(day) && (hieuLucDen == null || !hieuLucDen.isBefore(day));
    }

    /**
     * Whether a sex-specific code applies to a patient of that sex (1 male, 2 female, 3 undetermined).
     * ASSUMPTION: an undetermined sex is not rejected, since there is nothing to check it against.
     */
    public boolean appliesToSex(short patientSex) {
        return gioiTinhApDung == null || (patientSex != 1 && patientSex != 2) || gioiTinhApDung == patientSex;
    }

    private void apply(CatalogEntry entry, String source) {
        tenicd = Objects.requireNonNull(entry.name(), "name");
        maCha = entry.parentCode();
        khongLaBenhChinh = entry.notMainDiagnosis();
        khongKhuyenKhichBenhChinh = entry.mainDiagnosisDiscouraged();
        coMaChiTietHon = entry.moreDetailedCodeExists();
        chiNguyenNhanTuVong = entry.causeOfDeathOnly();
        gioiTinhApDung = entry.sex();
        kyHieu = entry.mark();
        ghiChu = entry.note();
        vanBan = source;
        isActive = true;
        hieuLucDen = null;
    }

    private boolean matches(CatalogEntry entry, String source) {
        return Objects.equals(tenicd, entry.name()) && Objects.equals(maCha, entry.parentCode())
                && khongLaBenhChinh == entry.notMainDiagnosis()
                && khongKhuyenKhichBenhChinh == entry.mainDiagnosisDiscouraged()
                && coMaChiTietHon == entry.moreDetailedCodeExists()
                && chiNguyenNhanTuVong == entry.causeOfDeathOnly()
                && Objects.equals(gioiTinhApDung, entry.sex()) && Objects.equals(kyHieu, entry.mark())
                && Objects.equals(ghiChu, entry.note()) && Objects.equals(vanBan, source);
    }

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
