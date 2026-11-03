import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aMachineMock, CreatedMachine, Machine, MachinesStore } from '@jordylab-fe/settings/api';
import { MachinesPageComponent } from './machines-page.component';

describe('MachinesPageComponent', () => {
  const machines = signal<Machine[]>([]);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const creating = signal(false);
  const created = signal<CreatedMachine | null>(null);

  const create = vi.fn<MachinesStore['create']>();
  const revoke = vi.fn<MachinesStore['revoke']>();
  const load = vi.fn<MachinesStore['load']>();
  const dismissCreated = vi.fn<MachinesStore['dismissCreated']>();

  const storeMock = {
    machines: machines.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    creating: creating.asReadonly(),
    created: created.asReadonly(),
    create,
    revoke,
    load,
    dismissCreated,
  };

  const createComponent = createComponentFactory({
    component: MachinesPageComponent,
    providers: [{ provide: MachinesStore, useValue: storeMock }],
  });

  let spectator: Spectator<MachinesPageComponent>;

  beforeEach(() => {
    vi.clearAllMocks();
    machines.set([aMachineMock()]);
    created.set(null);
    spectator = createComponent();
  });

  it('lists the machines with their last-used state', () => {
    const rows = spectator.queryAll('[data-testid="machine-row"]');

    expect(rows).toHaveLength(1);
    expect(spectator.query('[data-testid="machine-row"]')).toHaveText('JordyBox');
    expect(spectator.query('[data-testid="machine-row"]')).toHaveText('never used');
  });

  it('adds a machine by name', () => {
    spectator.typeInElement('JordyBox', '[data-testid="machine-name"]');
    spectator.click('[data-testid="create-machine"]');

    expect(create).toHaveBeenCalledWith('JordyBox');
  });

  it('requires a name', () => {
    expect(spectator.query('[data-testid="create-machine"]')).toBeDisabled();
  });

  it('shows the one-time token only after creation', () => {
    expect(spectator.query('[data-testid="created-token"]')).toBeNull();

    created.set({ id: 'm1', name: 'JordyBox', token: 'jlmt_secret' });
    spectator.detectChanges();

    expect(spectator.query('[data-testid="created-token"]')).toHaveText('jlmt_secret');

    spectator.click('[data-testid="dismiss-created"]');

    expect(dismissCreated).toHaveBeenCalled();
  });

  it('asks for confirmation before revoking', () => {
    spectator.click('[data-testid="revoke-machine"]');

    expect(revoke).not.toHaveBeenCalled();
    expect(spectator.query('[data-testid="revoke-machine"]')).toHaveText('Confirm revoke');

    spectator.click('[data-testid="revoke-machine"]');

    expect(revoke).toHaveBeenCalledWith(aMachineMock().id);
  });

  it('marks a revoked machine and hides its revoke action', () => {
    machines.set([aMachineMock({ revokedAt: '2026-11-03T12:00:00Z' })]);
    spectator.detectChanges();

    expect(spectator.query('[data-testid="machine-revoked"]')).toExist();
    expect(spectator.query('[data-testid="revoke-machine"]')).toBeNull();
  });

  it('shows a load error with retry', () => {
    loading.set(false);
    error.set('Failed to load machines.');
    spectator.detectChanges();

    expect(spectator.query('lib-error-state')).toExist();
  });
});
