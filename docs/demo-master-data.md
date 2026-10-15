# Demo Master Data — Iteration 1

## Document Authority

This is the **demo** reference data for development and demonstrations (I1-D01, I1-D03 in [`iteration-1-decisions.md`](iteration-1-decisions.md)). It is not real clinic data, and the prices are illustrative values, not an approved price list. It is never loaded into production; production reference data waits for an owner (I1-D02).

It is loaded by `backend/src/main/resources/db/dev-seed/V1000__demo_master_data.sql`, a Flyway location that only the `dev` profile enables. Column names follow [`iteration-1-schema.dbml`](iteration-1-schema.dbml). Fixed UUIDs in the seed use the UUIDv7 layout so they sort like application-generated IDs.

The clinic layout starts from the seed suggested by Report 3 UC-12, without Pediatrics and Obstetrics (team decision, 15/10/2026): 5 examination rooms (4 Internal Medicine: Musculoskeletal, Endocrinology, Gastroenterology, General Internal; 1 Surgery). Paraclinical departments and service groups follow the examples in Report 3 UC-17. Each role has 3 demo accounts.

Rows marked **Test** exist to exercise an alternate or error flow; the "Purpose" column says which.

Demo hospital name (printed on tickets, R3 UC-41): **Bệnh viện Đa khoa Tư nhân METPH**.

## Departments

| # | Name | Type | Description |
| --- | --- | --- | --- |
| D1 | Khoa Nội | CLINICAL | Khám và điều trị nội khoa: cơ xương khớp, nội tiết, tiêu hóa, nội tổng quát |
| D2 | Khoa Ngoại | CLINICAL | Khám ngoại khoa tổng quát |
| D3 | Khoa Xét nghiệm | PARACLINICAL | Xét nghiệm huyết học, sinh hóa, vi sinh |
| D4 | Khoa Chẩn đoán hình ảnh | PARACLINICAL | Siêu âm, X-quang, CT, MRI |
| D5 | Khoa Thăm dò chức năng | PARACLINICAL | Nội soi, điện tim |

## Service Groups

| Code | Name | Type |
| --- | --- | --- |
| HEMATOLOGY | Huyết học | LAB |
| BIOCHEMISTRY | Sinh hóa | LAB |
| MICROBIOLOGY | Vi sinh | LAB |
| ULTRASOUND | Siêu âm | IMAGING |
| XRAY | X-quang | IMAGING |
| CT | Chụp cắt lớp vi tính (CT) | IMAGING |
| MRI | Chụp cộng hưởng từ (MRI) | IMAGING |
| ENDOSCOPY | Nội soi | FUNCTIONAL |
| ECG | Điện tim | FUNCTIONAL |

## Rooms

