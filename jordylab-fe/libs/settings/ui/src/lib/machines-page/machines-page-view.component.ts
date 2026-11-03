import { Component, input, output, ChangeDetectionStrategy, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Machine } from '@jordylab-fe/settings/api';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { ErrorStateComponent, PageHeaderComponent, SkeletonRowsComponent } from '@jordylab-fe/shared/layout';

/**
 * Settings → Machines (spec 019 US5). The admin adds a machine, copies the one-time token into the scan client on that
 * device, and can revoke it. The token value is never listed again.
 */
@Component({
  selector: 'lib-machines-page-view',
  standalone: true,
  imports: [
    DatePipe,
    HlmBadgeDirective,
    HlmButtonDirective,
    HlmInputDirective,
    ErrorStateComponent,
    PageHeaderComponent,
    SkeletonRowsComponent,
  ],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './machines-page-view.component.html',
})
export class MachinesPageViewComponent {
  machines = input.required<Machine[]>();
  loading = input.required<boolean>();
  error = input.required<string | null>();
  creating = input.required<boolean>();
  created = input.required<{ id: string; name: string; token: string } | null>();

  create = output<string>();
  revoke = output<string>();
  reload = output<void>();
  dismissCreated = output<void>();

  protected readonly name = signal('');
  protected readonly confirmingRevokeId = signal<string | null>(null);
  protected readonly copied = signal(false);

  onCreate(): void {
    const value = this.name().trim();
    if (!value) {
      return;
    }
    this.create.emit(value);
    this.name.set('');
  }

  onRevoke(id: string): void {
    if (this.confirmingRevokeId() === id) {
      this.confirmingRevokeId.set(null);
      this.revoke.emit(id);

      return;
    }
    this.confirmingRevokeId.set(id);
  }

  async copyToken(token: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(token);
      this.copied.set(true);
    } catch {
      this.copied.set(false);
    }
  }

  dismiss(): void {
    this.copied.set(false);
    this.dismissCreated.emit();
  }
}
