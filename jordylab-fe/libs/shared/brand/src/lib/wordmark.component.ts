import { Component, ChangeDetectionStrategy } from '@angular/core';

/** "Jordy" in a light weight next to "Lab" in a heavy weight, same face. */
@Component({
  selector: 'lib-wordmark',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `<span
    class="leading-none tracking-[-0.03em]"
    ><span class="font-normal text-foreground">Jordy</span
    ><span class="font-extrabold text-accent-text">Lab</span></span
  >`,
})
export class WordmarkComponent {}
