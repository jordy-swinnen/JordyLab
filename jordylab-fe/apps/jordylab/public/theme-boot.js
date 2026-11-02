(function () {
  var storageKey = 'jordylab.theme';
  var themeIds = ['content', 'oxblood', 'flare', 'paper', 'glasshouse', 'heather'];
  var defaultDarkThemeId = 'content';
  var defaultLightThemeId = 'paper';
  var preference = 'system';

  try {
    var stored = window.localStorage.getItem(storageKey);
    if (stored === 'system' || themeIds.indexOf(stored) >= 0) {
      preference = stored;
    }
  } catch (error) {
    preference = 'system';
  }

  var theme = preference;
  if (preference === 'system') {
    var deviceIsLight = window.matchMedia && window.matchMedia('(prefers-color-scheme: light)').matches;
    theme = deviceIsLight ? defaultLightThemeId : defaultDarkThemeId;
  }

  document.documentElement.setAttribute('data-theme', theme);
})();
