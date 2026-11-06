import { render, screen } from '@testing-library/react';
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

// Reference range configuration on the detail is hidden for now (BR-46); restore these with the button.
// function referenceRangeApi() {
//   return {
//     listReferenceRanges: vi.fn().mockResolvedValue([]),
//     createReferenceRange: vi.fn(),
//     updateReferenceRange: vi.fn(),
//   };
// }
//
// describe('ServiceDetailView UC-23 lab reference ranges', () => {
//   it('opens reference range configuration for a parameter of a LAB service', async () => {
//     const api = referenceRangeApi();
//     render(
//       <ServiceDetailView
//         service={labService}
//         onGoToList={vi.fn()}
//         referenceRangeApi={api}
//         onReferenceRangesChanged={vi.fn()}
//       />,
//     );
//
//     await userEvent.click(screen.getByRole('button', { name: 'Cấu hình' }));
//
//     expect(await screen.findByRole('dialog')).toBeInTheDocument();
//     await waitFor(() => expect(api.listReferenceRanges).toHaveBeenCalledWith('parameter-1'));
//   });
// });

describe('ServiceDetailView lab indicators (UC-21 step 3)', () => {
  it('lists the indicators read-only, without a configure action (BR-46)', () => {
    render(<ServiceDetailView service={labService} onGoToList={vi.fn()} />);

    expect(screen.getByText('Số lượng bạch cầu')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cấu hình' })).not.toBeInTheDocument();
    expect(screen.queryByRole('columnheader', { name: 'Thao tác' })).not.toBeInTheDocument();
  });
});

describe('ServiceDetailView empty sections (UC-21 EF-03)', () => {
  it('shows MSG92 in a section without data and the other sections normally', () => {
    render(<ServiceDetailView service={{ ...labService, labParameters: [] }} onGoToList={vi.fn()} />);

    expect(screen.getAllByText('Chưa có dữ liệu để hiển thị.')).toHaveLength(2);
    expect(screen.getByText('Tổng phân tích tế bào máu', { selector: 'div' })).toBeInTheDocument();
  });
});
