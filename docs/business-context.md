# Business Context

## Document Authority

This document is the source of truth for confirmed business scope, terminology, actors, goals, and constraints. It must not contain invented clinic policies.

## Sources

The content below is transcribed from the team's analysis workbooks in [`source/`](source/). Where the workbooks disagree, the following order applies (team decision, 14/10/2026):

1. `usecases.xlsx`, sheet **`Mới`** — use-case catalog UC-1 → UC-74 and role assignments.
2. `Business_flow.xlsx`, sheet **`BR`** — business rules BR-01 → BR-39 (English/Vietnamese).
3. `Business_flow.xlsx`, sheet **`01 - Scope`** — functional areas and out-of-scope items.
4. `Report-3_Software_Requirement_Specification.docx` (Report 3, Aug 2026) — detailed use-case flows, entity attributes, data rules, and validation formats. It is used to close questions the workbooks leave open (team decision, 15/10/2026).

Team decisions recorded in [`security.md`](security.md) and in the decision table of [`use-cases/staff-account.md`](use-cases/staff-account.md#decisions) take precedence over Report 3. Where Report 3 differs from them, the difference is listed in that document's "Report 3 differences not adopted" section rather than silently applied. Any other conflict between Report 3 and the workbooks is recorded as an open question.

Superseded and kept only for history: `usecases.xlsx` sheet `Cũ` (UC-01 → UC-67, old numbering), `Business_flow.xlsx` sheets `02` → `09` (process steps, BR v9 numbering, change log). UC and BR codes in superseded sheets **do not** match the codes used in this repository.

Use-case codes in this repository are written `UC-<n>` using the `Mới` numbering. Business-rule codes are `BR-<nn>` from the `BR` sheet.

## Confirmed Context

- The product is for a small private outpatient clinic that receives about 200–300 patients per day. Peak reception hours are 07:00–09:00.
- Patients are either **health-insurance (BHYT)** visits or **self-paid** visits. Insurance cards are verified through a **simulated** insurance portal; there is no integration with the real portal.
- Examination and paraclinical fees are collected **in advance**. There is no payment step after the doctor's conclusion (BR-32).
- The clinic does not sell or dispense medication. Prescriptions are printed for the patient only, and receipts never include drug costs (BR-33).
- The software records the cashier's "paid" confirmation only. It does not process money transactions or accounting reconciliation (BR-36).
- Analysis approach: compare the actual clinic workflow with the clinic's existing software to find gaps, rather than starting from pain points.
- **TBD:** Business goals and measurable success criteria.

## Actors

Each staff account has exactly one role (BR-02). There are five roles:

| Role | Responsibility (summarised from the `Mới` sheet) |
| --- | --- |
| Admin | Staff accounts and roles, departments, rooms and staff assignment, service catalog and prices, statistics and report export. |
| Receptionist | Patient search and administrative profiles, insurance verification, visit registration, examination ticket, room-queue overview. |
| Doctor | Room waiting lists and patient calls, clinical examination, room transfer, paraclinical orders, medical records, conclusion, prescription, follow-up, closing the visit, own examined-patient list. |
| Technician | Paraclinical waiting list, calling and accepting orders, performing services, recording and printing results. |
| Cashier | Pending payments, payment confirmation, receipt printing, payment history. |

Additional actor facts:

- Room nurses have no separate account and use the Doctor account, including for vital signs (`usecases.xlsx` sheet `Actors`).
- The patient is not a system user.
- The simulated insurance portal is an external actor for UC-74.

## Glossary

| Term | Vietnamese | Meaning |
| --- | --- | --- |
| Visit | Lượt khám | One registration of a patient on one day, from registration to completion or abandonment. A patient has at most one active visit (BR-17). |
| Patient profile | Hồ sơ bệnh nhân | Administrative patient data, unique per citizen ID (BR-12). Drug allergies are a separate field (BR-23). |
| Medical record | Hồ sơ bệnh án | Clinical data recorded for a visit; locked when the visit is closed (BR-32). |
| Citizen ID | CCCD | Primary patient lookup key at reception. |
| Health insurance | BHYT | State health insurance; determines insurance price and the covered amount. |
| Paraclinical service / order | Dịch vụ / chỉ định cận lâm sàng (CLS) | Lab, imaging, or other diagnostic service ordered by a doctor and performed by a technician. |
| Department | Khoa | Clinical or paraclinical department grouping rooms. |
| Room | Phòng | Examination room or paraclinical room; has a unique room code (BR-06). |
| Service group | Nhóm dịch vụ | Group of paraclinical services a paraclinical room performs. |
| Clinic price / insurance price | Giá viện phí / giá BHYT | The two prices of every service (BR-08). |
| Receipt / payment | Phiếu thu / khoản thu | System-generated payable for an examination fee or a paraclinical order, split into insurance-covered and patient-payable amounts (BR-27). |
| Priority patient | Bệnh nhân ưu tiên | Patient placed at the top of all waiting lists (BR-14). |
| Skipped list | Danh sách bỏ qua / tạm hoãn | Patients called but absent (BR-21). |
| Abandoned | Bỏ khám | Terminal visit outcome without conclusion, set only by the end-of-day job (BR-21, BR-22). |
| Follow-up appointment | Lịch tái khám | Next visit date for periodic-monitoring patients (BR-34). |
| ICD-10 | ICD-10 | Diagnosis coding standard used when available (BR-31). |

## In Scope

| Area | Feature groups (`Mới` sheet) | Use cases |
| --- | --- | --- |
| Authentication and own profile | Authentication, Profile Management | UC-1 → UC-4 |
| Administration and catalogs | Account, Department, Room, Service & Price Management | UC-5 → UC-25 |
| Statistics and reporting | Statistics & Reporting | UC-26 → UC-34 |
| Patient profile and reception | Patient Profile Management, Patient Reception | UC-35 → UC-42, UC-74 |
| Clinical examination | Clinical Examination | UC-43 → UC-50 |
| Medical records | Medical Record | UC-51 → UC-53 |
| Conclusion, prescription, follow-up | Examination Conclusion | UC-54 → UC-62 |
| Paraclinical services | Paraclinical Service | UC-63 → UC-68 |
| Payment | Payment | UC-69 → UC-73 |
| End-of-day job (system) | — | No UC; defined by BR-22 |

The full catalog is in [`use-cases/README.md`](use-cases/README.md).

## Out of Scope

From `Business_flow.xlsx` sheet `01 - Scope`:

- Real connection to the national insurance (BHYT/BHXH) portal — replaced by a simulator.
- Patient referral to higher-level hospitals (chuyển tuyến).
- Actual money handling and accounting reconciliation, including reconciling the insurance-covered amount.
- Selling or dispensing medication and pharmacy stock.
- Finance, investment, and equipment management.
- Proactive follow-up reminders by phone or SMS.

Open, not decided: staff work schedules and shift management (currently kept in Excel by HR; gap EX-01).

## Business Rules

Transcribed from `Business_flow.xlsx` sheet `BR`. The workbook is the origin; this table is the repository reference.

| ID | Rule | Statement | Related UC |
| --- | --- | --- | --- |
| BR-01 | Login credentials | Usernames are unique. Staff sign in with username and password; a locked account cannot sign in. | UC-1, UC-7, UC-10 |
| BR-02 | Role-based access | Each account has exactly one role (Admin, Receptionist, Doctor, Technician, Cashier) and can only access the functions of that role. | UC-1, UC-9 |
| BR-03 | Password change | Changing a password requires the correct current password. Only Admin can reset a forgotten password. | UC-3, UC-11 |
| BR-04 | Profile edit scope | Staff can only update their own phone number and email. Full name, academic title, and role are updated only by Admin. | UC-4, UC-8, UC-9 |
| BR-05 | Staff department | A staff member's department is determined by the assigned room. A room can have several staff working in different shifts. | UC-15 |
| BR-06 | Room creation | Room codes are unique. A paraclinical room must declare the service group it performs. | UC-17 |
| BR-07 | Room deactivation | A room with waiting or in-progress patients cannot be deactivated. A deactivated room receives no registrations, transfers, or service assignments. | UC-19, UC-40, UC-48, UC-71 |
| BR-08 | Service pricing | Each service has a clinic price and an insurance price. | UC-20, UC-22 |
| BR-09 | Price effectiveness | A new price applies only to orders created after the update; existing orders keep their creation-time price. | UC-24, UC-49 |
| BR-10 | Active services only | Deactivated services are hidden from the order list; the Doctor can only order active services. | UC-25, UC-49 |
| BR-11 | Visit classification | Insurance versus self-paid is decided at reception by the Receptionist. | UC-40 |
| BR-12 | Patient identification | First-time and returning patients follow the same process. The Receptionist searches by citizen ID first; each citizen ID maps to one profile. | UC-35, UC-37 |
| BR-13 | Insurance verification | An insurance visit requires card verification through the simulated portal. An invalid card makes the visit self-paid and the Receptionist informs the patient. | UC-74, UC-40 |
| BR-14 | Priority patient | Elderly/frail patients are registered as priority, skip the reception queue, and go to the top of examination and paraclinical waiting lists. | UC-40, UC-43, UC-63 |
| BR-15 | Examination fee | An examination-fee receipt is created for every visit at registration and paid at the cashier before the patient joins the waiting list. A patient-payable amount of 0 makes the visit fee-exempt and it joins the list immediately. | UC-40, UC-41, UC-69, UC-71 |
| BR-16 | Examination ticket | The ticket shows registration code, queue number, room, and room location; plus the amount to pay when a fee is payable. | UC-41 |
| BR-17 | Single active visit | A patient has at most one visit that is not Completed, Cancelled, or Abandoned. | UC-40 |
| BR-18 | Waiting lists | Each examination room has an examination waiting list (paid or fee-exempt visits) and a conclusion waiting list (visits whose orders all have results). All lists, including paraclinical, order by priority, then registration time, including transferred visits. Queue numbers are for display and calling only. | UC-43, UC-54, UC-63 |
| BR-19 | Room scope | Doctors and Technicians only view, call, and handle patients or services of their assigned room. | UC-43 → UC-46, UC-54, UC-63, UC-65 |
| BR-20 | Patient call | A present patient continues at the current step (examination starts and is assigned to the caller, visit proceeds to conclusion, or the service starts). An absent patient moves to the skipped list. | UC-44, UC-46, UC-65 |
| BR-21 | Skip mechanism | Applies to examination, paraclinical, and conclusion calls. "Skipped" is a separate status of the visit or order. Skipped patients stay listed by skip time until they return or the day ends. No automatic recall and no recall limit; on return the Doctor/Technician presses "Accept". There is no manual "Abandoned" action. | UC-44, UC-45, UC-46, UC-65 |
| BR-22 | End-of-day job | At day end: unpaid examination and paraclinical payments become Cancelled; skipped orders and every visit not Completed (waiting, skipped, in examination, waiting for results, waiting for conclusion) become Abandoned without conclusion. Covers patients who leave without notice, including those refusing paraclinical services. | UC-43, UC-45, UC-46, UC-54, UC-69, UC-71 |
| BR-23 | Medical history and allergies | Drug allergy is a separate profile field. History and allergies are updated only by the Doctor during examination; the Receptionist updates administrative data only. | UC-38, UC-39 |
| BR-24 | Patient transfer | The Doctor may transfer a waiting or in-examination patient to another active examination room if no new medical record has been opened for the visit. A reason is required. The visit is reused and returns to "Waiting for Examination" in the new room with a new queue number, keeping its original registration time. No extra fee. | UC-48 |
| BR-25 | Paraclinical order | The Doctor explains services and estimated cost so the patient agrees before confirming. Confirming creates a paraclinical receipt. The Doctor cannot cancel a confirmed order; orders are cancelled only through the refusal flow or the end-of-day job. | UC-49 |
| BR-26 | Paraclinical payment and release | A paraclinical receipt is created for every order of every visit. A patient-payable amount of 0 releases the order immediately; otherwise it reaches the room only after the Cashier confirms payment. Insurance patients still pay for uncovered services. | UC-49, UC-63, UC-69, UC-71 |
| BR-27 | Receipt breakdown | Every receipt separates the insurance-covered amount from the patient-payable amount. Insurance prices apply to covered services of valid-insurance visits; clinic prices apply to self-paid visits and uncovered services. | UC-31, UC-40, UC-49, UC-71 |
| BR-28 | Paraclinical routing | Released orders are routed to the active room with the fewest waiting patients among rooms performing the service. A visit can have several orders in several rooms and moves to the conclusion list once all orders have results. | UC-71, UC-54 |
| BR-29 | Result submission | A Technician records results only for services they performed. A submitted result cannot be edited. | UC-64, UC-67 |
| BR-30 | Conclusion prerequisite | The Doctor can conclude only when every order of the visit has a result. | UC-54, UC-56 |
| BR-31 | Diagnosis coding | The final diagnosis includes an ICD-10 code when available. | UC-32, UC-56 |
| BR-32 | Visit closure | A visit becomes Completed when the Doctor closes the record; the record is then locked. No payment after conclusion. | UC-58 |
| BR-33 | Prescription | No medication sales or dispensing; prescriptions are printed only and receipts never include drug costs. | UC-59, UC-60 |
| BR-34 | Follow-up appointment | Only for periodic-monitoring patients (hypertension, diabetes). The date must be after the current visit date and is printed on the follow-up slip. | UC-61, UC-62 |
| BR-35 | Medical record access | Doctors can view and export the records of all patients, including all visits and paraclinical results. Only the current, uncompleted visit can be edited. | UC-51, UC-52, UC-53 |
| BR-36 | Receipt handling | Receipts are generated by the system; the Cashier cannot create them. The Cashier, not the Receptionist, issues and prints receipts. The software records only the "paid" confirmation. | UC-69 → UC-72 |
| BR-37 | Payment status and receipt | A receipt is Pending, Paid, or Cancelled. Each receipt has a unique number and can be printed only after payment is confirmed. | UC-71, UC-72, UC-73 |
| BR-38 | Revenue breakdown | Revenue statistics separate the insurance-covered amount from the patient-paid amount. | UC-31 |
| BR-39 | Examined-patient report scope | The examined-patient list shows only patients examined by the logged-in Doctor in the selected period; it does not restrict record access (BR-35). | UC-34 |

## Open Questions and Assumptions

Inconsistencies found while transcribing the sources. None of these may be resolved in code without a team decision.

| ID | Question | Where |
| --- | --- | --- |
| OQ-01 | **TBD:** BR-25 refers to a "refusal flow (BR-28)", but BR-28 is paraclinical routing and no rule in the `BR` sheet defines paraclinical refusal. BR-22 instead treats refusing patients as abandoned at day end. Does an explicit refusal flow exist? | BR-22, BR-25 |
| OQ-02 | **TBD:** BR-25 cites the end-of-day job as BR-21; the job is BR-22. BR-20 cites itself for the skipped list; BR-21 is presumably meant. Confirm and fix in the workbook. | BR-20, BR-25 |
| OQ-03 | **TBD:** UC-45 and UC-46 describe "Doctor or Technician", but only Doctor is assigned in the role matrix. Is there a separate Technician skipped-list use case, or does UC-65 cover it? | UC-45, UC-46, UC-65 |
| OQ-04 | **TBD:** BR-14 lists only elderly/frail patients as priority; the `Actors` sheet also mentions emergency patients. | BR-14 |
| OQ-05 | **TBD:** BR-24 allows transfer "if no new medical record has been opened". It is unclear whether recording findings or ordering paraclinical services counts as opening a record. | BR-24, UC-48 |
| OQ-06 | **TBD:** No use case covers reassigning patients when a room's doctor is unexpectedly absent (Receptionist action in the superseded v9 rules). | — |
| OQ-07 | **TBD:** There is no use case for creating or deactivating departments (only view/update), for a drug catalog used by prescriptions, or for doctors on duty per day. | UC-12 → UC-14, UC-59 |
| OQ-08 | **TBD:** BR-37 defines three receipt statuses and does not cover fee-exempt receipts (patient-payable amount 0). Is a zero-amount receipt created and auto-paid, or not created? | BR-15, BR-26, BR-37 |
| OQ-09 | **TBD:** The `01 - Scope` sheet lists the Receptionist as a user of medical records (F05), but UC-51 → UC-53 are Doctor-only; UC-36 gives the Receptionist visit history. | F05, UC-36, UC-51 |
| OQ-10 | **TBD:** BR-22 states which visits become Abandoned but not what happens to paid but unperformed paraclinical orders, or whether a refund is considered. | BR-22 |
| OQ-11 | **TBD:** End-of-day job run time, and whether the "day" boundary uses the clinic's local time zone. | BR-22 |
| OQ-12 | **TBD:** Export formats for medical records (UC-53) and the required Ministry of Health form templates noted in sheet `01 - Scope`. | UC-53 |
| OQ-13 | **TBD:** The `Actors` sheet descriptions and the `01 - Scope` UC ranges still follow the superseded numbering and flows (e.g. cashier "issues" and "cancels" invoices). They need updating in the workbook. | `usecases.xlsx`, `Business_flow.xlsx` |
