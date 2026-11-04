const PRICE_FORMAT = new Intl.NumberFormat('vi-VN');

/** Price in VND with thousands separators, e.g. 150.000. */
export function formatPrice(value) {
  return PRICE_FORMAT.format(value);
}

// Sex codes follow the patient record: 1 Nam, 2 Nữ, 3 Chưa xác định.
const SEX_LABELS = { 1: 'Nam', 2: 'Nữ', 3: 'Chưa xác định' };
const DAYS_PER_MONTH = 30.4375;

// Ages are stored in days; whole years or months read better, e.g. 6570 -> "18 tuổi", 183 -> "6 tháng".
function formatAge(days) {
  if (days % 365 === 0) return `${days / 365} tuổi`;
  const months = Math.round(days / DAYS_PER_MONTH);
  if (months > 0 && Math.abs(days - months * DAYS_PER_MONTH) < 1) return `${months} tháng`;
  return `${days} ngày`;
}

// An upper bound one day before a whole age is read as "dưới" that age, e.g. 6569 -> "dưới 18 tuổi".
function formatAgeUpTo(maxDays) {
  const next = formatAge(maxDays + 1);
  return next.endsWith('ngày') ? `đến ${formatAge(maxDays)}` : `dưới ${next}`;
}

function formatAgeRange(minDays, maxDays) {
  const hasMin = minDays != null && minDays > 0;
  const hasMax = maxDays != null;
  if (hasMin && hasMax) return `${formatAge(minDays)} – ${formatAgeUpTo(maxDays)}`;
  if (hasMin) return `từ ${formatAge(minDays)}`;
  if (hasMax) return formatAgeUpTo(maxDays);
  return null;
}

/**
 * One reference range as "giới tính, độ tuổi: giá trị", e.g. "Nam, từ 18 tuổi: < 41".
 * A range that applies to every sex and age is just its value.
 */
export function formatReferenceRangeLine(range) {
  const conditions = [SEX_LABELS[range.sex], formatAgeRange(range.ageMinDays, range.ageMaxDays)]
    .filter(Boolean)
    .join(', ');
  const value = range.displayText || '—';
  return conditions ? `${conditions}: ${value}` : value;
}
