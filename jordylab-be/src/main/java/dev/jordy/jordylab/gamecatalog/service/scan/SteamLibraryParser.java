package dev.jordy.jordylab.gamecatalog.service.scan;

import dev.jordy.jordylab.gamecatalog.domain.SourceType;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.GamePayload;
import dev.jordy.jordylab.gamecatalog.rest.controller.model.ScanRequest;
import dev.jordy.jordylab.gamecatalog.util.TextSanitizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a Steam library scan. The script sends a directory listing
 * (which includes each {@code appmanifest_<appid>.acf} under
 * {@code steamapps/}) plus the raw contents of every manifest in
 * {@code manifestContents} (keyed by the file's relative path). The parser
 * walks the manifest map, extracts {@code appid} and {@code name} from each
 * one, and emits a {@link GamePayload} per installed game.
 *
 * <p>Manifest files that fail to parse or lack a valid {@code appid} or
 * {@code name} are skipped (logged) — partial scans are an explicit feature.
 */
@Slf4j
@Component("STEAM")
public class SteamLibraryParser implements LibraryParser {

    private static final Pattern MANIFEST_PATH = Pattern.compile(".*steamapps/appmanifest_(\\d+)\\.acf$");

    @Override
    public SourceType supports() {
        return SourceType.STEAM;
    }

    @Override
    public List<GamePayload> parse(ScanRequest request) {
        Map<String, String> manifests = manifests(request);
        if (manifests == null || manifests.isEmpty()) {
            log.info("Steam scan from '{}' contained no manifests", request.hostname());

            return List.of();
        }
        List<GamePayload> games = new ArrayList<>();
        for (Map.Entry<String, String> entry : manifests.entrySet()) {
            String relpath = entry.getKey();
            String text = entry.getValue();
            Matcher matcher = MANIFEST_PATH.matcher(relpath);
            if (!matcher.matches()) {
                continue;
            }
            String appId = matcher.group(1);
            String title = extractTitle(text);
            if (!StringUtils.hasText(title)) {
                log.info("Skipping unparseable Steam manifest '{}' (no title)", relpath);
                continue;
            }

            games.add(new GamePayload(appId, TextSanitizer.sanitizeTitle(title), SourceType.STEAM.platform(), null));
        }

        return games;
    }

    private String extractTitle(String text) {
        Map<String, Object> root = VdfParser.parse(text);
        String name = VdfParser.nestedString(root, "", "AppState", "name");
        if (StringUtils.hasText(name)) {
            return name;
        }
        // Some VDFs escape quotes differently; nestedString already strips them
        // (readQuoted only collects the body). The fall-back is to look at the
        // installdir, which is the directory name under steamapps/common/.
        return VdfParser.nestedString(root, "", "AppState", "installdir");
    }
}
