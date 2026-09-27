package dev.jordy.jordylab.gamecatalog.util;

import lombok.experimental.UtilityClass;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Steam tools and runtimes ship {@code appmanifest_<appid>.acf} files on Linux, so a disk scan
 * sees them as installed apps. They are not games and must never be catalogued or enriched
 * (FR-013). The store {@code appdetails} {@code type} field is the authoritative online check;
 * this deny-list covers the offline/manifest path and known app IDs.
 */
@UtilityClass
public class ToolExclusion {

    public static final Set<String> TOOL_APP_IDS = Set.of(
            "228980",  // Steamworks Common Redistributables
            "1070560", // Steam Linux Runtime 1.0 (scout)
            "1391110", // Steam Linux Runtime 2.0 (soldier)
            "1628350", // Steam Linux Runtime 3.0 (sniper)
            "1493710"  // Proton Experimental
    );

    private static final Pattern TOOL_NAME = Pattern.compile(
            "(?is).*(proton|steam linux runtime|steamworks common redistributables).*");

    public static boolean isToolAppId(String appId) {
        return appId != null && TOOL_APP_IDS.contains(appId);
    }

    public static boolean isToolName(String name) {
        return name != null && TOOL_NAME.matcher(name).matches();
    }
}
