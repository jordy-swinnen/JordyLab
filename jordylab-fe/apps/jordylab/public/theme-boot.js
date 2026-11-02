(function () {
  const storageKey = 'jordylab.theme';
  const themeIds = ['content', 'oxblood', 'flare', 'paper', 'glasshouse', 'heather'];
  const defaultDarkThemeId = 'content';
  const defaultLightThemeId = 'paper';
  let preference = 'system';

  try {
    const stored = window.localStorage.getItem(storageKey);
    if (stored === 'system' || themeIds.indexOf(stored) >= 0) {
      preference = stored;
    }
  } catch {
    preference = 'system';
  }

  let theme = preference;
  if (preference === 'system') {
    const deviceIsLight = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches;
    theme = deviceIsLight ? defaultLightThemeId : defaultDarkThemeId;
  }

  document.documentElement.setAttribute('data-theme', theme);
})();
