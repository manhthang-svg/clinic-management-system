import { describe, expect, it, vi } from 'vitest';

import { createAdministrativeUnitApi } from './administrativeUnitApi';

const ok = (body) => Promise.resolve({ ok: true, json: () => Promise.resolve(body) });

describe('administrativeUnitApi', () => {
    it('returns provinces with 2-digit codes and caches the call', async () => {
        const fetchImplementation = vi.fn(() => ok([
            { code: 4, name: 'Tỉnh Cao Bằng' },
            { code: 1, name: 'Thành phố Hà Nội' },
        ]));
        const api = createAdministrativeUnitApi({ baseUrl: 'https://units.test', fetchImplementation });

        expect(await api.provinces()).toEqual([
            { code: '04', name: 'Tỉnh Cao Bằng' },
            { code: '01', name: 'Thành phố Hà Nội' },
        ]);
        await api.provinces();
        expect(fetchImplementation).toHaveBeenCalledTimes(1);
        expect(fetchImplementation.mock.calls[0][0]).toBe('https://units.test/p/');
    });

    it('returns the wards of a province with 5-digit codes', async () => {
        const fetchImplementation = vi.fn(() => ok({
            code: 1,
            wards: [{ code: 8, name: 'Phường Ngọc Hà' }, { code: 4, name: 'Phường Ba Đình' }],
        }));
        const api = createAdministrativeUnitApi({ baseUrl: 'https://units.test', fetchImplementation });

        expect(await api.wards('01')).toEqual([
            { code: '00008', name: 'Phường Ngọc Hà' },
            { code: '00004', name: 'Phường Ba Đình' },
        ]);
        expect(fetchImplementation.mock.calls[0][0]).toBe('https://units.test/p/1?depth=2');
    });

    it('fails when the service answers with an error', async () => {
        const api = createAdministrativeUnitApi({
            baseUrl: 'https://units.test',
            fetchImplementation: () => Promise.resolve({ ok: false, status: 503 }),
        });
        await expect(api.provinces()).rejects.toThrow('503');
    });
});
