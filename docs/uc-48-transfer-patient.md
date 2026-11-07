# UC-48 Transfer Patient to Another Room

**Status:** Decided 06/11/2026 (OQ-05, Q-33). This document describes the implemented behavior. It replaces Report 3 §9.6 where they differ; the differences are listed under [Deviations from Report 3](#deviations-from-report-3).

**Actor:** Doctor. **Secondary actors:** none.

**API:** `GET /api/v1/visits/{visitId}/transfer-rooms`, `POST /api/v1/visits/{visitId}/transfer`. The ticket reprint uses `GET /api/v1/visits/{visitId}/ticket` and `POST /api/v1/visits/{visitId}/commands/prepare-ticket-print` (UC-41).

**Code:** `VisitTransferServiceImpl`, `Visit.transferTo`, `frontend/src/components/clinical/TransferRoomPanel.jsx`, `transferRoom.js`.

## Description

During a valid shift, the Doctor sends a called-in patient to another examination room, before saving the clinical examination for the first time. Typical case: after meeting the patient, the Doctor sees that another specialty should examine them. The visit is reused: it returns to "Waiting for Examination" in the destination room with that room's next queue number. No new examination fee is charged. The Doctor can then reprint the examination ticket with the new room and number.

## Preconditions

- The Doctor is signed in (UC-01), has no pending password change, and has role Doctor with status Active.
- The Doctor is assigned to an active examination room and is on duty there now: a duty schedule exists for today, the current shift and that room (BR-05, BR-19, BR-55). Shifts are 08:00–11:30 and 13:00–17:30, Asia/Ho_Chi_Minh.
- The visit belongs to the Doctor's room, is "In Examination", and is not skipped. It was called in through UC-44, so the patient is present.
- The clinical examination of the visit has never been saved (see BR-48-02).

## Business rules

- **BR-48-01 (Q-33).** Only a visit in "In Examination" can be transferred. A visit still waiting is not transferred from the waiting list; the Doctor calls the patient in first, so the patient can be told in person. Reassigning waiting visits when a Doctor is absent is the Receptionist's V10, not this use case.
- **BR-48-02 (OQ-05).** The record counts as opened once the Doctor has saved the clinical examination at least once (UC-47 Save Draft or Save and Continue). Entering the examination screen creates an empty medical record and examination sheet; that alone does not block a transfer. The destination room's Doctor continues on the same record. A record that is not Open also blocks the transfer.
- **BR-48-03 (BR-96, ASSUMPTION).** A destination room is eligible when it differs from the current room, is an active examination room, and has an active Doctor on duty for the shift a registration made now would be for: the morning shift until 11:30, the afternoon shift until 17:30, none after that. This is the same rule as visit registration (`ExaminationRoomDuty`). Report 3 says "a Doctor scheduled that day"; the narrower rule keeps patients out of rooms whose Doctor has already left.
- **BR-48-04 (BR-97).** The transfer reason is required and has 5–500 characters after trimming. Notes are optional, at most 500 characters; blank notes are stored as empty.
- **BR-48-05 (BR-18, Q-24).** In the destination the visit is ordered by the common key: priority first, then original registration time. Transferred visits get no extra precedence. The Doctor's waiting list shows the note "Chuyển từ: {room}" for them.
- **BR-48-06 (BR-24).** The visit ID, registration code, registration time and receipts stay the same. No examination fee is added, even if the destination's fee differs.
- **BR-48-07.** The new queue number is the destination room's next number for the visit's queue date, from the same counter as registration, so it never repeats a number.
- **BR-48-08.** Two transfers of the same visit cannot both succeed: the visit row is locked during the transfer, and the second request finds the visit no longer transferable.

## Normal flow

1. The Doctor is on the Clinical Examination screen of a called-in patient (UC-47) and selects the "Chuyển phòng" tab. The tab is available only while the examination has not been saved (BR-48-02).
2. The system checks the Doctor, room, shift and visit (BR-48-01, BR-48-02) and lists eligible rooms (BR-48-03), ordered by room code, each with code, name, department, location and Doctor on duty.
3. The screen shows the patient header of the examination screen (name, patient code, date of birth, sex, visit type, insurance card number, visit code, queue number, current room, Doctor, status) and, beside the form, the visit code, current room, current queue number and reason for visit.
4. The Doctor selects the destination room. The system shows its department, location and Doctor on duty.
5. The Doctor enters the transfer reason (a character counter is shown) and optional notes.
6. The Doctor clicks "Xác nhận chuyển phòng". The button stays disabled until a room is selected and a reason is entered, and shows "Đang chuyển phòng…" while sending.
7. The client checks BR-48-04. The server checks again, in one transaction: the Doctor is on duty (EF-08), the reason is valid (EF-03), the visit is locked and still transferable (EF-05, EF-01), and the destination is still eligible (EF-04).
8. The system takes the destination's next queue number (BR-48-07) and updates the visit: destination room, new queue number, status "Waiting for Examination", no assigned Doctor, no examination start time.
9. The system stores the transfer history: visit, source and destination rooms, old and new queue numbers, reason, notes, transferring Doctor, time.
10. The system writes one `audit_logs` row: entity `visits`, action `UPDATE`, details `action = TRANSFER`, source and destination rooms, old and new queue numbers, new status.
11. The system shows MSG24 and a result panel with the new room, location and queue number, plus "In lại phiếu khám" and "Về danh sách chờ".
12. The visit leaves the source room's lists and appears in the destination's waiting list (BR-48-05). The use case ends.

## Alternative flows

- **AF-01 (step 4–6): Cancel.** "Hủy" closes the form and returns to the examination tab. If a room, reason or note was entered, MSG38 asks first. Nothing changes.
- **AF-02 (step 11): Reprint the ticket.** The Doctor clicks "In lại phiếu khám". The system loads the ticket of the same visit with the destination room and new queue number and shows it (UC-41). "In phiếu khám" records a `PRINT` audit row (`reprint = true` when printed before) and opens the browser print dialog. No new visit or fee is created. "Về danh sách chờ" ends the use case.
- **AF-03 (step 2): No eligible room.** The system explains that no other room can receive the patient now and keeps "Xác nhận chuyển phòng" disabled. The patient stays in the current room.
- **AF-04 (step 5): No notes.** Blank notes are accepted.

## Exception flows

- **EF-01 (step 2 or 7): Examination already saved.** MSG56. The form is disabled; room, queue number and status do not change.
- **EF-02 (step 6): Missing reason.** MSG31 under the field (client check).
- **EF-03 (step 6–7): Reason outside 5–500 characters.** MSG100 under the field.
- **EF-04 (step 7): Destination no longer eligible.** MSG53 under the room field; the room list reloads and the Doctor picks again.
- **EF-05 (step 2 or 7): Visit no longer transferable.** Already transferred, skipped, moved on, not in this room, or not found: MSG99, the form is disabled.
- **EF-06 (step 7–10): Concurrent transfer.** One request succeeds; the other gets EF-05. A failure rolls back every change of the transaction.
- **EF-07 (AF-02): Reprint fails.** MSG49; the transfer stays; the Doctor can retry.
- **EF-08 (step 2 or 7): Doctor without an active room or outside the shift.** MSG48; the form is disabled.
- **EF-09: Session expired.** MSG12.
- **EF-10: System or network error.** MSG80. Nothing is reported as successful before the server confirms it.

## Postconditions

- **Success:** the visit waits in the destination room with a new queue number, no assigned Doctor, the same visit ID and receipts. One transfer history row and one audit row exist. The ticket can be reprinted.
- **Failure:** room, queue number, status and history are unchanged.

## API

`GET /api/v1/visits/{visitId}/transfer-rooms` returns `[{ roomId, roomCode, roomName, location, departmentName, doctorName }]`.

`POST /api/v1/visits/{visitId}/transfer` with body `{ "toRoomId": "uuid", "reason": "5–500 characters", "notes": "optional, ≤ 500" }` returns `{ visitId, toRoomId, toRoomCode, toRoomName, toRoomLocation, newQueueNumber, transferredAt }`.

Both require role Doctor without a pending password change.

| Error code | HTTP | Message | When |
| --- | --- | --- | --- |
| `DOCTOR_OFF_DUTY` | 403 | MSG48 | Not on duty in the assigned room now |
| `ROOM_ASSIGNMENT_REQUIRED` | 409 | MSG48 | No active examination room assigned |
| `FORBIDDEN` | 403 | MSG13 | Not an active Doctor, or a pending password change |
| `VISIT_NOT_FOUND` | 404 | MSG99 | Unknown visit |
| `VISIT_NOT_TRANSFERABLE` | 409 | MSG99 | Not In Examination, skipped, or not in the Doctor's room |
| `MEDICAL_RECORD_ALREADY_OPENED` | 409 | MSG56 | Examination saved, or record not Open |
| `TRANSFER_ROOM_NOT_ELIGIBLE` | 409 | MSG53 | Destination is the same room, inactive, not an examination room, or has no Doctor on duty |
| `TRANSFER_REASON_INVALID` | 422 | MSG100 | Trimmed reason shorter than 5 or longer than 500 |
| `REQUEST_VALIDATION_FAILED` | 400 | MSG100 | Missing room, blank reason, or notes over 500 characters |

## Messages

| ID | Text |
| --- | --- |
| MSG12 | Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại. |
| MSG24 | Đã chuyển bệnh nhân đến {room_name}. Số thứ tự mới: {queue_number}. |
| MSG31 | Vui lòng nhập lý do. |
| MSG38 | Bạn có thay đổi chưa lưu. Nếu rời khỏi trang, các thay đổi này sẽ bị mất. |
| MSG48 | Bạn không có ca làm việc vào lúc này. Bạn chỉ xem được danh sách, không gọi được bệnh nhân. |
| MSG49 | Không thể in. Vui lòng kiểm tra máy in và thử lại. |
| MSG53 | {room_name} đã ngừng hoạt động hoặc không có bác sĩ làm việc trong ngày nên không thể tiếp nhận bệnh nhân. |
| MSG56 | Không thể chuyển bệnh nhân vì hồ sơ bệnh án của lượt khám này đã được mở. |
| MSG80 | Không thể kết nối hoặc hệ thống đang gặp sự cố. Vui lòng tải lại và thử lại. |
| MSG99 | **ASSUMPTION:** Lượt khám đã thay đổi. Vui lòng tải lại. |
| MSG100 | **ASSUMPTION:** Lý do chuyển phải từ 5 đến 500 ký tự. |

MSG99 and MSG100 are cited by Report 3 but missing from its message catalogue.

## Deviations from Report 3

| Report 3 §9.6 | Implemented | Decision |
| --- | --- | --- |
| Transfer a waiting patient from the waiting list, or a called patient | Only a called-in patient, from the examination screen | Q-33 |
| Blocked once "the medical record has been opened" | Blocked once the examination has been saved | OQ-05 |
| Transferred patients go after priority patients and before ordinary patients | Common key: priority, then original registration time | Q-24, Q-33 |
| Destination has "a Doctor scheduled that day" | A Doctor on duty for the current or next shift today | BR-48-03, ASSUMPTION |
| Notes without a length limit | Notes at most 500 characters | ASSUMPTION |

## Known limitations

- A visit of another room and a visit that has just been transferred away both return `VISIT_NOT_TRANSFERABLE` (MSG99); the first is not reported as a permission error.
- A blank reason that bypasses the client check returns `REQUEST_VALIDATION_FAILED`, which the client shows as MSG100 instead of MSG31.
- Ticket printing uses the browser print dialog; printer integration is TBD (HW-01).
