package clinic.backend.entity;

import clinic.backend.enums.ActivationStatus;
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
        @UniqueConstraint(name = "uq_medicine_catalog_mathuocvattu_bv", columnNames = "mathuocvattu_bv")
})
public class MedicineCatalog extends VersionedEntity {

    /** CV 365 IV.2.6.7. */
    @Column(name = "mathuocvattu_bv", nullable = false, length = 255)
    private String mathuocvattuBv;

    /** CV 365 IV.2.6.8. */
    @Column(name = "mathuocvattu_byt", length = 255)
    private String mathuocvattuByt;

    /** CV 365 IV.2.6.11. */
    @Column(name = "tenthuocvattu", nullable = false, length = 255)
    private String tenthuocvattu;

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
}
