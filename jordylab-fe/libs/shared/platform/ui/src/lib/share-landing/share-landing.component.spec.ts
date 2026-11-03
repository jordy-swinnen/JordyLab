import { signal } from '@angular/core';
import { convertToParamMap } from '@angular/router';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { createComponentFactory, Spectator } from '@ngneat/spectator/vitest';
import { AuthService } from '@jordylab-fe/shared/auth';
import { of, throwError } from 'rxjs';
import { ShareLandingComponent, targetUrlFrom } from './share-landing.component';

describe('ShareLandingComponent', () => {
  const isAdmin = signal(false);
  const post = vi.fn(() => of({}));

  const createComponent = createComponentFactory({
    component: ShareLandingComponent,
    imports: [RouterModule.forRoot([])],
    providers: [],
  });

  let spectator: Spectator<ShareLandingComponent>;
  let navigate: ReturnType<typeof vi.spyOn>;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  function create(query: Record<string, string>, admin = false): Spectator<ShareLandingComponent> {
    isAdmin.set(admin);
    const component = createComponent({
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            queryParamMap: of(convertToParamMap(query)),
            snapshot: { queryParamMap: convertToParamMap(query) },
          },
        },
        { provide: HttpClient, useValue: { post } },
        { provide: AuthService, useValue: { isAdmin: isAdmin.asReadonly() } },
      ],
    });
    // The real Router (from RouterModule) computes the empty-state link's href; the navigations themselves are spied.
    navigate = vi.spyOn(component.inject(Router), 'navigate').mockResolvedValue(true);
    navigateByUrl = vi.spyOn(component.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    return component;
  }

  beforeEach(() => {
    post.mockReset().mockReturnValue(of({}));
  });

  it('shows the shared text and offers only Ask LibBot to a guest', () => {
    spectator = create({ text: 'Cozy games for four', url: 'https://example.test/list' });

    expect(spectator.query('.jordylab-share-landing__text')?.textContent).toContain('Cozy games for four');
    expect(spectator.queryAll('button').map((button) => button.textContent?.trim())).toEqual(['Ask LibBot']);
  });

  it('sends Ask LibBot to LibBot with the full shared text prefilled', () => {
    spectator = create({ text: 'Cozy games for four' });

    spectator.click('button');

    expect(navigate).toHaveBeenCalledWith(['/games/libbot'], { queryParams: { prefill: 'Cozy games for four' } });
  });

  it('offers Save to FNA to the admin and submits the shared url', async () => {
    spectator = create({ text: 'Worth a read', url: 'https://example.test/article' }, true);

    const save = spectator.queryAll('button').find((button) => button.textContent?.includes('Save to FNA'));
    expect(save).toBeTruthy();
    (save as HTMLButtonElement).click();
    await Promise.resolve();
    await Promise.resolve();

    expect(post).toHaveBeenCalledWith('/api/fna/articles/manual', { url: 'https://example.test/article' });
    expect(navigateByUrl).toHaveBeenCalledWith('/fna/articles');
  });

  it('extracts a link that arrived inside the shared text (edge case)', async () => {
    spectator = create({ text: 'Look at this https://example.test/shared-article.' }, true);

    const save = spectator.queryAll('button').find((button) => button.textContent?.includes('Save to FNA'));
    (save as HTMLButtonElement).click();
    await Promise.resolve();
    await Promise.resolve();

    expect(post).toHaveBeenCalledWith('/api/fna/articles/manual', { url: 'https://example.test/shared-article' });
  });

  it('disables Save to FNA with an explanation when the share has no link', () => {
    spectator = create({ text: 'Just some words' }, true);

    const save = spectator.queryAll('button').find((button) => button.textContent?.includes('Save to FNA'));

    expect((save as HTMLButtonElement).disabled).toBe(true);
    expect(spectator.query('.jordylab-share-landing')?.textContent).toContain('Save to FNA needs a link');
  });

  it('keeps the payload on screen and offers a retry when saving fails', async () => {
    post.mockReturnValue(throwError(() => new Error('500')));
    spectator = create({ url: 'https://example.test/article' }, true);

    const save = spectator.queryAll('button').find((button) => button.textContent?.includes('Save to FNA'));
    (save as HTMLButtonElement).click();
    await Promise.resolve();
    await Promise.resolve();
    spectator.detectChanges();

    expect(spectator.query('[role="alert"]')?.textContent).toContain('Could not save to FNA');
    expect(spectator.query('.jordylab-share-landing__text')?.textContent).toContain('https://example.test/article');
  });

  it('shows a calm empty state with a way into the Library when nothing was shared', () => {
    spectator = create({});

    expect(spectator.query('h2')?.textContent).toContain('Nothing was shared');
    expect(spectator.query('a')?.getAttribute('href')).toBe('/games/grid');
    expect(spectator.query('.jordylab-share-landing__text')).toBeFalsy();
  });
});

describe('targetUrlFrom', () => {
  it('prefers the url parameter', () => {
    expect(targetUrlFrom('https://a.test/x', 'see https://b.test/y')).toBe('https://a.test/x');
  });

  it('falls back to the first link inside the text and trims trailing punctuation', () => {
    expect(targetUrlFrom(null, 'Read this: https://b.test/y.')).toBe('https://b.test/y');
  });

  it('ignores a url parameter that is not a link', () => {
    expect(targetUrlFrom('not-a-link', null)).toBeNull();
  });

  it('returns null when there is no link at all', () => {
    expect(targetUrlFrom(null, 'just words')).toBeNull();
  });
});
