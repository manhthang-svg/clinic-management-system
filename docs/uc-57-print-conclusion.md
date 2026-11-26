# UC-57 Print Conclusion, and printing after closure (UC-57, UC-60, UC-62)

**Status:** Decided by the product owner on 23/11/2026 and implemented on 23/11/2026 (backend, frontend; no migration). The decision still needs the team's decision record.

**Sources:** Report 3 §11.4 UC-57, §11.7 UC-60, §11.9 UC-62; `Business_Rules_v7.xlsx` (BR-32, BR-35, BR-42, MSG49, MSG61); [`uc-58-close-medical-record.md`](uc-58-close-medical-record.md); [`uc-59-e-prescription.md`](uc-59-e-prescription.md) (BR-59-13); [`uc-61-follow-up-appointment.md`](uc-61-follow-up-appointment.md) (BR-61-06); [`uc-51-52-medical-record-view.md`](uc-51-52-medical-record-view.md) (BR-52-07).

## Decision (PO 23/11/2026)

The conclusion (UC-57), the prescription (UC-60) and the follow-up slip (UC-62) are printed **only once the medical record is closed** (UC-58, status `LOCKED` or `ARCHIVED`).

| # | Question | Decision |
| --- | --- | --- |
| P1 | When | After "Xác nhận kết luận" closes the record. Before that, the "Đơn thuốc" and "Hẹn tái khám" tabs have no print button. A print request on an open record is refused with MSG61. |
| P2 | Where | Right after closing, a dialog offers the three slips and "Về danh sách chờ". Later, from the medical record detail (UC-52), which is AF-01 of the three use cases. |
| P3 | Who | Only the Doctor of the visit (`visits.doctor_account_id`), with an active Doctor account. The room and shift checks of the open visit do not apply. Other Doctors can still view the record (BR-35) but do not see the print buttons and are refused (`ACCESS_DENIED`). |
| P4 | "Bản sao" | Prescription and follow-up slip: the first print is the original, every later print carries "BẢN SAO", and the preview shows the mark that the next print will carry. Conclusion slip: no "BẢN SAO" mark, every print is the same (PO 26/11/2026, BR-57-03). |
| P5 | Follow-up slip status | Still printed only while the appointment is `SCHEDULED` (MSG128). |

Report 3 states the "after closure" precondition for UC-57 only. For UC-60 and UC-62, it is a project decision (see [Deviations](#deviations-from-the-sources)).

## UC-57 rules

- **BR-57-01 Content (Report 3 UC-57 step 2).** The slip shows:
  - the facility name, address and phone (UC-77);
  - the patient: code, name, date of birth, gender and address;
  - the visit code, the visit date and the examination room;
  - the main diagnosis with its ICD-10 code, and the Doctor's own wording (CV 365 III.13) when there is one;
  - the secondary diagnoses with their codes;
  - the treatment plan, the treatment outcome (1 Khỏi, 2 Đỡ, 3 Không thay đổi, 4 Nặng hơn, as on form 15/BV1) and the advice;
  - the Doctor's academic title and name, the print date, and space for the signature.
- **BR-57-02 Form (ASSUMPTION).** No legal form is named for the slip. Its title is "Phiếu kết luận khám bệnh" and its layout follows the follow-up slip (BR-61-06). It uses no abbreviations (TT 32/2023 Điều 52 khoản 2 điểm c).
- **BR-57-03 No copy mark (PO 26/11/2026).** The conclusion slip carries no "BẢN SAO" mark; every print is the same slip.
- **BR-57-04 No audit (PO 26/11/2026).** Printing the conclusion writes no audit row and changes no data. The preview records nothing either. (Until 26/11/2026 each print wrote a `PRINT` row on `conclusions`, used to mark later prints "BẢN SAO"; those old rows are kept but no longer read.)

## Flow

1. The Doctor clicks "In kết luận" in the dialog shown after closing, or in UC-52 (AF-01).
2. The system checks P1 and P3 and shows the preview (BR-57-01).
3. The Doctor clicks "In". The system adds the print time and opens the browser print dialog (printer integration TBD). Nothing is recorded (BR-57-04).

- **AF-02 (step 3).** The Doctor closes the preview. Nothing is printed.
- **EF-01 (step 2).** The record is not closed: MSG61 "Vui lòng xác nhận kết luận trước khi in."
- **EF-02 (step 3).** The printer fails: MSG49.

UC-60 and UC-62 follow the same steps, with their own forms (BR-59-13, BR-61-06) and their existing checks: only an accepted prescription (MSG135), only a scheduled appointment (MSG128).

## API

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/api/v1/conclusions/visits/{visitId}/print` | The slip of BR-57-01 for the preview; records nothing. |
| `POST` | `/api/v1/conclusions/visits/{visitId}/commands/prepare-print` | Returns the slip with the print time and the printer; records nothing (BR-57-04). |

The prescription and follow-up print routes keep their paths. They now run the closed-record check of P1 and P3 instead of the open-visit check, and return `copy`. `POST /conclusions/visits/{visitId}/commands/confirm` (UC-58) also returns `prescriptionId` (the accepted prescription, or null) and `followUpAppointmentId` (the scheduled appointment, or null), which the dialog of P2 uses.

| Code | HTTP | Message | When |
| --- | --- | --- | --- |
| `PRINT_RECORD_NOT_CLOSED` (new) | 409 | MSG61 | Any of the three prints on an open record |
| `ACCESS_DENIED` (exists) | 403 | MSG13 | Not the Doctor of the visit, or not an active Doctor |

## Implementation

- **Backend.**
  - `ClosedRecordPrintGuard` applies P1 and P3 and returns the same context as `ConcludingVisitGuard`.
  - `ConclusionPrintService` and its implementation, with the endpoints on `ConclusionController`.
  - `PrescriptionPrintServiceImpl` and the print methods of `FollowUpScheduleServiceImpl` use the new guard; `copy` is `printed_at != null`.
  - `MedicalRecordClosureServiceImpl.confirm` returns the IDs of P2.
- **Frontend.**
  - `ConclusionSlip` and `PrintConclusionButton` are new.
  - `ClosedRecordPrintActions` and `ClosedRecordPrintDialog` hold the three buttons. The dialog opens on `ClinicalExaminationPage` after closing. The actions are shown in `MedicalRecordDetailPage` for a closed record when the signed-in Doctor is the Doctor of the visit.
  - The print buttons were removed from `PrescriptionPanel` and `FollowUpPanel`.
  - `FollowUpSlip` shows "BẢN SAO".

## Deviations from the sources

| Source | Says | Implemented | Why |
| --- | --- | --- | --- |
| Report 3 UC-60, UC-62 preconditions | Print once the prescription is sent or the appointment is saved | Print once the record is closed | PO 23/11/2026 (P1) |
| BR-59-15 | Reprinting from a closed record follows UC-52's access rules (every Doctor, BR-35) | Only the Doctor of the visit | PO 23/11/2026 (P3) |
| BR-59-13, UC-60 step 2 | A print from a closed record carries "Bản sao" | The first print is the original, later prints carry "Bản sao" | Every print now comes from a closed record (P4) |
