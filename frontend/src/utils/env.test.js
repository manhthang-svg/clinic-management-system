import { describe, expect, it } from 'vitest';
import { resolveApiBaseUrl } from './env';

describe('resolveApiBaseUrl', () => {
    it('uses the versioned API path by default', () => {
        expect(resolveApiBaseUrl()).toBe('/api/v1');
    });
    it('normalizes a configured API base URL', () => {
        expect(resolveApiBaseUrl(' https://api.example.test/api/v1/ ')).toBe('https://api.example.test/api/v1');
    });
    it('rejects URLs containing credentials', () => {
        expect(() => resolveApiBaseUrl('https://user:secret@api.example.test/api/v1')).toThrow('must not contain credentials');
    });
});
