package clinic.backend.service.impl;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import clinic.backend.dto.request.SaveParaclinicalResultRequest;
import clinic.backend.dto.response.ParaclinicalResultResponse;
import clinic.backend.entity.ImagingResult;
import clinic.backend.entity.ImagingResultImage;
import clinic.backend.entity.LabParameter;
import clinic.backend.entity.LabReferenceRange;
import clinic.backend.entity.LabResult;
import clinic.backend.entity.LabResultItem;
import clinic.backend.entity.MedicalService;
import clinic.backend.entity.ParaclinicalInstrument;
import clinic.backend.entity.ParaclinicalOrder;
import clinic.backend.entity.ParaclinicalOrderDetail;
import clinic.backend.entity.Patient;
import clinic.backend.entity.ServiceLabParameter;
import clinic.backend.entity.StaffAccount;
import clinic.backend.entity.Visit;
import clinic.backend.enums.AuditLogAction;
import clinic.backend.enums.ErrorCode;
import clinic.backend.enums.InstrumentResultType;
import clinic.backend.enums.LabParameterValueType;
import clinic.backend.enums.LabReferenceType;
import clinic.backend.enums.MedicalServiceType;
import clinic.backend.enums.ParaclinicalOrderDetailStatus;
import clinic.backend.enums.ResultForm;
import clinic.backend.enums.VisitDoituongbnLoai;
import clinic.backend.exceptions.AppException;
import clinic.backend.repository.ClinicFacilityRepository;
import clinic.backend.repository.DepartmentRepository;
import clinic.backend.repository.ImagingResultImageRepository;
import clinic.backend.repository.ImagingResultRepository;
import clinic.backend.repository.LabParameterRepository;
import clinic.backend.repository.LabReferenceRangeRepository;
import clinic.backend.repository.LabResultRepository;
import clinic.backend.repository.MedicalRecordRepository;
import clinic.backend.repository.MedicalServiceRepository;
import clinic.backend.repository.ParaclinicalInstrumentRepository;
import clinic.backend.repository.ParaclinicalOrderDetailRepository;
import clinic.backend.repository.PatientRepository;
import clinic.backend.repository.RoomRepository;
import clinic.backend.repository.ServiceGroupRepository;
import clinic.backend.repository.ServiceLabParameterRepository;
import clinic.backend.repository.StaffAccountRepository;
import clinic.backend.repository.VisitRepository;
import clinic.backend.service.AuditLogService;
import clinic.backend.service.ParaclinicalProgressService;
import clinic.backend.service.ParaclinicalResultService;
import clinic.backend.service.ResultImageStorage;
import clinic.backend.utils.UuidV7Generator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

// UC-67 nhap ket qua CLS + UC-68 ghi nhan in phieu
// KTV nhap tay, chi KTV thuc hien duoc sua, gui roi thi khoa (BR-29)
@Slf4j
@Service
@RequiredArgsConstructor
public class ParaclinicalResultServiceImpl implements ParaclinicalResultService {

    private static final String AUDIT_ENTITY = "paraclinical_order_details";
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String EMERGENCY = "cấp cứu";
    // anh ket qua: toi da 10 anh, moi anh 10 MB, chi JPG / PNG (quyet dinh 16/11/2026)
    // cac muc mo ta bat buoc cua phieu co nhieu muc (khop voi frontend resultSections.js)
    static final Map<ResultForm, List<String>> REQUIRED_SECTIONS = Map.of(
            ResultForm.ENDOSCOPY, List.of("thucQuan", "tamVi", "phinhVi", "thanVi", "haiBoCong", "hangVi", "monVi",
                    "hanhTaTrang", "taTrang"),
            ResultForm.ECG, List.of("huyetAp", "chuyenDao", "nhip", "truc", "p", "pq", "qrs", "st", "t", "qt",
                    "truocTim"));
    static final int MAX_IMAGES = 10;
    static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;

    private final ParaclinicalOrderDetailRepository orderDetailRepository;
    private final LabResultRepository labResultRepository;
    private final ImagingResultRepository imagingResultRepository;
    private final ImagingResultImageRepository imageRepository;
    private final ResultImageStorage resultImageStorage;
    private final MedicalServiceRepository medicalServiceRepository;
    private final ServiceGroupRepository serviceGroupRepository;
    private final ServiceLabParameterRepository serviceLabParameterRepository;
    private final LabParameterRepository labParameterRepository;
    private final LabReferenceRangeRepository labReferenceRangeRepository;
    private final ParaclinicalInstrumentRepository instrumentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final VisitRepository visitRepository;
    private final PatientRepository patientRepository;
    private final StaffAccountRepository staffAccountRepository;
    private final RoomRepository roomRepository;
    private final DepartmentRepository departmentRepository;
    private final ClinicFacilityRepository clinicFacilityRepository;
    private final ParaclinicalProgressService paraclinicalProgressService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final UuidV7Generator ids;
    private final Clock clock;

    // [UC-67 buoc 2-17] MO PHIEU: tai phieu ket qua de xem / nhap (GET /paraclinical-results/{id})
    @Override
    @Transactional(readOnly = true)
    public ParaclinicalResultResponse get(UUID technicianAccountId, UUID orderDetailId) {
        ParaclinicalOrderDetail detail = findDetail(orderDetailId);
        // KTV ngoài ca trực hoặc không phải người thực hiện vẫn có thể xem phiếu ở chế độ chỉ xem (isPerformer = false)
        return toResponse(load(detail), technicianAccountId);
    }

    @Override
    @Transactional(readOnly = true)
    // bac si xem ket qua da gui (chi xem)
    public ParaclinicalResultResponse viewForDoctor(UUID doctorAccountId, UUID orderDetailId) {
        Context context = load(orderDetailId);
        boolean ownOrder = doctorAccountId.equals(context.order().getDoctorAccountId());
        boolean ownVisit = context.visit().map(visit -> doctorAccountId.equals(visit.getDoctorAccountId())).orElse(false);
        if (!ownOrder && !ownVisit) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        if (context.detail().getStatus() != ParaclinicalOrderDetailStatus.COMPLETED) {
            throw new AppException(ErrorCode.RESULT_NOT_SUBMITTED);
        }
        return toResponse(context, doctorAccountId, false);
    }

