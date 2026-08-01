import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { Subject } from 'rxjs';
import { FnaApiService } from '@jordylab-fe/fna/api';
import { BriefingDisplayComponent } from './briefing-display.component';

class FnaApiServiceMock {
  private briefingSubject = new Subject();
  private triggerSubject = new Subject();
  getLatestBriefing = vi.fn(() => this.briefingSubject.asObservable());
  triggerBriefing = vi.fn(() => this.triggerSubject.asObservable());

  setLatestBriefing(briefing: unknown) {
    this.briefingSubject.next(briefing);
  }

  failLatestBriefing() {
    this.briefingSubject.error(new Error('network error'));
  }

  setTriggeredBriefing(briefing: unknown) {
    this.triggerSubject.next(briefing);
  }

  failTriggeredBriefing() {
    this.triggerSubject.error(new Error('network error'));
  }
}

describe('BriefingDisplayComponent', () => {
  const createComponent = createComponentFactory({
    component: BriefingDisplayComponent,
    providers: [{ provide: FnaApiService, useClass: FnaApiServiceMock }],
  });

  it('renders the briefing display view', () => {
    const spectator: Spectator<BriefingDisplayComponent> = createComponent();
    spectator.detectChanges();

    expect(spectator.query('lib-briefing-display-view')).toBeTruthy();
  });

  it('displays a populated briefing', () => {
    const spectator: Spectator<BriefingDisplayComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.setLatestBriefing({
      id: 'b1',
      generatedAt: '2026-08-01T06:30:00Z',
      content: '## Portfolio Impact',
      modelUsed: 'claude-sonnet-4-20250514',
    });
    spectator.detectChanges();

    const model = spectator.queryAll('span.uppercase')
        .find((element) => element.textContent?.includes('claude-sonnet-4-20250514'));
    expect(model).toHaveText('claude-sonnet-4-20250514');
    expect(spectator.query('button')).toHaveText('Regenerate');
  });

  it('displays a loading state while fetching the briefing', () => {
    const spectator: Spectator<BriefingDisplayComponent> = createComponent();

    expect(spectator.query('lib-briefing-display-view p.text-sm')).toHaveText('Analyzing your portfolio…');
  });

  it('displays an empty state when no briefing exists', () => {
    const spectator: Spectator<BriefingDisplayComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.setLatestBriefing(null);
    spectator.detectChanges();

    expect(spectator.query('p.text-xl')).toHaveText('No briefing generated yet');
    expect(spectator.query('button')).toHaveText('Generate Briefing');
  });

  it('surfaces an error state when the API call fails', () => {
    const spectator: Spectator<BriefingDisplayComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.failLatestBriefing();
    spectator.detectChanges();

    expect(spectator.query('div.text-destructive')).toHaveText('Failed to load briefing.');
  });

  it('triggers briefing generation on button click', () => {
    const spectator: Spectator<BriefingDisplayComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.setLatestBriefing(null);
    spectator.detectChanges();

    spectator.click('button');

    expect(service.triggerBriefing).toHaveBeenCalled();
  });

  it('surfaces an error state when generation fails', () => {
    const spectator: Spectator<BriefingDisplayComponent> = createComponent();
    const service = spectator.inject(FnaApiService) as unknown as FnaApiServiceMock;
    service.setLatestBriefing(null);
    service.failTriggeredBriefing();
    spectator.detectChanges();

    spectator.click('button');
    spectator.detectChanges();

    expect(spectator.query('div.text-destructive')).toHaveText('Failed to generate briefing.');
  });
});
