# Feature Specification: Game Catalog Refinement and LibBot (AI) Rebuild

**Feature Branch**: `013-gamecatalog-refinement-ai`

**Created**: 2026-10-07

**Status**: Draft

**Module**: `gamecatalog` (existing), `shared` (AI layer), `settings` (host naming is shown there only as a link, no change)

**Builds on**: 002 (catalog), 004 (multi-host model, artwork slots), 005 (Steam library sources), 006 (admin and guest roles, guest chat limit), 009 (Switch games, which this spec generalises to consoles), and the AI research and rules filed on 2026-10-05 (`docs/research/spring-ai-architecture.md`, `docs/research/spring-ai-gap-analysis.md`, `jordylab-be/src/main/java/dev/jordy/jordylab/shared/ai/AGENTS.md`)

**Input**: User description: "Refinement of the game catalog app, found while testing manually; it must change before the app is shared with others. Redesign the filters (active filters always visible, the rest folded away). Let me give a host a display name that replaces the hostname everywhere. Replace the Owned/Family/Local source labels with Steam (Owned), Steam (Family), Emulated, Console. Give platform labels their official colors and installed/not-installed chips their own colors. At least 90% of games must have images and emulated games must be filled in without pressing a button. The AI chat is broken: it cites dozens of games when the question is irrelevant, cannot answer relevant questions, and has no memory within a session. Re-engineer it from the AI research and rules I added earlier; it must be robust, professional and user friendly. Remove the Nintendo Switch entry from Scan Sources, check what disabling a source does, and remove the confusing Refresh pending data button. Turn the Switch games tab into a Consoles tab (add a console, then add games to it, autocomplete for well-known consoles from generation 5 on), drop the physical/digital field, and make sure one game can live on several hosts, sources and platforms without duplicates. Rename the Chat tab and change its icon. New: admin buttons to refresh all deterministic data and all AI data (with a cost warning), a want-to-play mark and a played/liked mark that the AI also knows about, and AI that turns everyday phrasing such as 'six people here for game night' into filters."
---

## Context

The owner tested the catalog by hand and listed what must be fixed before friends are invited. The list is long, but one
thing matters most: **the AI feature is the reason the app exists, and it has to be airtight.** The remaining items
are either things the AI depends on (complete data, one entry per game, hosts the AI can name) or things a new visitor
hits in the first five minutes (filters, labels, colors, empty covers).

### What the current behaviour is (checked in the code on 2026-10-07)

| Reported | What actually happens today |
|---|---|
| A question about a cat cites dozens of games | When the AI's translation of the question produces no filter, the catalog returns up to 50 rows anyway, all of them are handed to the answer step and **every row is shown as a citation**, whether or not the answer mentions it. |
| "Which games can I play with four people" says nothing matches | The question becomes a hard database filter on "maximum local players". Most games have no player count stored (it is unknown, mostly for ROMs), and unknown is treated the same as "does not match". |
| "How many other games like this" cannot be understood | Every question is answered on its own; nothing from earlier in the conversation is available. |
| "Local" source label | It means "known only from a scan" (all ROMs and any installed game not found in a synced Steam library). It is a bookkeeping value, not something a person owns. |
| Disabling a scan source | Games that are only installed through that source disappear from the library, the filters and the chat (for example every ROM from that host). Games that are also in a synced Steam library stay, shown as not installed. Nothing is deleted and enabling the source brings them back. |
| One game on several platforms | A game has exactly one platform. The same title on Steam and on a console is two separate entries. |
| "Refresh pending data" | It processes a capped batch of games that still lack metadata, AI descriptions or multiplayer data. The same work already runs after each scan, in batches, which is why leftovers are common and why the button exists. |
| Covers | Artwork is looked up from Steam's image store and from a community ROM-thumbnail archive by exact title. A ROM whose name does not match exactly ends with a placeholder. |
| Nintendo Switch under Scan Sources | The manually managed Switch games hang off a virtual source that is listed next to real scan sources. |

### Design direction

The filter redesign (User Story 7) and the platform and status colors (User Story 9) are visual design work. They are to
be designed with the `frontend-design` skill during planning, as the owner asked. This spec fixes the behaviour and the
acceptance tests, not the look.

---

## Clarifications

### Session 2026-10-07

- Q: When a game has no stable external ID, should it merge into an existing game purely on the same normalised title? → A: Yes. Merge automatically on matching normalised title; games with different known IDs always stay separate, and a wrong merge is fixed by re-linking the game to the right database match.
- Q: If the same ROM is on two machines, is the ROM status one value per game or tracked per machine? → A: Per machine. Each machine holding the ROM has its own status; the card shows a summary and the game page lists each machine.
- Q: Should LibBot understand Dutch and answer in the language of the question, or is English enough? → A: English and Dutch. LibBot answers in the language of the question; the golden set includes Dutch cases; titles and game facts stay as they are.
- Q: How strongly should votes influence LibBot's recommendations? → A: Hard requirements first, then votes. Every game LibBot offers satisfies the stated requirements; among those, more want-to-play and liked votes (and fewer dislikes) rank higher, even over a slightly better description fit.
- Q: Should paid AI descriptions also be written for Steam games you own but have not installed? → A: No. Steam games keep Steam's own description and facts; AI writes descriptions only for games that have no store description (ROMs and console games). The About label states the real source, and AI-written text must be of comparable quality to Steam's (FR-063).
- Q: Should the system keep re-checking games that still lack a cover, facts or a description, or only retry after a scan, add or admin refresh? → A: Daily automatic retry for free lookups (covers, facts, player counts) in small batches; AI descriptions retry a limited number of times per game, then wait for the admin's "Regenerate AI data".

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ask LibBot and get a trustworthy answer (Priority: P1)

As someone using the catalog, I want to ask a question about my games in plain language and get a correct, concise
answer that only points to games that matter, so that I can rely on it at the moment I am deciding what to play.

**Why this priority**: This is the main reason the app exists and it is currently broken in three visible ways (wrong
citations, "no match" for answerable questions, errors). Nothing else in this spec matters if the AI cannot be trusted.

