import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FnaApiService, PortfolioPosition } from '@jordylab-fe/fna/api';
import { PortfolioManagerViewComponent } from './portfolio-manager-view.component';

@Component({
  selector: 'lib-portfolio-manager',
  standalone: true,
  imports: [PortfolioManagerViewComponent],
  templateUrl: './portfolio-manager.component.html',
})
export class PortfolioManagerComponent implements OnInit {
  #api = inject(FnaApiService);

  positions = signal<PortfolioPosition[]>([]);

  totalWorth = computed(() => {
    return this.positions().reduce((sum, position) => {
      if (position.lastPrice !== null) {
        return sum + position.shareCount * position.lastPrice;
      }

      return sum;
    }, 0);
  });

  hasAnyPrices = computed(() => {
    return this.positions().some((position) => position.lastPrice !== null);
  });

  ngOnInit(): void {
    this.#loadPositions();
  }

  addPosition(event: { ticker: string; shares: number }): void {
    this.#api.upsertPosition(event.ticker, event.shares).subscribe(() => {
      this.#loadPositions();
    });
  }

  deletePosition(id: string): void {
    this.#api.removePosition(id).subscribe(() => this.#loadPositions());
  }

  #loadPositions(): void {
    this.#api
      .getPortfolio()
      .subscribe((positions) => this.positions.set(positions));
  }
}
