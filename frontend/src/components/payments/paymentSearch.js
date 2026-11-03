/** UC-70 Search Payment and UC-73 View Payment History: request parameters and messages. */

export const SEARCH_MSG = {
    TOO_SHORT: 'Từ khóa tìm kiếm phải có ít nhất 2 ký tự.',
    NO_RESULT: 'Không tìm thấy kết quả phù hợp.',
    BAD_RANGE: 'Khoảng thời gian không hợp lệ: ngày bắt đầu phải trước hoặc bằng ngày kết thúc.',
};

const MIN_KEYWORD = 2;

/** Builds the query for GET /payments; returns an error instead when the keyword or the range is invalid. */
export function searchParams({ search = '', status = 'ALL', type = 'ALL', from = '', to = '' }) {
    const keyword = search.trim();
    if (keyword && keyword.length < MIN_KEYWORD) return { error: SEARCH_MSG.TOO_SHORT };
    if (from && to && from > to) return { error: SEARCH_MSG.BAD_RANGE };
    return {
        params: {
            size: 50,
            ...(status !== 'ALL' ? { status } : {}),
            ...(type !== 'ALL' ? { type } : {}),
            ...(keyword ? { search: keyword } : {}),
            ...(from ? { from } : {}),
            ...(to ? { to } : {}),
        },
    };
}

/** UC-70 AF-01: exactly one Pending receipt of the typed registration code opens Confirm Payment directly. */
export function directConfirmTarget(results, keyword) {
    const code = keyword.trim().toLowerCase();
    if (!code) return null;
    const pending = results.filter((p) => p.status === 'PENDING' && p.sovaovien?.toLowerCase() === code);
    return pending.length === 1 && results.length === pending.length ? pending[0].id : null;
}

/** UC-73: history query; status ALL means Paid and Cancelled. */
export function historyParams({ from, to, status = 'ALL', type = 'ALL', method = 'ALL', mine = true, sort }) {
    if (from && to && from > to) return { error: SEARCH_MSG.BAD_RANGE };
    return {
        params: {
            ...(from ? { from } : {}),
            ...(to ? { to } : {}),
            ...(status !== 'ALL' ? { status } : {}),
            ...(type !== 'ALL' ? { type } : {}),
            ...(method !== 'ALL' ? { method } : {}),
            mine: String(mine),
            ...(sort ? { sort } : {}),
            size: 100,
        },
    };
}