| Code | Name | Department | Type | Service group | Location | Status | Purpose |
| --- | --- | --- | --- | --- | --- | --- | --- |
| PK-NOI-CXK | Phòng khám Cơ xương khớp | D1 Khoa Nội | EXAMINATION | — | Tòa A - Tầng 1 - P.101 | ACTIVE | **Test:** no doctor — greyed out at reception (R3 UC-42 AF-01, UC-16 AF-02) |
| PK-NOI-NT | Phòng khám Nội tiết | D1 Khoa Nội | EXAMINATION | — | Tòa A - Tầng 1 - P.102 | ACTIVE | |
| PK-NOI-TH | Phòng khám Tiêu hóa | D1 Khoa Nội | EXAMINATION | — | Tòa A - Tầng 1 - P.103 | ACTIVE | |
| PK-NOI-TQ | Phòng khám Nội tổng quát | D1 Khoa Nội | EXAMINATION | — | Tòa A - Tầng 1 - P.104 | ACTIVE | |
| PK-NGOAI | Phòng khám Ngoại | D2 Khoa Ngoại | EXAMINATION | — | Tòa A - Tầng 2 - P.201 | ACTIVE | |
| PK-NOI-TQ-2 | Phòng khám Nội tổng quát 2 | D1 Khoa Nội | EXAMINATION | — | Tòa A - Tầng 1 - P.105 | INACTIVE | **Test:** inactive room — not offered at reception (BR-07), can be activated (UC-19) |
| XN-HH | Phòng Xét nghiệm Huyết học | D3 | PARACLINICAL | HEMATOLOGY | Tòa B - Tầng 1 - P.B101 | ACTIVE | |
| XN-SH | Phòng Xét nghiệm Sinh hóa | D3 | PARACLINICAL | BIOCHEMISTRY | Tòa B - Tầng 1 - P.B102 | ACTIVE | |
| XN-VS | Phòng Xét nghiệm Vi sinh | D3 | PARACLINICAL | MICROBIOLOGY | Tòa B - Tầng 1 - P.B103 | ACTIVE | |
| CDHA-SA-1 | Phòng Siêu âm 1 | D4 | PARACLINICAL | ULTRASOUND | Tòa B - Tầng 2 - P.B201 | ACTIVE | |
| CDHA-SA-2 | Phòng Siêu âm 2 | D4 | PARACLINICAL | ULTRASOUND | Tòa B - Tầng 2 - P.B202 | ACTIVE | Two rooms of one group, for least-busy routing later (BR-28) |
| CDHA-XQ | Phòng X-quang | D4 | PARACLINICAL | XRAY | Tòa B - Tầng 2 - P.B203 | ACTIVE | |
| CDHA-CT | Phòng CT | D4 | PARACLINICAL | CT | Tòa B - Tầng 2 - P.B204 | ACTIVE | |
| CDHA-MRI | Phòng MRI | D4 | PARACLINICAL | MRI | Tòa B - Tầng 2 - P.B205 | ACTIVE | |
| TDCN-NS | Phòng Nội soi | D5 | PARACLINICAL | ENDOSCOPY | Tòa B - Tầng 3 - P.B301 | ACTIVE | |
| TDCN-DT | Phòng Điện tim | D5 | PARACLINICAL | ECG | Tòa B - Tầng 3 - P.B302 | ACTIVE | |

## Services

Prices in VND. "—" in the insurance price means not covered by insurance (I1-D22).

| Code | Name | Type | Department | Group | Clinic price | Insurance price | Status | Purpose |
| --- | --- | --- | --- | --- | ---: | ---: | --- | --- |
| KB-001 | Khám bệnh | EXAMINATION | — | — | 150,000 | 50,600 | ACTIVE | The one shared examination service (I1-D06) |
| XN-HH-001 | Tổng phân tích tế bào máu ngoại vi | LAB | D3 | HEMATOLOGY | 90,000 | 46,200 | ACTIVE | |
| XN-HH-002 | Đông máu cơ bản (PT, APTT) | LAB | D3 | HEMATOLOGY | 120,000 | 63,000 | ACTIVE | |
| XN-SH-001 | Định lượng Glucose máu | LAB | D3 | BIOCHEMISTRY | 40,000 | 21,500 | ACTIVE | |
| XN-SH-002 | Định lượng HbA1c | LAB | D3 | BIOCHEMISTRY | 150,000 | 101,000 | ACTIVE | |
| XN-SH-003 | Bộ mỡ máu (Cholesterol, Triglycerid, HDL-C, LDL-C) | LAB | D3 | BIOCHEMISTRY | 160,000 | 107,000 | ACTIVE | |
| XN-SH-004 | Đo hoạt độ AST, ALT | LAB | D3 | BIOCHEMISTRY | 80,000 | 43,000 | ACTIVE | |
| XN-SH-005 | Tổng phân tích nước tiểu | LAB | D3 | BIOCHEMISTRY | 60,000 | 27,400 | ACTIVE | |
| XN-VS-001 | Test nhanh Helicobacter pylori | LAB | D3 | MICROBIOLOGY | 120,000 | — | ACTIVE | **Test:** uncovered service, charged at the clinic price (I1-D23 rule 1) |
| CDHA-SA-001 | Siêu âm ổ bụng tổng quát | IMAGING | D4 | ULTRASOUND | 200,000 | 43,900 | ACTIVE | |
| CDHA-SA-002 | Siêu âm tuyến giáp | IMAGING | D4 | ULTRASOUND | 200,000 | 43,900 | ACTIVE | |
| CDHA-XQ-001 | Chụp X-quang ngực thẳng | IMAGING | D4 | XRAY | 150,000 | 65,400 | ACTIVE | |
| CDHA-XQ-002 | Chụp X-quang cột sống thắt lưng | IMAGING | D4 | XRAY | 180,000 | 65,400 | INACTIVE | **Test:** inactive service, hidden from new orders (BR-10) |
| CDHA-CT-001 | Chụp CT sọ não không tiêm thuốc cản quang | IMAGING | D4 | CT | 900,000 | 522,000 | ACTIVE | |
| CDHA-MRI-001 | Chụp MRI cột sống thắt lưng | IMAGING | D4 | MRI | 2,500,000 | 1,309,000 | ACTIVE | |
| TDCN-NS-001 | Nội soi dạ dày - tá tràng | FUNCTIONAL | D5 | ENDOSCOPY | 500,000 | 244,000 | ACTIVE | |
| TDCN-DT-001 | Điện tâm đồ | FUNCTIONAL | D5 | ECG | 80,000 | 32,800 | ACTIVE | |

