# UC-59, UC-90, UC-91, UC-92, UC-60 — E-prescription

**Status:** Proposed 12/11/2026. Not implemented. Two choices made with the product owner on 12/11/2026 are marked **(PO 12/11)** and still need the team's decision record (see [Decisions needed](#decisions-needed)); the rest follows the sources listed below. Nothing here changes the schema; the schema changes it needs are listed, not applied.

**Sources:** TT 26/2025/TT-BYT ([`reference/TT-26-2025-TT-BYT.md`](reference/TT-26-2025-TT-BYT.md)), QĐ 808/QĐ-BYT ([`reference/QD-808-2022-BYT.md`](reference/QD-808-2022-BYT.md)), `Business_Rules_v7.xlsx` (BR-23, BR-32, BR-33, BR-45, BR-47, BR-48, BR-49, BR-52, BR-54, BR-65, BR-79, BR-107), `usecases.xlsx` (UC-56, UC-58, UC-59, UC-60, UC-90 → UC-93), Report 3 §11.6 and §11.7, I1-D45, I1-D46, migrations V9, V10, V55.

**Actor:** Doctor. **Secondary actor:** the simulated national prescription system (Hệ thống đơn thuốc quốc gia, BR-45).

**Use cases in this document**

| UC | Name | Summary |
| --- | --- | --- |
| UC-59 | Prescribe Medication | Create or change the visit's prescription while it is editable. |
| UC-90 | Submit e-Prescription | Sign the prescription with the signing PIN and send it to the national system. |
| UC-91 | View Prescription Submission Result | See the result and the history of every send. |
| UC-92 | Correct and Resubmit Prescription | Change a rejected prescription (UC-59) and send it again (UC-90). |
| UC-60 | Print Prescription | Print the accepted prescription on the form of TT 26/2025 Phụ lục I. |

## Description

After saving the conclusion (UC-56) and before confirming it (UC-58), the Doctor prescribes from the active medicine catalog: one prescription per visit, ordinary medicines only. The Doctor signs it with a signing PIN and the system sends it to the simulated national prescription system with the diagnoses of the conclusion and the follow-up date, if any. An accepted prescription is locked and can be printed; a rejected one is corrected and sent again under the same prescription code. The conclusion cannot be confirmed while a prescription exists that is not accepted (BR-32). The clinic sells no medicine; the patient buys it outside with the printed prescription (BR-33).

## Preconditions

- The Doctor is signed in (UC-01), has no pending password change, has role Doctor with status Active, is assigned to an active examination room and is on duty there now (BR-05, BR-19, BR-55) — the checks of `ConcludingVisitGuard`.
- The visit is in the Doctor's room, in status `CHO_KET_LUAN` (or `DANG_KHAM` with no paraclinical order, as UC-56 allows), and its medical record is Open.
- The conclusion of the visit is saved with exactly one main diagnosis (UC-56, BR-31). Otherwise `PRESCRIPTION_CONCLUSION_REQUIRED`.
- The Doctor's account has a prescriber link code `ma_lienthong_bacsi` (BR-49, BR-65, UC-93) and the facility has `ma_cskcb` and `ma_lienthong_donthuoc` (BR-48, UC-77). Missing codes block UC-59 and UC-90, not viewing.
- For UC-90 the Doctor has set a signing PIN (**PO 12/11**, see BR-59-10).

## Business rules

