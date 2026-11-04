import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { LabRangeRevisionModal } from './LabRangeRevisionModal';

const parameter = { id: 'glu', code: 'GLU', name: 'Glucose huyết tương', unit: 'mmol/L' };
const range = {
  id: 'r1',
  referenceType: 'RANGE',
  lowerBound: 5,
  upperBound: 6,
  lowerInclusive: true,
  upperInclusive: true,
  sex: 2,
  ageMinDays: null,
  ageMaxDays: null,
  effectiveFrom: '2026-01-01',
  effectiveTo: null,
  source: 'Quy trình xét nghiệm',
  active: true,
  version: 1,
};

function renderModal(onRevise = vi.fn().mockResolvedValue(undefined)) {
  render(
    <LabRangeRevisionModal parameter={parameter} range={range} today="2026-11-04" onRevise={onRevise} onClose={vi.fn()} />,
  );
  return onRevise;
}

describe('LabRangeRevisionModal', () => {
  it('shows who the range applies to and saves the new values from today', async () => {
    const user = userEvent.setup();
    const onRevise = renderModal();

    expect(screen.getByText('Nữ')).toBeInTheDocument();
    expect(screen.getByText('5 – 6 mmol/L')).toBeInTheDocument();
    expect(screen.getByText(/áp dụng từ hôm nay 04\/11\/2026/)).toBeInTheDocument();

    const lower = screen.getByLabelText(/Giá trị nhỏ nhất/);
    const upper = screen.getByLabelText(/Giá trị lớn nhất/);
    await user.clear(lower);
    await user.type(lower, '6');
    await user.clear(upper);
    await user.type(upper, '7');
    await user.click(screen.getByRole('button', { name: 'Lưu giá trị mới' }));

    expect(onRevise).toHaveBeenCalledWith(expect.objectContaining({
      lowerBound: 6,
      upperBound: 7,
      sex: 2,
      effectiveFrom: '2026-11-04',
      version: 1,
    }));
  });

  it('does not save a range whose minimum is not below its maximum', async () => {
    const user = userEvent.setup();
    const onRevise = renderModal();

    const lower = screen.getByLabelText(/Giá trị nhỏ nhất/);
    await user.clear(lower);
    await user.type(lower, '9');
    await user.click(screen.getByRole('button', { name: 'Lưu giá trị mới' }));

    expect(screen.getByText('Giá trị lớn nhất phải lớn hơn giá trị nhỏ nhất.')).toBeInTheDocument();
    expect(onRevise).not.toHaveBeenCalled();
  });
});
