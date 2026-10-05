package umr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/** Phase 2 report-only whole-mod classifier/calculator. No gameplay mutation. */
public final class UMRReportGenerator {
    private static final String WEAPON_DATA_PATH = "data/weapons/weapon_data.csv";

    private UMRReportGenerator() {}

    public static void run() {
        Logger log = UMRLogger.get();
        log.info("[UMR][PHASE2_REPORT_START] stage=APPLICATION_LOAD gameplayMutation=false combatPlugin=false");
        log.info("[UMR][PHASE2_DEFAULTS] baseFullMagSeconds=" + f(UMRDefaults.BASE_FULL_MAG_SECONDS) +
                " singleAmmoReductionSeconds=" + f(UMRDefaults.SINGLE_AMMO_REDUCTION_SECONDS) +
                " mediumWeaponAdditionSeconds=" + f(UMRDefaults.MEDIUM_ADDITION_SECONDS) +
                " largeWeaponAdditionSeconds=" + f(UMRDefaults.LARGE_ADDITION_SECONDS) +
                " safetyMultiplier=" + f(UMRDefaults.SAFETY_MULTIPLIER));

        try {
            SettingsAPI settings = Global.getSettings();
            Map<String, JSONObject> rows = loadMergedWeaponRows(settings, log);
            List<WeaponSpecAPI> specs = new ArrayList<WeaponSpecAPI>(settings.getActuallyAllWeaponSpecs());
            Collections.sort(specs, new Comparator<WeaponSpecAPI>() {
                @Override
                public int compare(WeaponSpecAPI a, WeaponSpecAPI b) {
                    return safeId(a).compareTo(safeId(b));
                }
            });

            int totalSpecs = 0;
            int uniqueSpecs = 0;
            int missileSpecs = 0;
            int eligible = 0;
            int systemHelper = 0;
            int nativeRegen = 0;
            int infinite = 0;
            int restrictedReview = 0;
            int missingTiming = 0;
            int csvMissingAnyMissile = 0;
            int csvSpecAmmoMismatch = 0;
            int csvSpecBurstMismatch = 0;
            int safetyBindingCount = 0;
            int nonDivisibleBaseCount = 0;
            int resolvedModSource = 0;
            int unattributedSource = 0;
            int loggedMissiles = 0;

            Set<String> seen = new HashSet<String>();
            List<String> digestLines = new ArrayList<String>();

            for (WeaponSpecAPI spec : specs) {
                if (spec == null) continue;
                totalSpecs++;
                String id = spec.getWeaponId();
                if (id == null || id.trim().isEmpty() || !seen.add(id)) continue;
                uniqueSpecs++;
                if (spec.getType() != WeaponAPI.WeaponType.MISSILE) continue;
                missileSpecs++;

                JSONObject row = rows.get(id);
                boolean csvPresent = row != null;
                if (!csvPresent) csvMissingAnyMissile++;

                boolean systemHint = spec.getAIHints() != null && spec.getAIHints().contains(WeaponAPI.AIHints.SYSTEM);
                boolean rowSystemHint = tokenContains(rowString(row, "hints"), "SYSTEM");
                boolean systemOrHelper = systemHint || rowSystemHint;
                boolean restricted = spec.hasTag("restricted") || tokenContains(rowString(row, "tags"), "restricted");
                boolean noStandardData = spec.hasTag("no_standard_data") || tokenContains(rowString(row, "tags"), "no_standard_data");
                int baseMax = spec.getMaxAmmo();
                int burst = Math.max(1, spec.getBurstSize());
                double ammoPerSecond = spec.getAmmoPerSecond();
                boolean usesAmmo = spec.usesAmmo();
                String size = String.valueOf(spec.getSize());

                String classification = UMRRuleEngine.classify(systemOrHelper, usesAmmo, baseMax,
                        ammoPerSecond, restricted || noStandardData, csvPresent);

                if (UMRRuleEngine.ELIGIBLE.equals(classification)) eligible++;
                else if (UMRRuleEngine.EXCLUDE_SYSTEM_HELPER.equals(classification)) systemHelper++;
                else if (UMRRuleEngine.PRESERVE_NATIVE_REGEN.equals(classification)) nativeRegen++;
                else if (UMRRuleEngine.EXCLUDE_INFINITE_OR_UNDEFINED.equals(classification)) infinite++;
                else if (UMRRuleEngine.REVIEW_RESTRICTED_SPECIAL.equals(classification)) restrictedReview++;
                else if (UMRRuleEngine.REVIEW_MISSING_TIMING_DATA.equals(classification)) missingTiming++;

                Double csvAmmo = rowDouble(row, "ammo");
                Double csvBurst = rowDouble(row, "burst size");
                if (csvAmmo != null && usesAmmo && baseMax != Integer.MAX_VALUE && Math.abs(csvAmmo - baseMax) > 0.000001) {
                    csvSpecAmmoMismatch++;
                }
                if (csvBurst != null && Math.abs(csvBurst - burst) > 0.000001) {
                    csvSpecBurstMismatch++;
                }

                double chargedown = valueOrZero(rowDouble(row, "chargedown"));
                double burstDelay = valueOrZero(rowDouble(row, "burst delay"));
                UMRRuleEngine.Timing timing = null;
                if (UMRRuleEngine.ELIGIBLE.equals(classification)) {
                    timing = UMRRuleEngine.computeTiming(baseMax, burst, size, chargedown, burstDelay, UMRConfig.getTimingSettings());
                    if (timing.safetyFloorBinding) safetyBindingCount++;
                    if (timing.baseMagazineRemainder != 0) nonDivisibleBaseCount++;
                }

                ModSpecAPI source = null;
                try { source = spec.getSourceMod(); } catch (Throwable ignored) {}
                if (source != null) resolvedModSource++; else unattributedSource++;

                String core = buildCoreLine(spec, source, classification, csvPresent,
                        systemOrHelper, restricted, noStandardData, usesAmmo, baseMax, burst,
                        ammoPerSecond, chargedown, burstDelay, csvAmmo, csvBurst, timing);
                log.info("[UMR][PHASE2_WEAPON] " + core);
                digestLines.add(core);
                loggedMissiles++;
            }

            String digest = sha256(digestLines);
            int mappedRows = rows.size();
            int orphanCsvRows = 0;
            for (String id : rows.keySet()) {
                if (!seen.contains(id)) orphanCsvRows++;
            }

            log.info("[UMR][PHASE2_SUMMARY] totalSpecObjects=" + totalSpecs +
                    " uniqueWeaponSpecs=" + uniqueSpecs +
                    " mergedWeaponRows=" + mappedRows +
                    " missileSpecs=" + missileSpecs +
                    " loggedMissiles=" + loggedMissiles +
                    " eligible=" + eligible +
                    " excludeSystemHelper=" + systemHelper +
                    " preserveNativeRegen=" + nativeRegen +
                    " excludeInfiniteUndefined=" + infinite +
                    " reviewRestrictedSpecial=" + restrictedReview +
                    " reviewMissingTiming=" + missingTiming +
                    " missileMissingCsvRow=" + csvMissingAnyMissile +
                    " csvSpecAmmoMismatch=" + csvSpecAmmoMismatch +
                    " csvSpecBurstMismatch=" + csvSpecBurstMismatch +
                    " eligibleSafetyFloorBinding=" + safetyBindingCount +
                    " eligibleNonDivisibleBase=" + nonDivisibleBaseCount +
                    " missileResolvedModSource=" + resolvedModSource +
                    " missileUnattributedSource=" + unattributedSource +
                    " orphanMergedCsvRows=" + orphanCsvRows +
                    " reportDigestSha256=" + digest +
                    " gameplayMutation=false");
            log.info("[UMR][PHASE2_REPORT_END] stage=APPLICATION_LOAD missileSpecs=" + missileSpecs +
                    " loggedMissiles=" + loggedMissiles + " reportDigestSha256=" + digest +
                    " gameplayMutation=false");
        } catch (Throwable t) {
            log.error("[UMR][PHASE2_ERROR] report generation failed; gameplayMutation=false", t);
        }
    }

