import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';

import { SEARCH_MSG, historyParams, searchParams } from '../../components/payments/paymentSearch';
import PaymentHistoryPage from './PaymentHistoryPage';

const history = {
    receipts: {
        content: [{
            id: 'p1', receiptNumber: 'PT20261103-HD0000000002', paymentCode: 'HD0000000002', type: 'PARACLINICAL_FEE',
            status: 'PAID', patientName: 'Nguyễn Văn An', patientCode: 'BN1', sovaovien: 'DK0000000001',
            paidAt: '2026-11-03T03:00:00Z', paymentMethod: 'CASH', patientTotalAmount: 200000, insuranceTotalAmount: 0,
        }],
        page: { number: 0, size: 100, totalElements: 1, totalPages: 1 },
    },
    totalsByMethod: [{ method: 'CASH', total: 200000, count: 1 }],
    totalPatientAmount: 200000,
};

function renderPage(api) {
    render(<MemoryRouter><PaymentHistoryPage paymentApi={api} /></MemoryRouter>);
}

describe('PaymentHistoryPage (UC-73)', () => {
    it('loads today for the current cashier and shows the totals per method (steps 2–4)', async () => {
        const api = { history: vi.fn().mockResolvedValue(history) };
        renderPage(api);

        expect(await screen.findByText('PT20261103-HD0000000002')).toBeInTheDocument();
        const today = new Date().toLocaleDateString('sv-SE');
        expect(api.history).toHaveBeenCalledWith(expect.objectContaining({ from: today, to: today, mine: 'true' }));
        const totals = screen.getByRole('region', { name: 'Tổng thu theo phương thức' });
        expect(within(totals).getByText('Tiền mặt (1 phiếu)')).toBeInTheDocument();
        expect(screen.getByTestId('history-grand-total')).toHaveTextContent('200.000');
    });

    it('applies the filters and the sort when View is clicked', async () => {
        const user = userEvent.setup();
        const api = { history: vi.fn().mockResolvedValue(history) };
        renderPage(api);
        await screen.findByText('PT20261103-HD0000000002');

        await user.selectOptions(screen.getByLabelText('Trạng thái'), 'CANCELLED');
        await user.selectOptions(screen.getByLabelText('Thu ngân'), 'ALL');
        await user.selectOptions(screen.getByLabelText('Sắp xếp'), 'patientTotalAmount,desc');
        await user.click(screen.getByRole('button', { name: 'Xem' }));

        expect(api.history).toHaveBeenLastCalledWith(expect.objectContaining({
            status: 'CANCELLED', mine: 'false', sort: 'patientTotalAmount,desc',
        }));
    });

    it('rejects a reversed date range without calling the API (EF-01, MSG53)', async () => {
        const user = userEvent.setup();
        const api = { history: vi.fn().mockResolvedValue(history) };
        renderPage(api);
        await screen.findByText('PT20261103-HD0000000002');

        const from = screen.getByLabelText('Từ ngày');
        await user.clear(from);
        await user.type(from, '2099-01-01');
        await user.click(screen.getByRole('button', { name: 'Xem' }));

        expect(screen.getByRole('alert')).toHaveTextContent(SEARCH_MSG.BAD_RANGE);
        expect(api.history).toHaveBeenCalledTimes(1);
    });

    it('says when nothing matches (EF-02, MSG01)', async () => {
        renderPage({ history: vi.fn().mockResolvedValue({ ...history, receipts: { content: [] }, totalsByMethod: [] }) });

        expect(await screen.findByText(SEARCH_MSG.NO_RESULT)).toBeInTheDocument();
    });
});

describe('paymentSearch (UC-70)', () => {
    it('requires at least 2 characters (EF-01)', () => {
        expect(searchParams({ search: ' a ' }).error).toBe(SEARCH_MSG.TOO_SHORT);
        expect(searchParams({ search: ' an ', status: 'PAID', from: '2026-11-01' }).params)
            .toEqual({ page: 0, size: 20, status: 'PAID', search: 'an', from: '2026-11-01' });
    });

    it('builds the history query', () => {
        expect(historyParams({ from: '2026-11-01', to: '2026-11-03', method: 'CASH', mine: false }).params)
            .toEqual({ from: '2026-11-01', to: '2026-11-03', method: 'CASH', mine: 'false', page: 0, size: 20 });
        expect(historyParams({ search: ' binh ' }).params).toEqual(expect.objectContaining({ search: 'binh' }));
        expect(historyParams({ search: 'b' }).error).toBe(SEARCH_MSG.TOO_SHORT);
    });

    it('searches and pages through the history, keeping the filters on every page', async () => {
        const user = userEvent.setup();
        const paged = { ...history, receipts: { ...history.receipts, page: { number: 0, size: 20, totalElements: 45, totalPages: 3 } } };
        const api = { history: vi.fn().mockResolvedValue(paged) };
        renderPage(api);
        await screen.findByText('Trang 1/3');

        await user.type(screen.getByLabelText('Tìm kiếm'), 'binh');
        await user.click(screen.getByRole('button', { name: 'Xem' }));
        expect(api.history).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'binh', page: 0, size: 20 }));

        await user.click(await screen.findByRole('button', { name: 'Trang sau' }));
        expect(await screen.findByText('Trang 2/3')).toBeInTheDocument();
        expect(api.history).toHaveBeenLastCalledWith(expect.objectContaining({ search: 'binh', page: 1, size: 20 }));
    });
});
