package clinic.backend.entity;

import java.time.Instant;

import clinic.backend.enums.MedicalRecordTypePhamVi;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A medical-record form from Appendix XXVIII of TT 32/2023/TT-BYT, for example 15/BV1.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "medical_record_types")
public class MedicalRecordType {

    /** CV 365 I.1. */
    @Id
    @Column(name = "loaiba", nullable = false, updatable = false, length = 20)
    private String loaiba;

    @Column(name = "ten", nullable = false, length = 255)
    private String ten;

    @Enumerated(EnumType.STRING)
    @Column(name = "pham_vi", nullable = false, length = 20)
    private MedicalRecordTypePhamVi phamVi;

    @Column(name = "van_ban", nullable = false, length = 50)
    private String vanBan;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // A null version marks a new entity for Spring Data.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
