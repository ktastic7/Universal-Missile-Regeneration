package umr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Phase 5C local player per-weapon overrides loaded once at application start. */
public final class UMROverrides {
    public static final String MOD_ID = "universal_missile_regeneration";
    public static final String PATH = "data/config/umr/weapon_overrides.csv";

    public static final String COL_WEAPON_ID = "weapon_id";
    public static final String COL_PACKET = "burst_or_packet_size_override";
    public static final String COL_FULL_MAG = "full_mag_target_seconds_override";
    public static final String COL_PACKET_REGEN = "packet_regen_seconds_override";
    public static final String COL_SAFETY_MULTIPLIER = "safety_multiplier_override";
    public static final String COL_DISABLE_SAFETY = "disable_safety_floor";
    public static final String COL_REASON = "reason";
    public static final String COL_SOURCE_OR_COMMENT = "source_or_comment";

    private static Map<String, Entry> entries = Collections.emptyMap();
    private static List<String> unknownIds = Collections.emptyList();
    private static List<String> warnings = Collections.emptyList();
    private static int duplicateRows = 0;
    private static int blankRows = 0;
    private static int sourceRows = 0;
    private static boolean initialized = false;

    private UMROverrides() {}

    public static synchronized void initialize() {
        Logger log = UMRLogger.get();
        log.info("[UMR][PHASE5C_OVERRIDE_START] path=" + PATH +
                " modId=" + MOD_ID +
                " precedence=hard_exclusion>blacklist>override>global_settings>defaults" +
                " packetIntervalBeatsFullMag=true safetyStillAppliesUnlessDisabled=true failSoft=true");
        try {
            SettingsAPI settings = Global.getSettings();
            Set<String> knownWeaponIds = collectKnownWeaponIds(settings);
            JSONArray rows = settings.loadCSV(PATH, MOD_ID);
            ParseResult parsed = parseRows(rows, knownWeaponIds);
            install(parsed);

            for (Entry entry : entries.values()) {
                if (UMRLogger.verbose()) {
                    log.info("[UMR][PHASE5C_OVERRIDE_ENTRY] weapon=" + entry.weaponId +
                            " packetOverride=" + val(entry.packetSizeOverride) +
                            " fullMagOverrideSec=" + val(entry.fullMagazineTargetSecondsOverride) +
                            " packetRegenOverrideSec=" + val(entry.packetRegenSecondsOverride) +
                            " safetyMultiplierOverride=" + val(entry.safetyMultiplierOverride) +
                            " disableSafetyFloor=" + val(entry.disableSafetyFloor) +
                            " effectiveFields=" + entry.effectiveFieldCount() +
                            " reason=" + safeLog(entry.reason) +
                            " sourceOrComment=" + safeLog(entry.sourceOrComment));
                }
                if (entry.fullMagazineTargetSecondsOverride != null && entry.packetRegenSecondsOverride != null) {
                    log.warn("[UMR][PHASE5C_OVERRIDE_TIMING_CONFLICT] weapon=" + entry.weaponId +
                            " fullMagOverrideSec=" + f(entry.fullMagazineTargetSecondsOverride.doubleValue()) +
                            " packetRegenOverrideSec=" + f(entry.packetRegenSecondsOverride.doubleValue()) +
                            " action=packet_regen_override_wins_nominal_interval");
                }
            }
            for (String id : unknownIds) {
                log.warn("[UMR][PHASE5C_OVERRIDE_UNKNOWN_ID] weapon=" + id + " action=ignore_unknown_row");
            }
            for (String warning : warnings) {
                log.warn("[UMR][PHASE5C_OVERRIDE_FIELD_WARNING] " + warning);
            }
            if (duplicateRows > 0) {
                log.warn("[UMR][PHASE5C_OVERRIDE_DUPLICATE] duplicateRows=" + duplicateRows +
                        " action=first_known_row_wins");
            }

            log.info("[UMR][PHASE5C_OVERRIDE_SUMMARY] sourceRows=" + sourceRows +
                    " activeKnownRows=" + entries.size() +
                    " effectiveKnownRows=" + effectiveRowCount(entries) +
                    " unknownIds=" + unknownIds.size() +
                    " fieldWarnings=" + warnings.size() +
                    " duplicateRows=" + duplicateRows +
                    " blankRows=" + blankRows +
                    " active=" + join(entries.keySet()) +
                    " result=PASS");
        } catch (Throwable t) {
            entries = Collections.emptyMap();
            unknownIds = Collections.emptyList();
            warnings = Collections.emptyList();
            duplicateRows = 0;
            blankRows = 0;
            sourceRows = 0;
            initialized = true;
            log.warn("[UMR][PHASE5C_OVERRIDE_LOAD_WARNING] path=" + PATH +
                    " action=fail_soft_empty_overrides type=" + t.getClass().getSimpleName() +
                    " message=" + safeLog(t.getMessage()));
            log.info("[UMR][PHASE5C_OVERRIDE_SUMMARY] sourceRows=0 activeKnownRows=0 effectiveKnownRows=0" +
                    " unknownIds=0 fieldWarnings=0 duplicateRows=0 blankRows=0 active= result=FAIL_SOFT_EMPTY");
        }
    }

