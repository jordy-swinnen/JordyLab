import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { AttachedGame, ChatMessage } from '@jordylab-fe/gamecatalog/api';
import { BrandMarkComponent } from '@jordylab-fe/shared/brand';
import { coverPalette } from '../cover';

interface TextSegment {
  text: string;
  bold: boolean;
}

@Component({
  selector: 'lib-game-chat-view',
  standalone: true,
  imports: [
    RouterLink,
    HlmBadgeDirective,
    HlmInputDirective,
    BrandMarkComponent,
  ],
  templateUrl: './game-chat-view.component.html',
})
export class GameChatViewComponent {
  messages = input.required<ChatMessage[]>();
  asking = input.required<boolean>();
  attachedGame = input.required<AttachedGame | null>();

  ask = output<string>();
  removeAttachment = output<void>();

  protected readonly palette = coverPalette;

  /** Splits `**bold**` markers from the model into segments so they render as emphasis, not asterisks. */
  protected segments(text: string): TextSegment[] {
    return text
      .split(/\*\*(.+?)\*\*/g)
      .map((part, index) => ({ text: part, bold: index % 2 === 1 }))
      .filter((segment) => segment.text.length > 0);
  }

  onSubmit(input: HTMLInputElement) {
    this.ask.emit(input.value);
    input.value = '';
  }
}
