import { Component, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { HlmBadgeDirective } from '@spartan-ng/ui-badge-helm';
import { HlmInputDirective } from '@spartan-ng/ui-input-helm';
import { ChatMessage } from './game-chat.component';

@Component({
  selector: 'lib-game-chat-view',
  standalone: true,
  imports: [RouterLink, HlmBadgeDirective, HlmInputDirective],
  templateUrl: './game-chat-view.component.html',
})
export class GameChatViewComponent {
  messages = input.required<ChatMessage[]>();
  asking = input.required<boolean>();

  ask = output<string>();

  onSubmit(input: HTMLInputElement) {
    this.ask.emit(input.value);
    input.value = '';
  }
}
