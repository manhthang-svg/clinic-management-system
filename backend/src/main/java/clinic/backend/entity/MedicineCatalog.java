package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.enums.ActivationStatus;
import clinic.backend.enums.MedicineControlGroup;
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
 * A medicine that can be prescribed. No price and no stock (BR-33).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "medicine_catalog", uniqueConstraints = {
        @UniqueConstraint(name = "uq_medicine_catalog_mathuocvattu_bv", columnNames = "mathuocvattu_bv"),
        @UniqueConstraint(name = "uq_medicine_catalog_so_dang_ky", columnNames = "so_dang_ky")
})
public class MedicineCatalog extends VersionedEntity {

    /** CV 365 IV.2.6.7; BR-79: up to 50 upper-case letters, digits or hyphens. */
    @Column(name = "mathuocvattu_bv", nullable = false, length = 50)
    private String mathuocvattuBv;

    /** CV 365 IV.2.6.8: code of the TT 20/2022/TT-BYT insurance list; optional, shared by products. */
    @Column(name = "mathuocvattu_byt", length = 255)
    private String mathuocvattuByt;

    /** Registration number from the Drug Administration of Vietnam (BR-79); required and unique. */
    @Column(name = "so_dang_ky", nullable = false, length = 20)
    private String soDangKy;

    /** CV 365 IV.2.6.11. */
    @Column(name = "tenthuocvattu", nullable = false, length = 255)
    private String tenthuocvattu;

    /** Active ingredient (BR-79). */
    @Column(name = "hoatchat", nullable = false, length = 500)
    private String hoatchat;

    /** Strength, for example "500mg". */
    @Column(name = "hamluong", nullable = false)
    private String hamluong;

    /** Route of administration, for example "Uống". */
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

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ActivationStatus status;

    /** The editable fields of a medicine, already validated against BR-79 and normalized. */
    public record CatalogEntry(
            String code, String soDangKy, String maThuocBhyt, String name, String hoatchat, String hamluong,
            String duongdung, String donvitinh, MedicineControlGroup nhomKiemSoat) {
    }

    /** A new medicine is active (UC-82). */
    public MedicineCatalog(UUID id, Instant now, CatalogEntry entry) {
        super(id, now);
        this.mathuocvattuBv = Objects.requireNonNull(entry.code(), "code");
        this.status = ActivationStatus.ACTIVE;
        apply(entry);
    }

    /**
     * Updates the medicine (UC-83, or an import). The code cannot change and the status is kept; prescriptions keep
     * the values copied when they were written. Returns whether anything changed.
     */
    public boolean update(CatalogEntry entry, Instant now) {
        if (!mathuocvattuBv.equals(entry.code())) {
            throw new IllegalArgumentException("The medicine code cannot change");
        }
        if (matches(entry)) {
            return false;
        }
        apply(entry);
        touch(now);
        return true;
    }

    /** Activates or deactivates the medicine (UC-84); a deactivated medicine cannot be prescribed. */
    public boolean changeStatus(ActivationStatus newStatus, Instant now) {
        if (status == Objects.requireNonNull(newStatus, "newStatus")) {
            return false;
        }
        status = newStatus;
        touch(now);
        return true;
    }

    /** The fields of the medicine as an entry, for comparing and auditing changes. */
    public CatalogEntry toEntry() {
        return new CatalogEntry(mathuocvattuBv, soDangKy, mathuocvattuByt, tenthuocvattu, hoatchat, hamluong,
                duongdung, donvitinh, nhomKiemSoat);
    }

    private boolean matches(CatalogEntry entry) {
        return Objects.equals(soDangKy, entry.soDangKy()) && Objects.equals(mathuocvattuByt, entry.maThuocBhyt())
                && Objects.equals(tenthuocvattu, entry.name()) && Objects.equals(hoatchat, entry.hoatchat())
                && Objects.equals(hamluong, entry.hamluong()) && Objects.equals(duongdung, entry.duongdung())
                && Objects.equals(donvitinh, entry.donvitinh()) && nhomKiemSoat == entry.nhomKiemSoat();
    }

    private void apply(CatalogEntry entry) {
        soDangKy = Objects.requireNonNull(entry.soDangKy(), "soDangKy");
        mathuocvattuByt = entry.maThuocBhyt();
        tenthuocvattu = Objects.requireNonNull(entry.name(), "name");
        hoatchat = Objects.requireNonNull(entry.hoatchat(), "hoatchat");
        hamluong = Objects.requireNonNull(entry.hamluong(), "hamluong");
        duongdung = Objects.requireNonNull(entry.duongdung(), "duongdung");
        donvitinh = Objects.requireNonNull(entry.donvitinh(), "donvitinh");
        nhomKiemSoat = Objects.requireNonNull(entry.nhomKiemSoat(), "nhomKiemSoat");
    }
}
