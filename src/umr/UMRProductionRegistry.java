package umr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
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
 * Production application-load registry using the validated Phase 2 classifier,
 * Phase 5 timing/configuration authority, and broad eligible third-party enrollment.
 * Hard/native/system/special exclusions remain authoritative for both vanilla and
 * source-attributed weapons.
 */
public final class UMRProductionRegistry {
    private static final String WEAPON_DATA_PATH = "data/weapons/weapon_data.csv";
    private static final int RC8_EXPECTED_ELIGIBLE_VANILLA = 31;

    private static Map<String, Entry> entries = Collections.emptyMap();
    private static boolean initialized = false;
    private static int lastVanillaMissileSpecs;
    private static int lastModSourceMissilesSkipped;
    private static int lastSystemHelper;
    private static int lastNativeRegen;
    private static int lastInfinite;
    private static int lastRestrictedReview;
    private static int lastMissingTiming;
    private static int lastLocalBlacklisted;
    private static int lastThirdPartyBlacklisted;
    private static int lastManagedVanilla;
    private static int lastManagedThirdParty;
    private static int lastPhase8CohortSeen;
    private static int lastPhase8CohortPolicySuppressed;
    private static int lastPhase8CohortRejected;
    private static int lastPhase8CohortMismatch;
    private static int lastErrors;

    private UMRProductionRegistry() {}

