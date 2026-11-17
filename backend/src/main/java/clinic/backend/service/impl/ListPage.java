package clinic.backend.service.impl;

import java.util.List;
import java.util.Map;

import clinic.backend.dto.response.PageResponse;
import clinic.backend.enums.ErrorCode;
import clinic.backend.exceptions.AppException;

/**
 * Cuts one page out of a list built on the server (for example statistics rows aggregated in memory), so the client
 * only receives the rows it shows.
 */
public final class ListPage {

    /** Rows per page when the client does not ask. */
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private ListPage() {
    }

    // trang dem tu 0; trang qua cuoi tra danh sach rong (van kem tong so dong)
    public static <T> PageResponse<T> of(List<T> rows, Integer page, Integer size) {
        int number = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (number < 0) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED, Map.of("page", "MUST_NOT_BE_NEGATIVE"));
        }
        if (pageSize < 1 || pageSize > MAX_SIZE) {
            throw new AppException(ErrorCode.REQUEST_VALIDATION_FAILED,
                    Map.of("size", "The size must be between 1 and " + MAX_SIZE + "."));
        }
        int total = rows.size();
        int from = (int) Math.min((long) number * pageSize, total);
        int to = Math.min(from + pageSize, total);
        int totalPages = (total + pageSize - 1) / pageSize;
        return new PageResponse<>(List.copyOf(rows.subList(from, to)),
                new PageResponse.PageMetadata(number, pageSize, total, totalPages));
    }
}
