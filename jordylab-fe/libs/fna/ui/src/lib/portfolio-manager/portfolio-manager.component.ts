import { Component, inject, ChangeDetectionStrategy } from '@angular/core';
import { PortfolioStore } from '@jordylab-fe/fna/api';
import { PortfolioManagerViewComponent } from './portfolio-manager-view.component';

@Component({
  selector: 'lib-portfolio-manager',
  standalone: true,
  imports: [PortfolioManagerViewComponent],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './portfolio-manager.component.html',
})
export class PortfolioManagerComponent {
  #store = inject(PortfolioStore);

  positions = this.#store.positionRows;
  error = this.#store.error;
  totalWorth = this.#store.totalWorth;
  hasAnyPrices = this.#store.hasAnyPrices;

  addPosition(event: { ticker: string; shares: number }): void {
    this.#store.upsertPosition(event.ticker, event.shares);
  }

  deletePosition(id: string): void {
    this.#store.removePosition(id);
  }
}
