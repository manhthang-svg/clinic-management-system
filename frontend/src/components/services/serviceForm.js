export const MAX_PRICE = 100000000;
export const MAX_CODE_LENGTH = 50;
export const MAX_NAME_LENGTH = 255;

export const EMPTY_SERVICE_FORM = {
  code: '',
  name: '',
  type: 'LAB',
  departmentId: '',
  serviceGroupId: '',
  clinicPrice: '',
  insuranceCovered: true,
  insurancePrice: '',
  description: '',
};

// Limits follow CreateMedicalServiceRequest and the services table.
export function validateServiceForm(form) {
  const errors = {};
  const code = form.code.trim();
  const name = form.name.trim();

  if (!code) errors.code = 'Vui lòng nhập mã dịch vụ.';
  else if (code.length > MAX_CODE_LENGTH) errors.code = `Mã dịch vụ tối đa ${MAX_CODE_LENGTH} ký tự.`;

  if (!name) errors.name = 'Vui lòng nhập tên dịch vụ.';
  else if (name.length > MAX_NAME_LENGTH) errors.name = `Tên dịch vụ tối đa ${MAX_NAME_LENGTH} ký tự.`;

  // The shared examination service has no department or group.
  if (form.type !== 'EXAMINATION') {
    if (!form.departmentId) errors.departmentId = 'Vui lòng chọn khoa thực hiện.';
    if (!form.serviceGroupId) errors.serviceGroupId = 'Vui lòng chọn nhóm dịch vụ.';
  }

  const clinicPrice = Number(form.clinicPrice);
  if (form.clinicPrice === '') errors.clinicPrice = 'Vui lòng nhập giá viện phí.';
  else if (clinicPrice <= 0 || clinicPrice > MAX_PRICE) errors.clinicPrice = 'Giá viện phí phải lớn hơn 0 và không quá 100.000.000 đ.';

  if (form.insuranceCovered) {
    const insurancePrice = Number(form.insurancePrice);
    if (form.insurancePrice === '') errors.insurancePrice = 'Vui lòng nhập giá BHYT.';
    else if (insurancePrice <= 0 || insurancePrice > MAX_PRICE) errors.insurancePrice = 'Giá BHYT phải lớn hơn 0 và không quá 100.000.000 đ.';
  }

  return errors;
}

/** Body of POST /services; an uncovered service sends a null insurance price. */
export function formToServiceRequest(form) {
  const examination = form.type === 'EXAMINATION';
  return {
    code: form.code.trim(),
    name: form.name.trim(),
    type: form.type,
    serviceGroupId: examination ? null : form.serviceGroupId,
    departmentId: examination ? null : form.departmentId,
    clinicPrice: Number(form.clinicPrice),
    insurancePrice: form.insuranceCovered ? Number(form.insurancePrice) : null,
    description: form.description.trim() || null,
  };
}
