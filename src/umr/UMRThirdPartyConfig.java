package umr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Phase 5D merged third-party blacklist/override contribution loader.
 *
 * Contributors append rows to the shared paths using a unique row_id. UMR then
 * resolves multiple contributions targeting the same weapon deterministically
 * by source_mod_id and row_id. Local player files are loaded separately and
 * remain higher authority in UMRProductionRegistry.
 */
public final class UMRThirdPartyConfig {
    public static final String BLACKLIST_PATH = "data/config/umr/third_party_blacklist.csv";
    public static final String OVERRIDE_PATH = "data/config/umr/third_party_overrides.csv";
    public static final String COL_ROW_ID = "row_id";
    public static final String COL_WEAPON_ID = "weapon_id";
    public static final String COL_SOURCE_MOD_ID = "source_mod_id";
    public static final String COL_REASON = "reason";

    private static Map<String, BlacklistEntry> blacklist = Collections.emptyMap();
    private static Map<String, OverrideEntry> overrides = Collections.emptyMap();
    private static List<String> warnings = Collections.emptyList();
    private static List<String> unknownIds = Collections.emptyList();
    private static int blacklistSourceRows;
    private static int overrideSourceRows;
    private static int blacklistConflicts;
    private static int overrideConflicts;
    private static int blankRows;
    private static boolean initialized;

    private UMRThirdPartyConfig() {}

    public static synchronized void initialize() {
        Logger log = UMRLogger.get();
        log.info("[UMR][PHASE5D_THIRD_PARTY_CONFIG_START] blacklistPath=" + BLACKLIST_PATH +
                " overridePath=" + OVERRIDE_PATH +
                " mergeKey=" + COL_ROW_ID +
                " precedence=hard>local_blacklist>local_override>third_party_blacklist>third_party_override>global>defaults" +
                " thirdPartyGameplay=" + UMRConfig.isThirdPartyGameplayEnabled());
        try {
            SettingsAPI settings = Global.getSettings();
            Set<String> knownIds = collectKnownWeaponIds(settings);
            List<String> issues = new ArrayList<String>();
            List<String> unknown = new ArrayList<String>();

            BlacklistParse bp = parseBlacklist(
                    settings.getMergedSpreadsheetData(COL_ROW_ID, BLACKLIST_PATH), knownIds, issues, unknown);
            OverrideParse op = parseOverrides(
                    settings.getMergedSpreadsheetData(COL_ROW_ID, OVERRIDE_PATH), knownIds, issues, unknown);

            blacklist = Collections.unmodifiableMap(bp.winners);
            overrides = Collections.unmodifiableMap(op.winners);
            warnings = Collections.unmodifiableList(new ArrayList<String>(issues));
            unknownIds = Collections.unmodifiableList(new ArrayList<String>(unknown));
            blacklistSourceRows = bp.sourceRows;
            overrideSourceRows = op.sourceRows;
            blacklistConflicts = bp.conflicts;
            overrideConflicts = op.conflicts;
            blankRows = bp.blankRows + op.blankRows;
            initialized = true;

            for (String issue : warnings) log.warn("[UMR][PHASE5D_THIRD_PARTY_WARNING] " + issue);
            for (String id : unknownIds) log.warn("[UMR][PHASE5D_THIRD_PARTY_UNKNOWN_ID] weapon=" + safe(id) + " action=ignore_row");
            for (BlacklistEntry e : blacklist.values()) {
                if (UMRLogger.verbose()) log.info("[UMR][PHASE5D_THIRD_PARTY_BLACKLIST_ENTRY] weapon=" + e.weaponId +
                        " sourceMod=" + safe(e.sourceModId) + " rowId=" + safe(e.rowId) +
                        " reason=" + safe(e.reason));
            }
            for (OverrideEntry e : overrides.values()) {
                if (UMRLogger.verbose()) log.info("[UMR][PHASE5D_THIRD_PARTY_OVERRIDE_ENTRY] weapon=" + e.weaponId +
                        " sourceMod=" + safe(e.sourceModId) + " rowId=" + safe(e.rowId) +
                        " packetOverride=" + val(e.entry.packetSizeOverride) +
                        " fullMagOverrideSec=" + val(e.entry.fullMagazineTargetSecondsOverride) +
                        " packetRegenOverrideSec=" + val(e.entry.packetRegenSecondsOverride) +
                        " safetyMultiplierOverride=" + val(e.entry.safetyMultiplierOverride) +
                        " disableSafetyFloor=" + val(e.entry.disableSafetyFloor) +
                        " effectiveFields=" + e.entry.effectiveFieldCount());
            }
            log.info("[UMR][PHASE5D_THIRD_PARTY_CONFIG_SUMMARY] blacklistSourceRows=" + blacklistSourceRows +
                    " blacklistActiveWeapons=" + blacklist.size() +
                    " blacklistConflicts=" + blacklistConflicts +
                    " overrideSourceRows=" + overrideSourceRows +
                    " overrideActiveWeapons=" + overrides.size() +
                    " overrideConflicts=" + overrideConflicts +
                    " unknownIds=" + unknownIds.size() +
                    " warnings=" + warnings.size() +
                    " blankRows=" + blankRows +
                    " result=PASS");
        } catch (Throwable t) {
            blacklist = Collections.emptyMap();
            overrides = Collections.emptyMap();
            warnings = Collections.emptyList();
            unknownIds = Collections.emptyList();
            blacklistSourceRows = overrideSourceRows = blacklistConflicts = overrideConflicts = blankRows = 0;
            initialized = true;
            log.warn("[UMR][PHASE5D_THIRD_PARTY_CONFIG_LOAD_WARNING] action=fail_soft_empty type=" +
                    t.getClass().getSimpleName() + " message=" + safe(t.getMessage()));
            log.info("[UMR][PHASE5D_THIRD_PARTY_CONFIG_SUMMARY] blacklistSourceRows=0 blacklistActiveWeapons=0" +
                    " blacklistConflicts=0 overrideSourceRows=0 overrideActiveWeapons=0 overrideConflicts=0" +
                    " unknownIds=0 warnings=0 blankRows=0 result=FAIL_SOFT_EMPTY");
        }
    }

