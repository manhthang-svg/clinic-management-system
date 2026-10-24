export const MAX_PRICE = 100000000;

export const EMPTY_SERVICE_FORM = {
  code: '',
  name: '',
  type: 'LAB',
  departmentId: '',
  group: '',
  clinicPrice: '',
  insuranceCovered: true,
  insurancePrice: '',
  description: '',
};

const CODE_PATTERN = /^[A-Z0-9._-]+$/;

export function validateServiceForm(form) {
  const errors = {};
  const code = form.code.trim();
  const name = form.name.trim();

  if (!code) errors.code = 'Vui lòng nhập mã dịch vụ.';
  else if (code.length > 20) errors.code = 'Mã dịch vụ tối đa 20 ký tự.';
  else if (!CODE_PATTERN.test(code)) errors.code = 'Mã chỉ gồm chữ in hoa, chữ số và các ký tự - . _';

  if (!name) errors.name = 'Vui lòng nhập tên dịch vụ.';
  else if (name.length > 200) errors.name = 'Tên dịch vụ tối đa 200 ký tự.';

  // The shared examination service has no department or group.
  if (form.type !== 'EXAMINATION') {
    if (!form.departmentId) errors.departmentId = 'Vui lòng chọn khoa thực hiện.';
    if (!form.group) errors.group = 'Vui lòng chọn nhóm dịch vụ.';
  }

  const clinicPrice = Number(form.clinicPrice);
  if (form.clinicPrice === '') errors.clinicPrice = 'Vui lòng nhập giá viện phí.';
  else if (clinicPrice <= 0 || clinicPrice > MAX_PRICE) errors.clinicPrice = 'Giá viện phí phải lớn hơn 0 và không quá 100.000.000 đ.';

  if (form.insuranceCovered) {
    const insurancePrice = Number(form.insurancePrice);
    if (form.insurancePrice === '') errors.insurancePrice = 'Vui lòng nhập giá BHYT.';
    else if (insurancePrice > MAX_PRICE) errors.insurancePrice = 'Giá BHYT không quá 100.000.000 đ.';
  }

  return errors;
}
