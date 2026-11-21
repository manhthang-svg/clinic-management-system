package clinic.backend.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.RecordArchive;
import clinic.backend.enums.RecordArchiveRetentionCategory;
import clinic.backend.enums.RecordArchiveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecordArchiveRepository extends JpaRepository<RecordArchive, UUID> {

    Optional<RecordArchive> findByMedicalRecordId(UUID medicalRecordId);

    boolean existsBySoluutru(String soluutru);

    interface RecordArchiveRow {
        UUID getArchiveId();
        UUID getMedicalRecordId();
        String getSoluutru();
        String getSoba();
        UUID getVisitId();
        String getVisitCode();
        Instant getAdmissionTime();
        UUID getPatientId();
        String getPatientCode();
        String getPatientName();
        String getPatientCccd();
        LocalDate getDateOfBirth();
        Integer getGender();
        RecordArchiveRetentionCategory getRetentionCategory();
        Short getRetentionYears();
        LocalDate getExpiresOn();
        String getPackageSha256();
        RecordArchiveStatus getStatus();
        Instant getArchivedAt();
    }

    @Query("""
        SELECT 
            ra.id AS archiveId,
            ra.medicalRecordId AS medicalRecordId,
            ra.soluutru AS soluutru,
            mr.soba AS soba,
            v.id AS visitId,
            v.sovaovien AS visitCode,
            v.thoigianvaovien AS admissionTime,
            p.id AS patientId,
            p.mabenhnhan AS patientCode,
            p.hoten AS patientName,
            p.cccdSo AS patientCccd,
            p.ngaysinh AS dateOfBirth,
            p.gioitinh AS gender,
            ra.retentionCategory AS retentionCategory,
            ra.retentionYears AS retentionYears,
            ra.expiresOn AS expiresOn,
            ra.packageSha256 AS packageSha256,
            ra.status AS status,
            ra.archivedAt AS archivedAt
        FROM RecordArchive ra
        JOIN MedicalRecord mr ON ra.medicalRecordId = mr.id
        JOIN Visit v ON mr.visitId = v.id
        JOIN Patient p ON v.patientId = p.id
        WHERE (:category IS NULL OR ra.retentionCategory = :category)
          AND (:from IS NULL OR ra.expiresOn >= :from)
          AND (:to IS NULL OR ra.expiresOn <= :to)
          AND (
            :keyword IS NULL OR :keyword = ''
            OR LOWER(ra.soluutru) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(mr.soba) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(v.sovaovien) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.mabenhnhan) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(p.hoten) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
        ORDER BY ra.archivedAt DESC
    """)
    Page<RecordArchiveRow> searchArchives(
            @Param("category") RecordArchiveRetentionCategory category,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
