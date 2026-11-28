# Permission Matrix

## Document Authority

This document is the source of truth for confirmed roles and their allowed actions. Security implementation must not invent permissions absent from this matrix.

## Status

- Roles and use-case assignments are transcribed from `usecases.xlsx`, sheet `Mới` (see [`business-context.md`](business-context.md#sources)). Scope conditions come from the business rules in the `BR` sheet.
- Roles: `ADMIN`, `RECEPTIONIST`, `DOCTOR`, `TECHNICIAN`, `CASHIER`. Each account has exactly one role (BR-02).
- Technical permissions follow the model decided in [`security.md`](security.md#authorization-model): names `<resource>:<action>`, a fixed role-to-permission table in backend code, and the JWT carrying only the role. Each permission name is added to this matrix together with the first use case that implements it; until then the use-case ID is the stable reference.
- An account whose password must be changed (first login or after an Admin reset) can only change its password, view its own profile, and log out, whatever its role.
- Anything not listed is denied.

Status values:

| Status | Meaning |
| --- | --- |
| Source | Assigned in the `Mới` sheet and consistent with the business rules |
| TBD | Assignment or scope is unclear; see the referenced open question in `business-context.md` |
| Decision | Not in the `Mới` sheet; added by a team decision recorded in [`iteration-1-decisions.md`](iteration-1-decisions.md) |

## Role Summary

| Use cases | Admin | Receptionist | Doctor | Technician | Cashier |
| --- | :---: | :---: | :---: | :---: | :---: |
| UC-1 → UC-4 Authentication and own profile | ✓ | ✓ | ✓ | ✓ | ✓ |
| UC-5 → UC-11 Account management | ✓ | | | | |
| UC-12 → UC-19 Department and room management | ✓ | | | | |
| UC-20 → UC-25 Service and price management | ✓ | | | | |
| UC-26 → UC-33 Statistics and report export | ✓ | | | | |
| UC-34 Own examined-patient list | | | ✓ | | |
| UC-35 → UC-38, UC-40 → UC-42, UC-74 Patient profile and reception | | ✓ | | | |
| UC-39 Medical history and allergies | | | ✓ | | |
| UC-43 → UC-62 Examination, records, conclusion | | | ✓ | | |
| UC-63 → UC-68 Paraclinical services | | | | ✓ | |
| UC-69 → UC-73 Payment | | | | | ✓ |

## Matrix

| Role | Resource | Action | Use case | Scope / Conditions | Status |
| --- | --- | --- | --- | --- | --- |
| All roles | Session | Log in, log out | UC-1, UC-2 | Locked accounts cannot log in (BR-01). | Source |
| All roles | Own account | Change password | UC-3 | Current password required (BR-03). | Source |
| All roles | Own account | View profile; update phone number and email | UC-4 | Own account only; other fields are Admin-only (BR-04). | Source |
| Admin | Staff account | List, search, view detail | UC-5, UC-6 | All accounts. Detail includes the 10 latest sign-in events, as specified in `security.md`. | Source |
| Admin | Staff account | Create; update name, academic title, phone, email | UC-7, UC-8 | Username unique (BR-01). The current deployment provisions its Admin through bootstrap configuration. | Source |
| Admin | Staff account | Assign or change role | UC-9 | Exactly one role per account (BR-02). | Source |
| Admin | Staff account | Lock, unlock | UC-10 | — | Source |
| Admin | Staff account | Reset password | UC-11 | Only Admin may reset (BR-03). | Source |
| Admin | Staff account (Doctor) | Add to / remove from the facility on the national prescription system | UC-93 | QĐ 808 IV, V with the facility's national sign-in (III); the facility password is asked when needed and never stored; the link code and the Doctor role are locked while added (BR-59-17). | **ASSUMPTION** (PO 27/11/2026; UC-93 in `usecases.xlsx` covers the link code only) |
| Admin | Department | List, view detail, update | UC-12 → UC-14 | No create/deactivate use case (OQ-07). | Source |
| Admin | Room | List, create, update | UC-16 → UC-18 | Room code unique; paraclinical room declares service group (BR-06). | Source |
| Admin | Room | Activate, deactivate | UC-19 | Not while patients are waiting or in progress (BR-07). | Source |
| Admin | Room staff assignment | Assign or remove doctors and technicians | UC-15 | Department follows the room (BR-05). | Source |
| Admin | Service | List, view detail, create, update information | UC-20 → UC-23 | Clinic price and insurance price (BR-08). | Source |
| Admin | Service price | Update | UC-24 | Applies only to orders created afterwards (BR-09). | Source |
| Admin | Service | Activate, deactivate | UC-25 | — | Source |
| Admin | Medicine | List, view detail, create, update, activate, deactivate | UC-81 → UC-84 | Code and registration number unique (BR-51, BR-79); the code cannot change; only active medicines of the ordinary group can be prescribed (BR-33). | Decided (I1-D46) |
| Admin | Statistics | View dashboard and visit, doctor, department/room, paraclinical, revenue, diagnosis statistics | UC-26 → UC-32 | Revenue split insurance/patient (BR-38). Contains aggregated clinical data. | Source |
| Admin | Statistics report | Export (Excel/PDF) | UC-33 | — | Source |
| Admin | Clinic facility | View, declare, update | UC-77 | Facility code locked once a visit exists (BR-77-02). See [`uc-77-clinic-facility.md`](uc-77-clinic-facility.md). | **ASSUMPTION** (UC-77 not yet in `usecases.xlsx`) |
| Doctor | Examined-patient list | View | UC-34 | Only patients examined by the logged-in doctor (BR-39). | Source |
| Receptionist | Patient profile | Search, view administrative data and visit history | UC-35, UC-36 | — | Source |
| Receptionist | Patient profile | Create, update administrative data | UC-37, UC-38 | One profile per citizen ID (BR-12); no medical history or allergies (BR-23). | Source |
| Receptionist | Insurance card | Verify via simulated portal | UC-74 | — | Source |
| Receptionist | Visit | Register, print examination ticket | UC-40, UC-41 | One active visit per patient (BR-17); active rooms only (BR-07). | Source |
| Receptionist | Room queue status | View counts per examination room | UC-42 | Counts only. | Source |
| Doctor | Patient medical history and allergies | Update | UC-39 | During examination only (BR-23). | Source |
| Doctor | Room waiting lists | View examination and conclusion lists | UC-43, UC-54 | Assigned room only (BR-19). | Source |
| Doctor | Visit | Call patient | UC-44 | Assigned room only (BR-19, BR-20). | Source |
| Doctor | Skipped list | View, recall | UC-45, UC-46 | Assigned room only (BR-19). Technician use is unclear (OQ-03). | TBD |
| Doctor | Visit | Record vital signs, symptoms, findings | UC-47 | Visit assigned to the doctor; not after closure (BR-32). | Source |
| Doctor | Visit | Transfer to another room | UC-48 | Own room, on duty; visit In Examination and its examination not yet saved (BR-24; OQ-05 decided); reason required (BR-97). | Decided |
| Doctor | Visit | Reprint examination ticket after a transfer | UC-48 AF-02, UC-41 | Visit the Doctor has just transferred; the ticket shows the destination room and new queue number. | Source |
| Doctor | Paraclinical order | Create and print | UC-49, UC-50 | Active services only (BR-10). | Source |
| Doctor | Paraclinical service | Cancel a service the patient refused | BR-25, BR-41 | Doctor of the visit; visit Waiting for Paraclinical and record open; no Technician has accepted the service and its receipt is unpaid; reason required. The receipt is recalculated (BR-27). | Decided (I1-D44) |
| Doctor | Medical record | List, view detail, export PDF (form 15/BV1) | UC-51 → UC-53 | All patients (BR-35). Export only a closed record, with a purpose; recipient required for the patient and authorities (BR-43, BR-131; [`uc-53-export-medical-record.md`](uc-53-export-medical-record.md)). The Admin has no access to these screens or their API (BR-120). | Source |
| Doctor | Paraclinical result | View | UC-55 | Current visit. | Source |
| Doctor | Visit | Conclude | UC-56 | All orders have results (BR-30). | Source |
| Doctor | Conclusion, prescription, follow-up slip | Print | UC-57, UC-60, UC-62 | Only the Doctor of the visit, once the record is closed, right after closing or from UC-52; no room or shift check. Only an accepted prescription, only a scheduled appointment. Later prints carry "Bản sao". | Decided (PO 23/11/2026, [`uc-57-print-conclusion.md`](uc-57-print-conclusion.md)) |
| Doctor | Prescription | Create, update | UC-59 | Current visit: the Doctor of the visit, on duty in its room, record open (BR-59-15). Printing (UC-60) is in the print row above. | Source |
| Doctor | Medicine catalog | Search prescribable medicines | UC-59 | Active ordinary medicines only (BR-59-03); no catalog maintenance. | Decided (PO 15/11, [`uc-59-e-prescription.md`](uc-59-e-prescription.md#decisions-needed)) |
| Doctor | Prescription | Send, resend, view send history and result, correct a rejected one, delete a never-sent one | UC-90 → UC-92, UC-59 AF-03 | Same scope as create. Sending needs the Doctor added to the facility on the national system and the Doctor's own national sign-in (BR-59-17); no digital signature (out of scope, 27/11/2026). Delete only while `PENDING` with no send. | Decided (PO 15/11, [`uc-59-e-prescription.md`](uc-59-e-prescription.md#decisions-needed)) |
| Doctor | Follow-up appointment | Create, change | UC-61 | Doctor of the visit, on duty in its room, record open, conclusion saved; one appointment per visit, changed only while scheduled; the Doctor decides who needs one (TT 01/2025 Điều 11); date after the visit date, at most 365 days (BR-108). | Decided (PO 15/11, [`uc-61-follow-up-appointment.md`](uc-61-follow-up-appointment.md#decisions-needed)) |
| Doctor | Visit / medical record | Close | UC-58 | Locks the record (BR-32). | Source |
| Technician | Personal dashboard and duty schedule | View | — | Dashboard shows a room assigned to the signed-in technician. Outside their shift, it also shows that room's waiting services and pending-result list for reference. The personal duty schedule contains only their scheduled shifts. | Updated |
| Technician | Paraclinical waiting list and pending-result list | View | UC-63, UC-64 | The on-duty technician sees paid or released waiting services for their room, including unaccepted services carried over from an earlier shift. Outside their shift, technicians can also see waiting services and pending-result entries assigned to other technicians in a room covered by their active room assignment or a duty schedule for today or later. Services in progress remain visible to their performer. | Updated |
| Technician | Paraclinical order | Call and accept | UC-65 | The on-duty technician can accept waiting services in their room. At shift change, waiting services carry over to the next on-duty technician in the room, with their queue number and check-in status preserved. | Updated |
| Technician | Paraclinical order | Perform | UC-66 | The accepting technician can finish the service and submit the result after their shift. | Updated |
| Technician | Paraclinical result | View pending, record and submit, print | UC-64, UC-67, UC-68 | Only services the technician performed; no edit after submission (BR-29). | Source |
| Cashier | Payment / receipt | View pending, search, view history | UC-69, UC-70, UC-73 | — | Source |
| Cashier | Payment / receipt | Confirm payment | UC-71 | Cannot create receipts manually (BR-36). | Source |
| Cashier | Payment / receipt | Print receipt | UC-72 | Only after payment is confirmed (BR-37). | Source |
| Cashier | Visit | Print the queue-number ticket | UC-41 | Only a visit that already has its queue number, i.e. its examination fee is paid; otherwise `PAYMENT_NOT_PAID` (I1-D48). Uses the examination-ticket endpoints. | Decision |
| System | Visit, order, receipt | End-of-day cancellation and abandonment | — | BR-22; not triggered by any user role. | Source |

## Not Granted

Explicit consequences of the matrix, useful for authorization tests:

- Only Admin manages accounts, departments, rooms, services, prices, and organisation-wide statistics.
- Receptionists cannot read or change medical records, medical history, or allergies (BR-23; OQ-09).
- Doctors and Technicians generally act within their assigned room. Doctors may read medical records (BR-19, BR-35), and Technicians may continue to receive and finish paraclinical services assigned to their shift after it ends.
- Cashiers cannot create or cancel receipts manually (BR-36).
- No role can edit a locked medical record or a submitted paraclinical result (BR-29, BR-32).
- No role can manually set a visit to Abandoned (BR-21).

## Decision Notes

| Date | Change | Source |
| --- | --- | --- |
| 14/10/2026 | Initial matrix transcribed from `usecases.xlsx` sheet `Mới` and `Business_flow.xlsx` sheet `BR`. | Team decision to treat these sheets as authoritative |
| 01/11/2026 | Added Admin UC-77 clinic facility row. | **ASSUMPTION** until UC-77 is added to `usecases.xlsx` |