    public static Entry get(String weaponId) {
        return weaponId == null ? null : entries.get(weaponId);
    }

    public static boolean hasEffectiveOverride(String weaponId) {
        Entry e = get(weaponId);
        return e != null && e.effectiveFieldCount() > 0;
    }

    public static int size() { return entries.size(); }
    public static int effectiveSize() { return effectiveRowCount(entries); }
    public static List<String> getUnknownIds() { return unknownIds; }
    public static List<String> getWarnings() { return warnings; }
    public static boolean isInitialized() { return initialized; }

    static ParseResult parseRows(JSONArray rows, Set<String> knownWeaponIds) {
        Map<String, Entry> active = new LinkedHashMap<String, Entry>();
        List<String> unknown = new ArrayList<String>();
        List<String> issues = new ArrayList<String>();
        Set<String> seenUnknown = new HashSet<String>();
        int duplicates = 0;
        int blanks = 0;
        int totalRows = rows == null ? 0 : rows.length();

        for (int i = 0; i < totalRows; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) { blanks++; continue; }
            String id = row.optString(COL_WEAPON_ID, "").trim();
            if (id.isEmpty()) { blanks++; continue; }

            if (active.containsKey(id) || seenUnknown.contains(id)) {
                duplicates++;
                continue;
            }
            if (knownWeaponIds == null || !knownWeaponIds.contains(id)) {
                unknown.add(id);
                seenUnknown.add(id);
                continue;
            }

            Integer packet = parsePositiveInt(row, id, COL_PACKET, issues);
            Double fullMag = parsePositiveDouble(row, id, COL_FULL_MAG, issues);
            Double packetRegen = parsePositiveDouble(row, id, COL_PACKET_REGEN, issues);
            Double safetyMultiplier = parsePositiveDouble(row, id, COL_SAFETY_MULTIPLIER, issues);
            Boolean disableSafety = parseBoolean(row, id, COL_DISABLE_SAFETY, issues);

            active.put(id, new Entry(
                    id, packet, fullMag, packetRegen, safetyMultiplier, disableSafety,
                    row.optString(COL_REASON, "").trim(),
                    row.optString(COL_SOURCE_OR_COMMENT, "").trim()));
        }
        return new ParseResult(active, unknown, issues, duplicates, blanks, totalRows);
    }

    private static Integer parsePositiveInt(JSONObject row, String id, String col, List<String> issues) {
        String raw = cell(row, col);
        if (raw.isEmpty()) return null;
        try {
            int value = Integer.parseInt(raw);
            if (value <= 0) throw new NumberFormatException("non-positive");
            return Integer.valueOf(value);
        } catch (Throwable t) {
            issues.add("weapon=" + id + " field=" + col + " raw=" + safeLog(raw) + " action=ignore_field_fall_through");
            return null;
        }
    }

    private static Double parsePositiveDouble(JSONObject row, String id, String col, List<String> issues) {
        String raw = cell(row, col);
        if (raw.isEmpty()) return null;
        try {
            double value = Double.parseDouble(raw);
            if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0) {
                throw new NumberFormatException("invalid positive double");
            }
            return Double.valueOf(value);
        } catch (Throwable t) {
            issues.add("weapon=" + id + " field=" + col + " raw=" + safeLog(raw) + " action=ignore_field_fall_through");
            return null;
        }
    }

    private static Boolean parseBoolean(JSONObject row, String id, String col, List<String> issues) {
        String raw = cell(row, col);
        if (raw.isEmpty()) return null;
        if ("true".equalsIgnoreCase(raw)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(raw)) return Boolean.FALSE;
        issues.add("weapon=" + id + " field=" + col + " raw=" + safeLog(raw) + " action=ignore_field_fall_through");
        return null;
    }

    private static String cell(JSONObject row, String col) {
        Object raw = row == null ? null : row.opt(col);
        if (raw == null || JSONObject.NULL.equals(raw)) return "";
        return String.valueOf(raw).trim();
    }

    private static Set<String> collectKnownWeaponIds(SettingsAPI settings) {
        Set<String> ids = new HashSet<String>();
        for (WeaponSpecAPI spec : settings.getActuallyAllWeaponSpecs()) {
            if (spec == null || spec.getWeaponId() == null) continue;
            String id = spec.getWeaponId().trim();
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private static void install(ParseResult parsed) {
        entries = Collections.unmodifiableMap(new LinkedHashMap<String, Entry>(parsed.entries));
        unknownIds = Collections.unmodifiableList(new ArrayList<String>(parsed.unknownIds));
        warnings = Collections.unmodifiableList(new ArrayList<String>(parsed.warnings));
        duplicateRows = parsed.duplicateRows;
        blankRows = parsed.blankRows;
        sourceRows = parsed.sourceRows;
        initialized = true;
    }

    private static int effectiveRowCount(Map<String, Entry> map) {
        int count = 0;
        for (Entry e : map.values()) if (e.effectiveFieldCount() > 0) count++;
        return count;
    }

    private static String safeLog(String text) {
        if (text == null) return "";
        return text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    private static String join(Iterable<String> values) {
        StringBuilder b = new StringBuilder();
        for (String value : values) {
            if (b.length() > 0) b.append(',');
            b.append(value);
        }
        return b.toString();
    }

    private static String val(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String f(double value) { return String.format(Locale.ROOT, "%.6f", value); }

    public static final class Entry {
        public final String weaponId;
        public final Integer packetSizeOverride;
        public final Double fullMagazineTargetSecondsOverride;
        public final Double packetRegenSecondsOverride;
        public final Double safetyMultiplierOverride;
        public final Boolean disableSafetyFloor;
        public final String reason;
        public final String sourceOrComment;

        Entry(String weaponId, Integer packetSizeOverride,
              Double fullMagazineTargetSecondsOverride, Double packetRegenSecondsOverride,
              Double safetyMultiplierOverride, Boolean disableSafetyFloor,
              String reason, String sourceOrComment) {
            this.weaponId = weaponId;
            this.packetSizeOverride = packetSizeOverride;
            this.fullMagazineTargetSecondsOverride = fullMagazineTargetSecondsOverride;
            this.packetRegenSecondsOverride = packetRegenSecondsOverride;
            this.safetyMultiplierOverride = safetyMultiplierOverride;
            this.disableSafetyFloor = disableSafetyFloor;
            this.reason = reason;
            this.sourceOrComment = sourceOrComment;
        }

        public int effectiveFieldCount() {
            int count = 0;
            if (packetSizeOverride != null) count++;
            if (fullMagazineTargetSecondsOverride != null) count++;
            if (packetRegenSecondsOverride != null) count++;
            if (safetyMultiplierOverride != null) count++;
            if (disableSafetyFloor != null) count++;
            return count;
        }

        UMRRuleEngine.TimingOverride toTimingOverride() {
            return new UMRRuleEngine.TimingOverride(
                    packetSizeOverride,
                    fullMagazineTargetSecondsOverride,
                    packetRegenSecondsOverride,
                    safetyMultiplierOverride,
                    disableSafetyFloor);
        }
    }

    static final class ParseResult {
        final Map<String, Entry> entries;
        final List<String> unknownIds;
        final List<String> warnings;
        final int duplicateRows;
        final int blankRows;
        final int sourceRows;

        ParseResult(Map<String, Entry> entries, List<String> unknownIds, List<String> warnings,
                    int duplicateRows, int blankRows, int sourceRows) {
            this.entries = entries;
            this.unknownIds = unknownIds;
            this.warnings = warnings;
            this.duplicateRows = duplicateRows;
            this.blankRows = blankRows;
            this.sourceRows = sourceRows;
        }
    }
}
