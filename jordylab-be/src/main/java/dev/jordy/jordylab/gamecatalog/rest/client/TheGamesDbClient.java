package dev.jordy.jordylab.gamecatalog.rest.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jordy.jordylab.gamecatalog.domain.PlayerCountGap;
import dev.jordy.jordylab.gamecatalog.util.TitleKeys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static java.util.Map.entry;

/**
 * TheGamesDB client for the player count of console games (spec 014). One search per game and console; the count is the
 * source's own {@code players} field and is only returned when every entry for the same title on that console agrees.
 * Optional: without {@code THEGAMESDB_API_KEY} every lookup is deferred. The service echoes the full request address,
 * key included, in the {@code pages} field of every answer, so no request address, key or answer body is ever logged,
 * stored or rethrown: failures are logged with the exception class or the HTTP status only.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TheGamesDbClient {

    private static final String DEFAULT_BASE_URL = "https://api.thegamesdb.net/v1";
    private static final String USER_AGENT = "JordyLab/1.0 (+https://jordylab.be)";
    private static final int MAX_PAGES = 3;
    private static final int MINIMUM_PLAYER_COUNT = 2;
    private static final Duration MINIMUM_REFUSAL_PAUSE = Duration.ofHours(1);
    private static final Duration OUTAGE_PAUSE = Duration.ofMinutes(15);
    private static final int FORBIDDEN = 403;

    /** The catalog's console name → TheGamesDB platform (id and name), read from the live platform list on 2026-11-01. */
    private static final Map<String, Platform> PLATFORMS = Map.ofEntries(
            entry("PlayStation", new Platform(10, "Sony Playstation")),
            entry("PlayStation 2", new Platform(11, "Sony Playstation 2")),
            entry("PlayStation 3", new Platform(12, "Sony Playstation 3")),
            entry("PlayStation 4", new Platform(4919, "Sony Playstation 4")),
            entry("PlayStation 5", new Platform(4980, "Sony Playstation 5")),
            entry("PSP", new Platform(13, "Sony Playstation Portable")),
            entry("PlayStation Vita", new Platform(39, "Sony Playstation Vita")),
            entry("Xbox", new Platform(14, "Microsoft Xbox")),
            entry("Xbox 360", new Platform(15, "Microsoft Xbox 360")),
            entry("Xbox One", new Platform(4920, "Microsoft Xbox One")),
            entry("Xbox Series X|S", new Platform(4981, "Microsoft Xbox Series X")),
            entry("Nintendo 64", new Platform(3, "Nintendo 64")),
            entry("GameCube", new Platform(2, "Nintendo GameCube")),
            entry("Wii", new Platform(9, "Nintendo Wii")),
            entry("Wii U", new Platform(38, "Nintendo Wii U")),
            entry("Nintendo Switch", new Platform(4971, "Nintendo Switch")),
            entry("Nintendo Switch 2", new Platform(5021, "Nintendo Switch 2")),
            entry("Nintendo DS", new Platform(8, "Nintendo DS")),
            entry("Nintendo 3DS", new Platform(4912, "Nintendo 3DS")),
            entry("Game Boy Advance", new Platform(5, "Nintendo Game Boy Advance")),
            entry("Game Boy Color", new Platform(41, "Nintendo Game Boy Color")),
            entry("Game Boy", new Platform(4, "Nintendo Game Boy")),
            entry("SNES", new Platform(6, "Super Nintendo (SNES)")),
            entry("NES", new Platform(7, "Nintendo Entertainment System (NES)")),
            entry("Saturn", new Platform(17, "Sega Saturn")),
            entry("Dreamcast", new Platform(16, "Sega Dreamcast")),
            entry("Genesis", new Platform(18, "Sega Genesis")),
            entry("Game Gear", new Platform(20, "Sega Game Gear")),
            entry("Sega 32X", new Platform(33, "Sega 32X")),
            entry("Sega CD", new Platform(21, "Sega CD")),
            entry("Master System", new Platform(35, "Sega Master System")),
            entry("Atari 2600", new Platform(22, "Atari 2600")));

    @Value("${THEGAMESDB_API_KEY:}")
    String apiKey;

    @Value("${jordylab.gamecatalog.thegamesdb.base-url:" + DEFAULT_BASE_URL + "}")
    String baseUrl;

    @Value("${jordylab.gamecatalog.thegamesdb.timeout-ms:5000}")
    int timeoutMs;

    /** Minimum gap between two calls; TheGamesDB publishes no per-second limit, one a second is polite. */
    @Value("${jordylab.gamecatalog.thegamesdb.min-interval-ms:1000}")
    long minIntervalMs;

    /** Calls stop when the monthly allowance is at or below this, so the owner's own checks keep some room. */
    @Value("${jordylab.gamecatalog.thegamesdb.allowance-reserve:20}")
    int allowanceReserve;

    private final ObjectMapper objectMapper;
    private final Clock clock;

    private RestClient restClient;
    private volatile Instant pausedUntil;
    private volatile PauseReason pauseReason;
    private volatile Integer remainingAllowance;
    private long lastCallNanos;

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        if (!isConfigured()) {
            log.info("TheGamesDB not configured (THEGAMESDB_API_KEY unset); player counts come from IGDB only");
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    /**
     * The player count of one title on one of the catalog's consoles. The title key is the stored {@link TitleKeys} key of
     * the game; only entries with exactly that key, on that console, with a {@code players} value take part. A console
     * the source does not list is answered without a request, and nothing is asked while the source is paused or
     * unconfigured ({@link Outcome#DEFERRED}: nothing is stored and no attempt is counted).
     */
    public PlayerCountLookup lookup(String title, String titleKey, String console) {
        if (!isConfigured()) {
            return PlayerCountLookup.deferred();
        }
        Platform platform = PLATFORMS.get(console);
        if (platform == null) {
            return PlayerCountLookup.gap(PlayerCountGap.CONSOLE_NOT_LISTED);
        }
        if (isPaused()) {
            return PlayerCountLookup.deferred();
        }
        Set<Integer> counts = new LinkedHashSet<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            Optional<JsonNode> answer = fetch(title, platform, page);
            if (answer.isEmpty()) {
                return PlayerCountLookup.deferred();
            }
            collectCounts(answer.get(), titleKey, platform, counts);
            if (!hasNextPage(answer.get()) || isPaused()) {
                break;
            }
        }

        return decide(counts);
    }

    /** What the Sources page shows about this source. Nothing here is persisted. */
    public ClientState state() {
        Instant until = pausedUntil;
        boolean paused = until != null && clock.instant().isBefore(until);

        return new ClientState(isConfigured(), paused ? until : null, paused ? pauseReason : null, remainingAllowance);
    }

    static Map<String, Platform> platforms() {
        return PLATFORMS;
    }

    private PlayerCountLookup decide(Set<Integer> counts) {
        if (counts.isEmpty()) {
            return PlayerCountLookup.gap(PlayerCountGap.NO_MATCH);
        }
        if (counts.size() > 1) {
            return PlayerCountLookup.gap(PlayerCountGap.ENTRIES_DISAGREE);
        }
        int count = counts.iterator().next();
        if (count < MINIMUM_PLAYER_COUNT) {
            return PlayerCountLookup.gap(PlayerCountGap.COUNT_BELOW_TWO);
        }

        return PlayerCountLookup.found(count);
    }

    private void collectCounts(JsonNode answer, String titleKey, Platform platform, Set<Integer> counts) {
        for (JsonNode game : answer.path("data").path("games")) {
            String name = game.path("game_title").asText(null);
            if (!StringUtils.hasText(name) || !titleKey.equals(TitleKeys.keyFor(name))) {
                continue;
            }
            if (game.path("platform").asInt(-1) != platform.id()) {
                continue;
            }
            Integer count = playersOf(game.path("players"));
            if (count != null) {
                counts.add(count);
            }
        }
    }

    private Integer playersOf(JsonNode players) {
        if (players.isNumber() && players.canConvertToInt() && players.asInt() > 0) {
            return players.asInt();
        }
        if (players.isTextual() && players.asText().matches("\\d{1,3}") && Integer.parseInt(players.asText()) > 0) {
            return Integer.parseInt(players.asText());
        }

        return null;
    }

    private boolean hasNextPage(JsonNode answer) {
        JsonNode next = answer.path("pages").path("next");

        return !next.isMissingNode() && !next.isNull() && StringUtils.hasText(next.asText());
    }

    private Optional<JsonNode> fetch(String title, Platform platform, int page) {
        paceCalls();
        try {
            String body = restClient.get()
                    .uri(requestUri(title, platform, page))
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .retrieve()
                    .body(String.class);
            JsonNode answer = objectMapper.readTree(body);
            noteAllowance(answer);

            return Optional.of(answer);
        } catch (RestClientResponseException refused) {
            pauseAfterStatus(refused);

            return Optional.empty();
        } catch (RestClientException unreachable) {
            pause(PauseReason.UNREACHABLE, OUTAGE_PAUSE,
                    "TheGamesDB unreachable (" + unreachable.getClass().getSimpleName() + ")");

            return Optional.empty();
        } catch (Exception unreadable) {
            log.warn("TheGamesDB response unreadable: {}", unreadable.getClass().getSimpleName());

            return Optional.empty();
        }
    }

    /** The request address carries the key, so it is built here and never passed on, logged or put in a message. */
    private URI requestUri(String title, Platform platform, int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/Games/ByGameName")
                .queryParam("name", "{name}")
                .queryParam("fields", "players")
                .queryParam("filter[platform]", "{platform}")
                .queryParam("apikey", "{apikey}");
        Map<String, Object> variables = new HashMap<>(Map.of(
                "name", title, "platform", platform.id(), "apikey", apiKey));
        if (page > 1) {
            builder.queryParam("page", "{page}");
            variables.put("page", page);
        }

        return builder.encode().buildAndExpand(variables).toUri();
    }

    private void pauseAfterStatus(RestClientResponseException refused) {
        int status = refused.getStatusCode().value();
        if (status == FORBIDDEN) {
            Duration pause = refusalPause(refused);
            pause(PauseReason.KEY_REFUSED_OR_ALLOWANCE_USED, pause, "TheGamesDB refused the request (HTTP 403)");

            return;
        }
        pause(PauseReason.UNREACHABLE, OUTAGE_PAUSE, "TheGamesDB answered HTTP " + status);
    }

    /** A refusal means a bad key or a used-up allowance; wait for the allowance timer, but at least an hour. */
    private Duration refusalPause(RestClientResponseException refused) {
        Duration advertised = Duration.ZERO;
        try {
            long seconds = objectMapper.readTree(refused.getResponseBodyAsString()).path("allowance_refresh_timer").asLong(0);
            advertised = Duration.ofSeconds(seconds);
        } catch (Exception unreadable) {
            log.debug("TheGamesDB refusal body unreadable: {}", unreadable.getClass().getSimpleName());
        }

        return advertised.compareTo(MINIMUM_REFUSAL_PAUSE) > 0 ? advertised : MINIMUM_REFUSAL_PAUSE;
    }

    private void noteAllowance(JsonNode answer) {
        int remaining = answer.path("remaining_monthly_allowance").asInt(-1);
        if (remaining < 0) {
            return;
        }
        remainingAllowance = remaining;
        if (remaining <= allowanceReserve) {
            long seconds = answer.path("allowance_refresh_timer").asLong(0);
            Duration pause = seconds > 0 ? Duration.ofSeconds(seconds) : MINIMUM_REFUSAL_PAUSE;
            pause(PauseReason.ALLOWANCE_LOW, pause, "TheGamesDB allowance low (" + remaining + " left)");
        }
    }

    private void pause(PauseReason reason, Duration duration, String description) {
        Instant until = clock.instant().plus(duration);
        this.pauseReason = reason;
        this.pausedUntil = until;
        log.warn("{}; pausing until {}", description, until);
    }

    private boolean isPaused() {
        Instant until = pausedUntil;

        return until != null && clock.instant().isBefore(until);
    }

    private synchronized void paceCalls() {
        long waitNanos = lastCallNanos + minIntervalMs * 1_000_000L - System.nanoTime();
        if (lastCallNanos != 0 && waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000L, (int) (waitNanos % 1_000_000L));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallNanos = System.nanoTime();
    }

    /** What a lookup found: {@code FOUND} with an agreed count, {@code GAP} with the reason, or {@code DEFERRED}. */
    public record PlayerCountLookup(Outcome outcome, Integer maxLocalPlayers, PlayerCountGap gap) {

        public static PlayerCountLookup found(int maxLocalPlayers) {
            return new PlayerCountLookup(Outcome.FOUND, maxLocalPlayers, null);
        }

        public static PlayerCountLookup gap(PlayerCountGap gap) {
            return new PlayerCountLookup(Outcome.GAP, null, gap);
        }

        public static PlayerCountLookup deferred() {
            return new PlayerCountLookup(Outcome.DEFERRED, null, null);
        }
    }

    public enum Outcome {
        FOUND,
        GAP,
        DEFERRED
    }

    public enum PauseReason {
        KEY_REFUSED_OR_ALLOWANCE_USED,
        ALLOWANCE_LOW,
        UNREACHABLE
    }

    /** The source's current state: configured, paused until when and why, and the last allowance seen. */
    public record ClientState(boolean configured, Instant pausedUntil, PauseReason pauseReason,
            Integer remainingAllowance) {
    }

    /** One console on TheGamesDB: its id and the name it is listed under. */
    record Platform(int id, String name) {
    }
}
