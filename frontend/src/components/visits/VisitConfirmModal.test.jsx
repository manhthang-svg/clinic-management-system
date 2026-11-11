import { render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { VisitConfirmModal } from './VisitConfirmModal';

const patient = { hoten: 'Bùi Thị My', mabenhnhan: 'BN00000007', ngaysinh: '2010-04-21', gioitinh: 'NU' };
const insuredForm = {
  visitType: 'BHYT', cardNumber: '7000000006', isPriority: false, reason: 'Đau khớp', followUpAppointmentId: null,
};

function renderModal(fee, form = insuredForm) {
  render(
    <VisitConfirmModal
      patient={patient}
      form={form}
      insurance={{ status: 'VALID', card: { coverageRate: 80 } }}
      room={{ code: 'PK-NOI', name: 'Phòng khám Nội', doctorName: 'Trần Văn An' }}
      fee={fee}
      feeLoading={false}
      submitting={false}
      error=""
      onBack={vi.fn()}
      onCancel={vi.fn()}
      onConfirm={vi.fn()}
    />,
  );
}

const priceOf = (label) => within(screen.getByText(label).parentElement).getByRole('definition');

describe('VisitConfirmModal', () => {
  it('shows the catalogue insurance price of the examination service, not the clinic price', () => {
    renderModal({ clinicPrice: 150000, insurancePrice: 50600, appliedPrice: 150000 });

    expect(priceOf('Giá viện phí')).toHaveTextContent('150.000');
    expect(priceOf('Giá BHYT')).toHaveTextContent('50.600');
  });

  it('shows both prices for a self-paid visit and asks the patient to pay the clinic price', () => {
    renderModal(
      { clinicPrice: 150000, insurancePrice: 50600, appliedPrice: 150000, insuranceAmount: 0, patientAmount: 150000 },
      { ...insuredForm, visitType: 'DV', cardNumber: '' },
    );

    expect(priceOf('Giá viện phí')).toHaveTextContent('150.000');
    expect(priceOf('Giá BHYT')).toHaveTextContent('50.600');
    expect(screen.getByText('Bệnh nhân phải trả').closest('dl')).toHaveTextContent('150.000');
    expect(screen.queryByText('BHYT chi trả')).not.toBeInTheDocument();
  });

  it('shows the doctor of the chosen room', () => {
    renderModal({ clinicPrice: 150000, insurancePrice: 50600, appliedPrice: 150000 });

    expect(priceOf('Bác sĩ khám')).toHaveTextContent('Trần Văn An');
  });

  it('says the service is not covered when it has no insurance price', () => {
    renderModal({ clinicPrice: 150000, insurancePrice: null, appliedPrice: 150000 });

    expect(priceOf('Giá BHYT')).toHaveTextContent('Không thuộc BHYT');
  });
});
