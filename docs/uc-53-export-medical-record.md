# UC-53 — Export Medical Record (PDF, form 15/BV1)

**Status:** Proposed 17/11/2026. All decisions were settled with the product owner on 17/11/2026 (see [Decisions](#decisions)). Implemented 17/11/2026 (backend, frontend; no migration). MSG131 still has to be added to the message sheet of `Business_Rules_v7.xlsx` by its owner.

This spec is written from `Business_Rules_v7.xlsx`: its rules sheet (BR-xx) and its message sheet (MSG-xx). Report 3 §10.3 is used only where it agrees with BR v7. Where they disagree, BR v7 wins and the difference is listed under [Deviations from the sources](#deviations-from-the-sources). Choices that no source makes are marked **ASSUMPTION** or **TBD**.

## Sources

**Project documents**

- `Business_Rules_v7.xlsx`:
  - rules BR-32, BR-35, BR-42, BR-43, BR-54, BR-120, BR-131;
  - messages MSG02, MSG05, MSG08, MSG12, MSG13, MSG34, MSG80.
- `usecases.xlsx` (UC-53) and Report 3 §10.3.
- [`uc-58-close-medical-record.md`](uc-58-close-medical-record.md): the archive package and the archive store.
- `schema.yaml`, tables `export_logs` (V10), `record_archives` and `medical_records`.

**Legal texts** (all in force on 17/11/2026)

- TT 32/2023/TT-BYT:
  - Điều 51, Điều 52 ([`reference/TT-32-2023-TT-BYT.md`](reference/TT-32-2023-TT-BYT.md));
  - Phụ lục XXVIII, form **15/BV1 Bệnh án Ngoại trú chung** ([`reference/TT-32-2023-TT-BYT-Phu-luc-XXVIII.md`](reference/TT-32-2023-TT-BYT-Phu-luc-XXVIII.md)).
- CV 365/TTYQG-GPQLCL mục III.1 điểm a, đ, e, g ([`reference/CV-365-2025-TTYQG.md`](reference/CV-365-2025-TTYQG.md)).
- Luật KBCB 15/2023/QH15 Điều 69 ([`reference/Luat-15-2023-QH15-Kham-benh-chua-benh.md`](reference/Luat-15-2023-QH15-Kham-benh-chua-benh.md)).
- TT 13/2025/TT-BYT Điều 3 ([`reference/TT-13-2025-TT-BYT.md`](reference/TT-13-2025-TT-BYT.md)).
- Luật Giao dịch điện tử 20/2023/QH15 Điều 10 ([`reference/Luat-20-2023-QH15-Giao-dich-dien-tu.md`](reference/Luat-20-2023-QH15-Giao-dich-dien-tu.md)).

## Scope

| Use case | Name | Scope here |
| --- | --- | --- |
| UC-53 | Export Medical Record | From the record detail (UC-52), the Doctor exports one closed outpatient record as a PDF in form 15/BV1, choosing the purpose and, when the purpose requires it, the recipient. |

Also in scope (decided 17/11/2026): the medical-record API of UC-51/UC-52 becomes Doctor-only, as BR-120 requires (BR-53-09).

Out of scope:

- the Admin's XML/JSON export of BR-43 and BR-131;
- exporting several visits in one file;
- exporting a record that is not closed;
- printing the conclusion, prescription or appointment (UC-57, UC-60, UC-62);
- the other forms of Phụ lục XXVIII (specialist outpatient forms 16/BV1, 19/BV1, 29/BV1).

## Legal basis

| Requirement | Text | How UC-53 meets it |
| --- | --- | --- |
| An electronic record contains the information of the forms of Phụ lục XXVIII | CV 365 III.1.a; TT 32/2023 Điều 52 khoản 1 điểm b | The PDF follows form 15/BV1 field by field (BR-53-05) |
| The software can show the record as PDF and print it "theo mẫu hồ sơ bệnh án theo quy định của Bộ Y tế" | CV 365 III.1.a, III.1.e | The PDF is the form 15/BV1 layout, A4 |
| Every user transaction is traced | CV 365 III.1.đ; BR-42 | Each export writes `export_logs` and an `EXPORT` audit row (BR-53-07) |
| Role-based access; prevent unauthorised access | CV 365 III.1.g; BR-120 | PDF export is Doctor-only (BR-43); the UC-51/52 API too (BR-53-09) |
| A closed, archived record may be copied by the clinic's practitioners with the clinic's consent; the patient and authorities on written request | Luật KBCB Điều 69 khoản 4 điểm a, b, d | **ASSUMPTION:** the clinic's electronic-record rules give that consent in advance to every Doctor, which is what BR-35 says. The purpose is required, and the recipient is required for the patient and for authorities (BR-53-03) |
| Users keep the information secret and use it only for the stated purpose | Luật KBCB Điều 69 khoản 5 | The purpose and the recipient are recorded in `export_logs` |
| The export is a faithful copy of the original electronic record | Luật GDĐT Điều 10 | The PDF is built from the frozen archive package. Its SHA-256 is checked first (BR-53-04) |
| No abbreviations in documents given to the patient | TT 32/2023 Điều 52 khoản 2 điểm c | Labels and coded values are printed in full words (BR-53-05) |
| The person and time of each entry are visible | TT 32/2023 Điều 52 khoản 2 điểm d | The examining Doctor, the treating Doctor and the electronic confirmation time are printed. The footer states who exported the file and when (BR-53-06) |

## Business rules

**BR-53-01 Who.** Only the `DOCTOR` role may export a PDF (BR-43, BR-131). This applies to the record of any patient (BR-35), not only to the Doctor's own visits. Any other role gets 403 (MSG13).

**BR-53-02 Which record.** Only one record per export: the record shown in UC-52 (one visit). Its status must be `LOCKED` or `ARCHIVED` and it must have a `record_archives` row. Otherwise the export is refused with `EXPORT_RECORD_NOT_CLOSED` (409, MSG131), and the button in UC-52 is disabled with MSG131 as its hint. There are no drafts.

**BR-53-03 Purpose and recipient.**

- Purpose is required (MSG02) and is one of `LIEN_THONG`, `LUU_TRU`, `CUNG_CAP_NGUOI_BENH`, `CO_QUAN_CO_THAM_QUYEN`, labelled "Trao đổi dữ liệu", "Lưu trữ", "Cung cấp cho người bệnh" and "Cơ quan có thẩm quyền" (BR-43, BR-131). Any other value is refused (MSG05).
- Recipient is required for `CUNG_CAP_NGUOI_BENH` and `CO_QUAN_CO_THAM_QUYEN` (MSG02), and optional otherwise. It is trimmed and at most 200 characters (MSG08).
- The field hint is "Họ tên / cơ quan nhận và số văn bản đề nghị (nếu có)", because Luật KBCB Điều 69 khoản 4 điểm d requires a written request.
- **ASSUMPTION:** there is no minimum length, because BR v7 has no message for one.

**BR-53-04 Source data.**

- The PDF is built only from the archive package of UC-58: the file at `record_archives.package_uri`.
- Before rendering, the system recomputes the package's SHA-256 and compares it with `record_archives.package_sha256`.
- If the file is missing, unreadable or different, the export fails: an `export_logs` row with status `FAILED`, then MSG80. Nothing is ever built from the live tables.
- The facility name, department, doctors' names and titles are therefore those frozen at closing.
- Only "Sở Y tế" comes from configuration (BR-53-05).

**BR-53-05 Layout and content.**

- The PDF reproduces form 15/BV1 on A4: two pages, plus continuation pages when the text is long.
- The text and section order are those of [`reference/TT-32-2023-TT-BYT-Phu-luc-XXVIII.md`](reference/TT-32-2023-TT-BYT-Phu-luc-XXVIII.md), filled as in [Field mapping](#field-mapping).
- Coded values are printed as Vietnamese words, never as codes alone: gender, đối tượng, ketquadieutri. Dates print as dd/mm/yyyy and times as HH giờ mm phút, in Asia/Ho_Chi_Minh.
- A field with no value is left blank, as on the paper form.
- A field that the package does not contain, because the record was closed before the package format had it, prints "(không có trong gói lưu trữ)".
- The font is embedded and supports Vietnamese.

**BR-53-06 Export footer.**

- Every page carries a footer line, which is not part of the BYT form (**ASSUMPTION**): "Bản xuất từ hồ sơ bệnh án điện tử · Số lưu trữ {soluutru} · Xuất lúc {HH:mm dd/mm/yyyy} bởi {title name} · Trang {n}/{total}".
- Under "Bác sỹ điều trị", the PDF prints "Đã xác nhận điện tử lúc {HH:mm dd/mm/yyyy}", from the closing time (TT 13/2025 Điều 3; UC-58 BR-58-09).

**BR-53-07 Storage and log.**

- The PDF is written once, read-only, as `<clinic.archive.root>/exports/<year>/<exportLogId>.pdf`, where year is the export year in Vietnam time.
- In the same transaction the system inserts one `export_logs` row with:
  - `export_format = PDF`, `purpose`, `recipient`;
  - `file_uri` (relative path), `file_sha256`;
  - `spec_version = "TT32/2023-15/BV1"`, `status = SUCCESS`;
  - `exported_by_account_id`, `exported_at`.
- It also writes one `audit_logs` row: action `EXPORT`, entity `MEDICAL_RECORD`. The audit row holds field names and masked values only: purpose, format, exportLogId. It never holds the recipient's text or medical content (`security.md`).
- If the transaction rolls back, the written file stays as an orphan. Its name is a fresh UUID, so it cannot collide with anything. A file with no `export_logs` row is not an export.

**BR-53-08 Response.**

- The backend returns the PDF bytes with `Content-Type: application/pdf` and `Content-Disposition: attachment; filename="BA-{soluutru}-{yyyyMMddHHmm}.pdf"`.
- The browser downloads the file. MSG34 "Xuất tệp thành công." is shown only after the file is received.
- No medical data is put in a URL. The purpose and recipient travel in the request body.

**BR-53-09 UC-51/UC-52 access (BR-120).**

- `MedicalRecordController` changes from `hasAnyRole('DOCTOR','ADMIN')` to `hasRole('DOCTOR')`. BR-120: "Admin xuất hồ sơ XML/JSON nhưng không xem nội dung lâm sàng trên màn hình."
- The old `POST /medical-records/{id}/export-log` endpoint is removed. It only wrote an audit row, without a file or a purpose. The frontend call to it and the `window.print()` export go with it.

**BR-53-10 Package additions.**

- From this change on, UC-58 also writes these values into the package's `extensions` group:
  - `khambenh_bophan`: form IV.2, the free text;
  - `daxuly`: form IV.5, `clinical_examinations.treatment_care_performed`.
- Packages already written keep their content (they are immutable), and those fields print as in BR-53-05.

## Field mapping

Package paths are relative to `HoSoBenhAn` unless they start with `DuLieuChu` or `extensions`.

**Header**

| Form 15/BV1 | Value |
| --- | --- |
| Sở Y tế | Configuration `clinic.facility.health-department` (**ASSUMPTION**, not in UC-77 yet); blank when not set |
| Bệnh viện | `DuLieuChu.tencskcb` |
| KHOA | `ThongTinBenhNhan.tenkhoa` |
| MS | "15/BV-01" |
| Số ngoại trú | `ThongTinBenhNhan.soba` |
| Số lưu trữ | `DuLieuChu.soluutru` |

**I. Hành chính**

| Form 15/BV1 | Value |
| --- | --- |
| 1 Họ và tên (in hoa) | `ThongTinBenhNhan.hoten` (already upper case) |
| Tuổi | `ThongTinBenhNhan.tuoi` |
| 2 Sinh ngày | `ThongTinBenhNhan.ngaysinh`, printed in the 8 boxes |
| 3 Giới | `gioitinh`: 1 ticks "Nam", 2 ticks "Nữ"; any other value ticks neither (the form has no third box) |
| 4 Nghề nghiệp | `nghenghiep`; code boxes `nghenghiep_ma` |
| 5 Dân tộc | `dantoc`; code boxes `dantoc_ma` |
| 6 Ngoại kiều | `ngoaikieu`; code boxes `ngoaikieu_ma` |
| 7 Địa chỉ, Số nhà / thôn, phố | `diachi` |
| 7 Xã, phường | `xaphuong` |
| 7 Huyện | blank (administrative units have 2 levels since 01/07/2025) |
| 7 Tỉnh, thành phố | `tinhthanh`; code boxes `tinhthanh_ma` |
| 8 Nơi làm việc | `noilamviec` |
| 9 Đối tượng | `doituongbn_loai`: BHYT ticks "1.BHYT", DV ticks "2.Thu phí"; "Miễn" and "Khác" are never ticked |
| 10 BHYT giá trị đến ngày; Số thẻ | `denngaybhyt`; `mabhyt`, printed in full (blank for a self-paid visit) |
| 11 Người nhà khi cần báo tin; Điện thoại | `hotennguoithan`, `diachinguoithan`; `sodienthoainguoithan` |
| 12 Đến khám bệnh lúc | `ThongTinVaoVien.denkhambenhluc` |
| 13 Chẩn đoán của nơi giới thiệu | `ThongTinVaoVien.chandoancuanoigioithieu` |
| 13 "1. Y tế" / "2. Tự đến" | `noigioithieu_loai = CO_SO_Y_TE` ticks "Y tế"; any other value ticks "Tự đến" |

**II–IV**

| Form 15/BV1 | Value |
| --- | --- |
| II Lý do vào viện | `ThongTinVaoVien.lydovaovien` |
| III.1 Quá trình bệnh lý | `ThongTinVaoVien.quatrinhbenhly` |
| III.2 Tiền sử bản thân | `ThongTinBenhNhan.tiensubenhtatcuabanthan`: each `thongtin_noidung`, separated by "; " (drug allergies first, prefixed "Dị ứng thuốc:") |
| III.2 Tiền sử gia đình | `ThongTinBenhNhan.tiensubenhtatcuagiadinh` |
| IV vital-sign box | `ThongTinVaoVien.mach`, `nhietdo`, `huyetap_tamthu`/`huyetap_tamtruong`, `nhiptho`, `cannang` |
| IV.1 Toàn thân | `ThongTinVaoVien.khambenh_toanthan` |
| IV.2 Các bộ phận | `extensions.khambenh_bophan`. After it, one line per organ of `khambenh_caccoquan`: organ, then `dauchung` |
| IV.3 Tóm tắt kết quả CLS | Generated (BR-53-11) |
| IV.4 Chẩn đoán ban đầu | `ThongTinVaoVien.chandoansobo` |
| IV.5 Đã xử lý (thuốc, chăm sóc) | `extensions.daxuly`. After it, "Thuốc:" and one line per prescription item: `tenthuocvattu`, `soluongthuocvattu` `donvitinh`, `cachdung` |
| IV.6 Chẩn đoán khi ra viện; Mã | `ThongTinDieuTri.chandoan`; `maicd` |
| IV.7 Điều trị ngoại trú từ ngày … đến ngày … | `ThongTinBenhNhan.thoigianvaovien` (date part) to `ThongTinDieuTri.ngayravien` |
| Ngày … tháng … năm (page 1) | `ngayravien` |
| Giám đốc bệnh viện | Printed as on the form, signature left blank (**ASSUMPTION**: for the clinic it means the head of the facility; no data is printed) |
| Bác sỹ khám bệnh; Họ và tên | `ThongTinVaoVien.bacsikhambenh` |

**Tổng kết bệnh án (page 2)**

| Form 15/BV1 | Value |
| --- | --- |
| 1 Quá trình bệnh lý và diễn biến lâm sàng | `ThongTinVaoVien.quatrinhbenhly`; then `ThongTinDieuTri.tomtat` when present |
| 2 Tóm tắt kết quả xét nghiệm CLS có giá trị chẩn đoán | Same text as IV.3 (BR-53-11) |
| 3 Bệnh chính; mã | `ThongTinDieuTri.tenicd` (or `chandoan`); `maicd` |
| 3 Bệnh kèm theo; mã | `tenicd_khac`; `maicd_khac`, one per line |
| 4 Phương pháp điều trị | `ThongTinDieuTri.huongdieutri` |
| 5 Tình trạng người bệnh ra viện | `tinhtrangravien.ketquadieutri`: 1 "Khỏi", 2 "Đỡ", 3 "Không thay đổi", 4 "Nặng hơn" (labels of UC-56, CV 365 III.31.1) |
| 6 Hướng điều trị và các chế độ tiếp theo | `tinhtrangravien.loidanbacsi`; then, if `extensions.lichhentaikham` exists, "Hẹn tái khám ngày {ngayhen}" |
| Hồ sơ, phim, ảnh (table) | Blank, for handwriting |
| Người giao hồ sơ / Người nhận hồ sơ | Blank, for handwriting |
| Ngày … tháng … năm (page 2) | `ngayravien` |
| Bác sỹ điều trị; Họ và tên | `ThongTinDieuTri.bacsidieutri`, with the confirmation line of BR-53-06 |

**BR-53-11 CLS summary.**

- The summary lists every result of `KetQuaChanDoanHinhAnh` and `KetQuaXetNghiem`, ordered by result time.
- An imaging result prints as "{tên chỉ định}: {kqcls_ketluan}".
- A lab result prints "{tên chỉ định}:" followed by its abnormal values only (`ketquabatthuong = "1"`), each as "{pkqxn_tenxetnghiem} {ketqua} {donvi} (tham chiếu {trisothamchieu})". When no value is abnormal, it prints "trong giới hạn bình thường".
- Cancelled orders are not listed. When no service was ordered (or all were cancelled), the summary prints "Không chỉ định cận lâm sàng."; when services were ordered but none has a result, it prints "Không có kết quả cận lâm sàng.".

## Flows

**Normal flow**

1. In UC-52 the Doctor clicks "Xuất hồ sơ bệnh án (PDF)". The button is enabled only for a closed record (BR-53-02).
2. The system opens a dialog with two fields: "Mục đích xuất" (select, required) and "Người nhận" (required for two purposes, BR-53-03).
3. The Doctor fills them in and clicks "Xuất PDF". The button reads "Đang xuất…" while the request runs.
4. The system checks BR-53-01 to BR-53-04, renders the PDF (BR-53-05, BR-53-06, BR-53-11), stores it and logs the export (BR-53-07).
5. The browser downloads the file and shows MSG34. The dialog closes.

**Alternative flow**

- **AF-01 (step 2 or 3):** The Doctor cancels. No request is sent and nothing is logged.

**Exception flows**

- **EF-01 (step 3):** Validation fails. MSG02, MSG05 or MSG08 is shown under the field, and the dialog stays open.
- **EF-02 (step 4):** The record is not closed. MSG131 is shown.
- **EF-03 (step 4):** The package is missing or does not match, or rendering or storage fails. An `export_logs` row is written with `FAILED` and an `error_message` (in its own transaction; no medical data in the message), and MSG80 is shown. The dialog stays open.
- **EF-04 (any step):** The user is not a Doctor: MSG13. The session has expired: MSG12.

**Postconditions:** No medical data is changed. A successful export leaves one stored PDF, one `export_logs` row and one audit row.

## API

| Method | Path | Contract |
| --- | --- | --- |
| `POST` | `/api/v1/medical-records/{medicalRecordId}/commands/export-pdf` | Doctor only. Body `{ purpose, recipient }`. 200 returns `application/pdf` (BR-53-08). Errors use the JSON `ErrorResponse` (below). |
| — | `POST /api/v1/medical-records/{id}/export-log` | Removed (BR-53-09) |

| Error code (new) | HTTP | Message |
| --- | --- | --- |
| `EXPORT_RECORD_NOT_CLOSED` | 409 | MSG131 |
| `EXPORT_INVALID` | 422 | MSG02 / MSG05 / MSG08, field in `details` |
| `EXPORT_FAILED` | 500 | MSG80 |
| existing `NOT_FOUND`, `ACCESS_DENIED`, `UNAUTHENTICATED` | 404, 403, 401 | MSG80, MSG13, MSG12 |

Frontend: `ApiClient.download` gains an optional JSON `body`, so the purpose and recipient are not sent in the URL. The method and headers stay as they are.

## Messages

| Code | Text | Source |
| --- | --- | --- |
| MSG02 | `{field_name} là bắt buộc.` ("Mục đích xuất", "Người nhận") | BR v7 |
| MSG05 | `{field_name} không đúng định dạng.` | BR v7 |
| MSG08 | `Vượt quá độ dài tối đa {max_length} ký tự.` | BR v7 |
| MSG12 | Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại. | BR v7 |
| MSG13 | Bạn không có quyền thực hiện thao tác này. | BR v7 |
| MSG34 | Xuất tệp thành công. | BR v7 |
| MSG80 | Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên. | BR v7 |
| **MSG131** | Chỉ xuất được hồ sơ bệnh án đã đóng. | **New** (17/11/2026). To be added to the message sheet by its owner |

## Schema and configuration

- **No schema change.** `export_logs` (V10) already has `PDF`, the four purposes, `recipient`, `file_uri`, `file_sha256` and `spec_version`.
- New property `clinic.facility.health-department` (env `CLINIC_HEALTH_DEPARTMENT`, default empty).
- The export directory lives under the existing `clinic.archive.root`.

## Dependencies (approved 17/11/2026)

- `io.github.openhtmltopdf:openhtmltopdf-pdfbox` **1.1.108** (released 01/11/2026), licence LGPL-2.1. It brings in Apache PDFBox (Apache-2.0). iText is not used because of its AGPL licence.
- Font: Noto Serif Regular and Bold (SIL Open Font License 1.1), bundled under `backend/src/main/resources/fonts/` with its licence file.
- The 15/BV1 layout is built as XHTML with inline CSS in `Form15Bv1Renderer` (no template engine is added). Every value is escaped before it is placed in the markup.

## Deviations from the sources

| Source says | This spec | Why |
| --- | --- | --- |
| R3 §10.3 step 2: scope "Current visit only / All visits" | One visit only | Decision 2A. Form 15/BV1 is one outpatient record per visit |
| R3 AF-02: an unclosed visit is exported as "Draft" | Refused (MSG131) | Decision 3A; BR-131 "chỉ xuất hồ sơ đã đóng"; Luật KBCB Điều 69 khoản 3 |
| R3 messages MSG36 (success), MSG99 (failure) | MSG34, MSG80 | In BR v7, MSG36 is the logout dialog and MSG99 does not exist |
| R3 has no purpose or recipient | Purpose required; recipient required for two purposes | BR-43, BR-131; Luật KBCB Điều 69 khoản 4 |
| R3 layout: "patient information, visits with vital signs, …" | Form 15/BV1 | CV 365 III.1.e; TT 32/2023 Điều 51 |
| Controller allowed `ADMIN` | Doctor only | BR-120 |

## Decisions

All settled on 17/11/2026, as proposed:

| # | Decision |
| --- | --- |
| 1a | "Sở Y tế" from a configuration property |
| 1b | "Bệnh viện" = facility name; "Khoa" = department of the visit's room |
| 1c | "Giám đốc bệnh viện" printed as on the form, signature blank |
| 1d | "Số ngoại trú" = `soba` |
| 1e | CLS summary generated from the results (BR-53-11) |
| 1f | Film table and handover rows blank |
| 1g | Page-2 mapping as in [Field mapping](#field-mapping) |
| 2 | One visit per export |
| 3 | Closed records only |
| 4 | Existing BR v7 messages plus MSG131 |
| 5 | Backend rendering with openhtmltopdf and an embedded font |
| 6 | Built from the frozen archive package; missing fields added to future packages |
| 7 | Four purposes; recipient required for the patient and authorities |
| — | UC-51/52 API restricted to Doctor (BR-120) |

## Implementation plan

1. **Backend, access and cleanup:** the Doctor-only controller, then remove `recordExportLog` and its service method.
2. **Backend, package:** add `extensions.khambenh_bophan` and `extensions.daxuly` to `ArchivePackageBuilder`, with a test.
3. **Backend, export:**
   - `pom.xml` dependency, font and template;
   - `MedicalRecordExportService`: guard, package read and hash check, view model, render, store, log;
   - `ExportLog.pdfExport(...)` and `ExportLog.failed(...)`;
   - error codes;
   - controller endpoint.
4. **Backend tests:**
   - unit tests for the view model mapping, BR-53-03 validation, BR-53-11 summary and the hash mismatch;
   - a render test (the PDF parses and contains the patient name and "BỆNH ÁN NGOẠI TRÚ");
   - a `SecurityRouteIT` entry.
5. **Frontend:**
   - `ApiClient.download` with a body;
   - `medicalRecordApi.exportPdf`;
   - the export dialog in `MedicalRecordDetailPage`, replacing the scope modal and `window.print()`;
   - tests.
6. **Docs:**
   - `permission-matrix.md` (Admin has no UC-51/52 access);
   - `README.md` (`CLINIC_HEALTH_DEPARTMENT`);
   - Report 3 §10.3 messages;
   - the message list (MSG131).
