import { signal } from '@angular/core';
import { createPipeFactory, SpectatorPipe } from '@ngneat/spectator/vitest';
import { MarkdownPipe } from './markdown.pipe';

describe('MarkdownPipe', () => {
  const content = signal<string | null>('');

  const createPipe = createPipeFactory({
    pipe: MarkdownPipe,
    template: `<div [innerHTML]="content() | markdown"></div>`,
  });

  let spectator: SpectatorPipe<MarkdownPipe>;

  beforeEach(() => {
    content.set('');
    spectator = createPipe({ hostProps: { content } });
  });

  const render = (markdown: string | null) => {
    content.set(markdown);
    spectator.detectChanges();
    return spectator.element.querySelector('div') as HTMLDivElement;
  };

  it('renders markdown formatting as HTML', () => {
    const output = render(
      '## Outlook\n\n- **Buy** the dip\n\n[source](https://example.com/article)',
    );

    expect(output.querySelector('h2')).toHaveText('Outlook');
    expect(output.querySelector('li strong')).toHaveText('Buy');
    expect(output.querySelector('a')).toHaveAttribute(
      'href',
      'https://example.com/article',
    );
  });

  it('strips script tags embedded in the markdown', () => {
    const output = render('Intro\n\n<script>window.pwned = true</script>');

    expect(output.querySelector('script')).toBeNull();
    expect(output.innerHTML).not.toContain('pwned');
  });

  it('strips inline event handlers from raw HTML in the markdown', () => {
    const output = render('<img src="x" onerror="window.pwned = true">');

    expect(output.innerHTML).not.toContain('onerror');
  });

  it('neutralises javascript: links', () => {
    const output = render('[click me](javascript:alert(1))');

    expect(output.querySelector('a')?.getAttribute('href') ?? '').not.toMatch(
      /^javascript:/,
    );
  });

  it('renders nothing for an empty briefing', () => {
    expect(render(null).innerHTML).toBe('');
  });
});
