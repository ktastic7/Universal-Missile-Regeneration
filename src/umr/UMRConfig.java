package umr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.apache.log4j.Logger;
import org.json.JSONObject;

/**
 * Phase 5A cached global configuration loader.
 *
 * Values are read once during application load from the merged settings JSON.
 * Invalid values fail soft to safe defaults (or to a safe relational fallback)
 * and are reported after the dedicated logger is initialized.
 */
public final class UMRConfig {
    public static final String KEY_BASE_FULL_MAG_SECONDS = "umrBaseFullMagTargetSeconds";
    public static final String KEY_SINGLE_AMMO_REDUCTION_SECONDS = "umrSingleAmmoReductionSeconds";
    public static final String KEY_MEDIUM_ADDITION_SECONDS = "umrMediumAdditionSeconds";
    public static final String KEY_LARGE_ADDITION_SECONDS = "umrLargeAdditionSeconds";
    public static final String KEY_SAFETY_FLOOR_ENABLED = "umrSafetyFloorEnabled";
    public static final String KEY_SAFETY_MULTIPLIER = "umrSafetyMultiplier";
    public static final String KEY_LOGGING_LEVEL = "umrLoggingLevel";
    public static final String KEY_NON_MISSILE_PLACEHOLDER = "umrEnableNonMissileLimitedAmmo";
    public static final String KEY_THIRD_PARTY_GAMEPLAY_ENABLED = "umrThirdPartyGameplayEnabled";
    public static final String KEY_PERFORMANCE_TELEMETRY_ENABLED = "umrPerformanceTelemetryEnabled";
    public static final String KEY_AI_TELEMETRY_ENABLED = "umrAiTelemetryEnabled";

    public static final String LOG_OFF = "OFF";
    public static final String LOG_SUMMARY = "SUMMARY";
    public static final String LOG_VERBOSE = "VERBOSE";
    public static final String LOG_TRACE = "TRACE";

    private static UMRRuleEngine.TimingSettings timing = UMRRuleEngine.TimingSettings.defaults();
    private static String loggingLevel = LOG_TRACE;
    private static boolean nonMissilePlaceholder = false;
    private static boolean thirdPartyGameplayEnabled = UMRDefaults.THIRD_PARTY_GAMEPLAY_ENABLED;
    private static boolean performanceTelemetryEnabled = UMRDefaults.PERFORMANCE_TELEMETRY_ENABLED;
    private static boolean aiTelemetryEnabled = UMRDefaults.AI_TELEMETRY_ENABLED;
    private static List<String> warnings = Collections.emptyList();
    private static boolean initialized = false;

    private UMRConfig() {}

