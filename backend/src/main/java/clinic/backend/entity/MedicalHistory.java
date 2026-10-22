package clinic.backend.entity;

import java.util.UUID;

import clinic.backend.enums.MedicalHistoryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One personal-history or drug-allergy item of a patient (I.45, BR-23).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "medical_histories")
public class MedicalHistory extends VersionedEntity {

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "medical_record_id")
    private UUID medicalRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "history_type", nullable = false, length = 30)
    private MedicalHistoryType historyType;

    /** CV 365 I.45.1. */
    @Column(name = "thongtin_ma", length = 200)
    private String thongtinMa;

    /** CV 365 I.45.2. */
    @Column(name = "thongtin_noidung", nullable = false)
    private String thongtinNoidung;

    /** CV 365 I.45.3; 1 yes, 0 no. */
    @Column(name = "thongtin_giatri", length = 100)
    private String thongtinGiatri;

    /** CV 365 I.45.4. */
    @Column(name = "thongtin_ghichu")
    private String thongtinGhichu;

    @Column(name = "updated_by_account_id", nullable = false)
    private UUID updatedByAccountId;
}
