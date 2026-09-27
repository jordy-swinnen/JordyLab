import { Component, computed, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe, DecimalPipe } from '@angular/common';
import { PortfolioPositionRow } from '@jordylab-fe/fna/api';
import { HlmButtonDirective } from '@spartan-ng/ui-button-helm';

/** Swatch colours, assigned by row position so the allocation bar and the table agree. */
const SWATCHES = [
  'bg-primary',
  'bg-iris',
  'bg-foreground',
  'bg-deep',
  'bg-muted-foreground',
];

interface AllocationSegment {
  ticker: string;
  swatch: string;
  percent: number;
}

@Component({
  selector: 'lib-portfolio-manager-view',
  standalone: true,
  imports: [FormsModule, DatePipe, DecimalPipe, HlmButtonDirective],
  templateUrl: './portfolio-manager-view.component.html',
})
export class PortfolioManagerViewComponent {
  positions = input.required<PortfolioPositionRow[]>();
  totalWorth = input.required<number>();
  hasAnyPrices = input.required<boolean>();

  addPosition = output<{ ticker: string; shares: number }>();
  deletePosition = output<string>();

  protected readonly swatch = (index: number): string =>
    SWATCHES[index % SWATCHES.length];

  /** Share of total value per priced position; positions without a price have no slice. */
  protected readonly allocation = computed<AllocationSegment[]>(() => {
    const priced = this.positions()
      .map((position, index) => ({ position, index }))
      .filter(({ position }) => position.value !== null && position.value > 0);
    const total = priced.reduce(
      (sum, { position }) => sum + (position.value ?? 0),
      0,
    );

    return priced.map(({ position, index }) => ({
      ticker: position.ticker,
      swatch: this.swatch(index),
      percent: total === 0 ? 0 : ((position.value ?? 0) / total) * 100,
    }));
  });

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
