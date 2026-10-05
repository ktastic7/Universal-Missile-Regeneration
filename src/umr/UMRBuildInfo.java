package umr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONObject;

/** Cached runtime/build identity and enabled-mod inventory retained from Phase 6 diagnostics. */
public final class UMRBuildInfo {
    public static final String MOD_ID = "universal_missile_regeneration";
    private static final String KEY_VERSION = "umrDevelopmentVersion";
    private static final String KEY_PHASE = "umrDevelopmentPhase";
    private static final String KEY_CORE = "umrDevelopmentCore";

    private static String version = "unknown";
    private static String settingsVersion = "unknown";
    private static String phase = "unknown";
    private static String core = "unknown";
    private static String gameVersion = "unknown";
    private static String javaVersion = "unknown";
    private static List<ModInfo> enabledMods = Collections.emptyList();
    private static boolean initialized;

    private UMRBuildInfo() {}

    public static synchronized void initialize() {
        if (initialized) return;
        List<ModInfo> mods = new ArrayList<ModInfo>();
        try {
            SettingsAPI settings = Global.getSettings();
            gameVersion = safe(settings.getGameVersion());
            JSONObject json = settings.getSettingsJSON();
            settingsVersion = json == null ? "unknown" : safe(json.optString(KEY_VERSION, "unknown"));
            phase = json == null ? "unknown" : safe(json.optString(KEY_PHASE, "unknown"));
            core = json == null ? "unknown" : safe(json.optString(KEY_CORE, "unknown"));
            ModManagerAPI mm = settings.getModManager();
            if (mm != null) {
                ModSpecAPI self = mm.getModSpec(MOD_ID);
                if (self != null) version = safe(self.getVersion());
                List<ModSpecAPI> enabled = mm.getEnabledModsCopy();
                if (enabled != null) {
                    for (ModSpecAPI mod : enabled) {
                        if (mod == null) continue;
                        mods.add(new ModInfo(safe(mod.getId()), safe(mod.getName()), safe(mod.getVersion())));
                    }
                }
            }
        } catch (Throwable ignored) {
            // Diagnostics must never block mod loading.
        }
        try { javaVersion = safe(System.getProperty("java.version")); }
        catch (Throwable ignored) {}
        Collections.sort(mods, new Comparator<ModInfo>() {
            @Override public int compare(ModInfo a, ModInfo b) { return a.id.compareTo(b.id); }
        });
        enabledMods = Collections.unmodifiableList(mods);
        initialized = true;
    }

    public static String getVersion() { return version; }
    public static String getSettingsVersion() { return settingsVersion; }
    public static boolean isIdentityConsistent() {
        return "unknown".equals(version) || "unknown".equals(settingsVersion) || version.equals(settingsVersion);
    }
    public static String getPhase() { return phase; }
    public static String getCore() { return core; }
    public static String getGameVersion() { return gameVersion; }
    public static String getJavaVersion() { return javaVersion; }
    public static List<ModInfo> getEnabledMods() { return enabledMods; }
    public static int getEnabledModCount() { return enabledMods.size(); }

    public static String enabledModsCompact() {
        StringBuilder b = new StringBuilder();
        for (ModInfo m : enabledMods) {
            if (b.length() > 0) b.append(';');
            b.append(m.id).append('@').append(m.version);
        }
        return b.toString();
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "unknown";
        return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    public static final class ModInfo {
        public final String id;
        public final String name;
        public final String version;
        ModInfo(String id, String name, String version) {
            this.id = id; this.name = name; this.version = version;
        }
    }
}