**Independent Test**: Run a fixed set of questions against the real catalog: relevant ones ("which games can I play with
four people", "what is the best racing game I have installed"), an unrelated one ("why is my cat black"), an ambiguous one
("how many are like this") and one that cannot be answered from the data. Check each answer and each citation list.

**Acceptance Scenarios**:

1. **Given** a catalog with local-multiplayer games, **When** I ask "which games can I play with four people", **Then**
   I get a short answer naming games that support at least four local players, each with a reference, and no unrelated
   games.
2. **Given** my question has nothing to do with the catalog ("why is my cat black"), **When** I ask it, **Then** the
   LibBot says briefly that it can only help with my game library, offers an example of what it can do, and shows **no**
   game references.
3. **Given** some games have unknown player counts, **When** I ask for four-player games, **Then** the answer separates
   games known to support four players from games where the count is unknown, and says so; it never answers "no games
   match" while such games exist.
4. **Given** my question is too vague to answer ("which one is better?" with no earlier context), **When** I send it,
   **Then** LibBot asks one short clarifying question instead of guessing.
5. **Given** an answer recommends three games, **When** it is shown, **Then** exactly those three games appear as
   references, and the total number never exceeds ten.
6. **Given** the AI service is unavailable, **When** I ask anything, **Then** I see a plain message saying LibBot
   cannot answer right now with a retry action, my question text is still in the box, and nothing counts against a
   guest's daily limit.
7. **Given** I opened a game's page and chose to ask about it, **When** I ask "does this support split screen", **Then** the
   answer is about that game.

---

### User Story 2 - Everyday phrasing becomes the right filter (Priority: P1)

As someone planning an evening, I want LibBot to understand my situation, not only explicit criteria, so that I can
say "there are six of us tonight" and get games that actually work for six.

**Why this priority**: This is the headline behaviour requested for the new LibBot and it is what makes it feel
smarter than the filter bar.

**Independent Test**: Send a list of situation phrases and check the constraints LibBot reports applying and the games it
returns.

**Acceptance Scenarios**:

1. **Given** I say "there are 6 people here today for game night", **When** LibBot answers, **Then** it only
   considers games that can be played together locally by at least six people, and ignores online-only and
   single-player games.
2. **Given** I say "we are with four players", **When** it answers, **Then** it only considers games for at least four
   local players.
3. **Given** I say "just me tonight, something short", **When** it answers, **Then** it only considers single-player
   games.
4. **Given** I say "my friends are online, what can we play together", **When** it answers, **Then** it only considers
   games with online multiplayer.
5. **Given** I say "what can I play on the Steam Deck right now", **When** it answers, **Then** it only considers games
   installed on that host (using the host's display name if one is set).
6. **Given** LibBot applied constraints I did not mean, **When** I read its answer, **Then** the constraints it applied are
   stated in plain words and I can correct them in my next message ("no, online is fine too").
7. **Given** I write in Dutch ("er zijn vandaag 6 mensen voor spelavond"), **When** LibBot answers, **Then** it applies the same constraints as in the English
   case and answers in Dutch; a question in English gets an English answer.

---

### User Story 3 - Follow-up questions within a conversation (Priority: P1)

As someone chatting with LibBot, I want it to remember what we just talked about, so that I can ask "how many
others like that" or "which of those is installed" without repeating myself.

**Why this priority**: A chat that forgets the previous message is not a conversation; the owner reported this directly,
and it is how people naturally ask.

**Independent Test**: Ask "which games should we play, two people here", then "which of those are installed", then
"how many other games like the first one do I have", and check each answer uses the earlier turns.

**Acceptance Scenarios**:

1. **Given** LibBot just recommended games, **When** I ask "which of those are installed", **Then** it answers about
   exactly those games.
2. **Given** it named a game, **When** I ask "how many other games like this do I have", **Then** it understands "this" as
   that game.
3. **Given** I choose "New conversation", **When** I ask a follow-up, **Then** the earlier turns are no longer used.
4. **Given** two people are signed in at the same time, **When** each chats, **Then** neither can see or influence the
   other's conversation.
5. **Given** I leave LibBot and later return in a new visit, **When** I open it, **Then** the conversation is empty;
   nothing is stored beyond the session.
6. **Given** a very long conversation, **When** I keep chatting, **Then** LibBot keeps working using the most recent
   turns, and never fails because the conversation grew.

---

### User Story 4 - Every game has artwork and facts without pressing anything (Priority: P1)

As a visitor, I want the library to look complete, with a cover and a description for nearly every game, without anyone
having to open each game and press a refresh button.

**Why this priority**: Empty gradient cards make the app look broken to new people, and LibBot answers poorly for games with
missing facts (player counts, genres, descriptions). The owner called this unacceptable.

**Independent Test**: In the real library, count games with a real cover after a normal scan; add a game on a console and a
ROM scan, wait, and check cover, genres, year, player information and description appeared with no button pressed.

**Acceptance Scenarios**:

1. **Given** the production library, **When** I measure it, **Then** at least 90% of games show a real cover image and
   not having one is the exception.
2. **Given** a scan finds new emulated games, **When** the scan has finished and a few minutes passed, **Then** each game
   has had its facts (genres, developer, year, player information) and its AI description fetched automatically.
3. **Given** a game has no match in the first image source, **When** the system keeps looking, **Then** it tries other
   sources and tolerant title matching (punctuation, subtitles, regional names) before settling for a placeholder.
4. **Given** a game ends with a placeholder, **When** the admin looks at the Sources page, **Then** the number of games
   without a cover is visible so the exceptions can be reviewed.
5. **Given** a scan is uploaded, **When** it is processed, **Then** the upload completes quickly and filling in data
   continues in the background.

---

### User Story 5 - One game, many places, never duplicated (Priority: P1)

As the owner, I want a game I have on Steam, on an emulator host and on a console to appear as one game that lists all of
those, so the library is honest and LibBot can say "you have it on three machines".

**Why this priority**: Consoles (User Story 6), host names (User Story 8) and LibBot's answers all rest on this. Today a
game has one platform, so the same title on two platforms splits into two entries.

**Independent Test**: Put the same title on a scanned Steam host, an emulator host and a console; check the grid shows it
once, the detail page lists all platforms and places, and removing or disabling each place in turn behaves as described.

**Acceptance Scenarios**:

1. **Given** a game exists from a Steam scan, **When** I add the same game to a console, **Then** the grid still shows one
   entry whose card and detail page list both platforms.
2. **Given** a game is present in several places, **When** one place is removed (uninstalled, console game removed, source
   deleted), **Then** the game stays and shows the remaining places.
3. **Given** a game is present in only one place, **When** that place goes away and the grace period for scanned games
   passes, **Then** the game is removed from the catalog.
4. **Given** I filter by one of a game's platforms, **When** the list shows, **Then** the game appears.
5. **Given** the same title was previously stored as two entries because of the old one-platform rule, **When** this feature
   is delivered, **Then** those entries are merged into one without losing facts, descriptions, covers or marks.
6. **Given** I disable a scan source, **When** I look at the library, **Then** games that live only there are hidden (not
   deleted), games that also live elsewhere remain and show only their other places, and re-enabling restores everything.
7. **Given** two different games share a title (for example a remake and the original), **When** their external
   identifiers differ, **Then** they stay separate games.

---

### User Story 6 - Consoles tab: add a console, then add games to it (Priority: P2)

As the admin, I want to register the consoles I own and add games to them, so that console games (Switch, PlayStation, and
more) live in the catalog like any other game.

**Why this priority**: It replaces the Switch-only page and is needed for the "Console" source. It depends on one-game-many-places
(User Story 5).

**Independent Test**: Open Consoles, add "Nintendo Switch" from the suggestions, add a game by search, add another game by typing
its title, and check both appear in the library with the console as their place.

**Acceptance Scenarios**:

1. **Given** I open the Consoles tab with no consoles, **When** I look at "Add game", **Then** it is unavailable and explains that
   a console is needed first, with a shortcut to add one.
2. **Given** I start typing a console name, **When** I type "play", **Then** suggestions include PlayStation, PlayStation 2, 3, 4
   and 5, and PlayStation Portable and Vita; for "nin" they include Nintendo 64, GameCube, Wii, Wii U, DS, 3DS, Switch and Switch 2.
3. **Given** the console I want is not in the suggestions, **When** I type its name and confirm, **Then** I can still add it.
4. **Given** I have at least one console, **When** I add a game, **Then** I choose the console, search by title with
   suggestions that fit that console's platform, pick a result and save; there is no physical/digital choice anywhere.
5. **Given** the search finds nothing, **When** I type the title and add it manually, **Then** the game is added with
   placeholder art and gets its facts automatically when a match becomes possible.
6. **Given** the game is already on that console, **When** I try to add it again, **Then** I am told and nothing is duplicated; adding
   it to a different console is allowed.
7. **Given** I paste a list of titles, **When** I review and confirm, **Then** they are added to the chosen console like before.
8. **Given** I remove a console, **When** I confirm (the dialog shows how many games are affected), **Then** its games leave that
   console; games that are also elsewhere remain in the library.
9. **Given** existing Switch games from before this feature, **When** it is delivered, **Then** they appear under a console named
   "Nintendo Switch", their physical/digital value is gone, and the old virtual entry no longer appears under Scan sources.
10. **Given** I am a guest, **When** I use the app, **Then** I cannot see or reach the Consoles tab.

---

### User Story 7 - A filter bar that is calm by default and clear when active (Priority: P2)

As a visitor, I want a library page that is not a wall of chips, where I can always see what is filtering my results and
remove it in one tap.

**Why this priority**: The current filter area is a stack of five rows and a chip per platform; the owner called it
atrocious. It is the first thing every visitor sees.

**Independent Test**: Open the library with nothing selected, then select platform, status and players, reload, and clear them one by one.

**Acceptance Scenarios**:

1. **Given** I open the library with no filter I chose, **When** it loads, **Then** only the search box, one compact Filters control and the Sort control are visible, plus a single removable "Installed" chip (the default status filter, which is a real filter), not every available filter.
2. **Given** I open the filter control, **When** I look, **Then** I can choose platform, where (host or console), install status,
   source, local players, ROM status (emulated games only), and marks (want to play, played & liked, played & disliked, anyone's or only mine), plus sorting by most wanted and most liked, and every control shows only choices that exist in my library.
3. **Given** one or more filters are active, **When** I look at the page, **Then** each active filter is shown as a removable
   chip next to the search box, a "Clear all" action is available and the result count is visible.
4. **Given** I have filters active, **When** I reload or use the browser's back button, **Then** the same filters are still applied.
5. **Given** I use a phone, **When** I open the library, **Then** the filter control and active chips fit the screen without
   horizontal scrolling and can be used with the keyboard and a screen reader.
6. **Given** a filter combination matches nothing, **When** the list is empty, **Then** the page says so and offers to clear the
   filters, naming which filter to relax first.

---

### User Story 8 - Give a host a name I choose (Priority: P2)

As the owner, I want to name my machines ("Living room PC" instead of `cachyos-htpc`), so that everything in the app uses names I
recognise.

**Why this priority**: Hostnames are technical and appear everywhere (cards, filters, detail, sources, LibBot answers). It is small,
but visible to everyone I share the app with.

**Independent Test**: Set a display name on a host, then check the grid, the detail page, the filters, the Sources page and a LibBot answer.

**Acceptance Scenarios**:

1. **Given** a host with the hostname `cachyos-htpc`, **When** I set the display name "Living room PC", **Then** every place that
   showed the hostname now shows the display name, including LibBot's answers, with no reload of data needed beyond opening the page.
2. **Given** a host has several sources (for example Steam and emulation), **When** I name the host, **Then** the name applies to all of them.
3. **Given** a display name is set, **When** I look at the Sources page, **Then** the original hostname is still shown as a secondary line so
   I can tell which machine it is.
4. **Given** I clear the display name, **When** I look anywhere, **Then** the hostname is shown again.
5. **Given** a scan arrives from a named host, **When** it is processed, **Then** the display name is unchanged.
6. **Given** another host or console already has that name (ignoring letter case), **When** I try to use it, **Then** I am told it is taken.

---

### User Story 9 - Labels you can read at a glance (Priority: P2)

As a visitor, I want source names that make sense and platform and status chips that I can tell apart by color, even if that
departs a little from the app's current look.

**Why this priority**: The owner explicitly prefers visibility over design consistency here. The "Local" label confuses the owner.

**Independent Test**: Open the library and a detail page with games from every platform and status; compare against the official
platform colors and run the accessibility check.

**Acceptance Scenarios**:

1. **Given** a game's sources, **When** I see its label, **Then** it reads Steam (Owned), Steam (Family), Emulated or Console; a game with more than
   one shows each.
2. **Given** a platform has an official brand color (for example PlayStation blue, Xbox green, Nintendo red, Sega blue, Steam blue-grey),
   **When** the platform appears anywhere (card, detail, filter, LibBot references, Consoles), **Then** it uses that color.
3. **Given** a platform without an official color (custom console), **When** it appears, **Then** it uses a neutral color.
4. **Given** the chips for Installed and Not installed, **When** I look at them, **Then** they use two different colors that also differ
   from the platform colors, and each is identifiable without relying on color alone (text or icon).
5. **Given** any platform or status chip, **When** the automated accessibility check runs, **Then** the text meets the contrast minimum.

---

### User Story 10 - Mark a game: want to play, played & liked, or played & disliked (Priority: P2)

As a user, I want to give each game one opinion, "Want to play", "Played & liked" or "Played & disliked", and see what everyone
else chose, so that the group's interest is visible: a game that five people want to play stands out, and LibBot can use that signal.

**Why this priority**: It turns the catalog from a list into a shared guide for game night and gives LibBot real signal for
recommendations. The more people mark a game, the more it matters.

**Independent Test**: With three users, have each pick options on overlapping games, check the counts on cards, sort and filter by them, then ask
LibBot "what should we play that people want to play" and "something like what we liked".

**Acceptance Scenarios**:

1. **Given** a game in the library, **When** I pick "Want to play" on its card or detail page, **Then** my vote is recorded, and the card shows
   the total number of people who want to play it and that I am one of them.
2. **Given** I picked one option on a game, **When** I pick a different one, **Then** the new option replaces the old one: I can only ever hold
   one of the three options on a game, and picking my current option again clears it.
3. **Given** several users have voted, **When** any user looks at the library, **Then** everyone sees the same totals for all three options (votes
   are public), and each person has at most one vote per game.
4. **Given** a game has many want-to-play votes, **When** I browse, **Then** it stands out: I can sort the library by most wanted and most liked,
   and filter by any of the three options (anyone's, or only mine).
5. **Given** several games satisfy a question's requirements (players, platform, installed, and so on), **When** LibBot ranks them, **Then** games with more
   want-to-play and liked votes come first and games with many dislikes come last, even over a game whose description fits slightly better, and the
   answer says so ("four of you want to play this").
6. **Given** a much-wanted game does not satisfy a stated requirement (for example it supports only two local players and six are present), **When** LibBot
   answers, **Then** it is not offered, however many votes it has.
7. **Given** I ask "which of my want-to-play games fit four players", **When** it answers, **Then** LibBot uses only the games I picked; and for
   "what does everyone want to play", it uses all votes.
8. **Given** I say "something I have been meaning to play with a friend", **When** LibBot answers, **Then** it only considers games I marked "Want to play" that support two or more players (this situation needs marks, which is why it lives here and not in the situation story).
9. **Given** I ask "what else is like the games I liked", **When** it answers, **Then** it uses my played & liked games as the reference, does not
   recommend those same games, and does not recommend games I disliked.
10. **Given** a rescan, a host rename, or disabling and re-enabling a source, **When** I look at the votes, **Then** they are unchanged.
11. **Given** a guest, **When** they use the app, **Then** they can vote, change and clear their vote like anyone else (voting is not an admin
   action), and their votes count in the totals.

---

### User Story 11 - Admin refreshes everything on demand (Priority: P3)

As the admin, I want two clear buttons on the Sources page: one to refresh all factual data and artwork for every game, and one
to regenerate all AI-written data, so that I can repair or update the whole library in one action.

**Why this priority**: Useful after improving the data sources or the AI, and a safety net for the automatic fill-in in User Story 4.
Not needed day to day.

**Independent Test**: Start each refresh from the Sources page, watch progress, leave the page and return, then read the summary.

**Acceptance Scenarios**:

1. **Given** I am the admin on the Sources page, **When** I choose "Refresh game data", **Then** factual data and artwork are re-fetched for
   every game, I see progress (done of total) and a summary of successes and failures at the end.
2. **Given** I choose "Regenerate AI data", **When** I click, **Then** a dialog warns that this makes paid AI calls for every game, states how many games
   are affected, and only starts after I explicitly confirm.
3. **Given** a refresh is running, **When** I leave and return, **Then** the progress is still shown, and I cannot start the same refresh a second time.
4. **Given** one game fails during a refresh, **When** the run continues, **Then** the rest are processed and the failure is listed in the summary.
5. **Given** I started a refresh, **When** I choose "Stop", **Then** it stops after the current game and reports how far it got.
6. **Given** facts I corrected by hand (for example a re-linked game), **When** a refresh runs, **Then** my corrections are kept.
7. **Given** the Sources page, **When** I look at it, **Then** the "Refresh pending data" button is gone.
8. **Given** a guest, **When** they use the app, **Then** neither button is visible or usable.

---

### User Story 12 - Clear names and a clean Sources page (Priority: P3)

As a visitor, I want the navigation to say what things are, and the Sources page to list only things that are scanned.

**Why this priority**: Small polish items from the same testing round.

**Independent Test**: Read the navigation as admin and as guest; open Sources.

**Acceptance Scenarios**:

1. **Given** the navigation, **When** I look, **Then** the "Chat" entry is named "LibBot" with an AI-style icon and the "Switch games" entry is named "Consoles".
2. **Given** the Sources page, **When** it lists sources, **Then** only Steam libraries and emulation scans appear; no console or "Nintendo Switch" entry.
3. **Given** I turn a source off, **When** I confirm, **Then** the confirmation says how many games will be hidden and that nothing is deleted.

---

### User Story 13 - Mark whether an emulated game actually works (Priority: P2)

As a user, I want to record whether the ROM of an emulated game launches, so that nobody wastes game night on a game that will not
start and LibBot only recommends games known to work.

**Why this priority**: Emulated libraries are full of ROMs that are present but do not run. Today there is no way to tell, and LibBot
would recommend them.

**Independent Test**: Mark one emulated game Broken and another Validated, check the card, the filter and LibBot's recommendations, and
confirm a Steam-only game offers no such control.

**Acceptance Scenarios**:

1. **Given** an emulated game, **When** I open its page, **Then** for each machine that holds its ROM I see a ROM status, "Unknown" by default, and can
   set it to "Validated" (we tested it and it works) or "Broken" (the ROM is present but the game does not launch), or back to Unknown.
2. **Given** a game that is not emulated (only on Steam or only on a console), **When** I look at it, **Then** it shows no ROM status and none can be set.
3. **Given** a game that is both on Steam and available as an emulated ROM, **When** I look at it, **Then** the status belongs to the emulated copy only
   and is shown next to that copy.
4. **Given** the same ROM is on two machines, **When** I set Validated on one and Broken on the other, **Then** each machine keeps its own status, the game
   page lists both, and the card shows a summary chip (for example "Validated on 1 of 2 hosts"), or a single "Validated" or "Broken ROM" chip when all
   machines agree, each in its own distinct color.
5. **Given** the filter control, **When** I choose a ROM status, **Then** every emulated game with at least one machine in that status is listed.
6. **Given** a ROM is marked Broken on one machine, **When** I ask LibBot what we can play, **Then** that copy is not offered as playable on that machine (the
   game is still offered through another working place, such as another machine's Validated copy or Steam); Validated copies are preferred, and LibBot
   names the machine ("works on the living room PC, untested on the MacBook").
7. **Given** a ROM status is set, **When** a rescan runs, the host is renamed, or its source is disabled and re-enabled, **Then** the status is unchanged.
8. **Given** any signed-in user (admin or guest), **When** they open an emulated game, **Then** they can set or change the status for any machine and everyone sees it.

---

### User Story 14 - The mobile app signs in cleanly and never scrolls sideways (Priority: P2)

As someone using the Android app, I want fingerprint sign-in to go straight in without a red error flashing first, and every page to
fit the screen width, so that the app feels finished.

**Why this priority**: The owner reported both on the real phone. A false error on every sign-in makes the app look broken, and sideways
scrolling is not acceptable on mobile.

**Independent Test**: On a phone (or the Android emulator suite), open the app, unlock with fingerprint and watch the sign-in screen; then open
every page at phone width and try to scroll sideways.

**Acceptance Scenarios**:

1. **Given** fingerprint unlock is turned on, **When** I open the app and unlock with my fingerprint, **Then** I reach the library without any error
   message appearing at any moment, including for a split second.
2. **Given** the stored fingerprint session is genuinely expired or revoked, **When** I try to unlock, **Then** the app shows one clear message saying
   why and offers normal sign-in (a real failure is still reported).
3. **Given** I open the app for the first time, or after signing out, **When** the sign-in screen appears, **Then** it shows no error message.
4. **Given** any page of the app, including the new LibBot, Consoles, filter and detail screens, **When** I view it at phone width (360 to 430 pixels),
   **Then** the page does not scroll sideways and no content is cut off.
5. **Given** a long game title, a long host or console name, or a long answer from LibBot, **When** it is shown at phone width, **Then** it wraps or
   truncates inside the screen.
6. **Given** the automated test suites, **When** they run, **Then** they fail if any page scrolls sideways at phone width, and a unit-level test of the
   sign-in flow fails if an error message is set while an unlock is in progress or after it succeeds. The fingerprint prompt itself cannot be automated,
   so it is checked by hand on the phone (SC-018).

---

### User Story 15 - The game page names the model that really wrote the text, and looks the same on a phone (Priority: P2)

As someone reading a game's page, I want the "About" text to say honestly where it came from, and the page to look as good on a phone as on a desktop.

**Why this priority**: The page says "written by Claude" no matter what wrote it, which has been untrue since the AI models became selectable (and since
OpenRouter became the main route), and "facts from Steam" sits in the wrong place. On a phone the cover floats under the banner with empty space beside it.

**Independent Test**: Generate descriptions with a fixed model and with a router model, open both game pages, then open one at 360 pixels.

**Acceptance Scenarios**:

1. **Given** an AI-written description, **When** I open the game page, **Then** the About heading says which model wrote it (for example "About · written
   by claude-haiku-4.5"), never a hard-coded "Claude".
2. **Given** the feature's model is a router that picks a model for each request (such as the owner's `jev-router` default on OpenRouter), **When** the
   description is written, **Then** the page shows the model the provider reports it actually used, and also names the router that was selected
   ("written by claude-sonnet-5, picked by jev-router").
3. **Given** the provider falls back to the second provider, **When** the description is written, **Then** the model that actually answered is shown.
4. **Given** a description written before this feature, with no recorded model, **When** I open the page, **Then** it says "written by AI" without a
   model name; regenerating it records the model.
5. **Given** a game whose description comes from Steam, **When** I open the page, **Then** the heading says "About · description from Steam" and makes no AI claim.
6. **Given** the About heading, **When** I read it, **Then** it no longer mentions where the facts came from; the Spec sheet shows that instead
   ("Facts: Steam", "Multiplayer: IGDB") as part of the sheet.
7. **Given** a phone at 360 pixels, **When** I open a game page, **Then** the cover overlaps the bottom-left corner of the banner exactly as on desktop (smaller),
   with no empty strip beside or below it and no sideways scroll.
8. **Given** a game with no store description (a ROM or a console game), **When** its AI description is written, **Then** it reads like a Steam store blurb (two
   to four plain sentences about what the game is and what you do, nothing invented), and a description that fails the quality checks is never shown.

---

### Edge Cases

- A question mixes in-scope and out-of-scope parts ("recommend a game and tell me why my cat is black"): LibBot answers the in-scope part and says it cannot help with the rest.
- A question asks for a game the user does not own ("is Hades good?"): LibBot says it is not in the catalog (or cannot say from the catalog), and does not describe it from general knowledge as if it were in the library.
- The conversation refers to a game that has since been hidden (source disabled) or removed: LibBot says the game is no longer in the library.
- A party size is implausible or huge ("40 people"): LibBot says no game supports that many together and suggests the largest group size it knows.
- Titles or descriptions in the catalog contain text that looks like instructions ("ignore previous rules"): it is treated as game data, never as an instruction.
- Two messages are sent rapidly or the page is reloaded mid-answer: no duplicate answers, the conversation stays consistent, and a cancelled answer does not count against a guest limit.
- A guest reaches the daily limit: LibBot explains the limit and when it resets; the existing configured limit is kept.
- The catalog is empty or only has hidden games: LibBot says the library is empty instead of failing.
- The same game is on two consoles, on Steam and on an emulator host: one card, all places listed.
- Adding a game to a console when an emulator-host entry with the same title exists: it links to the existing game and adds the console as another place.
- A console is removed while one of its games has marks or is the subject of an open LibBot conversation: votes stay while the game exists elsewhere; the game disappears with its last place and takes its votes with it.
- A Steam game is installed but not found in a synced library: until the library sync resolves it, it is labelled Steam (Owned).
- A display name is longer than the allowed length or only spaces: rejected with a message; spaces-only is treated as clearing the name.
- A refresh run is started while a scan is running: the run waits or runs alongside without corrupting data; the admin sees its status.
- The AI refresh is started with no AI credit left: the run reports the failure cause clearly after the first failures and stops early instead of burning through every game.
- External image or data sources are down: the games are retried later; the admin sees them counted as "without cover".
- An existing Switch game has no external match: it keeps its placeholder art and stays on the migrated console.
- Two users set different ROM statuses on the same emulated game at nearly the same time: the last change wins and everyone sees it.
- A Broken ROM game is also on Steam, a console, or a second machine where it works: it stays recommendable through that working place; only the broken copy is withheld.
- A ROM is removed from a host and the same ROM returns within the grace period: its ROM status is kept.
- The phone is offline while unlocking with fingerprint: one message about the connection appears, not the "session could not be refreshed" message.
- A very long unbroken word (a URL pasted into a question) would stretch a LibBot message: it wraps inside the screen.
- The router does not report which model it used: the page shows the selected id and "(model not reported)", never a guess.
- A router picks a different model each time the description is regenerated: the page shows the latest one only.
- The model id is long (for example `anthropic/claude-haiku-4.5`): it wraps or truncates inside the About line on a phone.

---

## Requirements *(mandatory)*

### Functional Requirements

**LibBot: answers, references and honesty**

- **FR-001**: LibBot MUST answer every question with one of: a grounded answer, one clarifying question, or a short out-of-scope reply. It MUST NOT show a raw error, an empty reply, or an unexplained "no match".
- **FR-002**: Answers MUST be based only on what the catalog knows (game facts, places, and everyone's votes: want to play, played & liked, played & disliked). LibBot MUST NOT present general knowledge as catalog fact, and MUST say when the catalog lacks the information.
- **FR-003**: References shown with an answer MUST be exactly the games the answer recommends or names, never the whole set that was searched, and MUST NOT exceed ten per answer. Out-of-scope replies and clarifying questions MUST carry no references.
- **FR-004**: When a constraint depends on data that is missing for some games, LibBot MUST distinguish confirmed matches from games whose data is unknown, and say so. It MUST NOT answer "no games match" when unknown-data games exist that could match.
- **FR-005**: LibBot MUST ask a clarifying question instead of guessing when the question cannot be understood from the question and the conversation.
- **FR-006**: LibBot MUST support asking about one attached game (from the game page) as before.
- **FR-007**: Every answer MUST state, in plain words, the constraints LibBot applied from the question (for example "local multiplayer, 6 or more players"), so the user can correct them.

**LibBot: understanding situations**

- **FR-008**: LibBot MUST turn situations into constraints on the attributes the catalog has: number of people present (at least that many local players and local multiplayer only), playing alone (single player), playing online with others (online multiplayer), a named host, console or platform, installed versus not installed, release period, genre, and marks (the asking user's own, or everyone's).
- **FR-009**: A stated group size N of two or more MUST mean "playable together locally by at least N people"; games that cannot be played together by N people locally MUST be excluded.
- **FR-010**: Questions about taste or mood ("something relaxing", "like Celeste") MUST be answered by meaning-based matching over each game's description, genres and facts, combined with the exact constraints above.

**LibBot: memory, safety and reliability**

- **FR-011**: LibBot MUST use the earlier messages of the current conversation to resolve follow-ups. The conversation is held only for the session: it is scoped to the signed-in user, never visible to or mixed with another user's, never saved as history, and discarded when the session ends or the user chooses "New conversation".
- **FR-012**: Only the most recent part of a long conversation MUST be used, so conversation length can never make LibBot fail.
- **FR-013**: If the AI service fails, the system MUST first retry through the existing fallback provider; if that also fails, LibBot MUST show a clear message with a retry action and keep the user's text. A failed or cancelled message MUST NOT count against a guest's daily limit. The existing guest daily limit remains in force and LibBot shows the remaining count.
- **FR-014**: LibBot MUST show progress immediately after a question is sent and show the answer progressively or as soon as it is ready, within the response time in SC-006.
- **FR-015**: Text from the catalog (titles, descriptions) and from the user MUST be treated as data, never as instructions to LibBot.
- **FR-016**: LibBot's knowledge MUST include every user's votes (want to play, played & liked, played & disliked) as totals, plus which ones belong to the asking user), and any change to a game's data, places or votes MUST be reflected in LibBot within one minute. Games hidden by a disabled source MUST NOT appear in answers.
- **FR-017**: LibBot MUST be rebuilt following the project's AI rules and research: all model calls go through the one shared resilient AI path; prompts are versioned files, not code; model output is parsed into validated structured results before any use; no model call keeps a database transaction open; the conversation key is derived by the server from the signed-in user and session; searches only filter by values and always apply the same visibility rules as the library; prompt and answer content is not written to logs; token usage is recorded for every call.
- **FR-018**: A fixed set of golden questions MUST exist and be runnable against the real models on demand before a release. It MUST include every failure the owner reported (the cat question, "four people", "six people for game night", "how many other games like this", and out-of-scope reference lists), each follow-up flow in User Story 3, and each situation in User Story 2, each in English and in Dutch; and a set of games with known facts whose generated descriptions must pass the FR-063 checks.

**Catalog data: completeness**

- **FR-019**: When games are discovered (scan, library sync, console add) or created manually, the system MUST automatically obtain, without any button press: cover and banner artwork, genres, developer, publisher, release year, player information and a description, in the background so that scan uploads and adds return quickly. The description is Steam's own for Steam games and AI-written for games with no store description (FR-063).
- **FR-020**: Artwork lookup MUST try more than one source and tolerate differences in titles (punctuation, subtitles, editions, regional names) before settling for a placeholder, and MUST retry games that ended with a placeholder or missing facts automatically once a day in small batches (no button needed), as well as when the admin refreshes data. Paid AI descriptions MUST be retried only a limited number of times per game, after which the game waits for the admin's "Regenerate AI data" and shows in the pending count.
- **FR-021**: The Sources page MUST show, for the admin, how many games still lack artwork, a description or player information, so exceptions are visible and can be acted on.
- **FR-022**: A game MUST NOT stay in a permanent "description unavailable" state because nobody pressed a button; failures are retried automatically (within the limit in FR-020) and shown as a visible count when they persist.

**One game, many places**

- **FR-023**: A game MUST exist once in the catalog and MAY be present on any number of hosts, consoles, platforms and library sources at once. The grid, the detail page, the filters and LibBot MUST present it as a single game listing all of them.
- **FR-024**: Two catalog entries MUST be treated as the same game when a stable external identifier matches, or, when no identifier is known, when their normalised titles match (ignoring letter case, punctuation and accents). Entries whose known identifiers differ MUST stay separate. A wrongly merged game MUST be fixable by re-linking it to the correct database match.
- **FR-025**: Adding another place for an existing game MUST never create a duplicate and never overwrite richer data (title chosen by hand, description, artwork, marks).
- **FR-026**: Removing a place (uninstall, console game removed, console removed, source removed) MUST keep the game while any other place or library membership remains, and MUST remove the game only when none remain (scanned places after the existing grace period; manual places immediately). Marks follow the game.
- **FR-027**: Changes to a place's details (a host's name, a game's title) MUST be visible everywhere that place is shown without any further action.
- **FR-028**: Existing duplicates caused by the one-platform rule MUST be merged during delivery without losing facts, descriptions, artwork or marks.
- **FR-029**: Disabling a scan source MUST hide that source's places from the library, filters and LibBot, hide games that exist only there, keep games that exist elsewhere (showing only their remaining places) and delete nothing. Re-enabling MUST restore everything exactly. The toggle MUST say how many games will be hidden before it is confirmed.

**Hosts and consoles**

- **FR-030**: A host (a machine that scans) MUST be able to carry an optional display name. When set it replaces the hostname everywhere the app shows it (grid, detail, filters, Sources, Consoles, LibBot answers); when cleared the hostname returns. The rule "which name to show" MUST live in one place in the domain model, not be repeated in screens.
- **FR-031**: A display name applies to the host as a whole, MUST be unique across hosts and consoles (ignoring letter case), MUST be limited to 40 characters, MUST never be changed by a scan, and the original hostname MUST remain visible as secondary text on the Sources page.
- **FR-032**: A Consoles area (admin only) MUST replace "Switch games". It lets the admin add a console, rename it, remove it and see how many games it holds, and add games to it.
- **FR-033**: Adding a console MUST offer autocomplete over well-known consoles from the fifth generation to the current one (including handhelds) and MUST allow a custom console name when none fits. A console has a platform and a name; the same platform MAY be added more than once under different names.
- **FR-034**: Adding a game MUST require at least one console, MUST let the admin pick the console, MUST offer title search suggestions that fit that console's platform and MUST allow adding by plain title when there is no match. Pasting a list of titles MUST remain available and add to the chosen console. There MUST be no physical/digital field anywhere.
- **FR-035**: Editing a console game (re-linking it to the right database match) and removing it from a console MUST remain available; deleting from one console MUST not affect other places of the same game.
- **FR-036**: Existing Switch games MUST be carried into a console named "Nintendo Switch" at delivery, dropping their physical/digital value, and the Sources page MUST list only scannable sources (Steam libraries and emulation scans).

**Library: filters, labels, colors**

- **FR-037**: The library MUST show only the search box, one compact filter control and the sort control when no filter beyond the default status (Installed, shown as a removable chip) is active. Platform, where (host or console), install status, source, local players, ROM status, marks (anyone's or only mine) and sorting by most wanted and most liked MUST be reachable from that control, offering only choices that exist.
- **FR-038**: Every active filter MUST always be visible as a removable chip with a "Clear all" action and the result count; filters MUST persist across reload and back navigation; the bar MUST work at phone width and by keyboard and screen reader; an empty result MUST name the filter to relax.
- **FR-039**: Source labels MUST read Steam (Owned), Steam (Family), Emulated, Console, replacing Owned, Family and Local everywhere (cards, filter, detail, LibBot). A game shows every source it has. A Steam game not yet found in a synced library is labelled Steam (Owned).
- **FR-040**: Platform labels MUST use the platform's official brand color wherever they appear, with text contrast meeting the accessibility minimum, and a neutral color for platforms without one. The palette MUST be defined in one place.
- **FR-041**: Installed and Not installed chips MUST use two distinct colors, different from each other and from the platform palette, and MUST be identifiable without color alone.
- **FR-042**: The navigation MUST rename the "Chat" entry "LibBot" with a distinct AI icon, and "Switch games" "Consoles".

**Marks (shared votes)**

- **FR-043**: Every signed-in user (admin and guest) MUST be able to give each game one of three mutually exclusive marks, "Want to play", "Played & liked" or "Played & disliked", from the game card and the game page. A mark is one vote: a user holds at most one of the three per game, picking another replaces it, and picking the current one clears it. Votes MUST survive rescans, host renames, and disabling and re-enabling a source.
- **FR-044**: Marks are public. Every user MUST see the total number of votes for each of the three marks on each game, and whether they voted themselves; voters' identities MUST NOT be shown. A new vote MUST be visible to every other user the next time they load or refresh the page, and the library and game pages MUST refetch the totals when the page regains focus (no live push is required).
- **FR-045**: More votes MUST mean more weight: the library MUST be sortable by most wanted and most liked and filterable by any of the three marks (anyone's or only mine), and LibBot MUST first apply every stated requirement and then rank the games that pass by votes (more want-to-play and liked votes higher, many dislikes lower, even over a slightly better description fit), never offering a game that fails a stated requirement, and MUST say when votes influenced a recommendation. A user's own votes MUST drive "like what I liked" style questions; a user's liked games MUST NOT be recommended back to them, games they disliked MUST NOT be recommended to them, and games with many dislikes MUST rank lower for everyone.
- **FR-046**: When a user account is removed or disabled, their votes MUST be removed from the totals.

**Admin refresh**

- **FR-047**: The Sources page MUST offer the admin a "Refresh game data" action that re-fetches factual data and artwork for every game, and a "Regenerate AI data" action that regenerates all AI-written data for every game. Both run in the background, show progress and a final summary, cannot run twice at once, survive leaving the page, can be stopped, continue past individual failures and keep data the admin corrected by hand.
- **FR-048**: "Regenerate AI data" MUST open a confirmation dialog stating that it makes paid AI calls, how many games it covers, and MUST start only after explicit confirmation. If AI calls fail repeatedly because of the same cause (for example no credit), the run MUST stop early and report that cause.
- **FR-049**: The "Refresh pending data" button MUST be removed.
- **FR-050**: Both refresh actions MUST be admin-only.

**ROM status (emulated games only)**

- **FR-051**: Every emulated copy of a game (each machine that holds its ROM) MUST carry its own ROM status of Unknown (the default), Validated or Broken. No other game or place (Steam, console) MAY have this property, and it MUST NOT be settable for them.
- **FR-052**: Any signed-in user (admin and guest) MUST be able to set, change or clear the status of each machine's copy from the game page; the status is shared, visible to everyone, and the last change wins.
- **FR-053**: The status MUST be listed per machine on the game page and summarised on the card as a chip (a single status when all copies agree, otherwise a count such as "Validated on 1 of 2 hosts"), with colors distinct from the platform, installed-status and mark colors and identifiable without color alone, and MUST be filterable (a game matches when at least one machine has that status).
- **FR-054**: LibBot MUST NOT offer a Broken emulated copy as playable on that machine (the game stays recommendable through any other working place), MUST prefer Validated copies, and MUST name the machine and status when it matters. Status changes MUST reach LibBot within one minute.
- **FR-055**: The status MUST survive rescans, host renames, and disabling and re-enabling a source, and MUST be kept while the emulated copy is removed and returns within the grace period.

**Language**

- **FR-056**: LibBot MUST understand questions in English and Dutch and answer in the language of the question, including situation phrasing and follow-ups; game titles, genres and facts stay as the catalog holds them. Other languages get a short polite reply in English asking the user to rephrase.

**Mobile polish**

- **FR-057**: Signing in on the mobile app with fingerprint MUST NOT show any error message unless sign-in actually failed. A stale or leftover failure message MUST be cleared when an unlock starts, and a failure MUST only be reported for a session that really existed. A genuine failure (expired, revoked, offline) MUST still show one accurate message.
- **FR-058**: Every page of the web and mobile app MUST fit the screen width from 360 pixels up: no sideways page scrolling and no cut-off content, including all pages added by this feature. Wide content (tables, long names, long answers) MUST wrap, truncate or scroll inside its own container.

**Game page**

- **FR-059**: Every AI-written description MUST record the model the provider reports as having answered (for a router, the model it picked), the model that was selected when it differs (the router), and when it was written. A description from Steam MUST be recorded as such, with no AI model.
- **FR-060**: The About heading MUST show the real source: the answering model for AI text (plus the router when one was selected), "description from Steam" for Steam text, "written by AI" for older text with no recorded model. Nothing may be hard-coded to a vendor or model name.
- **FR-061**: The About heading MUST NOT describe where the facts came from; the Spec sheet MUST show the source of its facts (Steam, IGDB or AI) as part of the sheet.
- **FR-062**: The game page MUST use the same composition on every width: the cover overlapping the bottom-left corner of the banner, scaled down on phones, with no empty strip and no sideways scroll.
- **FR-063**: An AI-written description MUST be of comparable quality to a Steam store description: two to four sentences (about 40 to 110 words) in plain, factual third person that says what kind of game it is and what the player does, grounded only in the game's known facts (title, platform, genres, developer, year, player information and, where available, the external database's summary), with no invented awards, scores, plot spoilers or first-person or marketing filler. A description that fails these checks MUST be rejected and retried, never shown.

### Key Entities *(include if feature involves data)*

- **Game**: A game once in the catalog: identity (title, external identifiers), facts (genres, developer, publisher, year, player information), description, artwork. It lists all of its places and platforms; it has no single platform or host.
- **Place**: One way a game is available: installed on a host through a scan, held on a console, or a member of a Steam library (owned or family). Places can be added and removed independently; the game exists while at least one remains.
- **Host**: A machine that scans. Has a hostname (from the machine), an optional display name chosen by the admin, and one or more scan sources. Decides which name is shown.
- **Scan source**: A Steam library or an emulation library on a host, that can be switched on or off. Never a console.
- **Console**: A console the admin registered: a platform (from the well-known list or custom) and a name; holds games added by hand.
- **Mark (vote)**: One user's single opinion on one game: Want to play, Played & liked or Played & disliked (mutually exclusive). Public as a total; the voter is not shown. A game's totals are the count of its votes.
- **Conversation**: The session-only exchange between one user and LibBot. Held while the session lasts, never stored as history.
- **LibBot knowledge**: What LibBot can search: every visible game's facts, description, places, and everyone's vote totals (with the asking user's own votes known), kept up to date as the catalog changes.
- **ROM status**: Unknown, Validated or Broken, held per emulated copy (per machine that holds the ROM) and never on Steam or console places; shared and public.
- **Refresh run**: One admin-started bulk refresh (data or AI) with progress, outcome counts and a stop control.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 90% of games in the production library show a real cover image, and the admin can see the exact number and the list of exceptions.
- **SC-002**: At least 90% of the golden questions pass on the real models, and 100% of out-of-scope questions in the set return zero game references, in English and in Dutch.
- **SC-003**: Every owner-reported failure passes: the cat question shows no references; "four people" and "six people for game night" return suitable games or an explanation about unknown data (never an unexplained "no match"); "how many other games like this" is understood after a recommendation.
- **SC-004**: No LibBot answer shows more than ten references, and every reference shown is named or recommended in the answer text.
- **SC-005**: In the golden set, at least 90% of follow-up questions that depend on earlier messages are answered correctly.
- **SC-006**: 95% of questions show progress within 1 second and have a complete answer within 10 seconds.
- **SC-007**: Ten minutes after a scan or an add completes, at least 90% of the newly discovered games have a cover, a description and player information, with no button pressed.
- **SC-008**: With no filter chosen, the library shows one filter control (plus search, sort and the default Installed chip) above the grid; a visitor can apply platform, install status and a player count in under 30 seconds and can name every active filter at a glance.
- **SC-009**: In the scenarios of User Story 5, 0 games appear twice, and removing, disabling and re-enabling any one place leaves every other place and every mark intact.
- **SC-010**: When a host has a display name, 0 screens show the hostname as the main label, including LibBot answers.
- **SC-011**: 100% of platform and status chips pass the automated accessibility contrast check.
- **SC-012**: An admin can add a console and its first game in under 60 seconds.
- **SC-013**: An admin can start either bulk refresh in two clicks (plus one confirmation for the AI refresh) and sees progress while it runs.
- **SC-014**: After disabling a source, 100% of games that exist only there are hidden, and after re-enabling, 100% are back with their data and votes.
- **SC-015**: With three users voting on overlapping games, every user sees identical totals, the most-wanted sort orders games by those totals, and in the golden set among games that all satisfy the question, a LibBot answer names the more-voted game first and never offers a game that fails a stated requirement.
- **SC-016**: In the golden set, 100% of "what can we play" answers exclude Broken emulated copies unless the game has another working place, and a ROM status set by one user is visible to every other user on their next load or refresh (and when their page regains focus).
- **SC-017**: A game that lacked a cover or facts only because an outside source was briefly unavailable gets them within 24 hours without anyone pressing a button.
- **SC-018**: Across 20 consecutive fingerprint sign-ins checked by hand on the real phone, 0 show an error message, and the unit-level sign-in tests pass.
- **SC-019**: 0 pages scroll sideways at 360, 390 and 430 pixel width, checked automatically on every signed-in page.
- **SC-020**: 100% of descriptions written after delivery show the model that answered (router case included); 0 pages show a hard-coded vendor name.
- **SC-021**: At 360 pixels the game page shows the cover overlapping the banner as on desktop, checked by a screenshot comparison in the browser pane and the sideways-scroll check.
- **SC-022**: In the golden set, 100% of generated descriptions pass the FR-063 checks, and in the owner's review of 20 randomly chosen AI-written descriptions next to 20 Steam ones, at least 18 of the AI ones are judged as good as Steam's.

---

## Assumptions

- LibBot, filters, labels and marks apply to both admin and guest users. Consoles, host names, Sources and the bulk refreshes are admin only. The guest daily message limit from the settings module stays as configured.
- Marks are public votes, not private lists: every signed-in user sees the totals, so a game wanted by five people is visibly notable. Who voted is not shown (counts only), and a user can see which games they voted for. This is a decision the owner made after the first draft assumed private marks.
- Marks apply only to games already in the catalog. Wishlisting a game the user does not own or have on any console is out of scope.
- The mark names "Want to play", "Played & liked" and "Played & disliked" are the owner's. The AI helper is named LibBot (short for library bot; "LabBot" was the owner's alternative) and the console area "Consoles"; both can be renamed in review.
- A conversation lasts for the visit: it is kept while LibBot page stays open or is revisited within the same sign-in session, and is not restored later. The AI helper uses the most recent ten exchanges (confirmed by the owner).
- A game counts as present on a console as soon as it is added there and is shown as Installed; consoles have no install states.
- A Steam game found by a scan but not yet matched by a library sync is shown as Steam (Owned) until the next sync decides otherwise.
- Disabling a source means "I do not want this machine's games counted", which is why games that exist only there are hidden rather than shown as not installed. Confirmed by the owner on 2026-10-07.
- The "well-known consoles" list covers the fifth generation (PlayStation, Sega Saturn, Nintendo 64 and their contemporaries) up to Nintendo Switch 2, PlayStation 5 and Xbox Series X|S, including handhelds; the list is maintained in one place.
- The scanner client does not change: host display names, consoles and the new labels are all handled on the server and in the web app. The mobile app uses the same web build and inherits all changes.
- The existing external game database used for Switch search remains the source for console search and facts; image sources may be extended, and the exact sources are decided in planning.
- The earlier AI drafts (prompts as files, model calls outside transactions, token usage, golden set, integration conventions) are treated as inputs: the parts LibBot needs are in this spec; anything beyond that stays in its own draft.
- Steam games (installed or not) use Steam's description and facts; paid AI descriptions are written only for games with no store description, and the admin's "Regenerate AI data" covers those. Owner decision, 2026-10-07.
- Only the model is shown, not a router's reasoning effort: routers do not report it reliably. If a provider ever reports it, it can be added to the same line.
- No cost dashboard, conversation history, per-user model choice, price tracking, play-time tracking or Nintendo account import is part of this feature.

## Delivery and Validation

The owner set the order of work for this feature:

1. **Build and verify locally first.** Every user story and acceptance scenario in this spec is exercised on the local stack (localhost) before anything is released. Real data comes from the real scanner and the real services, never hand-written rows (root `AGENTS.md`, Validation Data).
2. **Release only when the entire spec works locally.** No partial release: all user stories pass locally, including the golden questions run against the real models (FR-018).
3. **Deploy, then re-test on production (jordylab.be).** After the release, the same scenarios are re-run on production and the production-only measures are checked (SC-001 cover coverage and SC-007 automatic fill-in on the real library).
4. Production changes follow the existing release and approval path (`docs/runbook.md`); the local run and the production run are each recorded with their results.

---

## Out of Scope

- Saved conversation history or sharing conversations.
- Wishlisting games that are not in the catalog, prices, sales or purchase links.
- Play time, completion tracking, ratings other than the three marks.
- Showing who voted, notifications when a game is wanted by many people, or weighting votes differently per user.
- Changes to the Python scan client.
- Local (on-device) AI inference.
- A spending dashboard or hard budget for AI calls (covered by its own draft).
