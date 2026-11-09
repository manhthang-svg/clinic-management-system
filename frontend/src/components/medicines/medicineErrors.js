// Maps medicine API failures to Vietnamese messages; raw backend messages are never shown.
import { ApiError } from '../../apis/apiError';
import { MSG12, MSG13, MSG80, msg05, msg07, msg39, msg40, msg75 } from '../services/serviceMessages';
import { FIELD_LABELS } from './medicineForm';

const COMMON_MESSAGE = {
  UNAUTHORIZED: MSG12,
  FORBIDDEN: MSG13,
};

function codeOf(error) {
  return error instanceof ApiError ? error.errorResponse?.code : undefined;
}

/** UC-81 load failures. */
export function medicineLoadErrorMessage(error) {
  return COMMON_MESSAGE[codeOf(error)] || MSG80;
}

/** Field errors and one summary message for a failed create or update (UC-82, UC-83). */
export function medicineSaveErrors(error) {
  if (!(error instanceof ApiError)) return { fields: {}, message: MSG80 };
  const code = codeOf(error);
  const fields = {};
  let message = COMMON_MESSAGE[code] || null;

  if (code === 'MEDICINE_CODE_ALREADY_EXISTS') {
    fields.code = msg07(FIELD_LABELS.code); // MSG07
  } else if (code === 'MEDICINE_REGISTRATION_NUMBER_ALREADY_EXISTS') {
    fields.registrationNumber = msg07(FIELD_LABELS.registrationNumber); // MSG07
  } else if (code === 'CONCURRENT_MODIFICATION') {
    message = msg75('Thuốc'); // MSG75
  } else if (code === 'MEDICINE_NOT_FOUND') {
    message = msg39('thuốc'); // MSG39
  } else if (code === 'REQUEST_VALIDATION_FAILED') {
    const details = error.errorResponse?.details ?? {};
    Object.keys(details).forEach((field) => {
      if (field === 'route') fields.route = msg40(FIELD_LABELS.route); // MSG40: not in the route list
      else if (FIELD_LABELS[field]) fields[field] = msg05(FIELD_LABELS[field]); // MSG05
    });
    if (Object.keys(fields).length === 0) message = MSG80;
  } else if (!message) {
    message = MSG80;
  }
  return { fields, message };
}

/** One message for a failed status change (UC-84). */
export function medicineStatusErrorMessage(error) {
  const code = codeOf(error);
  if (COMMON_MESSAGE[code]) return COMMON_MESSAGE[code];
  if (code === 'CONCURRENT_MODIFICATION') return msg75('Thuốc'); // MSG75
  if (code === 'MEDICINE_NOT_FOUND') return msg39('thuốc'); // MSG39
  return MSG80;
}
