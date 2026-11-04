import { useState } from 'react';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { LabParameterSelector } from './LabParameterSelector';

const parameters = [
  {
    id: 'wbc',
    code: 'WBC',
    name: 'Số lượng bạch cầu',
    unit: 'G/L',
    referenceRanges: [{ id: 'wbc-adult', displayText: '4 – 10' }],
  },
  { id: 'rbc', code: 'RBC', name: 'Số lượng hồng cầu', unit: 'T/L' },
];

function SelectorHarness() {
  const [selectedIds, setSelectedIds] = useState(['wbc']);
  return (
    <LabParameterSelector
      parameters={parameters}
      selectedIds={selectedIds}
      onChange={setSelectedIds}
    />
  );
}

describe('LabParameterSelector', () => {
  it('adds, reorders, and removes laboratory parameters from the table', async () => {
    render(<SelectorHarness />);

    await userEvent.selectOptions(screen.getByLabelText('Chọn chỉ số'), 'rbc');
    await userEvent.click(screen.getByRole('button', { name: 'Thêm chỉ số' }));

    let rows = screen.getAllByRole('row').slice(1);
    expect(within(rows[0]).getByText('WBC')).toBeInTheDocument();
    expect(within(rows[0]).getByText('4 – 10')).toBeInTheDocument();
    expect(within(rows[1]).getByText('RBC')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Thứ tự của Số lượng hồng cầu'), {
      target: { value: '1' },
    });
    rows = screen.getAllByRole('row').slice(1);
    expect(within(rows[0]).getByText('RBC')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: 'Gỡ chỉ số Số lượng hồng cầu' }));
    expect(screen.queryByText('RBC')).not.toBeInTheDocument();
    expect(screen.getByText('WBC')).toBeInTheDocument();
  });
});
