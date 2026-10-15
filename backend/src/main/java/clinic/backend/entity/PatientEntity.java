package clinic.backend.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import clinic.backend.enums.Gender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "patients")
public class PatientEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "patient_code", nullable = false, updatable = false, length = 20)
    private String patientCode;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 20)
    private Gender gender;

    @Column(name = "citizen_id", length = 12)
    private String citizenId;

    @Column(name = "phone_number", length = 10)
    private String phoneNumber;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "occupation")
    private String occupation;

    @Column(name = "ethnicity", nullable = false)
    private String ethnicity;

    @Column(name = "nationality", nullable = false)
    private String nationality;

    @Column(name = "guardian_name", length = 100)
    private String guardianName;

    @Column(name = "guardian_phone_number", length = 10)
    private String guardianPhoneNumber;

    @Column(name = "guardian_relationship")
    private String guardianRelationship;

    @Column(name = "created_by_account_id", nullable = false, updatable = false)
    private UUID createdByAccountId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // A null version marks a new entity for Spring Data.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected PatientEntity() {
    }

    public PatientEntity(
            UUID id,
            String patientCode,
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            String citizenId,
            String phoneNumber,
            String address,
            String occupation,
            String ethnicity,
            String nationality,
            String guardianName,
            String guardianPhoneNumber,
            String guardianRelationship,
            UUID createdByAccountId,
            Instant createdAt,
            Instant updatedAt,
            Long version) {

        this.id = id;
        this.patientCode = patientCode;
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.citizenId = citizenId;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.occupation = occupation;
        this.ethnicity = ethnicity;
        this.nationality = nationality;
        this.guardianName = guardianName;
        this.guardianPhoneNumber = guardianPhoneNumber;
        this.guardianRelationship = guardianRelationship;
        this.createdByAccountId = createdByAccountId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }

    public UUID getId() {
        return id;
    }

    public String getPatientCode() {
        return patientCode;
    }

    public String getFullName() {
        return fullName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public String getCitizenId() {
        return citizenId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getAddress() {
        return address;
    }

    public String getOccupation() {
        return occupation;
    }

    public String getEthnicity() {
        return ethnicity;
    }

    public String getNationality() {
        return nationality;
    }

    public String getGuardianName() {
        return guardianName;
    }

    public String getGuardianPhoneNumber() {
        return guardianPhoneNumber;
    }

    public String getGuardianRelationship() {
        return guardianRelationship;
    }

    public UUID getCreatedByAccountId() {
        return createdByAccountId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
