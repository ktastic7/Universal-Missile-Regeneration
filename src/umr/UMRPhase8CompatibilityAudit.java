package umr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Phase 8A report-only audit of source-attributed missile weapons.
 *
 * Audits all source-attributed missile weapons against the same classifier and
 * configuration authority used by broad third-party gameplay enrollment.
 */
public final class UMRPhase8CompatibilityAudit {
    private static final String WEAPON_DATA_PATH = "data/weapons/weapon_data.csv";
    /** Development watch threshold only; it does not change gameplay. */
    static final int LARGE_BURST_WATCH_THRESHOLD = 8;

    private static boolean initialized;
    private static int lastModMissiles;
    private static int lastEligibleBeforePolicy;
    private static int lastWouldManage;
    private static int lastPolicyBlacklisted;
    private static int lastReviewOrExcluded;
    private static int lastErrors;
    private static int lastSourceMods;

    private UMRPhase8CompatibilityAudit() {}

    public static synchronized void initialize() {
        Logger log = UMRLogger.get();
        int modMissiles = 0;
        int eligibleBeforePolicy = 0;
        int wouldManage = 0;
        int policyBlacklisted = 0;
        int reviewOrExcluded = 0;
        int errors = 0;
        int unknownProvenance = 0;
        int localBlacklistCount = 0;
        int localOverrideCount = 0;
        int thirdPartyBlacklistCount = 0;
        int thirdPartyOverrideCount = 0;
        int defaultCount = 0;
        int remainderWatch = 0;
        int largeBurstWatch = 0;
        int singleBurstMagazineWatch = 0;
        int safetyBindingWatch = 0;

        Map<String, ModSummary> byMod = new TreeMap<String, ModSummary>();

        log.info("[UMR][PHASE8_AUDIT_START] mode=source_attributed_compatibility_telemetry" +
                " thirdPartyGameplay=" + UMRConfig.isThirdPartyGameplayEnabled() +
                " classifier=phase2_validated" +
                " timing=phase5_configurable" +
                " precedence=hard>localBlacklist>localOverride>thirdPartyBlacklist>thirdPartyOverride>global>defaults" +
                " largeBurstWatchThreshold=" + LARGE_BURST_WATCH_THRESHOLD);

        try {
            SettingsAPI settings = Global.getSettings();
            Map<String, JSONObject> rows = loadMergedWeaponRows(settings);
            List<WeaponSpecAPI> specs = new ArrayList<WeaponSpecAPI>(settings.getActuallyAllWeaponSpecs());
            Collections.sort(specs, new Comparator<WeaponSpecAPI>() {
                @Override
                public int compare(WeaponSpecAPI a, WeaponSpecAPI b) {
                    return safeId(a).compareTo(safeId(b));
                }
            });

            for (WeaponSpecAPI spec : specs) {
                if (spec == null || spec.getType() != WeaponAPI.WeaponType.MISSILE) continue;
                ModSpecAPI source = null;
                try { source = spec.getSourceMod(); } catch (Throwable ignored) {}
                if (source == null) continue;
                modMissiles++;

                String id = safeId(spec);
                String modId = safe(source.getId());
                String modName = safe(source.getName());
                String modVersion = safe(source.getVersion());
                ModSummary summary = byMod.get(modId);
                if (summary == null) {
                    summary = new ModSummary(modId, modName, modVersion);
                    byMod.put(modId, summary);
                }
                summary.totalMissiles++;

                try {
                    UMRProvenance.SourceInfo provenance = UMRProvenance.forWeapon(spec);
                    if ("unknown".equalsIgnoreCase(provenance.id) || "UNKNOWN".equals(provenance.kind)) {
                        unknownProvenance++;
                        summary.unknownProvenance++;
                    }

                    JSONObject row = rows.get(id);
                    boolean csvPresent = row != null;
                    boolean systemHint = spec.getAIHints() != null && spec.getAIHints().contains(WeaponAPI.AIHints.SYSTEM);
                    boolean rowSystemHint = tokenContains(rowString(row, "hints"), "SYSTEM");
                    boolean systemOrHelper = systemHint || rowSystemHint;
                    boolean restricted = spec.hasTag("restricted") || tokenContains(rowString(row, "tags"), "restricted");
                    boolean noStandardData = spec.hasTag("no_standard_data") || tokenContains(rowString(row, "tags"), "no_standard_data");
                    int baseMax = spec.getMaxAmmo();
                    int burst = Math.max(1, spec.getBurstSize());
                    double nativeAmmoPerSecond = spec.getAmmoPerSecond();
                    boolean usesAmmo = spec.usesAmmo();

                    String classification = UMRRuleEngine.classify(
                            systemOrHelper, usesAmmo, baseMax, nativeAmmoPerSecond,
                            restricted || noStandardData, csvPresent);
                    summary.countClassification(classification);

                    if (!UMRRuleEngine.ELIGIBLE.equals(classification)) {
                        reviewOrExcluded++;
                        if (UMRLogger.verbose()) {
                            log.info("[UMR][PHASE8_AUDIT_WEAPON] weapon=" + safeLog(id) +
                                    provenance.logFields("weapon") +
                                    " classification=" + classification +
                                    " candidate=false" +
                                    " baseMax=" + baseMax +
                                    " burst=" + burst +
                                    " nativeAmmoPerSecond=" + f(nativeAmmoPerSecond) +
                                    " usesAmmo=" + usesAmmo +
                                    " csvPresent=" + csvPresent +
                                    " tags=" + safeLog(join(spec.getTags())) +
                                    " rowTags=" + safeLog(rowString(row, "tags")) +
                                    " rowHints=" + safeLog(rowString(row, "hints")));
                        }
                        continue;
                    }

                    eligibleBeforePolicy++;
                    summary.eligibleBeforePolicy++;

                    UMROverrides.Entry localOverrideEntry = UMROverrides.get(id);
                    boolean hasLocalOverride = localOverrideEntry != null && localOverrideEntry.effectiveFieldCount() > 0;
                    UMRThirdPartyConfig.BlacklistEntry thirdPartyBlacklistEntry = UMRThirdPartyConfig.getBlacklist(id);
                    UMRThirdPartyConfig.OverrideEntry thirdPartyOverrideContainer = UMRThirdPartyConfig.getOverride(id);
                    UMROverrides.Entry thirdPartyOverrideEntry = thirdPartyOverrideContainer == null ? null : thirdPartyOverrideContainer.entry;
                    boolean hasThirdPartyOverride = thirdPartyOverrideEntry != null && thirdPartyOverrideEntry.effectiveFieldCount() > 0;
                    boolean localBlacklisted = UMRBlacklist.isBlacklisted(id);

                    Policy policy = resolveEligiblePolicy(localBlacklisted, hasLocalOverride,
                            thirdPartyBlacklistEntry != null, hasThirdPartyOverride);

                    UMROverrides.Entry effectiveOverride = null;
                    String overrideSource = "";
                    switch (policy) {
                        case LOCAL_BLACKLIST:
                            localBlacklistCount++;
                            policyBlacklisted++;
                            summary.policyBlacklisted++;
                            break;
                        case LOCAL_OVERRIDE:
                            localOverrideCount++;
                            wouldManage++;
                            summary.wouldManage++;
                            effectiveOverride = localOverrideEntry;
                            overrideSource = UMRBlacklist.MOD_ID;
                            break;
                        case THIRD_PARTY_BLACKLIST:
                            thirdPartyBlacklistCount++;
                            policyBlacklisted++;
                            summary.policyBlacklisted++;
                            break;
                        case THIRD_PARTY_OVERRIDE:
                            thirdPartyOverrideCount++;
                            wouldManage++;
                            summary.wouldManage++;
                            effectiveOverride = thirdPartyOverrideEntry;
                            overrideSource = thirdPartyOverrideContainer == null ? "" : thirdPartyOverrideContainer.sourceModId;
                            break;
                        default:
                            defaultCount++;
                            wouldManage++;
                            summary.wouldManage++;
                            break;
                    }

                    double chargedown = valueOrZero(rowDouble(row, "chargedown"));
                    double burstDelay = valueOrZero(rowDouble(row, "burst delay"));
                    UMRRuleEngine.Timing timing = UMRRuleEngine.computeTiming(
                            baseMax, burst, String.valueOf(spec.getSize()), chargedown, burstDelay,
                            UMRConfig.getTimingSettings(),
                            effectiveOverride == null ? null : effectiveOverride.toTimingOverride());

                    Risk risk = riskFor(baseMax, burst, timing);
                    if (risk.remainder) { remainderWatch++; summary.remainderWatch++; }
                    if (risk.largeBurst) { largeBurstWatch++; summary.largeBurstWatch++; }
                    if (risk.singleBurstMagazine) { singleBurstMagazineWatch++; summary.singleBurstMagazineWatch++; }
                    if (risk.safetyBinding) { safetyBindingWatch++; summary.safetyBindingWatch++; }

                    if (UMRLogger.verbose()) {
                        log.info("[UMR][PHASE8_AUDIT_WEAPON] weapon=" + safeLog(id) +
                                provenance.logFields("weapon") +
                                " classification=ELIGIBLE" +
                                " candidate=true" +
                                " wouldManage=" + policy.wouldManage +
                                " policy=" + policy +
                                " overrideSource=" + safeLog(overrideSource) +
                                " baseMax=" + baseMax +
                                " burst=" + burst +
                                " packet=" + timing.packetSize +
                                " intervalSec=" + f(timing.finalPacketSeconds) +
                                " fullMagSec=" + f(timing.actualFullMagazineSeconds) +
                                " safetyBinding=" + timing.safetyFloorBinding +
                                " remainder=" + timing.baseMagazineRemainder +
                                " risk=" + risk.label() +
                                " tags=" + safeLog(join(spec.getTags())) +
                                " rowTags=" + safeLog(rowString(row, "tags")) +
                                " rowHints=" + safeLog(rowString(row, "hints")));
                    }
                } catch (Throwable t) {
                    errors++;
                    summary.errors++;
                    log.error("[UMR][PHASE8_AUDIT_WEAPON_ERROR] weapon=" + safeLog(id) +
                            " sourceMod=" + safeLog(modId) + " action=skip_audit_weapon", t);
                }
            }
        } catch (Throwable t) {
            errors++;
            log.error("[UMR][PHASE8_AUDIT_ERROR] audit initialization failed; action=fail_soft_audit_only", t);
        }

        for (ModSummary s : byMod.values()) {
            log.info("[UMR][PHASE8_MOD_SUMMARY] sourceMod=" + safeLog(s.modId) +
                    " sourceName=" + safeLog(s.modName) +
                    " sourceVersion=" + safeLog(s.modVersion) +
                    " missileSpecs=" + s.totalMissiles +
                    " eligibleBeforePolicy=" + s.eligibleBeforePolicy +
                    " wouldManage=" + s.wouldManage +
                    " policyBlacklisted=" + s.policyBlacklisted +
                    " systemHelper=" + s.systemHelper +
                    " nativeRegen=" + s.nativeRegen +
                    " infiniteUndefined=" + s.infiniteUndefined +
                    " restrictedSpecial=" + s.restrictedSpecial +
                    " missingTiming=" + s.missingTiming +
                    " remainderWatch=" + s.remainderWatch +
                    " largeBurstWatch=" + s.largeBurstWatch +
                    " singleBurstMagazineWatch=" + s.singleBurstMagazineWatch +
                    " safetyBindingWatch=" + s.safetyBindingWatch +
                    " unknownProvenance=" + s.unknownProvenance +
                    " errors=" + s.errors);
        }

        initialized = true;
        lastModMissiles = modMissiles;
        lastEligibleBeforePolicy = eligibleBeforePolicy;
        lastWouldManage = wouldManage;
        lastPolicyBlacklisted = policyBlacklisted;
        lastReviewOrExcluded = reviewOrExcluded;
        lastErrors = errors;
        lastSourceMods = byMod.size();

        log.info("[UMR][PHASE8_AUDIT_SUMMARY] sourceMods=" + byMod.size() +
                " modMissileSpecs=" + modMissiles +
                " eligibleBeforePolicy=" + eligibleBeforePolicy +
                " wouldManage=" + wouldManage +
                " policyBlacklisted=" + policyBlacklisted +
                " reviewOrExcluded=" + reviewOrExcluded +
                " localBlacklist=" + localBlacklistCount +
                " localOverride=" + localOverrideCount +
                " thirdPartyBlacklist=" + thirdPartyBlacklistCount +
                " thirdPartyOverride=" + thirdPartyOverrideCount +
                " defaultPolicy=" + defaultCount +
                " remainderWatch=" + remainderWatch +
                " largeBurstWatch=" + largeBurstWatch +
                " singleBurstMagazineWatch=" + singleBurstMagazineWatch +
                " safetyBindingWatch=" + safetyBindingWatch +
                " unknownProvenance=" + unknownProvenance +
                " errors=" + errors +
                " gameplayEnrollment=" + (UMRConfig.isThirdPartyGameplayEnabled() ? "all_eligible_third_party" : "disabled") +
                " result=" + (errors == 0 ? "PASS" : "PARTIAL"));
    }

