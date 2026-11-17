// UC-70, UC-73: tham so tim kiem phieu thu va thong bao

export const SEARCH_MSG = {
    TOO_SHORT: 'Từ khóa tìm kiếm phải có ít nhất 2 ký tự.',
    NO_RESULT: 'Không tìm thấy kết quả phù hợp.',
    BAD_RANGE: 'Khoảng thời gian không hợp lệ: ngày bắt đầu phải trước hoặc bằng ngày kết thúc.',
};

const MIN_KEYWORD = 2;

// so phieu moi trang
export const PAYMENT_PAGE_SIZE = 20;

// tao tham so tim phieu thu; tu khoa / khoang ngay sai thi tra loi
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

// UC-73: tham so lich su (Tat ca = da thu + da huy), tu khoa it nhat 2 ky tu
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