    public static synchronized void initialize() {
        Logger log = UMRLogger.get();
        Map<String, Entry> built = new LinkedHashMap<String, Entry>();
        int totalSpecObjects = 0;
        int uniqueWeaponSpecs = 0;
        int missileSpecs = 0;
        int modSourceMissilesSkipped = 0;
        int vanillaMissileSpecs = 0;
        int vanillaSystemHelper = 0;
        int vanillaNativeRegen = 0;
        int vanillaInfinite = 0;
        int vanillaRestrictedReview = 0;
        int vanillaMissingTiming = 0;
        int vanillaBlacklisted = 0;
        int vanillaLocalOverrideApplied = 0;
        int vanillaThirdPartyOverrideApplied = 0;
        int localOverrideSuppressedByLocalBlacklist = 0;
        int thirdPartyOverrideSuppressedByLocalBlacklist = 0;
        int thirdPartyBlacklistSuppressedByLocalOverride = 0;
        int thirdPartyOverrideSuppressedByLocalOverride = 0;
        int thirdPartyBlacklisted = 0;
        int localOverrideIgnoredHardExclusion = 0;
        int thirdPartyOverrideIgnoredHardExclusion = 0;
        int duplicateIds = 0;
        int phase8CohortSeen = 0;
        int phase8CohortManaged = 0;
        int phase8CohortPolicySuppressed = 0;
        int phase8CohortRejected = 0;
        int phase8CohortMismatch = 0;
        int errors = 0;

        log.info("[UMR][PHASE5D_REGISTRY_START] selection=all_phase2_eligible_vanilla_plus_eligible_third_party" +
                " expectedRc8EligibleVanilla=" + RC8_EXPECTED_ELIGIBLE_VANILLA +
                " timingModel=phase2_validated_configurable vanillaOnly=false thirdPartyGameplay=" + UMRConfig.isThirdPartyGameplayEnabled() +
                " localBlacklistLoaded=" + UMRBlacklist.size() +
                " localOverrideRowsLoaded=" + UMROverrides.size() +
                " localOverrideEffectiveRows=" + UMROverrides.effectiveSize() +
                " thirdPartyBlacklistLoaded=" + UMRThirdPartyConfig.blacklistSize() +
                " thirdPartyOverrideLoaded=" + UMRThirdPartyConfig.overrideSize());

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

            Set<String> seen = new HashSet<String>();
            for (WeaponSpecAPI spec : specs) {
                if (spec == null) continue;
                totalSpecObjects++;

                String id = safeId(spec);
                if (id.isEmpty()) continue;
                if (!seen.add(id)) {
                    duplicateIds++;
                    continue;
                }
                uniqueWeaponSpecs++;

                if (spec.getType() != WeaponAPI.WeaponType.MISSILE) continue;
                missileSpecs++;

                ModSpecAPI source = null;
                try { source = spec.getSourceMod(); } catch (Throwable ignored) {}
                UMRProvenance.SourceInfo provenance = UMRProvenance.forWeapon(spec);
                if (source != null) {
                    UMRPhase8Enrollment.Decision phase8Decision =
                            UMRPhase8Enrollment.decide(UMRConfig.isThirdPartyGameplayEnabled(), source);
                    if (!phase8Decision.enroll) {
                        modSourceMissilesSkipped++;
                        if (UMRLogger.verbose()) logLoadReport(spec, provenance,
                                "SOURCE_ATTRIBUTED_GAMEPLAY_" + phase8Decision, false,
                                spec.getMaxAmmo(), Math.max(1, spec.getBurstSize()), spec.getAmmoPerSecond(),
                                "NONE", "", "NONE", "", null);
                        continue;
                    }

                    phase8CohortSeen++; // broad Phase 8 source-attributed specs considered
                    try {
                        SourceEnrollmentOutcome outcome = registerPhase8SourceWeapon(
                                spec, source, provenance, rows, built, log);
                        if (outcome == SourceEnrollmentOutcome.MANAGED) phase8CohortManaged++;
                        else if (outcome == SourceEnrollmentOutcome.POLICY_SUPPRESSED) phase8CohortPolicySuppressed++;
                        else if (outcome == SourceEnrollmentOutcome.REJECTED) phase8CohortRejected++;
                        else if (outcome == SourceEnrollmentOutcome.HARD_EXCLUDED) modSourceMissilesSkipped++;
                    } catch (Throwable t) {
                        errors++;
                        phase8CohortRejected++;
                        log.error("[UMR][PHASE8_THIRD_PARTY_ENTRY_ERROR] weapon=" + id +
                                " sourceMod=" + safeLog(source.getId()) +
                                " action=defer_weapon", t);
                    }
                    continue;
                }

                vanillaMissileSpecs++;
                try {
                    JSONObject row = rows.get(id);
                    boolean csvPresent = row != null;
                    boolean systemHint = spec.getAIHints() != null &&
                            spec.getAIHints().contains(WeaponAPI.AIHints.SYSTEM);
                    boolean rowSystemHint = tokenContains(rowString(row, "hints"), "SYSTEM");
                    boolean systemOrHelper = systemHint || rowSystemHint;
                    boolean restricted = spec.hasTag("restricted") ||
                            tokenContains(rowString(row, "tags"), "restricted");
                    boolean noStandardData = spec.hasTag("no_standard_data") ||
                            tokenContains(rowString(row, "tags"), "no_standard_data");
                    int baseMax = spec.getMaxAmmo();
                    int burst = Math.max(1, spec.getBurstSize());
                    double nativeAmmoPerSecond = spec.getAmmoPerSecond();
                    boolean usesAmmo = spec.usesAmmo();

                    String classification = UMRRuleEngine.classify(
                            systemOrHelper,
                            usesAmmo,
                            baseMax,
                            nativeAmmoPerSecond,
                            restricted || noStandardData,
                            csvPresent);

                    UMROverrides.Entry localOverrideEntry = UMROverrides.get(id);
                    boolean hasLocalOverride = localOverrideEntry != null && localOverrideEntry.effectiveFieldCount() > 0;
                    UMRThirdPartyConfig.BlacklistEntry thirdPartyBlacklistEntry = UMRThirdPartyConfig.getBlacklist(id);
                    UMRThirdPartyConfig.OverrideEntry thirdPartyOverrideContainer = UMRThirdPartyConfig.getOverride(id);
                    UMROverrides.Entry thirdPartyOverrideEntry = thirdPartyOverrideContainer == null ? null : thirdPartyOverrideContainer.entry;
                    boolean hasThirdPartyOverride = thirdPartyOverrideEntry != null && thirdPartyOverrideEntry.effectiveFieldCount() > 0;

                    if (!UMRRuleEngine.ELIGIBLE.equals(classification)) {
                        if (UMRRuleEngine.EXCLUDE_SYSTEM_HELPER.equals(classification)) vanillaSystemHelper++;
                        else if (UMRRuleEngine.PRESERVE_NATIVE_REGEN.equals(classification)) vanillaNativeRegen++;
                        else if (UMRRuleEngine.EXCLUDE_INFINITE_OR_UNDEFINED.equals(classification)) vanillaInfinite++;
                        else if (UMRRuleEngine.REVIEW_RESTRICTED_SPECIAL.equals(classification)) vanillaRestrictedReview++;
                        else if (UMRRuleEngine.REVIEW_MISSING_TIMING_DATA.equals(classification)) vanillaMissingTiming++;

                        if (hasLocalOverride) {
                            localOverrideIgnoredHardExclusion++;
                            log.info("[UMR][PHASE5D_LOCAL_OVERRIDE_IGNORED_HARD_EXCLUSION] weapon=" + id +
                                    " classification=" + classification +
                                    " action=preserve_hard_exclusion");
                        }
                        if (hasThirdPartyOverride) {
                            thirdPartyOverrideIgnoredHardExclusion++;
                            log.info("[UMR][PHASE5D_THIRD_PARTY_OVERRIDE_IGNORED_HARD_EXCLUSION] weapon=" + id +
                                    " sourceMod=" + safeLog(thirdPartyOverrideContainer.sourceModId) +
                                    " classification=" + classification +
                                    " action=preserve_hard_exclusion");
                        }
                        if (UMRLogger.verbose()) log.info("[UMR][PHASE5D_VANILLA_CLASSIFICATION] weapon=" + id +
                                " classification=" + classification +
                                " managed=false baseMax=" + baseMax +
                                " burst=" + burst +
                                " ammoPerSecond=" + f(nativeAmmoPerSecond) +
                                " csvPresent=" + csvPresent);
                        if (UMRLogger.verbose()) logLoadReport(spec, provenance, classification, false,
                                baseMax, burst, nativeAmmoPerSecond,
                                "NONE", "",
                                hasLocalOverride ? "LOCAL_IGNORED_HARD_EXCLUSION" :
                                        (hasThirdPartyOverride ? "THIRD_PARTY_IGNORED_HARD_EXCLUSION" : "NONE"),
                                hasLocalOverride ? UMRBlacklist.MOD_ID :
                                        (hasThirdPartyOverride ? thirdPartyOverrideContainer.sourceModId : ""),
                                null);
                        continue;
                    }

                    if (UMRBlacklist.isBlacklisted(id)) {
                        vanillaBlacklisted++;
                        if (hasLocalOverride) {
                            localOverrideSuppressedByLocalBlacklist++;
                            log.info("[UMR][PHASE5D_LOCAL_OVERRIDE_SUPPRESSED_BY_LOCAL_BLACKLIST] weapon=" + id +
                                    " effectiveFields=" + localOverrideEntry.effectiveFieldCount() +
                                    " action=local_blacklist_wins");
                        }
                        if (hasThirdPartyOverride) {
                            thirdPartyOverrideSuppressedByLocalBlacklist++;
                            log.info("[UMR][PHASE5D_THIRD_PARTY_OVERRIDE_SUPPRESSED_BY_LOCAL_BLACKLIST] weapon=" + id +
                                    " sourceMod=" + safeLog(thirdPartyOverrideContainer.sourceModId) +
                                    " action=local_blacklist_wins");
                        }
                        UMRBlacklist.Entry blacklistEntry = UMRBlacklist.get(id);
                        log.info("[UMR][PHASE5D_LOCAL_BLACKLIST_APPLIED] weapon=" + id +
                                " classification=ELIGIBLE managed=false" +
                                " reason=" + safeLog(blacklistEntry == null ? "" : blacklistEntry.reason) +
                                " sourceOrComment=" + safeLog(blacklistEntry == null ? "" : blacklistEntry.sourceOrComment));
                        if (UMRLogger.verbose()) logLoadReport(spec, provenance, "ELIGIBLE", false,
                                baseMax, burst, nativeAmmoPerSecond,
                                "LOCAL", UMRBlacklist.MOD_ID, "NONE", "", null);
                        continue;
                    }

                    // Local override is explicit player authority and therefore outranks all third-party contributions.
                    UMROverrides.Entry effectiveOverride = null;
                    String overrideAuthority = "NONE";
                    String overrideSource = "";
                    if (hasLocalOverride) {
                        effectiveOverride = localOverrideEntry;
                        overrideAuthority = "LOCAL";
                        overrideSource = UMRBlacklist.MOD_ID;
                        if (thirdPartyBlacklistEntry != null) {
                            thirdPartyBlacklistSuppressedByLocalOverride++;
                            log.info("[UMR][PHASE5D_THIRD_PARTY_BLACKLIST_SUPPRESSED_BY_LOCAL_OVERRIDE] weapon=" + id +
                                    " thirdPartySource=" + safeLog(thirdPartyBlacklistEntry.sourceModId) +
                                    " action=local_override_wins");
                        }
                        if (hasThirdPartyOverride) {
                            thirdPartyOverrideSuppressedByLocalOverride++;
                            log.info("[UMR][PHASE5D_THIRD_PARTY_OVERRIDE_SUPPRESSED_BY_LOCAL_OVERRIDE] weapon=" + id +
                                    " thirdPartySource=" + safeLog(thirdPartyOverrideContainer.sourceModId) +
                                    " action=local_override_wins");
                        }
                    } else if (thirdPartyBlacklistEntry != null) {
                        thirdPartyBlacklisted++;
                        log.info("[UMR][PHASE5D_THIRD_PARTY_BLACKLIST_APPLIED] weapon=" + id +
                                " sourceMod=" + safeLog(thirdPartyBlacklistEntry.sourceModId) +
                                " rowId=" + safeLog(thirdPartyBlacklistEntry.rowId) +
                                " classification=ELIGIBLE managed=false reason=" + safeLog(thirdPartyBlacklistEntry.reason));
                        if (UMRLogger.verbose()) logLoadReport(spec, provenance, "ELIGIBLE", false,
                                baseMax, burst, nativeAmmoPerSecond,
                                "THIRD_PARTY", thirdPartyBlacklistEntry.sourceModId,
                                "NONE", "", null);
                        continue;
                    } else if (hasThirdPartyOverride) {
                        effectiveOverride = thirdPartyOverrideEntry;
                        overrideAuthority = "THIRD_PARTY";
                        overrideSource = thirdPartyOverrideContainer.sourceModId;
                    }

                    double chargedown = valueOrZero(rowDouble(row, "chargedown"));
                    double burstDelay = valueOrZero(rowDouble(row, "burst delay"));
                    UMRRuleEngine.Timing timing = UMRRuleEngine.computeTiming(
                            baseMax,
                            burst,
                            String.valueOf(spec.getSize()),
                            chargedown,
                            burstDelay,
                            UMRConfig.getTimingSettings(),
                            effectiveOverride == null ? null : effectiveOverride.toTimingOverride());
                    if ("LOCAL".equals(overrideAuthority)) vanillaLocalOverrideApplied++;
                    else if ("THIRD_PARTY".equals(overrideAuthority)) vanillaThirdPartyOverrideApplied++;

                    Entry entry = new Entry(
                            id,
                            baseMax,
                            burst,
                            timing.packetSize,
                            timing.finalPacketSeconds,
                            timing.adjustedFullMagazineTargetSeconds,
                            timing.nominalPacketSeconds,
                            timing.baseFireCycleSeconds,
                            timing.safetyFloorSeconds,
                            timing.safetyFloorBinding,
                            timing.baseMagazineRemainder,
                            String.valueOf(spec.getSize()),
                            effectiveOverride != null,
                            effectiveOverride == null ? 0 : effectiveOverride.effectiveFieldCount(),
                            overrideAuthority,
                            overrideSource);
                    built.put(id, entry);

                    if (UMRLogger.verbose()) log.info("[UMR][PHASE5D_REGISTRY_ENTRY] weapon=" + id +
                            " size=" + entry.weaponSize +
                            " baseMax=" + entry.baseMaxAmmo +
                            " baseBurst=" + entry.baseBurstSize +
                            " packet=" + entry.packetSize +
                            " intervalSec=" + f(entry.intervalSeconds) +
                            " fullMagTargetSec=" + f(entry.adjustedFullMagazineTargetSeconds) +
                            " nominalPacketSec=" + f(entry.nominalPacketSeconds) +
                            " baseFireCycleSec=" + f(entry.baseFireCycleSeconds) +
                            " safetyFloorSec=" + f(entry.safetyFloorSeconds) +
                            " safetyBinding=" + entry.safetyFloorBinding +
                            " baseRemainder=" + entry.baseMagazineRemainder +
                            " overrideApplied=" + entry.overrideApplied +
                            " overrideEffectiveFields=" + entry.overrideEffectiveFields +
                            " overrideAuthority=" + entry.overrideAuthority +
                            " overrideSource=" + safeLog(entry.overrideSource) +
                            " effectiveSafetyMultiplier=" + f(timing.effectiveSafetyMultiplier) +
                            " safetyFloorEnabled=" + timing.safetyFloorEnabled +
                            " sourceId=null classification=ELIGIBLE managed=true");
                    if (UMRLogger.verbose()) logLoadReport(spec, provenance, "ELIGIBLE", true,
                            baseMax, burst, nativeAmmoPerSecond,
                            "NONE", "", overrideAuthority, overrideSource, timing);
                } catch (Throwable t) {
                    errors++;
                    log.error("[UMR][PHASE5D_REGISTRY_ENTRY_ERROR] weapon=" + id + " action=skip_weapon", t);
                }
            }
        } catch (Throwable t) {
            errors++;
            log.error("[UMR][PHASE5D_REGISTRY_ERROR] registry initialization failed; action=fail_soft_no_managed_weapons", t);
            built.clear();
        }

