# settings-nav

Settings UI that the app shell renders outside the lazy-loaded Settings routes — today the admin nav badge with the
number of pending sign-ups (`PendingCountBadgeComponent`). Kept separate from `settings-ui` so importing it does not
pull the Settings pages into the initial bundle (spec 011 BUG-001).

Run `bunx nx test settings-nav` to execute the unit tests.