    public static boolean isInitialized() { return initialized; }
    public static String diagnosticSummary() {
        return "sourceMods=" + lastSourceMods +
                " modMissileSpecs=" + lastModMissiles +
                " eligibleBeforePolicy=" + lastEligibleBeforePolicy +
                " wouldManage=" + lastWouldManage +
                " policyBlacklisted=" + lastPolicyBlacklisted +
                " reviewOrExcluded=" + lastReviewOrExcluded +
                " errors=" + lastErrors;
    }

    static Policy resolveEligiblePolicy(boolean localBlacklisted,
                                        boolean hasLocalOverride,
                                        boolean hasThirdPartyBlacklist,
                                        boolean hasThirdPartyOverride) {
        if (localBlacklisted) return Policy.LOCAL_BLACKLIST;
        if (hasLocalOverride) return Policy.LOCAL_OVERRIDE;
        if (hasThirdPartyBlacklist) return Policy.THIRD_PARTY_BLACKLIST;
        if (hasThirdPartyOverride) return Policy.THIRD_PARTY_OVERRIDE;
        return Policy.DEFAULT;
    }

    static Risk riskFor(int baseMax, int burst, UMRRuleEngine.Timing timing) {
        int b = Math.max(1, burst);
        boolean singleBurstMagazine = baseMax > 0 && baseMax <= b;
        boolean largeBurst = b >= LARGE_BURST_WATCH_THRESHOLD;
        boolean remainder = timing != null && timing.baseMagazineRemainder > 0;
        boolean safetyBinding = timing != null && timing.safetyFloorBinding;
        return new Risk(singleBurstMagazine, largeBurst, remainder, safetyBinding);
    }

