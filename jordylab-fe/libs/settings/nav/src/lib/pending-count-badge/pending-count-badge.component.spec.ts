import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { UsersStore } from '@jordylab-fe/settings/api';
import { PendingCountBadgeComponent } from './pending-count-badge.component';

describe('PendingCountBadgeComponent', () => {
  const pendingCount = signal(0);

  const storeMock = {
    pendingCount: pendingCount.asReadonly(),
  };

  let spectator: Spectator<PendingCountBadgeComponent>;

  const createComponent = createComponentFactory({
    component: PendingCountBadgeComponent,
    providers: [{ provide: UsersStore, useValue: storeMock }],
  });

  beforeEach(() => {
    pendingCount.set(0);
    spectator = createComponent();
  });

  it('renders nothing while no sign-up is pending', () => {
    expect(spectator.query('[data-testid="pending-count-badge"]')).toBeNull();
  });

  it('shows the number of pending sign-ups', () => {
    pendingCount.set(3);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="pending-count-badge"]')).toHaveText(
      '3',
    );
  });
});