        entries = Collections.unmodifiableMap(new LinkedHashMap<String, Entry>(built));
        initialized = true;
        lastVanillaMissileSpecs = vanillaMissileSpecs;
        lastModSourceMissilesSkipped = modSourceMissilesSkipped;
        lastSystemHelper = vanillaSystemHelper;
        lastNativeRegen = vanillaNativeRegen;
        lastInfinite = vanillaInfinite;
        lastRestrictedReview = vanillaRestrictedReview;
        lastMissingTiming = vanillaMissingTiming;
        lastLocalBlacklisted = vanillaBlacklisted;
        lastThirdPartyBlacklisted = thirdPartyBlacklisted;
        int managedVanilla = entries.size() - phase8CohortManaged;
        lastManagedVanilla = managedVanilla;
        lastManagedThirdParty = phase8CohortManaged;
        lastPhase8CohortSeen = phase8CohortSeen;
        lastPhase8CohortPolicySuppressed = phase8CohortPolicySuppressed;
        lastPhase8CohortRejected = phase8CohortRejected;
        lastPhase8CohortMismatch = phase8CohortMismatch;
        lastErrors = errors;

        int phase2EligibleVanillaAccounted = managedVanilla + vanillaBlacklisted + thirdPartyBlacklisted;
        boolean rc8BaselinePass = rc8BaselinePass(
                managedVanilla, vanillaBlacklisted + thirdPartyBlacklisted, vanillaMissileSpecs, errors);
        boolean thirdPartyEnrollmentPass = !UMRConfig.isThirdPartyGameplayEnabled() ||
                (phase8CohortRejected == 0 && phase8CohortMismatch == 0);
        boolean overallPass = rc8BaselinePass && thirdPartyEnrollmentPass;