    enum Policy {
        LOCAL_BLACKLIST(false),
        LOCAL_OVERRIDE(true),
        THIRD_PARTY_BLACKLIST(false),
        THIRD_PARTY_OVERRIDE(true),
        DEFAULT(true);
        final boolean wouldManage;
        Policy(boolean wouldManage) { this.wouldManage = wouldManage; }
    }

    static final class Risk {
        final boolean singleBurstMagazine;
        final boolean largeBurst;
        final boolean remainder;
        final boolean safetyBinding;
        Risk(boolean singleBurstMagazine, boolean largeBurst, boolean remainder, boolean safetyBinding) {
            this.singleBurstMagazine = singleBurstMagazine;
            this.largeBurst = largeBurst;
            this.remainder = remainder;
            this.safetyBinding = safetyBinding;
        }
        String label() {
            StringBuilder b = new StringBuilder();
            if (singleBurstMagazine) append(b, "SINGLE_BURST_MAGAZINE");
            if (largeBurst) append(b, "LARGE_BURST");
            if (remainder) append(b, "NON_DIVISIBLE_MAGAZINE");
            if (safetyBinding) append(b, "SAFETY_FLOOR_BINDING");
            return b.length() == 0 ? "NONE" : b.toString();
        }
        private static void append(StringBuilder b, String s) {
            if (b.length() > 0) b.append('|');
            b.append(s);
        }
    }