    private static Map<String, JSONObject> loadMergedWeaponRows(SettingsAPI settings, Logger log) throws Exception {
        JSONArray data = settings.getMergedSpreadsheetData("id", WEAPON_DATA_PATH);
        Map<String, JSONObject> rows = new HashMap<String, JSONObject>();
        int duplicateIds = 0;
        int blankIds = 0;
        for (int i = 0; i < data.length(); i++) {
            JSONObject row = data.getJSONObject(i);
            String id = row.optString("id", "").trim();
            if (id.isEmpty()) { blankIds++; continue; }
            if (rows.put(id, row) != null) duplicateIds++;
        }
        log.info("[UMR][PHASE2_MERGED_WEAPON_DATA] path=" + WEAPON_DATA_PATH +
                " rawRows=" + data.length() + " mappedIds=" + rows.size() +
                " duplicateIdsAfterMerge=" + duplicateIds + " blankIds=" + blankIds);
        return rows;
    }

    private static String buildCoreLine(WeaponSpecAPI spec, ModSpecAPI source, String classification,
                                        boolean csvPresent, boolean systemOrHelper, boolean restricted,
                                        boolean noStandardData, boolean usesAmmo, int baseMax, int burst,
                                        double ammoPerSecond, double chargedown, double burstDelay,
                                        Double csvAmmo, Double csvBurst, UMRRuleEngine.Timing timing) {
        StringBuilder b = new StringBuilder();
        b.append("id=").append(safe(spec.getWeaponId()));
        b.append(" name=\"").append(quoted(spec.getWeaponName())).append("\"");
        b.append(" sourceId=").append(source == null ? "null" : safe(source.getId()));
        b.append(" sourceVersion=\"").append(source == null ? "null" : quoted(source.getVersion())).append("\"");
        b.append(" classification=").append(classification);
        b.append(" size=").append(String.valueOf(spec.getSize()));
        b.append(" usesAmmo=").append(usesAmmo);
        b.append(" baseMax=").append(baseMax);
        b.append(" burst=").append(burst);
        b.append(" nativeAmmoPerSec=").append(f(ammoPerSecond));
        b.append(" systemOrHelper=").append(systemOrHelper);
        b.append(" restricted=").append(restricted);
        b.append(" noStandardData=").append(noStandardData);
        b.append(" csvPresent=").append(csvPresent);
        b.append(" csvAmmo=").append(csvAmmo == null ? "NA" : f(csvAmmo));
        b.append(" csvBurst=").append(csvBurst == null ? "NA" : f(csvBurst));
        b.append(" chargedownOrCooldown=").append(f(chargedown));
        b.append(" burstDelay=").append(f(burstDelay));
        b.append(" hints=\"").append(quoted(sortedHints(spec))).append("\"");
        b.append(" tags=\"").append(quoted(sortedTags(spec))).append("\"");
        if (timing != null) {
            b.append(" packet=").append(timing.packetSize);
            b.append(" packetsPerBaseMag=").append(timing.packetsPerBaseMagazine);
            b.append(" adjustedFullMagTargetSec=").append(f(timing.adjustedFullMagazineTargetSeconds));
            b.append(" nominalPacketSec=").append(f(timing.nominalPacketSeconds));
            b.append(" baseFireCycleSec=").append(f(timing.baseFireCycleSeconds));
            b.append(" safetyFloorSec=").append(f(timing.safetyFloorSeconds));
            b.append(" finalPacketSec=").append(f(timing.finalPacketSeconds));
            b.append(" actualFullMagSec=").append(f(timing.actualFullMagazineSeconds));
            b.append(" safetyBinding=").append(timing.safetyFloorBinding);
            b.append(" baseRemainder=").append(timing.baseMagazineRemainder);
        } else {
            b.append(" packet=NA packetsPerBaseMag=NA adjustedFullMagTargetSec=NA nominalPacketSec=NA");
            b.append(" baseFireCycleSec=NA safetyFloorSec=NA finalPacketSec=NA actualFullMagSec=NA safetyBinding=NA baseRemainder=NA");
        }
        return b.toString();
    }

