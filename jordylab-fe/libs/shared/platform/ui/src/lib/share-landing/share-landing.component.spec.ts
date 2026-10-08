import { signal } from '@angular/core';
import { Router } from '@angular/router';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AuthService } from '@jordylab-fe/shared/auth';
import { ShareTargetService } from '@jordylab-fe/shared/platform/api';
import { ShareLandingComponent } from './share-landing.component';

describe('ShareLandingComponent', () => {
  const pendingShare = signal<{ title: string; texts: string[]; files: unknown[] } | null>(null);
  const isAdmin = signal(false);
  const clear = vi.fn();
  const submitToFna = vi.fn();
  const navigate = vi.fn().mockResolvedValue(true);
  const navigateByUrl = vi.fn().mockResolvedValue(true);

  const createComponent = createComponentFactory({
    component: ShareLandingComponent,
    providers: [
      {
        provide: ShareTargetService,
        useValue: { pendingShare: pendingShare.asReadonly(), clear, submitToFna },
      },
      { provide: AuthService, useValue: { isAdmin: isAdmin.asReadonly() } },
      { provide: Router, useValue: { navigate, navigateByUrl } },
    ],
  });

  let spectator: Spectator<ShareLandingComponent>;

  beforeEach(() => {
    pendingShare.set(null);
    isAdmin.set(false);
    clear.mockClear();
    submitToFna.mockReset();
    navigate.mockClear();
    navigateByUrl.mockClear();
    spectator = createComponent();
  });

  it('renders nothing when there is no pending share', () => {
    expect(spectator.query('.jordylab-share-landing')).toBeFalsy();
  });

  it('shows the shared text and only "Ask LibBot" for a guest', () => {
    pendingShare.set({ title: 'A page', texts: ['https://example.com'], files: [] });
    spectator.detectChanges();

    expect(spectator.element).toHaveText('https://example.com');
    const buttons = spectator.queryAll('button').map((button) => button.textContent?.trim());
    expect(buttons).toEqual(['Ask LibBot']);
  });

  it('also shows "Save to FNA" for the admin', () => {
    pendingShare.set({ title: 'A page', texts: ['https://example.com'], files: [] });
    isAdmin.set(true);
    spectator.detectChanges();

    const buttons = spectator.queryAll('button').map((button) => button.textContent?.trim());
    expect(buttons).toEqual(['Ask LibBot', 'Save to FNA']);
  });

  it('clears the share and navigates to the pre-filled chat on "Ask LibBot"', () => {
    pendingShare.set({ title: 'A page', texts: ['https://example.com'], files: [] });
    spectator.detectChanges();

    spectator.click('button');

    expect(clear).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/games/libbot'], {
      queryParams: { prefill: 'https://example.com' },
    });
  });

  it('submits to FNA, clears the share, and navigates to articles on "Save to FNA"', async () => {
    pendingShare.set({ title: 'A page', texts: ['https://example.com'], files: [] });
    isAdmin.set(true);
    submitToFna.mockResolvedValueOnce(undefined);
    spectator.detectChanges();

    spectator.click('button:last-of-type');
    await Promise.resolve();
    await Promise.resolve();
    spectator.detectChanges();

    expect(submitToFna).toHaveBeenCalledWith('https://example.com');
    expect(clear).toHaveBeenCalledTimes(1);
    expect(navigateByUrl).toHaveBeenCalledWith('/fna/articles');
  });

  it('shows an error and does not clear the share when the FNA submission fails', async () => {
    pendingShare.set({ title: 'A page', texts: ['https://example.com'], files: [] });
    isAdmin.set(true);
    submitToFna.mockRejectedValueOnce(new Error('network error'));
    spectator.detectChanges();

    spectator.click('button:last-of-type');
    await Promise.resolve();
    await Promise.resolve();
    spectator.detectChanges();

    expect(clear).not.toHaveBeenCalled();
    expect(spectator.query('[role="alert"]')).toHaveText('Could not save to FNA. Try again.');
  });
});
