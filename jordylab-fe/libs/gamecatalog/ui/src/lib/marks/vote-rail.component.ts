import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MarkType, VoteTotals } from '@jordylab-fe/gamecatalog/api';
import { MarkChipComponent } from '../chips/mark-chip.component';

/**
 * The public totals on a cover: only the marks somebody gave, so a much-wanted game is noticed while scanning. The person's
 * own mark has an outline. Reads as one phrase to a screen reader ("5 people want to play this").
 */
@Component({
  selector: 'lib-vote-rail',
  standalone: true,
  imports: [MarkChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './vote-rail.component.html',
})
export class VoteRailComponent {
  votes = input.required<VoteTotals>();
  myMark = input<MarkType | null>(null);

  protected readonly visible = computed(() => {
    const votes = this.votes();

    return (
      [
        { mark: 'WANT_TO_PLAY', count: votes.wantToPlay },
        { mark: 'PLAYED_LIKED', count: votes.playedLiked },
        { mark: 'PLAYED_DISLIKED', count: votes.playedDisliked },
      ] as { mark: MarkType; count: number }[]
    ).filter((entry) => entry.count > 0);
  });

  protected readonly description = computed(() => {
    const votes = this.votes();
    const parts: string[] = [];
    if (votes.wantToPlay > 0) {
      parts.push(`${votes.wantToPlay} ${votes.wantToPlay === 1 ? 'person wants' : 'people want'} to play this`);
    }
    if (votes.playedLiked > 0) {
      parts.push(`${votes.playedLiked} played and liked it`);
    }
    if (votes.playedDisliked > 0) {
      parts.push(`${votes.playedDisliked} played and disliked it`);
    }

    return parts.join(', ');
  });
}
