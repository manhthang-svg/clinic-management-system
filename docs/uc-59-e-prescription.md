# UC-59, UC-90, UC-91, UC-92, UC-60 — E-prescription

**Status:** Proposed 12/11/2026, revised 13/11/2026. UC-59 is implemented (commit 61bd0b9). UC-90 is implemented on 14/11/2026 with the simulators of [Signing simulator](#signing-simulator) (migration V58). The deletion of a pending prescription (AF-03) and UC-91 on the "Đơn thuốc" tab (the history of every send, with the signature verified again, BR-59-16) are implemented on 15/11/2026; opening UC-91 from a closed record (UC-52) is not. UC-92 is implemented on 15/11/2026 on the tab ("Sửa đơn", "Gửi lại", AF-01 guidance); opening it from UC-91 in a closed record is not. UC-60 is implemented on 15/11/2026 on the tab (preview, print, `PRINT` audit, QR code); the copy printed from a closed record (UC-52, "Bản sao") is not. The UC-58 check (BR-59-09) is not implemented yet. Choices made with the product owner are marked **(PO 12/11)** or **(PO 13/11)** and still need the team's decision record (see [Decisions needed](#decisions-needed)). On 13/11/2026 the signing of UC-90 was revised from a signing PIN (PO 12/11) to a digital signature by remote signing, because TT 26/2025 Điều 10 requires the e-prescription to be "ký số" (BR-59-10); this replaces the PO 12/11 choice once recorded. The rest follows the sources listed below. The schema changes of the digital signature and the dependency `bcpkix-jdk18on` were approved for implementation on 13/11/2026 and applied in V58 (see [Schema changes](#schema-changes)).

**Sources:** TT 26/2025/TT-BYT ([`reference/TT-26-2025-TT-BYT.md`](reference/TT-26-2025-TT-BYT.md)), QĐ 808/QĐ-BYT ([`reference/QD-808-2022-BYT.md`](reference/QD-808-2022-BYT.md)), TT 13/2025/TT-BYT ([`reference/TT-13-2025-TT-BYT.md`](reference/TT-13-2025-TT-BYT.md)), Luật Giao dịch điện tử 2023 ([`reference/Luat-20-2023-QH15-Giao-dich-dien-tu.md`](reference/Luat-20-2023-QH15-Giao-dich-dien-tu.md)), NĐ 23/2025/NĐ-CP ([`reference/ND-23-2025-ND-CP.md`](reference/ND-23-2025-ND-CP.md)), QĐ 425/QĐ-BYT/2025 ([`reference/QD-425-2025-BYT.md`](reference/QD-425-2025-BYT.md)), TT 15/2025/TT-BKHCN ([`reference/TT-15-2025-TT-BKHCN.md`](reference/TT-15-2025-TT-BKHCN.md)), TT 46/2018/TT-BYT, no longer in force ([`reference/TT-46-2018-TT-BYT.md`](reference/TT-46-2018-TT-BYT.md)), `Business_Rules_v7.xlsx` (BR-23, BR-32, BR-33, BR-45, BR-47, BR-48, BR-49, BR-52, BR-54, BR-65, BR-79, BR-107), `usecases.xlsx` (UC-56, UC-58, UC-59, UC-60, UC-90 → UC-93), Report 3 §11.6 and §11.7, I1-D45, I1-D46, migrations V9, V10, V55.

**Actor:** Doctor. **Secondary actors:** the simulated national prescription system (Hệ thống đơn thuốc quốc gia, BR-45) and the simulated Cổng kết nối dịch vụ chứng thực chữ ký số công cộng with its remote-signing certification authority (NĐ 23/2025 Điều 44, BR-59-10).

**Use cases in this document**

| UC | Name | Summary |
| --- | --- | --- |
| UC-59 | Prescribe Medication | Create or change the visit's prescription while it is editable. |
| UC-90 | Submit e-Prescription | Sign the prescription with the Doctor's digital signature (remote signing) and send it to the national system. |
| UC-91 | View Prescription Submission Result | See the result and the history of every send. |
| UC-92 | Correct and Resubmit Prescription | Change a rejected prescription (UC-59) and send it again (UC-90). |
| UC-60 | Print Prescription | Print the accepted prescription on the form of TT 26/2025 Phụ lục I. |

## Description

After saving the conclusion (UC-56) and before confirming it (UC-58), the Doctor prescribes from the active medicine catalog: one prescription per visit, ordinary medicines only. The Doctor signs it with a public digital signature through a remote-signing service, the system verifies the signature and sends it to the simulated national prescription system with the diagnoses of the conclusion and the follow-up date, if any. An accepted prescription is locked and can be printed; a rejected one is corrected and sent again under the same prescription code. The conclusion cannot be confirmed while a prescription exists that is not accepted (BR-32). The clinic sells no medicine; the patient buys it outside with the printed prescription (BR-33).

## Preconditions

- The Doctor is signed in (UC-01), has no pending password change, has role Doctor with status Active, is assigned to an active examination room and is on duty there now (BR-05, BR-19, BR-55) — the checks of `ConcludingVisitGuard`.
- The visit is in the Doctor's room, in status `CHO_KET_LUAN` (or `DANG_KHAM` with no paraclinical order, as UC-56 allows), and its medical record is Open.
- The conclusion of the visit is saved with exactly one main diagnosis (UC-56, BR-31). Otherwise `PRESCRIPTION_CONCLUSION_REQUIRED`.
- The Doctor's account has a prescriber link code `ma_lienthong_bacsi` (BR-49, BR-65, UC-93) and the facility has `ma_cskcb` and `ma_lienthong_donthuoc` (BR-48, UC-77). Missing codes block UC-59 and UC-90, not viewing.
- For UC-90 the Doctor's account has a registered public digital-signature certificate of a trusted certification authority (BR-59-10, BR-59-16).

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
- **BR-59-10 (TT 26 Điều 10; Luật GDĐT Điều 22 khoản 3, Điều 23 khoản 2; NĐ 23 Điều 12, 15, 16, 17, 26, 37, 44; revised 13/11).** The e-prescription is signed with the Doctor's **public digital signature** (chữ ký số công cộng) before it is sent. A signing PIN or other electronic confirmation (TT 13/2025 Điều 3 khoản 3) is not enough: TT 26 Điều 10, the specific rule for prescriptions, requires "ký số". Signing uses the **remote-signing** model (NĐ 23 Điều 26 khoản 1 điểm c): the certification authority (CA) keeps the Doctor's private key (Điều 37 khoản 4) and the Doctor approves each signature with the CA (its app or OTP), so the clinic system never holds the key or the Doctor's signing credential (Luật Điều 22 khoản 3 điểm c). The system reaches the CA through the Cổng kết nối dịch vụ chứng thực chữ ký số công cộng (Điều 44 khoản 2). In this project the gateway, the CA, its certificate-status service and the time-stamp authority are simulators; see [Known limitations](#known-limitations). Steps:
  1. **Pre-check (NĐ 23 Điều 15).** Before a signature is requested, the Doctor's registered certificate must be valid (not expired, suspended or revoked, NĐ 23 Điều 3 khoản 3) on the CA's status service, and the CA's own certificate must be valid on the national trust service (Trung tâm Chứng thực điện tử quốc gia). Otherwise no signature is requested (`SIGNING_CERTIFICATE_INVALID`).
  2. **Document.** The signed document is the canonical send payload of BR-59-11 without `signature`: UTF-8 JSON with keys in a fixed order and no insignificant whitespace (**ASSUMPTION**: QĐ 808 does not define a canonical form). Its SHA-256 is computed and the exact bytes are kept.
  3. **Request.** A signing request is created for the prescription version with the hash and sent to the gateway; it waits for the Doctor's approval at most 5 minutes (**ASSUMPTION**). At most one pending request per prescription.
  4. **Signature.** The approved signature is a detached CMS (PKCS#7) SignedData over the payload bytes with the signer certificate chain, at CAdES baseline level (the signed attributes include `signing-certificate-v2`), RSASSA-PSS with SHA-256 or ECDSA (TT 15/2025/TT-BKHCN Phụ lục I mục 1.2, 1.4, 1.7; the format of the national system's `signature` field is still **ASSUMPTION**: QĐ 808 VII.3 row 27 names the field "chữ ký số" but not its format).
  5. **Verification (NĐ 23 Điều 16, Điều 17 khoản 3).** Before storing or sending, the system checks that the signature matches the payload and the public key of the certificate; the chain leads to a trusted CA (BR-59-16); the certificate and the CA certificate were valid at the signing time, on the CA's status service and the national trust service; the certificate is the one registered for the Doctor. Any failure refuses the signature (`SIGNATURE_INVALID`) and nothing is sent.
  6. **Time stamp (Luật Điều 31; NĐ 23 Điều 32).** A time stamp (RFC 3161) of a licensed time-stamp service over the signature value is put into the signature as its signature-time-stamp attribute (CAdES-T); this signature is what is stored and sent, so the signing time used in step 5 is not only the clinic server's clock. Not mandatory by law ("trường hợp cần xác thực về thời gian ký"); included (**ASSUMPTION**, recommended).
  7. **Record (Luật Điều 10, 13; NĐ 23 Điều 17 khoản 2 điểm c).** One `record_signatures` row: `document_type = DON_THUOC`, `document_id` = the prescription, `document_version` = its version, `document_sha256` = the hash, `signer_type = STAFF`, signer = the Doctor, `signature_method = DIGITAL_SIGNATURE`, certificate serial and issuer, `signature_format = CAdES-T`, `signed_at` = the time-stamp time, `timestamp_token`; plus the exact payload bytes, the CMS value, the certificate chain and the verification evidence (status responses), so the signed message can be verified again later (schema change).
  8. **Notice (NĐ 23 Điều 17 khoản 2 điểm đ).** The Doctor is told whether the signing succeeded and, if not, why.

  The signature value is sent as `signature` (BR-59-11). Each send, including a resend after a rejection or no answer, signs again, because the version and the payload change. No PIN is stored by the clinic system.
- **BR-59-11 (BR-45, BR-54, QĐ 808 VII).** The gateway to the national system is one interface with a simulator implementation. The payload follows QĐ 808 VII.3: `loai_don_thuoc = "c"`, `ma_don_thuoc`, patient fields, `gioi_tinh` 1/2/3 (unknown/male/female), dates `d/m/Y`, `ngay_gio_ke_don` `Y-m-d H:i:s` in Asia/Ho_Chi_Minh, `ngay_tai_kham` = days from the prescribing date to the follow-up date, `chan_doan[]` from BR-59-08, `thong_tin_don_thuoc[]` with `ma_thuoc = so_dang_ky`, `biet_duoc = hoatchat`, `ten_thuoc`, `don_vi_tinh`, `so_luong`, `cach_dung` = "{dose} × {times}/ngày × {days} ngày, {usage}", `hinh_thuc_dieu_tri` = outpatient (fixed; the Ministry's code list is **TBD**, PO 12/11), `signature` = the base64 CMS value of BR-59-10. The facility and Doctor sign in with their link codes to obtain a 7-day token (QĐ 808 III, VI); the simulator issues tokens without checking a password (**ASSUMPTION**). Payloads are never written to application logs (security.md).
- **BR-59-12 (BR-47, QĐ 808 VII.4–5).** The simulator answers 200 with a success message, 422 with `danh_sach_cac_loi`, or does not answer (timeout 10 s, **ASSUMPTION**). Results map to `ACCEPTED`, `REJECTED` (errors stored as a JSON array) and `NO_RESPONSE`. Nothing is retried automatically; the Doctor sends again (UC-91 AF-01). The simulator rejects when the Doctor's link code is not registered, the code format is wrong or a required field is missing, so the team can demonstrate UC-92; its rules are configuration, not business rules.
- **BR-59-13 (TT 26 Điều 6 khoản 5–7, Phụ lục I; UC-60).** The printed prescription shows the facility name, address and phone, the prescription code and a QR code of it (**ASSUMPTION**: QR content = the code), patient data per BR-59-07, the diagnoses, each medicine as "{hoatchat} ({tenthuocvattu}) {hamluong}" — or the trade name alone when the catalog marks a multi-ingredient product (**TBD**: the catalog has no such flag; until then the INN + trade-name form is printed for every line) — with quantity, unit, dose, times per day, days and usage, quantities below 10 printed with a leading zero, notes, advice, follow-up date, prescribing date, the Doctor's academic title and name, and the digital-signature information of BR-59-10 (signer, certificate serial and issuer, signing time). The printout is a paper document converted from a data message (Luật GDĐT Điều 12 khoản 2), so it also carries: the system and the facility that keep the original for lookup (with the prescription code); a mark that it was converted from the electronic prescription; and who printed it and when. The exact wording and form of the mark follow the Government's detailed rules (Điều 12 khoản 4), which are not in `docs/reference/` — **TBD**; until then "Bản in từ đơn thuốc điện tử {code} lưu tại {facility}; người in {name}, {time}" (**ASSUMPTION**). Only an `ACCEPTED` prescription is printed; a print from a closed record is marked "Bản sao" (Report 3 UC-60 AF-01). Each print writes an audit row (`PRINT`) and sets `printed_at` on the first print.
- **BR-59-14 (BR-42, CV 365 III.1.1.c and III.1.1.đ, V10).** Audit rows in `audit_logs`: `CREATE` on `prescriptions` with the saved notes, advice and lines; `UPDATE` with only the fields whose value changed (`changed_fields`) and their old and new values, as `audit_logs` stores them under V10 (old values must allow restoring the data), and no row when a save changes nothing; `VIEW_RECORD` when the Doctor opens an existing prescription (medical-record access); `DELETE` with the removed prescription's values when a pending prescription is removed, `SIGN` on each verified signature (BR-59-10 step 7), `PRINT` on each print. The send history lives in `prescription_submissions`, not in the audit log.
- **BR-59-15 (BR-47).** Only the Doctor of the visit can save, send, resend, delete or print the prescription while the record is Open. Reprinting from a closed record follows UC-52's access rules.
- **BR-59-16 (NĐ 23 Điều 17 khoản 2 điểm d, khoản 3 điểm c; Điều 47 khoản 6–7).** The signing and verification functions keep a list of trusted CA certificates that can be added and removed, and each Doctor's account is linked to one registered signing certificate (serial, issuer, subject, validity, the certificate itself) issued under the Doctor's identity. A certificate whose subject does not identify the Doctor cannot be registered: its subject must carry the Doctor's 12-digit personal identification number (số định danh cá nhân), which the Admin records on the Doctor's account (`staff_accounts.citizen_id`, V60, UC-8 edit form, Doctor only); the number is matched again on every signing request (`SIGNING_CERTIFICATE_INVALID`, reason `CERTIFICATE_NOT_DOCTOR`). The number is shown masked outside the edit form and never written to the audit log (only that it changed). The certificate carries it in the subject's UID attribute as `CCCD:` + the number (**ASSUMPTION**: the form Vietnamese CAs commonly use; to confirm against a real certificate) (NĐ 23/2025 Điều 16 khoản 1 điểm a; TT 15/2025/TT-BKHCN Điều 5 khoản 2 điểm a). Who maintains the CA list and the registrations, and on which screen, is **TBD** (a new Admin use case, next to UC-93); until it exists the CA list is deployment configuration and registrations are loaded by an administrative profile. The signature status of an accepted prescription (valid / not valid, with the reason) is shown wherever the prescription is shown (UC-91, UC-60), by re-running BR-59-10 step 5 on the stored evidence.

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

1. The Doctor clicks "Ký và gửi đơn" on a `PENDING`, `REJECTED` or `SENT` prescription. The system shows the preview of the prescription as it will be printed (BR-59-13, draft watermark), the diagnoses that will be sent and the follow-up date if any, with the reminder "Sau khi gửi thành công, đơn thuốc và chẩn đoán không sửa được nữa."
2. The Doctor checks the preview and clicks "Ký số" ("Đang chuẩn bị ký…" while waiting).
3. The server, in one transaction: runs the guard; checks the `version`; re-checks the prescription against BR-59-03, BR-59-04, BR-59-05 and BR-59-07 against the current conclusion and vital signs (EF-02 → EF-05); checks that the Doctor has a registered certificate (EF-10) and runs the pre-check of BR-59-10 step 1 (EF-11); copies the diagnoses into `prescription_diagnoses` and the follow-up date into `ngay_tai_kham` (BR-59-08); builds the payload and its hash (BR-59-10 step 2); creates the signing request and sends it to the signing gateway (BR-59-10 step 3, EF-15). While the request is pending, a save in UC-59 changes the version and cancels the request (`CANCELLED`).
4. The system shows "Vui lòng xác nhận ký trên ứng dụng ký số của bạn" (MSG120) with the time left, and asks the server for the result every few seconds. The Doctor approves the signature with the CA.
5. The server obtains the signature, verifies it and attaches the time stamp (BR-59-10 steps 4–6, EF-16), then in one transaction: writes the signature row and the `SIGN` audit row (step 7); obtains or reuses the national-system token (BR-59-11); creates the submission row with the next `attempt_no` and `submitted_at = now`.
6. The system calls the national prescription system.
7. The national system accepts. The system sets the submission to `ACCEPTED` with `responded_at`, sets the prescription to `ACCEPTED` with `lienthong_sent_at`, and locks it. The conclusion's diagnoses are locked (BR-59-08).
8. The system shows MSG110 with the prescription code, the signature status "Chữ ký số hợp lệ" (BR-59-10 step 8) and the actions "In đơn thuốc" (UC-60) and "Xác nhận kết luận" (UC-58). The use case ends.

## Alternative flows — UC-90

- **AF-01 (step 7): The national system rejects.** The submission is `REJECTED` with the error list; the prescription becomes `REJECTED` and stays editable. The system shows MSG111 and the error list; "Sửa đơn" opens UC-92. The signature row stays as a record of the attempt.
- **AF-02 (step 6): No answer.** After the timeout the submission is `NO_RESPONSE`; the prescription becomes `SENT`. The system shows MSG86. "Gửi lại" repeats UC-90 from step 1 with a new attempt number and a new signature; the simulator treats a resend of a code it already accepted as accepted (idempotent, **ASSUMPTION**).
- **AF-03 (steps 2 and 4): Cancel.** "Hủy" closes the dialog. Before step 3 nothing changes; after it the pending signing request is cancelled (`CANCELLED`), the copied diagnoses stay until the next send overwrites them, and nothing is sent.
- **AF-04 (step 4): The Doctor declines the signature with the CA.** The request becomes `DECLINED`; MSG121; nothing is sent; the Doctor can start again from step 1.
- **AF-05 (step 4): No approval in time.** After 5 minutes (**ASSUMPTION**) the request becomes `EXPIRED`; MSG122; nothing is sent.
- **AF-06 (step 4): The Doctor closes the screen while waiting.** The request keeps running until it is approved, declined or expires. Reopening the tab shows the pending request and resumes step 4; an approval received while the screen was closed continues at step 5 when the result is next asked for. Nothing is sent until then (**ASSUMPTION**: no background job completes the send).

## Normal flow — UC-91 View Prescription Submission Result

1. On the "Đơn thuốc" tab, or from the visit's medical record (UC-52), the Doctor opens "Kết quả gửi".
2. The system lists every submission of the prescription, newest first: attempt number, sent time, result (Chấp nhận / Bị từ chối / Không phản hồi), response time, the error list of a rejection, the sender, and the signature of the attempt (signer, certificate serial and issuer, signing time, "Hợp lệ" / "Không hợp lệ" with the reason, BR-59-16).
3. The current status is shown on the tab header with the code and, for `ACCEPTED`, the accepted time. A pending signing request is shown with its time left (UC-90 AF-06). The use case ends.

- **AF-01 (step 2): The last result is `SENT` or `REJECTED`.** The screen offers "Gửi lại" (UC-90) or "Sửa đơn" (UC-92).

## Normal flow — UC-92 Correct and Resubmit Prescription

1. From UC-91 AF-01 or the tab, the Doctor clicks "Sửa đơn" on a `REJECTED` prescription. The errors of the last rejection stay visible above the form.
2. The Doctor changes lines, notes or advice and saves (UC-59 steps 4–9). The code does not change; the status stays `REJECTED` until the next send.
3. The Doctor signs and sends again (UC-90). The next submission has `attempt_no + 1`. The use case ends.

- **AF-01 (step 1): The rejection concerns the conclusion or the Doctor's code.** The Doctor fixes the conclusion in UC-56 (its diagnoses are not locked yet) or asks the Admin to correct the link code (UC-93), then sends again without changing the prescription.

## Normal flow — UC-60 Print Prescription

1. The Doctor clicks "In đơn thuốc" on an `ACCEPTED` prescription, from the tab, from UC-90 step 8 or from the closed record (UC-52).
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
- **EF-10 (UC-90 step 3): No registered certificate.** `SIGNING_CERTIFICATE_NOT_REGISTERED` (new); MSG117; "Ký số" is disabled; the Admin registers the certificate (BR-59-16).
- **EF-11 (UC-90 step 3): Certificate not valid.** The Doctor's certificate is expired, suspended or revoked, or the CA certificate is not valid or not trusted (BR-59-10 step 1). `SIGNING_CERTIFICATE_INVALID` (new) with the reason; MSG118; no signature is requested.
- **EF-12 (UC-58): Prescription not accepted.** `PRESCRIPTION_NOT_ACCEPTED` (new); MSG108; the conclusion stays unconfirmed.
- **EF-13: Session expired.** MSG12.
- **EF-14: System or network error between the client and the backend.** MSG80. A failure in UC-90 step 3 rolls back the signing request; a failure in step 5 rolls back the signature and submission rows and the request stays `PENDING` while the CA keeps the approved signature, so asking again retries step 5; a failure after step 6 keeps the submission row with the recorded result.
- **EF-15 (UC-90 steps 3–5): Signing service unavailable.** The signing gateway, the CA, the status service or the time-stamp service does not answer within 10 s (**ASSUMPTION**). `SIGNING_SERVICE_UNAVAILABLE` (new, 502); MSG123; nothing is sent; the Doctor tries again.
- **EF-16 (UC-90 step 5): Signature not valid.** BR-59-10 step 5 fails (signature does not match, chain not trusted, certificate not valid at the signing time, certificate not the Doctor's). `SIGNATURE_INVALID` (new) with the reason; MSG124; the request becomes `FAILED`; nothing is stored or sent.
- **EF-17 (UC-90 step 3): A signing request is already pending.** `SIGNING_REQUEST_PENDING` (new); the client resumes the pending request (AF-06) instead of creating another.

## Postconditions

- **UC-59 success:** the visit has one prescription in `PENDING` (or still `REJECTED`) with its code, lines, notes and advice; one audit row. **Failure:** nothing changes.
- **UC-90 success:** one signing request `SIGNED`; one verified digital-signature row with the signed payload, certificate chain, time stamp and verification evidence; one `SIGN` audit row; one more submission row; the prescription is `ACCEPTED`, locked, with its diagnoses and follow-up date copied; the conclusion's diagnoses are locked. **Rejected / no answer:** the same signing, signature and submission rows; the prescription is `REJECTED` or `SENT`; nothing is locked. **Declined, expired, cancelled or invalid signature:** one signing request in that state; no signature, submission or audit `SIGN` row; the prescription's status does not change.
- **UC-60:** one `PRINT` audit row; `printed_at` set on the first print; no data changes.

## States

Proposed section for `state-transitions.md` (entity "Đơn thuốc", column `lienthong_status`):

| ID | From | Event | Condition | To | UC |
| --- | --- | --- | --- | --- | --- |
| P01 | — | Doctor saves the first version | Conclusion saved; visit eligible | `PENDING` | UC-59 |
| P02 | `PENDING`, `REJECTED` | Doctor saves again | Editable | same | UC-59, UC-92 |
| P03 | `PENDING` | Doctor deletes | Never sent | — (row removed) | UC-59 AF-03 |
| P04 | `PENDING`, `REJECTED`, `SENT` | Doctor signs and sends; national system accepts | Digital signature verified (BR-59-10); checks pass | `ACCEPTED` | UC-90 |
| P05 | `PENDING`, `REJECTED`, `SENT` | National system rejects | Digital signature verified | `REJECTED` | UC-90 AF-01 |
| P06 | `PENDING`, `REJECTED`, `SENT` | No answer | Digital signature verified | `SENT` | UC-90 AF-02 |
| P07 | `ACCEPTED` | — | Locked; only print | — | UC-60 |

Confirming the conclusion (V19) gains the condition "no prescription, or the prescription is `ACCEPTED`".

Proposed new entity "Yêu cầu ký số" (`signing_requests.status`, BR-59-10 step 3):

| ID | From | Event | Condition | To | UC |
| --- | --- | --- | --- | --- | --- |
| K01 | — | Doctor clicks "Ký số" | Pre-check passed; no other pending request | `PENDING` | UC-90 step 3 |
| K02 | `PENDING` | CA returns the approved signature | Verification passed (BR-59-10 step 5) | `SIGNED` | UC-90 step 5 |
| K03 | `PENDING` | CA returns the approved signature | Verification failed | `FAILED` | UC-90 EF-16 |
| K04 | `PENDING` | Doctor declines with the CA | — | `DECLINED` | UC-90 AF-04 |
| K05 | `PENDING` | Approval time runs out | 5 minutes (**ASSUMPTION**) | `EXPIRED` | UC-90 AF-05 |
| K06 | `PENDING` | Doctor cancels, or saves the prescription (new version) | — | `CANCELLED` | UC-90 AF-03, UC-59 |

`SIGNED`, `FAILED`, `DECLINED`, `EXPIRED` and `CANCELLED` are final.

## API

All endpoints require role Doctor without a pending password change and run the `ConcludingVisitGuard` checks for the visit, except the print and submission reads from a closed record, which use UC-52's access.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/prescriptions/medicine-search?keyword=` | Active `THUONG` medicines with a registration number; keyword ≤ 100 characters, accent-insensitive (BR-110); at most 20 results ordered by name. `[{ id, code, registration, name, activeIngredient, strength, route, unit }]`. |
| `GET` | `/api/v1/prescriptions/visits/{visitId}` | The visit's prescription with lines, diagnoses to be sent, follow-up date, allergies, child data, missing link codes, `status` (`lienthong_status`), `signingCertificateRegistered` (EF-10) and `lastSubmission` (result, errors, and the signer, certificate and signing time of the signature sent with it). When the visit has no prescription yet the prescription fields are `null` and the rest is still returned, because the form needs it. |
| `PUT` | `/api/v1/prescriptions/visits/{visitId}` | Create (first call) or replace the prescription. Body: `{ "version": null on the first save, then the loaded version (required), "notes": "", "advice": "", "lines": [{ "medicineId", "dosePerTime", "timesPerDay", "days", "quantity", "usage", "note" }] }`; `notes` and `advice` are `luu_y` and `loi_dan`, named like the other request bodies of the API. Returns the prescription. |
| `DELETE` | `/api/v1/prescriptions/{prescriptionId}` | Remove a `PENDING` prescription (AF-03). `204`. |
| `POST` | `/api/v1/prescriptions/{prescriptionId}/commands/request-signature` | UC-90 step 3. Body `{ "version": n }`. Runs the checks and the pre-check, creates the signing request. `201` with `{ id, status: "PENDING", documentVersion, expiresAt }`; copying the diagnoses creates a new prescription version, `documentVersion`, which a new request after a decline or expiry names. |
| `GET` | `/api/v1/prescriptions/{prescriptionId}/signing-requests/current` | The pending request, if any (AF-06); `null` otherwise. |
| `POST` | `/api/v1/prescriptions/{prescriptionId}/signing-requests/{requestId}/commands/complete` | UC-90 steps 5–7, asked by the client every 3 s (**ASSUMPTION**). While the CA has no answer: `200` with `{ status: "PENDING", expiresAt }` and nothing changes. Approved: verifies, stores, sends; returns `{ status: "SIGNED", prescription }` with the new submission. Declined or expired: returns that status. Idempotent: calling it again on a final request returns the same result. |
| `POST` | `/api/v1/prescriptions/{prescriptionId}/signing-requests/{requestId}/commands/cancel` | AF-03. `204`; no effect on a final request. |
| `GET` | `/api/v1/prescriptions/{prescriptionId}/submissions` | Submission history, newest first, each with its signature and signature status (BR-59-16). |
| `GET` | `/api/v1/prescriptions/{prescriptionId}/print` | Data of the printed form (BR-59-13), `copy = true` from a closed record. |
| `POST` | `/api/v1/prescriptions/{prescriptionId}/commands/prepare-print` | Writes the `PRINT` audit row and sets `printed_at` on the first print; same pattern as UC-41 ticket printing. |

Registering a Doctor's signing certificate and maintaining the trusted CA list (BR-59-16) need Admin endpoints of their own use case (**TBD**). The simulated signing gateway exposes, outside `/api/v1` and only in the `dev` profile, a page where the "CA app" approves or declines a pending request (**ASSUMPTION**, stands in for the CA's mobile app).

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
| `SIGNING_CERTIFICATE_NOT_REGISTERED` (new) | 409 | MSG117 | Doctor without a registered signing certificate |
| `SIGNING_CERTIFICATE_INVALID` (new) | 409 | MSG118 | Pre-check failed: certificate expired, suspended or revoked; CA certificate not valid or not trusted |
| `SIGNING_REQUEST_PENDING` (new) | 409 | — | A signing request of the prescription is already pending; the client resumes it |
| `SIGNING_REQUEST_NOT_FOUND` (new) | 404 | MSG80 | Unknown request, or not of this prescription |
| `SIGNATURE_INVALID` (new) | 422 | MSG124 | Verification of the returned signature failed (BR-59-10 step 5) |
| `SIGNING_SERVICE_UNAVAILABLE` (new) | 502 | MSG123 | No answer from the signing gateway, CA status service or time-stamp service |
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
| MSG117 | **ASSUMPTION** (new): Tài khoản của bạn chưa được đăng ký chứng thư chữ ký số. Vui lòng liên hệ quản trị viên. |
| MSG118 | **ASSUMPTION** (new): Chứng thư chữ ký số không còn hiệu lực ({reason}). Không thể ký đơn thuốc. |
| MSG119 | Not used (was the signing-PIN message of the 12/11 version). |
| MSG120 | **ASSUMPTION** (new): Vui lòng xác nhận ký trên ứng dụng ký số của bạn. Yêu cầu hết hạn sau {time}. |
| MSG121 | **ASSUMPTION** (new): Bạn đã từ chối ký. Đơn thuốc chưa được gửi. |
| MSG122 | **ASSUMPTION** (new): Yêu cầu ký đã hết hạn. Vui lòng ký lại. |
| MSG123 | **ASSUMPTION** (new): Không thể kết nối với dịch vụ chữ ký số. Vui lòng thử lại. |
| MSG124 | **ASSUMPTION** (new): Chữ ký số không hợp lệ ({reason}). Đơn thuốc chưa được gửi. |

MSG106 → MSG124 continue after MSG105 (uc-55). I1-D45 reserved "MSG93–MSG96" for these messages, but Report 3 already uses MSG93 and MSG94 for rooms; the decision needs the numbers corrected.

## Schema changes

The first three rows and the withdrawal of the PIN columns are applied in V58 (approved 13/11/2026). The others remain proposals.

| Change | Reason |
| --- | --- |
| New table `staff_signing_certificates`: `staff_account_id` (FK, one active per account), `provider_code`, `provider_user_id`, `serial_number`, `issuer_dn`, `subject_dn`, `valid_from`, `valid_to`, `certificate_der BYTEA`, `status` (`ACTIVE` / `REMOVED`), audit columns; unique (`issuer_dn`, `serial_number`) | BR-59-10 step 5, BR-59-16 |
| New table `signing_requests`: `document_type`, `document_id`, `document_version`, `document_sha256`, `payload BYTEA` (the exact bytes to sign), `requested_by_account_id`, `certificate_id`, `provider_transaction_id`, `status` (K01–K06), `failure_reason`, `requested_at`, `expires_at`, `finished_at`; at most one `PENDING` per document (partial unique index) | BR-59-10 step 3, UC-90 AF-03 → AF-06 |
| `record_signatures`: add `signature_value BYTEA`, `signed_payload BYTEA`, `certificate_chain BYTEA`, `verification_evidence JSONB` (status responses at signing time), `signing_request_id` (FK); a CHECK that `DIGITAL_SIGNATURE` rows have the value, payload and chain | BR-59-10 step 7; Luật GDĐT Điều 10, 13. The existing `certificate_serial`, `certificate_issuer`, `signature_format`, `timestamp_token` columns are used as they are |
| Trusted CA list: deployment configuration first; table `trusted_ca_certificates` only when the Admin use case of BR-59-16 is decided | BR-59-16 (**TBD**) |
| `staff_accounts.citizen_id VARCHAR(12)`, unique, 12 digits (V60, applied 15/11/2026) | BR-59-16: the certificate is matched against the Doctor's personal identification number |
| The PIN columns of the 12/11 version (`staff_accounts.signing_pin_*`) are withdrawn | BR-59-10 revised 13/11 |
| `prescription_details.lieu_moi_lan`, `so_lan_ngay`, `so_ngay_dung`, `soluongthuocvattu` unchanged; `cachdung` stays 255 | BR-59-04 maps onto V55 |
| `medicine_catalog.nhieu_hoat_chat BOOLEAN` (optional) | BR-59-13 naming rule; **TBD** |
| No change to `prescriptions.lienthong_status` | BR-59-09 reuses `PENDING`, `SENT`, `ACCEPTED`, `REJECTED` |

## Deviations from the sources

| Source | Says | This document | Why |
| --- | --- | --- | --- |
| BR-47 | "Bỏ 'đã ký' (không làm ký điện tử)" | Digital signature by remote signing before sending (BR-59-10) | TT 26 Điều 10 requires "ký số"; Luật GDĐT Điều 22–23 and NĐ 23 Điều 15–17, 44 define it. BR-47 must be updated |
| This document, 12/11 version (PO 12/11) | Electronic confirmation with a signing PIN | Digital signature (revised 13/11) | A PIN is a confirmation under TT 13/2025 Điều 3 khoản 3, not the "ký số" TT 26 Điều 10 requires; **decision needed** |
| QĐ 808 VII.3 row 27 | `signature` "chữ ký số của người kê đơn", not marked required, format not given | Always sent, as base64 detached CMS | TT 26 Điều 10; format **ASSUMPTION** |
| Report 3 UC-59 | Drug name, unit, quantity, usage, days; no code; prescription saved to the record and printed | Fields of BR-107; code at creation; send before confirming the conclusion | BR-33, BR-52, BR-107 as revised on 09/11/2026 |
| Report 3 UC-59 AF-02 | Edit until the record is closed | Edit until accepted by the national system | BR-47 |
| Report 3 UC-60 | Print any saved prescription | Print only an accepted one; draft preview with watermark | usecases.xlsx UC-60 |
| V9 comment | Refuse to print or send without an ID or passport number | Optional | TT 26 Điều 6 khoản 2 "(nếu có)", Phụ lục I note 3; **decision needed** |
| QĐ 808 VII.3 row 10 | Guardian required for a child "hơn 72 tháng" | Under 72 months | TT 26 Điều 6 khoản 4; QĐ 808 wording is a typo |
| Report 3 UC-59 step 5 | Check the medicines against the patient's allergies and warn | No check; allergies shown only (BR-23) | PO 13/11: a textual match misses drug classes and gives false assurance; **decision needed** |
| usecases.xlsx UC-92 | Correct and resend only | Same, plus a pending prescription can be deleted before the first send | AF-03, **ASSUMPTION** |

## Decisions needed

1. **Digital signature instead of the signing PIN (revised 13/11, replaces PO 12/11).** Record that the e-prescription is signed with the Doctor's public digital signature by remote signing (BR-59-10) because TT 26 Điều 10 requires "ký số"; update BR-47 and the Actors sheet (signing gateway and CA as secondary actors); drop the PIN use case (old UC76); approve the schema changes above and the new dependency `org.bouncycastle:bcpkix-jdk18on` (CMS, certificate paths, OCSP, RFC 3161 time stamps; the JDK has no CMS API).
2. **Correct only rejected prescriptions (PO 12/11).** QĐ 425/QĐ-BYT Điều 9 khoản 3 điểm c says the same as TT 26 Điều 6 khoản 9: a sent prescription is never changed; a change is a new prescription that replaces it, and the old one stays on the national system but is not dispensed. Record that TT 26 Điều 6 khoản 9 (replacement prescription) is out of scope because the record is closed right after the conclusion, so a replacement would need a new visit; BR-33 stays "one per visit".
3. **ID number optional** on the prescription (TT 26) versus the V9 comment and the 2025 change log of UC-59 ("yêu cầu người bệnh có số định danh hoặc hộ chiếu").
4. **`hinh_thuc_dieu_tri`** fixed to outpatient (PO 12/11); the Ministry's code value is TBD.
5. **Prescription-type suffix** `c` versus `C` (BR-59-02).
6. **Message numbers** MSG106 → MSG124 and the I1-D45 correction.
7. **`permission-matrix.md`** Doctor/Prescription row: add send, resend, view result, delete pending; the medicine search for Doctors.
8. **No drug-allergy check (PO 13/11).** Record the removal of the Report 3 UC-59 step 5 check (BR-59-06, AF-01) and of MSG26 from this use case.
9. **Multi-ingredient flag** on the catalog for the printed name (BR-59-13), or print INN + trade name for every line.
10. **Signing certificates and trusted CAs (BR-59-16).** Add an Admin use case to register a Doctor's signing certificate and to maintain the trusted CA list (NĐ 23 Điều 17 khoản 2 điểm d), or keep both as deployment configuration for the project.
11. **Signed-data format (TT 15/2025/TT-BKHCN, [`reference/TT-15-2025-TT-BKHCN.md`](reference/TT-15-2025-TT-BKHCN.md)).** The technical rules of NĐ 23 Điều 17 khoản 4 are now known. The design meets them on hash (SHA-256), key size (RSA 2048), CMS (PKCS #7 v1.5), certificate check before signing, trust path to a trusted CA, OCSP status at the signing time and keeping the status evidence (Điều 5, 6; Phụ lục II). Two earlier gaps are closed (14/11/2026): the simulator signs with RSASSA-PSS (Phụ lục I mục 1.2), and the CMS is CAdES baseline with `signing-certificate-v2` and the time stamp inside (CAdES-T, mục 1.7). Still open: mục 3.10 recommends JWS (RFC 7515) for JSON — a detached JWS over the canonical payload is an alternative to CMS. Which one the national system expects in `signature` is still unknown: it needs the facility's account on that system (QĐ 425 Điều 8 khoản 1 điểm đ: software vendors get none). Phụ lục II mục 1.2 asks only for a warning when the status cannot be checked; the project refuses to sign instead (EF-15), which is stricter. Phụ lục III fixes the Cổng eSign connection: POST APIs at `https://esign.neac.gov.vn`, `sp_id` + `sp_password` or a token, TLS ≥ 1.2.
12. **Printed-copy mark (Luật GDĐT Điều 12 khoản 2).** The wording of the conversion mark on the printout (BR-59-13) until the Government's detailed rules (Điều 12 khoản 4) are added to `docs/reference/`.
13. **Who signs (QĐ 425/QĐ-BYT Điều 11) — proposed: form 2.** QĐ 425 allows two forms: (1) the digital signature of the facility's person in charge of professional matters on each prescription, with the prescriber's electronic signature, "theo khoản 2 Điều 13" TT 46/2018/TT-BYT; (2) the prescriber's own digital signature on each prescription. This document follows form 2 (BR-59-10). Reasons: TT 46/2018, the basis of form 1, was replaced by TT 13/2025 from 21/07/2025, and TT 13/2025 Điều 3 does not keep the "head of the facility confirms an electronic signature with a digital signature" mechanism; QĐ 425 is an administrative decision issued before TT 26/2025 and TT 13/2025, on TT 27/2021, and applies "tạm thời"; form 2 ties each prescription to its prescriber (NĐ 23/2025, non-repudiation) and keeps every private key with the CA, whereas form 1 needs the clinic to hold the facility's key. Form 1 stays an alternative for a real deployment if the Ministry still accepts it; it would reuse `record_signatures` (`ELECTRONIC_CONFIRMATION` for the prescriber) and add server-side signing with the facility's key.

## Signing simulator

The gateway, the CA, its status service, the national trust service and the time-stamp service are one simulator inside the backend, behind three interfaces that a real implementation replaces without changing UC-90: `RemoteSigningGateway` (NĐ 23 Điều 44), `CertificateStatusService` (Điều 15, 16) and `TimestampAuthority` (Điều 32). The national prescription system is a fourth simulator behind `NationalPrescriptionSystem` (BR-59-11, BR-59-12). The verification of BR-59-10 steps 1, 5 and 6 is not simulated: it uses BouncyCastle against the trusted CA list of `clinic.signing.trusted-ca-files`, so a real CA only needs its certificate in that list.

- **Keys.** A test hierarchy "Trung tâm Chứng thực điện tử quốc gia (giả lập)" → "CA ký số từ xa (giả lập)" → the Doctors' certificates and the time-stamp unit, RSA 2048 with RSASSA-PSS and SHA-256 (TT 15/2025/TT-BKHCN Phụ lục I mục 1.2). The keys are in a PKCS#12 file named by `SIGNING_SIMULATOR_KEYSTORE`, created on first use, outside the database and the repository (`.signing-simulator/`, git-ignored); the PIN hashes and revocations are in a JSON file beside it, and the CA certificate is written to `trusted-ca.pem` there. No clinic table holds a private key. Without `SIGNING_SIMULATOR_KEYSTORE_PASSWORD` the simulator answers as unavailable (EF-15); the `dev` profile sets a development password and trusts `trusted-ca.pem`.
- **Signing.** The CA receives only the SHA-256 of the payload and returns a detached CMS SignedData whose `messageDigest` is that hash, with the certificate chain. A real remote-signing CA may instead return a raw signature: the public integration document of VNPT SmartCA (v4.1, read 14/11/2026) signs a hash only and returns `signature_value`, with a transaction that expires after 299 s and a status API plus a webhook. A real `RemoteSigningGateway` then builds the CMS itself (signed attributes with the payload hash, the CA signs their hash, the raw signature is put into the SignerInfo); UC-90 and the verification do not change. Pending signing transactions are kept in memory; after a restart the clinic side sees them as expired.
- **CA app.** In the `dev` profile (`clinic.signing-simulator.console-enabled`), `http://localhost:8080/dev/ca-sim/` stands in for the CA's mobile app (a certificate is issued only for a Doctor whose account has a personal identification number): the Doctor signs in with the certificate serial and the simulator PIN (6 digits, **ASSUMPTION**), not with the clinic account, and approves or declines the pending requests. The same page issues a simulated certificate for a Doctor's user name and registers it as the account's signing certificate (standing in for the Admin use case of BR-59-16), and revokes one to demonstrate EF-11. The page and its endpoints are outside `/api/v1` and do not exist outside the `dev` profile.
- **Failure modes.** `SIGNING_SIMULATOR_FAILURE_MODE=UNAVAILABLE` (EF-15) or `BAD_SIGNATURE` (EF-16, a signature made with another key); `SIGNING_SIMULATOR_RESPONSE_DELAY`. For the national system, `NATIONAL_PRESCRIPTION_SIMULATOR_FAILURE_MODE=NO_RESPONSE` (AF-02), `NATIONAL_PRESCRIPTION_SIMULATOR_RESPONSE_DELAY` and `NATIONAL_PRESCRIPTION_SIMULATOR_PRESCRIBER_CODES` (known prescriber link codes; empty accepts all).

Assumptions made by the implementation, to be confirmed with the decisions above:

- The certificate subject names the Doctor by CN = full name and UID = `CCCD:` + the personal identification number of the account (BR-59-16); the prefix is an assumption about real certificates. The simulator also uses the number as the subscriber's user id at the CA, as VNPT SmartCA does.
- `chan_doan[]` items use the keys of QĐ 808 VII.3 row 12 (`ma_benh`, `ten_benh`, `ket_luan`), not those of the sample request (`ma_chan_doan`, `ten_chan_doan`); `ket_luan` is the conclusion's diagnosis text. `ma_so_bao_hiem_y_te` is the 10-digit social insurance number of an insurance visit; `can_nang` is the latest weight of the visit when there is one; `thong_tin_nguoi_giam_ho` is the relative's name, phone and address; `hinh_thuc_dieu_tri` = 1 (Decisions needed 4).
- In the canonical payload absent optional values are left out; `signature` is appended as the last key of the sent JSON, so the receiver can remove it and verify the rest byte for byte.
- The UC-90 dialog shows a summary of the prescription with a "BẢN NHÁP" watermark, not the printed form: `GET /print` serves only an accepted prescription (MSG112), so a draft of the printed form for UC-90 step 1 would need its own read; not done.
- UC-60 (implemented 15/11/2026, frontend dependency `qrcode.react` approved 15/11/2026): `GET /print` returns the form for the preview and records nothing; `POST /commands/prepare-print` writes the `PRINT` audit row (`prescriptionCode`, `reprint`) on every print, sets `printed_at` only on the first one and returns the form with the printer and the print time. The QR code holds the prescription code only. Every line is printed as "{hoatchat} ({tenthuocvattu}) {hamluong}", the strength left out when the trade name already contains it, and the ingredient alone when it equals the trade name (PO 15/11, Decisions needed 9). The mark is "Bản in từ đơn thuốc điện tử {code} lưu tại {facility}; người in {name}, {time}." (PO 15/11, Decisions needed 12, still **TBD** against the Government's rules). The insurance number printed is the one sent (`ma_so_bao_hiem_y_te`); the ID line shows the citizen ID number, otherwise the passport number; the contact phone is the patient's, otherwise the relative's (TT 26 Phụ lục I chú thích 8). Printing from a closed record ("Bản sao", UC-52) is not implemented: `copy` is always false and printing goes through `ConcludingVisitGuard`, so only the Doctor of the open visit prints.
- The signature status shown in UC-91 (the history and the latest result on the tab) re-runs BR-59-10 step 5 on the stored CAdES-T signature without calling any service: the signature against the stored payload, the certificate against the serial and issuer of the signature row, the embedded time stamp, the chain to the CA list trusted now and the validity at the time-stamp time; the revocation status is the one kept in the verification evidence at signing time (**ASSUMPTION**: a later revocation does not invalidate a signature made before it, NĐ 23 Điều 16).
- A send is shown with the latest signature row created no later than its `submitted_at`, because UC-90 step 5 writes the signature just before the submission; `prescription_submissions` has no link to the signature.
- UC-92: the national system's error list is free text, so the system cannot tell whether a rejection concerns the conclusion or the Doctor's link code; the AF-01 guidance is shown on every rejection (**ASSUMPTION** wording). "Gửi lại" stays disabled while the corrections are unsaved. "Sửa đơn" moves the focus to the first editable field.
- AF-03 refuses with `PRESCRIPTION_ALREADY_SENT` (MSG115) any prescription that is not `PENDING`, has a submission or has a signature row (**ASSUMPTION**: MSG115 also covers rejected and unanswered prescriptions; the button is shown only for `PENDING`). A pending signing request is cancelled first. The `DELETE` audit row has the removed code, visit number, notes, advice and lines as old values and null new values. After the deletion the tab shows "Đã xóa đơn thuốc." (**ASSUMPTION**: AF-03 names no message).

## Known limitations

- The simulator does not implement the national system's own validation; its rejection rules are configuration for demonstrations.
- Printing uses the browser print dialog (HW-02 TBD).
- There is no automatic drug-allergy check (BR-59-06, PO 13/11); the Doctor relies on the allergies shown in red.
- The signing gateway, the CA, its certificate-status service, the national trust service and the time-stamp service are simulators run by the project. The signatures are real CMS signatures that any standard tool can verify, but the certificates come from a test CA, not a licensed one (Luật GDĐT Điều 28 khoản 3), so they have no legal value; going live means replacing the simulated gateway with the real Cổng kết nối dịch vụ chứng thực chữ ký số công cộng (NĐ 23 Điều 44) and registering real certificates. The simulator holds the test private keys, which the real remote-signing CA would hold (NĐ 23 Điều 37 khoản 4); the clinic application never reads them.
- The facility's own rules for electronic signatures (TT 13/2025 Điều 6 khoản 3 điểm b, CV 365 mục V.2) are outside the software.