- **BR-59-01 (BR-33, TT 26 Điều 4 khoản 2).** At most one prescription per visit record (`uq_prescriptions_medical_record_id`). It is created by the first save of UC-59; its 14-character code is generated then and never changes, even when the prescription is corrected and resent (BR-52).
- **BR-59-02 (BR-52, QĐ 808 VII.1).** Code `xxxxxyyyyyyy-c`: the facility code `ma_cskcb` (5 digits), 7 random characters from `0-9a-z`, `-`, type `c`. Generation retries on a duplicate. The lowercase suffix follows QĐ 808 and V55; TT 26 Phụ lục I prints `C` — **TBD**, printed form shows the stored value.
- **BR-59-03 (BR-33, BR-79, I1-D46).** A medicine can be prescribed only when it is Active, has a registration number and is in control group `THUONG`. The doctor's search shows only such medicines. A line whose medicine no longer qualifies when sending is refused (`PRESCRIPTION_MEDICINE_UNAVAILABLE`, MSG40 for Inactive, MSG106 for a changed control group) until the Doctor replaces it.
- **BR-59-04 (BR-107).** Per line: medicine (once per prescription), dose per time 1–50 characters, times per day 1–24, days 1–30 (up to 90 under BR-59-05), quantity a whole number from 1 to 9,999 of the dispensing unit, usage 2–255 characters. Prescription notes (`luu_y`) and advice (`loi_dan`) optional, ≤ 2,000 characters each. The quantity is what the patient buys: the catalog units are countable (viên, ống, lọ, tuýp) and TT 26 Điều 6 khoản 7a writes quantities as whole numbers (leading zero below 10); a split dose such as "½ viên" belongs to the dose per time, and the quantity is rounded up by the Doctor. Column `soluongthuocvattu NUMERIC(10,2)` is not a reason to allow decimals. The quantity is entered by the Doctor; the client suggests `dose × times per day × days` only when the dose is a whole number of the line's dispensing unit ("1 viên" for Viên, or a bare number) and the result is at most 9,999, so "500 mg" or "0,5 viên" suggests nothing (**ASSUMPTION**, the dose is free text).
- **BR-59-05 (BR-107, TT 26 Điều 6 khoản 8, I1-D45).** A line may exceed 30 days (max 90) only when the main or a secondary diagnosis of the saved conclusion is covered by `long_term_prescription_disease_codes`. Checked on save and again on send against the conclusion at that moment.
- **BR-59-06 (BR-23; removed, PO 13/11).** The system does not check the prescribed medicines against the patient's recorded drug allergies. The allergies are only shown in red on the form (BR-23) and the Doctor decides. A textual match could not recognise drug classes (a Penicillin allergy would not warn on Amoxicillin), so it was dropped instead of giving false assurance.
- **BR-59-07 (TT 26 Điều 6 khoản 2–4, Phụ lục I, I1-D45).** Administrative data is read, not entered: name, date of birth, sex, address, phone, ID or passport number and insurance number from the patient and visit. For a patient under 72 months on the prescribing date the age in months is shown, the weight is the latest vital sign of this visit and the accompanying person is the patient's relative (`hotennguoithan`); both are required to send (MSG107). The ID number is optional on the prescription (TT 26 "nếu có"); see [Decisions needed](#decisions-needed) for the V9 comment that says otherwise.
- **BR-59-08 (BR-47, I1-D45).** The diagnoses are the conclusion's main diagnosis (`thu_tu = 1`) and secondary diagnoses, copied into `prescription_diagnoses` when the prescription is sent. After an accepted send they are locked in UC-56 (`CONCLUSION_DIAGNOSES_LOCKED`, exists). The follow-up date is copied from the visit's scheduled follow-up appointment at send time; an appointment created or changed afterwards is valid but does not change the sent prescription (option A, team 09/11/2026).
- **BR-59-09 (BR-47, BR-32).** Lifecycle of `lienthong_status`: `PENDING` (saved, editable) → `SENT` (sent, no answer) / `ACCEPTED` (locked) / `REJECTED` (editable). Every send writes one `prescription_submissions` row. An accepted prescription cannot be changed or deleted (`PRESCRIPTION_ALREADY_SENT`). UC-58 refuses to confirm the conclusion while the visit has a prescription in `PENDING`, `SENT` or `REJECTED` (new `PRESCRIPTION_NOT_ACCEPTED`, MSG108). A visit with no prescription confirms normally.
- **BR-59-10 (TT 26 Điều 10; PO 12/11).** Sending requires an electronic confirmation of the Doctor, simulated with a signing PIN: the Doctor enters the PIN, the system verifies it, computes SHA-256 of the canonical send payload (BR-59-11) and writes one `record_signatures` row (`document_type = DON_THUOC`, `signature_method = ELECTRONIC_CONFIRMATION`, signer = the Doctor, `document_version` = the prescription version, `document_sha256` = the hash). The hash is sent as `signature`. Each resend signs again. Five wrong PINs in a row block signing for 15 minutes (**ASSUMPTION**, same policy as UC-01). The PIN is 6 digits, set and changed after re-entering the password (old UC76, **TBD** as a separate use case); it is stored hashed on `staff_accounts` (schema change, see [Schema changes](#schema-changes)). No certificate is involved; replacing the simulation with a remote signing service is out of scope.
- **BR-59-11 (BR-45, BR-54, QĐ 808 VII).** The gateway to the national system is one interface with a simulator implementation. The payload follows QĐ 808 VII.3: `loai_don_thuoc = "c"`, `ma_don_thuoc`, patient fields, `gioi_tinh` 1/2/3 (unknown/male/female), dates `d/m/Y`, `ngay_gio_ke_don` `Y-m-d H:i:s` in Asia/Ho_Chi_Minh, `ngay_tai_kham` = days from the prescribing date to the follow-up date, `chan_doan[]` from BR-59-08, `thong_tin_don_thuoc[]` with `ma_thuoc = so_dang_ky`, `biet_duoc = hoatchat`, `ten_thuoc`, `don_vi_tinh`, `so_luong`, `cach_dung` = "{dose} × {times}/ngày × {days} ngày, {usage}", `hinh_thuc_dieu_tri` = outpatient (fixed; the Ministry's code list is **TBD**, PO 12/11), `signature` from BR-59-10. The facility and Doctor sign in with their link codes to obtain a 7-day token (QĐ 808 III, VI); the simulator issues tokens without checking a password (**ASSUMPTION**). Payloads are never written to application logs (security.md).
- **BR-59-12 (BR-47, QĐ 808 VII.4–5).** The simulator answers 200 with a success message, 422 with `danh_sach_cac_loi`, or does not answer (timeout 10 s, **ASSUMPTION**). Results map to `ACCEPTED`, `REJECTED` (errors stored as a JSON array) and `NO_RESPONSE`. Nothing is retried automatically; the Doctor sends again (UC-91 AF-01). The simulator rejects when the Doctor's link code is not registered, the code format is wrong or a required field is missing, so the team can demonstrate UC-92; its rules are configuration, not business rules.
- **BR-59-13 (TT 26 Điều 6 khoản 5–7, Phụ lục I; UC-60).** The printed prescription shows the facility name, address and phone, the prescription code and a QR code of it (**ASSUMPTION**: QR content = the code), patient data per BR-59-07, the diagnoses, each medicine as "{hoatchat} ({tenthuocvattu}) {hamluong}" — or the trade name alone when the catalog marks a multi-ingredient product (**TBD**: the catalog has no such flag; until then the INN + trade-name form is printed for every line) — with quantity, unit, dose, times per day, days and usage, quantities below 10 printed with a leading zero, notes, advice, follow-up date, prescribing date, the Doctor's academic title and name, and the confirmation time of BR-59-10. Only an `ACCEPTED` prescription is printed; a print from a closed record is marked "Bản sao" (Report 3 UC-60 AF-01). Each print writes an audit row (`PRINT`) and sets `printed_at` on the first print.
- **BR-59-14 (BR-42, CV 365 III.1.1.c and III.1.1.đ, V10).** Audit rows in `audit_logs`: `CREATE` on `prescriptions` with the saved notes, advice and lines; `UPDATE` with only the fields whose value changed (`changed_fields`) and their old and new values, as `audit_logs` stores them under V10 (old values must allow restoring the data), and no row when a save changes nothing; `VIEW_RECORD` when the Doctor opens an existing prescription (medical-record access); `DELETE` with the removed prescription's values when a pending prescription is removed, `SIGN` on each confirmation, `PRINT` on each print. The send history lives in `prescription_submissions`, not in the audit log.
- **BR-59-15 (BR-47).** Only the Doctor of the visit can save, send, resend, delete or print the prescription while the record is Open. Reprinting from a closed record follows UC-52's access rules.

## Normal flow — UC-59 Prescribe Medication

1. On the Conclusion screen of the visit, after the conclusion is saved, the Doctor opens the "Đơn thuốc" tab.
2. The system checks the preconditions and loads: the patient header, the conclusion's diagnoses, the drug allergies in red (BR-23), the scheduled follow-up appointment if any, and the current prescription if one exists (status, lines, notes, advice, last send result).
3. The system shows the prescription form: the medicine lines, "Lưu ý" and "Lời dặn". For a patient under 72 months it also shows the age in months, the weight and the accompanying person (BR-59-07) and marks missing values.
4. The Doctor searches a medicine by name, active ingredient, strength, internal code or registration number (`GET /api/v1/prescriptions/medicine-search`, BR-59-03) and selects one. The line shows active ingredient, trade name, strength, route, unit and control group.
5. The Doctor enters dose per time, times per day, days, quantity and usage. The client checks BR-59-04 and shows a notice when days exceed 30 while no diagnosis allows it (BR-59-05).
6. The Doctor repeats steps 4–5 for the other medicines, then enters notes and advice.
7. The Doctor clicks "Lưu đơn thuốc". The button is disabled until at least one complete line exists and while saving ("Đang lưu…").
8. The server checks, in one transaction: the guard, the conclusion (`PRESCRIPTION_CONCLUSION_REQUIRED`), the link codes (EF-07), the medicines (BR-59-03), the lines (BR-59-04, BR-59-05) and that the prescription is editable (BR-59-09).
9. On the first save the system creates the prescription with a new code (BR-59-01, BR-59-02), `prescribed_at = now`, `doituongbn` from the visit type, the lines as snapshots of the catalog fields, status `PENDING`. On a later save it replaces the lines, notes and advice and keeps the code. It writes the audit row (BR-59-14).
10. The system shows MSG03 ("Đơn thuốc") and the actions "Ký và gửi đơn" (UC-90) and "Xóa đơn" (AF-03). The use case ends.

## Alternative flows — UC-59

- **AF-01: removed (PO 13/11).** No allergy warning on save; see BR-59-06.
- **AF-02 (step 1): No medication is needed.** The Doctor skips the tab. UC-58 confirms the conclusion without a prescription.
- **AF-03 (step 10): Remove a pending prescription.** "Xóa đơn" asks for confirmation (MSG109) and deletes the prescription and its lines while it is `PENDING` (`DELETE`, BR-59-14). A prescription that was ever sent cannot be deleted; a rejected one is corrected instead (UC-92). **ASSUMPTION**: Report 3 has no delete; without it a Doctor who changes their mind would be unable to confirm the conclusion (BR-59-09).
- **AF-04 (step 7): Leave with unsaved changes.** MSG38 asks first.
- **AF-05 (step 5): More than 30 days.** Allowed when BR-59-05 holds; the line shows "Bệnh được kê dài ngày: {disease}".

## Normal flow — UC-90 Submit e-Prescription

1. The Doctor clicks "Ký và gửi đơn" on a `PENDING` or `REJECTED` prescription. The system shows the preview of the prescription as it will be printed (BR-59-13), the diagnoses that will be sent and the follow-up date if any, with the reminder "Sau khi gửi thành công, đơn thuốc và chẩn đoán không sửa được nữa."
2. The Doctor checks the preview and enters the signing PIN, then clicks "Xác nhận ký và gửi" (disabled until 6 digits are entered; "Đang gửi…" while sending).
3. The server, in one transaction: runs the guard; verifies the PIN (EF-10, EF-11); re-checks the prescription against BR-59-03, BR-59-04, BR-59-05 and BR-59-07 against the current conclusion and vital signs (EF-02 → EF-05); copies the diagnoses into `prescription_diagnoses` and the follow-up date into `ngay_tai_kham` (BR-59-08); builds the payload, computes the hash and writes the signature row (BR-59-10); obtains or reuses the gateway token (BR-59-11); creates the submission row with the next `attempt_no` and `submitted_at = now`.
4. The system calls the gateway.
5. The gateway accepts. The system sets the submission to `ACCEPTED` with `responded_at`, sets the prescription to `ACCEPTED` with `lienthong_sent_at`, and locks it. The conclusion's diagnoses are locked (BR-59-08).
6. The system shows MSG110 with the prescription code and the actions "In đơn thuốc" (UC-60) and "Xác nhận kết luận" (UC-58). The use case ends.

## Alternative flows — UC-90

- **AF-01 (step 5): The gateway rejects.** The submission is `REJECTED` with the error list; the prescription becomes `REJECTED` and stays editable. The system shows MSG111 and the error list; "Sửa đơn" opens UC-92. The signature row stays as a record of the attempt.
- **AF-02 (step 4): No answer.** After the timeout the submission is `NO_RESPONSE`; the prescription becomes `SENT`. The system shows MSG86. "Gửi lại" repeats UC-90 from step 1 with a new attempt number and a new signature; the simulator treats a resend of a code it already accepted as accepted (idempotent, **ASSUMPTION**).
- **AF-03 (step 2): Cancel.** "Hủy" closes the dialog; nothing changes.

## Normal flow — UC-91 View Prescription Submission Result

1. On the "Đơn thuốc" tab, or from the visit's medical record (UC-52), the Doctor opens "Kết quả gửi".
2. The system lists every submission of the prescription, newest first: attempt number, sent time, result (Chấp nhận / Bị từ chối / Không phản hồi), response time, the error list of a rejection, and the sender.
3. The current status is shown on the tab header with the code and, for `ACCEPTED`, the accepted time. The use case ends.

- **AF-01 (step 2): The last result is `SENT` or `REJECTED`.** The screen offers "Gửi lại" (UC-90) or "Sửa đơn" (UC-92).

## Normal flow — UC-92 Correct and Resubmit Prescription

1. From UC-91 AF-01 or the tab, the Doctor clicks "Sửa đơn" on a `REJECTED` prescription. The errors of the last rejection stay visible above the form.
2. The Doctor changes lines, notes or advice and saves (UC-59 steps 4–9). The code does not change; the status stays `REJECTED` until the next send.
3. The Doctor signs and sends again (UC-90). The next submission has `attempt_no + 1`. The use case ends.

- **AF-01 (step 1): The rejection concerns the conclusion or the Doctor's code.** The Doctor fixes the conclusion in UC-56 (its diagnoses are not locked yet) or asks the Admin to correct the link code (UC-93), then sends again without changing the prescription.

## Normal flow — UC-60 Print Prescription

1. The Doctor clicks "In đơn thuốc" on an `ACCEPTED` prescription, from the tab, from UC-90 step 6 or from the closed record (UC-52).
2. The system builds the form (BR-59-13) and shows the preview; a print from a closed record carries "Bản sao".
3. The Doctor clicks "In". The system records the `PRINT` audit row, sets `printed_at` on the first print and opens the browser print dialog (printer integration TBD, HW-02).
4. The Doctor hands the prescription to the patient with the conclusion (UC-57). The use case ends.

- **AF-01 (step 1): The prescription is not accepted.** The button is disabled with the hint "Gửi đơn thành công trước khi in." (MSG112). A preview with the watermark "BẢN NHÁP – KHÔNG CÓ GIÁ TRỊ" is available from UC-90 step 1 only; it is not printed and not logged (**ASSUMPTION**).
- **AF-02 (step 3): The print fails.** MSG49; the Doctor can retry.

## Exception flows

- **EF-01: Conclusion not saved.** `PRESCRIPTION_CONCLUSION_REQUIRED`; the tab shows MSG113 and no form.
- **EF-02 (UC-59 step 8, UC-90 step 3): A line is invalid.** `PRESCRIPTION_INVALID` with the field names; MSG02, MSG05 or MSG08 under the field.
- **EF-03: Medicine not prescribable.** `PRESCRIPTION_MEDICINE_UNAVAILABLE`; MSG40 or MSG106 under the line; the line must be replaced.
- **EF-04: Days over 30 without a qualifying diagnosis.** `PRESCRIPTION_LONG_TERM_NOT_ALLOWED`; MSG114 under the days field.
- **EF-05 (UC-90 step 3): Child data missing.** `PRESCRIPTION_CHILD_DATA_REQUIRED` (new); MSG107 names the missing item; the Doctor records the weight in UC-47 or asks the Receptionist to add the relative.
- **EF-06: Prescription locked.** `PRESCRIPTION_ALREADY_SENT`; MSG115; the form is read-only.
- **EF-07: Link code missing.** `PRESCRIBER_LINK_CODE_REQUIRED` or `FACILITY_LINK_CODE_REQUIRED` (new); MSG116; the form is disabled.
- **EF-08: Visit or record not eligible.** `VISIT_NOT_IN_EXAMINATION`, `MEDICAL_RECORD_CLOSED`, `ACCESS_DENIED`, `DOCTOR_OFF_DUTY`, `ROOM_ASSIGNMENT_REQUIRED` as in UC-56; MSG13, MSG48 or MSG99.
- **EF-09: Concurrent change.** `CONCURRENT_MODIFICATION` on a stale `version`; MSG75, the client reloads.
- **EF-10 (UC-90 step 3): Wrong PIN.** `SIGNING_PIN_INCORRECT` (new); MSG117; the PIN field clears.
- **EF-11 (UC-90 step 3): PIN blocked or not set.** `SIGNING_PIN_BLOCKED` / `SIGNING_PIN_NOT_SET` (new); MSG118 / MSG119.
- **EF-12 (UC-58): Prescription not accepted.** `PRESCRIPTION_NOT_ACCEPTED` (new); MSG108; the conclusion stays unconfirmed.
- **EF-13: Session expired.** MSG12.
- **EF-14: System or network error between the client and the backend.** MSG80. A failure in UC-90 step 3 rolls back the submission and signature rows; a failure after step 4 keeps the submission row with the recorded result.

## Postconditions

- **UC-59 success:** the visit has one prescription in `PENDING` (or still `REJECTED`) with its code, lines, notes and advice; one audit row. **Failure:** nothing changes.
- **UC-90 success:** one more submission row; the prescription is `ACCEPTED`, locked, with its diagnoses and follow-up date copied; one signature row; the conclusion's diagnoses are locked. **Rejected / no answer:** one more submission row and signature row; the prescription is `REJECTED` or `SENT`; nothing is locked.
- **UC-60:** one `PRINT` audit row; `printed_at` set on the first print; no data changes.

## States

Proposed section for `state-transitions.md` (entity "Đơn thuốc", column `lienthong_status`):

| ID | From | Event | Condition | To | UC |
| --- | --- | --- | --- | --- | --- |
| P01 | — | Doctor saves the first version | Conclusion saved; visit eligible | `PENDING` | UC-59 |
| P02 | `PENDING`, `REJECTED` | Doctor saves again | Editable | same | UC-59, UC-92 |
| P03 | `PENDING` | Doctor deletes | Never sent | — (row removed) | UC-59 AF-03 |
| P04 | `PENDING`, `REJECTED`, `SENT` | Doctor signs and sends; gateway accepts | PIN verified; checks pass | `ACCEPTED` | UC-90 |
| P05 | `PENDING`, `REJECTED`, `SENT` | Gateway rejects | — | `REJECTED` | UC-90 AF-01 |
| P06 | `PENDING`, `REJECTED`, `SENT` | No answer | — | `SENT` | UC-90 AF-02 |
| P07 | `ACCEPTED` | — | Locked; only print | — | UC-60 |

Confirming the conclusion (V19) gains the condition "no prescription, or the prescription is `ACCEPTED`".

## API

All endpoints require role Doctor without a pending password change and run the `ConcludingVisitGuard` checks for the visit, except the print and submission reads from a closed record, which use UC-52's access.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/prescriptions/medicine-search?keyword=` | Active `THUONG` medicines with a registration number; keyword ≤ 100 characters, accent-insensitive (BR-110); at most 20 results ordered by name. `[{ id, code, registration, name, activeIngredient, strength, route, unit }]`. |
| `GET` | `/api/v1/prescriptions/visits/{visitId}` | The visit's prescription with lines, diagnoses to be sent, follow-up date, allergies, child data, missing link codes and the last submission. When the visit has no prescription yet the prescription fields are `null` and the rest is still returned, because the form needs it. |
| `PUT` | `/api/v1/prescriptions/visits/{visitId}` | Create (first call) or replace the prescription. Body: `{ "version": null on the first save, then the loaded version (required), "notes": "", "advice": "", "lines": [{ "medicineId", "dosePerTime", "timesPerDay", "days", "quantity", "usage", "note" }] }`; `notes` and `advice` are `luu_y` and `loi_dan`, named like the other request bodies of the API. Returns the prescription. |
| `DELETE` | `/api/v1/prescriptions/{prescriptionId}` | Remove a `PENDING` prescription (AF-03). `204`. |
| `POST` | `/api/v1/prescriptions/{prescriptionId}/commands/sign-and-send` | Body `{ "signingPin": "123456", "version": n }`. Returns the prescription with the new submission. |
| `GET` | `/api/v1/prescriptions/{prescriptionId}/submissions` | Submission history, newest first. |
| `GET` | `/api/v1/prescriptions/{prescriptionId}/print` | Data of the printed form (BR-59-13), `copy = true` from a closed record. |
| `POST` | `/api/v1/prescriptions/{prescriptionId}/commands/prepare-print` | Writes the `PRINT` audit row and sets `printed_at` on the first print; same pattern as UC-41 ticket printing. |
| `PUT` | `/api/v1/me/signing-pin` | Set or change the PIN after re-entering the password (**TBD**, old UC76). |

| Error code | HTTP | Message | When |
| --- | --- | --- | --- |
| `PRESCRIPTION_CONCLUSION_REQUIRED` | 409 | MSG113 | No saved conclusion with a main diagnosis |
| `PRESCRIPTION_INVALID` | 422 | MSG02 / MSG05 / MSG08 | Line or text outside BR-59-04 |
| `PRESCRIPTION_MEDICINE_UNAVAILABLE` | 409 | MSG40 / MSG106 | Inactive, no registration number, or not `THUONG` |
| `PRESCRIPTION_LONG_TERM_NOT_ALLOWED` | 422 | MSG114 | Days > 30 without a qualifying diagnosis |
| `PRESCRIPTION_ALREADY_SENT` | 409 | MSG115 | Save or delete of an `ACCEPTED` prescription |
| `PRESCRIPTION_CHILD_DATA_REQUIRED` (new) | 409 | MSG107 | Under 72 months without weight or accompanying person |
| `PRESCRIPTION_NOT_ACCEPTED` (new) | 409 | MSG108 | UC-58 with a prescription not accepted |
| `PRESCRIPTION_NOT_PRINTABLE` (new) | 409 | MSG112 | Print of a prescription not accepted |
| `PRESCRIBER_LINK_CODE_REQUIRED` (new) | 409 | MSG116 | Doctor without `ma_lienthong_bacsi` |
| `FACILITY_LINK_CODE_REQUIRED` (new) | 409 | MSG116 | Facility without `ma_lienthong_donthuoc` or `ma_cskcb` |
| `SIGNING_PIN_INCORRECT` (new) | 422 | MSG117 | Wrong PIN |
| `SIGNING_PIN_BLOCKED` (new) | 429 | MSG118 | Fifth wrong PIN within the block window |
| `SIGNING_PIN_NOT_SET` (new) | 409 | MSG119 | No PIN on the account |
| `NATIONAL_PRESCRIPTION_SYSTEM_UNAVAILABLE` (new) | 502 | MSG86 | No answer from the gateway; the submission row is `NO_RESPONSE` |
| `CONCURRENT_MODIFICATION` | 409 | MSG75 | Stale `version` |
| `REQUEST_VALIDATION_FAILED` | 400 | MSG02 | Malformed body |

Existing codes `VISIT_NOT_FOUND`, `VISIT_NOT_IN_EXAMINATION`, `MEDICAL_RECORD_CLOSED`, `ACCESS_DENIED`, `DOCTOR_OFF_DUTY`, `ROOM_ASSIGNMENT_REQUIRED` and `CONCLUSION_DIAGNOSES_LOCKED` apply as in UC-48 and UC-56.

## Messages

| ID | Text |
| --- | --- |
| MSG02 | {field_name} là bắt buộc. |
| MSG03 | {item} cập nhật thành công. |
| MSG05 | {field_name} không đúng định dạng. |
| MSG08 | Vượt quá độ dài tối đa {max_length} ký tự. |
| MSG12 | Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại. |
| MSG13 | Bạn không có quyền thực hiện thao tác này. |
| MSG38 | Bạn có các thay đổi chưa được lưu. Bạn có muốn loại bỏ những thay đổi này không? |
| MSG40 | {item} đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác. |
| MSG49 | Không thể in. Vui lòng kiểm tra máy in và thử lại. |
| MSG75 | {item} vừa được người khác cập nhật. Vui lòng tải lại và thử lại. |
| MSG80 | Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên. |
| MSG86 | Không thể kết nối với Hệ thống đơn thuốc quốc gia. Vui lòng thử lại. |
| MSG99 | **ASSUMPTION:** Lượt khám đã thay đổi. Vui lòng tải lại. |
| MSG106 | **ASSUMPTION** (new): {medicine} không còn thuộc nhóm thuốc thông thường nên không kê được. Vui lòng chọn thuốc khác. |
| MSG107 | **ASSUMPTION** (new): Trẻ dưới 72 tháng tuổi cần có {item} trước khi gửi đơn thuốc. |
| MSG108 | **ASSUMPTION** (new): Đơn thuốc chưa được Hệ thống đơn thuốc quốc gia chấp nhận. Vui lòng gửi đơn thành công trước khi xác nhận kết luận. |
| MSG109 | **ASSUMPTION** (new): Xóa đơn thuốc này? Mã đơn {code} sẽ không dùng lại. |
| MSG110 | **ASSUMPTION** (new): Đã gửi đơn thuốc {code} thành công. Đơn thuốc đã được khóa. |
| MSG111 | **ASSUMPTION** (new): Hệ thống đơn thuốc quốc gia từ chối đơn thuốc. Vui lòng sửa theo danh sách lỗi và gửi lại. |
| MSG112 | **ASSUMPTION** (new): Gửi đơn thành công trước khi in. |
| MSG113 | **ASSUMPTION** (new): Lưu kết luận với chẩn đoán bệnh chính trước khi kê đơn. |
| MSG114 | **ASSUMPTION** (new): Số ngày dùng tối đa 30 ngày. Chỉ kê đến 90 ngày khi chẩn đoán thuộc danh mục bệnh được kê dài ngày. |
| MSG115 | **ASSUMPTION** (new): Đơn thuốc đã gửi thành công nên không sửa hoặc xóa được. |
| MSG116 | **ASSUMPTION** (new): Chưa có mã liên thông {item}. Vui lòng liên hệ quản trị viên. |
| MSG117 | **ASSUMPTION** (new): Mã PIN ký không đúng. |
| MSG118 | **ASSUMPTION** (new): Bạn đã nhập sai mã PIN quá nhiều lần. Vui lòng thử lại sau 15 phút. |
| MSG119 | **ASSUMPTION** (new): Bạn chưa thiết lập mã PIN ký. Vui lòng thiết lập trong trang tài khoản. |

MSG106 → MSG119 continue after MSG105 (uc-55). I1-D45 reserved "MSG93–MSG96" for these messages, but Report 3 already uses MSG93 and MSG94 for rooms; the decision needs the numbers corrected.

## Schema changes

Proposals only; none applied.

| Change | Reason |
| --- | --- |
| `staff_accounts.signing_pin_hash VARCHAR(100)`, `signing_pin_failed_count SMALLINT NOT NULL DEFAULT 0`, `signing_pin_blocked_until TIMESTAMPTZ` | BR-59-10 (PO 12/11) |
| `prescription_details.lieu_moi_lan`, `so_lan_ngay`, `so_ngay_dung`, `soluongthuocvattu` unchanged; `cachdung` stays 255 | BR-59-04 maps onto V55 |
| `medicine_catalog.nhieu_hoat_chat BOOLEAN` (optional) | BR-59-13 naming rule; **TBD** |
| No change to `prescriptions.lienthong_status` | BR-59-09 reuses `PENDING`, `SENT`, `ACCEPTED`, `REJECTED` |

## Deviations from the sources

| Source | Says | This document | Why |
| --- | --- | --- | --- |
| BR-47 | "Bỏ 'đã ký' (không làm ký điện tử)" | Simulated electronic confirmation with a PIN before sending | TT 26 Điều 10 requires a signed e-prescription (PO 12/11); BR-47 must be updated |
| Report 3 UC-59 | Drug name, unit, quantity, usage, days; no code; prescription saved to the record and printed | Fields of BR-107; code at creation; send before confirming the conclusion | BR-33, BR-52, BR-107 as revised on 09/11/2026 |
| Report 3 UC-59 AF-02 | Edit until the record is closed | Edit until accepted by the national system | BR-47 |
| Report 3 UC-60 | Print any saved prescription | Print only an accepted one; draft preview with watermark | usecases.xlsx UC-60 |
| V9 comment | Refuse to print or send without an ID or passport number | Optional | TT 26 Điều 6 khoản 2 "(nếu có)", Phụ lục I note 3; **decision needed** |
| QĐ 808 VII.3 row 10 | Guardian required for a child "hơn 72 tháng" | Under 72 months | TT 26 Điều 6 khoản 4; QĐ 808 wording is a typo |
| Report 3 UC-59 step 5 | Check the medicines against the patient's allergies and warn | No check; allergies shown only (BR-23) | PO 13/11: a textual match misses drug classes and gives false assurance; **decision needed** |
| usecases.xlsx UC-92 | Correct and resend only | Same, plus a pending prescription can be deleted before the first send | AF-03, **ASSUMPTION** |

## Decisions needed

1. **Signing by PIN (PO 12/11).** Record it, update BR-47 and the Actors sheet, add the PIN use case (old UC76) and the schema change above.
2. **Correct only rejected prescriptions (PO 12/11).** Record that TT 26 Điều 6 khoản 9 (replacement prescription) is out of scope because the record is closed right after the conclusion, so a replacement would need a new visit; BR-33 stays "one per visit".
3. **ID number optional** on the prescription (TT 26) versus the V9 comment and the 2025 change log of UC-59 ("yêu cầu người bệnh có số định danh hoặc hộ chiếu").
4. **`hinh_thuc_dieu_tri`** fixed to outpatient (PO 12/11); the Ministry's code value is TBD.
5. **Prescription-type suffix** `c` versus `C` (BR-59-02).
6. **Message numbers** MSG106 → MSG119 and the I1-D45 correction.
7. **`permission-matrix.md`** Doctor/Prescription row: add send, resend, view result, delete pending; the medicine search for Doctors.
8. **No drug-allergy check (PO 13/11).** Record the removal of the Report 3 UC-59 step 5 check (BR-59-06, AF-01) and of MSG26 from this use case.
9. **Multi-ingredient flag** on the catalog for the printed name (BR-59-13), or print INN + trade name for every line.

## Known limitations

- The simulator does not implement the national system's own validation; its rejection rules are configuration for demonstrations.
- Printing uses the browser print dialog (HW-02 TBD).
- There is no automatic drug-allergy check (BR-59-06, PO 13/11); the Doctor relies on the allergies shown in red.
- The PIN simulates the confirmation of TT 26 Điều 10 and TT 13/2025; it is not a digital signature with a certificate, and `record_signatures.signature_method` records it as `ELECTRONIC_CONFIRMATION`.
