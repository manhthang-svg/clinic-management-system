/**
 * Vietnamese administrative units (two-level model since 01/07/2025: province -> ward) from the public
 * provinces.open-api.vn v2 API. Used by UC-37 / UC-38 to suggest Tỉnh/thành phố and Xã/phường and fill their codes.
 * Only a province code is ever sent; no patient data leaves the browser.
 */
export const ADMINISTRATIVE_UNITS_BASE_URL =
    import.meta.env.VITE_ADMINISTRATIVE_UNITS_API_URL || 'https://provinces.open-api.vn/api/v2';

// official code formats: province 2 digits, ward 5 digits (QĐ 19/2025/QĐ-TTg)
const pad = (code, length) => String(code).padStart(length, '0');

const toProvince = (item) => ({ code: pad(item.code, 2), name: item.name });
const toWard = (item) => ({ code: pad(item.code, 5), name: item.name });

export function createAdministrativeUnitApi({
    baseUrl = ADMINISTRATIVE_UNITS_BASE_URL,
    fetchImplementation = (input, init) => globalThis.fetch(input, init),
} = {}) {
    const cache = new Map();

    async function getJson(path, signal) {
        if (cache.has(path)) return cache.get(path);
        const response = await fetchImplementation(`${baseUrl}${path}`, { signal, headers: { Accept: 'application/json' } });
        if (!response.ok) throw new Error(`Administrative units request failed (${response.status})`);
        const data = await response.json();
        cache.set(path, data);
        return data;
    }

    return {
        /** All provinces, in the official code order. */
        provinces: async (signal) => (await getJson('/p/', signal)).map(toProvince),
        /** Wards of one province (code as stored, e.g. '01'), in the official code order. */
        wards: async (provinceCode, signal) => ((await getJson(`/p/${Number(provinceCode)}?depth=2`, signal)).wards ?? [])
            .map(toWard),
    };
}

const administrativeUnitApi = createAdministrativeUnitApi();
export default administrativeUnitApi;
