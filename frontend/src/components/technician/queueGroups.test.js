import { describe, expect, it } from 'vitest';
import { groupStatusOf, isServiceCheckedIn, serviceForAction } from './queueGroups';

const svc = (status, { id = 1, ...extra } = {}) => ({ id: `${status}-${id}`, status, isSkipped: false, ...extra });

describe('groupStatusOf', () => {
  it('never falls back to WAITING once every service was performed', () => {
    expect(groupStatusOf([svc('PENDING_RESULT'), svc('PENDING_RESULT', { id: 2 })])).toBe('PENDING_RESULT');
    expect(groupStatusOf([svc('PENDING_RESULT'), svc('COMPLETED')])).toBe('PENDING_RESULT');
  });

  it('puts the work still to do first', () => {
    expect(groupStatusOf([svc('PENDING_RESULT'), svc('IN_PROGRESS')])).toBe('IN_PROGRESS');
    expect(groupStatusOf([svc('PENDING_RESULT'), svc('WAITING')])).toBe('WAITING');
    expect(groupStatusOf([svc('WAITING', { isSkipped: true }), svc('PENDING_RESULT')])).toBe('SKIPPED');
    expect(groupStatusOf([svc('COMPLETED'), svc('CANCELLED')])).toBe('COMPLETED');
    expect(groupStatusOf([svc('CANCELLED')])).toBe('CANCELLED');
  });
});

describe('isServiceCheckedIn', () => {
  it('counts a performed service as checked in', () => {
    expect(isServiceCheckedIn(svc('PENDING_RESULT'))).toBe(true);
    expect(isServiceCheckedIn(svc('WAITING'))).toBe(false);
    expect(isServiceCheckedIn(svc('WAITING', { queueNumber: 3 }))).toBe(true);
  });
});

describe('serviceForAction', () => {
  it('picks the service in the row status', () => {
    const group = { status: 'WAITING', roomServices: [svc('PENDING_RESULT'), svc('WAITING', { id: 2 })] };
    expect(serviceForAction(group).id).toBe('WAITING-2');
    const skipped = { status: 'SKIPPED', roomServices: [svc('PENDING_RESULT'), svc('WAITING', { id: 3, isSkipped: true })] };
    expect(serviceForAction(skipped).id).toBe('WAITING-3');
  });
});
