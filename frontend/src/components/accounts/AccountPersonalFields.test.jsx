import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { AccountPersonalFields } from './AccountPersonalFields';
import { EMPTY_ACCOUNT_FORM } from './accountForm';

function renderFields(role) {
  render(
    <AccountPersonalFields
      form={{ ...EMPTY_ACCOUNT_FORM, role }}
      errors={{}}
      onChange={vi.fn()}
      editable={['title', 'practiceLicenseNo']}
    />,
  );
}

describe('AccountPersonalFields professional information', () => {
  it.each(['DOCTOR', 'TECHNICIAN'])('marks professional fields required for %s', (role) => {
    renderFields(role);

    expect(screen.getAllByText('Bắt buộc đối với Bác sĩ và Kỹ thuật viên.')).toHaveLength(2);
    expect(screen.getByLabelText(/Học hàm \/ học vị/)).toBeRequired();
    expect(screen.getByLabelText(/Số giấy phép hành nghề/)).toBeRequired();
  });

  it('keeps professional fields optional for other roles', () => {
    renderFields('RECEPTIONIST');

    expect(screen.getByText('Không bắt buộc, vd: BSCKI, ThS.BS')).toBeInTheDocument();
    expect(screen.getByLabelText(/Học hàm \/ học vị/)).not.toBeRequired();
    expect(screen.getByLabelText(/Số giấy phép hành nghề/)).not.toBeRequired();
  });
});
