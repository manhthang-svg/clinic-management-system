import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { VisitInsuranceField } from './VisitInsuranceField';

describe('VisitInsuranceField (UC-40 step 6)', () => {
  it('shows MSG16 with the card validity and coverage rate of a valid card (BR-13, BR-50)', () => {
    render(
      <VisitInsuranceField
        value="DN4017000000005"
        onChange={vi.fn()}
        insurance={{
          cardNumber: 'DN4017000000005',
          status: 'VALID',
          card: { validFrom: '2026-01-01', validTo: '2026-12-31', coverageRate: 80 },
        }}
        onVerify={vi.fn()}
        onSwitchToSelfPaid={vi.fn()}
      />,
    );

    expect(screen.getByText('Thẻ BHYT hợp lệ.')).toBeInTheDocument();
    expect(screen.getByText(/Hạn thẻ: 1\/1\/2026 – 31\/12\/2026/)).toHaveTextContent('Mức hưởng: 80%');
  });
});