    // [UC-67 buoc 24-25] LUU NHAP: luu do dang, khong bat nhap du, sua lai duoc (PUT .../draft)
    @Override
    @Transactional
    public ParaclinicalResultResponse saveDraft(
            UUID technicianAccountId, UUID orderDetailId, SaveParaclinicalResultRequest request) {
        Context context = load(orderDetailId);
        requireEditableBy(context, technicianAccountId);
        Instant now = clock.instant();
        record(context, technicianAccountId, request, false, now);
        audit(technicianAccountId, context.detail(), "SAVE_RESULT_DRAFT", now);
        return toResponse(load(orderDetailId), technicianAccountId);
    }

    // [UC-67 buoc 26-51] GUI KET QUA: kiem tra du -> luu + khoa -> dich vu hoan thanh -> (xong het) luot cho ket luan
    @Override
    @Transactional
    public ParaclinicalResultResponse submit(
            UUID technicianAccountId, UUID orderDetailId, SaveParaclinicalResultRequest request) {
        // tai du lieu phieu: dich vu, phieu chi dinh
        Context context = load(orderDetailId);
        // dich vu chua o trang thai cho ket qua -> RESULT_NOT_EDITABLE (409)
        requireEditableBy(context, technicianAccountId);
        Instant now = clock.instant();
        validateForSubmit(context, request);
        // (may khong thuoc phong / sai loai -> INSTRUMENT_UNAVAILABLE, buoc 41-43)
        record(context, technicianAccountId, request, true, now);

        // dich vu -> HOAN THANH (buoc 45)
        ParaclinicalOrderDetail detail = context.detail();
        detail.completeWithResult(technicianAccountId, now);
        orderDetailRepository.save(detail);
        context.visit().ifPresent(paraclinicalProgressService::moveToConclusionIfDone);
        audit(technicianAccountId, detail, "SUBMIT_RESULT", now);
        return toResponse(load(orderDetailId), technicianAccountId);
    }

    // [UC-68] GHI NHAN IN PHIEU: chi in ket qua da gui, chi KTV thuc hien; tang so lan in + ghi log PRINT
    @Override
    @Transactional
    public ParaclinicalResultResponse recordPrint(UUID technicianAccountId, UUID orderDetailId) {
        Context context = load(orderDetailId);
        requirePerformer(context.detail(), technicianAccountId);
        Instant now = clock.instant();
        // chi in ket qua da gui, chi KTV thuc hien duoc in, phai co thong tin co so de in tieu de
        if (!isSubmitted(context)) {
            throw new AppException(ErrorCode.RESULT_NOT_SUBMITTED);
        }
        if (clinicFacilityRepository.findCurrent().isEmpty()) {
            throw new AppException(ErrorCode.CLINIC_FACILITY_NOT_CONFIGURED);
        }
        if (context.isLab()) {
            LabResult result = labResultRepository.findByOrderDetailId(orderDetailId)
                    .filter(LabResult::isSubmitted)
                    .orElseThrow(() -> new AppException(ErrorCode.RESULT_NOT_SUBMITTED));
            result.recordPrint(now);
            labResultRepository.save(result);
        } else {
            ImagingResult result = imagingResultRepository.findByOrderDetailId(orderDetailId)
                    .filter(ImagingResult::isSubmitted)
                    .orElseThrow(() -> new AppException(ErrorCode.RESULT_NOT_SUBMITTED));
            result.recordPrint(now);
            imagingResultRepository.save(result);
        }
        auditLogService.recordBusinessChange(technicianAccountId, AuditLogAction.PRINT, AUDIT_ENTITY,
                orderDetailId, context.detail().getMachidinh(), Map.of("action", "PRINT_RESULT"), now);
        return toResponse(load(orderDetailId), technicianAccountId);
    }

    // tai anh len (UC-67): kiem tra -> day len Cloudinary -> luu link vao DB
    @Override
    @Transactional
    public ParaclinicalResultResponse uploadImage(
            UUID technicianAccountId, UUID orderDetailId, String fileName, String contentType, byte[] content,
            String caption) {
        Context context = load(orderDetailId);
        requireEditableBy(context, technicianAccountId);
        // xet nghiem khong co anh
        if (context.isLab()) {
            throw new AppException(ErrorCode.RESULT_IMAGE_INVALID, Map.of("file", "LAB_RESULT"));
        }
        if (!resultImageStorage.isConfigured()) {
            throw new AppException(ErrorCode.FILE_STORAGE_NOT_CONFIGURED);
        }
        String type = imageType(contentType, content);
        if (content.length > MAX_IMAGE_BYTES) {
            throw new AppException(ErrorCode.RESULT_IMAGE_INVALID, Map.of("file", "TOO_LARGE"));
        }

        Instant now = clock.instant();
        ParaclinicalOrderDetail detail = context.detail();
        // chua co ket qua nhap thi tao ban nhap de gan anh vao
        ImagingResult result = imagingResultRepository.findByOrderDetailId(detail.getId())
                .orElseGet(() -> imagingResultRepository.save(ImagingResult.start(ids.next(), now, detail.getId(),
                        technicianAccountId, detail.getMachidinh(), context.order().getChandoansobo())));
        List<ImagingResultImage> images = imageRepository.findByImagingResultIdOrderByDisplayOrderAsc(result.getId());
        if (images.size() >= MAX_IMAGES) {
            throw new AppException(ErrorCode.RESULT_IMAGE_LIMIT_REACHED);
        }
        int nextOrder = images.stream().map(ImagingResultImage::getDisplayOrder).filter(Objects::nonNull)
                .max(Integer::compare).orElse(0) + 1;

        UUID imageId = ids.next();
        String publicId = resultImageStorage.folder() + "/" + imageId;
        String url = resultImageStorage.upload(publicId, content, type);
        // loi DB sau khi da up thi xoa anh tren Cloudinary cho khoi rac
        afterRollback(() -> resultImageStorage.delete(publicId));

        imageRepository.save(ImagingResultImage.uploaded(imageId, result.getId(), url, safeFileName(fileName), type,
                sha256(content), technicianAccountId, now, nextOrder, trimTo(caption, 255)));
        audit(technicianAccountId, detail, "UPLOAD_RESULT_IMAGE", now);
        return toResponse(load(orderDetailId), technicianAccountId);
    }