    public static synchronized void initialize() {
        List<String> issues = new ArrayList<String>();
        double base = UMRDefaults.BASE_FULL_MAG_SECONDS;
        double singleReduction = UMRDefaults.SINGLE_AMMO_REDUCTION_SECONDS;
        double mediumAddition = UMRDefaults.MEDIUM_ADDITION_SECONDS;
        double largeAddition = UMRDefaults.LARGE_ADDITION_SECONDS;
        boolean safetyEnabled = UMRDefaults.SAFETY_FLOOR_ENABLED;
        double safetyMultiplier = UMRDefaults.SAFETY_MULTIPLIER;
        String level = UMRDefaults.LOGGING_LEVEL;
        boolean nonMissile = UMRDefaults.NON_MISSILE_PLACEHOLDER_ENABLED;
        boolean thirdPartyGameplay = UMRDefaults.THIRD_PARTY_GAMEPLAY_ENABLED;
        boolean performanceTelemetry = UMRDefaults.PERFORMANCE_TELEMETRY_ENABLED;
        boolean aiTelemetry = UMRDefaults.AI_TELEMETRY_ENABLED;

        try {
            SettingsAPI settings = Global.getSettings();
            JSONObject json = settings.getSettingsJSON();

            base = validPositiveDouble(json, KEY_BASE_FULL_MAG_SECONDS,
                    UMRDefaults.BASE_FULL_MAG_SECONDS, issues);
            singleReduction = validNonNegativeDouble(json, KEY_SINGLE_AMMO_REDUCTION_SECONDS,
                    UMRDefaults.SINGLE_AMMO_REDUCTION_SECONDS, issues);
            mediumAddition = validNonNegativeDouble(json, KEY_MEDIUM_ADDITION_SECONDS,
                    UMRDefaults.MEDIUM_ADDITION_SECONDS, issues);
            largeAddition = validNonNegativeDouble(json, KEY_LARGE_ADDITION_SECONDS,
                    UMRDefaults.LARGE_ADDITION_SECONDS, issues);
            safetyEnabled = validBoolean(json, KEY_SAFETY_FLOOR_ENABLED,
                    UMRDefaults.SAFETY_FLOOR_ENABLED, issues);
            safetyMultiplier = validPositiveDouble(json, KEY_SAFETY_MULTIPLIER,
                    UMRDefaults.SAFETY_MULTIPLIER, issues);
            level = validLoggingLevel(json, KEY_LOGGING_LEVEL,
                    UMRDefaults.LOGGING_LEVEL, issues);
            nonMissile = validBoolean(json, KEY_NON_MISSILE_PLACEHOLDER,
                    UMRDefaults.NON_MISSILE_PLACEHOLDER_ENABLED, issues);
            thirdPartyGameplay = validBoolean(json, KEY_THIRD_PARTY_GAMEPLAY_ENABLED,
                    UMRDefaults.THIRD_PARTY_GAMEPLAY_ENABLED, issues);
            performanceTelemetry = validBoolean(json, KEY_PERFORMANCE_TELEMETRY_ENABLED,
                    UMRDefaults.PERFORMANCE_TELEMETRY_ENABLED, issues);
            aiTelemetry = validBoolean(json, KEY_AI_TELEMETRY_ENABLED,
                    UMRDefaults.AI_TELEMETRY_ENABLED, issues);

            // The one-ammo target must remain strictly positive. Rather than impose
            // an arbitrary balance maximum on either field, disable only the
            // reduction when their combination becomes invalid.
            if (base - singleReduction <= 0.0) {
                issues.add(KEY_SINGLE_AMMO_REDUCTION_SECONDS + "=" + singleReduction +
                        " would make the one-ammo target non-positive with " +
                        KEY_BASE_FULL_MAG_SECONDS + "=" + base + "; using 0.0");
                singleReduction = 0.0;
            }
        } catch (Throwable t) {
            issues.add("settings read failed (" + t.getClass().getSimpleName() +
                    "); using all Phase 5A defaults");
            base = UMRDefaults.BASE_FULL_MAG_SECONDS;
            singleReduction = UMRDefaults.SINGLE_AMMO_REDUCTION_SECONDS;
            mediumAddition = UMRDefaults.MEDIUM_ADDITION_SECONDS;
            largeAddition = UMRDefaults.LARGE_ADDITION_SECONDS;
            safetyEnabled = UMRDefaults.SAFETY_FLOOR_ENABLED;
            safetyMultiplier = UMRDefaults.SAFETY_MULTIPLIER;
            level = UMRDefaults.LOGGING_LEVEL;
            nonMissile = UMRDefaults.NON_MISSILE_PLACEHOLDER_ENABLED;
            thirdPartyGameplay = UMRDefaults.THIRD_PARTY_GAMEPLAY_ENABLED;
            performanceTelemetry = UMRDefaults.PERFORMANCE_TELEMETRY_ENABLED;
            aiTelemetry = UMRDefaults.AI_TELEMETRY_ENABLED;
        }

        timing = new UMRRuleEngine.TimingSettings(
                base,
                singleReduction,
                mediumAddition,
                largeAddition,
                safetyEnabled,
                safetyMultiplier);
        loggingLevel = level;
        nonMissilePlaceholder = nonMissile;
        thirdPartyGameplayEnabled = thirdPartyGameplay;
        performanceTelemetryEnabled = performanceTelemetry;
        aiTelemetryEnabled = aiTelemetry;
        warnings = Collections.unmodifiableList(new ArrayList<String>(issues));
        initialized = true;
    }

    public static UMRRuleEngine.TimingSettings getTimingSettings() {
        return timing;
    }

