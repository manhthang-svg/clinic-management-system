# UC-55 View Paraclinical Results

**Status:** Specified 11/11/2026, not implemented. Based on Report 3 §11.2 UC-55 (`docs/source/Report-3_Software_Requirement_Specification.md`). Items marked **ASSUMPTION** or **TBD** are not in Report 3 or `Business_Rules_v7.xlsx` and need team review.

**Actor:** Doctor. **Secondary actors:** none.

**API:** `GET /api/v1/paraclinical-results/{orderDetailId}`, opened to the Doctor role (see [API](#api)).

**Code (planned):** `ParaclinicalResultController.get`, `ParaclinicalResultServiceImpl.get` (Doctor branch); `frontend/src/components/paraclinical/ParaclinicalOrderList.jsx` ("Xem chi tiết"), `frontend/src/components/paraclinicalResult/ResultPrintView.jsx` (reused for the preview).

## Description

From the paraclinical order list of a visit (UC-54), the Doctor opens the submitted result of one ordered service. The system shows the result slip on screen in preview mode, with the same template and content as the slip the Technician prints (UC-68), so the Doctor can use it to conclude the visit (UC-56). Nothing is changed.

## Preconditions

- The Doctor is signed in (UC-01), has no pending password change, and has role Doctor with status Active (BR-02).
- The Doctor is assigned to an examination room (BR-05).
- The Doctor is viewing the paraclinical order list of a visit (UC-54).
- The ordered service has status Completed and its result has been submitted (BR-29).

## Business rules

- **BR-02, BR-05.** Only an active Doctor assigned to a room uses this function.
- **BR-29.** Only the submitted result is shown. A submitted result is locked; the preview is read-only and has no edit action.
- **BR-120.** Clinical content is visible to the Doctor and to the Technician who performed the service only. Receptionists and Cashiers get MSG13.
- **BR-55-01 (ASSUMPTION, same scope as UC-50).** "Current visit" (permission matrix) means: the Doctor created the order, or the Doctor is the Doctor of the visit. Viewing the results of other visits goes through the medical record (UC-52, BR-35), not through this use case.
- **BR-55-02 (ASSUMPTION).** A draft result is never shown to the Doctor. A direct request for a service without a submitted result is rejected (EF-03).
- **BR-55-03 (ASSUMPTION).** The preview is the UC-68 slip without print marks: no "Bản sao" mark, and the Technician's print count is not changed. UC-55 has no print action; the Doctor prints the conclusion in UC-57.
- **BR-55-04 (ASSUMPTION, follows the `VIEW_RECORD` pattern of UC-47 and UC-36).** Opening a result writes one `VIEW_RESULT` audit row (entity `paraclinical_order_details`). It does not change business data, so it does not contradict the postcondition "No data is changed".

## Slip content

The same as the UC-68 slip (form PF-03), built from the submitted result:

| Part | Content |
| --- | --- |
| Header | Clinic name, address and phone (UC-77); slip title by result form (`printForm`: lab, X-ray, CT, MRI, ultrasound, functional) |
| Patient | Patient code, full name, age (BR-123), sex, address, visit type and insurance card number |
| Order | Registration code, order code, service, ordering Doctor and room, department, priority, initial diagnosis |
| Result, lab | Each parameter in display order (BR-132): name, value, unit, reference range in force (BR-133) and abnormal flag (BR-103) |
| Result, imaging and functional | Technique, description, conclusion, recommendation, image link and images (BR-102) |
| Footer | Technician (and reading doctor, if any), instrument, performed and submitted time |

## Normal flow

1. The Doctor clicks "Xem chi tiết" on an order with a result in the order list (UC-54 AF-04).
2. The client requests the result. The server checks the Doctor, the visit scope and that the result is submitted (EF-01 to EF-03).
3. The system displays the result slip on screen in preview mode, with the content above.
4. The Doctor reads the result.
5. The Doctor clicks "Đóng".
6. The system closes the preview and returns to the order list (UC-54). The use case ends.

## Alternative flows

None (Report 3).

## Exception flows

- **EF-01 (step 1): The user is not a Doctor, or the service is not of the Doctor's visit.** MSG13. The use case ends.
- **EF-02 (step 2): The ordered service does not exist.** MSG39 (**ASSUMPTION**: Report 3 lists no message for this case). The use case ends.
- **EF-03 (step 2): The result is not submitted yet.** Only on a direct request or a stale list. The system shows **MSG105 (ASSUMPTION, new)** and reloads the order list. The use case ends.
- **EF-04 (any step): Session expired or system error.** MSG12 (session expired, opens "Đăng nhập") or MSG80 (system error); nothing is changed. The use case ends.

## Postconditions

The result slip is shown. No business data is changed. Submitted results cannot be edited (BR-29). One `VIEW_RESULT` audit row is written (BR-55-04).

## API

`GET /api/v1/paraclinical-results/{orderDetailId}` today allows only `TECHNICIAN`. It becomes `hasAnyRole('DOCTOR', 'TECHNICIAN')`; the service branches on the role:

- **Technician:** unchanged (UC-67, UC-68).
- **Doctor:** checks BR-55-01 (`order.doctorAccountId` or `visit.doctorAccountId` equals the caller), then BR-55-02. Returns the same `ParaclinicalResultResponse`, so the client renders it with `ResultPrintView`, with `isPerformer = false` and an empty `instruments` list (the instrument picker is for editing only).

No new endpoint and **no schema change** are needed. **TBD:** imaging images are returned as an empty `files` list today (`ParaclinicalResultServiceImpl`, imaging branch); until image download for results is built, the preview shows only the image link.

| Error code | HTTP | Message | When |
| --- | --- | --- | --- |
| `FORBIDDEN` | 403 | MSG13 | Not an active Doctor, pending password change, or not the Doctor's visit |
| `NOT_FOUND` | 404 | MSG39 | Unknown ordered service |
| `RESULT_NOT_SUBMITTED` | 409 | MSG105 | No submitted result |

## Frontend

- In `ParaclinicalOrderList.jsx`, "Xem chi tiết" is shown only on services with status `COMPLETED` (UC-54 AF-04).
- The preview opens in a dialog with "Đóng"; it has no "In" and no edit button.
- Error mapping by `errorResponse.code`, Vietnamese messages from the table below; raw backend messages are never shown.

## Messages

| ID | Text |
| --- | --- |
| MSG12 | Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại. |
| MSG13 | Bạn không có quyền thực hiện thao tác này. |
| MSG39 | Không tìm thấy {record_type}. Here: "Không tìm thấy dịch vụ cận lâm sàng." |
| MSG80 | Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên. |
| MSG105 | **ASSUMPTION** (new): Dịch vụ này chưa có kết quả. |

## Deviations from Report 3

| Source | This specification | Decision |
| --- | --- | --- |
| UC-54 AF-04: "Xem chi tiết" on status "Đã có kết quả" | `state-transitions.md` §3.1 names the status "Hoàn thành" (`COMPLETED`); the list already shows "Hoàn thành" | **TBD**: team picks one label |
| UC-55 lists only MSG12, MSG13, MSG80 | Adds MSG39 and a new MSG105 for direct requests | **ASSUMPTION** |
| UC-55 "No data is changed" | Writes a `VIEW_RESULT` audit row | **ASSUMPTION** (BR-55-04) |

## Open items

- **TBD:** label "Đã có kết quả" or "Hoàn thành" (see deviations).
- **TBD:** whether a Doctor viewing a result is audited (BR-55-04); if the team says no, drop the audit row.
- **TBD:** image download for imaging results, shared with UC-52 step 4 ("checks the Doctor's access before every download").
- **TBD:** MSG105 is the next free number after MSG104 (Report 3 uses MSG01 to MSG104); add it to the Report 3 message list.