    // xoa anh (UC-67): chi khi ket qua chua gui
    @Override
    @Transactional
    public ParaclinicalResultResponse deleteImage(UUID technicianAccountId, UUID orderDetailId, UUID imageId) {
        Context context = load(orderDetailId);
        requireEditableBy(context, technicianAccountId);
        ImagingResult result = imagingResultRepository.findByOrderDetailId(orderDetailId)
                .orElseThrow(() -> new AppException(ErrorCode.RESULT_IMAGE_NOT_FOUND));
        ImagingResultImage image = imageRepository.findByIdAndImagingResultId(imageId, result.getId())
                .orElseThrow(() -> new AppException(ErrorCode.RESULT_IMAGE_NOT_FOUND));
        imageRepository.delete(image);
        audit(technicianAccountId, context.detail(), "DELETE_RESULT_IMAGE", clock.instant());
        // DB xoa xong moi xoa tren Cloudinary; xoa loi thi chi ghi log
        String publicId = publicIdOf(image.getHinhanh());
        afterCommit(() -> {
            try {
                resultImageStorage.delete(publicId);
            } catch (RuntimeException exception) {
                log.warn("Result image left in the file storage [imageId={}]", imageId);
            }
        });
        return toResponse(load(orderDetailId), technicianAccountId);
    }

    // chi nhan JPG / PNG: kiem ca content type lan noi dung file (magic bytes)
    private static String imageType(String contentType, byte[] content) {
        if (content == null || content.length == 0) {
            throw new AppException(ErrorCode.RESULT_IMAGE_INVALID, Map.of("file", "EMPTY"));
        }
        boolean png = content.length > 8 && (content[0] & 0xFF) == 0x89 && content[1] == 'P' && content[2] == 'N'
                && content[3] == 'G';
        boolean jpeg = content.length > 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8
                && (content[2] & 0xFF) == 0xFF;
        if ("image/png".equals(contentType) && png) {
            return "image/png";
        }
        if (("image/jpeg".equals(contentType) || "image/jpg".equals(contentType)) && jpeg) {
            return "image/jpeg";
        }
        throw new AppException(ErrorCode.RESULT_IMAGE_INVALID, Map.of("file", "TYPE"));
    }

    // lay ten file (bo duong dan), toi da 255 ky tu
    private static String safeFileName(String fileName) {
        String name = fileName == null ? "" : fileName.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        return name.isEmpty() ? "anh" : trimTo(name, 255);
    }

