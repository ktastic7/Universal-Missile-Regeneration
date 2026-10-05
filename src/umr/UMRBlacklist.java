package umr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Phase 5C inherited local player blacklist loaded once at application start. */
public final class UMRBlacklist {
    public static final String MOD_ID = "universal_missile_regeneration";
    public static final String PATH = "data/config/umr/weapon_blacklist.csv";
    public static final String COL_WEAPON_ID = "weapon_id";
    public static final String COL_REASON = "reason";
    public static final String COL_SOURCE_OR_COMMENT = "source_or_comment";

    private static Map<String, Entry> entries = Collections.emptyMap();
    private static List<String> unknownIds = Collections.emptyList();
    private static int duplicateRows = 0;
    private static int blankRows = 0;
    private static int sourceRows = 0;
    private static boolean initialized = false;

    private UMRBlacklist() {}

    public static synchronized void initialize() {
        Logger log = UMRLogger.get();
        log.info("[UMR][PHASE5C_BLACKLIST_START] path=" + PATH +
                " modId=" + MOD_ID +
                " columns=" + COL_WEAPON_ID + "," + COL_REASON + "," + COL_SOURCE_OR_COMMENT +
                " precedence=blacklist_before_local_override failSoft=true");

        try {
            SettingsAPI settings = Global.getSettings();
            Set<String> knownWeaponIds = collectKnownWeaponIds(settings);
            JSONArray rows = settings.loadCSV(PATH, MOD_ID);
            ParseResult parsed = parseRows(rows, knownWeaponIds);
            install(parsed);

            for (Entry entry : entries.values()) {
                if (UMRLogger.verbose()) {
                    log.info("[UMR][PHASE5C_BLACKLIST_ENTRY] weapon=" + entry.weaponId +
                            " reason=" + safeLog(entry.reason) +
                            " sourceOrComment=" + safeLog(entry.sourceOrComment) +
                            " known=true active=true");
                }
            }
            for (String id : unknownIds) {
                log.warn("[UMR][PHASE5C_BLACKLIST_UNKNOWN_ID] weapon=" + id +
                        " action=ignore_unknown_row");
            }
            if (duplicateRows > 0) {
                log.warn("[UMR][PHASE5C_BLACKLIST_DUPLICATE] duplicateRows=" + duplicateRows +
                        " action=first_known_row_wins");
            }

            log.info("[UMR][PHASE5C_BLACKLIST_SUMMARY] sourceRows=" + sourceRows +
                    " activeKnownIds=" + entries.size() +
                    " unknownIds=" + unknownIds.size() +
                    " duplicateRows=" + duplicateRows +
                    " blankRows=" + blankRows +
                    " active=" + join(entries.keySet()) +
                    " result=PASS");
        } catch (Throwable t) {
            entries = Collections.emptyMap();
            unknownIds = Collections.emptyList();
            duplicateRows = 0;
            blankRows = 0;
            sourceRows = 0;
            initialized = true;
            log.warn("[UMR][PHASE5C_BLACKLIST_LOAD_WARNING] path=" + PATH +
                    " action=fail_soft_empty_blacklist type=" + t.getClass().getSimpleName() +
                    " message=" + safeLog(t.getMessage()));
            log.info("[UMR][PHASE5C_BLACKLIST_SUMMARY] sourceRows=0 activeKnownIds=0 unknownIds=0" +
                    " duplicateRows=0 blankRows=0 active= result=FAIL_SOFT_EMPTY");
        }
    }

    public static boolean isBlacklisted(String weaponId) {
        return weaponId != null && entries.containsKey(weaponId);
    }

    public static Entry get(String weaponId) {
        return weaponId == null ? null : entries.get(weaponId);
    }

    public static int size() {
        return entries.size();
    }

    public static Set<String> getIds() {
        return entries.keySet();
    }

    public static List<String> getUnknownIds() {
        return unknownIds;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    static ParseResult parseRows(JSONArray rows, Set<String> knownWeaponIds) {
        Map<String, Entry> active = new LinkedHashMap<String, Entry>();
        List<String> unknown = new ArrayList<String>();
        Set<String> seenUnknown = new HashSet<String>();
        int duplicates = 0;
        int blanks = 0;
        int totalRows = rows == null ? 0 : rows.length();

        for (int i = 0; i < totalRows; i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) {
                blanks++;
                continue;
            }
            String id = row.optString(COL_WEAPON_ID, "").trim();
            if (id.isEmpty()) {
                blanks++;
                continue;
            }

            if (active.containsKey(id) || seenUnknown.contains(id)) {
                duplicates++;
                continue;
            }

            if (knownWeaponIds == null || !knownWeaponIds.contains(id)) {
                unknown.add(id);
                seenUnknown.add(id);
                continue;
            }

            active.put(id, new Entry(
                    id,
                    row.optString(COL_REASON, "").trim(),
                    row.optString(COL_SOURCE_OR_COMMENT, "").trim()));
        }

        return new ParseResult(active, unknown, duplicates, blanks, totalRows);
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
        duplicateRows = parsed.duplicateRows;
        blankRows = parsed.blankRows;
        sourceRows = parsed.sourceRows;
        initialized = true;
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

    public static final class Entry {
        public final String weaponId;
        public final String reason;
        public final String sourceOrComment;

        Entry(String weaponId, String reason, String sourceOrComment) {
            this.weaponId = weaponId;
            this.reason = reason;
            this.sourceOrComment = sourceOrComment;
        }
    }

    static final class ParseResult {
        final Map<String, Entry> entries;
        final List<String> unknownIds;
        final int duplicateRows;
        final int blankRows;
        final int sourceRows;

        ParseResult(Map<String, Entry> entries, List<String> unknownIds,
                    int duplicateRows, int blankRows, int sourceRows) {
            this.entries = entries;
            this.unknownIds = unknownIds;
            this.duplicateRows = duplicateRows;
            this.blankRows = blankRows;
            this.sourceRows = sourceRows;
        }
    }
}
