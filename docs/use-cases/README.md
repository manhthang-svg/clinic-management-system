# Use-Case Specifications

Files in this directory are the source of truth for confirmed use-case behavior. Create one focused Markdown file per use case only after its scope is known.

Each specification should cover:

- goal and participating actors;
- trigger and preconditions;
- primary flow and alternate/error flows;
- business rules and permissions;
- state transitions and transaction boundary;
- inputs, outputs, and observable effects;
- acceptance criteria and test considerations;
- open questions labeled **ASSUMPTION** or **TBD**.

> **TODO:** Add one specification file per use case, starting with the catalog below. Do not infer workflows beyond the catalog and business rules.

## Use-Case Catalog

Transcribed from `docs/source/usecases.xlsx`, sheet `Mới` (authoritative; see [`../business-context.md`](../business-context.md#sources)). The superseded sheet `Cũ` uses different numbers. A specification file for a use case is named `UC-<n>-<short-name>.md` when it is written.

| ID | Use case | Feature | Roles | Description |
| --- | --- | --- | --- | --- |
| UC-1 | Login | Authentication | Admin, Receptionist, Doctor, Technician, Cashier | All staff sign in with username and password to access the functions of their role. |
| UC-2 | Logout | Authentication | Admin, Receptionist, Doctor, Technician, Cashier | All staff sign out and end their current working session. |
| UC-3 | Change Password | Profile Management | Admin, Receptionist, Doctor, Technician, Cashier | All staff change their own password after entering the current password. |
| UC-4 | Update Profile | Profile Management | Admin, Receptionist, Doctor, Technician, Cashier | All staff view their profile and update their own contact information (phone number, email). |
| UC-5 | View Account List | Account Management | Admin | Admin views the list of staff accounts and searches/filters them by name, role, department or status. |
| UC-6 | View Account Detail | Account Management | Admin | Admin views the detailed information of a selected staff account. |
| UC-7 | Create Account | Account Management | Admin | Admin creates a new staff account with login information and personal details. |
| UC-8 | Update Account Information | Account Management | Admin | Admin updates the personal and professional information of a staff account (full name, academic title, phone number, email). |
| UC-9 | Assign Role | Account Management | Admin | Admin assigns or changes the role of an account. |
| UC-10 | Lock/Unlock Account | Account Management | Admin | Admin locks an account to block sign-in, or unlocks a previously locked account. |
| UC-11 | Reset Password | Account Management | Admin | Admin resets the password of a staff account when the user forgets it. |
| UC-12 | View Department List | Department Management | Admin | Admin views the list of clinical and paraclinical departments. |
| UC-13 | View Department Detail | Department Management | Admin | Admin views the detailed information of a department, including its rooms, services and assigned staff. |
| UC-14 | Update Department | Department Management | Admin | Admin updates department information (name, type, description). |
| UC-15 | Assign Staff to Room | Room Management | Admin | Admin assigns doctors and technicians to a room, or removes them from it; a room can have several assigned staff who work in different shifts. The staff member’s department is determined by the room. |
| UC-16 | View Room List | Room Management | Admin | Admin views the list of rooms grouped by department, with their location and status. |
| UC-17 | Create Room | Room Management | Admin | Admin creates a new examination or paraclinical room in a department, with room code, name, location and, for paraclinical rooms, the service group it performs. |
| UC-18 | Update Room | Room Management | Admin | Admin updates room information (name, location). |
| UC-19 | Activate/Deactivate Room | Room Management | Admin | Admin activates or deactivates a room; a room with waiting or in-progress patients cannot be deactivated. |
| UC-20 | View Service and Price List | Service & Price Management | Admin | Admin views the catalog of examination and paraclinical services with their clinic price and insurance price. |
| UC-21 | View Service Detail | Service & Price Management | Admin | Admin views the detailed information of a selected service. |
| UC-22 | Create Service | Service & Price Management | Admin | Admin creates a new service with its department, type, group, clinic price and insurance price. |
| UC-23 | Update Service Information | Service & Price Management | Admin | Admin updates service information other than price (name, group, performing department). |
| UC-24 | Update Price | Service & Price Management | Admin | Admin updates the clinic price or insurance price of a service; the new price applies to orders created afterwards. |
| UC-25 | Activate/Deactivate Service | Service & Price Management | Admin | Admin activates or deactivates a service so that it is shown or hidden in the order list. |
| UC-26 | View Statistic Report | Statistics & Reporting | Admin | Admin views the overall statistics dashboard of the clinic. |
| UC-27 | View Patient Visit Statistics | Statistics & Reporting | Admin | Admin views statistics on the number of patient visits by day, month, visit type, etc. |
| UC-28 | View Doctor Statistics | Statistics & Reporting | Admin | Admin views statistics on the number of patients examined by each doctor. |
| UC-29 | View Department and Room Statistics | Statistics & Reporting | Admin | Admin views statistics on the workload of each department and each room. |
| UC-30 | View Paraclinical Service Statistics | Statistics & Reporting | Admin | Admin views statistics on the number of paraclinical services performed by service type and room. |
| UC-31 | View Revenue Statistics | Statistics & Reporting | Admin | Admin views statistics on collected payments and revenue, including insurance amount and patient amount, over a period of time. |
| UC-32 | View Diagnosis Statistics | Statistics & Reporting | Admin | Admin views the most common diagnoses by ICD-10 code over a selected period. |
| UC-33 | Export Report | Statistics & Reporting | Admin | Admin exports the selected statistics report to a file (Excel/PDF). |
| UC-34 | View Examined Patient List | Statistics & Reporting | Doctor | Doctor views the list of patients they have examined over a selected period. |
| UC-35 | Search Patient | Patient Profile Management | Receptionist | Receptionist searches for a patient by name, phone number, citizen ID or health insurance number. |
| UC-36 | View Patient Information | Patient Profile Management | Receptionist | Receptionist views the administrative information and visit history of a patient. |
| UC-37 | Create New Patient Profile | Patient Profile Management | Receptionist | Receptionist creates a new patient profile for a first-time patient. |
| UC-38 | Update Patient Information | Patient Profile Management | Receptionist | Receptionist updates the administrative information of an existing patient. |
| UC-39 | Update Medical History and Allergies | Patient Profile Management | Doctor | Doctor updates the patient’s medical history and drug allergies during the examination. |
| UC-40 | Register Examination | Patient Reception | Receptionist | Receptionist registers a new visit for a patient: enters the reason for visit and initial symptoms, selects the visit type, enters the health insurance card information if the patient uses insurance, marks the visit as priority if needed, and selects an examination room. The receptionist reviews the summary and confirms; the system issues a queue number. The system also creates a pending examination fee payment. |
| UC-41 | Print Examination Ticket | Patient Reception | Receptionist | Receptionist prints the examination ticket showing the registration code, queue number, examination room and location. For self-paid visits, the ticket also shows the examination fee to be paid at the cashier before the patient can join the room queue. |
| UC-42 | View Room Queue Status | Patient Reception | Receptionist | Receptionist views the number of waiting and in-progress patients of each examination room. |
| UC-43 | View Patient Waiting List | Clinical Examination | Doctor | Doctor views the waiting list of their assigned room after logging in, ordered by priority and queue number. |
| UC-44 | Handle Patient Call | Clinical Examination | Doctor | Doctor calls the next patient from either the examination waiting list or the conclusion waiting list of their room. If the patient is present: a patient waiting for examination starts the examination and is assigned to the doctor; a patient waiting for conclusion proceeds to the conclusion step. If absent, the visit is moved to the skipped list. |
| UC-45 | View Skipped Patient List | Clinical Examination | Doctor | Doctor or Technician views the list of patients of their room who were called but absent, ordered by the time they were skipped. |
| UC-46 | Recall Skipped Patient | Clinical Examination | Doctor | Doctor or Technician calls a patient from the skipped list again; if the patient is present, the examination or service starts, otherwise the patient remains in the skipped list. |
| UC-47 | Perform Clinical Examination | Clinical Examination | Doctor | Doctor records vital signs, symptoms and clinical examination findings. |
| UC-48 | Transfer Patient to Another Room | Clinical Examination | Doctor | Doctor transfers a patient of their room to another examination room, either a waiting patient (e.g. when the room is overloaded) or a patient being examined (when a different specialty is needed). The doctor enters the transfer reason; the patient receives a new queue number in the target room. |
| UC-49 | Order Paraclinical Services | Clinical Examination | Doctor | Doctor selects one or more paraclinical services, reviews the services and the estimated patient cost with the patient, and confirms the order; the system creates a pending payment for the order. A confirmed order cannot be cancelled. |
| UC-50 | Print Paraclinical Order | Clinical Examination | Doctor | Doctor prints the paraclinical order slip showing the services and the location of each paraclinical department. |
| UC-51 | View List Medical Record | Medical Record | Doctor | Doctor views the list of medical records of patients. |
| UC-52 | View Medical Record Detail | Medical Record | Doctor | Doctor views the detail of a medical record, including examination history and paraclinical results. |
| UC-53 | Export Medical Record | Medical Record | Doctor | Doctor exports a medical record to a printable file. |
| UC-54 | View Patients Pending Conclusion | Examination Conclusion | Doctor | Doctor views the patients of their room whose paraclinical results are completed and are waiting for conclusion. |
| UC-55 | View Paraclinical Results | Examination Conclusion | Doctor | Doctor views the paraclinical results recorded for the current visit. |
| UC-56 | Conclude Examination | Examination Conclusion | Doctor | The Doctor concludes the examination after reviewing all paraclinical results, recording the final diagnosis with an ICD-10 code if available and the treatment plan. |
| UC-57 | Print Conclusion | Examination Conclusion | Doctor | Doctor prints the examination conclusion for the patient. |
| UC-58 | Close Medical Record | Examination Conclusion | Doctor | Doctor closes the visit after concluding and prescribing; the visit is marked Completed and can no longer be edited. |
| UC-59 | Prescribe Medication | Examination Conclusion | Doctor | Doctor creates a prescription (drug, quantity, unit, usage instructions) |
| UC-60 | Print Prescription | Examination Conclusion | Doctor | Doctor prints the prescription for the patient. |
| UC-61 | Create Follow-up Appointment | Examination Conclusion | Doctor | Doctor sets a follow-up date and advice for the patient. |
| UC-62 | Print Follow-up Appointment | Examination Conclusion | Doctor | Doctor prints the follow-up appointment slip for the patient. |
| UC-63 | View Waiting Paraclinical List | Paraclinical Service | Technician | Technician views the waiting list of paid paraclinical services of their room |
| UC-64 | View Pending Result List | Paraclinical Service | Technician | Technician views the list of services they have performed but whose results have not been submitted yet, ordered by performed time. |
| UC-65 | Accept Paraclinical Order | Paraclinical Service | Technician | Technician calls the next paid service in their room’s waiting list. If the patient is present, the service is accepted and assigned to the technician; if absent, the service is moved to the skipped list. |
| UC-66 | Perform Paraclinical Order | Paraclinical Service | Technician | Technician performs the accepted service and updates its status. |
| UC-67 | Record Paraclinical Result | Paraclinical Service | Technician | Technician enters, reviews and submits the result (content, conclusion, files) of the service they have performed; a submitted result cannot be edited. |
| UC-68 | Print Paraclinical Result | Paraclinical Service | Technician | Technician prints the paraclinical result for the patient. |
| UC-69 | View Waiting Payment List | Payment | Cashier | Cashier views the list of pending payments automatically generated by the system from service examination registrations or paraclinical service orders. |
| UC-70 | Search Payment | Payment | Cashier | Cashier searches for a payment by receipt number, patient name or registration code. |
| UC-71 | Confirm Payment | Payment | Cashier | Cashier reviews the payment lines, selects the payment method and confirms that the patient has paid. For an examination fee, the patient joins the room queue; for a paraclinical order, the system assigns each service to the least busy room that performs it. |
| UC-72 | Print Payment Receipt | Payment | Cashier | Cashier prints the payment receipt for the patient after the payment is confirmed. |
| UC-73 | View Payment History | Payment | Cashier | Cashier views the history of payments that have been paid or cancelled. |
| UC-74 | Verify Health Insurance | Patient Reception | Receptionist | Receptionist enters the health insurance number and verifies the card validity (simulated connection to the insurance portal). |

Business rules referenced by use cases are listed in [`../business-context.md`](../business-context.md#business-rules).
