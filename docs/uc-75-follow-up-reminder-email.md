# UC-75 — Follow-up reminder email

**Status:** Implemented 24/11/2026 at the product owner's request (AnNT). **TBD:** Report 3 and `usecases.xlsx` still
list UC-75 as "Reassign Waiting Patients" (earlier "Sign Document"); the team must record the new number or renumber.
Every rule below is an **ASSUMPTION** until the team's decision record.

**Sources:** [`uc-61-follow-up-appointment.md`](uc-61-follow-up-appointment.md) (the appointment), TT 01/2025/TT-BYT
Phụ lục V (the "đến trước ngày hẹn nếu có dấu hiệu bất thường" sentence), [`security.md`](security.md) (privacy of
logs), `patients.email` (optional, at most 100 characters, validated by UC-37/UC-38).

## Description

The day before a follow-up appointment, the system emails the patient a reminder of the date, so fewer patients miss it
(`MISSED`, BR-34). Sending is automatic; there is no button (product owner, 24/11/2026). The product owner requires a
free solution, in development and once deployed: Spring Boot Mail over SMTP, Mailpit locally and a personal Gmail
account with an App Password in a deployment. No paid email service is used.

## Business rules

- **BR-75-01 Who.** Each appointment in status `SCHEDULED` whose patient has a non-blank email address. Patients
  without an email address get no reminder; the "Hẹn tái khám" tab says so (MSG-R3).
- **BR-75-02 When.** `FOLLOW_UP_REMINDER_DAYS_BEFORE` days (default 1) before `follow_up_date`, in clinic time
  (Asia/Ho_Chi_Minh). The job runs at 08:00, 14:00 and 20:00 (`FOLLOW_UP_REMINDER_CRON`); a send that failed is retried
  by the next run of the same day.
- **BR-75-03 Once.** `follow_up_appointments.reminder_sent_at` (V62) is set when the email is accepted by the SMTP
  server; an appointment with it set is never reminded again. A cancelled, used or missed appointment is not reminded.
  An appointment rescheduled after its reminder is not reminded again (**TBD**).
- **BR-75-04 Content.** Vietnamese plain text: patient name, follow-up date, slip number, Doctor, clinic name, address
  and phone, the Phụ lục V sentence on coming earlier with abnormal symptoms, and "Đây là email tự động". It carries
  **no diagnosis and no advice**: email is not a protected channel; they stay on the paper slip (UC-62).
- **BR-75-05 Audit (BR-42).** One `audit_logs` row per email sent: action `UPDATE`, entity `follow_up_appointments`,
  no actor (system task), `sovaovien` of the visit, values `reminderSentAt`, `channel` = `EMAIL`, `followUpDate`. The
  email address is never written to the audit trail or the application log; a failure logs only the appointment id.
- **BR-75-06 Off by default.** `FOLLOW_UP_REMINDER_ENABLED` is `false` unless set, so no machine sends email without
  configured SMTP. `docker compose` turns it on with its Mailpit.

## Flow

1. The job starts at a scheduled time and lists the due appointments (BR-75-01, BR-75-02).
2. For each one, in its own transaction, it locks the row and checks again that it is `SCHEDULED`, not reminded, of the
   due date, and that the patient still has an email address; otherwise it skips it.
3. It sends the email (BR-75-04), sets `reminder_sent_at` and writes the audit row (BR-75-05).
4. **EF-01: the SMTP server refuses or cannot be reached.** The transaction rolls back; nothing is recorded; the next
   run retries. One failure does not stop the other reminders.

## Screen (UC-61 "Hẹn tái khám" tab)

`GET /api/v1/follow-up-appointments/visits/{visitId}` also returns `patientHasEmail`, `reminderDaysBefore` (null when
the job is off) and `appointment.reminderSentAt`. Under the saved appointment the tab shows one line:

| ID | When | Text |
| --- | --- | --- |
| MSG-R1 | Scheduled, patient has email, job on | Hệ thống sẽ tự động gửi email nhắc lịch cho bệnh nhân trước ngày tái khám {n} ngày. |
| MSG-R2 | Reminder sent | Đã gửi email nhắc lịch tái khám cho bệnh nhân lúc {HH:mm dd/MM/yyyy}. |
| MSG-R3 | Scheduled, no email | Bệnh nhân chưa có email nên sẽ không nhận được email nhắc lịch tái khám. |

Nothing is shown before the appointment is saved, for an appointment no longer scheduled and not reminded, or when the
job is off. Message numbers are **TBD**.

## Configuration

See [README — Follow-up reminder emails](../README.md#follow-up-reminder-emails-uc-75). Gmail's free limit (about 500
recipients a day) is well above the clinic's daily follow-ups. Elastic Beanstalk blocks only port 25, so Gmail on 587
works there.
