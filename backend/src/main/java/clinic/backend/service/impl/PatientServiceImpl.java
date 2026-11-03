
package clinic.backend.service.impl;

import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.request.UpdatePatientRequest;
import clinic.backend.dto.response.PatientActiveVisitResponse;
import clinic.backend.dto.response.PatientResponse;
import clinic.backend.entity.Patient;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.PatientSearchType;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.PatientService;
import clinic.backend.utils.UuidV7Generator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    /** UC-35 step 6: at most 50 results. */
    static final int MAX_SEARCH_RESULTS = 50;

    private final PatientRepository patientRepository;
    private final VisitRepository visitRepository;
    private final UuidV7Generator uuidV7Generator;

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

        if (cccdSo != null && patientRepository.existsByCccdSo(cccdSo)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "CCCD đã tồn tại trong hệ thống"
            );
        }

        String mabenhnhan = generatePatientCode();

        Patient patient = new Patient(
                uuidV7Generator.next(),
                Instant.now(),
                mabenhnhan,
                cccdSo,
                normalize(request.hochieuSo()),
                request.hoten(),
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
                null,
                null
        );
    }

    @Override
    @Transactional
    public PatientResponse updatePatient(UUID id, UpdatePatientRequest request) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy bệnh nhân"
                ));

        if (!patient.getVersion().equals(request.version())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Thông tin bệnh nhân đã được cập nhật bởi người khác"
            );
        }

        String cccdSo = normalize(request.cccdSo());
        if (cccdSo != null && patientRepository.existsByCccdSoAndIdNot(cccdSo, id)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "CCCD đã tồn tại trong hệ thống"
            );
        }

        patient.updateAdministrativeInfo(
                cccdSo,
                normalize(request.hochieuSo()),
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
                Instant.now()
        );

        return toResponse(patientRepository.save(patient));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PatientResponse> listPatients(String keyword, PatientSearchType type, Pageable pageable) {
        Pageable limited = pageable.getPageSize() > MAX_SEARCH_RESULTS
                ? PageRequest.of(pageable.getPageNumber(), MAX_SEARCH_RESULTS, pageable.getSort())
                : pageable;
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
                case PATIENT_CODE -> patientRepository.findAllByMabenhnhanIgnoreCase(normalizedKeyword, limited);
                case PHONE -> patientRepository.findAllBySodienthoai(normalizedKeyword, limited);
                case NAME -> patientRepository.searchByName(normalizedKeyword, limited);
                case INSURANCE_CARD -> patientRepository.searchByInsuranceCard(normalizedKeyword, limited);
            };
        }
        return withVisitSummary(patients);
    }

    /** UC-35 step 4: keyword format by search type. */
    private static boolean isValidKeyword(String keyword, PatientSearchType type) {
        return switch (type) {
            case CITIZEN_ID -> keyword.matches("\\d{12}");
            case PASSPORT -> keyword.matches("[A-Za-z0-9]{1,15}");
            case PHONE -> keyword.matches("\\d{10}");
            case NAME -> keyword.length() >= 2 && keyword.length() <= 100;
            case INSURANCE_CARD -> keyword.matches("[A-Za-z0-9]{15}");
            case PATIENT_CODE -> true;
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
    @Transactional
    public PatientResponse getPatientById(UUID id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy bệnh nhân"
                ));
        return toResponse(patient);
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
