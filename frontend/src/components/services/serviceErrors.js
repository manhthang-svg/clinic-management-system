import { ApiError } from '../../apis/apiError';
import {
  MSG01,
  MSG12,
  MSG13,
  MSG52,
  MSG80,
  MSG92,
  msg05,
  msg07,
  msg39,
  msg40,
  msg55,
  msg73,
  msg75,
} from './serviceMessages';

export { MSG01, MSG92, msg39, msg55 };

// Messages for failures every service request shares: session (MSG12), role (MSG13), system (MSG80).
const COMMON_MESSAGE = {
  UNAUTHORIZED: MSG12,
  FORBIDDEN: MSG13,
};

function isApiError(error) {
  return error instanceof ApiError;
}

function codeOf(error) {
  return isApiError(error) ? error.errorResponse?.code : undefined;
}

/** UC-20 / UC-21 load failures. */
export function serviceErrorMessage(error) {
  return COMMON_MESSAGE[codeOf(error)] || MSG80;
}

const FIELD_LABEL = {
  code: 'Mã dịch vụ',
  name: 'Tên dịch vụ',
  type: 'Loại dịch vụ',
  serviceGroupId: 'Nhóm dịch vụ',
  departmentId: 'Khoa thực hiện',
  clinicPrice: 'Giá viện phí',
  insurancePrice: 'Giá BHYT',
  description: 'Mô tả',
  labParameters: 'Chỉ số xét nghiệm',
};

// SERVICE_ASSIGNMENT_INVALID details.assignment -> form field and message (MSG40: the selection is no longer usable).
const ASSIGNMENT_ERRORS = {
  DEPARTMENT_NOT_FOUND: ['departmentId', msg40('Khoa thực hiện')],
  DEPARTMENT_NOT_ACTIVE_PARACLINICAL: ['departmentId', msg40('Khoa thực hiện')],
  SERVICE_GROUP_NOT_FOUND: ['serviceGroupId', msg40('Nhóm dịch vụ')],
  SERVICE_GROUP_INACTIVE: ['serviceGroupId', msg40('Nhóm dịch vụ')],
  SERVICE_GROUP_TYPE_MISMATCH: ['serviceGroupId', msg40('Nhóm dịch vụ')],
  SERVICE_GROUP_DEPARTMENT_MISMATCH: ['serviceGroupId', msg40('Nhóm dịch vụ')],
  DEPARTMENT_AND_GROUP_REQUIRED: ['serviceGroupId', msg40('Nhóm dịch vụ')],
};

// REQUEST_VALIDATION_FAILED details name the fields the backend rejected (MSG05).
function validationFields(error) {
  const details = error.errorResponse?.details ?? {};
  const fields = {};
  Object.keys(details).forEach((field) => {
    if (FIELD_LABEL[field]) fields[field] = msg05(FIELD_LABEL[field]);
  });
  return fields;
}

/** Field errors and one summary message for a failed create-service request (UC-22). */
export function serviceCreateErrors(error, serviceName = 'dịch vụ') {
  if (!isApiError(error)) return { fields: {}, message: MSG80 };
  const code = codeOf(error);
  const details = error.errorResponse?.details ?? {};
  let fields = {};
  let message = COMMON_MESSAGE[code] || null;

  if (code === 'SERVICE_CODE_ALREADY_EXISTS') {
    fields.code = msg07('Mã dịch vụ'); // MSG07, EF-03
  } else if (code === 'SERVICE_NAME_ALREADY_EXISTS') {
    fields.name = msg07('Tên dịch vụ'); // MSG07, EF-03
  } else if (code === 'SERVICE_TYPE_NOT_CREATABLE') {
    fields.type = msg40('Loại dịch vụ');
  } else if (code === 'SERVICE_GROUP_HAS_NO_ACTIVE_ROOM') {
    message = msg73(serviceName); // MSG73, EF-05
    fields.serviceGroupId = message;
  } else if (code?.startsWith('LAB_PARAMETER') || code === 'LAB_PARAMETERS_NOT_ALLOWED') {
    fields.labParameterIds = msg40('Chỉ số xét nghiệm');
  } else if (code === 'SERVICE_ASSIGNMENT_INVALID' && ASSIGNMENT_ERRORS[details.assignment]) {
    const [field, fieldMessage] = ASSIGNMENT_ERRORS[details.assignment];
    fields[field] = fieldMessage;
  } else if (code === 'REQUEST_VALIDATION_FAILED') {
    fields = validationFields(error);
    if (Object.keys(fields).length === 0) message = MSG80;
  } else if (!message) {
    message = MSG80;
  }

  return { fields, message };
}

/** Field errors and one summary message for a failed UC-23 / UC-24 update. */
export function serviceUpdateErrors(error) {
  if (!isApiError(error)) return { fields: {}, message: MSG80 };
  const code = codeOf(error);
  let fields = {};
  let message = COMMON_MESSAGE[code] || null;

  if (code === 'SERVICE_NAME_ALREADY_EXISTS') {
    fields.name = msg07('Tên dịch vụ'); // MSG07, UC-23 EF-02
  } else if (code === 'CONCURRENT_MODIFICATION') {
    message = msg75('Dịch vụ'); // MSG75, BR-119
  } else if (code === 'SERVICE_NOT_FOUND') {
    message = msg39('dịch vụ'); // MSG39
  } else if (code === 'REQUEST_VALIDATION_FAILED') {
    fields = validationFields(error);
    if (Object.keys(fields).length === 0) message = MSG80;
  } else if (!message) {
    message = MSG80;
  }

  return { fields, message };
}

/**
 * One message for a failed UC-25 status check or change. item names the service in MSG55 (e.g. 'Dịch vụ "X"');
 * serviceName fills MSG73.
 */
export function serviceStatusErrorMessage(error, item = 'Dịch vụ', serviceName = 'dịch vụ') {
  const code = codeOf(error);
  if (COMMON_MESSAGE[code]) return COMMON_MESSAGE[code];
  switch (code) {
    case 'EXAMINATION_SERVICE_NOT_DEACTIVATABLE':
      return MSG52; // EF-01
    case 'SERVICE_HAS_UNFINISHED_ORDERS':
      return msg55(item); // EF-02
    case 'SERVICE_GROUP_HAS_NO_ACTIVE_ROOM':
      return msg73(serviceName); // EF-03
    case 'CONCURRENT_MODIFICATION':
      return msg75('Dịch vụ');
    case 'SERVICE_NOT_FOUND':
      return msg39('dịch vụ');
    default:
      return MSG80; // EF-04
  }
}