    public static String getLoggingLevel() {
        return loggingLevel;
    }

    public static boolean isNonMissilePlaceholderEnabled() {
        return nonMissilePlaceholder;
    }

    public static boolean isThirdPartyGameplayEnabled() {
        return thirdPartyGameplayEnabled;
    }

    public static boolean isPerformanceTelemetryEnabled() {
        return performanceTelemetryEnabled;
    }

    public static boolean isAiTelemetryEnabled() {
        return aiTelemetryEnabled;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static boolean isSummaryEnabled() {
        return !LOG_OFF.equals(loggingLevel);
    }

    public static boolean isVerboseEnabled() {
        return LOG_VERBOSE.equals(loggingLevel) || LOG_TRACE.equals(loggingLevel);
    }

    public static boolean isTraceEnabled() {
        return LOG_TRACE.equals(loggingLevel);
    }

    public static void logLoadedConfiguration() {
        Logger log = UMRLogger.get();
        UMRRuleEngine.TimingSettings t = timing;
        log.info("[UMR][CONFIG_SUMMARY]" +
                " baseFullMagTargetSec=" + f(t.baseFullMagazineSeconds) +
                " singleAmmoReductionSec=" + f(t.singleAmmoReductionSeconds) +
                " mediumAdditionSec=" + f(t.mediumAdditionSeconds) +
                " largeAdditionSec=" + f(t.largeAdditionSeconds) +
                " safetyFloorEnabled=" + t.safetyFloorEnabled +
                " safetyMultiplier=" + f(t.safetyMultiplier) +
                " loggingLevel=" + loggingLevel +
                " nonMissilePlaceholder=" + nonMissilePlaceholder +
                " thirdPartyGameplayEnabled=" + thirdPartyGameplayEnabled +
                " performanceTelemetryEnabled=" + performanceTelemetryEnabled +
                " aiTelemetryEnabled=" + aiTelemetryEnabled +
                " warningCount=" + warnings.size());
        for (String warning : warnings) {
            log.warn("[UMR][CONFIG_WARNING] " + warning);
        }
    }

    private static double validPositiveDouble(JSONObject json, String key,
                                              double fallback, List<String> issues) {
        Double value = readDouble(json, key);
        if (value == null || !finite(value.doubleValue()) || value.doubleValue() <= 0.0) {
            issues.add(key + " invalid; using default " + fallback);
            return fallback;
        }
        return value.doubleValue();
    }

    private static double validNonNegativeDouble(JSONObject json, String key,
                                                  double fallback, List<String> issues) {
        Double value = readDouble(json, key);
        if (value == null || !finite(value.doubleValue()) || value.doubleValue() < 0.0) {
            issues.add(key + " invalid; using default " + fallback);
            return fallback;
        }
        return value.doubleValue();
    }

    private static boolean validBoolean(JSONObject json, String key,
                                        boolean fallback, List<String> issues) {
        Object raw = json == null ? null : json.opt(key);
        if (raw instanceof Boolean) return ((Boolean) raw).booleanValue();
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if ("true".equalsIgnoreCase(s)) return true;
            if ("false".equalsIgnoreCase(s)) return false;
        }
        issues.add(key + " invalid; using default " + fallback);
        return fallback;
    }

    private static String validLoggingLevel(JSONObject json, String key,
                                            String fallback, List<String> issues) {
        Object raw = json == null ? null : json.opt(key);
        String value = raw == null ? "" : String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        if (LOG_OFF.equals(value) || LOG_SUMMARY.equals(value) ||
                LOG_VERBOSE.equals(value) || LOG_TRACE.equals(value)) {
            return value;
        }
        issues.add(key + " invalid; using default " + fallback);
        return fallback;
    }

    private static Double readDouble(JSONObject json, String key) {
        if (json == null) return null;
        Object raw = json.opt(key);
        if (raw instanceof Number) return Double.valueOf(((Number) raw).doubleValue());
        if (raw instanceof String) {
            try { return Double.valueOf(((String) raw).trim()); }
            catch (Throwable ignored) { return null; }
        }
        return null;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }
}
