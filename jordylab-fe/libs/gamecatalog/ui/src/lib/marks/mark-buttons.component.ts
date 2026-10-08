import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { MARK_LABELS, MarkType } from '@jordylab-fe/gamecatalog/api';

const MARKS: MarkType[] = ['WANT_TO_PLAY', 'PLAYED_LIKED', 'PLAYED_DISLIKED'];

const PRESSED: Record<MarkType, string> = {
  WANT_TO_PLAY: 'aria-pressed:border-[#A99BFF] aria-pressed:bg-[#A99BFF]/15 aria-pressed:text-[#A99BFF]',
  PLAYED_LIKED: 'aria-pressed:border-[#FF8FB1] aria-pressed:bg-[#FF8FB1]/15 aria-pressed:text-[#FF8FB1]',
  PLAYED_DISLIKED: 'aria-pressed:border-[#98A2B3] aria-pressed:bg-[#98A2B3]/15 aria-pressed:text-[#98A2B3]',
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
