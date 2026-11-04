import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

import { ServiceTable } from './ServiceTable';

function renderTable(props) {
  render(
    <ServiceTable
      services={[]}
      totalCount={0}
      page={1}
      pageSize={10}
      onPageChange={vi.fn()}
      onPageSizeChange={vi.fn()}
      {...props}
    />,
  );
}

describe('ServiceTable empty states (UC-20 EF-01)', () => {
  it('shows MSG92 when there is no service at all', () => {
    renderTable({ filtered: false });

    expect(screen.getByText('Chưa có dữ liệu để hiển thị.')).toBeInTheDocument();
  });

  it('shows MSG01 when the search or filters match nothing', () => {
    renderTable({ filtered: true });

    expect(screen.getByText('Không tìm thấy kết quả.')).toBeInTheDocument();
  });
});
