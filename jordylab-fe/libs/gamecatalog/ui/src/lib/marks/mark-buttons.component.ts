import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MARK_LABELS, MarkType } from '@jordylab-fe/gamecatalog/api';

const MARKS: MarkType[] = ['WANT_TO_PLAY', 'PLAYED_LIKED', 'PLAYED_DISLIKED'];

const PRESSED: Record<MarkType, string> = {
  WANT_TO_PLAY: 'aria-pressed:border-mark-want aria-pressed:bg-mark-want/15 aria-pressed:text-mark-want',
  PLAYED_LIKED: 'aria-pressed:border-mark-liked aria-pressed:bg-mark-liked/15 aria-pressed:text-mark-liked',
  PLAYED_DISLIKED: 'aria-pressed:border-mark-disliked aria-pressed:bg-mark-disliked/15 aria-pressed:text-mark-disliked',
};

/**
 * The three marks as mutually exclusive toggle buttons, 44 px each. Pressing another mark replaces yours; pressing the one
 * you hold clears it. With {@code compact} only the icon shows (a card), otherwise icon and words (the game page).
 */
@Component({
  selector: 'lib-mark-buttons',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mark-buttons.component.html',
})
export class MarkButtonsComponent {
  myMark = input<MarkType | null>(null);
  disabled = input(false);
  compact = input(false);

  markChange = output<MarkType>();

  protected readonly marks = MARKS.map((value) => ({ value, label: MARK_LABELS[value], pressedClass: PRESSED[value] }));
}
