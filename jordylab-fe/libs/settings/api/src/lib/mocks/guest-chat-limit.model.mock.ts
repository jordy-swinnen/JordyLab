import { GuestChatLimit } from '../settings.models';

export function aGuestChatLimitMock(overrides: Partial<GuestChatLimit> = {}): GuestChatLimit {
  return {
    dailyLimit: 20,
    source: 'ENVIRONMENT',
    updatedAt: null,
    updatedBy: null,
    ...overrides,
  };
}
