import { MSG38, msg02, msg05, msg06, msg08, msg32, msg40 } from './serviceMessages';

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

// MSG38 confirms leaving a form with unsaved changes (UC-22 AF-04, UC-23 AF-01, UC-24 AF-01).
export { MSG38 };

/** MSG06 before a price update (UC-24 step 6). */
export const PRICE_MSG06 = msg06('cập nhật giá dịch vụ');

const PRICE_ERROR = msg32(1, '100.000.000');

/** True once the Admin has entered or changed any field of the create form. */
export function isServiceFormDirty(form) {
  return Object.entries(EMPTY_SERVICE_FORM).some(([field, initial]) => {
    const value = form[field];
    if (Array.isArray(initial)) return value.length > 0;
    if (typeof initial === 'string') return value.trim() !== initial;
    return value !== initial;
  });
}

function validateName(form, errors) {
  const name = form.name.trim();
  if (!name) errors.name = msg02('Tên dịch vụ');
  // ASSUMPTION: the message list has no "too short" text; a one-character name is reported as a wrong format.
  else if (name.length < MIN_NAME_LENGTH) errors.name = msg05('Tên dịch vụ');
  else if (name.length > MAX_NAME_LENGTH) errors.name = msg08(MAX_NAME_LENGTH);
}

function validateDescription(form, errors) {
  if (form.description.length > MAX_DESCRIPTION_LENGTH) errors.description = msg08(MAX_DESCRIPTION_LENGTH);
}

// BR-78: whole VND from 1 to 100,000,000 (MSG02 when empty, MSG32 otherwise).
function validatePrices(form, errors) {
  const clinicPrice = Number(form.clinicPrice);
  if (form.clinicPrice === '') errors.clinicPrice = msg02('Giá viện phí');
  else if (clinicPrice <= 0 || clinicPrice > MAX_PRICE) errors.clinicPrice = PRICE_ERROR;

  if (form.insuranceCovered) {
    const insurancePrice = Number(form.insurancePrice);
    if (form.insurancePrice === '') errors.insurancePrice = msg02('Giá BHYT');
    else if (insurancePrice <= 0 || insurancePrice > MAX_PRICE) errors.insurancePrice = PRICE_ERROR;
  }
}

// Limits follow CreateMedicalServiceRequest and the services table.
export function validateServiceForm(form) {
  const errors = {};
  const code = form.code.trim();

  if (!code) errors.code = msg02('Mã dịch vụ');
  else if (code.length > MAX_CODE_LENGTH) errors.code = msg08(MAX_CODE_LENGTH);

  validateName(form, errors);
  validateDescription(form, errors);

  // EF-02: a laboratory service needs at least one indicator.
  if (form.type === 'LAB' && form.labParameterIds.length === 0) {
    errors.labParameterIds = msg02('Chỉ số xét nghiệm');
  }

  // The shared examination service has no department or group.
  if (form.type !== 'EXAMINATION') {
    // The department comes from the group, so it is missing only when the group has none (MSG40).
    if (!form.serviceGroupId) errors.serviceGroupId = msg02('Nhóm dịch vụ');
    else if (!form.departmentId) errors.departmentId = msg40('Nhóm dịch vụ');
  }

  validatePrices(form, errors);
  return errors;
}

/** UC-23: only the name and description can change; code, type, and assignment are fixed. */
export function validateServiceInfoForm(form) {
  const errors = {};
  validateName(form, errors);
  validateDescription(form, errors);
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
