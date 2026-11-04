import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { MachinesApiService } from './machines-api.service';
import { CreatedMachine, Machine } from './machines.models';
import { MachinesStore } from './machines.store';
import { aMachineMock } from './mocks/machine.model.mock';

describe('MachinesStore', () => {
  const getMachines = vi.fn<MachinesApiService['getMachines']>();
  const createMachine = vi.fn<MachinesApiService['createMachine']>();
  const revoke = vi.fn<MachinesApiService['revoke']>();

  let store: MachinesStore;

  beforeEach(() => {
    vi.clearAllMocks();
    getMachines.mockReturnValue(of<Machine[]>([aMachineMock()]));
    createMachine.mockReturnValue(
      of<CreatedMachine>({ id: 'm1', name: 'JordyBox', token: 'jlmt_secret' }),
    );
    revoke.mockReturnValue(of(undefined));

    TestBed.configureTestingModule({
      providers: [
        MachinesStore,
        {
          provide: MachinesApiService,
          useValue: { getMachines, createMachine, revoke },
        },
      ],
    });

    store = TestBed.inject(MachinesStore);
  });

  it('loads the machines on creation', () => {
    expect(getMachines).toHaveBeenCalled();
    expect(store.machines()).toHaveLength(1);
    expect(store.loading()).toBe(false);
  });

  it('creates a machine and exposes the one-time token', () => {
    store.create('JordyBox');

    expect(createMachine).toHaveBeenCalledWith('JordyBox');
    expect(store.created()).toEqual({ id: 'm1', name: 'JordyBox', token: 'jlmt_secret' });
    expect(store.creating()).toBe(false);
    // The list is reloaded so the new machine appears.
    expect(getMachines).toHaveBeenCalledTimes(2);
  });

  it('dismisses the one-time token', () => {
    store.create('JordyBox');

    store.dismissCreated();

    expect(store.created()).toBeNull();
  });

  it('revokes a machine and reloads the list', () => {
    store.revoke('m1');

    expect(revoke).toHaveBeenCalledWith('m1');
    expect(getMachines).toHaveBeenCalledTimes(2);
  });

  it('maps an already-revoked rejection to a message', () => {
    revoke.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 409, error: { reason: 'ALREADY_REVOKED' } }),
      ),
    );

    store.revoke('m1');

    expect(store.error()).toBe('That machine token is already revoked.');
  });

  it('maps a missing machine to a message', () => {
    revoke.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 404, error: { reason: 'MACHINE_NOT_FOUND' } }),
      ),
    );

    store.revoke('m1');

    expect(store.error()).toBe('That machine no longer exists.');
  });

  it('reports a failed load', () => {
    getMachines.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));

    store.load();

    expect(store.error()).toBe('Failed to load machines.');
    expect(store.loading()).toBe(false);
  });
});