    private static final class ModSummary {
        final String modId;
        final String modName;
        final String modVersion;
        int totalMissiles;
        int eligibleBeforePolicy;
        int wouldManage;
        int policyBlacklisted;
        int systemHelper;
        int nativeRegen;
        int infiniteUndefined;
        int restrictedSpecial;
        int missingTiming;
        int remainderWatch;
        int largeBurstWatch;
        int singleBurstMagazineWatch;
        int safetyBindingWatch;
        int unknownProvenance;
        int errors;
        ModSummary(String modId, String modName, String modVersion) {
            this.modId = modId; this.modName = modName; this.modVersion = modVersion;
        }
        void countClassification(String c) {
            if (UMRRuleEngine.EXCLUDE_SYSTEM_HELPER.equals(c)) systemHelper++;
            else if (UMRRuleEngine.PRESERVE_NATIVE_REGEN.equals(c)) nativeRegen++;
            else if (UMRRuleEngine.EXCLUDE_INFINITE_OR_UNDEFINED.equals(c)) infiniteUndefined++;
            else if (UMRRuleEngine.REVIEW_RESTRICTED_SPECIAL.equals(c)) restrictedSpecial++;
            else if (UMRRuleEngine.REVIEW_MISSING_TIMING_DATA.equals(c)) missingTiming++;
        }
    }

