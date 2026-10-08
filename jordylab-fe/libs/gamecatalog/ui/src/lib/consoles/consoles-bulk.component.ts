import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ConsoleBulkItem, ConsoleBulkLine, ConsoleStore } from '@jordylab-fe/gamecatalog/api';

/**
 * Paste a list of titles onto one console: review what IGDB matched (or did not), untick what you do not want, and add
 * the rest. Nothing is saved before the confirm step, and a game already on the console is shown but not added again.
 */
@Component({
  selector: 'lib-consoles-bulk',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <div class="flex flex-col gap-3.5">
      <div class="eyebrow">Game Catalog · consoles</div>
      <h2 class="page-title">Paste a list</h2>
    </div>
    @if (console(); as current) {
      <p class="mt-5 text-[15px] text-secondary-foreground">
        One title per line, added to <strong>{{ current.name }}</strong
        >.
      </p>
    }

    @if (error(); as message) {
      <p class="mt-6 rounded-xl border border-destructive/40 bg-destructive/10 p-4 text-sm" role="alert">{{ message }}</p>
    }

    @if (summary(); as result) {
      <section class="panel mt-8 px-6 py-5" data-testid="bulk-summary">
        <p class="font-display text-xl font-bold">{{ result.added.length }} added</p>
        @if (result.skipped.length > 0) {
          <p class="mt-2 text-sm text-secondary-foreground">Already there: {{ result.skipped.join(', ') }}</p>
        }
        @for (failure of result.failed; track failure.line) {
          <p class="mt-2 text-sm text-destructive">{{ failure.line }}: {{ failure.reason }}</p>
        }
        <a routerLink="/games/consoles" class="mt-4 inline-block font-semibold text-primary">Back to consoles</a>
      </section>
    } @else if (lines().length === 0) {
      <label class="mt-8 flex flex-col gap-2">
        <span class="mono-label">Titles</span>
        <textarea
          #text
          rows="10"
          class="rounded-xl border border-input bg-background p-3"
          placeholder="Mario Kart 8 Deluxe&#10;Super Mario Odyssey&#10;Celeste"
          data-testid="bulk-text"
        ></textarea>
      </label>
      <button
        type="button"
        class="mt-4 h-11 rounded-xl bg-primary px-5 font-semibold text-primary-foreground disabled:opacity-50"
        [disabled]="busy()"
        data-testid="bulk-review"
        (click)="review(text.value)"
      >
        {{ busy() ? 'Reviewing…' : 'Review the list' }}
      </button>
    } @else {
      <ul class="mt-8 flex flex-col gap-3" data-testid="bulk-lines">
        @for (line of lines(); track line.line) {
          <li class="panel flex items-center gap-4 px-4 py-3">
            <input
              type="checkbox"
              class="h-5 w-5"
              [checked]="isTicked(line)"
              [disabled]="line.status === 'ALREADY_PRESENT'"
              [attr.aria-label]="'Add ' + line.line"
              (change)="toggle(line)"
            />
            <div class="min-w-0 flex-1">
              <div class="truncate font-semibold">{{ line.line }}</div>
              <div class="text-sm text-muted-foreground">
                @switch (line.status) {
                  @case ('MATCHED') {
                    Matches “{{ line.match?.title }}”@if (line.match?.releaseYear) { ({{ line.match?.releaseYear }}) }
                  }
                  @case ('NO_MATCH') {
                    No IGDB match: it will be added by title
                  }
                  @case ('ALREADY_PRESENT') {
                    Already on this console
                  }
                }
              </div>
            </div>
          </li>
        }
      </ul>
      <div class="mt-6 flex gap-3">
        <button
          type="button"
          class="h-11 rounded-xl bg-primary px-5 font-semibold text-primary-foreground disabled:opacity-50"
          [disabled]="busy() || tickedCount() === 0"
          data-testid="bulk-confirm"
          (click)="confirm()"
        >
          Add {{ tickedCount() }} {{ tickedCount() === 1 ? 'game' : 'games' }}
        </button>
        <button type="button" class="h-11 rounded-xl border border-input px-5 font-semibold" (click)="startOver()">
          Start over
        </button>
      </div>
    }
  `,
})
export class ConsolesBulkComponent {
  readonly #store = inject(ConsoleStore);
  readonly #route = inject(ActivatedRoute);
  readonly #params = toSignal(this.#route.paramMap);
  readonly #unticked = signal<Set<string>>(new Set());

  readonly lines = this.#store.bulkLines;
  readonly summary = this.#store.bulkSummary;
  readonly busy = this.#store.busy;
  readonly error = this.#store.error;
  readonly console = this.#store.selected;
  readonly tickedCount = computed(() => this.lines().filter((line) => this.isTicked(line)).length);

  constructor() {
    this.#store.resetBulk();
    this.#store.load();
    effect(() => {
      const id = this.#params()?.get('id');
      if (id && this.#store.selectedId() !== id && this.#store.consoles().some((console) => console.id === id)) {
        this.#store.selectConsole(id);
      }
    });
  }

  review(text: string): void {
    this.#unticked.set(new Set());
    this.#store.previewBulk(text);
  }

  isTicked(line: ConsoleBulkLine): boolean {
    return line.status !== 'ALREADY_PRESENT' && !this.#unticked().has(line.line);
  }

  toggle(line: ConsoleBulkLine): void {
    this.#unticked.update((set) => {
      const next = new Set(set);
      if (next.has(line.line)) {
        next.delete(line.line);
      } else {
        next.add(line.line);
      }

      return next;
    });
  }

  confirm(): void {
    const items: ConsoleBulkItem[] = this.lines()
      .filter((line) => this.isTicked(line))
      .map((line) => ({
        line: line.line,
        igdbGameId: line.status === 'MATCHED' ? (line.match?.igdbGameId ?? null) : null,
        title: line.status === 'MATCHED' ? null : line.line,
      }));
    this.#store.confirmBulk(items);
  }

  startOver(): void {
    this.#store.resetBulk();
  }
}
