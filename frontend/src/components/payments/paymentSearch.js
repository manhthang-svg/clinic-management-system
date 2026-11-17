/** UC-70 Search Payment and UC-73 View Payment History: request parameters and messages. */

export const SEARCH_MSG = {
    TOO_SHORT: 'Từ khóa tìm kiếm phải có ít nhất 2 ký tự.',
    NO_RESULT: 'Không tìm thấy kết quả phù hợp.',
    BAD_RANGE: 'Khoảng thời gian không hợp lệ: ngày bắt đầu phải trước hoặc bằng ngày kết thúc.',
};

const MIN_KEYWORD = 2;

/** Receipts per page of the payment list (UC-70); the backend caps a page itself. */
export const PAYMENT_PAGE_SIZE = 20;

/** Builds the query for GET /payments; returns an error instead when the keyword or the range is invalid. */
export function searchParams({ search = '', status = 'ALL', type = 'ALL', from = '', to = '', page = 0 }) {
    const keyword = search.trim();
    if (keyword && keyword.length < MIN_KEYWORD) return { error: SEARCH_MSG.TOO_SHORT };
    if (from && to && from > to) return { error: SEARCH_MSG.BAD_RANGE };
    return {
        params: {
            page,
            size: PAYMENT_PAGE_SIZE,
            ...(status !== 'ALL' ? { status } : {}),
            ...(type !== 'ALL' ? { type } : {}),
            ...(keyword ? { search: keyword } : {}),
            ...(from ? { from } : {}),
            ...(to ? { to } : {}),
        },
    };
}

/** UC-73: history query; status ALL means Paid and Cancelled. The keyword works like UC-70 (at least 2 characters). */
export function historyParams({
    from, to, status = 'ALL', type = 'ALL', method = 'ALL', mine = true, sort, search = '', page = 0,
}) {
    const keyword = search.trim();
    if (keyword && keyword.length < MIN_KEYWORD) return { error: SEARCH_MSG.TOO_SHORT };
    if (from && to && from > to) return { error: SEARCH_MSG.BAD_RANGE };
    return {
        params: {
            ...(keyword ? { search: keyword } : {}),
            ...(from ? { from } : {}),
            ...(to ? { to } : {}),
            ...(status !== 'ALL' ? { status } : {}),
            ...(type !== 'ALL' ? { type } : {}),
            ...(method !== 'ALL' ? { method } : {}),
            mine: String(mine),
            ...(sort ? { sort } : {}),
            page,
            size: PAYMENT_PAGE_SIZE,
        },
    };
}
