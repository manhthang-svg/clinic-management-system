package clinic.backend.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.MedicalRecord;
import clinic.backend.enums.MedicalRecordStatus;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Optional<MedicalRecord> findByVisitId(UUID visitId);

    boolean existsBySoba(String soba);

    interface MedicalRecordRow {
        UUID getMedicalRecordId();
        String getRecordNumber();
        String getRecordType();
        MedicalRecordStatus getStatus();
        Instant getOpenedAt();
        Instant getClosedAt();
        UUID getVisitId();
        String getVisitCode();
        LocalDate getQueueDate();
        UUID getPatientId();
        String getPatientCode();
        String getPatientName();
        LocalDate getDateOfBirth();
        Integer getGender();
        String getCitizenId();
        String getPhoneNumber();
        UUID getRoomId();
        String getRoomName();
        UUID getDoctorAccountId();
        String getDoctorName();
        String getAcademicTitle();
        String getIcdCode();
        String getIcdName();
        String getClinicalDiagnosis();
    }

    interface PatientMedicalRecordSummaryRow {
        UUID getPatientId();
        String getPatientCode();
        String getFullName();
        LocalDate getBirthDate();
        Short getGender();
        String getCitizenId();
        String getPhoneNumber();
        Long getTotalVisits();
        LocalDate getLastVisitDate();
        String getLastDiagnosisIcd();
        String getLastDiagnosisName();
    }

    interface PatientVisitRow {
        UUID getVisitId();
        UUID getMedicalRecordId();
        String getRegistrationCode();
        Instant getRegisteredAt();
        String getRoomName();
        String getAcademicTitle();
        String getDoctorName();
        VisitDoituongbnLoai getDoituongbnLoai();
        String getFinalDiagnosisIcd();
        String getFinalDiagnosisName();
        VisitStatus getVisitStatus();
    }

    @Query(value = """
            SELECT 
                p.id AS patientId,
                p.mabenhnhan AS patientCode,
                p.hoten AS fullName,
                p.ngaysinh AS birthDate,
                p.gioitinh AS gender,
                p.cccd_so AS citizenId,
                p.sodienthoai AS phoneNumber,
                (SELECT COUNT(v_all.id) FROM visits v_all WHERE v_all.patient_id = p.id) AS totalVisits,
                lv.queue_date AS lastVisitDate,
                lv.maicd AS lastDiagnosisIcd,
                lv.tenicd AS lastDiagnosisName
            FROM patients p
            LEFT JOIN LATERAL (
                SELECT v_last.queue_date, c_last.maicd, c_last.tenicd
                FROM visits v_last
                LEFT JOIN medical_records mr_last ON mr_last.visit_id = v_last.id
                LEFT JOIN conclusions c_last ON c_last.medical_record_id = mr_last.id
                WHERE v_last.patient_id = p.id
                ORDER BY v_last.queue_date DESC, v_last.thoigianvaovien DESC, v_last.id DESC
                LIMIT 1
            ) lv ON true
            WHERE (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                   OR LOWER(p.mabenhnhan) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%'))
                   OR (p.cccd_so IS NOT NULL AND LOWER(p.cccd_so) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')))
                   OR (p.sodienthoai IS NOT NULL AND LOWER(p.sodienthoai) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')))
                   OR LOWER(unaccent(p.hoten)) LIKE LOWER(unaccent(CONCAT('%', CAST(:keyword AS VARCHAR), '%'))))
              AND (
                   (CAST(:from AS DATE) IS NULL AND CAST(:to AS DATE) IS NULL AND CAST(:roomId AS UUID) IS NULL AND CAST(:status AS VARCHAR) IS NULL AND (CAST(:icd10 AS VARCHAR) IS NULL OR CAST(:icd10 AS VARCHAR) = ''))
                   OR EXISTS (
                       SELECT 1 FROM visits v_filter
                       LEFT JOIN medical_records mr_filter ON mr_filter.visit_id = v_filter.id
                       LEFT JOIN conclusions c_filter ON c_filter.medical_record_id = mr_filter.id
                       WHERE v_filter.patient_id = p.id
                         AND (CAST(:from AS DATE) IS NULL OR v_filter.queue_date >= CAST(:from AS DATE))
                         AND (CAST(:to AS DATE) IS NULL OR v_filter.queue_date <= CAST(:to AS DATE))
                         AND (CAST(:roomId AS UUID) IS NULL OR v_filter.room_id = CAST(:roomId AS UUID))
                         AND (CAST(:status AS VARCHAR) IS NULL OR CAST(:status AS VARCHAR) = '' OR 
                              (CAST(:status AS VARCHAR) = 'COMPLETED' AND v_filter.status = 'COMPLETED') OR
                              (CAST(:status AS VARCHAR) = 'ABANDONED' AND v_filter.status = 'ABANDONED') OR
                              (CAST(:status AS VARCHAR) = 'ACTIVE' AND v_filter.status IN ('WAITING_FEE_PAYMENT', 'WAITING', 'IN_EXAMINATION', 'WAITING_PARACLINICAL', 'WAITING_CONCLUSION')) OR
                              (v_filter.status = CAST(:status AS VARCHAR)))
                         AND (CAST(:icd10 AS VARCHAR) IS NULL OR CAST(:icd10 AS VARCHAR) = '' OR
                              LOWER(c_filter.maicd) LIKE LOWER(CONCAT('%', CAST(:icd10 AS VARCHAR), '%')) OR
                              LOWER(c_filter.tenicd) LIKE LOWER(CONCAT('%', CAST(:icd10 AS VARCHAR), '%')))
                   )
              )
            ORDER BY lv.queue_date DESC NULLS LAST, p.hoten ASC
            """,
            countQuery = """
            SELECT COUNT(p.id)
            FROM patients p
            WHERE (CAST(:keyword AS VARCHAR) IS NULL OR CAST(:keyword AS VARCHAR) = ''
                   OR LOWER(p.mabenhnhan) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%'))
                   OR (p.cccd_so IS NOT NULL AND LOWER(p.cccd_so) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')))
                   OR (p.sodienthoai IS NOT NULL AND LOWER(p.sodienthoai) LIKE LOWER(CONCAT('%', CAST(:keyword AS VARCHAR), '%')))
                   OR LOWER(unaccent(p.hoten)) LIKE LOWER(unaccent(CONCAT('%', CAST(:keyword AS VARCHAR), '%'))))
              AND (
                   (CAST(:from AS DATE) IS NULL AND CAST(:to AS DATE) IS NULL AND CAST(:roomId AS UUID) IS NULL AND CAST(:status AS VARCHAR) IS NULL AND (CAST(:icd10 AS VARCHAR) IS NULL OR CAST(:icd10 AS VARCHAR) = ''))
                   OR EXISTS (
                       SELECT 1 FROM visits v_filter
                       LEFT JOIN medical_records mr_filter ON mr_filter.visit_id = v_filter.id
                       LEFT JOIN conclusions c_filter ON c_filter.medical_record_id = mr_filter.id
                       WHERE v_filter.patient_id = p.id
                         AND (CAST(:from AS DATE) IS NULL OR v_filter.queue_date >= CAST(:from AS DATE))
                         AND (CAST(:to AS DATE) IS NULL OR v_filter.queue_date <= CAST(:to AS DATE))
                         AND (CAST(:roomId AS UUID) IS NULL OR v_filter.room_id = CAST(:roomId AS UUID))
                         AND (CAST(:status AS VARCHAR) IS NULL OR CAST(:status AS VARCHAR) = '' OR 
                              (CAST(:status AS VARCHAR) = 'COMPLETED' AND v_filter.status = 'COMPLETED') OR
                              (CAST(:status AS VARCHAR) = 'ABANDONED' AND v_filter.status = 'ABANDONED') OR
                              (CAST(:status AS VARCHAR) = 'ACTIVE' AND v_filter.status IN ('WAITING_FEE_PAYMENT', 'WAITING', 'IN_EXAMINATION', 'WAITING_PARACLINICAL', 'WAITING_CONCLUSION')) OR
                              (v_filter.status = CAST(:status AS VARCHAR)))
                         AND (CAST(:icd10 AS VARCHAR) IS NULL OR CAST(:icd10 AS VARCHAR) = '' OR
                              LOWER(c_filter.maicd) LIKE LOWER(CONCAT('%', CAST(:icd10 AS VARCHAR), '%')) OR
                              LOWER(c_filter.tenicd) LIKE LOWER(CONCAT('%', CAST(:icd10 AS VARCHAR), '%')))
                   )
              )
            """,
            nativeQuery = true)
    Page<PatientMedicalRecordSummaryRow> searchPatientsSummary(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("roomId") UUID roomId,
            @Param("status") String status,
            @Param("icd10") String icd10,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            select v.id as visitId,
                   m.id as medicalRecordId,
                   v.sovaovien as registrationCode,
                   v.thoigianvaovien as registeredAt,
                   r.roomName as roomName,
                   s.academicTitle as academicTitle,
                   s.fullName as doctorName,
                   v.doituongbnLoai as doituongbnLoai,
                   c.maicd as finalDiagnosisIcd,
                   c.tenicd as finalDiagnosisName,
                   v.status as visitStatus
            from Visit v
            left join MedicalRecord m on m.visitId = v.id
            left join Room r on r.id = v.roomId
            left join StaffAccount s on s.id = v.doctorAccountId
            left join Conclusion c on c.medicalRecordId = m.id
            where v.patientId = :patientId
            order by v.thoigianvaovien desc, v.id desc
            """)
    List<PatientVisitRow> findVisitsByPatientId(@Param("patientId") UUID patientId);

    @Query(value = """
            select m.id as medicalRecordId, m.soba as recordNumber, m.loaiba as recordType,
                   m.status as status, m.openedAt as openedAt, m.closedAt as closedAt,
                   v.id as visitId, v.sovaovien as visitCode, v.queueDate as queueDate,
                   p.id as patientId, p.mabenhnhan as patientCode, p.hoten as patientName,
                   p.ngaysinh as dateOfBirth, p.gioitinh as gender, p.cccdSo as citizenId,
                   p.sodienthoai as phoneNumber, r.id as roomId, r.roomName as roomName,
                   s.id as doctorAccountId, s.fullName as doctorName, s.academicTitle as academicTitle,
                   c.maicd as icdCode, c.tenicd as icdName,
                   coalesce(c.tenicd, ce.chandoansobo, ce.chandoanvaovien) as clinicalDiagnosis
            from MedicalRecord m
            join Visit v on v.id = m.visitId
            join Patient p on p.id = v.patientId
            left join Room r on r.id = v.roomId
            left join StaffAccount s on s.id = v.doctorAccountId
            left join Conclusion c on c.medicalRecordId = m.id
            left join ClinicalExamination ce on ce.medicalRecordId = m.id
            where (:from is null or v.queueDate >= :from)
              and (:to is null or v.queueDate <= :to)
              and (:status is null or m.status = :status)
              and (:roomId is null or v.roomId = :roomId)
              and (:keyword is null or :keyword = ''
                   or lower(m.soba) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(v.sovaovien) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(p.mabenhnhan) like lower(concat('%', cast(:keyword as String), '%'))
                   or (p.cccdSo is not null and lower(p.cccdSo) like lower(concat('%', cast(:keyword as String), '%')))
                   or (p.sodienthoai is not null and lower(p.sodienthoai) like lower(concat('%', cast(:keyword as String), '%')))
                   or lower(function('unaccent', p.hoten)) like lower(function('unaccent', concat('%', cast(:keyword as String), '%'))))
            order by v.queueDate desc, m.openedAt desc, m.id desc
            """,
            countQuery = """
            select count(m)
            from MedicalRecord m
            join Visit v on v.id = m.visitId
            join Patient p on p.id = v.patientId
            where (:from is null or v.queueDate >= :from)
              and (:to is null or v.queueDate <= :to)
              and (:status is null or m.status = :status)
              and (:roomId is null or v.roomId = :roomId)
              and (:keyword is null or :keyword = ''
                   or lower(m.soba) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(v.sovaovien) like lower(concat('%', cast(:keyword as String), '%'))
                   or lower(p.mabenhnhan) like lower(concat('%', cast(:keyword as String), '%'))
                   or (p.cccdSo is not null and lower(p.cccdSo) like lower(concat('%', cast(:keyword as String), '%')))
                   or (p.sodienthoai is not null and lower(p.sodienthoai) like lower(concat('%', cast(:keyword as String), '%')))
                   or lower(function('unaccent', p.hoten)) like lower(function('unaccent', concat('%', cast(:keyword as String), '%'))))
            """)
    Page<MedicalRecordRow> findMedicalRecords(
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("status") MedicalRecordStatus status,
            @Param("roomId") UUID roomId,
            @Param("keyword") String keyword,
            Pageable pageable);
}