    private static String sortedHints(WeaponSpecAPI spec) {
        List<String> out = new ArrayList<String>();
        if (spec.getAIHints() != null) {
            for (WeaponAPI.AIHints h : spec.getAIHints()) out.add(h.name());
        }
        Collections.sort(out);
        return join(out);
    }

    private static String sortedTags(WeaponSpecAPI spec) {
        List<String> out = new ArrayList<String>();
        if (spec.getTags() != null) out.addAll(spec.getTags());
        Collections.sort(out);
        return join(out);
    }

    private static String join(List<String> values) {
        StringBuilder b = new StringBuilder();
        for (String v : values) {
            if (b.length() > 0) b.append(',');
            b.append(v);
        }
        return b.toString();
    }

    private static boolean tokenContains(String text, String needle) {
        if (text == null || needle == null) return false;
        String[] parts = text.split(",");
        for (String p : parts) {
            if (needle.equalsIgnoreCase(p.trim())) return true;
        }
        return false;
    }

    private static String rowString(JSONObject row, String key) {
        if (row == null) return "";
        return row.optString(key, "");
    }

    private static Double rowDouble(JSONObject row, String key) {
        if (row == null) return null;
        String text = row.optString(key, "");
        if (text == null) return null;
        text = text.trim();
        if (text.isEmpty()) return null;
        try { return Double.valueOf(text); } catch (Throwable ignored) { return null; }
    }

    private static double valueOrZero(Double value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    private static String safeId(WeaponSpecAPI spec) {
        return spec == null || spec.getWeaponId() == null ? "" : spec.getWeaponId();
    }

    private static String safe(String value) {
        if (value == null) return "null";
        return value.replace(' ', '_').replace('\n', '_').replace('\r', '_').replace('|', '/');
    }

    private static String quoted(String value) {
        if (value == null) return "null";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace('\n', ' ').replace('\r', ' ');
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private static String sha256(List<String> lines) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String line : lines) {
            md.update(line.getBytes(StandardCharsets.UTF_8));
            md.update((byte) '\n');
        }
        byte[] bytes = md.digest();
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        return hex.toString();
    }
}
