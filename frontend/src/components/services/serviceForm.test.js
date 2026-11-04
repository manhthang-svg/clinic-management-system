import { describe, expect, it } from 'vitest';
import {
  EMPTY_SERVICE_FORM,
  formToServiceRequest,
  validateServiceForm,
  validateServiceInfoForm,
} from './serviceForm';

const validLabForm = (overrides = {}) => ({
  ...EMPTY_SERVICE_FORM,
  code: 'XN-01',
  name: 'Xét nghiệm máu',
  departmentId: 'department-1',
  serviceGroupId: 'group-1',
  clinicPrice: '120000',
  insuranceCovered: false,
  labParameterIds: ['parameter-2', 'parameter-1'],
  ...overrides,
});

describe('serviceForm (UC-22)', () => {
  it('requires at least one parameter for a laboratory service', () => {
    const errors = validateServiceForm(validLabForm({ labParameterIds: [] }));

    expect(errors.labParameterIds).toBeTruthy();
  });

  it('sends laboratory parameters in the selected display order', () => {
    const request = formToServiceRequest(validLabForm());

    expect(request.labParameters).toEqual([
      { parameterId: 'parameter-2', displayOrder: 1 },
      { parameterId: 'parameter-1', displayOrder: 2 },
    ]);
    expect(request.insurancePrice).toBeNull();
  });

  it('does not send parameters for a non-laboratory service', () => {
    const request = formToServiceRequest(validLabForm({ type: 'IMAGING' }));

    expect(request.labParameters).toEqual([]);
  });
});

describe('serviceForm (UC-23)', () => {
  it('validates the editable name and description limits', () => {
    expect(validateServiceInfoForm({ name: 'A', description: 'x'.repeat(501) }))
      .toMatchObject({ name: expect.any(String), description: expect.any(String) });
  });
});
