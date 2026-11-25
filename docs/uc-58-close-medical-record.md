# UC-58 — Close Medical Record

**Status:** Proposed 17/11/2026 and revised the same day against the legal texts of [Legal basis](#legal-basis). Implemented 17/11/2026 (backend, frontend, migration V61). Since UC-53 (17/11/2026) the package's `extensions` group also holds `khambenh_bophan` and `daxuly` ([`uc-53-export-medical-record.md`](uc-53-export-medical-record.md) BR-53-10); the integration tests need Docker and have not run yet. The "Xác nhận kết luận" action after MSG133 of UC-90 is not added yet. Written from `Business_Rules_v7.xlsx`: the rules sheet (BR-xx) and its message sheet (MSG-xx). Report 3 §11.5 and the other project documents are used only where they agree with BR v7. Where they disagree, BR v7 wins and the difference is listed under [Deviations from the sources](#deviations-from-the-sources). Choices that no source makes are marked **ASSUMPTION** or **TBD** and are listed under [Decisions needed](#decisions-needed).

**Sources:** `Business_Rules_v7.xlsx` (BR-02, BR-05, BR-15, BR-19, BR-22, BR-26, BR-27, BR-30, BR-32, BR-35, BR-37, BR-41, BR-42, BR-43, BR-44, BR-47, BR-53, BR-54, BR-55, BR-119, BR-121, BR-122, BR-125, BR-126; messages MSG05, MSG12, MSG13, MSG25, MSG27, MSG38, MSG48, MSG67, MSG68, MSG75, MSG80); `usecases.xlsx` (UC-58); Report 3 §11.5; [`uc-59-e-prescription.md`](uc-59-e-prescription.md) BR-59-09; [`uc-61-follow-up-appointment.md`](uc-61-follow-up-appointment.md) BR-61-04; `schema.yaml` tables `medical_records`, `export_logs`, `record_signatures`, `record_archives` (V7, V10). Legal texts, all in force on 17/11/2026:

- Luật KBCB 15/2023/QH15 Điều 2 khoản 17, Điều 69 ([`reference/Luat-15-2023-QH15-Kham-benh-chua-benh.md`](reference/Luat-15-2023-QH15-Kham-benh-chua-benh.md));
- TT 32/2023/TT-BYT Chương X ([`reference/TT-32-2023-TT-BYT.md`](reference/TT-32-2023-TT-BYT.md));
- TT 13/2025/TT-BYT Điều 1 to 3 ([`reference/TT-13-2025-TT-BYT.md`](reference/TT-13-2025-TT-BYT.md));
- CV 365/TTYQG-GPQLCL mục III.1 and Phụ lục ([`reference/CV-365-2025-TTYQG.md`](reference/CV-365-2025-TTYQG.md));
- Luật Lưu trữ 33/2024/QH15 Điều 2, 3, 15, 16, 33, 36 ([`reference/Luat-33-2024-QH15-Luu-tru.md`](reference/Luat-33-2024-QH15-Luu-tru.md));
- TT 33/2025/TT-BYT and its Phụ lục ([`reference/TT-33-2025-TT-BYT.md`](reference/TT-33-2025-TT-BYT.md));
- Luật Giao dịch điện tử 20/2023/QH15 Điều 10, 13, 22 ([`reference/Luat-20-2023-QH15-Giao-dich-dien-tu.md`](reference/Luat-20-2023-QH15-Giao-dich-dien-tu.md)).

| Use case | Name | Scope here |
| --- | --- | --- |
| UC-58 | Close Medical Record | The Doctor confirms the conclusion. In the same action the system closes and locks the record, completes the visit, fixes the BHYT charges and archives the record. |

Out of scope:

- printing after closure: UC-57 (conclusion), UC-60 and UC-62, specified in [`uc-57-print-conclusion.md`](uc-57-print-conclusion.md) (PO 23/11/2026: printed only once the record is closed; `confirm` returns the prescription and appointment IDs to print);
- unlocking a closed record (the `unlocked_*` columns of V7 have no BR in v7);
- the Admin's XML/JSON export of BR-43;
- record disposal after the retention period (`PENDING_DISPOSAL`, `DISPOSED`).

## Description

After saving the conclusion (UC-56) and, if needed, sending the prescription (UC-90) and creating the follow-up appointment (UC-61), the Doctor of the visit clicks "Xác nhận kết luận". BR-32: "Khi bác sĩ xác nhận kết luận, hệ thống tự động đóng hồ sơ và chuyển lượt khám sang 'Hoàn thành' trong cùng một thao tác." The record is then locked. A BHYT visit's charges that are still "Chờ thu" are paid at the cashier afterwards (BR-15, BR-22 (2)). The closed record is archived automatically (BR-126) for at least 10 years (BR-44).

## Legal basis

No legal text defines a "close record" action. BR-32 and BR-126 are the project's own rules. The laws set requirements that closing must meet:

| Requirement | Text | How UC-58 meets it |
| --- | --- | --- |
| A record is either "đang trong quá trình điều trị" or "đã hoàn thành quá trình điều trị và được chuyển lưu trữ", and the two are accessed differently | Luật KBCB Điều 69 khoản 3, 4 | Closing is the moment the record passes from khoản 3 to khoản 4: locked (BR-58-04) and archived (BR-58-06) in one transaction |
| Paper and electronic records have equal legal value; archiving follows the archive law | Luật KBCB Điều 69 khoản 1, 2 | The archive of BR-58-06 follows Luật Lưu trữ 2024 and TT 33/2025 |
| Staff sign *or* confirm the record electronically: a legal electronic signature, biometrics, or "các hình thức xác nhận bằng phương tiện điện tử khác" | TT 13/2025 Điều 3; Luật GDĐT Điều 22 khoản 4 | The Doctor's confirmation, made while signed in, is recorded as an electronic confirmation (BR-58-09). A digital signature is not required. |
| Each entry shows the time and the person who wrote it | TT 32/2023 Điều 52 khoản 2 điểm d | The closing time and the closing Doctor are on the record, the confirmation and the archive |
| An electronic record counts as original only if it stays intact from creation and can be accessed in full | Luật GDĐT Điều 10 | The record is locked, the package is frozen with its SHA-256, and it is never overwritten |
| Archived electronic data must be accessible, kept in its original format or one that shows it exactly, and must identify origin, sender and time | Luật GDĐT Điều 13 khoản 1 | The package uses the CV 365 exchange structure and carries its metadata (BR-58-06) |
| A born-digital archive document must identify its origin, stay intact, be stored with its metadata ("dữ liệu chủ") and be accessible in full | Luật Lưu trữ Điều 33 khoản 2, Điều 36 khoản 2 | The package's metadata block and the `record_archives` row (BR-58-06) |
| The software exports the record as XML or JSON per the CV 365 Phụ lục, shows it at least as .pdf, and logs every user action | CV 365 mục III.1 điểm a, d, đ | The package is JSON in the CV 365 structure; the PDF view is UC-52/UC-57; audit per BR-58-07 |
| Inpatient and outpatient records are kept 10 years; deaths 30 years; psychiatric, work-accident and traffic-accident records 20 years | TT 33/2025 Phụ lục dòng 39, 40, 44 | `retention_years` = 10 by default (BR-58-06); the longer cases are Decisions needed 10 |
| Retention is counted in years, "kể từ năm kết thúc công việc"; a file of several documents takes the longest period | Luật Lưu trữ Điều 15 khoản 3, 4 | `expires_on` (BR-58-06) |
| Records are destroyed only after the retention period, by decision of the head of the facility, completely and irreversibly | Luật Lưu trữ Điều 16 | Out of scope; `record_archives.status` `PENDING_DISPOSAL`, `DISPOSED` stay unused |
| The clinic is private, so its records are "tài liệu lưu trữ tư" and the clinic decides how to apply the archive law, in line with other laws. Luật KBCB still requires every facility to keep medical records under the archive law. | Luật Lưu trữ Điều 3 khoản 5, Điều 9 khoản 4 điểm c, Điều 47 khoản 1; Luật KBCB Điều 69 khoản 2 | TT 33/2025 (10 years) and Luật Lưu trữ Điều 15 khoản 3 are applied as the clinic's rule. Transfer to a state historical archive (Điều 17 to 19) does not apply. |

Left to the facility, not the software: the clinic's quy chế on records (TT 13/2025 Điều 6 khoản 3 điểm b, Luật Lưu trữ Điều 15 khoản 6) and backups, including one off-site copy (CV 365 mục III.2). TT 13/2025 Điều 4 khoản 2 điểm b gives clinics with outpatients until **31/12/2026** to run electronic records.

## Preconditions

- The checks of `ConcludingVisitGuard`, as in UC-56, UC-59 and UC-61:
  - the Doctor is signed in (UC-01), has no pending password change, and has role Doctor with status Active (BR-02);
  - the Doctor is assigned to an active examination room and is on duty there now (BR-05, BR-19, BR-55);
  - the visit is the Doctor's own, is not skipped, and is in `IN_EXAMINATION` or `WAITING_CONCLUSION`;
  - the medical record is `OPEN` (BR-32, BR-35).
- The conclusion of the visit is saved (UC-56). Until it is, "Xác nhận kết luận" is disabled.

## Business rules

- **BR-58-01 (BR-32, BR-30, BR-47): Close conditions.** The server checks all of the following in the closing transaction, in this order, and refuses at the first one that fails:
  1. A conclusion is saved for the record.
  2. Every paraclinical order detail of the visit that is not `CANCELLED` is `COMPLETED` (BR-30, as counted in I1-D44). A detail cancelled because the patient refused (BR-41) does not block.
  3. If the visit has a prescription, its `lienthong_status` is `ACCEPTED` (BR-47: "Đơn chưa gửi thành công chặn xác nhận kết luận"). `PENDING`, `SENT` (no answer yet) and `REJECTED` block. A visit with no prescription closes normally (BR-59-09).
  4. Follow-up appointment: BR-32 requires only that the appointment, *if any*, "đã tạo". A saved appointment is created by definition, so this condition never blocks (settled 17/11/2026, Decisions needed 2). A `CANCELLED` appointment counts as none.
- **BR-58-02 (BR-119): Unchanged since the Doctor looked.** The request carries the `version` of the conclusion the Doctor saw. If the conclusion was changed after that, the request is refused with MSG75 and the Doctor reloads. While the conclusion form has unsaved changes, "Xác nhận kết luận" is disabled with the hint "Lưu các thay đổi của kết luận trước khi xác nhận." (**ASSUMPTION** wording), so the Doctor never confirms a version other than the one on screen.
- **BR-58-03 (BR-27, BR-15, BR-37, BR-22 (2)): Charges.** For a BHYT visit with a valid card, the applied coverage rate is recalculated one last time before the visit changes status. It is fixed from then on: `Visit.isCoverageOpen()` is false once the visit is `COMPLETED`, and the order and cancel use cases cannot change an unopen visit. Receipts that are `PENDING` and not handed to PayOS stay "Chờ thu" and appear in the cashier's list (UC-69). A receipt the patient owes 0 for is "Miễn phí" (`FEE_EXEMPT`, BR-37; already done by `Payment.repriceInsurance`). A self-paid visit pays before its services are released (BR-15, BR-26), so it has no pending receipt left once condition 2 holds. Paid and cancelled receipts are not touched.
- **BR-58-04 (BR-32, BR-35): Lock.** The `medical_records` row gets status `LOCKED`, `closed_at` = now and `closed_by_account_id` = the Doctor. The visit gets status `COMPLETED` and `completed_at` = now. That time is "thời điểm xác nhận kết luận" in BR-125. Nothing is locked row by row: every write use case of the record already refuses a record that is not `OPEN` (`MEDICAL_RECORD_CLOSED`, MSG68). After closure this covers:
  - the conclusion (UC-56);
  - the prescription (UC-59, UC-90, UC-92);
  - the follow-up appointment (UC-61);
  - medical history (UC-39), clinical examination (UC-41) and paraclinical orders (UC-49).
  
  Reception's transitions of the appointment (BR-34, BR-121: "Đã đến khám", "Lỡ hẹn", cancel) are not doctor edits and stay allowed.
- **BR-58-05 (CV 365 III.31.4, BR-54): Discharge date.** `conclusions.ngayravien` is set to the closing date in Asia/Ho_Chi_Minh (**ASSUMPTION**: the outpatient visit ends when the record is closed; the field is exported as `ngayravien` yyyyMMdd).
- **BR-58-06 (BR-126, BR-44, BR-43; Luật Lưu trữ Điều 15, 33, 36; TT 33/2025; CV 365): Archive.** In the same transaction the system creates the archive, which is never overwritten:
  - **Archive number:** `soluutru` is a standard field of the record (CV 365 Phụ lục, ThongTinBenhNhan dòng 4, string of up to 200 characters). Its value is `LT-` + the visit code `sovaovien`, e.g. `LT-DK0000000123`. **ASSUMPTION**: neither BR-53 nor CV 365 gives a format; this follows the slip numbers `DT-` and `GH-`. The number is unique, never reused, and also written to `medical_records.soluutru`.
  - **Package content:** the whole record as defined by Luật KBCB Điều 2 khoản 17, frozen as it is at closure:
    - administrative data of the visit and patient;
    - medical history and allergies;
    - the clinical examination and vital signs;
    - paraclinical orders, with results and file hashes;
    - the conclusion, with main and secondary diagnoses;
    - the prescription with its submissions;
    - the follow-up appointment;
    - the Doctor's confirmation of BR-58-09.
  - **Package format (settled 17/11/2026):** the exchange structure of the CV 365 Phụ lục "Mô tả dữ liệu trao đổi hồ sơ bệnh án điện tử", in JSON. CV 365 mục III.1 điểm d and mục IV khoản 1 accept "XML hoặc JSON" equally, and the Phụ lục mục 2 gives the same example in both forms (root `HoSoBenhAn`, then `ThongTinBenhNhan` …). The structure is what the guidance fixes; the file format is the facility's choice. JSON is chosen because the backend already produces it. The JSON uses the Phụ lục field names, with values encoded per BR-54; `export_format` = `JSON` and `spec_version` = `CV365/2025`. This is the format the law asks for: an exchange format that "thể hiện chính xác" the record (Luật GDĐT Điều 13 khoản 1 điểm b) and a defined data structure (Luật Lưu trữ Điều 36 khoản 2 điểm a). Clinic data with no CV 365 field (for example the prescription submissions) goes in an `extensions` block (**ASSUMPTION**). The field-by-field mapping is written with the implementation (Decisions needed 4).
  - **Metadata ("dữ liệu chủ"):** the package carries a metadata block, as Luật Lưu trữ Điều 33 khoản 2 điểm a, c and Luật GDĐT Điều 13 khoản 1 điểm c require:
    - the facility code and name (UC-77);
    - `soluutru`, `soba`, `sovaovien`;
    - the closing Doctor's account and name;
    - `closed_at`;
    - the retention period and `expires_on`;
    - the package format version.
    
    `record_archives` and `export_logs` hold the same metadata in the database.
  - **Storage (settled 17/11/2026):** the archive store is a directory on the server, set by a new property `clinic.archive.root`; no file storage exists in the backend yet. Each package is written once, as `<clinic.archive.root>/<closing year>/<soluutru>.json`, and that relative path is `package_uri`. The steps are:
    1. Write the bytes to a temporary file in the same directory.
    2. Compute the SHA-256 of those bytes.
    3. Move the temporary file to the final name, refusing if the name already exists (create-new). An existing package is therefore never replaced.
    4. Make the file read-only.
    
    The hash goes to `package_sha256` and into the confirmation of BR-58-09. Anyone can recompute it later to prove the file is unchanged (Luật GDĐT Điều 10; Luật Lưu trữ Điều 33 khoản 2 điểm b). Backups of the directory, including one off-site copy (CV 365 mục III.2), are an operations task outside the software.
  - **Export log:** one `export_logs` row records the package with purpose `LUU_TRU` (BR-43), format `JSON`, the same URI and hash, status `SUCCESS`, and the Doctor.
  - **Retention category (TT 33/2025 Phụ lục dòng 40, 44; Luật Lưu trữ Điều 15 khoản 4).** In the MSG27 dialog the Doctor picks the retention category of the record:

    | Category | Label | Years | Source |
    | --- | --- | --- | --- |
    | `STANDARD` | Thông thường | 10 | dòng 44 "Hồ sơ bệnh án nội trú, ngoại trú" |
    | `WORK_ACCIDENT` | Tai nạn lao động | 20 | dòng 40 |
    | `TRAFFIC_ACCIDENT` | Tai nạn giao thông | 20 | dòng 40 |
    | `PSYCHIATRIC` | Tâm thần | 20 | dòng 40 |

    The default is `STANDARD`. The Doctor chooses the category, not the system: only the Doctor knows whether the visit was for an accident. Death (dòng 39, 30 years), organ grafts and cosmetic surgery (dòng 43) do not occur in this outpatient clinic and are not offered (**ASSUMPTION**). The category and its years go into the package metadata and the `LOCK` audit row.
  - **Archive row:** one `record_archives` row with status `ARCHIVED`, `retention_category` and `retention_years` from the table above, and `archived_at` = now. `retention_category` is added by migration V61 (see [Schema changes](#schema-changes)).
  - **Expiry date (settled 17/11/2026):** Luật Lưu trữ Điều 15 khoản 3 counts the period "theo năm … kể từ năm kết thúc công việc", while BR-44 counts it from the closing date. The system sets `expires_on` = 31/12 of (the closing year in Asia/Ho_Chi_Minh + `retention_years`). This is never earlier than BR-44's date, so it satisfies both. For example, a record closed on 17/11/2026 with 10 years expires on 31/12/2036.
  - **Unsigned package:** neither BR-126 nor the law requires a digital signature on the package. TT 13/2025 Điều 3 accepts an electronic confirmation, which BR-58-09 records. The `schema.yaml` description "gói XML + PDF đã ký" is not followed (Deviations).
- **BR-58-09 (TT 13/2025 Điều 3 khoản 3; TT 32/2023 Điều 52 khoản 2 điểm d): Electronic confirmation.** The Doctor's click on "Xác nhận" in MSG27 is the electronic confirmation of the record. Signing in (UC-01), being the Doctor of the visit and passing the explicit MSG27 confirmation identify the Doctor and show the Doctor's approval. In the same transaction the system writes one `record_signatures` row:
  - `document_type` = `BENH_AN_TONG_HOP`, `document_id` = the record id;
  - `document_version` = the record version after closing, `document_sha256` = `package_sha256`;
  - `signer_type` = `STAFF`, with `signer_account_id` and `signer_name` = the Doctor's academic title and name;
  - `signature_method` = `ELECTRONIC_CONFIRMATION`, `signed_at` = `closed_at`.
  
  The certificate columns stay empty. Because the confirmation is tied to the package hash, any later change to the package can be detected. Chosen over a digital signature on 17/11/2026 (Decisions needed 11). The clinic's quy chế (TT 13/2025 Điều 6 khoản 3 điểm b) must name this form of confirmation.
- **BR-58-07 (BR-42): Audit.** One `LOCK` row on entity `medical_records`, with before/after values:
  - record status, `closed_at`, `soluutru`;
  - visit status;
  - the applied coverage rate and each receipt's patient amount when they changed.
  
  Plus one `EXPORT` row on entity `record_archives` with the archive number, URI and hash. Repricing writes its own `payments` audit rows, as in `InsuranceCoverageServiceImpl`.
- **BR-58-08 (BR-32, BR-126): All or nothing.** Every step from BR-58-03 to BR-58-07, and BR-58-09, runs in one transaction. Any failure rolls everything back and the record stays open (Report 3 EF-04). The visit row is locked first (`SELECT … FOR UPDATE`), so a second click, the end-of-day job or a paraclinical cancellation waits. If the archive file was written and the commit then fails, the file is referenced by no row. The next closing of that visit finds it, keeps it aside as `<soluutru>.json.orphan-<millis>` without deleting it, and writes the new package (`ArchiveStore`).

## Normal flow

1. On the "Kết luận" tab of a visit with a saved conclusion, the Doctor clicks "Xác nhận kết luận". The button is disabled while the conclusion is not saved or has unsaved changes. Adding it after MSG133 of UC-90 is still to do.
2. The client loads the close check (see [API](#api)). It shows any blocking condition of BR-58-01 inline (EF-02, EF-03). Otherwise it shows the confirmation dialog: MSG27 and the field "Loại hồ sơ lưu trữ" (BR-58-06), default "Thông thường (10 năm)".
3. The Doctor checks the category and clicks "Xác nhận". The button shows "Đang xác nhận…" while the request runs.
4. The server checks the guard, BR-58-01 and BR-58-02, then runs BR-58-03 to BR-58-07 and BR-58-09 in one transaction.
5. The system shows MSG130 ("Đã xác nhận kết luận. Lượt khám {visit_code} đã hoàn thành.") and the record opens read-only.
6. The visit leaves the room's lists: the waiting list (UC-39) and the doctor queue list only visits that are not finished. The use case ends.

## Alternative flows

- **AF-01 (step 3): The Doctor does not confirm.** "Hủy" in MSG27 closes the dialog; nothing changes and the record stays open.
- **AF-02 (step 1): Unsaved changes on the tab.** "Xác nhận kết luận" is disabled until the Doctor saves the conclusion (BR-58-02); leaving the tab still asks MSG38 (UC-56 AF-03).

## Exception flows

- **EF-01 (step 4): Guard.** No permission, wrong role or another Doctor's visit: `ACCESS_DENIED` (MSG13). No room or off duty: `ROOM_ASSIGNMENT_REQUIRED`, `DOCTOR_OFF_DUTY` (MSG48). Visit not concludable: `VISIT_NOT_IN_EXAMINATION`. Record already closed: `MEDICAL_RECORD_CLOSED` (MSG68). Unknown visit: `VISIT_NOT_FOUND`.
- **EF-02 (step 2 or 4): Paraclinical results missing.** `CLOSE_PARACLINICAL_RESULTS_PENDING` (new, 409) with `details.count`. MSG25: "Không thể kết luận: còn {number} dịch vụ cận lâm sàng chưa có kết quả." The use case ends.
- **EF-03 (step 2 or 4): Prescription not accepted.** `PRESCRIPTION_NOT_ACCEPTED` (new, 409) with `details.lienthongStatus`. MSG67: "Vui lòng gửi đơn thuốc thành công trước khi xác nhận kết luận." The message has a link to the "Đơn thuốc" tab. The use case ends.
- **EF-04 (step 4): No saved conclusion.** `CLOSE_CONCLUSION_REQUIRED` (new, 409). MSG129 (**ASSUMPTION**, new): "Vui lòng lưu kết luận trước khi xác nhận." This only happens when the client is bypassed, since the button is disabled.
- **EF-05 (step 4): Changed meanwhile.** Stale conclusion `version`, or a concurrent update of the visit or record: `CONCURRENT_MODIFICATION`. MSG75: "Kết luận vừa được người khác cập nhật. Vui lòng tải lại và thử lại." Nothing changes.
- **EF-06 (any step): Session expired or system error,** including an archive write failure. MSG12 or MSG80. The whole transaction is rolled back and the record stays open (BR-58-08).

## Postconditions

- **Success:**
  - the visit is `COMPLETED` with `completed_at`;
  - the record is `LOCKED` with `closed_at`, `closed_by_account_id` and `soluutru`, and `conclusions.ngayravien` is set;
  - the BHYT coverage rate is fixed; the remaining BHYT receipts are `PENDING` (at the cashier) or `FEE_EXEMPT`;
  - there is one `export_logs` row (`LUU_TRU`), one `record_archives` row (`ARCHIVED`), one archive file and one `record_signatures` row (`ELECTRONIC_CONFIRMATION`);
  - there are `LOCK` and `EXPORT` audit rows.
- **Failure or cancel:** nothing changes, and the record stays open.

## States

| Entity | Transition | Ref. |
| --- | --- | --- |
| Visit | `IN_EXAMINATION` or `WAITING_CONCLUSION` → `COMPLETED` | V19 of `state-transitions.md`, extended to `IN_EXAMINATION` (Deviations) |
| Medical record | `OPEN` → `LOCKED` | R02 |
| Receipt (BHYT) | `PENDING` stays `PENDING`, or `PENDING` ↔ `FEE_EXEMPT` after the last repricing | BR-15, BR-22 (2), BR-37 |
| Record archive | (new) → `ARCHIVED` | BR-126 |

`medical_records.status = ARCHIVED` is not used: every closed record is archived at once, so the archive state lives in `record_archives` (**ASSUMPTION**, Decisions needed 7).

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/conclusions/visits/{visitId}/closure` | The close check: `conclusionSaved`, `conclusionVersion`, `pendingParaclinicalCount`, `prescriptionStatus` (null or the `lienthong_status`), `followUpScheduled`, and `canClose`. Records nothing. |
| `POST` | `/api/v1/conclusions/visits/{visitId}/commands/confirm` | Body `{ conclusionVersion, retentionCategory }`; `retentionCategory` defaults to `STANDARD` and an unknown value is refused with `CLOSE_INVALID` (new, 422, MSG05). Runs BR-58-01 to BR-58-08 and returns `{ visitId, visitStatus, closedAt, archiveNumber }`. |

Both require role Doctor without a pending password change, as for `ConclusionController`.

| Error code | HTTP | Message | When |
| --- | --- | --- | --- |
| `CLOSE_CONCLUSION_REQUIRED` (new) | 409 | MSG129 | No saved conclusion |
| `CLOSE_PARACLINICAL_RESULTS_PENDING` (new) | 409 | MSG25 | BR-58-01 condition 2 |
| `PRESCRIPTION_NOT_ACCEPTED` (new, named in BR-59-09) | 409 | MSG67 | BR-58-01 condition 3 |
| `MEDICAL_RECORD_CLOSED` (exists) | 409 | MSG68 | Record already closed |
| `VISIT_NOT_IN_EXAMINATION` (exists) | 409 | existing | Visit not concludable |
| `CONCURRENT_MODIFICATION` (exists) | 409 | MSG75 | BR-58-02 |

## Messages

| ID | Text | Source |
| --- | --- | --- |
| MSG05 | Loại hồ sơ lưu trữ không đúng định dạng. (`{field_name} không đúng định dạng.`) | BR v7 |
| MSG25 | Không thể kết luận: còn {number} dịch vụ cận lâm sàng chưa có kết quả. | BR v7 |
| MSG27 | Sau khi xác nhận, kết luận và lịch hẹn sẽ bị khóa và lượt khám hoàn thành. Bạn có muốn tiếp tục không? | BR v7 |
| MSG38 | Bạn có các thay đổi chưa được lưu. Bạn có muốn loại bỏ những thay đổi này không? | existing |
| MSG67 | Vui lòng gửi đơn thuốc thành công trước khi xác nhận kết luận. | BR v7, shortened 17/11/2026 (decision 2); the BR v7 text adds "và hoàn tất lịch hẹn tái khám" |
| MSG68 | Hồ sơ bệnh án đã đóng và không thể chỉnh sửa. | BR v7 |
| MSG75 | Kết luận vừa được người khác cập nhật. Vui lòng tải lại và thử lại. | BR v7 |
| MSG129 | (new, agreed 17/11/2026) Vui lòng lưu kết luận trước khi xác nhận. | — |
| MSG130 | (new, agreed 17/11/2026) Đã xác nhận kết luận. Lượt khám {visit_code} đã hoàn thành. | — |

## Schema changes

**One change, approved 17/11/2026 and written as V61.**

| # | Change | Why |
| --- | --- | --- |
| S1 | Migration `V61__add_record_archive_retention_category.sql` (written). Add `record_archives.retention_category VARCHAR(30) NOT NULL DEFAULT 'STANDARD'` with `CHECK (retention_category IN ('STANDARD', 'WORK_ACCIDENT', 'TRAFFIC_ACCIDENT', 'PSYCHIATRIC'))`, plus `CHECK (retention_category = 'STANDARD' OR retention_years >= 20)`. New enum `RecordArchiveRetentionCategory`, field on `RecordArchive`, and an update of `schema.yaml` table 37. | Decision 10A: the reason for a 20-year period must stay with the archive, so the facility can review it before any disposal (Luật Lưu trữ Điều 16). Without the column only the number of years would remain. |

The table has no rows yet (UC-58 is not implemented), so the default only matters for consistency.

Every other column used already exists:

- `visits.status`, `completed_at`;
- `medical_records.status`, `closed_at`, `closed_by_account_id`, `soluutru`;
- `conclusions.ngayravien`;
- `export_logs`, `record_signatures` (`BENH_AN_TONG_HOP`, `ELECTRONIC_CONFIRMATION`) and `record_archives` (`retention_years` ≥ 10) (V10).

The coverage rate is fixed by the visit status, so no column is needed for it (BR-58-03). The new configuration property `clinic.archive.root` is not a schema change.

## Deviations from the sources

| Source | Says | This document | Reason |
| --- | --- | --- | --- |
| `state-transitions.md` S7 | When the visit ends, receipts still `CHO_TT` → `DA_HUY` | BHYT receipts stay `PENDING` after `COMPLETED` | BR-22 (2) and BR-15 in v7 win. **Corrected 17/11/2026.** |
| `state-transitions.md` V19 | Only `CHO_KET_LUAN` → `HOAN_THANH`; cites UC-50, UC-52 | Also from `IN_EXAMINATION` | BR-32 has no such limit; UC-56 concludes a visit without paraclinical services in `IN_EXAMINATION` (`ConcludingVisitGuard`). **Corrected 17/11/2026**, with the UC numbers. |
| [`uc-59-e-prescription.md`](uc-59-e-prescription.md) BR-59-09, EF-12, error table | `PRESCRIPTION_NOT_ACCEPTED` with MSG108 | MSG67 | In the BR v7 message sheet, MSG67 is the UC-58 message; MSG108 in Report 3 is another message. **Corrected 17/11/2026.** |
| Report 3 UC-49 step 9 | Shows MSG67 after ordering | MSG63 ("Đã tạo chỉ định cận lâm sàng {order_number}.") | MSG63 is the "Paraclinical order confirmed" message of the Report 3 message table. **Corrected 17/11/2026.** |
| `schema.yaml` `record_archives` | "gói XML + PDF đã ký" | One JSON package in the CV 365 structure, unsigned, tied to an electronic confirmation (BR-58-09) | BR-126 only asks for an archive number, a data package, a hash and a retention date. TT 13/2025 Điều 3 accepts an electronic confirmation, and CV 365 III.1.d allows JSON. The PDF view comes from the same data (UC-52, UC-57). |
| BR-44 | "Hạn lưu trữ bằng ngày đóng hồ sơ cộng số năm lưu trữ" | `expires_on` = 31/12 of (closing year + years) | Luật Lưu trữ Điều 15 khoản 3 counts "theo năm … kể từ năm kết thúc công việc"; the result is never earlier than BR-44's date. BR-44 also cites "TT 33/2025/TT-BYT (cần đối chiếu bản gốc)": now checked, Phụ lục dòng 44 gives 10 years. |
| Report 3 §11.5 step 2 | Checks the follow-up appointment "has been created" | Never blocks | BR-58-01 condition 4 |

## Decisions needed

All decisions were settled with the product owner on 17/11/2026; they still need the team's decision record.

1. **Settled: correct the documents.** `state-transitions.md` S7 and V19, Report 3 UC-49 step 9 (MSG63) and `uc-59-e-prescription.md` (MSG67) are corrected.
2. **Settled: follow-up condition of BR-32.** It never blocks (BR-58-01 condition 4), and MSG67 is shortened to "Vui lòng gửi đơn thuốc thành công trước khi xác nhận kết luận." so that it names the only reason it is shown. The owner of `Business_Rules_v7.xlsx` should update MSG67 there.
3. **Settled: archive number** `LT-` + visit code (BR-58-06).
4. **Settled: package format.** JSON in the CV 365 structure, with an `extensions` block for data that has no CV 365 field. XML would be equally valid (BR-58-06).
5. **Settled: archive store.** A server directory (`clinic.archive.root`), write-once, as described in BR-58-06. Backups are an operations task.
6. **Settled: success message** MSG130.
7. **Settled: `medical_records.status`.** The record becomes `LOCKED`; `ARCHIVED` stays unused, and the archive state lives in `record_archives`.
8. **Settled: MSG129**, and `ngayravien` = closing date (BR-58-05).
9. **Settled: expiry date** = 31/12 of (closing year + retention years). BR-44's wording ("ngày đóng hồ sơ cộng số năm") should be corrected in `Business_Rules_v7.xlsx` by its owner.
10. **Settled: longer retention periods.** The Doctor picks the retention category in the confirmation dialog (BR-58-06). This uses the schema change S1, approved and written as V61.
11. **Electronic confirmation. Settled 17/11/2026: keep BR-58-09 as written.** The signed-in Doctor's "Xác nhận" is the electronic confirmation of TT 13/2025 Điều 3 khoản 3. No digital signature is used: TT 13 does not require one for the record, and the remote signing of UC-90 stays for the prescription only. Still to do: record this in the team's decision record, and the clinic's quy chế (TT 13/2025 Điều 6 khoản 3 điểm b) must name this form of confirmation.
12. ~~Public or private clinic.~~ **Settled 17/11/2026: private clinic.** Its records are tài liệu lưu trữ tư (Luật Lưu trữ Điều 9 khoản 4 điểm c). Luật KBCB Điều 69 khoản 2 still requires keeping them under the archive law, so TT 33/2025 and Luật Lưu trữ Điều 15 khoản 3 are applied as above. The head of the clinic decides any later disposal. Still to be recorded by the team.

## Implementation plan

1. **Backend.**
   - Domain methods: `Visit.complete(now)`, `MedicalRecord.close(doctorId, soluutru, now)`, `Conclusion.discharge(date)`, and `RecordArchive` / `ExportLog` / `RecordSignature` factories.
   - `ArchivePackageBuilder` for the BR-58-06 package: CV 365 structure, metadata block, `extensions`.
   - `ArchiveStore` (create-new write and SHA-256), with `clinic.archive.root` in `config/properties`.
   - `RetentionRules` for `expires_on`.
   - `MedicalRecordClosureService.closureCheck` and `confirm` (a service of its own, next to `ConclusionService`) on `ConcludingVisitGuard`, calling `InsuranceCoverageService.recalculate` before the status change.
   - Routes in `ConclusionController`, and the error codes of [API](#api).
2. **Tests.**
   - Unit tests for each BR-58-01 condition: a cancelled detail does not block; `SENT`/`REJECTED`/`PENDING` block; no prescription passes.
   - BHYT repricing to `FEE_EXEMPT` and staying `PENDING`; self-paid untouched.
   - Stale version.
   - Rollback when the archive write fails.
   - `expires_on` = 31/12 of (closing year + 10) in the clinic zone, including a closing late on 31/12 in Vietnam time; 20 years for the three accident and psychiatric categories.
   - A second package with the same `soluutru` is refused; the file is read-only.
   - The `record_signatures` row hash equals `package_sha256`.
   - The package contains the CV 365 fields and the metadata block.
   - Audit rows.
   - Every write use case returning MSG68 afterwards.
   - `SecurityRouteIT` routes.
3. **Frontend** (`ConclusionPanel.jsx`, `ClinicalExaminationPage.jsx`):
   - the "Xác nhận kết luận" button, disabled until the conclusion is saved;
   - the close check with inline MSG25/MSG67 and a link to the "Đơn thuốc" tab;
   - the MSG27 dialog with the "Loại hồ sơ lưu trữ" field, and the "Đang xác nhận…" progress label;
   - MSG130 and the read-only record afterwards.
   
   Still to do: the action after MSG133 of UC-90.
4. **Docs.** Decisions 1 and 2 are applied. `schema.yaml` table 37 has `retention_category` (V61). `Business_Rules_v7.xlsx` (BR-44 wording, MSG67 text) is updated by its owner.
