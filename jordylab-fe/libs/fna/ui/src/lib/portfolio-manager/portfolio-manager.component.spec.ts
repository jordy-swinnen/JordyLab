import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { BehaviorSubject } from 'rxjs';
import { FnaApiService } from '@jordylab-fe/fna/api';
import { PortfolioManagerComponent } from './portfolio-manager.component';

class FnaApiServiceMock {
  private portfolioSubject = new BehaviorSubject([]);
  private upsertSubject = new BehaviorSubject(null);
  private removeSubject = new BehaviorSubject(null);
  getPortfolio = vi.fn(() => this.portfolioSubject.asObservable());
  upsertPosition = vi.fn(() => this.upsertSubject.asObservable());
  removePosition = vi.fn(() => this.removeSubject.asObservable());

  setPortfolio(positions: unknown[]) {
    this.portfolioSubject.next(positions);
  }

  failPortfolio() {
    this.portfolioSubject.error(new Error('network error'));
  }
}

describe('PortfolioManagerComponent', () => {
  const createComponent = createComponentFactory({
    component: PortfolioManagerComponent,
    providers: [{ provide: FnaApiService, useClass: FnaApiServiceMock }],
  });

  it('renders the portfolio manager view', () => {
    const spectator: Spectator<PortfolioManagerComponent> = createComponent();
    spectator.detectChanges();

    expect(spectator.query('lib-portfolio-manager-view')).toBeTruthy();
  });

  it('displays populated positions', () => {
    const spectator: Spectator<PortfolioManagerComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.setPortfolio([
      { id: 'p1', ticker: 'ABI', shareCount: 10, lastPrice: 70.5, lastPriceFetchedAt: '2026-08-01T06:00:00Z' },
      { id: 'p2', ticker: 'KBC', shareCount: 5, lastPrice: null, lastPriceFetchedAt: null },
    ]);
    spectator.detectChanges();

    expect(spectator.query('td')).toHaveText('ABI');
    expect(spectator.query('p.text-2xl')).toHaveText('€705.00');
  });

  it('displays an empty state when no positions exist', () => {
    const spectator: Spectator<PortfolioManagerComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.setPortfolio([]);
    spectator.detectChanges();

    expect(spectator.query('h2')).toHaveText('Portfolio');
    expect(spectator.query('span.uppercase')).toHaveText('0 positions');
  });

  it('surfaces an error state when the API call fails', () => {
    const spectator: Spectator<PortfolioManagerComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.failPortfolio();
    spectator.detectChanges();

    expect(spectator.query('div.text-destructive')).toHaveText('Failed to load portfolio.');
  });

  it('adds a position and reloads the portfolio', () => {
    const spectator: Spectator<PortfolioManagerComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.setPortfolio([]);
    spectator.detectChanges();

    spectator.component.addPosition({ ticker: 'ABI', shares: 10 });

    expect(service.upsertPosition).toHaveBeenCalledWith('ABI', 10);
  });

  it('deletes a position and reloads the portfolio', () => {
    const spectator: Spectator<PortfolioManagerComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    spectator.detectChanges();

    spectator.component.deletePosition('p1');

    expect(service.removePosition).toHaveBeenCalledWith('p1');
  });
});
