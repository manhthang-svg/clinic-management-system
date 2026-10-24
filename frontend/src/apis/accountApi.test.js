import { describe, expect, it, vi } from 'vitest';
import { createAccountApi } from './accountApi';

describe('accountApi', () => {
  it('maps every account-management query endpoint to the backend contract', async () => {
    const client = { request: vi.fn().mockResolvedValue({}) };
    const api = createAccountApi(client);

    await api.departments();
    await api.list({ search: 'an', departmentId: 'dep-1', sort: 'fullName,asc', page: 0, size: 20 });
    await api.detail('acc-1');
    await api.activities('acc-1', 10);
    await api.changeHistory('acc-1', 1, 20);
    await api.workInProgress('acc-1');

    expect(client.request).toHaveBeenNthCalledWith(1, '/accounts/departments', { signal: undefined });
    expect(client.request).toHaveBeenNthCalledWith(2, '/accounts?search=an&departmentId=dep-1&sort=fullName%2Casc&page=0&size=20', { signal: undefined });
    expect(client.request).toHaveBeenNthCalledWith(3, '/accounts/acc-1', { signal: undefined });
    expect(client.request).toHaveBeenNthCalledWith(4, '/accounts/acc-1/activities?limit=10', { signal: undefined });
    expect(client.request).toHaveBeenNthCalledWith(5, '/accounts/acc-1/change-history?page=1&size=20', { signal: undefined });
    expect(client.request).toHaveBeenNthCalledWith(6, '/accounts/acc-1/work-in-progress', { signal: undefined });
  });

  it('maps every account-management command to its expected method', async () => {
    const client = { request: vi.fn().mockResolvedValue({}) };
    const api = createAccountApi(client);
    const body = { version: 1 };

    await api.create(body);
    await api.update('acc-1', body);
    await api.checkRoleChange('acc-1', body);
    await api.changeRole('acc-1', body);
    await api.lock('acc-1', body);
    await api.unlock('acc-1', body);
    await api.resetPassword('acc-1', body);
    await api.updateProfile('0901234567', 'user@clinic.test');
    await api.changePassword('old-password', 'new-password');

    expect(client.request.mock.calls.map(([path, options]) => [path, options.method])).toEqual([
      ['/accounts', 'POST'],
      ['/accounts/acc-1', 'PUT'],
      ['/accounts/acc-1/commands/check-role-change', 'POST'],
      ['/accounts/acc-1/commands/change-role', 'POST'],
      ['/accounts/acc-1/commands/lock', 'POST'],
      ['/accounts/acc-1/commands/unlock', 'POST'],
      ['/accounts/acc-1/commands/reset-password', 'POST'],
      ['/me/profile', 'PUT'],
      ['/me/commands/change-password', 'POST'],
    ]);
  });
});
