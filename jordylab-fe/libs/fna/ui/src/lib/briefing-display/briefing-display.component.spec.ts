import { signal } from '@angular/core';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { aBriefingMock, Briefing, BriefingStore } from '@jordylab-fe/fna/api';
import { BriefingDisplayComponent } from './briefing-display.component';

describe('BriefingDisplayComponent', () => {
  const briefing = signal<Briefing | null>(null);
  const loading = signal(false);
  const error = signal<string | null>(null);
  const generate = vi.fn();

  const storeMock = {
    briefing: briefing.asReadonly(),
    loading: loading.asReadonly(),
    error: error.asReadonly(),
    load: vi.fn(),
    generate,
  };

  const createComponent = createComponentFactory({
    component: BriefingDisplayComponent,
    providers: [{ provide: BriefingStore, useValue: storeMock }],
  });

  let spectator: Spectator<BriefingDisplayComponent>;

  beforeEach(() => {
    briefing.set(null);
    loading.set(false);
    error.set(null);
    generate.mockClear();
    spectator = createComponent();
  });

  it('renders the briefing display view', () => {
    spectator.detectChanges();

    expect(spectator.query('lib-briefing-display-view')).toBeTruthy();
  });

  it('displays a populated briefing', () => {
    briefing.set(aBriefingMock());
    spectator.detectChanges();

    const model = spectator
      .queryAll('span.uppercase')
      .find((element) => element.textContent?.includes('claude-sonnet-5'));
    expect(model).toHaveText('claude-sonnet-5');
    expect(spectator.query('button')).toHaveText('Regenerate');
  });

  it('displays a loading state while fetching the briefing', () => {
    loading.set(true);
    spectator.detectChanges();

    expect(spectator.query('lib-briefing-display-view p.text-sm')).toHaveText('Analyzing your portfolio…');
  });

  it('displays an empty state when no briefing exists', () => {
    spectator.detectChanges();

    expect(spectator.query('p.text-xl')).toHaveText('No briefing generated yet');
    expect(spectator.query('button')).toHaveText('Generate Briefing');
  });

  it('surfaces an error state when the store reports an error', () => {
    error.set('Failed to load briefing.');
    spectator.detectChanges();

    expect(spectator.query('div.text-destructive')).toHaveText('Failed to load briefing.');
  });

  it('triggers briefing generation on button click', () => {
    spectator.detectChanges();

    spectator.click('button');

    expect(generate).toHaveBeenCalled();
  });
});
