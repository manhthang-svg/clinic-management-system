
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
import clinic.backend.utils.CountryCatalog;
import clinic.backend.utils.EthnicityCatalog;
import clinic.backend.utils.OccupationCatalog;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    // UC-35: moi trang 20 benh nhan
    static final int PAGE_SIZE = 20;
    static final Sort DEFAULT_SEARCH_SORT = Sort.by("mabenhnhan");

    private static final String PATIENT_AUDIT_ENTITY = "patients";
    private static final int ADULT_AGE = 18;
    // ma nghe nghiep "Ngoai kieu" (Phu luc 6)
    private static final String FOREIGNER_OCCUPATION_CODE = "12";
    private static final int MAX_AGE_YEARS = 120;
    private static final int MAX_CODE_ATTEMPTS = 10;
    // UC-37: so ho so trung toi da hien len
    private static final int MAX_DUPLICATES = 10;

    private final PatientRepository patientRepository;
    private final VisitRepository visitRepository;
    private final PaymentRepository paymentRepository;
    private final MedicalHistoryRepository medicalHistoryRepository;
    private final AuditLogService auditLogService;
    private final UuidV7Generator uuidV7Generator;
    private final Clock clock;

    // sinh ma BN ngau nhien: BN + 7 so
    private String generatePatientCode() {
        int number = java.util.concurrent.ThreadLocalRandom
                .current()
                .nextInt(1_000_000, 10_000_000);

        return "BN" + number;
    }

    // bo khoang trang 2 dau, chuoi rong thi tra null
    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    // UC-37: tao ho so benh nhan moi
    @Override
    @Transactional
    public PatientResponse createPatient(UUID createdByAccountId, CreatePatientRequest request) {
        String cccdSo = normalize(request.cccdSo());
        String hochieuSo = normalize(request.hochieuSo());
        checkAgeRules(request);
        // CCCD / ho chieu da co ho so thi bao trung (BR-12, MSG19)
        if (cccdSo != null) {
            patientRepository.findByCccdSo(cccdSo).ifPresent(existing -> identificationExists("cccdSo", existing));
        }
        if (hochieuSo != null) {
            patientRepository.findFirstByHochieuSoIgnoreCase(hochieuSo)
                    .ifPresent(existing -> identificationExists("hochieuSo", existing));
        }

        String mabenhnhan = uniquePatientCode();
        Instant now = clock.instant();
        String nghenghiepMa = occupationCode(request.nghenghiep(), request.nghenghiepMa(), null);
        String ngoaikieuMa = countryCode(nghenghiepMa, request.ngoaikieu(), request.ngoaikieuMa());

        // tao va luu ho so
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
                nghenghiepMa,
                request.dantoc(),
                ethnicityCode(request.dantoc(), request.dantocMa(), null),
                normalize(request.ngoaikieu()),
                ngoaikieuMa,
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
        // ghi audit log tao ho so
        auditLogService.recordBusinessChange(createdByAccountId, AuditLogAction.CREATE, PATIENT_AUDIT_ENTITY,
                savedPatient.getId(), null, Map.of("mabenhnhan", mabenhnhan), now);
        return toResponse(savedPatient);
    }

    // UC-37: tim ho so co the trung (cung ho ten + ngay sinh)
    @Override
    @Transactional(readOnly = true)
    public List<PatientResponse> findPossibleDuplicates(String fullName, LocalDate birthDate) {
        if (normalize(fullName) == null || birthDate == null) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    Map.of(birthDate == null ? "ngaysinh" : "hoten", "REQUIRED"));
        }
        if (fullName.strip().length() > 100) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("hoten", "TOO_LONG"));
        }
        return patientRepository.findPossibleDuplicates(fullName.trim(), birthDate).stream()
                .limit(MAX_DUPLICATES).map(this::toResponse).toList();
    }

    private void checkAgeRules(CreatePatientRequest request) {
        checkAgeRules(request.ngaysinh(), request.sodienthoai(), request.hotennguoithan(), request.nguoithanQuanhe(), request.sodienthoainguoithan());
    }

    // kiem tra theo tuoi: tu 18 tuoi bat buoc SDT, duoi 18 bat buoc nguoi than va SDT nguoi than
    private void checkAgeRules(LocalDate birthDate, String phone, String guardianName, String guardianRelation, String guardianPhone) {
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
        if (age < ADULT_AGE && normalize(guardianPhone) == null) {
            errors.put("sodienthoainguoithan", "REQUIRED");
        }
        if (!errors.isEmpty()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, errors);
        }
    }

    // bao loi trung giay to, tra ve ho so dang dung giay to do
    private static void identificationExists(String field, Patient existing) {
        throw new AppException(ErrorCode.PATIENT_IDENTIFICATION_EXISTS, Map.of(
                "field", field,
                "patientId", existing.getId().toString(),
                "patientCode", existing.getMabenhnhan(),
                "fullName", existing.getHoten()));
    }

    // sinh ma BN, trung thi sinh lai (toi da 10 lan)
    private String uniquePatientCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = generatePatientCode();
            if (!patientRepository.existsByMabenhnhan(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No free patient code after " + MAX_CODE_ATTEMPTS + " attempts");
    }

    // UC-38: cap nhat thong tin hanh chinh cua benh nhan
    @Override
    @Transactional
    public PatientResponse updatePatient(UUID actorAccountId, UUID id, UpdatePatientRequest request) {
        Patient patient = requiredPatient(id);
        // ho so da bi nguoi khac sua thi khong cho luu (MSG75)
        if (!patient.getVersion().equals(request.version())) {
            throw new AppException(ErrorCode.CONCURRENT_MODIFICATION);
        }
        // kiem tra tuoi giong luc tao ho so
        checkAgeRules(request.ngaysinh(), request.sodienthoai(), request.hotennguoithan(), request.nguoithanQuanhe(), request.sodienthoainguoithan());

        String cccdSo = normalize(request.cccdSo());
        String hochieuSo = normalize(request.hochieuSo());
        // CCCD / ho chieu dang thuoc benh nhan khac thi bao trung (BR-12, MSG19)
        if (cccdSo != null) {
            patientRepository.findByCccdSo(cccdSo).filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> identificationExists("cccdSo", other));
        }
        if (hochieuSo != null) {
            patientRepository.findFirstByHochieuSoIgnoreCase(hochieuSo).filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> identificationExists("hochieuSo", other));
        }

        // so sanh truoc khi sua: khong doi gi thi khong luu, khong ghi audit
        Map<String, Object> changes = changedFields(administrativeValues(patient),
                requestedValues(request, cccdSo, hochieuSo, patient.getDantocMa(), patient.getNghenghiepMa()));
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
                occupationCode(request.nghenghiep(), request.nghenghiepMa(), patient.getNghenghiepMa()),
                request.dantoc().trim(),
                ethnicityCode(request.dantoc(), request.dantocMa(), patient.getDantocMa()),
                normalize(request.ngoaikieu()),
                countryCode(occupationCode(request.nghenghiep(), request.nghenghiepMa(), patient.getNghenghiepMa()),
                        request.ngoaikieu(), request.ngoaikieuMa()),
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
        // ghi audit cac truong da doi (gia tri cu va moi)
        auditLogService.recordBusinessChange(actorAccountId, AuditLogAction.UPDATE, PATIENT_AUDIT_ENTITY, id, null,
                Map.of("mabenhnhan", patient.getMabenhnhan(), "changes", changes), now);
        return toResponse(saved);
    }

    // ma dan toc lay theo ten dan toc; ten ngoai danh muc (vd "Khac" cua ho so cu) thi dung ma gui len hoac ma dang luu
    private String ethnicityCode(String dantoc, String requestedCode, String currentCode) {
        return EthnicityCatalog.codeOf(dantoc)
                .or(() -> Optional.ofNullable(normalize(requestedCode)))
                .or(() -> Optional.ofNullable(normalize(currentCode)))
                .orElseThrow(() -> new AppException(ErrorCode.REQUEST_PARAMETER_INVALID,
                        Map.of("dantoc", "INVALID_FORMAT")));
    }

    // ma nghe nghiep lay theo ten (khong bat buoc): khong co ten thi khong co ma; ten tu do cu thi giu ma gui len / dang luu
    private String occupationCode(String nghenghiep, String requestedCode, String currentCode) {
        if (normalize(nghenghiep) == null) {
            return null;
        }
        return OccupationCatalog.codeOf(nghenghiep)
                .orElseGet(() -> Optional.ofNullable(normalize(requestedCode)).orElse(normalize(currentCode)));
    }

    // ma nuoc (Phu luc 7) lay theo ten nuoc, khong dung ma gui len.
    // Nghe nghiep la Ngoai kieu thi bat buoc chon nuoc trong danh muc; nguoi khac thi khong duoc co nuoc.
    private String countryCode(String occupationCode, String ngoaikieu, String requestedCode) {
        String country = normalize(ngoaikieu);
        if (!FOREIGNER_OCCUPATION_CODE.equals(occupationCode)) {
            if (country != null || normalize(requestedCode) != null) {
                throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("ngoaikieu", "INVALID_FORMAT"));
            }
            return null;
        }
        if (country == null) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("ngoaikieu", "REQUIRED"));
        }
        return CountryCatalog.codeOf(country).orElseThrow(() -> new AppException(
                ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("ngoaikieu", "INVALID_FORMAT")));
    }

    // cac truong le tan duoc sua (khong gom tien su, di ung - BR-23)
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

    // cung cac truong tren nhung lay tu request, chuan hoa nhu khi luu
    private Map<String, Object> requestedValues(
            UpdatePatientRequest request, String cccdSo, String hochieuSo, String currentDantocMa,
            String currentNghenghiepMa) {
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
        values.put("nghenghiepMa", occupationCode(request.nghenghiep(), request.nghenghiepMa(), currentNghenghiepMa));
        values.put("noilamviec", normalize(request.noilamviec()));
        values.put("dantoc", request.dantoc().trim());
        values.put("dantocMa", ethnicityCode(request.dantoc(), request.dantocMa(), currentDantocMa));
        values.put("ngoaikieu", normalize(request.ngoaikieu()));
        values.put("ngoaikieuMa", countryCode((String) values.get("nghenghiepMa"), request.ngoaikieu(),
                request.ngoaikieuMa()));
        values.put("yeutorh", normalize(request.yeutorh()));
        values.put("nhommau", normalize(request.nhommau()));
        values.put("hotennguoithan", normalize(request.hotennguoithan()));
        values.put("nguoithanQuanhe", normalize(request.nguoithanQuanhe()));
        values.put("diachinguoithan", normalize(request.diachinguoithan()));
        values.put("sodienthoainguoithan", normalize(request.sodienthoainguoithan()));
        return values;
    }

    // tra ve cac truong bi doi: { truong: { old, new } }
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

    // UC-35: tim kiem benh nhan, luon tra ve danh sach co phan trang
    @Override
    @Transactional(readOnly = true)
    public Page<PatientResponse> listPatients(String keyword, PatientSearchType type, Pageable pageable) {
        // mac dinh sap theo ma BN, moi trang 20 dong
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : DEFAULT_SEARCH_SORT;
        Pageable limited = PageRequest.of(pageable.getPageNumber(), PAGE_SIZE, sort);

        String normalizedKeyword = normalize(keyword);
        Page<Patient> patients;

        if (type == null) {
            // khong chon loai tim: khong co tu khoa thi lay het; co thi tim tu do
            // (ma BN, ho ten, SDT khop 1 phan; CCCD khop dung)
            patients = normalizedKeyword == null
                    ? patientRepository.findAll(limited)
                    : patientRepository.search(normalizedKeyword, limited);
        } else {
            // co chon loai tim thi bat buoc tu khoa va dung dinh dang
            if (normalizedKeyword == null) {
                throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("keyword", "REQUIRED"));
            }
            if (!isValidKeyword(normalizedKeyword, type)) {
                throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("keyword", "INVALID_FORMAT"));
            }
            // giay to (CCCD, ho chieu, BHYT) khop dung; ma BN, SDT, ho ten khop 1 phan
            patients = switch (type) {
                case CITIZEN_ID -> patientRepository.findAllByCccdSo(normalizedKeyword, limited);
                case PASSPORT -> patientRepository.findAllByHochieuSoIgnoreCase(normalizedKeyword, limited);
                case PATIENT_CODE -> patientRepository.findAllByMabenhnhanContainingIgnoreCase(normalizedKeyword, limited);
                case PHONE -> patientRepository.findAllBySodienthoaiContaining(normalizedKeyword, limited);
                case NAME -> patientRepository.searchByName(normalizedKeyword, limited);
                case INSURANCE_CARD -> patientRepository.searchByInsuranceCard(normalizedKeyword, limited);
            };
        }

        // gan them lan kham gan nhat va luot kham dang mo
        return withVisitSummary(patients);
    }

    // kiem tra dinh dang tu khoa theo loai tim
    // giay to phai nhap du so (khong lo thong tin nguoi khac); SDT tu 4 so, ho ten tu 2 ky tu
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

    // gan lan kham gan nhat + luot kham dang mo cho moi benh nhan (2 query cho ca trang)
    private Page<PatientResponse>  withVisitSummary(Page<Patient> patients) {
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
                        // 1 benh nhan co nhieu luot dang mo thi lay luot dau
                        (first, second) -> first));
        return patients.map(patient -> toResponse(
                patient, lastVisits.get(patient.getId()), activeVisits.get(patient.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public PatientResponse getPatientById(UUID id) {
        return toResponse(requiredPatient(id));
    }

    // UC-36: xem ho so benh nhan (thong tin, lich su kham, luot kham hien tai, di ung)
    @Override
    @Transactional
    public PatientOverviewResponse getPatientOverview(UUID actorAccountId, UUID id) {
        Patient patient = requiredPatient(id);
        // lich su kham, moi nhat truoc
        List<VisitRepository.PatientVisitHistory> history = visitRepository.findPatientHistory(id);
        List<PatientOverviewResponse.VisitHistoryItem> visits = history.stream()
                .map(visit -> new PatientOverviewResponse.VisitHistoryItem(
                        visit.getVisitId(), visit.getRegistrationCode(), visit.getRegisteredAt(),
                        visit.getRoomName(), doctorName(visit.getDoctorTitle(), visit.getDoctorName()),
                        visit.getVisitType(), visit.getStatus()))
                .toList();
        // the BHYT dung o lan kham gan nhat
        String lastInsuranceCard = history.stream()
                .map(VisitRepository.PatientVisitHistory::getInsuranceCard)
                .filter(card -> card != null && !card.isBlank())
                .findFirst().orElse(null);

        // luot kham dang mo + trang thai phi kham
        PatientOverviewResponse.CurrentVisit activeVisit = history.stream()
                .filter(visit -> !VisitRepository.FINISHED_STATUSES.contains(visit.getStatus()))
                .findFirst()
                .map(visit -> new PatientOverviewResponse.CurrentVisit(
                        visit.getVisitId(), visit.getRegistrationCode(), visit.getStatus(), visit.getRoomName(),
                        paymentRepository.findByVisitIdAndType(visit.getVisitId(), PaymentType.EXAMINATION_FEE)
                                .map(Payment::getStatus).orElse(null)))
                .orElse(null);

        // di ung thuoc
        List<String> drugAllergies = medicalHistoryRepository
                .findByPatientIdAndHistoryTypeOrderByCreatedAtAsc(id, MedicalHistoryType.DRUG_ALLERGY).stream()
                // bo dong "khong di ung" (khong phai di ung that)
                .filter(item -> !item.isNoKnownDrugAllergy())
                .map(MedicalHistory::getThongtinNoidung)
                .toList();

        // ghi audit moi lan xem ho so (BR-48)
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
