# UC-50 Print Paraclinical Order

**Status:** Implemented 10/11/2026 (I1-D47); the sheet layout rules marked **ASSUMPTION** still need team review. Report 3 §UC-50 was not available when this was written (the SRS file is not in `docs/source/`); compare with it and list any differences under [Deviations](#deviations-from-report-3-and-br-28).

**Actor:** Doctor. **Secondary actors:** none.

**API:** `POST /api/v1/paraclinical-orders/{orderId}/commands/prepare-print`. Changes to UC-49: `GET /api/v1/paraclinical-orders/services` and `POST /api/v1/paraclinical-orders` (see [Changes to UC-49](#changes-to-uc-49-order-paraclinical-services)).

**Code:** `ParaclinicalOrderPrintServiceImpl`, `ParaclinicalOrderServiceImpl` (room choice), `PaymentCompletionService` (release to the chosen room), `DepartmentServiceImpl.toggleRoomStatus` (BR-50-03); `frontend/src/components/paraclinical/ParaclinicalOrderSlip.jsx`, `ParaclinicalOrderPanel.jsx`, `ParaclinicalOrderList.jsx`.

## Description

After confirming a paraclinical order (UC-49), the Doctor prints the order slip that the patient carries to the paraclinical rooms. The slip lists the ordered services and, for each, the room and its location, so the patient knows where to go. The Doctor chooses the performing room of each service when ordering (I1-D47); the system suggests the least busy room. One sheet is printed per performing room, following the request part of the TT 32/2023/TT-BYT forms 08/BV2 to 26/BV2.

## Preconditions

- The Doctor is signed in (UC-01), has no pending password change, and has role Doctor with status Active.
- The order exists, belongs to a visit of the Doctor (the Doctor who created it or the Doctor assigned to the visit), and is not Cancelled.
- Every service of the order has a chosen room (I1-D47). Orders created before I1-D47 may not; see AF-03.

## Business rules

- **BR-50-01 (I1-D47, replaces the automatic choice of BR-28).** The Doctor chooses the performing room of each service in UC-49. The choice list holds the Active paraclinical rooms of the service's group, each with name, location and number of waiting services; the room with the fewest waiting services is preselected (ties: lower room code). A group with one Active room needs no choice. The room is stored on the order line when the order is confirmed.
- **BR-50-02 (I1-D47, changed by the BA on 12/11/2026).** Release without payment (an insurance visit or a fee-exempt receipt, BR-26) sends each service to its chosen room. Release at payment of a self-pay visit (UC-71) picks the room again: among the Active rooms of the service group, the rooms with staff on duty in the current shift; among them the one with the fewest waiting services; ties go to the lower room code (**ASSUMPTION:** when no room of the group is staffed now, every Active room is a candidate). The room on the printed slip may therefore differ from the room the patient is sent to; the Cashier's confirmation lists the actual rooms. The queue number is taken at the on-site check-in, does not exist before release and is **not printed**.
- **BR-50-03 (I1-D47, extends I1-D32).** A paraclinical room cannot be deactivated while any order line has it as chosen room and is Pending Payment, Waiting (skipped included) or In Progress. MSG55.
- **BR-50-04 (ASSUMPTION).** One sheet per performing room. All sheets share the order number and show "Tờ {i}/{n}". Sheets are ordered by room code; services in a sheet keep their order line order.
- **BR-50-05 (ASSUMPTION).** Cancelled and abandoned services are not printed. An order with no printable service (all cancelled) cannot be printed.
- **BR-50-06 (ASSUMPTION).** Amounts are not printed. The TT 32 forms have no money fields; the receipt (UC-72) shows them.
- **BR-50-07 (BR-120).** Only Doctors print the slip; it contains the initial diagnosis, which Receptionists and Cashiers cannot see.
- **BR-50-08 (UC-41 pattern).** Every print attempt writes a `PRINT` audit row. A print after the first one is a reprint and the sheets show "Bản in lại". Reprinting is always allowed while the order is not Cancelled.

## Slip content

Per sheet. Field sources in brackets; CV 365 references in the last column.

| Part | Content | CV 365 |
| --- | --- | --- |
| Header | Facility name, address, phone (UC-77); title "PHIẾU CHỈ ĐỊNH CẬN LÂM SÀNG"; "Số: {sophieu}"; "Tờ {i}/{n}"; "Bản in lại" on a reprint | V.2.1 |
| Patient | Full name, year of birth and age, sex, address, patient code, visit code; visit type (BHYT / Viện phí) and insurance card number for an insurance visit | V.2.5 |
| Ordering place | Department and room of the ordering Doctor | V.2.10, V.2.13, V.2.14 |
| Clinical | Initial diagnosis (`chandoansobo`); priority "Thường" / "Cấp cứu" (`mucdochidinh`) | V.2.4, V.2.11 |
| Services | Table: No., order line code (`machidinh`), service name, quantity | V.2.6.1, V.2.6.9, V.2.6.10 |
| Where to go | "Nơi thực hiện: {room name} – {location}" | V.2.6.7, V.2.6.8 |
| Footer | Order time (dd/MM/yyyy HH:mm, Asia/Ho_Chi_Minh); "Bác sĩ chỉ định" with space for a hand signature and the Doctor's full name | V.2.2, V.2.6.2 |

Not printed: queue number (BR-50-02), amounts (BR-50-06), cancelled services (BR-50-05), bed (V.2.9: outpatient clinic), blood delivery time (V.2.7).

## Normal flow

1. The Doctor confirms an order in UC-49, or opens an existing order in the order list of the visit, and clicks "In phiếu chỉ định".
2. The client sends `prepare-print`. The server checks the Doctor and the order (EF-01 to EF-04).
3. The server groups the printable services by chosen room (BR-50-04, BR-50-05), builds the sheets with the content above, works out whether this is a reprint from earlier `PRINT` rows (BR-50-08), and writes the `PRINT` audit row.
4. The client shows the sheets and opens the browser print dialog.
5. The patient takes the sheets to the rooms. The use case ends.

## Alternative flows

- **AF-01 (step 1): Print right after ordering.** After MSG63 the order result shows "In phiếu chỉ định"; clicking it starts at step 2.
- **AF-02 (step 3): Reprint.** Same flow; the sheets show "Bản in lại" and reflect the current order (cancelled services left out).
- **AF-03 (step 3): A service has no chosen room.** Only for orders made before I1-D47 and not yet released. The sheet shows the service group name and "Phòng sẽ được chỉ định sau khi thanh toán" instead of a room. Once released, the routed room is printed.

## Exception flows

- **EF-01: Not an active Doctor, pending password change, or the order is not of the Doctor's visit.** MSG13.
- **EF-02: Order not found.** MSG39.
- **EF-03: Order Cancelled, or no printable service.** MSG97.
- **EF-04: Session expired.** MSG12.
- **EF-05: Printing fails in the browser.** MSG49; the audit row stays; the Doctor can retry (it counts as a reprint).
- **EF-06: System or network error.** MSG80.

## Postconditions

- **Success:** one `PRINT` audit row (entity `paraclinical_orders`, details: `sophieu`, sheet count, `reprint`). No order, payment or queue data changes.
- **Failure:** nothing changes.

## API

`POST /api/v1/paraclinical-orders/{orderId}/commands/prepare-print`, no body, role Doctor. Returns:

```json
{
  "orderId": "uuid",
  "sophieu": "CD0000000123",
  "reprint": false,
  "printedAt": "2026-11-10T02:15:00Z",
  "facility": { "tenCskcb": "...", "diachi": "...", "sodienthoai": "..." },
  "patient": { "fullName": "...", "birthYear": 1980, "age": 46, "gender": "...", "address": "...",
               "patientCode": "BN00000001", "visitCode": "DK0000000001",
               "visitType": "BHYT", "insuranceCardNumber": "..." },
  "orderingDepartment": "...", "orderingRoom": "...",
  "chandoansobo": "...", "mucdochidinh": "Thường",
  "orderedAt": "2026-11-10T02:10:00Z",
  "doctorName": "...",
  "sheets": [
    { "index": 1, "total": 2,
      "room": { "roomCode": "...", "roomName": "...", "location": "..." },
      "serviceGroupName": "...",
      "services": [ { "machidinh": "CD0000000123-01", "tenchidinh": "...", "soluong": 1 } ] }
  ]
}
```

`room` is null only in AF-03.

| Error code | HTTP | Message | When |
| --- | --- | --- | --- |
| `FORBIDDEN` | 403 | MSG13 | Not an active Doctor, pending password change, or not the Doctor's visit |
| `PARACLINICAL_ORDER_NOT_FOUND` | 404 | MSG39 | Unknown order |
| `PARACLINICAL_ORDER_NOT_PRINTABLE` | 409 | MSG97 | Order Cancelled or without printable services |

## Changes to UC-49 Order Paraclinical Services

- `GET /api/v1/paraclinical-orders/services` adds to each service `rooms: [{ roomId, roomCode, roomName, location, waitingCount }]` (Active rooms of its group, by room code) and `suggestedRoomId` (BR-50-01). A service whose group has no Active room (BR-117 should prevent it) is still offered with an empty list; its line gets no room and release reports it as unrouted (UC-71 AF-02).
- `POST /api/v1/paraclinical-orders` replaces `serviceIds` with `items: [{ serviceId, roomId }]`. `roomId` may be empty; the server then uses the least busy Active room (**ASSUMPTION**). The server checks that each room is Active, is a paraclinical room and belongs to the service's group; otherwise `PARACLINICAL_ROOM_NOT_ELIGIBLE` (409, MSG40) and the client reloads the room lists. The same service twice stays MSG89 (BR-101).
- The order line stores `room_id` at creation; `queue_number` stays empty until release. **No schema change:** both columns exist.
- `PaymentCompletionService` routes a paid line to the staffed, least busy room of its group (BR-50-02 as changed on 12/11/2026, using `ParaclinicalRoomDuty`); a line released without payment goes to its stored room. If that room is somehow no longer Active (BR-50-03 should prevent it), it falls back to the least busy room and writes a warning log (**ASSUMPTION**, defensive only).
- The order screen shows each selected service with its room choice; the default is preselected.

## Changes to room deactivation

`PATCH /api/v1/departments/rooms/{id}/status` (UC-19) rejects deactivating a paraclinical room with `ROOM_HAS_PENDING_WORK` (409, MSG55) while an order line that chose it is Pending Payment, Waiting or In Progress (BR-50-03). This also covers the paraclinical part of I1-D32. **TBD:** the examination-room part of I1-D32 (visits waiting, skipped, in examination or waiting for conclusion) is still not checked by this endpoint.

## Messages

| ID | Text |
| --- | --- |
| MSG12 | Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại. |
| MSG13 | Bạn không có quyền thực hiện thao tác này. |
| MSG39 | Không tìm thấy phiếu chỉ định. |
| MSG40 | {room_name} đã chọn hiện không còn khả dụng. Vui lòng chọn mục khác. |
| MSG49 | Không thể in. Vui lòng kiểm tra máy in và thử lại. |
| MSG55 | {room_name} vẫn còn bệnh nhân hoặc dịch vụ đang chờ hoặc đang thực hiện nên hiện không thể thay đổi. |
| MSG63 | Đã tạo chỉ định cận lâm sàng {order_number}. |
| MSG80 | Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên. |
| MSG89 | {item} đã được chọn trong đơn này. |
| MSG97 | **ASSUMPTION** (new): Phiếu chỉ định đã bị hủy hoặc không còn dịch vụ nào nên không thể in. |

## Legal and standard references

- **CV 365/TTYQG-GPQLCL (06/06/2025), Phụ lục, group V `PhieuChiDinh`:** data fields of the order (V.1 to V.2.15). Section 1.1.e: the software prints according to the Ministry of Health record forms.
- **TT 32/2023/TT-BYT, Phụ lục XXIX:** no stand-alone order form; the request part of forms 08/BV2 to 26/BV2 (patient, department, diagnosis, request, date, ordering Doctor's signature) is the model for the sheet.
- **CV 65/KCB-QLCL&CĐT (12/01/2024):** draft order forms CD-01 to CD-05, for trial only; not followed until issued.
- **TT 13/2025/TT-BYT:** electronic signing of medical records; outpatient facilities by 31/12/2026. **TBD:** electronic signing of the order is a separate use case; this one prints a hand-signature line.
- **QĐ 130/QĐ-BYT as amended by QĐ 4750/QĐ-BYT (XML3):** the ordering Doctor's code is the practice licence number (`staff_accounts.practice_license_no`); relevant to export, not to this print.

## Deviations from Report 3 and BR-28

| Source | This specification | Decision |
| --- | --- | --- |
| BR-28: the system routes a released order to the least busy room | The Doctor chooses the room when ordering; least busy is only the default | I1-D47 |
| I1-D32: rooms with waiting, skipped or in-progress orders cannot be deactivated | Also rooms chosen by orders still Pending Payment | I1-D47 |
| Report 3 UC-50 | Not compared (source missing) | **TBD** |

## Open items

- **TBD:** compare with Report 3 UC-50 once the SRS file is back in `docs/source/`.
- **TBD:** `loaichidinh` and `loaimachidinh` are stored as `null` by `ParaclinicalOrderServiceImpl` (line 170); `schema.yaml` says they come from the service group (V.2.6.5–6). Fill them in UC-49 so sheets and the export can use them.
- **TBD:** the ordering department and room (V.2.10, V.2.14) are not stored on the order; this print reads them from the Doctor's room (`noichidinh`) and its department.
- **TBD:** a barcode of the order number on the sheet, as on the result slips (UC-68).
- **TBD:** printer integration (HW-01); the browser print dialog is used.
- **TBD:** the Business Rules owner updates BR-28 in `Business_Rules_v7.xlsx`.