        log.info("[UMR][PHASE5D_REGISTRY_SUMMARY] totalSpecObjects=" + totalSpecObjects +
                " uniqueWeaponSpecs=" + uniqueWeaponSpecs +
                " missileSpecs=" + missileSpecs +
                " vanillaMissileSpecs=" + vanillaMissileSpecs +
                " modSourceMissilesSkipped=" + modSourceMissilesSkipped +
                " managedEligibleVanilla=" + managedVanilla +
                " managedThirdParty=" + phase8CohortManaged +
                " managedTotal=" + entries.size() +
                " thirdPartyGameplayEnabled=" + UMRConfig.isThirdPartyGameplayEnabled() +
                " sourceAttributedConsidered=" + phase8CohortSeen +
                " sourceAttributedManaged=" + phase8CohortManaged +
                " sourceAttributedPolicySuppressed=" + phase8CohortPolicySuppressed +
                " sourceAttributedHardExcluded=" + modSourceMissilesSkipped +
                " sourceAttributedRejected=" + phase8CohortRejected +
                " localBlacklistedEligibleVanilla=" + vanillaBlacklisted +
                " thirdPartyBlacklistedEligibleVanilla=" + thirdPartyBlacklisted +
                " blacklistedEligibleVanilla=" + (vanillaBlacklisted + thirdPartyBlacklisted) +
                " phase2EligibleVanillaAccounted=" + phase2EligibleVanillaAccounted +
                " blacklistKnownIds=" + UMRBlacklist.size() +
                " blacklistUnknownIds=" + UMRBlacklist.getUnknownIds().size() +
                " overrideKnownRows=" + UMROverrides.size() +
                " overrideEffectiveRows=" + UMROverrides.effectiveSize() +
                " overrideUnknownIds=" + UMROverrides.getUnknownIds().size() +
                " overrideFieldWarnings=" + UMROverrides.getWarnings().size() +
                " localOverrideAppliedManaged=" + vanillaLocalOverrideApplied +
                " thirdPartyOverrideAppliedManaged=" + vanillaThirdPartyOverrideApplied +
                " localOverrideSuppressedByLocalBlacklist=" + localOverrideSuppressedByLocalBlacklist +
                " thirdPartyOverrideSuppressedByLocalBlacklist=" + thirdPartyOverrideSuppressedByLocalBlacklist +
                " thirdPartyBlacklistSuppressedByLocalOverride=" + thirdPartyBlacklistSuppressedByLocalOverride +
                " thirdPartyOverrideSuppressedByLocalOverride=" + thirdPartyOverrideSuppressedByLocalOverride +
                " localOverrideIgnoredHardExclusion=" + localOverrideIgnoredHardExclusion +
                " thirdPartyOverrideIgnoredHardExclusion=" + thirdPartyOverrideIgnoredHardExclusion +
                " thirdPartyBlacklistRows=" + UMRThirdPartyConfig.blacklistSize() +
                " thirdPartyOverrideRows=" + UMRThirdPartyConfig.overrideSize() +
                " thirdPartyBlacklistConflicts=" + UMRThirdPartyConfig.getBlacklistConflicts() +
                " thirdPartyOverrideConflicts=" + UMRThirdPartyConfig.getOverrideConflicts() +
                " excludeSystemHelper=" + vanillaSystemHelper +
                " preserveNativeRegen=" + vanillaNativeRegen +
                " excludeInfiniteUndefined=" + vanillaInfinite +
                " reviewRestrictedSpecial=" + vanillaRestrictedReview +
                " reviewMissingTiming=" + vanillaMissingTiming +
                " duplicateIds=" + duplicateIds +
                " errors=" + errors +
                " expectedRc8VanillaMissiles=58" +
                " expectedRc8EligibleVanilla=" + RC8_EXPECTED_ELIGIBLE_VANILLA +
                " managed=" + join(entries.keySet()) +
                " vanillaBaseline=" + (rc8BaselinePass ? "PASS" : "PARTIAL") +
                " thirdPartyEnrollment=" + (thirdPartyEnrollmentPass ? "PASS" : "PARTIAL") +
                " result=" + (overallPass ? "PASS" : "PARTIAL"));
    }


    private static SourceEnrollmentOutcome registerPhase8SourceWeapon(
            WeaponSpecAPI spec,
            ModSpecAPI source,
            UMRProvenance.SourceInfo provenance,
            Map<String, JSONObject> rows,
            Map<String, Entry> built,
            Logger log) {

        String id = safeId(spec);
        JSONObject row = rows.get(id);
        boolean csvPresent = row != null;
        boolean systemHint = spec.getAIHints() != null &&
                spec.getAIHints().contains(WeaponAPI.AIHints.SYSTEM);
        boolean rowSystemHint = tokenContains(rowString(row, "hints"), "SYSTEM");
        boolean systemOrHelper = systemHint || rowSystemHint;
        boolean restricted = spec.hasTag("restricted") ||
                tokenContains(rowString(row, "tags"), "restricted");
        boolean noStandardData = spec.hasTag("no_standard_data") ||
                tokenContains(rowString(row, "tags"), "no_standard_data");
        int baseMax = spec.getMaxAmmo();
        int burst = Math.max(1, spec.getBurstSize());
        double nativeAmmoPerSecond = spec.getAmmoPerSecond();
        boolean usesAmmo = spec.usesAmmo();

        String classification = UMRRuleEngine.classify(
                systemOrHelper,
                usesAmmo,
                baseMax,
                nativeAmmoPerSecond,
                restricted || noStandardData,
                csvPresent);

        if (!UMRRuleEngine.ELIGIBLE.equals(classification)) {
            if (UMRLogger.verbose()) {
                log.info("[UMR][PHASE8_THIRD_PARTY_EXCLUDED] weapon=" + id +
                        " sourceMod=" + safeLog(source == null ? "" : source.getId()) +
                        " sourceVersion=" + safeLog(source == null ? "" : source.getVersion()) +
                        " classification=" + classification +
                        " managed=false action=preserve_authored_behavior");
                logLoadReport(spec, provenance, classification, false,
                        baseMax, burst, nativeAmmoPerSecond,
                        "NONE", "", "NONE", "", null);
            }
            return SourceEnrollmentOutcome.HARD_EXCLUDED;
        }

        UMROverrides.Entry localOverrideEntry = UMROverrides.get(id);
        boolean hasLocalOverride = localOverrideEntry != null && localOverrideEntry.effectiveFieldCount() > 0;
        UMRThirdPartyConfig.BlacklistEntry thirdPartyBlacklistEntry = UMRThirdPartyConfig.getBlacklist(id);
        UMRThirdPartyConfig.OverrideEntry thirdPartyOverrideContainer = UMRThirdPartyConfig.getOverride(id);
        UMROverrides.Entry thirdPartyOverrideEntry = thirdPartyOverrideContainer == null ? null : thirdPartyOverrideContainer.entry;
        boolean hasThirdPartyOverride = thirdPartyOverrideEntry != null && thirdPartyOverrideEntry.effectiveFieldCount() > 0;

        if (UMRBlacklist.isBlacklisted(id)) {
            UMRBlacklist.Entry blacklistEntry = UMRBlacklist.get(id);
            log.info("[UMR][PHASE8_THIRD_PARTY_LOCAL_BLACKLIST] weapon=" + id +
                    " sourceMod=" + safeLog(source == null ? "" : source.getId()) +
                    " reason=" + safeLog(blacklistEntry == null ? "" : blacklistEntry.reason) +
                    " action=do_not_enroll");
            if (UMRLogger.verbose()) logLoadReport(spec, provenance, "ELIGIBLE", false,
                    baseMax, burst, nativeAmmoPerSecond,
                    "LOCAL", UMRBlacklist.MOD_ID, "NONE", "", null);
            return SourceEnrollmentOutcome.POLICY_SUPPRESSED;
        }

        UMROverrides.Entry effectiveOverride = null;
        String overrideAuthority = "NONE";
        String overrideSource = "";
        if (hasLocalOverride) {
            effectiveOverride = localOverrideEntry;
            overrideAuthority = "LOCAL";
            overrideSource = UMRBlacklist.MOD_ID;
        } else if (thirdPartyBlacklistEntry != null) {
            log.info("[UMR][PHASE8_THIRD_PARTY_BLACKLIST] weapon=" + id +
                    " sourceMod=" + safeLog(source == null ? "" : source.getId()) +
                    " contributionSource=" + safeLog(thirdPartyBlacklistEntry.sourceModId) +
                    " rowId=" + safeLog(thirdPartyBlacklistEntry.rowId) +
                    " action=do_not_enroll");
            if (UMRLogger.verbose()) logLoadReport(spec, provenance, "ELIGIBLE", false,
                    baseMax, burst, nativeAmmoPerSecond,
                    "THIRD_PARTY", thirdPartyBlacklistEntry.sourceModId, "NONE", "", null);
            return SourceEnrollmentOutcome.POLICY_SUPPRESSED;
        } else if (hasThirdPartyOverride) {
            effectiveOverride = thirdPartyOverrideEntry;
            overrideAuthority = "THIRD_PARTY";
            overrideSource = thirdPartyOverrideContainer.sourceModId;
        }

        double chargedown = valueOrZero(rowDouble(row, "chargedown"));
        double burstDelay = valueOrZero(rowDouble(row, "burst delay"));
        UMRRuleEngine.Timing timing = UMRRuleEngine.computeTiming(
                baseMax,
                burst,
                String.valueOf(spec.getSize()),
                chargedown,
                burstDelay,
                UMRConfig.getTimingSettings(),
                effectiveOverride == null ? null : effectiveOverride.toTimingOverride());

        Entry entry = new Entry(
                id,
                baseMax,
                burst,
                timing.packetSize,
                timing.finalPacketSeconds,
                timing.adjustedFullMagazineTargetSeconds,
                timing.nominalPacketSeconds,
                timing.baseFireCycleSeconds,
                timing.safetyFloorSeconds,
                timing.safetyFloorBinding,
                timing.baseMagazineRemainder,
                String.valueOf(spec.getSize()),
                effectiveOverride != null,
                effectiveOverride == null ? 0 : effectiveOverride.effectiveFieldCount(),
                overrideAuthority,
                overrideSource);
        built.put(id, entry);

        log.info("[UMR][PHASE8_THIRD_PARTY_REGISTRY_ENTRY] weapon=" + id +
                " sourceMod=" + safeLog(source == null ? "" : source.getId()) +
                " sourceVersion=" + safeLog(source == null ? "" : source.getVersion()) +
                " size=" + entry.weaponSize +
                " baseMax=" + entry.baseMaxAmmo +
                " baseBurst=" + entry.baseBurstSize +
                " packet=" + entry.packetSize +
                " intervalSec=" + f(entry.intervalSeconds) +
                " fullMagTargetSec=" + f(entry.adjustedFullMagazineTargetSeconds) +
                " safetyBinding=" + entry.safetyFloorBinding +
                " baseRemainder=" + entry.baseMagazineRemainder +
                " overrideAuthority=" + entry.overrideAuthority +
                " overrideSource=" + safeLog(entry.overrideSource) +
                " classification=ELIGIBLE managed=true result=PASS");

        if (UMRLogger.verbose()) logLoadReport(spec, provenance, "ELIGIBLE", true,
                baseMax, burst, nativeAmmoPerSecond,
                "NONE", "", overrideAuthority, overrideSource, timing);
        return SourceEnrollmentOutcome.MANAGED;
    }

    private enum SourceEnrollmentOutcome {
        MANAGED,
        POLICY_SUPPRESSED,
        HARD_EXCLUDED,
        REJECTED
    }

    public static Entry get(String weaponId) {
        return weaponId == null ? null : entries.get(weaponId);
    }

    public static Set<String> getManagedIds() {
        return entries.keySet();
    }

    public static int size() {
        return entries.size();
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static String diagnosticSummary() {
        return "vanillaMissileSpecs=" + lastVanillaMissileSpecs +
                " modSourceMissilesSkipped=" + lastModSourceMissilesSkipped +
                " managedVanilla=" + lastManagedVanilla +
                " managedThirdParty=" + lastManagedThirdParty +
                " managedTotal=" + entries.size() +
                " phase8CohortSeen=" + lastPhase8CohortSeen +
                " phase8CohortPolicySuppressed=" + lastPhase8CohortPolicySuppressed +
                " phase8CohortRejected=" + lastPhase8CohortRejected +
                " sourceAttributedRejected=" + lastPhase8CohortRejected +
                " thirdPartyGameplayEnabled=" + UMRConfig.isThirdPartyGameplayEnabled() +
                " localBlacklistedEligible=" + lastLocalBlacklisted +
                " thirdPartyBlacklistedEligible=" + lastThirdPartyBlacklisted +
                " excludeSystemHelper=" + lastSystemHelper +
                " preserveNativeRegen=" + lastNativeRegen +
                " excludeInfiniteUndefined=" + lastInfinite +
                " reviewRestrictedSpecial=" + lastRestrictedReview +
                " reviewMissingTiming=" + lastMissingTiming +
                " errors=" + lastErrors;
    }

    static boolean rc8BaselinePass(int managedEligibleVanilla,
                                   int blacklistedEligibleVanilla,
                                   int vanillaMissileSpecs,
                                   int errors) {
        return managedEligibleVanilla + blacklistedEligibleVanilla == RC8_EXPECTED_ELIGIBLE_VANILLA &&
                vanillaMissileSpecs == 58 &&
                errors == 0;
    }

    private static void logLoadReport(WeaponSpecAPI spec,
                                      UMRProvenance.SourceInfo source,
                                      String classification,
                                      boolean managed,
                                      int baseMax,
                                      int burst,
                                      double nativeAmmoPerSecond,
                                      String blacklistAuthority,
                                      String blacklistSource,
                                      String overrideAuthority,
                                      String overrideSource,
                                      UMRRuleEngine.Timing timing) {
        UMRLogger.get().info("[UMR][PHASE6_LOAD_REPORT]" +
                " weapon=" + safeLog(spec == null ? "" : spec.getWeaponId()) +
                " name=" + safeLog(spec == null ? "" : spec.getWeaponName()) +
                source.logFields("weapon") +
                " classification=" + safeLog(classification) +
                " managed=" + managed +
                " usesAmmo=" + (spec != null && spec.usesAmmo()) +
                " baseAmmo=" + baseMax +
                " nativeAmmoPerSecond=" + f(nativeAmmoPerSecond) +
                " burst=" + burst +
                " size=" + safeLog(spec == null ? "" : String.valueOf(spec.getSize())) +
                " packet=" + (timing == null ? "" : String.valueOf(timing.packetSize)) +
                " adjustedFullMagSec=" + (timing == null ? "" : f(timing.adjustedFullMagazineTargetSeconds)) +
                " nominalPacketSec=" + (timing == null ? "" : f(timing.nominalPacketSeconds)) +
                " baseFireCycleSec=" + (timing == null ? "" : f(timing.baseFireCycleSeconds)) +
                " safetyFloorSec=" + (timing == null ? "" : f(timing.safetyFloorSeconds)) +
                " finalIntervalSec=" + (timing == null ? "" : f(timing.finalPacketSeconds)) +
                " safetyBinding=" + (timing == null ? "" : String.valueOf(timing.safetyFloorBinding)) +
                " baseMagazineRemainder=" + (timing == null ? "" : String.valueOf(timing.baseMagazineRemainder)) +
                " remainderWarning=" + (timing != null && timing.baseMagazineRemainder > 0) +
                " blacklistAuthority=" + safeLog(blacklistAuthority) +
                " blacklistSource=" + safeLog(blacklistSource) +
                " overrideAuthority=" + safeLog(overrideAuthority) +
                " overrideSource=" + safeLog(overrideSource));
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
        for (String p : parts) {
            if (needle.equalsIgnoreCase(p.trim())) return true;
        }
        return false;
    }

    private static String rowString(JSONObject row, String key) {
        return row == null ? "" : row.optString(key, "");
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
        if (spec == null || spec.getWeaponId() == null) return "";
        return spec.getWeaponId().trim();
    }

    private static String join(Iterable<String> values) {
        StringBuilder b = new StringBuilder();
        for (String v : values) {
            if (b.length() > 0) b.append(',');
            b.append(v);
        }
        return b.toString();
    }

    private static String safeLog(String text) {
        if (text == null) return "";
        return text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    public static final class Entry {
        public final String weaponId;
        public final int baseMaxAmmo;
        public final int baseBurstSize;
        public final int packetSize;
        public final double intervalSeconds;
        public final double adjustedFullMagazineTargetSeconds;
        public final double nominalPacketSeconds;
        public final double baseFireCycleSeconds;
        public final double safetyFloorSeconds;
        public final boolean safetyFloorBinding;
        public final int baseMagazineRemainder;
        public final String weaponSize;
        public final boolean overrideApplied;
        public final int overrideEffectiveFields;
        public final String overrideAuthority;
        public final String overrideSource;

        Entry(String weaponId,
              int baseMaxAmmo,
              int baseBurstSize,
              int packetSize,
              double intervalSeconds,
              double adjustedFullMagazineTargetSeconds,
              double nominalPacketSeconds,
              double baseFireCycleSeconds,
              double safetyFloorSeconds,
              boolean safetyFloorBinding,
              int baseMagazineRemainder,
              String weaponSize,
              boolean overrideApplied,
              int overrideEffectiveFields,
              String overrideAuthority,
              String overrideSource) {
            this.weaponId = weaponId;
            this.baseMaxAmmo = baseMaxAmmo;
            this.baseBurstSize = baseBurstSize;
            this.packetSize = packetSize;
            this.intervalSeconds = intervalSeconds;
            this.adjustedFullMagazineTargetSeconds = adjustedFullMagazineTargetSeconds;
            this.nominalPacketSeconds = nominalPacketSeconds;
            this.baseFireCycleSeconds = baseFireCycleSeconds;
            this.safetyFloorSeconds = safetyFloorSeconds;
            this.safetyFloorBinding = safetyFloorBinding;
            this.baseMagazineRemainder = baseMagazineRemainder;
            this.weaponSize = weaponSize;
            this.overrideApplied = overrideApplied;
            this.overrideEffectiveFields = overrideEffectiveFields;
            this.overrideAuthority = overrideAuthority == null ? "NONE" : overrideAuthority;
            this.overrideSource = overrideSource == null ? "" : overrideSource;
        }
    }
}