    public static boolean isBlacklisted(String weaponId) {
        return weaponId != null && blacklist.containsKey(weaponId);
    }
    public static BlacklistEntry getBlacklist(String weaponId) {
        return weaponId == null ? null : blacklist.get(weaponId);
    }
    public static OverrideEntry getOverride(String weaponId) {
        return weaponId == null ? null : overrides.get(weaponId);
    }
    public static int blacklistSize() { return blacklist.size(); }
    public static int overrideSize() { return overrides.size(); }
    public static int getBlacklistConflicts() { return blacklistConflicts; }
    public static int getOverrideConflicts() { return overrideConflicts; }
    public static List<String> getWarnings() { return warnings; }
    public static List<String> getUnknownIds() { return unknownIds; }
    public static boolean isInitialized() { return initialized; }

    static BlacklistParse parseBlacklist(JSONArray rows, Set<String> knownIds,
                                         List<String> issues, List<String> unknown) {
        List<BlacklistEntry> parsed = new ArrayList<BlacklistEntry>();
        int blanks = 0;
        int n = rows == null ? 0 : rows.length();
        Set<String> unknownSeen = new HashSet<String>();
        for (int i = 0; i < n; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) { blanks++; continue; }
            String rowId = cell(row, COL_ROW_ID);
            String weaponId = cell(row, COL_WEAPON_ID);
            String sourceMod = cell(row, COL_SOURCE_MOD_ID);
            if (rowId.isEmpty() && weaponId.isEmpty() && sourceMod.isEmpty()) { blanks++; continue; }
            if (rowId.isEmpty() || weaponId.isEmpty() || sourceMod.isEmpty()) {
                issues.add("kind=blacklist row=" + i + " rowId=" + safe(rowId) + " weapon=" + safe(weaponId) +
                        " sourceMod=" + safe(sourceMod) + " action=ignore_missing_required_field");
                continue;
            }
            if (knownIds == null || !knownIds.contains(weaponId)) {
                if (unknownSeen.add(weaponId)) unknown.add(weaponId);
                continue;
            }
            parsed.add(new BlacklistEntry(rowId, weaponId, sourceMod, cell(row, COL_REASON)));
        }
        Collections.sort(parsed, BLACKLIST_ORDER);
        Map<String, BlacklistEntry> winners = new LinkedHashMap<String, BlacklistEntry>();
        int conflicts = 0;
        for (BlacklistEntry e : parsed) {
            BlacklistEntry prev = winners.get(e.weaponId);
            if (prev == null) winners.put(e.weaponId, e);
            else {
                conflicts++;
                issues.add("kind=blacklist_conflict weapon=" + e.weaponId +
                        " winnerSource=" + safe(prev.sourceModId) + " winnerRow=" + safe(prev.rowId) +
                        " ignoredSource=" + safe(e.sourceModId) + " ignoredRow=" + safe(e.rowId) +
                        " action=deterministic_first_source_then_row_wins");
            }
        }
        return new BlacklistParse(new LinkedHashMap<String, BlacklistEntry>(winners), n, conflicts, blanks);
    }

    static OverrideParse parseOverrides(JSONArray rows, Set<String> knownIds,
                                        List<String> issues, List<String> unknown) {
        List<OverrideEntry> parsed = new ArrayList<OverrideEntry>();
        int blanks = 0;
        int n = rows == null ? 0 : rows.length();
        Set<String> unknownSeen = new HashSet<String>();
        for (int i = 0; i < n; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) { blanks++; continue; }
            String rowId = cell(row, COL_ROW_ID);
            String weaponId = cell(row, COL_WEAPON_ID);
            String sourceMod = cell(row, COL_SOURCE_MOD_ID);
            if (rowId.isEmpty() && weaponId.isEmpty() && sourceMod.isEmpty()) { blanks++; continue; }
            if (rowId.isEmpty() || weaponId.isEmpty() || sourceMod.isEmpty()) {
                issues.add("kind=override row=" + i + " rowId=" + safe(rowId) + " weapon=" + safe(weaponId) +
                        " sourceMod=" + safe(sourceMod) + " action=ignore_missing_required_field");
                continue;
            }
            if (knownIds == null || !knownIds.contains(weaponId)) {
                if (unknownSeen.add(weaponId)) unknown.add(weaponId);
                continue;
            }
            List<String> fieldIssues = new ArrayList<String>();
            Integer packet = parsePositiveInt(row, weaponId, UMROverrides.COL_PACKET, fieldIssues);
            Double fullMag = parsePositiveDouble(row, weaponId, UMROverrides.COL_FULL_MAG, fieldIssues);
            Double packetRegen = parsePositiveDouble(row, weaponId, UMROverrides.COL_PACKET_REGEN, fieldIssues);
            Double safetyMult = parsePositiveDouble(row, weaponId, UMROverrides.COL_SAFETY_MULTIPLIER, fieldIssues);
            Boolean disableSafety = parseBoolean(row, weaponId, UMROverrides.COL_DISABLE_SAFETY, fieldIssues);
            for (String fi : fieldIssues) issues.add("sourceMod=" + safe(sourceMod) + " rowId=" + safe(rowId) + " " + fi);
            UMROverrides.Entry entry = new UMROverrides.Entry(
                    weaponId, packet, fullMag, packetRegen, safetyMult, disableSafety,
                    cell(row, UMROverrides.COL_REASON), sourceMod + ":" + rowId);
            if (entry.effectiveFieldCount() <= 0) {
                issues.add("kind=override_empty weapon=" + weaponId + " sourceMod=" + safe(sourceMod) +
                        " rowId=" + safe(rowId) + " action=ignore_no_effective_fields");
                continue;
            }
            parsed.add(new OverrideEntry(rowId, weaponId, sourceMod, entry));
        }
        Collections.sort(parsed, OVERRIDE_ORDER);
        Map<String, OverrideEntry> winners = new LinkedHashMap<String, OverrideEntry>();
        int conflicts = 0;
        for (OverrideEntry e : parsed) {
            OverrideEntry prev = winners.get(e.weaponId);
            if (prev == null) winners.put(e.weaponId, e);
            else {
                conflicts++;
                issues.add("kind=override_conflict weapon=" + e.weaponId +
                        " winnerSource=" + safe(prev.sourceModId) + " winnerRow=" + safe(prev.rowId) +
                        " ignoredSource=" + safe(e.sourceModId) + " ignoredRow=" + safe(e.rowId) +
                        " action=deterministic_first_source_then_row_wins");
            }
        }
        return new OverrideParse(new LinkedHashMap<String, OverrideEntry>(winners), n, conflicts, blanks);
    }

    private static final Comparator<BlacklistEntry> BLACKLIST_ORDER = new Comparator<BlacklistEntry>() {
        @Override public int compare(BlacklistEntry a, BlacklistEntry b) {
            int d = a.weaponId.compareTo(b.weaponId); if (d != 0) return d;
            d = a.sourceModId.compareTo(b.sourceModId); if (d != 0) return d;
            return a.rowId.compareTo(b.rowId);
        }
    };
    private static final Comparator<OverrideEntry> OVERRIDE_ORDER = new Comparator<OverrideEntry>() {
        @Override public int compare(OverrideEntry a, OverrideEntry b) {
            int d = a.weaponId.compareTo(b.weaponId); if (d != 0) return d;
            d = a.sourceModId.compareTo(b.sourceModId); if (d != 0) return d;
            return a.rowId.compareTo(b.rowId);
        }
    };

    private static Set<String> collectKnownWeaponIds(SettingsAPI settings) {
        Set<String> ids = new HashSet<String>();
        for (WeaponSpecAPI spec : settings.getActuallyAllWeaponSpecs()) {
            if (spec == null || spec.getWeaponId() == null) continue;
            String id = spec.getWeaponId().trim();
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private static Integer parsePositiveInt(JSONObject row, String id, String col, List<String> issues) {
        String raw = cell(row, col); if (raw.isEmpty()) return null;
        try { int v = Integer.parseInt(raw); if (v <= 0) throw new NumberFormatException(); return Integer.valueOf(v); }
        catch (Throwable t) { issues.add("weapon=" + id + " field=" + col + " raw=" + safe(raw) + " action=ignore_field_fall_through"); return null; }
    }
    private static Double parsePositiveDouble(JSONObject row, String id, String col, List<String> issues) {
        String raw = cell(row, col); if (raw.isEmpty()) return null;
        try { double v = Double.parseDouble(raw); if (Double.isNaN(v) || Double.isInfinite(v) || v <= 0d) throw new NumberFormatException(); return Double.valueOf(v); }
        catch (Throwable t) { issues.add("weapon=" + id + " field=" + col + " raw=" + safe(raw) + " action=ignore_field_fall_through"); return null; }
    }
    private static Boolean parseBoolean(JSONObject row, String id, String col, List<String> issues) {
        String raw = cell(row, col); if (raw.isEmpty()) return null;
        if ("true".equalsIgnoreCase(raw)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(raw)) return Boolean.FALSE;
        issues.add("weapon=" + id + " field=" + col + " raw=" + safe(raw) + " action=ignore_field_fall_through");
        return null;
    }
    private static String cell(JSONObject row, String col) {
        Object raw = row == null ? null : row.opt(col);
        if (raw == null || JSONObject.NULL.equals(raw)) return "";
        return String.valueOf(raw).trim();
    }
    private static String safe(String text) {
        if (text == null) return "";
        return text.replace('\n',' ').replace('\r',' ').replace('\t',' ').trim();
    }
    private static String val(Object value) { return value == null ? "" : String.valueOf(value); }

    public static final class BlacklistEntry {
        public final String rowId;
        public final String weaponId;
        public final String sourceModId;
        public final String reason;
        BlacklistEntry(String rowId, String weaponId, String sourceModId, String reason) {
            this.rowId=rowId; this.weaponId=weaponId; this.sourceModId=sourceModId; this.reason=reason;
        }
    }
    public static final class OverrideEntry {
        public final String rowId;
        public final String weaponId;
        public final String sourceModId;
        public final UMROverrides.Entry entry;
        OverrideEntry(String rowId, String weaponId, String sourceModId, UMROverrides.Entry entry) {
            this.rowId=rowId; this.weaponId=weaponId; this.sourceModId=sourceModId; this.entry=entry;
        }
    }
    static final class BlacklistParse {
        final Map<String, BlacklistEntry> winners; final int sourceRows; final int conflicts; final int blankRows;
        BlacklistParse(Map<String, BlacklistEntry> winners,int sourceRows,int conflicts,int blankRows){this.winners=winners;this.sourceRows=sourceRows;this.conflicts=conflicts;this.blankRows=blankRows;}
    }
    static final class OverrideParse {
        final Map<String, OverrideEntry> winners; final int sourceRows; final int conflicts; final int blankRows;
        OverrideParse(Map<String, OverrideEntry> winners,int sourceRows,int conflicts,int blankRows){this.winners=winners;this.sourceRows=sourceRows;this.conflicts=conflicts;this.blankRows=blankRows;}
    }
}
