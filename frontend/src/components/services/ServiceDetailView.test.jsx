import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ServiceDetailView } from './ServiceDetailView';

const labService = {
  id: 'service-1',
  code: 'XN-HH-001',
  name: 'Tổng phân tích tế bào máu',
  type: 'LAB',
  status: 'ACTIVE',
  clinicPrice: 90000,
  insurancePrice: 46200,
  description: '',
  performingRooms: [],
  labParameters: [{
    parameterId: 'parameter-1',
    code: 'WBC',
    name: 'Số lượng bạch cầu',
    valueType: 'NUMERIC',
    unit: 'G/L',
    displayOrder: 1,
    referenceRanges: [],
  }],
};

function referenceRangeApi() {
  return {
    listReferenceRanges: vi.fn().mockResolvedValue([]),
    createReferenceRange: vi.fn(),
    updateReferenceRange: vi.fn(),
  };
}

describe('ServiceDetailView UC-23 lab reference ranges', () => {
  it('opens reference range configuration for a parameter of a LAB service', async () => {
    const api = referenceRangeApi();
    render(
      <ServiceDetailView
        service={labService}
        onGoToList={vi.fn()}
        referenceRangeApi={api}
        onReferenceRangesChanged={vi.fn()}
      />,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Cấu hình' }));

    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    await waitFor(() => expect(api.listReferenceRanges).toHaveBeenCalledWith('parameter-1'));
  });

  it('does not expose configuration when no reference range API is supplied', () => {
    render(<ServiceDetailView service={labService} onGoToList={vi.fn()} />);

    expect(screen.queryByRole('button', { name: 'Cấu hình' })).not.toBeInTheDocument();
  });
});