## Staff Accounts

Three accounts per role. The bootstrap Admin is **not** seeded; it is created by the bootstrap (AQ-10, AQ-17). Every account has a fictitious phone number and the email `<username>@clinic.example` (reserved domain).

| Username | Full name | Role | Academic title | Gender | Room | Purpose |
| --- | --- | --- | --- | --- | --- | --- |
| bs.tranvanan | Trần Văn An | DOCTOR | BSCKII | MALE | PK-NOI-TQ | |
| bs.lethibinh | Lê Thị Bình | DOCTOR | ThS.BS | FEMALE | PK-NOI-NT | |
| bs.phamminhchau | Phạm Minh Châu | DOCTOR | BSCKI | MALE | — | **Test:** doctor without a room — MSG50 after login (R3 UC-01 AF-04) |
| ktv.nguyenvanbao | Nguyễn Văn Bảo | TECHNICIAN | — | MALE | XN-SH | |
| ktv.tranthicuc | Trần Thị Cúc | TECHNICIAN | — | FEMALE | CDHA-SA-1 | |
| ktv.lethidao | Lê Thị Đào | TECHNICIAN | — | FEMALE | TDCN-DT | |
| lt.nguyenthihong | Nguyễn Thị Hồng | RECEPTIONIST | — | FEMALE | — | |
| lt.tranvanquang | Trần Văn Quang | RECEPTIONIST | — | MALE | — | |
| lt.lethisuong | Lê Thị Sương | RECEPTIONIST | — | FEMALE | — | |
| tn.phamthithu | Phạm Thị Thu | CASHIER | — | FEMALE | — | |
| tn.hoangvantuan | Hoàng Văn Tuấn | CASHIER | — | MALE | — | |
| tn.vuthiyen | Vũ Thị Yến | CASHIER | — | FEMALE | — | |

All demo accounts are `ACTIVE`. A locked account is not seeded because the lock details must name the locking Admin, which does not exist when Flyway runs; lock one through UC-10 instead. Examination rooms PK-NOI-CXK, PK-NOI-TH, and PK-NGOAI have no doctor, so reception shows them greyed out until a doctor is assigned.

Staff-room assignments made by the seed have no assigning Admin (`assigned_by_account_id` is null, meaning "loaded by a data migration").

### Demo Password

No password or hash is committed (AGENTS.md: no hardcoded secrets). The seed stores the placeholder hash `{bcrypt}dev-seed-password-not-set`, which never matches a password. At startup with the `dev` profile, if `DEV_SEED_PASSWORD` is set, every account that still has the placeholder gets that password, hashed with BCrypt, and does not have to change it at first login. Without the variable the demo accounts cannot log in. The password must satisfy the password policy (8–64 characters).

## Simulated Insurance Cards (UC-74)

Card format: 2-letter group code + benefit code (third character) + 2-digit province code + 10 digits = 15 characters. Coverage rates are what the simulated portal returns (I1-D18).

