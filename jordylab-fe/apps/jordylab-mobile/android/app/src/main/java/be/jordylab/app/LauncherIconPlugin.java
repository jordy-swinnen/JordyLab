package be.jordylab.app;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.Map;

/**
 * Switches the home-screen icon to the one of the chosen theme. Android has no API to change an app's icon, so the manifest
 * declares one launcher activity-alias per theme and this plugin enables the chosen one and disables the others.
 *
 * Switching can make a launcher drop the pinned shortcut of the old alias, so the web side only calls this when the theme's
 * icon actually changes.
 */
@CapacitorPlugin(name = "LauncherIcon")
public class LauncherIconPlugin extends Plugin {

    /** Theme id → alias class declared in AndroidManifest.xml. The ids are the ones in themes.js. */
    private static final Map<String, String> ALIAS_BY_THEME = Map.of(
        "content", ".LauncherIconContent",
        "oxblood", ".LauncherIconOxblood",
        "flare", ".LauncherIconFlare",
        "paper", ".LauncherIconPaper",
        "glasshouse", ".LauncherIconGlasshouse",
        "heather", ".LauncherIconHeather"
    );

    @PluginMethod
    public void set(PluginCall call) {
        String theme = call.getString("theme");
        if (theme == null || !ALIAS_BY_THEME.containsKey(theme)) {
            call.reject("Unknown theme: " + theme);
            return;
        }
        PackageManager packageManager = getContext().getPackageManager();
        String packageName = getContext().getPackageName();
        String wanted = ALIAS_BY_THEME.get(theme);
        // Enable the new alias before disabling the others so there is never a moment without a launcher entry.
        setEnabled(packageManager, packageName, wanted, true);
        for (String alias : ALIAS_BY_THEME.values()) {
            if (!alias.equals(wanted)) {
                setEnabled(packageManager, packageName, alias, false);
            }
        }
        call.resolve(new JSObject().put("theme", theme));
    }

    /** DONT_KILL_APP keeps the running app alive; Android applies the change to the launcher shortly after. */
    private void setEnabled(PackageManager packageManager, String packageName, String alias, boolean enabled) {
        ComponentName component = new ComponentName(packageName, packageName + alias);
        int state = enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (packageManager.getComponentEnabledSetting(component) == state) {
            return;
        }
        packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP);
    }
}
