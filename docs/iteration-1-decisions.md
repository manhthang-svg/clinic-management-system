# Iteration 1 — Decision Register

## Document Authority

This document records the business and technical decisions that the Iteration 1 schema ([`iteration-1-schema.dbml`](iteration-1-schema.dbml)), Flyway migrations, and entities depend on. It started as the team's draft register of 36 questions (I1-D01 → I1-D36, reviewed 15/10/2026) and records how each one was resolved.

Source order follows [`business-context.md`](business-context.md#sources). Report 3 is cited as "R3" with its section or use case. Account questions are tracked separately in [`use-cases/staff-account.md`](use-cases/staff-account.md#decisions).

Status values:

| Status | Meaning |
| --- | --- |
| Decided (R3) | Report 3 states the rule; adopted on 15/10/2026 |
| Decided (team) | The team chose between options on 15/10/2026 |
| Proposed | Technical proposal in the DBML, approved when the DBML review is accepted |
| Open | Not decided; the "Blocks" column says what waits for it |

## Scope

Iteration 1 covers UC-1 → UC-19, UC-35 → UC-38, UC-40 → UC-42 from the team's Phase 0 plan. The team additionally includes UC-74 so insurance verification can be completed by the registration flow (I1-D37 and I1-D38).

## Decisions Made on 15/10/2026

These four resolve contradictions inside Report 3.

| ID | Decision | Report 3 conflict | Report 3 must be corrected in |
| --- | --- | --- | --- |
| I1-D06 / D20 / D21 | **One shared examination service.** A single `EXAMINATION` service without a department is used for the fee of every visit. At most one `EXAMINATION` service is active at a time. Rooms and departments are not linked to services for the examination fee. | §I.3.2 Entity Service and ExaminationRegistration say "shared"; UC-22 says an Examination service belongs to a Clinical department | UC-22 step 6 |
| I1-D28 | **Queue numbers per room per day**, starting at 1. | §I.3.3 "Queue number per department per day" vs UC-40 step 12 "next queue number of the room for today" | §I.3.3 data rule, §III Background Job #3 |
| I1-D26 | **Invoice number assigned when the payment is created.** Every payment, including `PENDING` and later `CANCELLED` ones, has a unique number, so the Cashier can search by it (UC-70). A cancelled payment keeps its number. | Not stated in Report 3 | — |
| I1-D01 / D03 | **Demo seed for development only.** Demo reference data lives in a separate Flyway location that runs only with the development profile. Production reference data waits for the real lists and an owner (I1-D02). | — | — |

## Register

| ID | Topic | Resolution | Status | Source |
| --- | --- | --- | --- | --- |
| I1-D01 | How departments are first created | No create-department use case exists (OQ-07). Development uses the demo seed; production data is loaded by an approved migration once I1-D02 is settled. | Decided (team) | Phase 0 plan |
| I1-D02 | Owner of the department, room, service-group, service, and price lists | Nobody assigned yet. | Open — blocks production seed and go-live, not the schema | — |
| I1-D03 | Demo or real data | Demo data for development only; it is never applied to production. | Decided (team) | — |
| I1-D04 | Department identity | Name unique, at most 100 characters; description at most 500. No department code. The type cannot change once the department has a room. | Decided (R3) | R3 UC-14, Entity Department |
| I1-D05 | Room code | Unique across all rooms, including inactive ones; at most 20 characters of letters, digits, and hyphen. | Decided (R3) | BR-06, R3 UC-17 |
| I1-D06 | Examination service model | See the decisions above. | Decided (team) | — |
| I1-D07 | Role change with work in progress | See AQ-09, AQ-16, AQ-17. | Decided (R3, team) | R3 UC-09 |
| I1-D08 | Lock with work in progress | Allowed with a warning; work stays in the room's lists. | Decided (R3) | R3 UC-10 AF-04 |
| I1-D09 | Staff per room | A Doctor or Technician has at most one active room; a room can have several staff. Partial unique index on `staff_account_id` for active assignments. | Decided (R3) | BR-05, R3 UC-15 |
| I1-D10 | Shifts | Managed in the HR spreadsheet; out of scope. | Decided (R3) | R3 UC-15 |
| I1-D11 | Account search | Partial match ignoring case and Vietnamese diacritics. The normalization technique is chosen when UC-5 is designed. | Decided (R3) | R3 UC-05 |
| I1-D12 | Patient required fields | Required: full name (at most 100), date of birth (not in the future, at most 120 years ago), gender, address. Citizen ID required from age 14; phone required from age 18; guardian (name, phone, relationship) required under 18. Optional: occupation. Ethnicity defaults to `Kinh`, nationality to `Viet Nam`. Age-dependent rules are enforced by the application because age changes over time. | Decided (R3) | R3 UC-37 |
| I1-D13 | Patient without citizen ID | Allowed under age 14. | Decided (R3) | R3 UC-35 AF-02, UC-37 |
| I1-D14 | Citizen ID uniqueness | Unique when present; exactly 12 digits. | Decided (R3) | BR-12, R3 UC-37 |
| I1-D15 | Address format | One free-text field, filled by hand or from the citizen-ID QR code. | Decided (R3) | R3 UC-37 AF-01 |
| I1-D16 | Guardian | Name, phone, and relationship stored on the patient; required under 18. | Decided (R3) | R3 Entity Patient, UC-37 |
| I1-D17 | Insurance-card history | No card history on the patient. Card data is stored per visit; a search by card number searches previous visits. | Decided (R3) | R3 UC-35 step 5, Entity ExaminationRegistration |
| I1-D18 | Insurance data per visit | Card number (15 characters), benefit code (third character, 1–5), expiry date, coverage rate, and verification time. The coverage rate is the value returned by the simulated portal (UC-74), in (0, 100%]; the system does not derive it from the benefit code, route, or beneficiary group (I1-D23 rule 3). The registered primary-care facility returned by the portal is not stored. | Decided (R3, team) | R3 §I.3.3, UC-74 |
| I1-D19 | Possible duplicate patients | Same full name and date of birth triggers a warning; the Receptionist opens the existing profile or creates anyway. Never merged automatically. | Decided (R3) | R3 UC-37 AF-02 |
| I1-D20 | Fee lookup at registration | The shared examination service (I1-D06). | Decided (team) | — |
| I1-D21 | Examination services per room | Not applicable: rooms are not linked to the examination service. | Decided (team) | — |
| I1-D22 | Money | Whole VND. Clinic price greater than 0 and at most 100,000,000; insurance price empty (not covered) or 0 to 100,000,000. Empty and 0 are different. | Decided (R3) | R3 UC-22 |
| I1-D22a | Money column type | `numeric(15,0)` mapped to `BigDecimal` (database-conventions requires `NUMERIC`; scale 0 enforces whole VND). | Proposed | database-conventions |
| I1-D23 | Line amounts (exam-fee and paraclinical receipts) | See [Money Calculation](#money-calculation). | Decided (team) | BR-09, BR-15, BR-26, BR-27, BR-33 |
| I1-D23a | Simplifying assumptions for insurance | See [Money Calculation](#money-calculation), rule 8. | Decided (team) | — |
| I1-D24 | Fee receipt for every visit | Always created at registration. | Decided (R3) | BR-15 |
| I1-D25 | Fee-exempt receipt | Exemption is decided per receipt: a receipt whose patient total is 0 is set to `PAID` automatically and the visit goes straight to `WAITING` (BR-15), or the paraclinical order is released (BR-26). No `EXEMPT` status. | Decided (R3, team) | R3 UC-40 AF-03, I1-D23 rule 6 |
| I1-D26 | Invoice number timing | See the decisions above. | Decided (team) | — |
| I1-D26a | Code formats | Patient code, registration code, and invoice number come from PostgreSQL sequences with a prefix: `BN` + 8 digits, `DK` + 10 digits, `HD` + 10 digits. | Proposed | — |
| I1-D27 | Price snapshot | Prices are copied onto the payment line when the payment is created; later price changes do not affect it. Because of the snapshot, prices are kept on the service itself and no price-history table is needed. | Decided (R3) | BR-09, R3 §I.3.3 Price snapshot |
| I1-D28 | Queue-number scope | See the decisions above. | Decided (team) | — |
| I1-D28a | Queue date | The clinic's local date, time zone `Asia/Ho_Chi_Minh`. **ASSUMPTION** pending OQ-11. Numbers come from a per-room, per-day counter row updated in the registration transaction. | Proposed | OQ-11 |
| I1-D29 | When the queue number is issued | At registration, before payment. | Decided (R3) | R3 UC-40 step 12, BR-16 |
| I1-D30 | Unpaid visits | Have a queue number but are not in the waiting list while `WAITING_FEE_PAYMENT`. | Decided (R3) | BR-15, BR-18 |
| I1-D31 | Fee-exempt visit | Payment `PAID` and visit `WAITING` in the same transaction as the registration. | Decided (R3) | R3 UC-40 AF-03 |
| I1-D32 | Room deactivation guard | Rejected while an examination room has a visit `WAITING_FEE_PAYMENT`, `WAITING`, `SKIPPED`, `IN_EXAMINATION`, or `WAITING_CONCLUSION`; or a paraclinical room has an order waiting, skipped, or in progress. | Decided (R3) | BR-07, R3 UC-19 |
| I1-D33 | Ticket printing | HTML and print CSS; reprint marked "Reprint" and audited. | Proposed | R3 UC-41 |
| I1-D34 | Ticket content | Hospital name, registration code, patient code, full name, year of birth, gender, queue number, room name and location, the room's doctors, visit type, coverage rate, priority mark, registration time; the amount to pay or "Fee covered – go to the waiting area". No QR code, logo, or signature required. | Decided (R3) | BR-16, R3 UC-41 |
| I1-D35 | Business audit for Iteration 1 | A `business_audit_events` table, separate from `security_audit_events`, append-only, written in the same transaction as the change. Each row: time, actor, event type, entity type and ID, correlation ID, the names of changed fields, and old/new values **only for departments and rooms** (non-personal data, required by R3 UC-14 and UC-18). Patient rows record field names only, never values. Events: `PATIENT_CREATED`, `PATIENT_UPDATED`, `VISIT_REGISTERED`, `TICKET_REPRINTED`, `INSURANCE_VERIFIED` (outcome only, no card data), `PAYMENT_CREATED`, `DEPARTMENT_UPDATED`, `ROOM_CREATED`, `ROOM_UPDATED`, `ROOM_ACTIVATED`, `ROOM_DEACTIVATED`, `STAFF_ASSIGNED_TO_ROOM`, `STAFF_REMOVED_FROM_ROOM`. Reading a patient profile (UC-35, UC-36) is not audited: R3 requires audit of medical-record access, which starts with UC-51. No API reads this table in Iteration 1; retention follows security.md. | Proposed | R3 §IV.2.3 Audit trail, security.md |
| I1-D36 | Audit retention and viewers | Append-only until legal review; only UC-6 reads sign-in events (AQ-18). | Decided (team) | security.md |
| I1-D37 | Iteration 1 WBS | `Project_Tracking.xlsx` assigns UC-1 → UC-19, UC-35 → UC-38, and UC-40 → UC-42 to Iteration 1. The team explicitly extends that scope with UC-74 (I1-D38). | Decided (team) | Project_Tracking.xlsx, team decision |
| I1-D38 | Insurance verification in Iteration 1 | **UC-74 is in Iteration 1.** The simulated portal is a separate adapter behind a port interface, backed by its own `simulated_insurance_cards` table that domain code never reads directly. It returns card status, full name, date of birth, benefit code, coverage rate, valid from, valid to, and primary-care facility; a configurable response delay lets tests cover the 10-second timeout (R3 UC-74 EF-04). The visit stores the verified card data and `insurance_verified_at`. Demo cards come from the development seed; how the cards are loaded outside development is part of I1-D02. | Decided (team) | BR-13, R3 UC-74 |
| I1-D39 | Priority reason | A priority visit stores its reason: `ELDERLY` or `FRAIL_DISABLED`. | Decided (R3) | R3 UC-40 step 4 |
| I1-D40 | Approved full schema (40 tables) | `DB_HSBA_phongkham_de_xuat_v2.xlsx` (exported as [`schema.yaml`](schema.yaml)) is the approved target schema, approved 22/10/2026. It is applied by new Flyway migrations from V3 on; V1 and V2 are never edited because they ran on a shared environment. Column names follow the CV 365 export field names (`makhoa`, `sovaovien`, …), an intended exception to the abbreviation rule in database-conventions.md. Java mapping keeps the repository conventions where the spreadsheet differs: `Instant` for `timestamptz`, IDs from `UuidV7Generator` (no `uuid-creator`), no setters, existing enum names (`Role`, `AccountStatus`, `Gender`, `ActivationStatus`, `AuditOutcome`) and the `MedicalService` entity name. Supersedes part of I1-D04 (departments gain the code `makhoa`; the name may be 255 characters) and the 20-character room code limit of I1-D05 (now 255; the format rule stays). | Decided (team) | [`schema.yaml`](schema.yaml) |
| I1-D41 | Applying the approved schema | **ASSUMPTION** unless noted; review with the spreadsheet owner. (1) Corrections to the spreadsheet: `visits.doituongbn_loai` is `VARCHAR(4)`, because `VARCHAR(3)` cannot hold `BHYT`. (2) V2 business rules are kept on the new columns: priority reason (I1-D39), card number format and benefit code, coverage rate range, relative data (I1-D16), one unfinished visit per patient (BR-17), queue numbers unique per room and day (I1-D28), and money reconciliation per line and per receipt. The 10-digit phone formats are dropped because the columns now allow 15 characters; the V2 rule that every insurance field is set exactly for insurance visits is dropped until UC-74 defines how an invalid card is recorded (**TBD**). (3) Supersedes I1-D25 and I1-D31: a receipt whose patient total is 0 has status `FEE_EXEMPT`, not `PAID`. A skipped visit keeps its status and sets `is_skipped`; there is no `SKIPPED` status. (4) A visit has one examination-fee payment and any number of paraclinical payments. (5) Coded values (`gioitinh`, `doituongbn`, `ketquadieutri`, `loaidon`, `ketquabatthuong`) stay numbers or characters as in the export format, not Java enums; they have a CHECK where the spreadsheet lists every value (not `ketquadieutri`). (6) `conclusions.maicd_khac` and `tenicd_khac` are not stored; the export joins `conclusion_secondary_diagnoses`. (7) Entity names: `MedicalService` (spreadsheet `Service`) and `ConclusionSecondaryDiagnosis` (spreadsheet `ConclusionSecondaryDiagnose`). `prescriptions.lienthong_status` is an enum with a CHECK. (8) Constraints read from the spreadsheet notes: one default medical-record form; one `clinic_facility` row; at most one initial vital-sign measurement per record; an appointment is `ARRIVED` exactly when it has a visit; one payment per paraclinical order; a result file belongs to exactly one result; `submitted_at` is set exactly for `SUBMITTED` results; a failed export has an error message; staff sign with their account, a patient or representative with an ID number, a representative also with the relationship, and a digital signature has a certificate serial; archives are kept at least 10 years; the prescription code is `xxxxxyyyyyyy-z` ending with its type. (9) Supersedes I1-D35: the business audit trail is `audit_logs`, whose old and new values are kept masked for every entity. (10) **TBD**: database privileges that block UPDATE and DELETE on `audit_logs` and `record_signatures` (needs the deployment roles); the value lists of `examination_findings.coquan` and `conclusions.ketquadieutri`; the reference data (form 15/BV1, ICD-10, medicines, clinic facility), which waits for I1-D02. | Proposed | [`schema.yaml`](schema.yaml) |

## Money Calculation

Decided by the team on 15/10/2026 (I1-D23, I1-D23a). Scope: every line of an examination-fee receipt and of a paraclinical receipt. Each line is one service, quantity 1. No drug charges (BR-33).

1. **Applied price.** Snapshotted when the line is created and never changed afterwards (BR-09).
   - The visit has valid insurance and the service has an insurance price: the line is **insurance-covered** and the applied price is the insurance price.
   - Every other case (self-paid visit, invalid card, service without an insurance price): the applied price is the clinic price (BR-27).
2. **Insurance amount.** Covered line: `round_half_up(applied price × coverage rate)`, to whole VND. Other lines: 0.
3. **Coverage rate.** Taken from the simulated portal result (UC-74) and stored on the visit at verification time. Range (0, 100%]. It is final; the system does not recalculate it by route or beneficiary group.
4. **Patient amount.** `applied price − insurance amount`. Only the insurance amount is rounded, so every total reconciles.
5. **Stored per line:** price type (`INSURANCE` or `CLINIC`), applied price, coverage rate, insurance amount, patient amount.
6. **Receipt totals** are the sums of the lines. Exemption is decided per receipt, not per line: a receipt whose patient total is 0 is fee-exempt; the visit joins the waiting list immediately (BR-15) or the order is released immediately (BR-26).
7. **Database CHECKs** on each line: insurance amount ≥ 0; patient amount ≥ 0; insurance amount + patient amount = applied price; price type `CLINIC` implies insurance amount = 0.
8. **Simplifying assumptions** (stated in the report, like BR-21). The system does not apply:
   - co-payment exemption when the visit total is below 15% of the base salary, or after 5 consecutive years of coverage;
   - service-specific payment rates;
   - charging the difference between the clinic price and the insurance price.

Report 3 must be corrected to match: UC-74 step 6 and §I.3.3 "Payment calculation" derive the coverage rate from the benefit code (100% / 95% / 80%); here the portal returns it.

## Report 3 Fields Deferred From Iteration 1

These Report 3 attributes belong to use cases outside Iteration 1 and are added with them: patient medical history and allergies (UC-39), visit cancellation reason (cancellation flow), visit transfer reason (UC-48), payment method and confirming Cashier (UC-71), paraclinical orders and results (UC-49 → UC-68).

## Documents to Update

- [`state-transitions.md`](state-transitions.md) still uses the superseded numbering, Vietnamese status codes, and an `MIEN_PHI` invoice status. It must be rewritten with the status codes in the DBML before visit and payment transitions are implemented.
- Report 3 sections listed under "Decisions Made on 15/10/2026".
- [`iteration-1-schema.dbml`](iteration-1-schema.dbml) still shows the V2 schema; the applied schema is [`schema.yaml`](schema.yaml) with the corrections in I1-D41 (Flyway V3 to V10).
