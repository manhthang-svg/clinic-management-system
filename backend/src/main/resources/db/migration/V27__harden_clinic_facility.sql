-- UC-77 Update Hospital Information.
-- The facility code is exactly five digits (QĐ 384/QĐ-BYT); the organization form is one of the
-- forms of Điều 39 NĐ 96/2023/NĐ-CP that examine outpatients (enum FacilityOrganizationForm).

ALTER TABLE clinic_facility DROP CONSTRAINT ck_clinic_facility_ma_cskcb;

ALTER TABLE clinic_facility
    ADD CONSTRAINT ck_clinic_facility_ma_cskcb CHECK (ma_cskcb ~ '^[0-9]{5}$');

ALTER TABLE clinic_facility
    ADD CONSTRAINT ck_clinic_facility_hinh_thuc_to_chuc CHECK (hinh_thuc_to_chuc IN (
        'BENH_VIEN_DA_KHOA',
        'BENH_VIEN_CHUYEN_KHOA',
        'BENH_VIEN_Y_HOC_CO_TRUYEN',
        'BENH_VIEN_RANG_HAM_MAT',
        'PHONG_KHAM_DA_KHOA',
        'PHONG_KHAM_CHUYEN_KHOA',
        'PHONG_KHAM_LIEN_CHUYEN_KHOA',
        'PHONG_KHAM_BAC_SY_Y_KHOA',
        'PHONG_KHAM_Y_HOC_CO_TRUYEN',
        'PHONG_KHAM_RANG_HAM_MAT',
        'PHONG_KHAM_DINH_DUONG',
        'PHONG_KHAM_Y_SY_DA_KHOA',
        'TRAM_Y_TE',
        'NHA_HO_SINH',
        'PHONG_CHAN_TRI_Y_HOC_CO_TRUYEN',
        'CO_SO_Y_HOC_GIA_DINH'
    ));
