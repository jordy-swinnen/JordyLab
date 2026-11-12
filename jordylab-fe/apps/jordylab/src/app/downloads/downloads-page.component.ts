import { ChangeDetectionStrategy, Component } from '@angular/core';
import { PageHeaderComponent } from '@jordylab-fe/shared/layout';

const RELEASES = 'https://github.com/jordy-swinnen/JordyLab/releases/latest/download';

interface Installer {
  readonly name: string;
  readonly platform: string;
  readonly file: string;
  readonly note: string;
}

/** The desktop sync app's installers (spec 024 FR-008, design W_Downloads.png). */
const INSTALLERS: readonly Installer[] = [
  {
    name: 'macOS',
    platform: 'Apple silicon',
    file: 'jordylab-desktop-macos-aarch64.zip',
    note: 'First launch: right-click the app and choose Open once.',
  },
  {
    name: 'Linux',
    platform: 'x86_64',
    file: 'jordylab-desktop-linux-x86_64',
    note: 'Make the file executable, then run it.',
  },
];

/**
 * Settings → Downloads (admin): the latest desktop sync app installers, published by the tag-triggered release workflow.
 */
@Component({
  selector: 'app-downloads-page',
  standalone: true,
  imports: [PageHeaderComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <lib-page-header
      title="Downloads"
      subtitle="The desktop sync app keeps a PC's game libraries in the catalog and updates itself."
    />
    <section class="mt-10 flex max-w-2xl flex-col gap-4">
      @for (installer of installers; track installer.file) {
        <article class="flex flex-col gap-2 rounded-xl border border-border p-4">
          <div class="flex items-baseline justify-between gap-4">
            <h3 class="font-semibold">{{ installer.name }}</h3>
            <span class="text-sm text-muted-foreground">{{ installer.platform }}</span>
          </div>
          <p class="text-sm text-muted-foreground">{{ installer.note }}</p>
          <a class="underline" [href]="releases + '/' + installer.file">Download for {{ installer.name }}</a>
        </article>
      }
      <p class="text-sm text-muted-foreground">
        After installing, open the app and connect this PC with the admin account. Nothing else needs to be downloaded.
      </p>
    </section>
  `,
})
export class DownloadsPageComponent {
  protected readonly installers = INSTALLERS;
  protected readonly releases = RELEASES;
}
