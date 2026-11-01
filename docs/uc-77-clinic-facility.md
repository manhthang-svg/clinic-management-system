# UC-77 Update Hospital Information

**Status:** **ASSUMPTION**. UC-77 is not yet in `docs/source/usecases.xlsx` or the SRS; review with the team before treating this as source.

**Actor:** Admin. **API:** `GET` and `PUT /api/v1/clinic-facility`.

The clinic does not manage prescriptions, so the prescription interconnection code (`ma_lienthong_donthuoc`) is not part of this use case. The column stays in the schema.

## Fields

| Field | API name | Required | Rule | Printed |
| --- | --- | --- | --- | --- |
| Facility code (mã cơ sở KBCB) | `maCskcb` | Yes | Exactly 5 digits. The first two digits are **not** checked against the current province list (BR-77-02). | Yes |
| Name | `tenCskcb` | Yes | 1–255 characters, as written on the operating licence. | Yes |
| Address | `diachi` | Yes | 1–1024 characters. | Yes |
| Phone | `sodienthoai` | Yes | Mobile `0[35789]` + 8 digits, landline `02` + 9 digits, or `1800`/`1900` + 4 or 6 digits. Digits only; the client removes spaces and dots. | Yes |
| Operating licence number | `soGiayPhepHoatDong` | Yes | 1–100 characters, free text (each province uses its own format). | Yes |
| Organization form | `hinhThucToChuc` | Yes | One value of `FacilityOrganizationForm`. | Yes |

Leading and trailing spaces are removed from the name, address, and licence number.

## What can change

| Field | Can change | Condition |
| --- | --- | --- |
| Facility code | Only before the first visit | Rejected with `409 FACILITY_CODE_LOCKED` once any visit exists. |
| Name | Always | — |
| Address | Always | The client reminds the Admin to check the licence number. |
| Phone | Always | — |
| Operating licence number | Always | — |
| Organization form | Always | The client reminds the Admin to check the licence number. |
| `version`, `updatedAt` | Never | Managed by the system. |

## Business rules

- **BR-77-01.** The facility code, name, address, operating licence number and organization form are entered as issued by the competent authority (QĐ 384/QĐ-BYT; Luật Khám bệnh, chữa bệnh 2023; NĐ 96/2023/NĐ-CP). The system does not verify them with the authority.
- **BR-77-02.** The facility code is exactly 5 digits. It can change only while no visit exists. From the first visit, the code is locked, because it is printed on documents and identifies the facility in examination data. Codes of the Ministry of Defence (`97xxx`) and the Ministry of Public Security (`98xxx`) do not follow the province code, and facilities kept their codes after the 2025 province merger, so the first two digits are not validated.
- **BR-77-03.** The other fields can change at any time. When the address or organization form changes, the client reminds the Admin to check the operating licence number, because such changes usually require a new licence. The reminder does not block saving, since an address can also change only because the administrative unit was renamed.
- **BR-77-04.** Changes are not retroactive. Documents already printed keep the information they were printed with. Documents with legal value that can be reprinted store a copy of the facility information when issued; that belongs to their own use cases.
- **BR-77-05.** Every save that changes something writes one `audit_logs` row (`entity_type = clinic_facility`, action `CREATE` or `UPDATE`) with the actor, time, changed fields, old values and new values. A save that changes nothing writes nothing.

**ASSUMPTION:** If the authority issues a new facility code after visits exist, the change is outside UC-77 and is made by a technical procedure that is recorded in the audit trail.

## Flows

**Main flow**

1. The Admin opens the facility page. The system shows the current information, or an empty form when the facility has not been declared (`configured = false`).
2. The Admin edits the information and saves.
3. The system validates the data, saves it, writes the audit row and returns the saved information.

**Exception flows**

| Code | Condition | Response |
| --- | --- | --- |
| EF-01 | A field is missing or invalid | `400 REQUEST_VALIDATION_FAILED` with the field names |
| EF-02 | The facility code changes after the first visit | `409 FACILITY_CODE_LOCKED` |
| EF-03 | `version` does not match, or a first declaration sends a `version`, or two first declarations race | `409 CONCURRENT_MODIFICATION`; the client reloads |
| EF-04 | The caller is not an Admin, or must change the password first | `403` |

## Organization forms

From Điều 39 NĐ 96/2023/NĐ-CP, limited to forms that examine outpatients (**ASSUMPTION**: stand-alone paraclinical, dental-prosthesis and rehabilitation facilities are out of scope).

| Value | Form |
| --- | --- |
| `BENH_VIEN_DA_KHOA` | Bệnh viện đa khoa |
| `BENH_VIEN_CHUYEN_KHOA` | Bệnh viện chuyên khoa |
| `BENH_VIEN_Y_HOC_CO_TRUYEN` | Bệnh viện y học cổ truyền |
| `BENH_VIEN_RANG_HAM_MAT` | Bệnh viện răng hàm mặt |
| `PHONG_KHAM_DA_KHOA` | Phòng khám đa khoa |
| `PHONG_KHAM_CHUYEN_KHOA` | Phòng khám chuyên khoa |
| `PHONG_KHAM_LIEN_CHUYEN_KHOA` | Phòng khám liên chuyên khoa |
| `PHONG_KHAM_BAC_SY_Y_KHOA` | Phòng khám bác sỹ y khoa |
| `PHONG_KHAM_Y_HOC_CO_TRUYEN` | Phòng khám y học cổ truyền |
| `PHONG_KHAM_RANG_HAM_MAT` | Phòng khám răng hàm mặt |
| `PHONG_KHAM_DINH_DUONG` | Phòng khám dinh dưỡng |
| `PHONG_KHAM_Y_SY_DA_KHOA` | Phòng khám y sỹ đa khoa |
| `TRAM_Y_TE` | Trạm y tế |
| `NHA_HO_SINH` | Nhà hộ sinh |
| `PHONG_CHAN_TRI_Y_HOC_CO_TRUYEN` | Phòng chẩn trị y học cổ truyền |
| `CO_SO_Y_HOC_GIA_DINH` | Cơ sở khám bệnh, chữa bệnh y học gia đình |

## Known limitation

The check "no visit exists" and the first visit are not serialized. A code change saved at the same moment as the very first visit could slip through. This can only happen during initial setup and is accepted.
