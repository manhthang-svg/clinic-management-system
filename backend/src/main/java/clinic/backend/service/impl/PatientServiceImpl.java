
package clinic.backend.service.impl;

import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.request.UpdatePatientRequest;
import clinic.backend.dto.response.PatientActiveVisitResponse;
import clinic.backend.dto.response.PatientOverviewResponse;
import clinic.backend.dto.response.PatientResponse;
import clinic.backend.entity.MedicalHistory;
import clinic.backend.entity.Patient;
import clinic.backend.entity.Payment;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.MedicalHistoryType;
import clinic.backend.enums.PatientSearchType;
import clinic.backend.enums.PaymentType;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.PatientService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    /** UC-35 step 6: at most 50 results. */
    static final int MAX_SEARCH_RESULTS = 50;
    static final Sort DEFAULT_SEARCH_SORT = Sort.by("mabenhnhan");

    private static final String PATIENT_AUDIT_ENTITY = "patients";
    private static final int ADULT_AGE = 18;
    private static final int MAX_AGE_YEARS = 120;
    private static final int MAX_CODE_ATTEMPTS = 10;
    /** UC-37 AF-02: the possible duplicates shown with MSG57. */
    private static final int MAX_DUPLICATES = 10;

    private final PatientRepository patientRepository;
    private final VisitRepository visitRepository;
    private final PaymentRepository paymentRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final AuditLogService auditLogService;
    private final UuidV7Generator uuidV7Generator;
    private final Clock clock;

    private String generatePatientCode() {
        int number = java.util.concurrent.ThreadLocalRandom
                .current()
                .nextInt(1_000_000, 10_000_000);

        return "BN" + number;
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    @Override
    @Transactional
    public PatientResponse createPatient(CreatePatientRequest request) {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Không xác định được tài khoản đang đăng nhập"
            );
        }

        UUID createdByAccountId = UUID.fromString(jwt.getSubject());

        String cccdSo = normalize(request.cccdSo());
        String hochieuSo = normalize(request.hochieuSo());
        checkAgeRules(request);
        // Step 6 / EF-02: one profile per citizen ID or passport (BR-12); MSG19 offers to open it.
        if (cccdSo != null) {
            patientRepository.findByCccdSo(cccdSo).ifPresent(existing -> identificationExists("cccdSo", existing));
        }
        if (hochieuSo != null) {
            patientRepository.findFirstByHochieuSoIgnoreCase(hochieuSo)
                    .ifPresent(existing -> identificationExists("hochieuSo", existing));
        }

        String mabenhnhan = uniquePatientCode();
        Instant now = clock.instant();

        Patient patient = new Patient(
                uuidV7Generator.next(),
                now,
                mabenhnhan,
                cccdSo,
                hochieuSo,
                request.hoten().trim(),
                request.ngaysinh(),
                request.gioitinh(),
                normalize(request.nghenghiep()),
                normalize(request.nghenghiepMa()),
                request.dantoc(),
                request.dantocMa(),
                normalize(request.ngoaikieu()),
                normalize(request.ngoaikieuMa()),
                request.diachi(),
                normalize(request.xaphuong()),
                normalize(request.xaphuongMa()),
                normalize(request.tinhthanh()),
                normalize(request.tinhthanhMa()),
                normalize(request.noilamviec()),
                normalize(request.sodienthoai()),
                normalize(request.email()),
                normalize(request.yeutorh()),
                normalize(request.nhommau()),
                normalize(request.hotennguoithan()),
                normalize(request.nguoithanQuanhe()),
                normalize(request.diachinguoithan()),
                normalize(request.sodienthoainguoithan()),
                createdByAccountId
        );

        Patient savedPatient = patientRepository.save(patient);
        // Step 8: the creation is recorded in the audit log.
        auditLogService.recordBusinessChange(createdByAccountId, AuditLogAction.CREATE, PATIENT_AUDIT_ENTITY,
                savedPatient.getId(), null, Map.of("mabenhnhan", mabenhnhan), now);
        return toResponse(savedPatient);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PatientResponse> findPossibleDuplicates(String fullName, LocalDate birthDate) {
        if (normalize(fullName) == null || birthDate == null) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    Map.of(birthDate == null ? "ngaysinh" : "hoten", "REQUIRED"));
        }
        return patientRepository.findPossibleDuplicates(fullName.trim(), birthDate).stream()
                .limit(MAX_DUPLICATES).map(this::toResponse).toList();
    }

    private void checkAgeRules(CreatePatientRequest request) {
        checkAgeRules(request.ngaysinh(), request.sodienthoai(), request.hotennguoithan(), request.nguoithanQuanhe());
    }

    /** UC-37 step 4 (also UC-38 step 4): phone from 18 years old; guardian name and relationship under 18. */
    private void checkAgeRules(LocalDate birthDate, String phone, String guardianName, String guardianRelation) {
        LocalDate today = LocalDate.now(clock);
        int age = Period.between(birthDate, today).getYears();
        Map<String, String> errors = new LinkedHashMap<>();
        if (birthDate.isBefore(today.minusYears(MAX_AGE_YEARS))) {
            errors.put("ngaysinh", "INVALID_FORMAT");
        }
        if (age >= ADULT_AGE && normalize(phone) == null) {
            errors.put("sodienthoai", "REQUIRED");
        }
        if (age < ADULT_AGE && normalize(guardianName) == null) {
            errors.put("hotennguoithan", "REQUIRED");
        }
        if (age < ADULT_AGE && normalize(guardianRelation) == null) {
            errors.put("nguoithanQuanhe", "REQUIRED");
        }
        if (!errors.isEmpty()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, errors);
        }
    }

    private static void identificationExists(String field, Patient existing) {
        throw new AppException(ErrorCode.PATIENT_IDENTIFICATION_EXISTS, Map.of(
                "field", field,
                "patientId", existing.getId().toString(),
                "patientCode", existing.getMabenhnhan(),
                "fullName", existing.getHoten()));
    }

    /** Step 8: a random code is drawn again while it is already used. */
    private String uniquePatientCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generatePatientCode();
            if (!patientRepository.existsByMabenhnhan(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No free patient code after " + MAX_CODE_ATTEMPTS + " attempts");
    }

    @Override
    @Transactional
    public PatientResponse updatePatient(UUID actorAccountId, UUID id, UpdatePatientRequest request) {
        Patient patient = requiredPatient(id);
        if (!patient.getVersion().equals(request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        // Step 4: the checks of UC-37 steps 4–5 (annotations and age rules).
        checkAgeRules(request.ngaysinh(), request.sodienthoai(), request.hotennguoithan(), request.nguoithanQuanhe());

        String cccdSo = normalize(request.cccdSo());
        String hochieuSo = normalize(request.hochieuSo());
        // Step 5 / EF-02: a citizen ID or passport used by another patient (BR-12); MSG19 offers to open it.
        if (cccdSo != null) {
            patientRepository.findByCccdSo(cccdSo).filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> identificationExists("cccdSo", other));
        }
        if (hochieuSo != null) {
            patientRepository.findFirstByHochieuSoIgnoreCase(hochieuSo).filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> identificationExists("hochieuSo", other));
        }

        // AF-02: compare before touching the managed entity, so an unchanged form saves and records nothing.
        Map<String, Object> changes = changedFields(administrativeValues(patient),
                requestedValues(request, cccdSo, hochieuSo));
        if (changes.isEmpty()) {
            return toResponse(patient);
        }
        Instant now = clock.instant();
        patient.updateAdministrativeInfo(
                cccdSo,
                hochieuSo,
                request.hoten().trim(),
                request.ngaysinh(),
                request.gioitinh(),
                normalize(request.nghenghiep()),
                normalize(request.nghenghiepMa()),
                request.dantoc().trim(),
                request.dantocMa().trim(),
                normalize(request.ngoaikieu()),
                normalize(request.ngoaikieuMa()),
                request.diachi().trim(),
                normalize(request.xaphuong()),
                normalize(request.xaphuongMa()),
                normalize(request.tinhthanh()),
                normalize(request.tinhthanhMa()),
                normalize(request.noilamviec()),
                normalize(request.sodienthoai()),
                normalize(request.email()),
                normalize(request.yeutorh()),
                normalize(request.nhommau()),
                normalize(request.hotennguoithan()),
                normalize(request.nguoithanQuanhe()),
                normalize(request.diachinguoithan()),
                normalize(request.sodienthoainguoithan()),
                now
        );
        Patient saved = patientRepository.save(patient);
        // Step 6: changed fields with old and new values (Security: Audit trail).
        auditLogService.recordBusinessChange(actorAccountId, AuditLogAction.UPDATE, PATIENT_AUDIT_ENTITY, id, null,
                Map.of("mabenhnhan", patient.getMabenhnhan(), "changes", changes), now);
        return toResponse(saved);
    }

    /** The administrative fields a Receptionist can change (BR-23: no history or allergies). */
    private static Map<String, Object> administrativeValues(Patient patient) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("hoten", patient.getHoten());
        values.put("ngaysinh", patient.getNgaysinh());
        values.put("gioitinh", patient.getGioitinh());
        values.put("cccdSo", patient.getCccdSo());
        values.put("hochieuSo", patient.getHochieuSo());
        values.put("sodienthoai", patient.getSodienthoai());
        values.put("email", patient.getEmail());
        values.put("diachi", patient.getDiachi());
        values.put("xaphuong", patient.getXaphuong());
        values.put("xaphuongMa", patient.getXaphuongMa());
        values.put("tinhthanh", patient.getTinhthanh());
        values.put("tinhthanhMa", patient.getTinhthanhMa());
        values.put("nghenghiep", patient.getNghenghiep());
        values.put("nghenghiepMa", patient.getNghenghiepMa());
        values.put("noilamviec", patient.getNoilamviec());
        values.put("dantoc", patient.getDantoc());
        values.put("dantocMa", patient.getDantocMa());
        values.put("ngoaikieu", patient.getNgoaikieu());
        values.put("ngoaikieuMa", patient.getNgoaikieuMa());
        values.put("yeutorh", patient.getYeutorh());
        values.put("nhommau", patient.getNhommau());
        values.put("hotennguoithan", patient.getHotennguoithan());
        values.put("nguoithanQuanhe", patient.getNguoithanQuanhe());
        values.put("diachinguoithan", patient.getDiachinguoithan());
        values.put("sodienthoainguoithan", patient.getSodienthoainguoithan());
        return values;
    }

    /** The same fields as {@link #administrativeValues}, normalized the way they are saved. */
    private Map<String, Object> requestedValues(UpdatePatientRequest request, String cccdSo, String hochieuSo) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("hoten", request.hoten().trim());
        values.put("ngaysinh", request.ngaysinh());
        values.put("gioitinh", request.gioitinh());
        values.put("cccdSo", cccdSo);
        values.put("hochieuSo", hochieuSo);
        values.put("sodienthoai", normalize(request.sodienthoai()));
        values.put("email", normalize(request.email()));
        values.put("diachi", request.diachi().trim());
        values.put("xaphuong", normalize(request.xaphuong()));
        values.put("xaphuongMa", normalize(request.xaphuongMa()));
        values.put("tinhthanh", normalize(request.tinhthanh()));
        values.put("tinhthanhMa", normalize(request.tinhthanhMa()));
        values.put("nghenghiep", normalize(request.nghenghiep()));
        values.put("nghenghiepMa", normalize(request.nghenghiepMa()));
        values.put("noilamviec", normalize(request.noilamviec()));
        values.put("dantoc", request.dantoc().trim());
        values.put("dantocMa", request.dantocMa().trim());
        values.put("ngoaikieu", normalize(request.ngoaikieu()));
        values.put("ngoaikieuMa", normalize(request.ngoaikieuMa()));
        values.put("yeutorh", normalize(request.yeutorh()));
        values.put("nhommau", normalize(request.nhommau()));
        values.put("hotennguoithan", normalize(request.hotennguoithan()));
        values.put("nguoithanQuanhe", normalize(request.nguoithanQuanhe()));
        values.put("diachinguoithan", normalize(request.diachinguoithan()));
        values.put("sodienthoainguoithan", normalize(request.sodienthoainguoithan()));
        return values;
    }

    /** { field: { old, new } } for every field whose value changed. */
    private static Map<String, Object> changedFields(Map<String, Object> before, Map<String, Object> after) {
        Map<String, Object> changes = new LinkedHashMap<>();
        before.forEach((field, oldValue) -> {
            Object newValue = after.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("old", oldValue == null ? null : oldValue.toString());
                change.put("new", newValue == null ? null : newValue.toString());
                changes.put(field, change);
            }
        });
        return changes;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientResponse>   listPatients(String keyword, PatientSearchType type, Pageable pageable) {
        // At most 50 patients per page. Without a client sort, pages follow the patient code so paging
        // neither repeats nor skips a patient.
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : DEFAULT_SEARCH_SORT;
        Pageable limited = PageRequest.of(
                pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_SEARCH_RESULTS), sort);
        String normalizedKeyword = normalize(keyword);
        Page<Patient> patients;
        if (type == null) {
            patients = normalizedKeyword == null
                    ? patientRepository.findAll(limited)
                    : patientRepository.search(normalizedKeyword, limited);
        } else {
            if (normalizedKeyword == null) {
                throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("keyword", "REQUIRED"));
            }
            if (!isValidKeyword(normalizedKeyword, type)) {
                throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("keyword", "INVALID_FORMAT"));
            }
            patients = switch (type) {
                case CITIZEN_ID -> patientRepository.findAllByCccdSo(normalizedKeyword, limited);
                case PASSPORT -> patientRepository.findAllByHochieuSoIgnoreCase(normalizedKeyword, limited);
                case PATIENT_CODE -> patientRepository.findAllByMabenhnhanContainingIgnoreCase(normalizedKeyword, limited);
                case PHONE -> patientRepository.findAllBySodienthoaiContaining(normalizedKeyword, limited);
                case NAME -> patientRepository.searchByName(normalizedKeyword, limited);
                case INSURANCE_CARD -> patientRepository.searchByInsuranceCard(normalizedKeyword, limited);
            };
        }
        return withVisitSummary(patients);
    }

    /**
     * UC-35 step 4: keyword format by search type.
     * DECISION (team, 2026-11-15): numbers read from a document (citizen ID, passport, insurance card) must be
     * typed in full and match exactly, so a partial number cannot list other patients' identities. What a
     * patient says from memory may be partial. The minimum length follows how sensitive the field is: the
     * internal patient code from 1 character, the phone (personal data) from 4 digits, the name from 2.
     */
    private static boolean isValidKeyword(String keyword, PatientSearchType type) {
        return switch (type) {
            case CITIZEN_ID -> keyword.matches("\\d{12}");
            case PASSPORT -> keyword.matches("[A-Za-z0-9]{1,15}");
            case INSURANCE_CARD -> keyword.matches("[A-Za-z0-9]{15}");
            case PHONE -> keyword.matches("\\d{4,10}");
            case PATIENT_CODE -> keyword.matches("[A-Za-z0-9-]{1,20}");
            case NAME -> keyword.length() >= 2 && keyword.length() <= 100;
        };
    }

    /** UC-35 step 6: last visit date and active visit of each result, loaded with two queries per page. */
    private Page<PatientResponse> withVisitSummary(Page<Patient> patients) {
        List<UUID> ids = patients.map(Patient::getId).getContent();
        if (ids.isEmpty()) {
            return patients.map(this::toResponse);
        }
        Map<UUID, Instant> lastVisits = visitRepository.findLastVisitTimes(ids).stream()
                .collect(Collectors.toMap(
                        VisitRepository.PatientLastVisit::getPatientId,
                        VisitRepository.PatientLastVisit::getLastVisitAt));
        Map<UUID, PatientActiveVisitResponse> activeVisits = visitRepository
                .findActiveVisits(ids, VisitRepository.FINISHED_STATUSES).stream()
                .collect(Collectors.toMap(
                        VisitRepository.PatientActiveVisit::getPatientId,
                        visit -> new PatientActiveVisitResponse(
                                visit.getRegistrationCode(), visit.getStatus(), visit.getRoomName()),
                        (first, second) -> first));
        return patients.map(patient -> toResponse(
                patient, lastVisits.get(patient.getId()), activeVisits.get(patient.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public PatientResponse getPatientById(UUID id) {
        return toResponse(requiredPatient(id));
    }

    @Override
    @Transactional
    public PatientOverviewResponse getPatientOverview(UUID actorAccountId, UUID id) {
        Patient patient = requiredPatient(id);
        List<VisitRepository.PatientVisitHistory> history = visitRepository.findPatientHistory(id);

        List<PatientOverviewResponse.VisitHistoryItem> visits = history.stream()
                .map(visit -> new PatientOverviewResponse.VisitHistoryItem(
                        visit.getVisitId(), visit.getRegistrationCode(), visit.getRegisteredAt(),
                        visit.getRoomName(), doctorName(visit.getDoctorTitle(), visit.getDoctorName()),
                        visit.getVisitType(), visit.getStatus()))
                .toList();
        String lastInsuranceCard = history.stream()
                .map(VisitRepository.PatientVisitHistory::getInsuranceCard)
                .filter(card -> card != null && !card.isBlank())
                .findFirst().orElse(null);
        PatientOverviewResponse.CurrentVisit activeVisit = history.stream()
                .filter(visit -> !VisitRepository.FINISHED_STATUSES.contains(visit.getStatus()))
                .findFirst()
                .map(visit -> new PatientOverviewResponse.CurrentVisit(
                        visit.getVisitId(), visit.getRegistrationCode(), visit.getStatus(), visit.getRoomName(),
                        paymentRepository.findByVisitIdAndType(visit.getVisitId(), PaymentType.EXAMINATION_FEE)
                                .map(Payment::getStatus).orElse(null)))
                .orElse(null);
        List<String> drugAllergies = medicalHistoryRepository
                .findByPatientIdAndHistoryTypeOrderByCreatedAtAsc(id, MedicalHistoryType.DRUG_ALLERGY).stream()
                // UC-39 AF-01: the "no known drug allergy" statement is not an allergy.
                .filter(item -> !item.isNoKnownDrugAllergy())
                .map(MedicalHistory::getThongtinNoidung)
                .toList();

        // BR-48: every view of a patient profile is recorded.
        auditLogService.recordBusinessChange(actorAccountId, AuditLogAction.VIEW_RECORD, PATIENT_AUDIT_ENTITY,
                id, null, Map.of("mabenhnhan", patient.getMabenhnhan()), Instant.now());

        return new PatientOverviewResponse(toResponse(patient), drugAllergies, lastInsuranceCard, activeVisit, visits);
    }

    private Patient requiredPatient(UUID id) {
        return patientRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PATIENT_NOT_FOUND));
    }

    private static String doctorName(String academicTitle, String fullName) {
        if (fullName == null) {
            return null;
        }
        return academicTitle == null || academicTitle.isBlank() ? fullName : academicTitle.strip() + " " + fullName;
    }

    private PatientResponse toResponse(Patient savedPatient) {
        return toResponse(savedPatient, null, null);
    }

    private PatientResponse toResponse(
            Patient savedPatient, Instant lastVisitAt, PatientActiveVisitResponse activeVisit) {
        return new PatientResponse(
                savedPatient.getId(),
                savedPatient.getMabenhnhan(),
                savedPatient.getCccdSo(),
                savedPatient.getHochieuSo(),
                savedPatient.getHoten(),
                savedPatient.getNgaysinh(),
                savedPatient.getGioitinh(),
                savedPatient.getNghenghiep(),
                savedPatient.getNghenghiepMa(),
                savedPatient.getDantoc(),
                savedPatient.getDantocMa(),
                savedPatient.getNgoaikieu(),
                savedPatient.getNgoaikieuMa(),
                savedPatient.getDiachi(),
                savedPatient.getXaphuong(),
                savedPatient.getXaphuongMa(),
                savedPatient.getTinhthanh(),
                savedPatient.getTinhthanhMa(),
                savedPatient.getNoilamviec(),
                savedPatient.getSodienthoai(),
                savedPatient.getEmail(),
                savedPatient.getYeutorh(),
                savedPatient.getNhommau(),
                savedPatient.getHotennguoithan(),
                savedPatient.getNguoithanQuanhe(),
                savedPatient.getDiachinguoithan(),
                savedPatient.getSodienthoainguoithan(),
                savedPatient.getVersion(),
                lastVisitAt,
                activeVisit
        );
    }
}