    private static String trimTo(String text, int max) {
        String value = trim(text);
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    // ma bam noi dung anh
    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    // link Cloudinary .../image/upload/v123/<public_id>.png -> <public_id>
    static String publicIdOf(String url) {
        String path = url.substring(url.indexOf("/upload/") + "/upload/".length());
        path = path.replaceFirst("^v\\d+/", "");
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(0, dot) : path;
    }

    // chay sau khi transaction commit thanh cong
    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    // chay khi transaction bi rollback (vd xoa anh vua up)
    private void afterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        action.run();
                    } catch (RuntimeException exception) {
                        log.warn("Uploaded result image could not be removed after a rollback");
                    }
                }
            }
        });
    }

    // ---- tai du lieu -------------------------------------------------------------------------------------------

    // goi du lieu dung chung cho 1 phieu: chi tiet chi dinh, phieu, dich vu, mau phieu, luot kham, benh nhan
    private record Context(ParaclinicalOrderDetail detail, ParaclinicalOrder order, MedicalService service,
                           ResultForm form, Optional<Visit> visit, Optional<Patient> patient) {

        boolean isLab() {
            return form == ResultForm.LAB;
        }

        InstrumentResultType instrumentType() {
            return isLab() ? InstrumentResultType.LAB : InstrumentResultType.IMAGING;
        }
    }

    // tai Context tu id chi tiet chi dinh: dich vu, phieu chi dinh, luot kham, benh nhan (khong co -> loi 404)
    private Context load(UUID orderDetailId) {
        return load(findDetail(orderDetailId));
    }

    private ParaclinicalOrderDetail findDetail(UUID orderDetailId) {
        return orderDetailRepository.findById(orderDetailId)
                .orElseThrow(() -> new AppException(ErrorCode.PARACLINICAL_SERVICE_NOT_FOUND));
    }

    private Context load(ParaclinicalOrderDetail detail) {
        MedicalService service = medicalServiceRepository.findById(detail.getServiceId())
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        ParaclinicalOrder order = detail.getOrder();
        Optional<Visit> visit = medicalRecordRepository.findById(order.getMedicalRecordId())
                .flatMap(record -> visitRepository.findById(record.getVisitId()));
        Optional<Patient> patient = visit.flatMap(found -> patientRepository.findById(found.getPatientId()));
        return new Context(detail, order, service, resultFormOf(service), visit, patient);
    }

    // TBD: danh muc dich vu chua co truong mau phieu nen chon theo loai + ten dich vu
    // chon MAU PHIEU theo loai dich vu: xet nghiem / sieu am / noi soi / dien tim / MRI / CT / X-quang (theo ten)
    static ResultForm resultFormOf(MedicalService service) {
        if (service.getType() == MedicalServiceType.LAB) {
            return ResultForm.LAB;
        }
        String name = fold(service.getTenchidinh());
        if (name.contains("sieu am")) {
            return ResultForm.ULTRASOUND;
        }
        if (name.contains("noi soi")) {
            return ResultForm.ENDOSCOPY;
        }
        if (name.contains("dien tim") || name.contains("dien tam do") || name.matches(".*\\becg\\b.*")) {
            return ResultForm.ECG;
        }
        if (name.contains("cong huong tu") || name.matches(".*\\bmri\\b.*")) {
            return ResultForm.MRI;
        }
        if (name.contains("cat lop") || name.matches(".*\\bct\\b.*")) {
            return ResultForm.CT;
        }
        return ResultForm.XRAY;
    }

    // chuyen chu thuong + bo dau tieng Viet de so tu khoa trong ten dich vu ("Sieu am" -> "sieu am")
    private static String fold(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT).replace('đ', 'd');
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    // ---- quy tac -----------------------------------------------------------------------------------------------

    // KIEM TRA QUYEN SUA (buoc 29-37): chua gui (BR-29), dung KTV thuc hien, dich vu dang cho ket qua
    private void requireEditableBy(Context context, UUID technicianAccountId) {
        ParaclinicalOrderDetail detail = context.detail();
        requirePerformer(detail, technicianAccountId);
        if (isSubmitted(context)) {
            throw new AppException(ErrorCode.RESULT_ALREADY_SUBMITTED);
        }
        if (!detail.awaitsResultFrom(technicianAccountId)) {
            throw new AppException(ErrorCode.RESULT_NOT_EDITABLE);
        }
    }

    private static void requirePerformer(ParaclinicalOrderDetail detail, UUID technicianAccountId) {
        if (!technicianAccountId.equals(detail.getPerformerAccountId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    // ket qua cua dich vu nay da GUI chua (tim trong bang xet nghiem hoac hinh anh)
    private boolean isSubmitted(Context context) {
        UUID detailId = context.detail().getId();
        return context.isLab()
                ? labResultRepository.findByOrderDetailId(detailId).map(LabResult::isSubmitted).orElse(false)
                : imagingResultRepository.findByOrderDetailId(detailId).map(ImagingResult::isSubmitted).orElse(false);
    }

    // ket qua hinh anh cua dich vu nay da co anh tai len chua
    private boolean hasImages(Context context) {
        return imagingResultRepository.findByOrderDetailId(context.detail().getId())
                .map(result -> !imageRepository.findByImagingResultIdOrderByDisplayOrderAsc(result.getId()).isEmpty())
                .orElse(false);
    }

    // KIEM TRA DU LIEU KHI GUI (buoc 38-40): chi so bat buoc, so hop le, mo ta, ky thuat (CT/MRI), ket luan
    private void validateForSubmit(Context context, SaveParaclinicalResultRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (context.isLab()) {
            Map<String, String> values = Objects.requireNonNullElse(request.values(), Map.of());
            for (LabRow row : labRows(context)) {
                String value = trim(values.get(row.parameter().getCode()));
                if (value == null) {
                    if (row.link().isRequired()) {
                        errors.put("value:" + row.parameter().getCode(), "Vui lòng nhập kết quả");
                    }
                } else if (row.parameter().getValueType() == LabParameterValueType.NUMERIC && parseNumber(value) == null) {
                    errors.put("value:" + row.parameter().getCode(), "Kết quả phải là số");
                }
            }
        } else {
            List<String> requiredSections = REQUIRED_SECTIONS.get(context.form());
            if (requiredSections != null) {
                // noi soi / dien tim: moi muc mo ta deu bat buoc (giong FE)
                Map<String, String> sections = Objects.requireNonNullElse(request.sections(), Map.of());
                for (String key : requiredSections) {
                    if (trim(sections.get(key)) == null) {
                        errors.put("section:" + key, "Bắt buộc");
                    }
                }
            } else if (trim(request.description()) == null) {
                errors.put("description", "Vui lòng nhập mô tả kết quả");
            }
            if (List.of(ResultForm.CT, ResultForm.MRI).contains(context.form()) && trim(request.technique()) == null) {
                errors.put("technique", "Vui lòng nhập kỹ thuật");
            }
            // chan doan hinh anh / tham do chuc nang phai co anh: it nhat 1 anh tai len hoac link xem anh (PACS, CT/MRI)
            // xet nghiem thi chi nhap chi so, khong co anh
            if (trim(request.imageLink()) == null && !hasImages(context)) {
                errors.put("images", "Vui lòng tải lên ít nhất 1 ảnh kết quả hoặc nhập link xem ảnh");
            }
        }
        if (trim(request.conclusion()) == null) {
            errors.put("conclusion", "Vui lòng nhập kết luận");
        }
        if (!errors.isEmpty()) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, errors);
        }
    }

    // KIEM TRA MAY (buoc 41-43): may phai thuoc phong + dung loai, sai -> INSTRUMENT_UNAVAILABLE; khong chon -> rong
    private Optional<ParaclinicalInstrument> instrumentFor(Context context, UUID instrumentId) {
        if (instrumentId == null) {
            return Optional.empty();
        }
        ParaclinicalInstrument instrument = instrumentRepository.findById(instrumentId)
                .filter(found -> found.usableFor(context.detail().getRoomId(), context.instrumentType()))
                .orElseThrow(() -> new AppException(ErrorCode.INSTRUMENT_UNAVAILABLE,
                        Map.of("instrumentId", "Máy thực hiện đã chọn hiện không còn khả dụng")));
        return Optional.of(instrument);
    }

    // ---- ghi du lieu -------------------------------------------------------------------------------------------

    // [UC-67 buoc 44] LUU KET QUA: dung chung nhap (submit=false) va gui (submit=true); chua co thi tao, co roi thi sua
    private void record(Context context, UUID technicianAccountId, SaveParaclinicalResultRequest request,
                        boolean submit, Instant now) {
        ParaclinicalOrderDetail detail = context.detail();
        // gio nhan mau xet nghiem khong duoc o tuong lai (luu nhap va gui deu kiem tra)
        if (context.isLab() && request.receivedAt() != null && request.receivedAt().isAfter(now)) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    Map.of("receivedAt", "Giờ nhận mẫu không được sau thời điểm hiện tại"));
        }
        // kiem tra may KTV chon: phai thuoc phong + dung loai, sai -> INSTRUMENT_UNAVAILABLE (buoc 41-43)
        Optional<ParaclinicalInstrument> instrument = instrumentFor(context, request.instrumentId());
        UUID instrumentId = instrument.map(ParaclinicalInstrument::getId).orElse(null);
        String instrumentName = instrument.map(ParaclinicalInstrument::getName).orElse(null);
        // chan doan so bo bac si ghi luc chi dinh (UC-49) -> in len phieu ket qua
        String diagnosis = context.order().getChandoansobo();

        if (context.isLab()) {
            // tim ket qua cua dich vu nay; chua co (lan dau) thi tao moi, da co nhap thi dung lai
            LabResult result = labResultRepository.findByOrderDetailId(detail.getId())
                    .orElseGet(() -> LabResult.start(ids.next(), now, detail.getId(), technicianAccountId,
                            detail.getMachidinh(), diagnosis));
            // ghi: gio nhan mau, ket luan, gia tri tung chi so (kem cao/thap theo khoang tham chieu, may)
            result.record(request.receivedAt(), trim(request.conclusion()),
                    labValues(context, request, instrumentId, instrumentName), now);
            if (submit) {
                result.submit(now);
            }
            labResultRepository.save(result);
        } else {
            ImagingResult result = imagingResultRepository.findByOrderDetailId(detail.getId())
                    .orElseGet(() -> ImagingResult.start(ids.next(), now, detail.getId(), technicianAccountId,
                            detail.getMachidinh(), diagnosis));
            // ghi noi dung phieu: ky thuat, mo ta, cac muc mo ta (luu dang JSON), ket luan, de nghi,
            // link hinh anh, bac si doc ket qua, may thuc hien
            result.record(new ImagingResult.Content(
                    trim(request.technique()), trim(request.description()), sectionsJson(request.sections()),
                    trim(request.conclusion()), trim(request.recommendation()), trim(request.imageLink()),
                    trim(request.readingDoctor()), instrumentId, instrumentName), now);
            if (submit) {
                result.submit(now);
            }
            ImagingResult saved = imagingResultRepository.save(result);
            // luu chu thich anh gui kem
            if (request.imageCaptions() != null && !request.imageCaptions().isEmpty()) {
                imageRepository.findByImagingResultIdOrderByDisplayOrderAsc(saved.getId()).stream()
                        .filter(image -> request.imageCaptions().containsKey(image.getId()))
                        .forEach(image -> image.changeCaption(trimTo(request.imageCaptions().get(image.getId()), 255)));
            }
        }
    }

    // doi gia tri KTV nhap thanh danh sach chi so xet nghiem de luu (kem don vi, khoang tham chieu, cao/thap, may)
    private List<LabResultItem.Value> labValues(Context context, SaveParaclinicalResultRequest request,
                                                UUID instrumentId, String instrumentName) {
        Map<String, String> values = Objects.requireNonNullElse(request.values(), Map.of());
        String groupLabel = serviceGroupRepository.findById(Objects.requireNonNullElse(
                        context.service().getServiceGroupId(), new UUID(0, 0)))
                .map(group -> group.getServiceGroupName()).orElse(null);
        List<LabResultItem.Value> rows = new ArrayList<>();
        for (LabRow row : labRows(context)) {
            String text = trim(values.get(row.parameter().getCode()));
            if (text == null) {
                continue;
            }
            BigDecimal number = row.parameter().getValueType() == LabParameterValueType.NUMERIC ? parseNumber(text) : null;
            String qualitative = row.parameter().getValueType() == LabParameterValueType.QUALITATIVE ? text : null;
            rows.add(new LabResultItem.Value(
                    ids.next(), row.link().getId(), row.parameter().getCode(), row.parameter().getName(),
                    row.parameter().getUnit(), referenceText(row.range()), text, number, qualitative,
                    interpret(row.parameter(), row.range(), text), row.link().getDisplayOrder(),
                    row.link().getSectionName(), Objects.requireNonNullElse(row.link().getGroupName(),
                    context.service().getTenchidinh()), groupLabel, instrumentId, instrumentName));
        }
        return rows;
    }

    // gom cac muc mo ta (gan, than, lach...) thanh 1 chuoi JSON de luu vao 1 cot
    private String sectionsJson(Map<String, String> sections) {
        if (sections == null || sections.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sections);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write the result sections", exception);
        }
    }

    // doc nguoc chuoi JSON cac muc mo ta ra Map de tra ve FE
    private Map<String, String> sectionsOf(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    // ghi log thao tac tren chi dinh (SAVE_RESULT_DRAFT / SUBMIT_RESULT) vao audit_logs
    private void audit(UUID actor, ParaclinicalOrderDetail detail, String action, Instant now) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("action", action);
        values.put("status", detail.getStatus().name());
        auditLogService.recordBusinessChange(actor, AuditLogAction.UPDATE, AUDIT_ENTITY, detail.getId(),
                detail.getMachidinh(), values, now);
    }

    // ---- chi so xet nghiem va khoang tham chieu ----------------------------------------------------------------

    // 1 dong chi so xet nghiem: lien ket dich vu-chi so, chi so, khoang tham chieu ap dung
    private record LabRow(ServiceLabParameter link, LabParameter parameter, LabReferenceRange range) {
    }

    // lay cac chi so cua dich vu xet nghiem + khoang tham chieu hop voi benh nhan (truy van theo lo)
    private List<LabRow> labRows(Context context) {
        List<ServiceLabParameter> links = serviceLabParameterRepository
                .findAllByServiceIdOrderByDisplayOrderAsc(context.service().getId()).stream()
                .filter(ServiceLabParameter::isActive)
                .toList();
        Map<UUID, LabParameter> parameters = labParameterRepository
                .findAllByIdInAndActiveTrue(links.stream().map(ServiceLabParameter::getParameterId).toList())
                .stream().collect(Collectors.toMap(LabParameter::getId, Function.identity()));
        Map<UUID, List<LabReferenceRange>> ranges = labReferenceRangeRepository
                .findAllByParameterIdInAndActiveTrueOrderByEffectiveFromAsc(parameters.keySet()).stream()
                .collect(Collectors.groupingBy(LabReferenceRange::getParameterId));
        LocalDate onDate = LocalDate.ofInstant(context.detail().getNgaychidinh(), CLINIC_ZONE);
        Optional<Patient> patient = context.patient();
        return links.stream()
                .filter(link -> parameters.containsKey(link.getParameterId()))
                .map(link -> new LabRow(link, parameters.get(link.getParameterId()),
                        rangeFor(ranges.getOrDefault(link.getParameterId(), List.of()), onDate, patient)))
                .toList();
    }

    // chon KHOANG THAM CHIEU dung: con hieu luc ngay chi dinh, dung gioi tinh + tuoi; cu the nhat duoc chon
    private static LabReferenceRange rangeFor(List<LabReferenceRange> ranges, LocalDate onDate, Optional<Patient> patient) {
        Short sex = patient.map(found -> (short) found.getGioitinh()).orElse(null);
        Long ageDays = patient.map(Patient::getNgaysinh).filter(Objects::nonNull)
                .map(birth -> ChronoUnit.DAYS.between(birth, onDate)).orElse(null);
        return ranges.stream()
                .filter(range -> !range.getEffectiveFrom().isAfter(onDate))
                .filter(range -> range.getEffectiveTo() == null || !range.getEffectiveTo().isBefore(onDate))
                .filter(range -> range.getSex() == null || range.getSex().equals(sex))
                .filter(range -> ageDays == null
                        || (range.getAgeMinDays() == null || ageDays >= range.getAgeMinDays())
                        && (range.getAgeMaxDays() == null || ageDays <= range.getAgeMaxDays()))
                .max(Comparator.comparing((LabReferenceRange range) -> range.getSex() != null)
                        .thenComparing(range -> range.getAgeMinDays() != null || range.getAgeMaxDays() != null)
                        .thenComparing(LabReferenceRange::getEffectiveFrom))
                .orElse(null);
    }

    // DANH GIA KET QUA: NORMAL / LOW (thap) / HIGH (cao) / ABNORMAL (dinh tinh bat thuong) so voi khoang tham chieu
    static String interpret(LabParameter parameter, LabReferenceRange range, String text) {
        if (range == null || text == null) {
            return null;
        }
        if (range.getReferenceType() == LabReferenceType.QUALITATIVE) {
            return range.getNormalValues() != null && Arrays.asList(range.getNormalValues()).contains(text)
                    ? "NORMAL" : "ABNORMAL";
        }
        if (parameter.getValueType() != LabParameterValueType.NUMERIC) {
            return null;
        }
        BigDecimal value = parseNumber(text);
        if (value == null) {
            return null;
        }
        BigDecimal low = range.getLowerBound();
        BigDecimal high = range.getUpperBound();
        if (low != null && (range.isLowerInclusive() ? value.compareTo(low) < 0 : value.compareTo(low) <= 0)) {
            return "LOW";
        }
        if (high != null && (range.isUpperInclusive() ? value.compareTo(high) > 0 : value.compareTo(high) >= 0)) {
            return "HIGH";
        }
        return "NORMAL";
    }

    // doi chuoi thanh so, nhan ca dau phay va dau cham ("6,2" hoac "6.2"); khong phai so -> null
    static BigDecimal parseNumber(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.strip().replace(',', '.');
        return normalized.matches("-?\\d+(\\.\\d+)?") ? new BigDecimal(normalized) : null;
    }

    // chuyen khoang tham chieu thanh chu de in: "4 - 10", "< 5", "> 1", "Am tinh"...
    private static String referenceText(LabReferenceRange range) {
        if (range == null) {
            return null;
        }
        return switch (range.getReferenceType()) {
            case RANGE -> plain(range.getLowerBound()) + " - " + plain(range.getUpperBound());
            case UPPER_LIMIT -> "< " + plain(range.getUpperBound());
            case LOWER_LIMIT -> "> " + plain(range.getLowerBound());
            case QUALITATIVE -> String.join(", ", range.getNormalValues());
            case TEXT -> range.getReferenceText();
        };
    }

    // in so gon, bo so 0 thua ("10.00" -> "10")
    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    // ---- du lieu tra ve ----------------------------------------------------------------------------------------

    // DUNG KET QUA TRA VE FE: thong tin phieu, benh nhan, chi so, ket qua da luu, may, cac tab cung dot, isPerformer
    private ParaclinicalResultResponse toResponse(Context context, UUID technicianAccountId) {
        return toResponse(context, technicianAccountId, true);
    }

    // forEditing = false: bac si chi xem, bo qua danh sach may va cac tab (bot 2 query)
    private ParaclinicalResultResponse toResponse(Context context, UUID technicianAccountId, boolean forEditing) {
        ParaclinicalOrderDetail detail = context.detail();
        ParaclinicalOrder order = context.order();
        boolean isPerformer = technicianAccountId.equals(detail.getPerformerAccountId());
        List<LabRow> rows = context.isLab() ? labRows(context) : List.of();
        int printCount = 0;
        ParaclinicalResultResponse.Result result = null;
        if (context.isLab()) {
            Optional<LabResult> lab = labResultRepository.findByOrderDetailId(detail.getId());
            if (lab.isPresent()) {
                printCount = lab.get().getPrintCount();
                result = labResult(lab.get());
            }
        } else {
            Optional<ImagingResult> imaging = imagingResultRepository.findByOrderDetailId(detail.getId());
            if (imaging.isPresent()) {
                printCount = imaging.get().getPrintCount();
                result = imagingResult(imaging.get());
            }
        }
        boolean currentIsDraft = result != null && !"SUBMITTED".equals(result.status());
        List<ParaclinicalResultResponse.SiblingService> siblingServices = forEditing
                ? siblingServices(detail, order, currentIsDraft, technicianAccountId)
                : List.of();
        Map<UUID, String> staffNames = staffNames(order.getDoctorAccountId(), detail.getPerformerAccountId());
        return new ParaclinicalResultResponse(
                detail.getId(), order.getSophieu(), context.form(), context.service().getType().name(),
                sectionOf(context, rows), context.service().getCode(), detail.getTenchidinh(),
                staffNames.get(order.getDoctorAccountId()), order.getNoichidinh(), null, performingDepartment(detail),
                order.getMucdochidinh() != null && order.getMucdochidinh().toLowerCase(Locale.ROOT).contains(EMERGENCY)
                        ? "EMERGENCY" : "NORMAL",
                patient(context), order.getChandoansobo(),
                context.visit().map(Visit::getLydovaovien).orElse(null),
                context.visit().map(Visit::getLydovaovien).orElse(null),
                context.form() == ResultForm.MRI ? screening(detail) : null,
                detail.getNgaychidinh(), detail.getPerformedAt(), statusOf(detail),
                detail.getPerformerAccountId() == null ? null : new ParaclinicalResultResponse.Performer(
                        detail.getPerformerAccountId(), staffNames.get(detail.getPerformerAccountId())),
                isPerformer,
                rows.stream().map(ParaclinicalResultServiceImpl::parameter).toList(),
                result, printCount, forEditing ? instruments(context) : List.of(),
                context.visit().map(Visit::getSovaovien).orElse(null),
                clinicFacilityRepository.findCurrent()
                        .map(facility -> new ParaclinicalResultResponse.Clinic(
                                facility.getTenCskcb(), facility.getDiachi(), facility.getSodienthoai()))
                        .orElse(null),
                siblingServices);
    }

    // doi ket qua XET NGHIEM da luu thanh dang tra ve (gia tri + danh gia tung chi so)
    private ParaclinicalResultResponse.Result labResult(LabResult lab) {
        Map<String, String> values = new LinkedHashMap<>();
        Map<String, String> interpretations = new LinkedHashMap<>();
        lab.getItems().forEach(item -> {
            values.put(item.getMachiso(), item.getKetqua());
            if (item.getInterpretation() != null) {
                interpretations.put(item.getMachiso(), item.getInterpretation());
            }
        });
        LabResultItem first = lab.getItems().stream().findFirst().orElse(null);
        return new ParaclinicalResultResponse.Result(
                lab.getStatus().name(), lab.getUpdatedAt(), lab.getSubmittedAt(), lab.getGiolaymau(), values,
                interpretations, lab.getGhichu(), null, null, null, null, null, null,
                first == null ? null : first.getInstrumentId(), first == null ? null : first.getInstrumentNameSnapshot(),
                List.of());
    }

    // doi ket qua HINH ANH da luu thanh dang tra ve (ky thuat, mo ta, cac muc, ket luan, de nghi...)
    private ParaclinicalResultResponse.Result imagingResult(ImagingResult imaging) {
        return new ParaclinicalResultResponse.Result(
                imaging.getStatus().name(), imaging.getUpdatedAt(), imaging.getSubmittedAt(), null, null, null,
                imaging.getKqclsKetluan(), imaging.getKqclsKythuat(), imaging.getKqclsMota(),
                sectionsOf(imaging.getKqclsMotaChitiet()), imaging.getKqclsDenghi(), imaging.getKqclsLinkhinhanh(),
                imaging.getReadingDoctorName(), imaging.getInstrumentId(), imaging.getInstrumentNameSnapshot(),
                imageRepository.findByImagingResultIdOrderByDisplayOrderAsc(imaging.getId()).stream()
                        .map(image -> new ParaclinicalResultResponse.ResultFile(image.getId(), image.getFileName(),
                                image.getMimeType(), null, image.getHinhanh(), image.getCaption()))
                        .toList());
    }

    // doi 1 chi so + khoang tham chieu thanh dang tra ve FE (de FE tu to mau cao/thap khi nhap)
    private static ParaclinicalResultResponse.Parameter parameter(LabRow row) {
        LabReferenceRange range = row.range();
        ParaclinicalResultResponse.Reference reference = range == null ? null : switch (range.getReferenceType()) {
            case RANGE -> new ParaclinicalResultResponse.Reference("RANGE", range.getLowerBound(), range.getUpperBound(), null, null);
            case UPPER_LIMIT -> new ParaclinicalResultResponse.Reference("UPPER", null, range.getUpperBound(), null, null);
            case LOWER_LIMIT -> new ParaclinicalResultResponse.Reference("LOWER", range.getLowerBound(), null, null, null);
            case QUALITATIVE -> new ParaclinicalResultResponse.Reference("QUALITATIVE", null, null,
                    List.of(range.getNormalValues()), null);
            case TEXT -> new ParaclinicalResultResponse.Reference("TEXT", null, null, null, range.getReferenceText());
        };
        return new ParaclinicalResultResponse.Parameter(
                row.parameter().getCode(), row.parameter().getName(), row.parameter().getUnit(),
                row.parameter().getValueType().name(), row.link().getGroupName(), row.link().isRequired(), reference);
    }

    // tieu de khoi xet nghiem tren phieu (vd "HUYET HOC"); hinh anh thi khong co
    private String sectionOf(Context context, List<LabRow> rows) {
        if (!context.isLab()) {
            return null;
        }
        return rows.stream().map(row -> row.link().getSectionName()).filter(Objects::nonNull).findFirst()
                .orElseGet(() -> serviceGroupRepository.findById(Objects.requireNonNullElse(
                                context.service().getServiceGroupId(), new UUID(0, 0)))
                        .map(group -> group.getServiceGroupName().toUpperCase(Locale.ROOT))
                        .orElse("XÉT NGHIỆM"));
    }

    // thong tin benh nhan in tren phieu: ma BN, ho ten, tuoi (tinh den ngay chi dinh), gioi tinh, BHYT/dich vu
    private ParaclinicalResultResponse.Patient patient(Context context) {
        return context.patient().map(found -> {
            Visit visit = context.visit().orElseThrow();
            LocalDate born = found.getNgaysinh();
            Integer age = born == null ? null
                    : Period.between(born, LocalDate.ofInstant(context.detail().getNgaychidinh(), CLINIC_ZONE)).getYears();
            String gender = switch (found.getGioitinh()) {
                case 1 -> "Nam";
                case 2 -> "Nữ";
                default -> "Khác";
            };
            boolean insured = visit.getDoituongbnLoai() == VisitDoituongbnLoai.BHYT;
            return new ParaclinicalResultResponse.Patient(
                    found.getMabenhnhan(), found.getHoten(), age, gender, found.getGioitinh(), found.getDiachi(),
                    insured ? "BHYT" : "Dịch vụ", insured ? visit.getMabhyt() : null, found.getDantoc(),
                    found.getSodienthoai());
        }).orElse(null);
    }

    // phieu MRI: cau hoi sang loc (co kim loai trong nguoi khong, ket qua truoc do)
    private static ParaclinicalResultResponse.Screening screening(ParaclinicalOrderDetail detail) {
        String implant = detail.getHasMetalImplant() == null ? null : detail.getHasMetalImplant() ? "Có" : "Không";
        return new ParaclinicalResultResponse.Screening(implant, detail.getPriorResults());
    }

    // [UC-67 buoc 14-15] CAC TAB CUNG DOT: dich vu cung phieu, cung phong + trang thai tung tab (Ban nhap / Da gui)
    private List<ParaclinicalResultResponse.SiblingService> siblingServices(
            ParaclinicalOrderDetail detail, ParaclinicalOrder order, boolean currentIsDraft, UUID technicianAccountId) {
        List<ParaclinicalOrderDetail> siblings = orderDetailRepository.findByOrderId(order.getId()).stream()
                .filter(sibling -> Objects.equals(detail.getRoomId(), sibling.getRoomId()))
                .filter(sibling -> technicianAccountId.equals(sibling.getPerformerAccountId()))
                .toList();
        Map<UUID, MedicalService> services = medicalServiceRepository
                .findAllById(siblings.stream().map(ParaclinicalOrderDetail::getServiceId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(MedicalService::getId, Function.identity()));
        // ket qua cua tab dang mo da co, chi tra cac tab khac
        Set<UUID> drafts = draftDetailIds(
                siblings.stream().filter(sibling -> !sibling.getId().equals(detail.getId())).toList(), services);
        if (currentIsDraft) {
            drafts.add(detail.getId());
        }
        return siblings.stream()
                .map(sibling -> {
                    MedicalService service = services.get(sibling.getServiceId());
                    return new ParaclinicalResultResponse.SiblingService(
                            sibling.getId(),
                            order.getSophieu(),
                            sibling.getTenchidinh(),
                            service != null ? service.getCode() : null,
                            service != null ? resultFormOf(service) : null,
                            drafts.contains(sibling.getId()) ? "DRAFT" : statusOf(sibling),
                            sibling.getRoomId(),
                            true);
                })
                .toList();
    }

    // tim cac dich vu da co BAN NHAP (luu nhung chua gui) de tab hien "Ban nhap"
    private Set<UUID> draftDetailIds(List<ParaclinicalOrderDetail> siblings, Map<UUID, MedicalService> services) {
        Map<Boolean, List<UUID>> pendingByLab = siblings.stream()
                .filter(sibling -> sibling.getStatus() == ParaclinicalOrderDetailStatus.PENDING_RESULT)
                .filter(sibling -> services.containsKey(sibling.getServiceId()))
                .collect(Collectors.partitioningBy(
                        sibling -> services.get(sibling.getServiceId()).getType() == MedicalServiceType.LAB,
                        Collectors.mapping(ParaclinicalOrderDetail::getId, Collectors.toList())));
        Set<UUID> drafts = new HashSet<>();
        if (!pendingByLab.get(true).isEmpty()) {
            labResultRepository.findByOrderDetailIdIn(pendingByLab.get(true)).stream()
                    .filter(result -> !result.isSubmitted())
                    .forEach(result -> drafts.add(result.getOrderDetailId()));
        }
        if (!pendingByLab.get(false).isEmpty()) {
            imagingResultRepository.findByOrderDetailIdIn(pendingByLab.get(false)).stream()
                    .filter(result -> !result.isSubmitted())
                    .forEach(result -> drafts.add(result.getOrderDetailId()));
        }
        return drafts;
    }

    // trang thai hien tren tab: Cho ket qua -> "PERFORMED" (da thuc hien), con lai giu nguyen
    private static String statusOf(ParaclinicalOrderDetail detail) {
        return detail.getStatus() == ParaclinicalOrderDetailStatus.PENDING_RESULT ? "PERFORMED" : detail.getStatus().name();
    }

    // ten khoa thuc hien (khoa cua phong CLS) de in tren phieu
    private String performingDepartment(ParaclinicalOrderDetail detail) {
        if (detail.getRoomId() == null) {
            return null;
        }
        return roomRepository.findById(detail.getRoomId())
                .flatMap(room -> departmentRepository.findById(room.getDepartmentId()))
                .map(department -> department.getDepartmentName())
                .orElse(null);
    }

    // danh sach MAY cua phong, dung loai (xet nghiem / hinh anh), dang hoat dong -> o chon may
    private List<ParaclinicalResultResponse.Instrument> instruments(Context context) {
        if (context.detail().getRoomId() == null) {
            return List.of();
        }
        return instrumentRepository.findAllByRoomIdAndActiveTrueOrderByCodeAsc(context.detail().getRoomId()).stream()
                .filter(instrument -> instrument.getResultType() == context.instrumentType())
                .map(instrument -> new ParaclinicalResultResponse.Instrument(
                        instrument.getId(), instrument.getCode(), instrument.getName()))
                .toList();
    }

    // lay ten bac si chi dinh + KTV thuc hien trong 1 truy van
    private Map<UUID, String> staffNames(UUID... accountIds) {
        Set<UUID> ids = Arrays.stream(accountIds).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return staffAccountRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(StaffAccount::getId, ParaclinicalResultServiceImpl::displayName));
    }

    // ten hien thi nhan vien: hoc ham + ho ten ("BSCKII Tran Van An")
    private static String displayName(StaffAccount account) {
        return account.getAcademicTitle() == null || account.getAcademicTitle().isBlank()
                ? account.getFullName()
                : account.getAcademicTitle().strip() + " " + account.getFullName();
    }

    // bo khoang trang 2 dau; o trong / toan dau cach -> null
    private static String trim(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
