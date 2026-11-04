export const MAX_PRICE = 100000000;
export const MAX_CODE_LENGTH = 50;
export const MAX_NAME_LENGTH = 255;
export const MIN_NAME_LENGTH = 2;
export const MAX_DESCRIPTION_LENGTH = 500;

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
  labParameterIds: [],
};

function validateName(form, errors) {
  const name = form.name.trim();
  if (!name) errors.name = 'Vui lòng nhập tên dịch vụ.';
  else if (name.length < MIN_NAME_LENGTH) errors.name = `Tên dịch vụ phải có ít nhất ${MIN_NAME_LENGTH} ký tự.`;
  else if (name.length > MAX_NAME_LENGTH) errors.name = `Tên dịch vụ tối đa ${MAX_NAME_LENGTH} ký tự.`;
}

function validatePrices(form, errors) {
  const clinicPrice = Number(form.clinicPrice);
  if (form.clinicPrice === '') errors.clinicPrice = 'Vui lòng nhập giá viện phí.';
  else if (clinicPrice <= 0 || clinicPrice > MAX_PRICE) errors.clinicPrice = 'Giá viện phí phải lớn hơn 0 và không quá 100.000.000 đ.';

  if (form.insuranceCovered) {
    const insurancePrice = Number(form.insurancePrice);
    if (form.insurancePrice === '') errors.insurancePrice = 'Vui lòng nhập giá BHYT.';
    else if (insurancePrice <= 0 || insurancePrice > MAX_PRICE) errors.insurancePrice = 'Giá BHYT phải lớn hơn 0 và không quá 100.000.000 đ.';
  }
}

// Limits follow CreateMedicalServiceRequest and the services table.
export function validateServiceForm(form) {
  const errors = {};
  const code = form.code.trim();

  if (!code) errors.code = 'Vui lòng nhập mã dịch vụ.';
  else if (code.length > MAX_CODE_LENGTH) errors.code = `Mã dịch vụ tối đa ${MAX_CODE_LENGTH} ký tự.`;

  validateName(form, errors);

  if (form.description.length > MAX_DESCRIPTION_LENGTH) {
    errors.description = `Mô tả tối đa ${MAX_DESCRIPTION_LENGTH} ký tự.`;
  }

  if (form.type === 'LAB' && form.labParameterIds.length === 0) {
    errors.labParameterIds = 'Vui lòng chọn ít nhất một thông số xét nghiệm.';
  }

  // The shared examination service has no department or group.
  if (form.type !== 'EXAMINATION') {
    if (!form.departmentId) errors.departmentId = 'Vui lòng chọn khoa thực hiện.';
    if (!form.serviceGroupId) errors.serviceGroupId = 'Vui lòng chọn nhóm dịch vụ.';
  }

  validatePrices(form, errors);
  return errors;
}

/** UC-23: only the name and description can change; code, type, and assignment are fixed. */
export function validateServiceInfoForm(form) {
  const errors = {};
  validateName(form, errors);
  if (form.description.length > MAX_DESCRIPTION_LENGTH) {
    errors.description = `Mô tả tối đa ${MAX_DESCRIPTION_LENGTH} ký tự.`;
  }
  return errors;
}

/** UC-24, with the same "insurance price > 0 when covered" rule as the create form. */
export function validateServicePriceForm(form) {
  const errors = {};
  validatePrices(form, errors);
  return errors;
}

export function serviceToInfoForm(service) {
  return { name: service.name ?? '', description: service.description ?? '' };
}

export function serviceToPriceForm(service) {
  const covered = service.insurancePrice !== null && service.insurancePrice !== undefined;
  return {
    clinicPrice: String(service.clinicPrice ?? ''),
    insuranceCovered: covered,
    insurancePrice: covered ? String(service.insurancePrice) : '',
  };
}

/** Body of PUT /services/{id}. */
export function infoFormToRequest(form, version) {
  return { name: form.name.trim(), description: form.description.trim() || null, version };
}

/** Body of PUT /services/{id}/prices; an uncovered service sends a null insurance price. */
export function priceFormToRequest(form, version) {
  return {
    clinicPrice: Number(form.clinicPrice),
    insurancePrice: form.insuranceCovered ? Number(form.insurancePrice) : null,
    version,
  };
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
    labParameters: form.type === 'LAB'
      ? form.labParameterIds.map((parameterId, index) => ({ parameterId, displayOrder: index + 1 }))
      : [],
  };
}
