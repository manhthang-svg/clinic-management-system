
package clinic.backend.service.impl;

import clinic.backend.dto.request.CreatePatientRequest;
import clinic.backend.dto.response.PatientResponse;
import clinic.backend.entity.Patient;
import clinic.backend.repository.PatientRepository;
import clinic.backend.service.PatientService;
import clinic.backend.utils.UuidV7Generator;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final UuidV7Generator uuidV7Generator;

    public PatientServiceImpl(
            PatientRepository patientRepository,
            UuidV7Generator uuidV7Generator
    ) {
        this.patientRepository = patientRepository;
        this.uuidV7Generator = uuidV7Generator;
    }

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
                savedPatient.getYeutorh(),
                savedPatient.getNhommau(),
                savedPatient.getHotennguoithan(),
                savedPatient.getNguoithanQuanhe(),
                savedPatient.getDiachinguoithan(),
                savedPatient.getSodienthoainguoithan()
        );
    }
}
