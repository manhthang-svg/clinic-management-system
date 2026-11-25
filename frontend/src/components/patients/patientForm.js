// UC-37, UC-38: gia tri form, kiem tra truong va thong bao (backend kiem tra lai)

import { ageOn } from './patientOverview';

export const ADULT_AGE = 18;
export const MAX_AGE_YEARS = 120;
export const MAX_NAME_LENGTH = 100;
// Ho va ten chi gom chu cai (Unicode), khoang trang, dau gach noi va dau nhay don (cho ten dong bao dan toc)
export const NAME_REGEX = /^(?=.*\p{L})[\p{L}\s'-]+$/u;

// Chuan hoa viet hoa chu cai dau (Title Case) cho Ho va ten, thu gon khoang trang thua
export function formatFullName(name) {
    if (!name || typeof name !== 'string') return '';
    return name
        .trim()
        .toLowerCase()
        .replace(/(?:^|[\s'-])\p{L}/gu, (match) => match.toUpperCase())
        .replace(/\s+/g, ' ');
}

// Danh muc cac dan toc Viet Nam cua Tong cuc Thong ke (nso.gov.vn), ma 2 chu so cho dantoc_ma (CV 365).
// Le tan chon ten, o Ma dan toc tu dien theo ten.
export const ETHNICITIES = [
    ['01', 'Kinh'], ['02', 'Tày'], ['03', 'Thái'], ['04', 'Hoa'], ['05', 'Khơ-me'], ['06', 'Mường'],
    ['07', 'Nùng'], ['08', 'HMông'], ['09', 'Dao'], ['10', 'Gia-rai'], ['11', 'Ngái'], ['12', 'Ê-đê'],
    ['13', 'Ba na'], ['14', 'Xơ-Đăng'], ['15', 'Sán Chay'], ['16', 'Cơ-ho'], ['17', 'Chăm'], ['18', 'Sán Dìu'],
    ['19', 'Hrê'], ['20', 'Mnông'], ['21', 'Ra-glai'], ['22', 'Xtiêng'], ['23', 'Bru-Vân Kiều'], ['24', 'Thổ'],
    ['25', 'Giáy'], ['26', 'Cơ-tu'], ['27', 'Gié Triêng'], ['28', 'Mạ'], ['29', 'Khơ-mú'], ['30', 'Co'],
    ['31', 'Tà-ôi'], ['32', 'Chơ-ro'], ['33', 'Kháng'], ['34', 'Xinh-mun'], ['35', 'Hà Nhì'], ['36', 'Chu ru'],
    ['37', 'Lào'], ['38', 'La Chí'], ['39', 'La Ha'], ['40', 'Phù Lá'], ['41', 'La Hủ'], ['42', 'Lự'],
    ['43', 'Lô Lô'], ['44', 'Chứt'], ['45', 'Mảng'], ['46', 'Pà Thẻn'], ['47', 'Co Lao'], ['48', 'Cống'],
    ['49', 'Bố Y'], ['50', 'Si La'], ['51', 'Pu Péo'], ['52', 'Brâu'], ['53', 'Ơ Đu'], ['54', 'Rơ măm'],
    ['55', 'Người nước ngoài'], ['56', 'Không rõ'],
].map(([code, name]) => ({ code, name }));

// ten cu da luu truoc khi dung ten chinh thuc
const ETHNICITY_ALIASES = { Khmer: '05' };

// ma dan toc theo ten, khong co trong danh muc thi tra null
export function ethnicityCode(name) {
    return ETHNICITIES.find((item) => item.name === name)?.code ?? ETHNICITY_ALIASES[name] ?? null;
}

// lua chon cho o Dan toc; ho so cu luu ten ngoai danh muc (vd "Khác") van hien de khong mat du lieu
export function ethnicityOptions(current) {
    const options = ETHNICITIES.map(({ name }) => ({ value: name, label: name }));
    return current && !ETHNICITIES.some((item) => item.name === current) ? [{ value: current, label: current }, ...options] : options;
}

// Ma nghe nghiep theo thanh phan xa hoi (QD 4069/2001/QD-BYT, Phu luc 6), ma cho nghenghiep_ma (CV 365).
export const OCCUPATIONS = [
    ['01', 'Trẻ dưới 6 tuổi đi học, dưới 15 tuổi không đi học'],
    ['02', 'Sinh viên, học sinh'],
    ['03', 'Hưu và trên 60 tuổi'],
    ['04', 'Công nhân'],
    ['05', 'Nông dân'],
    ['06', 'Lực lượng vũ trang'],
    ['07', 'Trí thức'],
    ['08', 'Hành chính, sự nghiệp'],
    ['09', 'Y tế'],
    ['10', 'Dịch vụ'],
    ['11', 'Việt kiều'],
    ['12', 'Ngoại kiều'],
    ['99', 'Khác'],
].map(([code, name]) => ({ code, name }));

// ma nghe nghiep theo ten, khong co trong danh muc thi tra null
export function occupationCode(name) {
    return OCCUPATIONS.find((item) => item.name === name)?.code ?? null;
}

// lua chon cho o Nghe nghiep (khong bat buoc); nghe nghiep cu go tu do van hien de khong mat du lieu
export function occupationOptions(current) {
    const options = [{ value: '', label: 'Chưa chọn' },
        ...OCCUPATIONS.map(({ name }) => ({ value: name, label: name }))];
    return current && !occupationCode(current) ? [options[0], { value: current, label: current }, ...options.slice(1)]
        : options;
}

// ma nghe nghiep "Ngoai kieu": chi khi do moi chon nuoc o o Ngoai kieu
export const FOREIGNER_OCCUPATION_CODE = '12';

// Bang ma mot so nuoc (QD 4069/2001/QD-BYT, Phu luc 7), ma cho ngoaikieu_ma (CV 365).
// Giu ten khop COUNTRIES trong backend CountryCatalog.java.
export const COUNTRIES = [
    ['01', 'Anh'], ['02', 'Brunei'], ['03', 'Campuchia'], ['04', 'Canada'], ['05', 'Cuba'],
    ['06', 'Đài Loan'], ['07', 'Đức'], ['08', 'Hà Lan'], ['09', 'Hàn Quốc'], ['10', 'Hoa Kỳ'],
    ['11', 'Indonesia'], ['12', 'Lào'], ['13', 'Malaysia'], ['14', 'Myanmar'], ['15', 'Nga'],
    ['16', 'Nhật'], ['17', 'Pháp'], ['18', 'Philippines'], ['19', 'Singapore'], ['20', 'Thái Lan'],
    ['21', 'Trung Quốc'], ['99', 'Khác'],
].map(([code, name]) => ({ code, name }));

// ma nuoc theo ten, khong co trong danh muc thi tra null
export function countryCode(name) {
    return COUNTRIES.find((item) => item.name === name)?.code ?? null;
}

// o Ngoai kieu chi mo khi nghe nghiep la Ngoai kieu
export function isForeigner(form) {
    return form.nghenghiepMa === FOREIGNER_OCCUPATION_CODE;
}

// lua chon cho o Ngoai kieu; ten cu ngoai danh muc van hien de nguoi dung thay va chon lai
export function countryOptions(current) {
    const options = [{ value: '', label: 'Chưa chọn' },
        ...COUNTRIES.map(({ name }) => ({ value: name, label: name }))];
    return current && !countryCode(current) ? [options[0], { value: current, label: current }, ...options.slice(1)]
        : options;
}

// khi doi ten dan toc / nghe nghiep / nuoc thi tu dien ma tuong ung
export function withDerivedCodes(previous, name, value) {
    if (name === 'dantoc') return { ...previous, dantoc: value, dantocMa: ethnicityCode(value) ?? previous.dantocMa };
    if (name === 'nghenghiep') {
        const next = { ...previous, nghenghiep: value, nghenghiepMa: value ? occupationCode(value) ?? previous.nghenghiepMa : '' };
        // bo chon Ngoai kieu thi xoa nuoc da chon
        return isForeigner(next) ? next : { ...next, ngoaikieu: '', ngoaikieuMa: '' };
    }
    if (name === 'ngoaikieu') return { ...previous, ngoaikieu: value, ngoaikieuMa: countryCode(value) ?? '' };
    return { ...previous, [name]: value };
}

export const INITIAL_PATIENT_FORM = {
    hoten: '',
    ngaysinh: '',
    gioitinh: '1',
    cccdSo: '',
    hochieuSo: '',
    nghenghiep: '',
    nghenghiepMa: '',
    dantoc: 'Kinh',
    dantocMa: '01',
    ngoaikieu: '',
    ngoaikieuMa: '',
    diachi: '',
    xaphuong: '',
    xaphuongMa: '',
    tinhthanh: '',
    tinhthanhMa: '',
    noilamviec: '',
    sodienthoai: '',
    yeutorh: '',
    nhommau: '',
    hotennguoithan: '',
    nguoithanQuanhe: '',
    diachinguoithan: '',
    sodienthoainguoithan: '',
};

// thong bao theo bang System Messages
export const FIELD_MSG = {
    REQUIRED: 'Trường này là bắt buộc.',
    INVALID_FORMAT: 'Định dạng không hợp lệ.',
    TOO_LONG: 'Vượt quá độ dài tối đa cho phép.',
};
export const MSG = {
    CREATED: 'Tạo hồ sơ bệnh nhân thành công.',
    UPDATED: 'Cập nhật hồ sơ bệnh nhân thành công.',
    // TBD: chua co MSG cho ho so bi nguoi khac sua
    STALE: 'Hồ sơ vừa được người khác cập nhật. Vui lòng tải lại trang để xem dữ liệu mới.',
    NOT_FOUND: 'Không tìm thấy hồ sơ bệnh nhân được yêu cầu.',
    FIX_FIELDS: 'Vui lòng kiểm tra lại các trường được đánh dấu.',
    DISCARD: 'Bạn có các thay đổi chưa được lưu. Bạn có muốn loại bỏ những thay đổi này không?',
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
    SESSION_EXPIRED: 'Phiên đăng nhập của bạn đã hết hạn. Vui lòng đăng nhập lại.',
    SYSTEM_ERROR: 'Đã xảy ra lỗi. Vui lòng thử lại hoặc liên hệ quản trị viên.',
};

// MSG57: canh bao ho so co the trung
export const msg57 = (count) =>
    `Đã tồn tại ${count} bệnh nhân có cùng họ tên và ngày sinh. Vui lòng chọn một hồ sơ hoặc tạo hồ sơ mới.`;

// MSG19: CCCD / ho chieu da thuoc ho so khac
export const msg19 = (field, code, name) =>
    `Bệnh nhân có số ${field === 'hochieuSo' ? 'hộ chiếu' : 'CCCD'} này đã tồn tại (${code} – ${name}).`;

// tinh tuoi tu ngay sinh tren form, sai ngay thi tra null
export function formAge(form, today) {
    return /^\d{4}-\d{2}-\d{2}$/.test(form.ngaysinh) ? ageOn(form.ngaysinh, today) : null;
}

// kiem tra form, tra ve { truong: loi } (rong la hop le)
export function validatePatientForm(form, today) {
    const errors = {};
    const text = (field) => (form[field] ?? '').trim();
    const require = (field) => { if (!text(field)) errors[field] = FIELD_MSG.REQUIRED; };

    require('hoten');
    if (text('hoten').length > MAX_NAME_LENGTH) errors.hoten = FIELD_MSG.TOO_LONG;
    else if (text('hoten') && !NAME_REGEX.test(text('hoten'))) errors.hoten = FIELD_MSG.INVALID_FORMAT;
    require('diachi');
    require('dantoc');
    // ma dan toc khong co o nhap rieng: thieu ma thi bao o Dan toc
    if (!errors.dantoc && !text('dantocMa')) errors.dantoc = FIELD_MSG.REQUIRED;
    // Ngoai kieu: bat buoc chon nuoc trong danh muc; nguoi khong phai ngoai kieu thi de trong
    if (isForeigner(form)) {
        if (!text('ngoaikieu')) errors.ngoaikieu = FIELD_MSG.REQUIRED;
        else if (!countryCode(text('ngoaikieu'))) errors.ngoaikieu = FIELD_MSG.INVALID_FORMAT;
    } else if (text('ngoaikieu') || text('ngoaikieuMa')) {
        errors.ngoaikieu = FIELD_MSG.INVALID_FORMAT;
    }

    const age = formAge(form, today);
    if (!form.ngaysinh) errors.ngaysinh = FIELD_MSG.REQUIRED;
    else if (age === null || form.ngaysinh > today || age > MAX_AGE_YEARS) errors.ngaysinh = FIELD_MSG.INVALID_FORMAT;

    if (text('cccdSo') && !/^\d{12}$/.test(text('cccdSo'))) errors.cccdSo = FIELD_MSG.INVALID_FORMAT;
    if (text('hochieuSo') && !/^[A-Za-z0-9]{1,15}$/.test(text('hochieuSo'))) errors.hochieuSo = FIELD_MSG.INVALID_FORMAT;
    if (text('sodienthoai') && !/^\d{10}$/.test(text('sodienthoai'))) errors.sodienthoai = FIELD_MSG.INVALID_FORMAT;
    if (text('sodienthoainguoithan') && !/^\d{10}$/.test(text('sodienthoainguoithan'))) {
        errors.sodienthoainguoithan = FIELD_MSG.INVALID_FORMAT;
    }

    if (age !== null && age >= ADULT_AGE && !text('sodienthoai')) errors.sodienthoai = FIELD_MSG.REQUIRED;
    if (age !== null && age < ADULT_AGE) {
        require('hotennguoithan');
        require('nguoithanQuanhe');
        require('sodienthoainguoithan');
    }
    if (text('hotennguoithan')) {
        if (text('hotennguoithan').length > 255) errors.hotennguoithan = FIELD_MSG.TOO_LONG;
        else if (!NAME_REGEX.test(text('hotennguoithan'))) errors.hotennguoithan = FIELD_MSG.INVALID_FORMAT;
    }
    return errors;
}

// tao body gui len: bo khoang trang, truong rong thanh null, chuan hoa Title Case ho ten
export function buildPatientPayload(form) {
    const payload = {};
    Object.keys(INITIAL_PATIENT_FORM).forEach((field) => {
        let value = (form[field] ?? '').trim();
        if ((field === 'hoten' || field === 'hotennguoithan') && value) {
            value = formatFullName(value);
        }
        payload[field] = value || null;
    });
    payload.gioitinh = Number(form.gioitinh);
    return payload;
}

// UC-38: doi ho so da luu thanh gia tri form
export function toFormValues(patient) {
    return Object.fromEntries(Object.keys(INITIAL_PATIENT_FORM).map((field) => {
        const value = patient?.[field];
        return [field, value === null || value === undefined ? '' : String(value)];
    }));
}

// o ma chi doc: loi cua ma hien o o ten tuong ung
const CODE_FIELD_OWNER = { dantocMa: 'dantoc', nghenghiepMa: 'nghenghiep', ngoaikieuMa: 'ngoaikieu' };

// doi ma loi backend thanh loi tung truong
export function serverFieldErrors(details) {
    return Object.fromEntries(Object.entries(details ?? {})
        .filter(([field]) => field in INITIAL_PATIENT_FORM)
        .map(([field, code]) => [CODE_FIELD_OWNER[field] ?? field, FIELD_MSG[code] ?? FIELD_MSG.INVALID_FORMAT]));
}

// form da bi sua so voi ban dau chua
export function isDirty(form, initial) {
    return Object.keys(INITIAL_PATIENT_FORM).some((field) => (form[field] ?? '') !== (initial[field] ?? ''));
}

// thong bao loi chung (khong thuoc truong nao)
export function createErrorMessage(error) {
    if (error?.status === 401) return MSG.SESSION_EXPIRED;
    if (error?.status === 403) return MSG.FORBIDDEN;
    if (error?.errorResponse?.code === 'REQUEST_VALIDATION_FAILED') return MSG.FIX_FIELDS;
    return MSG.SYSTEM_ERROR;
}
