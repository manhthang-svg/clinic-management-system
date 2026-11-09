package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A disease whose medicines may be prescribed for more than 30 days (TT 26/2025/TT-BYT Phụ lục VII). Loaded by
 * the long-term-disease-import profile, never edited by users.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "long_term_prescription_diseases", uniqueConstraints = {
        @UniqueConstraint(name = "uq_long_term_prescription_diseases_stt", columnNames = {"van_ban", "stt"})
})
public class LongTermPrescriptionDisease extends VersionedEntity {

    /** Row number in the appendix (column 1). */
    @Column(name = "stt", nullable = false, updatable = false)
    private int stt;

    /** Specialty number in the appendix (column 2), for example "4.13". */
    @Column(name = "ma_tt", nullable = false, length = 10)
    private String maTt;

    @Column(name = "ten_benh", nullable = false)
    private String tenBenh;

    /** ICD-10 codes as the appendix writes them; null when it lists none. */
    @Column(name = "ma_icd_goc", length = 255)
    private String maIcdGoc;

    @Column(name = "van_ban", nullable = false, updatable = false, length = 100)
    private String vanBan;

    @Column(name = "hieu_luc_tu", nullable = false)
    private LocalDate hieuLucTu;

    @Column(name = "hieu_luc_den")
    private LocalDate hieuLucDen;

    /** One row of the appendix, already validated by the importer. */
    public record ListEntry(int stt, String maTt, String tenBenh, String maIcdGoc) {
    }

    public LongTermPrescriptionDisease(
            UUID id, Instant now, ListEntry entry, String vanBan, LocalDate effectiveFrom) {
        super(id, now);
        this.stt = entry.stt();
        this.vanBan = Objects.requireNonNull(vanBan, "vanBan");
        this.hieuLucTu = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        apply(entry);
    }

    /**
     * Updates the row from the same document's list. A row that comes back after being retired is in effect
     * again. Returns whether anything changed.
     */
    public boolean updateFromList(ListEntry entry, Instant now) {
        if (matches(entry) && hieuLucDen == null) {
            return false;
        }
        apply(entry);
        hieuLucDen = null;
        touch(now);
        return true;
    }

    /** The row is no longer in the list: in effect until the day before the new list takes effect. */
    public boolean retire(LocalDate newListEffectiveFrom, Instant now) {
        if (hieuLucDen != null) {
            return false;
        }
        LocalDate lastDay = newListEffectiveFrom.minusDays(1);
        hieuLucDen = lastDay.isBefore(hieuLucTu) ? hieuLucTu : lastDay;
        touch(now);
        return true;
    }

    /** Whether the row allows a prescription of more than 30 days on that day. */
    public boolean isInEffectOn(LocalDate day) {
        return !hieuLucTu.isAfter(day) && (hieuLucDen == null || !hieuLucDen.isBefore(day));
    }

    private boolean matches(ListEntry entry) {
        return maTt.equals(entry.maTt()) && tenBenh.equals(entry.tenBenh())
                && Objects.equals(maIcdGoc, entry.maIcdGoc());
    }

    private void apply(ListEntry entry) {
        maTt = Objects.requireNonNull(entry.maTt(), "maTt");
        tenBenh = Objects.requireNonNull(entry.tenBenh(), "tenBenh");
        maIcdGoc = entry.maIcdGoc();
    }
}
