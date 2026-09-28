import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CapacitorShareTarget, ShareReceivedEvent } from '@capgo/capacitor-share-target';
import { firstValueFrom } from 'rxjs';
import { PlatformService } from './platform.service';

const SHARE_LANDING_ROUTE = '/mobile/share';

/**
 * Listens for the Android share-sheet target (spec US5, research D4/D6) and holds the payload
 * in memory only — never persisted across a restart (contracts/app-shell-contract.md), so a
 * killed app before the landing screen is handled simply drops the share, matching what the OS
 * itself would do. A no-op on web.
 */
@Injectable({ providedIn: 'root' })
export class ShareTargetService {
  readonly #platform = inject(PlatformService);
  readonly #router = inject(Router);
  readonly #http = inject(HttpClient);

  readonly #pendingShare = signal<ShareReceivedEvent | null>(null);
  readonly pendingShare = this.#pendingShare.asReadonly();

  listen(): void {
    if (!this.#platform.isNative()) {
      return;
    }
    CapacitorShareTarget.addListener('shareReceived', (event) => {
      this.#pendingShare.set(event);
      void this.#router.navigateByUrl(SHARE_LANDING_ROUTE);
    });
  }

  clear(): void {
    this.#pendingShare.set(null);
  }

  /** "Save to FNA" (admin only, D6) — the endpoint lives in `fna`, not `mobile`; see access-matrix.md. */
  async submitToFna(url: string): Promise<void> {
    await firstValueFrom(this.#http.post('/api/fna/articles/manual', { url }));
  }
}
