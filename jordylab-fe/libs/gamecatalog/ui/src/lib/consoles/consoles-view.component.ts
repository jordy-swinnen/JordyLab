import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  ConsoleGameListItem,
  ConsoleImpact,
  ConsoleSearchResult,
  GameConsole,
  KnownConsole,
} from '@jordylab-fe/gamecatalog/api';
import { PlatformChipComponent } from '../chips/platform-chip.component';
import { coverInitials, coverPalette } from '../cover';

export interface ConsoleRemoval {
  console: GameConsole;
  impact: ConsoleImpact;
}

@Component({
  selector: 'lib-consoles-view',
  standalone: true,
  imports: [RouterLink, PlatformChipComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './consoles-view.component.html',
})
export class ConsolesViewComponent {
  consoles = input.required<GameConsole[]>();
  loading = input.required<boolean>();
  error = input.required<string | null>();
  suggestions = input.required<KnownConsole[]>();
  selectedId = input.required<string | null>();
  games = input.required<ConsoleGameListItem[]>();
  loadingGames = input.required<boolean>();
  results = input.required<ConsoleSearchResult[]>();
  searching = input.required<boolean>();
  noMatchFor = input.required<string | null>();
  busy = input.required<boolean>();
  removal = input.required<ConsoleRemoval | null>();
  lastAdded = input.required<string | null>();

  suggest = output<string>();
  addConsole = output<{ platform: string; name: string }>();
  renameConsole = output<{ id: string; name: string }>();
  requestRemoval = output<GameConsole>();
  confirmRemoval = output<void>();
  cancelRemoval = output<void>();
  selectConsole = output<string>();
  searchGames = output<string>();
  addGameFromSearch = output<ConsoleSearchResult>();
  addGameByTitle = output<string>();
  relinkGame = output<{ gameId: string; igdbGameId: number }>();
  removeGame = output<string>();

  protected readonly renamingId = signal<string | null>(null);
  protected readonly relinkingGameId = signal<string | null>(null);
  protected readonly palette = coverPalette;
  protected readonly initials = coverInitials;

  protected selected(): GameConsole | null {
    return this.consoles().find((console) => console.id === this.selectedId()) ?? null;
  }

  protected onSuggest(event: Event): void {
    this.suggest.emit((event.target as HTMLInputElement).value);
  }

  protected onAddConsole(platform: HTMLInputElement, name: HTMLInputElement): void {
    if (!platform.value.trim()) {
      return;
    }
    this.addConsole.emit({ platform: platform.value, name: name.value });
    platform.value = '';
    name.value = '';
  }

  protected onSearch(event: Event): void {
    this.searchGames.emit((event.target as HTMLInputElement).value);
  }

  protected onAddByTitle(input: HTMLInputElement): void {
    const title = input.value.trim();
    if (!title) {
      return;
    }
    this.addGameByTitle.emit(title);
    input.value = '';
  }

  protected onSelectConsole(event: Event): void {
    this.selectConsole.emit((event.target as HTMLSelectElement).value);
  }

  protected onRename(console: GameConsole, input: HTMLInputElement): void {
    this.renameConsole.emit({ id: console.id, name: input.value });
    this.renamingId.set(null);
  }

  protected onRelink(result: ConsoleSearchResult): void {
    const gameId = this.relinkingGameId();
    if (gameId) {
      this.relinkGame.emit({ gameId, igdbGameId: result.igdbGameId });
      this.relinkingGameId.set(null);
    }
  }

  protected startRelink(gameId: string): void {
    this.relinkingGameId.set(this.relinkingGameId() === gameId ? null : gameId);
  }
}