| Card number | Full name | Date of birth | Benefit code | Coverage | Valid from | Valid to | Status | Purpose |
| --- | --- | --- | --- | ---: | --- | --- | --- | --- |
| CC1790000000001 | Nguyễn Văn Bình | 1950-03-12 | 1 | 100% | 2026-01-01 | 2026-12-31 | VALID | 100%: insurance price 50,600 → patient pays 0 → fee-exempt (I1-D23 rule 6) |
| HN2790000000002 | Trần Thị Cẩm | 1985-07-25 | 2 | 100% | 2026-01-01 | 2026-12-31 | VALID | 100% coverage |
| HT3790000000003 | Lê Văn Dũng | 1958-11-02 | 3 | 95% | 2026-01-01 | 2026-12-31 | VALID | 95%: insurance 48,070, patient 2,530 |
| DN4790000000004 | Phạm Thị Hoa | 1990-05-18 | 4 | 80% | 2026-01-01 | 2026-12-31 | VALID | 80%: insurance 40,480, patient 10,120 |
| TE1790000000005 | Hoàng Minh Khôi | 2020-09-09 | 1 | 100% | 2026-01-01 | 2026-12-31 | VALID | Child card; patient has no citizen ID |
| GD4790000000006 | Vũ Thị Lan | 1978-02-14 | 4 | 80% | 2025-01-01 | 2025-12-31 | VALID | **Test:** expired → self-paid (R3 UC-74 EF-02) |
| DN4790000000007 | Đặng Văn Long | 1982-08-30 | 4 | 80% | 2026-01-01 | 2026-12-31 | REVOKED | **Test:** revoked → self-paid (EF-02) |
| HS4790000000008 | Bùi Thị Mỹ | 2010-04-21 | 4 | 80% | 2026-01-01 | 2026-12-31 | VALID | **Test:** portal name differs from the profile → MSG18 (EF-03) |
| HN5790000000009 | Đỗ Văn Nghĩa | 1965-12-01 | 5 | 100% | 2026-01-01 | 2026-12-31 | VALID | Benefit code 5 |

Any well-formed card number not in this table is "not found" (EF-02). With these demo prices (multiples of 100) and rates of 80%, 95%, or 100%, every insurance amount is a whole number, so the rounding rule (I1-D23 rule 2) never applies to demo data. Rounding is covered by unit tests, for example 21,510 × 95% = 20,434.5 → 20,435.

## Demo Patients

Patients are not master data, but a few profiles make search and registration testable from day one. They are created by `lt.nguyenthihong`.

| Patient code | Full name | Date of birth | Gender | Citizen ID | Phone | Guardian | Card in visits | Purpose |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| BN00000001 | Nguyễn Văn Bình | 1950-03-12 | MALE | 079050000001 | 0901000001 | — | CC1790000000001 | Elderly → priority `ELDERLY`; fee-exempt |
| BN00000002 | Trần Thị Cẩm | 1985-07-25 | FEMALE | 079185000002 | 0901000002 | — | HN2790000000002 | |
| BN00000003 | Lê Văn Dũng | 1958-11-02 | MALE | 079058000003 | 0901000003 | — | HT3790000000003 | 95% coverage |
| BN00000004 | Phạm Thị Hoa | 1990-05-18 | FEMALE | 079190000004 | 0901000004 | — | DN4790000000004 | 80% coverage |
| BN00000005 | Hoàng Minh Khôi | 2020-09-09 | MALE | — | — | Hoàng Văn Kiên, 0901000099, Bố | TE1790000000005 | **Test:** under 14 without citizen ID; under 18 with guardian (I1-D12) |
| BN00000006 | Vũ Thị Lan | 1978-02-14 | FEMALE | 079178000006 | 0901000006 | — | GD4790000000006 | Expired card |
| BN00000007 | Bùi Thị My | 2010-04-21 | FEMALE | 079310000008 | — | Bùi Văn Nam, 0901000098, Bố | HS4790000000008 | **Test:** name mismatch with card (Mỹ vs My); under 18, phone optional |
| BN00000008 | Nguyễn Thị Thảo | 1995-10-10 | FEMALE | 079195000012 | 0901000012 | — | — | Self-paid patient |

To trigger the duplicate warning (I1-D19), create "Nguyễn Văn Bình", born 1950-03-12, without a citizen ID, in UC-37.

All patients live at the fictitious address "12 Đường Số 1, Phường Bến Thành, TP. Hồ Chí Minh". Ethnicity `Kinh`, nationality `Viet Nam`. Citizen IDs are fictitious and follow the 12-digit format only.