    private static Map<String, JSONObject> loadMergedWeaponRows(SettingsAPI settings) throws Exception {
        JSONArray data = settings.getMergedSpreadsheetData("id", WEAPON_DATA_PATH);
        Map<String, JSONObject> rows = new LinkedHashMap<String, JSONObject>();
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.getJSONObject(i);
            String id = row.optString("id", "").trim();
            if (!id.isEmpty()) rows.put(id, row);
        }
        return rows;
    }

    private static boolean tokenContains(String text, String needle) {
        if (text == null || needle == null) return false;
        String[] parts = text.split(",");
        for (String p : parts) if (needle.equalsIgnoreCase(p.trim())) return true;
        return false;
    }
    private static String rowString(JSONObject row, String key) { return row == null ? "" : row.optString(key, ""); }
    private static Double rowDouble(JSONObject row, String key) {
        if (row == null) return null;
        String text = row.optString(key, "");
        if (text == null || text.trim().isEmpty()) return null;
        try { return Double.valueOf(text.trim()); } catch (Throwable ignored) { return null; }
    }
    private static double valueOrZero(Double value) { return value == null ? 0.0 : value.doubleValue(); }
    private static String safeId(WeaponSpecAPI spec) {
        if (spec == null || spec.getWeaponId() == null) return "";
        return spec.getWeaponId().trim();
    }
    private static String safe(String text) {
        if (text == null || text.trim().isEmpty()) return "unknown";
        return text.replace('\n',' ').replace('\r',' ').replace('\t',' ').trim();
    }
    private static String safeLog(String text) {
        if (text == null) return "";
        return text.replace('\n',' ').replace('\r',' ').replace('\t',' ').trim();
    }
    private static String join(Set<String> values) {
        if (values == null || values.isEmpty()) return "";
        List<String> sorted = new ArrayList<String>();
        for (String value : values) if (value != null) sorted.add(value);
        Collections.sort(sorted);
        StringBuilder b = new StringBuilder();
        for (String s : sorted) {
            if (b.length() > 0) b.append(',');
            b.append(s);
        }
        return b.toString();
    }
    private static String f(double value) { return String.format(Locale.ROOT, "%.6f", value); }
}
