import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { LabReferenceRangeModal } from './LabReferenceRangeModal';

const parameter = { id: 'wbc', code: 'WBC', name: 'Số lượng bạch cầu', unit: 'G/L' };
const savedRange = {
  id: 'range-1', parameterId: 'wbc', referenceType: 'RANGE', lowerBound: 4,
  upperBound: 10, lowerInclusive: true, upperInclusive: true, normalValues: [],
  referenceText: null, sex: null, ageMinDays: null, ageMaxDays: null,
  effectiveFrom: '2026-01-01', effectiveTo: null, source: 'Bộ Y tế', active: true,
  version: 0, displayText: '≥4 – ≤10',
};

function createApi(ranges = []) {
  return {
    listReferenceRanges: vi.fn().mockResolvedValue(ranges),
    createReferenceRange: vi.fn().mockResolvedValue(savedRange),
    updateReferenceRange: vi.fn().mockResolvedValue({ ...savedRange, version: 1 }),
  };
}

describe('LabReferenceRangeModal', () => {
  it('creates an active numeric reference range', async () => {
    const api = createApi();
    const onChanged = vi.fn().mockResolvedValue();
    render(<LabReferenceRangeModal parameter={parameter} api={api} onChanged={onChanged} onClose={vi.fn()} />);

    await screen.findByText('Chưa có trị số tham chiếu. Hãy nhập cấu hình đầu tiên.');
    await userEvent.type(screen.getByLabelText(/Cận dưới/), '4');
    await userEvent.type(screen.getByLabelText(/Cận trên/), '10');
    await userEvent.type(screen.getByLabelText(/Nguồn tham chiếu/), 'Bộ Y tế');
    await userEvent.click(screen.getByRole('button', { name: 'Thêm trị số' }));

    await waitFor(() => expect(api.createReferenceRange).toHaveBeenCalledWith('wbc', expect.objectContaining({
      referenceType: 'RANGE', lowerBound: 4, upperBound: 10, source: 'Bộ Y tế', active: true,
    })));
    expect(onChanged).toHaveBeenCalled();
    expect(await screen.findByText('≥4 – ≤10')).toBeInTheDocument();
  });

  it('loads an existing range and updates it with its version', async () => {
    const api = createApi([savedRange]);
    render(<LabReferenceRangeModal parameter={parameter} api={api} onChanged={vi.fn()} onClose={vi.fn()} />);

    await userEvent.click(await screen.findByRole('button', { name: /≥4 – ≤10/ }));
    await userEvent.clear(screen.getByLabelText(/Cận trên/));
    await userEvent.type(screen.getByLabelText(/Cận trên/), '11');
    await userEvent.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));

    await waitFor(() => expect(api.updateReferenceRange).toHaveBeenCalledWith(
      'wbc', 'range-1', expect.objectContaining({ upperBound: 11, version: 0 }),
    ));
  });
});
