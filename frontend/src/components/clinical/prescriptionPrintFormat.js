/** UC-60 Print Prescription: wording and formatting of the printed form (BR-59-13, docs/uc-59-e-prescription.md). */
import { prescriptionError } from './prescriptionForm';

const ZONE = 'Asia/Ho_Chi_Minh';

export const PRINT_MSG = {
    NOT_PRINTABLE: 'Gửi đơn thành công trước khi in.', // MSG112 (ASSUMPTION wording)
    // Luật GDĐT Điều 12 khoản 2: the mark of a paper copy converted from the data message (ASSUMPTION wording, TBD).
    MARK: (code, facility) => `Bản in từ đơn thuốc điện tử ${code} lưu tại ${facility}`,
    PRINTED_BY: (name, time) => `; người in ${name}, ${time}`,
};

/** BR-59-13: "{hoatchat} ({tenthuocvattu}) {hamluong}" for every line until the catalog marks multi-ingredient products. */
export function medicineLabel(line) {
    const ingredient = line.activeIngredient?.trim();
    const name = line.name?.trim();
    const head = ingredient && name && ingredient !== name ? `${ingredient} (${name})` : (ingredient || name || '');
    const strength = line.strength?.trim();
    return strength && !head.includes(strength) ? `${head} ${strength}` : head;
}

/** TT 26 Điều 6 khoản 7a: a quantity below 10 is written with a leading zero. */
export function quantityText(quantity) {
    const value = Number(quantity);
    if (!Number.isFinite(value)) return String(quantity ?? '');
    return Number.isInteger(value) && value >= 0 && value < 10 ? `0${value}` : value.toLocaleString('vi-VN');
}

function parts(value, options) {
    const formatted = new Intl.DateTimeFormat('en-GB', { timeZone: ZONE, ...options }).formatToParts(new Date(value));
    return (type) => formatted.find((item) => item.type === type)?.value;
}

/** "05/11/2026" for an ISO date (yyyy-mm-dd). */
export function shortDate(value) {
    if (!value) return '';
    const [year, month, day] = String(value).split('-');
    return `${day}/${month}/${year}`;
}

/** "Ngày 12 tháng 11 năm 2026" for an instant, in the clinic's time zone. */
export function longDate(value) {
    if (!value) return 'Ngày …… tháng …… năm ……';
    const part = parts(value, { day: '2-digit', month: '2-digit', year: 'numeric' });
    return `Ngày ${part('day')} tháng ${part('month')} năm ${part('year')}`;
}

/** "10:05 12/11/2026" for an instant, in the clinic's time zone. */
export function dateTime(value) {
    if (!value) return '';
    const part = parts(value, { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
    return `${part('hour')}:${part('minute')} ${part('day')}/${part('month')}/${part('year')}`;
}

/** The Vietnamese message of a failed UC-60 call. */
export function printError(error) {
    if (error?.errorResponse?.code === 'PRESCRIPTION_NOT_PRINTABLE') return PRINT_MSG.NOT_PRINTABLE;
    return prescriptionError(error).message;
}
