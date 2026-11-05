package clinic.backend.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import clinic.backend.enums.AllergySeverity;
import clinic.backend.enums.MedicalHistoryCategory;
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
 * A drug-allergy row with {@code thongtinGiatri = "0"} is the "no known drug allergy" statement (UC-39 AF-01).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "medical_histories")
public class MedicalHistory extends VersionedEntity {

    public static final String PRESENT = "1";
    public static final String ABSENT = "0";
    public static final String NO_KNOWN_DRUG_ALLERGY = "Không có tiền sử dị ứng thuốc";

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

    /** CV 365 I.45.2: the history entry, or the drug / drug group of an allergy. */
    @Column(name = "thongtin_noidung", nullable = false)
    private String thongtinNoidung;

    /** CV 365 I.45.3; 1 yes, 0 no. */
    @Column(name = "thongtin_giatri", length = 100)
    private String thongtinGiatri;

    /** CV 365 I.45.4: the reaction of a drug allergy. */
    @Column(name = "thongtin_ghichu")
    private String thongtinGhichu;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 30)
    private MedicalHistoryCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private AllergySeverity severity;

    @Column(name = "updated_by_account_id", nullable = false)
    private UUID updatedByAccountId;

    private MedicalHistory(UUID id, Instant now, UUID patientId, UUID medicalRecordId,
                           MedicalHistoryType historyType, String content, String value, String note,
                           MedicalHistoryCategory category, AllergySeverity severity, UUID updatedByAccountId) {
        super(id, now);
        this.patientId = Objects.requireNonNull(patientId, "patientId");
        this.medicalRecordId = medicalRecordId;
        this.historyType = Objects.requireNonNull(historyType, "historyType");
        this.thongtinNoidung = Objects.requireNonNull(content, "content");
        this.thongtinGiatri = value;
        this.thongtinGhichu = note;
        this.category = category;
        this.severity = severity;
        this.updatedByAccountId = Objects.requireNonNull(updatedByAccountId, "updatedByAccountId");
    }

    public static MedicalHistory personal(UUID id, Instant now, UUID patientId, UUID medicalRecordId,
                                          MedicalHistoryCategory category, String content, UUID doctorId) {
        return new MedicalHistory(id, now, patientId, medicalRecordId, MedicalHistoryType.PERSONAL, content,
                PRESENT, null, Objects.requireNonNull(category, "category"), null, doctorId);
    }

    public static MedicalHistory drugAllergy(UUID id, Instant now, UUID patientId, UUID medicalRecordId,
                                             String drug, String reaction, AllergySeverity severity,
                                             UUID doctorId) {
        return new MedicalHistory(id, now, patientId, medicalRecordId, MedicalHistoryType.DRUG_ALLERGY, drug,
                PRESENT, reaction, null, severity, doctorId);
    }

    public static MedicalHistory noKnownDrugAllergy(UUID id, Instant now, UUID patientId, UUID medicalRecordId,
                                                    UUID doctorId) {
        return new MedicalHistory(id, now, patientId, medicalRecordId, MedicalHistoryType.DRUG_ALLERGY,
                NO_KNOWN_DRUG_ALLERGY, ABSENT, null, null, null, doctorId);
    }

    /** True for the "no known drug allergy" statement rather than a real allergy. */
    public boolean isNoKnownDrugAllergy() {
        return historyType == MedicalHistoryType.DRUG_ALLERGY && ABSENT.equals(thongtinGiatri);
    }

    public MedicalHistory(
            UUID id, Instant createdAt, UUID patientId, UUID medicalRecordId, MedicalHistoryType historyType,
            String content, String notes, UUID updatedByAccountId) {
        this(id, createdAt, patientId, medicalRecordId, historyType, content, null, notes, null, null,
                updatedByAccountId);
    }

    /** UC-39 during examination: only the Doctor changes history and allergies (BR-23). */
    public void update(
            MedicalHistoryType historyType, String content, String notes, UUID medicalRecordId,
            UUID updatedByAccountId, Instant now) {
        this.historyType = historyType;
        this.thongtinNoidung = content;
        this.thongtinGhichu = notes;
        this.medicalRecordId = medicalRecordId;
        this.updatedByAccountId = updatedByAccountId;
        touch(now);
    }
}
