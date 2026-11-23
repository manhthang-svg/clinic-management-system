package clinic.backend.controller;

import java.time.Instant;
import java.util.UUID;

import clinic.backend.dto.request.LoginRequest;
import clinic.backend.entity.StaffAccount;
import clinic.backend.enums.AccountStatus;
import clinic.backend.enums.Role;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.security.PasswordHasher;
import clinic.backend.service.ResultImageStorage;
import clinic.backend.support.PostgresIntegrationTest;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** UC-67 Record Paraclinical Result and the UC-68 print counter. */
class ParaclinicalResultApiIT extends PostgresIntegrationTest {

    private static final String PASSWORD = "paraclinical-result-password";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired StaffAccountRepository accounts;
    @Autowired PasswordHasher passwordHasher;
    @Autowired UuidV7Generator ids;
    @Autowired JdbcTemplate jdbc;
    // khong goi Cloudinary that trong test
    @MockitoBean ResultImageStorage imageStorage;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13};
    private static final String IMAGE_URL = "https://res.cloudinary.com/demo/image/upload/v17/test/anh-1.png";

    @Test
    void performerUploadsCaptionsAndDeletesAnImageOfAnImagingResult() throws Exception {
        Fixture fixture = fixture("IMAGING", "Siêu âm ổ bụng tổng quát");
        String token = accessToken(fixture.technician());
        when(imageStorage.isConfigured()).thenReturn(true);
        when(imageStorage.folder()).thenReturn("test");
        when(imageStorage.upload(any(), any(), eq("image/png"))).thenReturn(IMAGE_URL);

        String body = mockMvc.perform(withToken(multipart(path(fixture) + "/images")
                                .file(new MockMultipartFile("file", "sieu-am.png", "image/png", PNG))
                                .param("caption", "Gan"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.result.files", hasSize(1)))
                .andExpect(jsonPath("$.data.result.files[0].url").value(IMAGE_URL))
                .andExpect(jsonPath("$.data.result.files[0].name").value("sieu-am.png"))
                .andExpect(jsonPath("$.data.result.files[0].caption").value("Gan"))
                .andReturn().getResponse().getContentAsString();
        String imageId = objectMapper.readTree(body).path("data").path("result").path("files").path(0).path("id").asText();

        // chu thich luu cung ban nhap
        mockMvc.perform(withToken(put(path(fixture) + "/draft"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conclusion\":\"\",\"imageCaptions\":{\"" + imageId + "\":\"Gan phải\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.files[0].caption").value("Gan phải"));

        mockMvc.perform(withToken(delete(path(fixture) + "/images/" + imageId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.files", hasSize(0)));
        verify(imageStorage).delete("test/anh-1");
    }

    @Test
    void imagesAreOnlyJpgOrPngOfImagingResultsAndNeedTheStorage() throws Exception {
        Fixture imaging = fixture("IMAGING", "Chụp X-quang ngực thẳng");
        String token = accessToken(imaging.technician());
        when(imageStorage.isConfigured()).thenReturn(false);

        mockMvc.perform(withToken(multipart(path(imaging) + "/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG)), token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FILE_STORAGE_NOT_CONFIGURED"));

        when(imageStorage.isConfigured()).thenReturn(true);
        // khai bao PNG nhung noi dung khong phai anh
        mockMvc.perform(withToken(multipart(path(imaging) + "/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", "khong phai anh".getBytes())), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESULT_IMAGE_INVALID"));

        Fixture lab = fixture("LAB", "Tổng phân tích tế bào máu");
        mockMvc.perform(withToken(multipart(path(lab) + "/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", PNG)), accessToken(lab.technician())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RESULT_IMAGE_INVALID"));
    }

    @Test
    void performerSavesADraftThenSubmitsALabResultAndTheVisitWaitsForConclusion() throws Exception {
        Fixture fixture = fixture("LAB", "Định lượng Glucose máu");
        String token = accessToken(fixture.technician());

        mockMvc.perform(withToken(get(path(fixture)), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.printForm").value("LAB"))
                .andExpect(jsonPath("$.data.isPerformer").value(true))
                .andExpect(jsonPath("$.data.status").value("PERFORMED"))
                .andExpect(jsonPath("$.data.section").value("XÉT NGHIỆM SINH HÓA"))
                .andExpect(jsonPath("$.data.parameters", hasSize(2)))
                .andExpect(jsonPath("$.data.parameters[0].ref.type").value("RANGE"))
                .andExpect(jsonPath("$.data.result").value(nullValue()))
                .andExpect(jsonPath("$.data.patient.name").value("Người Bệnh Thử"));

        String glucose = fixture.parameterCodes()[0];
        String cholesterol = fixture.parameterCodes()[1];
        mockMvc.perform(withToken(put(path(fixture) + "/draft"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"" + glucose + "\":\"11,4\"},\"conclusion\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.result.values." + glucose).value("11,4"))
                .andExpect(jsonPath("$.data.result.interpretations." + glucose).value("HIGH"));

        // EF-01: a required value and the conclusion are missing.
        mockMvc.perform(withToken(post(path(fixture) + "/commands/submit"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"" + glucose + "\":\"11,4\"},\"conclusion\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details['value:" + cholesterol + "']").exists())
                .andExpect(jsonPath("$.details.conclusion").exists());

        mockMvc.perform(withToken(post(path(fixture) + "/commands/submit"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"" + glucose + "\":\"11,4\",\"" + cholesterol + "\":\"4.1\"},"
                                + "\"conclusion\":\"Tăng đường huyết\",\"instrumentId\":\"" + fixture.labInstrument() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.result.instrumentName").value("Máy sinh hóa"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        assertThat(jdbc.queryForObject("select status from visits where id = ?", String.class, fixture.visitId()))
                .isEqualTo("WAITING_CONCLUSION");
        assertThat(jdbc.queryForObject("""
                select count(*) from lab_result_items i join lab_results r on r.id = i.lab_result_id
                where r.order_detail_id = ? and i.ketquabatthuong = '1' and i.instrument_name_snapshot = 'Máy sinh hóa'
                """, Integer.class, fixture.detailId())).isEqualTo(1);

        // BR-29: the result is locked; UC-68 counts the print.
        mockMvc.perform(withToken(put(path(fixture) + "/draft"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"conclusion\":\"Sửa\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_ALREADY_SUBMITTED"));
        // UC-68: the slip needs the clinic header; only the performer prints; every print is counted (AF-01 copy).
        mockMvc.perform(withToken(post(path(fixture) + "/commands/record-print"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLINIC_FACILITY_NOT_CONFIGURED"));
        UUID facility = UUID.randomUUID();
        jdbc.update("""
                insert into clinic_facility (id, ma_cskcb, ten_cskcb, diachi, sodienthoai, so_giay_phep_hoat_dong,
                    hinh_thuc_to_chuc, created_at, updated_at, version)
                values (?, '79999', 'Phòng khám Thử', '1 Đường Số 1', '0281234567', 'GP-001', 'PHONG_KHAM_DA_KHOA',
                    now(), now(), 0)
                """, facility);
        try {
            mockMvc.perform(withToken(post(path(fixture) + "/commands/record-print"),
                            accessToken(account(Role.TECHNICIAN))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(withToken(post(path(fixture) + "/commands/record-print"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.printCount").value(1))
                    .andExpect(jsonPath("$.data.clinic.name").value("Phòng khám Thử"))
                    .andExpect(jsonPath("$.data.registrationCode").value(org.hamcrest.Matchers.startsWith("DK-")));
            mockMvc.perform(withToken(post(path(fixture) + "/commands/record-print"), token))
                    .andExpect(jsonPath("$.data.printCount").value(2));
            assertThat(jdbc.queryForObject(
                    "select count(*) from audit_logs where entity_id = ? and action = 'PRINT'",
                    Integer.class, fixture.detailId())).isEqualTo(2);
        } finally {
            // Only one clinic facility may exist; other suites create their own.
            jdbc.update("delete from clinic_facility where id = ?", facility);
        }
    }

    @Test
    void performerSubmitsAnUltrasoundResultWithItsInstrument() throws Exception {
        Fixture fixture = fixture("IMAGING", "Siêu âm ổ bụng tổng quát");
        String token = accessToken(fixture.technician());

        mockMvc.perform(withToken(get(path(fixture)), token))
                .andExpect(jsonPath("$.data.printForm").value("ULTRASOUND"))
                .andExpect(jsonPath("$.data.instruments", hasSize(1)))
                .andExpect(jsonPath("$.data.instruments[0].code").value(fixture.imagingInstrumentCode()));

        // A LAB instrument cannot record an imaging result (V54).
        mockMvc.perform(withToken(put(path(fixture) + "/draft"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conclusion\":\"\",\"instrumentId\":\"" + fixture.labInstrument() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUMENT_UNAVAILABLE"));

        String ultrasound = "{\"description\":\"Gan: bình thường\",\"sections\":{\"gan\":\"Bình thường\"},"
                + "\"conclusion\":\"Siêu âm ổ bụng bình thường\",\"readingDoctor\":\"BS Lan\","
                + "\"instrumentId\":\"" + fixture.imagingInstrument() + "\"}";

        // An imaging result needs at least one image.
        mockMvc.perform(withToken(post(path(fixture) + "/commands/submit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(ultrasound))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.images").exists());

        when(imageStorage.isConfigured()).thenReturn(true);
        when(imageStorage.folder()).thenReturn("test");
        when(imageStorage.upload(any(), any(), eq("image/png"))).thenReturn(IMAGE_URL);
        mockMvc.perform(withToken(multipart(path(fixture) + "/images")
                        .file(new MockMultipartFile("file", "sieu-am.png", "image/png", PNG)), token))
                .andExpect(status().isOk());

        mockMvc.perform(withToken(post(path(fixture) + "/commands/submit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(ultrasound))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.result.sections.gan").value("Bình thường"))
                .andExpect(jsonPath("$.data.result.readingDoctor").value("BS Lan"))
                .andExpect(jsonPath("$.data.result.instrumentName").value("Máy siêu âm"));
    }

    @Test
    void anotherTechnicianSeesTheFormReadOnlyAndCannotSave() throws Exception {
        Fixture fixture = fixture("IMAGING", "Chụp X-quang ngực thẳng");
        String token = accessToken(account(Role.TECHNICIAN));

        mockMvc.perform(withToken(get(path(fixture)), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.printForm").value("XRAY"))
                .andExpect(jsonPath("$.data.isPerformer").value(false));
        mockMvc.perform(withToken(put(path(fixture) + "/draft"), token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"conclusion\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(withToken(post(path(fixture) + "/commands/record-print"), token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_NOT_SUBMITTED"));
    }

    @Test
    void onlyTechniciansUseTheResultForm() throws Exception {
        Fixture fixture = fixture("IMAGING", "Chụp CT sọ não");
        for (Role role : new Role[] {Role.DOCTOR, Role.RECEPTIONIST, Role.CASHIER, Role.ADMIN}) {
            mockMvc.perform(withToken(get(path(fixture)), accessToken(account(role))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(path(fixture))).andExpect(status().isUnauthorized());
        mockMvc.perform(withToken(get("/api/v1/paraclinical-results/" + UUID.randomUUID()),
                        accessToken(fixture.technician())))
                .andExpect(status().isNotFound());
    }

    @Test
    void aCtResultNeedsItsTechnique() throws Exception {
        Fixture fixture = fixture("IMAGING", "Chụp cắt lớp vi tính sọ não");
        mockMvc.perform(withToken(post(path(fixture) + "/commands/submit"), accessToken(fixture.technician()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Không thấy tổn thương\",\"conclusion\":\"Bình thường\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.technique").exists());
    }

    @Test
    void siblingServicesOfTheSameRoomShowTheirResultStatusAndCanEachBeSubmitted() throws Exception {
        Fixture fixture = fixture("LAB", "Bộ đường huyết (Glucose, HbA1c)");
        String token = accessToken(fixture.technician());
        UUID second = sibling(fixture, "Đo hoạt độ AST, ALT");
        String first = path(fixture);
        String other = "/api/v1/paraclinical-results/" + second;
        String glucose = fixture.parameterCodes()[0];
        String cholesterol = fixture.parameterCodes()[1];

        mockMvc.perform(withToken(put(first + "/draft"), token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"values\":{\"" + glucose + "\":\"5\"},\"conclusion\":\"\"}"))
                .andExpect(status().isOk());

        // The tabs of the result form: the draft is no longer shown as "waiting for entry".
        mockMvc.perform(withToken(get(other), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siblingServices", hasSize(2)))
                .andExpect(jsonPath("$.data.siblingServices[?(@.id == '" + fixture.detailId() + "')].status")
                        .value("DRAFT"))
                .andExpect(jsonPath("$.data.siblingServices[?(@.id == '" + second + "')].status")
                        .value("PERFORMED"));

        String body = "{\"values\":{\"" + glucose + "\":\"5\",\"" + cholesterol + "\":\"4\"},"
                + "\"conclusion\":\"Bình thường\"}";
        mockMvc.perform(withToken(post(other + "/commands/submit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siblingServices[?(@.id == '" + second + "')].status")
                        .value("COMPLETED"));
        mockMvc.perform(withToken(post(first + "/commands/submit"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    // ---- fixture -----------------------------------------------------------------------------------------------

    private record Fixture(StaffAccount technician, UUID detailId, UUID visitId, String[] parameterCodes,
                           UUID labInstrument, UUID imagingInstrument, String imagingInstrumentCode) {
    }

    private static String path(Fixture fixture) {
        return "/api/v1/paraclinical-results/" + fixture.detailId();
    }

    private Fixture fixture(String serviceType, String serviceName) {
        String suffix = suffix();
        StaffAccount technician = account(Role.TECHNICIAN);
        StaffAccount doctor = account(Role.DOCTOR);
        Instant now = Instant.now();
        UUID department = UUID.randomUUID();
        jdbc.update("""
                insert into departments (id, makhoa, tenkhoa, type, created_at, updated_at, version)
                values (?, ?, ?, 'PARACLINICAL', now(), now(), 0)
                """, department, "CLS-" + suffix, "Khoa CLS " + suffix);
        UUID group = UUID.randomUUID();
        jdbc.update("""
                insert into service_groups (id, loaimachidinh, loaichidinh, type, department_id, created_at, updated_at,
                    version)
                values (?, ?, ?, ?, ?, now(), now(), 0)
                """, group, "NH-" + suffix, "LAB".equals(serviceType) ? "Xét nghiệm sinh hóa" : "Chẩn đoán hình ảnh",
                serviceType, department);
        UUID room = UUID.randomUUID();
        jdbc.update("""
                insert into rooms (id, department_id, service_group_id, maphongkham, phongkham, location, type, status,
                    created_at, updated_at, version)
                values (?, ?, ?, ?, 'Phòng CLS', 'Tầng 2', 'PARACLINICAL', 'ACTIVE', now(), now(), 0)
                """, room, department, group, "CLS-" + suffix);
        UUID service = UUID.randomUUID();
        jdbc.update("""
                insert into services (id, code, tenchidinh, type, department_id, service_group_id, clinic_price, status,
                    created_at, updated_at, version)
                values (?, ?, ?, ?, ?, ?, 50000, 'ACTIVE', now(), now(), 0)
                """, service, "DV-" + suffix, serviceName, serviceType, department, group);

        String[] codes = new String[0];
        if ("LAB".equals(serviceType)) {
            codes = new String[] {"GLU" + suffix, "CHOL" + suffix};
            insertParameter(service, codes[0], "Glucose", 1, "3.9", "6.4");
            insertParameter(service, codes[1], "Cholesterol", 2, "3.0", "5.2");
        }
        UUID labInstrument = insertInstrument(room, "MSH-" + suffix, "Máy sinh hóa", "LAB");
        String imagingCode = "MSA-" + suffix;
        UUID imagingInstrument = insertInstrument(room, imagingCode, "Máy siêu âm", "IMAGING");

        UUID patient = UUID.randomUUID();
        jdbc.update("""
                insert into patients (id, mabenhnhan, hoten, ngaysinh, gioitinh, diachi, created_by_account_id,
                    created_at, updated_at, version)
                values (?, ?, 'Người Bệnh Thử', date '1980-01-01', 1, '1 Đường Số 1', ?, now(), now(), 0)
                """, patient, "BN-" + suffix, doctor.getId());
        UUID visit = UUID.randomUUID();
        jdbc.update("""
                insert into visits (id, sovaovien, patient_id, room_id, registered_by_account_id, thoigianvaovien,
                    doituongbn_loai, lydovaovien, queue_date, queue_number, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, now(), 'DV', 'Đau bụng', current_date, 1, 'WAITING_PARACLINICAL',
                    now(), now(), 0)
                """, visit, "DK-" + suffix, patient, room, doctor.getId());
        // An own record type: ApprovedSchemaIT deletes the seeded NGOAITRU type, so no record may point at it.
        String recordType = "CLS-" + suffix;
        jdbc.update("""
                insert into medical_record_types (loaiba, ten, pham_vi, van_ban, is_default, created_at, updated_at,
                    version)
                values (?, 'Bệnh án ngoại trú', 'OUTPATIENT', '32/2023/TT-BYT', false, now(), now(), 0)
                """, recordType);
        UUID record = UUID.randomUUID();
        jdbc.update("""
                insert into medical_records (id, visit_id, soba, loaiba, status, opened_at, opened_by_account_id,
                    created_at, updated_at, version)
                values (?, ?, ?, ?, 'OPEN', now(), ?, now(), now(), 0)
                """, record, visit, "BA-" + suffix, recordType, doctor.getId());
        UUID payment = UUID.randomUUID();
        jdbc.update("""
                insert into payments (id, payment_code, visit_id, type, status, total_amount,
                    insurance_total_amount, patient_total_amount, paid_at, created_at, updated_at, version)
                values (?, ?, ?, 'PARACLINICAL_FEE', 'PAID', 50000, 0, 50000, now(), now(), now(), 0)
                """, payment, "HD-" + suffix, visit);
        UUID order = UUID.randomUUID();
        jdbc.update("""
                insert into paraclinical_orders (id, sophieu, medical_record_id, doctor_account_id, payment_id,
                    chandoansobo, doituongbn, noichidinh, tongcong, status, created_at, updated_at, version)
                values (?, ?, ?, ?, ?, 'E11 – Đái tháo đường', 2, 'Phòng khám Nội', 50000, 'RELEASED',
                    now(), now(), 0)
                """, order, "PCD-" + suffix, record, doctor.getId(), payment);
        UUID detail = UUID.randomUUID();
        jdbc.update("""
                insert into paraclinical_order_details (id, order_id, service_id, machidinh, ngaychidinh, tenchidinh,
                    dongia, thanhtien, benhnhanphaitra, room_id, queue_number, status, performer_account_id,
                    released_at, accepted_at, performed_at, created_at, updated_at, version)
                values (?, ?, ?, ?, now(), ?, 50000, 50000, 50000, ?, 1, 'PENDING_RESULT', ?, now(), now(), now(),
                    now(), now(), 0)
                """, detail, order, service, "CD-" + suffix, serviceName, room, technician.getId());
        return new Fixture(technician, detail, visit, codes, labInstrument, imagingInstrument, imagingCode);
    }

    /** Another performed service of the same order, in the same room and with the same service. */
    private UUID sibling(Fixture fixture, String serviceName) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into paraclinical_order_details (id, order_id, service_id, machidinh, ngaychidinh, tenchidinh,
                    dongia, thanhtien, benhnhanphaitra, room_id, queue_number, status, performer_account_id,
                    released_at, accepted_at, performed_at, created_at, updated_at, version)
                select ?, order_id, service_id, ?, now(), ?, 50000, 50000, 50000, room_id, 1, 'PENDING_RESULT',
                    performer_account_id, now(), now(), now(), now(), now(), 0
                from paraclinical_order_details where id = ?
                """, id, "CD-" + suffix(), serviceName, fixture.detailId());
        return id;
    }

    private void insertParameter(UUID service, String code, String name, int order, String low, String high) {
        UUID parameter = UUID.randomUUID();
        jdbc.update("""
                insert into lab_parameters (id, code, name, value_type, unit) values (?, ?, ?, 'NUMERIC', 'mmol/L')
                """, parameter, code, name);
        jdbc.update("""
                insert into service_lab_parameters (service_id, parameter_id, section_name, group_name, display_order)
                values (?, ?, 'XÉT NGHIỆM SINH HÓA', null, ?)
                """, service, parameter, order);
        jdbc.update("""
                insert into lab_reference_ranges (parameter_id, reference_type, lower_bound, upper_bound,
                    effective_from, source, is_active)
                values (?, 'RANGE', ?::numeric, ?::numeric, date '2020-01-01', 'Kiểm thử', true)
                """, parameter, low, high);
    }

    private UUID insertInstrument(UUID room, String code, String name, String resultType) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into paraclinical_instruments (id, code, name, result_type, room_id, created_at, updated_at,
                    version)
                values (?, ?, ?, ?, ?, now(), now(), 0)
                """, id, code, name, resultType, room);
        return id;
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private StaffAccount account(Role role) {
        String username = role.name().toLowerCase() + "." + suffix();
        Instant now = Instant.now();
        return accounts.saveAndFlush(StaffAccount.builder()
                .id(ids.next())
                .username(username)
                .passwordHash(passwordHasher.hash(PASSWORD))
                .fullName("Account " + username)
                .phoneNumber(String.format("09%08d", Math.abs(username.hashCode() % 100_000_000L)))
                .role(role)
                .status(AccountStatus.ACTIVE)
                .passwordChangeRequired(false)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    private String accessToken(StaffAccount account) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(account.getUsername(), PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return json.get("data").get("accessToken").asText();
    }
}
