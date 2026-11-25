package clinic.backend.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
import clinic.backend.enums.PaymentStatus;
import clinic.backend.enums.PaymentType;
import clinic.backend.enums.VisitStatus;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.MedicalHistoryRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.PaymentRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.utils.UuidV7Generator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class PatientServiceImplTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID PATIENT_ID = UUID.randomUUID();

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private MedicalHistoryRepository medicalHistoryRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private UuidV7Generator ids;

    private PatientServiceImpl service;

    /** 03/11/2026 in the clinic time zone. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-03T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new PatientServiceImpl(
                patientRepository, visitRepository, paymentRepository, medicalHistoryRepository, auditLogService, ids,
                CLOCK);
    }

    @Test
    void requiresPhoneFromEighteenYearsOld() {

        assertThatThrownBy(() -> service.createPatient(ACCOUNT_ID, 
                createRequest("Nguyễn Văn A", LocalDate.of(2008, 11, 3), null, null, null, null, null)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getDetails()).containsExactly(Map.entry("sodienthoai", "REQUIRED")));
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void requiresGuardianNameAndRelationshipUnderEighteen() {

        assertThatThrownBy(() -> service.createPatient(ACCOUNT_ID, 
                createRequest("Nguyễn Bé", LocalDate.of(2008, 11, 4), null, null, null, null, null)))
                .isInstanceOfSatisfying(AppException.class, error -> assertThat(error.getDetails())
                        .containsOnlyKeys("hotennguoithan", "nguoithanQuanhe", "sodienthoainguoithan"));
    }

    @Test
    void rejectsBirthDateMoreThan120YearsAgo() {

        assertThatThrownBy(() -> service.createPatient(ACCOUNT_ID, 
                createRequest("Cụ A", LocalDate.of(1906, 11, 2), null, null, "0901234567", null, null)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getDetails()).containsEntry("ngaysinh", "INVALID_FORMAT"));
    }

    @Test
    void rejectsExistingPassportWithTheProfileToOpen() {
        when(patientRepository.findFirstByHochieuSoIgnoreCase("C1234567")).thenReturn(Optional.of(patient()));

        assertThatThrownBy(() -> service.createPatient(ACCOUNT_ID, 
                createRequest("John Smith", LocalDate.of(1990, 1, 1), null, "C1234567", "0901234567", null, null)))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PATIENT_IDENTIFICATION_EXISTS);
                    assertThat(error.getDetails()).containsEntry("field", "hochieuSo")
                            .containsEntry("patientId", PATIENT_ID.toString())
                            .containsEntry("patientCode", "BN1234567");
                });
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void drawsAnotherPatientCodeWhileTheCodeIsUsed() {
        when(ids.next()).thenReturn(PATIENT_ID);
        when(patientRepository.existsByMabenhnhan(any())).thenReturn(true, true, false);
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createPatient(ACCOUNT_ID, createRequest("Nguyễn Văn A", null, null, "0901234567"));

        verify(patientRepository, org.mockito.Mockito.times(3)).existsByMabenhnhan(any());
    }

    @Test
    void findsPossibleDuplicatesByNameAndBirthDate() {
        when(patientRepository.findPossibleDuplicates("Nguyen Van A", LocalDate.of(1990, 1, 1)))
                .thenReturn(List.of(patient()));

        var duplicates = service.findPossibleDuplicates(" Nguyen Van A ", LocalDate.of(1990, 1, 1));

        assertThat(duplicates).extracting(PatientResponse::mabenhnhan).containsExactly("BN1234567");
    }

    private CreatePatientRequest createRequest(String name, LocalDate birthDate, String citizenId, String passport,
                                               String phone, String guardianName, String guardianRelation) {
        return new CreatePatientRequest(name, birthDate, (short) 1, citizenId, passport, null, null, "Kinh", "01",
                null, null, "Ha Noi", null, null, null, null, null, phone, null, null, null, guardianName,
                guardianRelation, null, null);
    }

    @Test
    void rejectsADuplicateCheckWithANameLongerThan100Characters() {
        assertThatThrownBy(() -> service.findPossibleDuplicates("a".repeat(101), LocalDate.of(1990, 1, 1)))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getDetails()).containsEntry("hoten", "TOO_LONG"));
    }

    @Test
    void overviewShowsHistoryActiveVisitAllergiesAndRecordsTheView() {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient()));
        UUID activeId = UUID.randomUUID();
        VisitRepository.PatientVisitHistory active = history(activeId, "VV002", VisitStatus.WAITING_FEE_PAYMENT, null,
                null, null);
        VisitRepository.PatientVisitHistory done = history(UUID.randomUUID(), "VV001", VisitStatus.COMPLETED,
                "ThS.", "Trần Bình", "DN4010123456789");
        when(visitRepository.findPatientHistory(PATIENT_ID)).thenReturn(List.of(active, done));
        Payment fee = mock(Payment.class);
        when(fee.getStatus()).thenReturn(PaymentStatus.PENDING);
        when(paymentRepository.findByVisitIdAndType(activeId, PaymentType.EXAMINATION_FEE))
                .thenReturn(Optional.of(fee));
        MedicalHistory allergy = mock(MedicalHistory.class);
        when(allergy.getThongtinNoidung()).thenReturn("Penicillin");
        when(medicalHistoryRepository.findByPatientIdAndHistoryTypeOrderByCreatedAtAsc(
                PATIENT_ID, MedicalHistoryType.DRUG_ALLERGY)).thenReturn(List.of(allergy));

        var overview = service.getPatientOverview(ACCOUNT_ID, PATIENT_ID);

        assertThat(overview.patient().id()).isEqualTo(PATIENT_ID);
        assertThat(overview.drugAllergies()).containsExactly("Penicillin");
        assertThat(overview.lastInsuranceCard()).isEqualTo("DN4010123456789");
        assertThat(overview.activeVisit().registrationCode()).isEqualTo("VV002");
        assertThat(overview.activeVisit().examinationFeeStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(overview.visits()).extracting(PatientOverviewResponse.VisitHistoryItem::registrationCode)
                .containsExactly("VV002", "VV001");
        assertThat(overview.visits().get(1).doctorName()).isEqualTo("ThS. Trần Bình");
        verify(auditLogService).recordBusinessChange(eq(ACCOUNT_ID), eq(AuditLogAction.VIEW_RECORD), eq("patients"),
                eq(PATIENT_ID), any(), any(), any());
    }

    @Test
    void overviewWithoutVisitsHasNoActiveVisit() {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient()));

        var overview = service.getPatientOverview(ACCOUNT_ID, PATIENT_ID);

        assertThat(overview.visits()).isEmpty();
        assertThat(overview.activeVisit()).isNull();
        assertThat(overview.lastInsuranceCard()).isNull();
    }

    @Test
    void overviewOfMissingPatientIsNotFoundAndNotAudited() {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPatientOverview(ACCOUNT_ID, PATIENT_ID))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PATIENT_NOT_FOUND));
        verifyNoInteractions(auditLogService);
    }

    private static VisitRepository.PatientVisitHistory history(UUID id, String code, VisitStatus status,
                                                               String doctorTitle, String doctorName, String card) {
        VisitRepository.PatientVisitHistory visit = mock(VisitRepository.PatientVisitHistory.class);
        org.mockito.Mockito.lenient().when(visit.getVisitId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(visit.getRegistrationCode()).thenReturn(code);
        org.mockito.Mockito.lenient().when(visit.getStatus()).thenReturn(status);
        org.mockito.Mockito.lenient().when(visit.getDoctorTitle()).thenReturn(doctorTitle);
        org.mockito.Mockito.lenient().when(visit.getDoctorName()).thenReturn(doctorName);
        org.mockito.Mockito.lenient().when(visit.getInsuranceCard()).thenReturn(card);
        return visit;
    }

    @Test
    void createsPatientWithNormalizedOptionalFieldsAndAuthenticatedCreator() {
        when(ids.next()).thenReturn(PATIENT_ID);
        when(patientRepository.findByCccdSo("012345678901")).thenReturn(Optional.empty());
        when(patientRepository.save(any(Patient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createPatient(ACCOUNT_ID, createRequest(
                " Nguyễn Văn A ",
                " 012345678901 ",
                "  Nghề nghiệp  ",
                "  0901234567  "));

        assertThat(response.id()).isEqualTo(PATIENT_ID);
        assertThat(response.hoten()).isEqualTo("Nguyễn Văn A");
        assertThat(response.cccdSo()).isEqualTo("012345678901");
        assertThat(response.nghenghiep()).isEqualTo("Nghề nghiệp");
        assertThat(response.sodienthoai()).isEqualTo("0901234567");
        assertThat(response.email()).isEqualTo("patient@example.com");
        verify(patientRepository).save(any(Patient.class));
        verify(auditLogService).recordBusinessChange(eq(ACCOUNT_ID), eq(AuditLogAction.CREATE), eq("patients"),
                eq(PATIENT_ID), any(), any(), any());
    }

    @Test
    void derivesEthnicityCodeFromTheNameIgnoringTheSentCode() {
        when(ids.next()).thenReturn(PATIENT_ID);
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createPatient(ACCOUNT_ID, ethnicityRequest(" Mường ", "99"));

        assertThat(response.dantocMa()).isEqualTo("06");
    }

    @Test
    void createsWithoutAnEthnicityCode() {
        when(ids.next()).thenReturn(PATIENT_ID);
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createPatient(ACCOUNT_ID, ethnicityRequest("Kinh", null)).dantocMa()).isEqualTo("01");
    }

    @Test
    void rejectsAnUnlistedEthnicityWithoutACode() {
        assertThatThrownBy(() -> service.createPatient(ACCOUNT_ID, ethnicityRequest("Khác", null)))
                .isInstanceOfSatisfying(AppException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.REQUEST_PARAMETER_INVALID);
                    assertThat(ex.getDetails()).containsEntry("dantoc", "INVALID_FORMAT");
                });
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void updateWithoutAnEthnicityCodeKeepsTheDerivedCodeAndSavesNothing() {
        Patient patient = storedPatient();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(patientRepository.findByCccdSo("012345678901")).thenReturn(Optional.of(patient));

        service.updatePatient(ACCOUNT_ID, PATIENT_ID, new UpdatePatientRequest("Nguyen Van A",
                LocalDate.of(1990, 1, 1), (short) 1, "012345678901", null, null, null, "Kinh", null, null, null,
                "Ha Noi", null, null, null, null, null, "0901234567", "patient@example.com", null, null, null,
                null, null, null, 0L));

        verify(patientRepository, never()).save(any(Patient.class));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void derivesOccupationCodeFromTheName() {
        when(ids.next()).thenReturn(PATIENT_ID);
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createPatient(ACCOUNT_ID, occupationRequest("Công nhân", "77"));

        assertThat(response.nghenghiepMa()).isEqualTo("04");
    }

    @Test
    void leavesOccupationCodeEmptyWithoutAnOccupation() {
        when(ids.next()).thenReturn(PATIENT_ID);
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createPatient(ACCOUNT_ID, occupationRequest(null, "04")).nghenghiepMa()).isNull();
    }

    @Test
    void keepsTheSentCodeForAnOldFreeTextOccupation() {
        when(ids.next()).thenReturn(PATIENT_ID);
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.createPatient(ACCOUNT_ID, occupationRequest("Kỹ sư", "07")).nghenghiepMa()).isEqualTo("07");
    }

    @Test
    void derivesCountryCodeForAForeignerFromTheCountryName() {
        when(ids.next()).thenReturn(PATIENT_ID);
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createPatient(ACCOUNT_ID, foreignerRequest("Ngoại kiều", "Nhật", "77"));

        assertThat(response.ngoaikieu()).isEqualTo("Nhật");
        assertThat(response.ngoaikieuMa()).isEqualTo("16");
    }

    @Test
    void requiresACountryForAForeigner() {
        assertThatThrownBy(() -> service.createPatient(ACCOUNT_ID, foreignerRequest("Ngoại kiều", null, null)))
                .isInstanceOf(AppException.class)
                .extracting("details").isEqualTo(Map.of("ngoaikieu", "REQUIRED"));
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void rejectsACountryOutsideTheList() {
        assertThatThrownBy(() -> service.createPatient(ACCOUNT_ID, foreignerRequest("Ngoại kiều", "Úc", "22")))
                .isInstanceOf(AppException.class)
                .extracting("details").isEqualTo(Map.of("ngoaikieu", "INVALID_FORMAT"));
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void rejectsACountryWhenTheOccupationIsNotForeigner() {
        assertThatThrownBy(() -> service.createPatient(ACCOUNT_ID, foreignerRequest("Công nhân", "Nhật", null)))
                .isInstanceOf(AppException.class)
                .extracting("details").isEqualTo(Map.of("ngoaikieu", "INVALID_FORMAT"));
        assertThatThrownBy(() -> service.createPatient(ACCOUNT_ID, foreignerRequest(null, null, "16")))
                .isInstanceOf(AppException.class)
                .extracting("details").isEqualTo(Map.of("ngoaikieu", "INVALID_FORMAT"));
        verify(patientRepository, never()).save(any(Patient.class));
    }

    private CreatePatientRequest foreignerRequest(String nghenghiep, String ngoaikieu, String ngoaikieuMa) {
        return new CreatePatientRequest("Nguyễn Văn A", LocalDate.of(1990, 1, 1), (short) 1, null, null, nghenghiep,
                null, "Kinh", null, ngoaikieu, ngoaikieuMa, "Ha Noi", null, null, null, null, null, "0901234567",
                null, null, null, null, null, null, null);
    }

    private CreatePatientRequest occupationRequest(String nghenghiep, String nghenghiepMa) {
        return new CreatePatientRequest("Nguyễn Văn A", LocalDate.of(1990, 1, 1), (short) 1, null, null, nghenghiep,
                nghenghiepMa, "Kinh", null, null, null, "Ha Noi", null, null, null, null, null, "0901234567", null,
                null, null, null, null, null, null);
    }

    private CreatePatientRequest ethnicityRequest(String dantoc, String dantocMa) {
        return new CreatePatientRequest("Nguyễn Văn A", LocalDate.of(1990, 1, 1), (short) 1, null, null, null, null,
                dantoc, dantocMa, null, null, "Ha Noi", null, null, null, null, null, "0901234567", null, null,
                null, null, null, null, null);
    }

    @Test
    void rejectsDuplicateCitizenIdBeforeCreatingPatient() {
        when(patientRepository.findByCccdSo("012345678901")).thenReturn(Optional.of(patient()));

        assertThatThrownBy(() -> service.createPatient(ACCOUNT_ID, createRequest(
                "Nguyễn Văn A", "012345678901", null, "0901234567")))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PATIENT_IDENTIFICATION_EXISTS);
                    assertThat(error.getDetails()).containsEntry("field", "cccdSo");
                });

        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void rejectsStalePatientVersionBeforeUpdating() {
        Patient patient = mock(Patient.class);
        when(patient.getVersion()).thenReturn(1L);
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.updatePatient(
                ACCOUNT_ID,
                PATIENT_ID,
                updateRequest(patient.getVersion() + 1)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CONCURRENT_MODIFICATION));

        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void updatesChangedFieldsAndRecordsOldAndNewValues() {
        Patient patient = storedPatient();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(patientRepository.findByCccdSo("012345678901")).thenReturn(Optional.of(patient));
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.updatePatient(ACCOUNT_ID, PATIENT_ID, updateRequest(0L));

        assertThat(response.hoten()).isEqualTo("Nguyen Van B");
        ArgumentCaptor<Map<String, ?>> values = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).recordBusinessChange(eq(ACCOUNT_ID), eq(AuditLogAction.UPDATE), eq("patients"),
                eq(PATIENT_ID), any(), values.capture(), any());
        @SuppressWarnings("unchecked")
        Map<String, Map<String, String>> changes = (Map<String, Map<String, String>>) values.getValue().get("changes");
        assertThat(changes).containsOnlyKeys("hoten", "email");
        assertThat(changes.get("hoten")).containsEntry("old", "Nguyen Van A").containsEntry("new", "Nguyen Van B");
    }

    @Test
    void savesNothingWhenNothingChanged() {
        Patient patient = storedPatient();
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(patientRepository.findByCccdSo("012345678901")).thenReturn(Optional.of(patient));

        service.updatePatient(ACCOUNT_ID, PATIENT_ID, unchangedRequest());

        verify(patientRepository, never()).save(any(Patient.class));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsACitizenIdOfAnotherPatient() {
        Patient patient = storedPatient();
        Patient other = mock(Patient.class);
        when(other.getId()).thenReturn(UUID.randomUUID());
        when(other.getMabenhnhan()).thenReturn("BN7");
        when(other.getHoten()).thenReturn("Trần Thị B");
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(patient));
        when(patientRepository.findByCccdSo("012345678901")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updatePatient(ACCOUNT_ID, PATIENT_ID, updateRequest(0L)))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PATIENT_IDENTIFICATION_EXISTS);
                    assertThat(error.getDetails()).containsEntry("patientCode", "BN7");
                });
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    void appliesTheAgeRulesOnUpdate() {
        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(storedPatient()));
        UpdatePatientRequest request = updateRequest(0L);
        UpdatePatientRequest withoutPhone = new UpdatePatientRequest(request.hoten(), request.ngaysinh(),
                request.gioitinh(), request.cccdSo(), null, null, null, "Kinh", "01", null, null, "Ha Noi",
                null, null, null, null, null, null, null, null, null, null, null, null, null, 0L);

        assertThatThrownBy(() -> service.updatePatient(ACCOUNT_ID, PATIENT_ID, withoutPhone))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getDetails()).containsEntry("sodienthoai", "REQUIRED"));
    }

    private Patient storedPatient() {
        Patient patient = patient();
        org.springframework.test.util.ReflectionTestUtils.setField(patient, "version", 0L);
        return patient;
    }

    /** The same values as {@link #patient()}, so the update changes nothing. */
    private UpdatePatientRequest unchangedRequest() {
        return new UpdatePatientRequest("Nguyen Van A", LocalDate.of(1990, 1, 1), (short) 1, "012345678901", null,
                null, null, "Kinh", "01", null, null, "Ha Noi", null, null, null, null, null, "0901234567",
                "patient@example.com", null, null, null, null, null, null, 0L);
    }

    @Test
    void searchesWithTrimmedKeywordAndMapsResults() {
        Patient patient = patient();
        when(patientRepository.search(
                org.mockito.ArgumentMatchers.eq("Nguyen"),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(patient)));

        var response = service.listPatients(
                "  Nguyen  ",
                null,
                org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(response).hasSize(1);
        assertThat(response.getContent().getFirst().id()).isEqualTo(PATIENT_ID);
        verify(patientRepository).search(
                org.mockito.ArgumentMatchers.eq("Nguyen"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void searchesByTypeWithTheMatchingQuery() {
        PageRequest page = PageRequest.of(0, 20, Sort.by("mabenhnhan"));
        Page<Patient> found = new PageImpl<>(List.of(patient()));
        when(patientRepository.findAllByCccdSo("012345678901", page)).thenReturn(found);
        when(patientRepository.findAllByHochieuSoIgnoreCase("C1234567", page)).thenReturn(found);
        when(patientRepository.searchByInsuranceCard("DN4010123456789", page)).thenReturn(found);
        when(patientRepository.findAllByMabenhnhanContainingIgnoreCase("01", page)).thenReturn(found);
        when(patientRepository.findAllBySodienthoaiContaining("4567", page)).thenReturn(found);
        when(patientRepository.searchByName("an", page)).thenReturn(found);

        // Document numbers in full, exact match.
        assertThat(service.listPatients(" 012345678901 ", PatientSearchType.CITIZEN_ID, page)).hasSize(1);
        assertThat(service.listPatients("C1234567", PatientSearchType.PASSPORT, page)).hasSize(1);
        assertThat(service.listPatients("DN4010123456789", PatientSearchType.INSURANCE_CARD, page)).hasSize(1);
        // What a patient remembers: any part.
        assertThat(service.listPatients("01", PatientSearchType.PATIENT_CODE, page)).hasSize(1);
        assertThat(service.listPatients("4567", PatientSearchType.PHONE, page)).hasSize(1);
        assertThat(service.listPatients("an", PatientSearchType.NAME, page)).hasSize(1);
    }

    @Test
    void addsLastVisitAndActiveVisitToEachResult() {
        PageRequest page = PageRequest.of(0, 20, Sort.by("mabenhnhan"));
        when(patientRepository.searchByName("nguyen", page)).thenReturn(new PageImpl<>(List.of(patient())));
        Instant lastVisitAt = Instant.parse("2026-11-01T02:00:00Z");
        VisitRepository.PatientLastVisit lastVisit = mock(VisitRepository.PatientLastVisit.class);
        when(lastVisit.getPatientId()).thenReturn(PATIENT_ID);
        when(lastVisit.getLastVisitAt()).thenReturn(lastVisitAt);
        VisitRepository.PatientActiveVisit activeVisit = mock(VisitRepository.PatientActiveVisit.class);
        when(activeVisit.getPatientId()).thenReturn(PATIENT_ID);
        when(activeVisit.getRegistrationCode()).thenReturn("VV001");
        when(activeVisit.getStatus()).thenReturn(VisitStatus.WAITING);
        when(activeVisit.getRoomName()).thenReturn("Phòng khám Nội 1");
        when(visitRepository.findLastVisitTimes(List.of(PATIENT_ID))).thenReturn(List.of(lastVisit));
        when(visitRepository.findActiveVisits(List.of(PATIENT_ID), VisitRepository.FINISHED_STATUSES))
                .thenReturn(List.of(activeVisit));

        var result = service.listPatients("nguyen", PatientSearchType.NAME, page).getContent().getFirst();

        assertThat(result.lastVisitAt()).isEqualTo(lastVisitAt);
        assertThat(result.activeVisit())
                .isEqualTo(new PatientActiveVisitResponse("VV001", VisitStatus.WAITING, "Phòng khám Nội 1"));
    }

    @ParameterizedTest
    @CsvSource({
            "CITIZEN_ID, 0790500",
            "CITIZEN_ID, 0123456789012",
            "CITIZEN_ID, 01234567890A",
            "PHONE, 090",
            "PHONE, 09012345678",
            "PASSPORT, C123456789012345",
            "PASSPORT, C-123",
            "INSURANCE_CARD, DN40",
            "PATIENT_CODE, BN#0001",
            "NAME, A"
    })
    void rejectsKeywordWithWrongFormatForItsType(PatientSearchType type, String keyword) {
        assertThatThrownBy(() -> service.listPatients(keyword, type, PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(AppException.class, error -> {
                    assertThat(error.getErrorCode()).isEqualTo(ErrorCode.REQUEST_VALIDATION_FAILED);
                    assertThat(error.getDetails()).containsEntry("keyword", "INVALID_FORMAT");
                });
        verifyNoInteractions(patientRepository);
    }

    @Test
    void requiresKeywordWhenSearchTypeIsGiven() {
        assertThatThrownBy(() -> service.listPatients("  ", PatientSearchType.NAME, PageRequest.of(0, 10)))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getDetails()).containsEntry("keyword", "REQUIRED"));
    }

    @Test
    void returnsTwentyPatientsPerPageWhateverSizeIsAsked() {
        when(patientRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.listPatients(null, null, PageRequest.of(2, 500));
        service.listPatients(null, null, PageRequest.of(0, 5));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(patientRepository, org.mockito.Mockito.times(2)).findAll(pageable.capture());
        assertThat(pageable.getAllValues().get(0).getPageSize()).isEqualTo(20);
        assertThat(pageable.getAllValues().get(0).getPageNumber()).isEqualTo(2);
        assertThat(pageable.getAllValues().get(1).getPageSize()).isEqualTo(20);
    }

    @Test
    void ordersPagesByPatientCodeUnlessTheClientSorts() {
        when(patientRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        service.listPatients(null, null, PageRequest.of(1, 20));
        service.listPatients(null, null, PageRequest.of(0, 20, Sort.by("hoten")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(patientRepository, org.mockito.Mockito.times(2)).findAll(pageable.capture());
        assertThat(pageable.getAllValues().get(0)).isEqualTo(PageRequest.of(1, 20, Sort.by("mabenhnhan")));
        assertThat(pageable.getAllValues().get(1).getSort()).isEqualTo(Sort.by("hoten"));
    }

    private Patient patient() {
        return new Patient(
                PATIENT_ID,
                java.time.Instant.parse("2026-10-28T01:00:00Z"),
                "BN1234567",
                "012345678901",
                null,
                "Nguyen Van A",
                LocalDate.of(1990, 1, 1),
                (short) 1,
                null,
                null,
                "Kinh",
                "01",
                null,
                null,
                "Ha Noi",
                null,
                null,
                null,
                null,
                null,
                "0901234567",
                "patient@example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                ACCOUNT_ID);
    }

    private CreatePatientRequest createRequest(
            String name,
            String citizenId,
            String occupation,
            String phone) {
        return new CreatePatientRequest(
                name,
                LocalDate.of(1990, 1, 1),
                (short) 1,
                citizenId,
                null,
                occupation,
                null,
                "Kinh",
                "01",
                null,
                null,
                "Ha Noi",
                null,
                null,
                null,
                null,
                null,
                phone,
                "patient@example.com",
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private UpdatePatientRequest updateRequest(Long version) {
        return new UpdatePatientRequest(
                "Nguyen Van B",
                LocalDate.of(1990, 1, 1),
                (short) 1,
                "012345678901",
                null,
                null,
                null,
                "Kinh",
                "01",
                null,
                null,
                "Ha Noi",
                null,
                null,
                null,
                null,
                null,
                "0901234567",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                version);
    }
}
