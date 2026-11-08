import { afterNextRender, Injector } from '@angular/core';

/**
 * Closing a history returns focus to the History button of the row it came from. That row is rendered again only after
 * the close, so the focus waits for the next render (spec 023).
 */
export function returnFocusToHistoryButton(
  host: HTMLElement,
  injector: Injector,
  positionId: string | null,
): void {
  if (!positionId) {
    return;
  }

  afterNextRender(
    () =>
      host
        .querySelector<HTMLButtonElement>(
          `[data-history-button="${positionId}"]`,
        )
        ?.focus(),
    { injector },
  );
}
