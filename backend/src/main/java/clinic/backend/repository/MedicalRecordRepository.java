package clinic.backend.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import clinic.backend.entity.ClinicalExamination;
import clinic.backend.entity.Conclusion;
import clinic.backend.entity.FollowUpAppointment;
import clinic.backend.entity.MedicalRecord;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Prescription;
import clinic.backend.entity.Room;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.entity.VitalSign;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.enums.VisitStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    /**
     * UC-51 BR-51-04: the patient matches the keyword and has at least one visit that meets every visit filter at
     * once. Patterns are lower-case, already wrapped in {@code %} and LIKE-escaped by the caller; a null pattern
     * does not filter. {@code statuses} is never empty.
     */
    String PATIENT_SEARCH_WHERE = """
             WHERE (CAST(:keywordPattern AS VARCHAR) IS NULL
                    OR LOWER(p.mabenhnhan) LIKE CAST(:keywordPattern AS VARCHAR)
                    OR LOWER(p.cccd_so) LIKE CAST(:keywordPattern AS VARCHAR)
                    OR LOWER(p.sodienthoai) LIKE CAST(:keywordPattern AS VARCHAR)
                    OR LOWER(unaccent(p.hoten)) LIKE unaccent(CAST(:keywordPattern AS VARCHAR)))
               AND EXISTS (
                    SELECT 1
                    FROM visits v
                    LEFT JOIN medical_records mr ON mr.visit_id = v.id
                    LEFT JOIN conclusions c ON c.medical_record_id = mr.id
                    WHERE v.patient_id = p.id
                      AND (CAST(:from AS DATE) IS NULL OR v.queue_date >= CAST(:from AS DATE))
                      AND (CAST(:to AS DATE) IS NULL OR v.queue_date <= CAST(:to AS DATE))
                      AND (CAST(:roomId AS UUID) IS NULL OR v.room_id = CAST(:roomId AS UUID))
                      AND v.status IN (:statuses)
                      AND (CAST(:icdPattern AS VARCHAR) IS NULL
                           OR LOWER(c.maicd) LIKE CAST(:icdPattern AS VARCHAR)
                           OR LOWER(unaccent(c.tenicd)) LIKE unaccent(CAST(:icdPattern AS VARCHAR))))
            """;

    Optional<MedicalRecord> findByVisitId(UUID visitId);

    /** UC-52: the record with its visit, patient, room and doctor, read in one query. */
    interface DetailHeader {
        MedicalRecord getRecord();
        Visit getVisit();
        Patient getPatient();
        Room getRoom();
        StaffAccount getDoctor();
    }

    String DETAIL_HEADER_SELECT = """
            select m as record, v as visit, p as patient, r as room, s as doctor
            from MedicalRecord m
            left join Visit v on v.id = m.visitId
            left join Patient p on p.id = v.patientId
            left join Room r on r.id = v.roomId
            left join StaffAccount s on s.id = v.doctorAccountId
            """;

    @Query(DETAIL_HEADER_SELECT + " where m.id = :id")
    Optional<DetailHeader> findDetailHeaderById(@Param("id") UUID id);

    @Query(DETAIL_HEADER_SELECT + " where m.visitId = :visitId")
    Optional<DetailHeader> findDetailHeaderByVisitId(@Param("visitId") UUID visitId);

    /**
     * UC-52: the one-per-record parts of the detail, read in one query; each is empty when not recorded yet. The
     * initial vital signs are unique per record (index {@code uq_vital_signs_initial}).
     */
    interface DetailParts {
        VitalSign getVitalSign();
        ClinicalExamination getExamination();
        Conclusion getConclusion();
        Prescription getPrescription();
        FollowUpAppointment getFollowUp();
    }

    @Query("""
            select vs as vitalSign, ce as examination, c as conclusion, pr as prescription, f as followUp
            from MedicalRecord m
            left join VitalSign vs on vs.medicalRecordId = m.id and vs.isInitial = true
            left join ClinicalExamination ce on ce.medicalRecordId = m.id
            left join Conclusion c on c.medicalRecordId = m.id
            left join Prescription pr on pr.medicalRecordId = m.id
            left join FollowUpAppointment f on f.medicalRecordId = m.id
            where m.id = :id
            """)
    Optional<DetailParts> findDetailParts(@Param("id") UUID id);

    boolean existsBySoba(String soba);

    /** UC-51 level 1 (BR-51-05). No citizen ID or phone number: they are search keys, not shown. */
    interface PatientMedicalRecordSummaryRow {
        UUID getPatientId();
        String getPatientCode();
        String getFullName();
        LocalDate getBirthDate();
        Short getGender();
        Long getTotalVisits();
        LocalDate getLastVisitDate();
        String getLastDiagnosisIcd();
        String getLastDiagnosisName();
    }

    /** UC-51 level 2 (BR-51-06) and the UC-52 timeline (BR-52-08). */
    interface PatientVisitRow {
        UUID getVisitId();
        UUID getMedicalRecordId();
        String getRegistrationCode();
        Instant getRegisteredAt();
        LocalDate getVisitDate();
        String getRoomName();
        String getAcademicTitle();
        String getDoctorName();
        VisitDoituongbnLoai getDoituongbnLoai();
        String getFinalDiagnosisIcd();
        String getFinalDiagnosisName();
        VisitStatus getVisitStatus();
        Boolean getSkipped();
    }

    /**
     * Last visit date: the newest visit. Last diagnosis: the newest visit that has a conclusion. The visit count
     * covers every visit of the patient, whatever the filters.
     */
    @Query(value = """
            SELECT p.id AS patientId,
                   p.mabenhnhan AS patientCode,
                   p.hoten AS fullName,
                   p.ngaysinh AS birthDate,
                   p.gioitinh AS gender,
                   (SELECT COUNT(*) FROM visits va WHERE va.patient_id = p.id) AS totalVisits,
                   lv.queue_date AS lastVisitDate,
                   ld.maicd AS lastDiagnosisIcd,
                   ld.tenicd AS lastDiagnosisName
            FROM patients p
            LEFT JOIN LATERAL (
                SELECT vl.queue_date
                FROM visits vl
                WHERE vl.patient_id = p.id
                ORDER BY vl.queue_date DESC, vl.thoigianvaovien DESC, vl.id DESC
                LIMIT 1
            ) lv ON true
            LEFT JOIN LATERAL (
                SELECT cd.maicd, cd.tenicd
                FROM visits vd
                JOIN medical_records mrd ON mrd.visit_id = vd.id
                JOIN conclusions cd ON cd.medical_record_id = mrd.id
                WHERE vd.patient_id = p.id
                ORDER BY vd.queue_date DESC, vd.thoigianvaovien DESC, vd.id DESC
                LIMIT 1
            ) ld ON true
            """ + PATIENT_SEARCH_WHERE + """
            ORDER BY lv.queue_date DESC NULLS LAST, p.hoten ASC, p.id ASC
            """,
            countQuery = "SELECT COUNT(*) FROM patients p " + PATIENT_SEARCH_WHERE,
            nativeQuery = true)
    Page<PatientMedicalRecordSummaryRow> searchPatientsSummary(
            @Param("keywordPattern") String keywordPattern,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("roomId") UUID roomId,
            @Param("statuses") Collection<String> statuses,
            @Param("icdPattern") String icdPattern,
            Pageable pageable
    );

    @Query("""
            select v.id as visitId,
                   m.id as medicalRecordId,
                   v.sovaovien as registrationCode,
                   v.thoigianvaovien as registeredAt,
                   v.queueDate as visitDate,
                   r.roomName as roomName,
                   s.academicTitle as academicTitle,
                   s.fullName as doctorName,
                   v.doituongbnLoai as doituongbnLoai,
                   c.maicd as finalDiagnosisIcd,
                   c.tenicd as finalDiagnosisName,
                   v.status as visitStatus,
                   v.isSkipped as skipped
            from Visit v
            left join MedicalRecord m on m.visitId = v.id
            left join Room r on r.id = v.roomId
            left join StaffAccount s on s.id = v.doctorAccountId
            left join Conclusion c on c.medicalRecordId = m.id
            where v.patientId = :patientId
            order by v.thoigianvaovien desc, v.id desc
            """)
    List<PatientVisitRow> findVisitsByPatientId(@Param("patientId") UUID patientId);
}
