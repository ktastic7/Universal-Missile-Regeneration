package umr;

import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cached source-mod provenance for weapon and hull diagnostics. */
public final class UMRProvenance {
    private static final Map<String, SourceInfo> weaponSources = new LinkedHashMap<String, SourceInfo>();
    private static final Map<String, SourceInfo> hullSources = new LinkedHashMap<String, SourceInfo>();

    private UMRProvenance() {}

    public static synchronized SourceInfo forWeapon(WeaponSpecAPI spec) {
        if (spec == null) return SourceInfo.unknown();
        String id = safe(spec.getWeaponId());
        SourceInfo cached = weaponSources.get(id);
        if (cached != null) return cached;
        SourceInfo info = fromSource(sourceOf(spec));
        weaponSources.put(id, info);
        return info;
    }

    public static synchronized SourceInfo forHull(ShipHullSpecAPI spec) {
        if (spec == null) return SourceInfo.unknown();
        String id = safe(spec.getHullId());
        SourceInfo cached = hullSources.get(id);
        if (cached != null) return cached;
        SourceInfo info = fromSource(sourceOf(spec));
        hullSources.put(id, info);
        return info;
    }

    public static synchronized Map<String, SourceInfo> weaponSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, SourceInfo>(weaponSources));
    }

    private static ModSpecAPI sourceOf(WeaponSpecAPI spec) {
        try { return spec.getSourceMod(); } catch (Throwable ignored) { return null; }
    }
    private static ModSpecAPI sourceOf(ShipHullSpecAPI spec) {
        try { return spec.getSourceMod(); } catch (Throwable ignored) { return null; }
    }

    private static SourceInfo fromSource(ModSpecAPI source) {
        if (source == null) return SourceInfo.vanilla();
        return new SourceInfo("MOD", safe(source.getId()), safe(source.getName()), safe(source.getVersion()));
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "unknown";
        return value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    public static final class SourceInfo {
        public final String kind;
        public final String id;
        public final String name;
        public final String version;
        SourceInfo(String kind, String id, String name, String version) {
            this.kind=kind; this.id=id; this.name=name; this.version=version;
        }
        public static SourceInfo vanilla() {
            return new SourceInfo("VANILLA", "vanilla", "Starsector", UMRBuildInfo.getGameVersion());
        }
        public static SourceInfo unknown() {
            return new SourceInfo("UNKNOWN", "unknown", "unknown", "unknown");
        }
        public String logFields(String prefix) {
            return " " + prefix + "SourceKind=" + kind +
                    " " + prefix + "SourceId=" + id +
                    " " + prefix + "SourceName=" + name +
                    " " + prefix + "SourceVersion=" + version;
        }
    }
}
