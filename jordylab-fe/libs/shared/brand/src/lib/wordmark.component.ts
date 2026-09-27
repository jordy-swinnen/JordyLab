import { Component } from '@angular/core';

/** "Jordy" in a light weight next to "Lab" in a heavy weight, same face. */
@Component({
  selector: 'lib-wordmark',
  standalone: true,
  template: `<span
    class="font-display leading-none tracking-[-0.03em]"
    style="font-variation-settings: 'wdth' 88"
    ><span class="font-normal text-foreground">Jordy</span
    ><span class="font-extrabold text-primary">Lab</span></span
  >`,
})
export class WordmarkComponent {}
