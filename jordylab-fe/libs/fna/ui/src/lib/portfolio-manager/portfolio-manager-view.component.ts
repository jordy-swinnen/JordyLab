import { Component, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { PortfolioPosition } from '@jordylab-fe/fna/api';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

@Component({
  selector: 'lib-portfolio-manager-view',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, HlmButtonDirective],
  templateUrl: './portfolio-manager-view.component.html',
})
export class PortfolioManagerViewComponent {
  positions = input.required<PortfolioPosition[]>();
  totalWorth = input.required<number>();
  hasAnyPrices = input.required<boolean>();

  addPosition = output<{ ticker: string; shares: number }>();
  deletePosition = output<string>();

  newTicker = signal('');
  newShares = signal<number | null>(null);

  onAddPosition(): void {
    const ticker = this.newTicker();
    const shares = this.newShares();
    if (!ticker || shares === null) return;

    this.addPosition.emit({ ticker, shares });
    this.newTicker.set('');
    this.newShares.set(null);
  }
}
