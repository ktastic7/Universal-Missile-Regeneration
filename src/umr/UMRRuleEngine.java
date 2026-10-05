package umr;

/** Pure scalar classification/timing rules. */
public final class UMRRuleEngine {
    public static final String ELIGIBLE = "ELIGIBLE";
    public static final String EXCLUDE_SYSTEM_HELPER = "EXCLUDE_SYSTEM_HELPER";
    public static final String EXCLUDE_INFINITE_OR_UNDEFINED = "EXCLUDE_INFINITE_OR_UNDEFINED_AMMO";
    public static final String PRESERVE_NATIVE_REGEN = "PRESERVE_NATIVE_REGEN";
    public static final String REVIEW_RESTRICTED_SPECIAL = "REVIEW_RESTRICTED_SPECIAL";
    public static final String REVIEW_MISSING_TIMING_DATA = "REVIEW_MISSING_TIMING_DATA";

    private UMRRuleEngine() {}

    public static String classify(boolean systemOrHelper,
                                  boolean usesAmmo,
                                  int baseMaxAmmo,
                                  double nativeAmmoPerSecond,
                                  boolean restrictedOrNoStandardData,
                                  boolean timingRowPresent) {
        if (systemOrHelper) return EXCLUDE_SYSTEM_HELPER;
        if (!usesAmmo || baseMaxAmmo <= 0 || baseMaxAmmo == Integer.MAX_VALUE) {
            return EXCLUDE_INFINITE_OR_UNDEFINED;
        }
        if (nativeAmmoPerSecond > 0.0) return PRESERVE_NATIVE_REGEN;
        if (restrictedOrNoStandardData) return REVIEW_RESTRICTED_SPECIAL;
        if (!timingRowPresent) return REVIEW_MISSING_TIMING_DATA;
        return ELIGIBLE;
    }

    public static Timing computeTiming(int baseMaxAmmo,
                                       int rawBurstSize,
                                       String weaponSize,
                                       double chargedownOrCooldown,
                                       double burstDelay) {
        return computeTiming(baseMaxAmmo, rawBurstSize, weaponSize,
                chargedownOrCooldown, burstDelay, TimingSettings.defaults(), null);
    }

    public static Timing computeTiming(int baseMaxAmmo,
                                       int rawBurstSize,
                                       String weaponSize,
                                       double chargedownOrCooldown,
                                       double burstDelay,
                                       TimingSettings settings) {
        return computeTiming(baseMaxAmmo, rawBurstSize, weaponSize,
                chargedownOrCooldown, burstDelay, settings, null);
    }

    /** Phase 5C calculation with local per-weapon override precedence. */
    public static Timing computeTiming(int baseMaxAmmo,
                                       int rawBurstSize,
                                       String weaponSize,
                                       double chargedownOrCooldown,
                                       double burstDelay,
                                       TimingSettings settings,
                                       TimingOverride override) {
        TimingSettings cfg = settings == null ? TimingSettings.defaults() : settings;
        int baseBurst = Math.max(1, rawBurstSize);
        int packet = override != null && override.packetSizeOverride != null
                ? Math.max(1, override.packetSizeOverride.intValue()) : baseBurst;
        int packets = Math.max(1, (int) Math.ceil((double) baseMaxAmmo / (double) packet));

        double magTarget = cfg.baseFullMagazineSeconds;
        if (baseMaxAmmo == 1) magTarget -= cfg.singleAmmoReductionSeconds;
        if ("MEDIUM".equals(weaponSize)) magTarget += cfg.mediumAdditionSeconds;
        else if ("LARGE".equals(weaponSize)) magTarget += cfg.largeAdditionSeconds;
        if (override != null && override.fullMagazineTargetSecondsOverride != null) {
            magTarget = override.fullMagazineTargetSecondsOverride.doubleValue();
        }
        if (!finitePositive(magTarget)) magTarget = 0.001;

        double nominalPacket = magTarget / packets;
        if (override != null && override.packetRegenSecondsOverride != null) {
            nominalPacket = override.packetRegenSecondsOverride.doubleValue();
        }
        if (!finitePositive(nominalPacket)) nominalPacket = 0.001;

        // Safety is tied to the weapon's real firing burst, not an artificial
        // refill-packet override.
        double baseCycle = Math.max(0.0, chargedownOrCooldown) +
                (baseBurst - 1) * Math.max(0.0, burstDelay);
        double safetyMultiplier = cfg.safetyMultiplier;
        if (override != null && override.safetyMultiplierOverride != null) {
            safetyMultiplier = override.safetyMultiplierOverride.doubleValue();
        }
        if (!finitePositive(safetyMultiplier)) safetyMultiplier = UMRDefaults.SAFETY_MULTIPLIER;

        boolean safetyEnabled = cfg.safetyFloorEnabled;
        if (override != null && Boolean.TRUE.equals(override.disableSafetyFloor)) {
            safetyEnabled = false;
        }
        double safety = baseCycle * safetyMultiplier;
        boolean safetyBinding = safetyEnabled && safety > nominalPacket;
        double finalPacket = safetyBinding ? safety : nominalPacket;
        double actualFullMag = finalPacket * packets;
        int remainder = baseMaxAmmo % packet;
        return new Timing(packet, packets, magTarget, nominalPacket, baseCycle, safety,
                finalPacket, actualFullMag, safetyBinding, remainder,
                safetyEnabled, safetyMultiplier, override != null && override.hasAny());
    }

    private static boolean finitePositive(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0;
    }

    public static final class TimingSettings {
        public final double baseFullMagazineSeconds;
        public final double singleAmmoReductionSeconds;
        public final double mediumAdditionSeconds;
        public final double largeAdditionSeconds;
        public final boolean safetyFloorEnabled;
        public final double safetyMultiplier;

        public TimingSettings(double baseFullMagazineSeconds,
                              double singleAmmoReductionSeconds,
                              double mediumAdditionSeconds,
                              double largeAdditionSeconds,
                              boolean safetyFloorEnabled,
                              double safetyMultiplier) {
            this.baseFullMagazineSeconds = baseFullMagazineSeconds;
            this.singleAmmoReductionSeconds = singleAmmoReductionSeconds;
            this.mediumAdditionSeconds = mediumAdditionSeconds;
            this.largeAdditionSeconds = largeAdditionSeconds;
            this.safetyFloorEnabled = safetyFloorEnabled;
            this.safetyMultiplier = safetyMultiplier;
        }

        public static TimingSettings defaults() {
            return new TimingSettings(
                    UMRDefaults.BASE_FULL_MAG_SECONDS,
                    UMRDefaults.SINGLE_AMMO_REDUCTION_SECONDS,
                    UMRDefaults.MEDIUM_ADDITION_SECONDS,
                    UMRDefaults.LARGE_ADDITION_SECONDS,
                    UMRDefaults.SAFETY_FLOOR_ENABLED,
                    UMRDefaults.SAFETY_MULTIPLIER);
        }
    }

    public static final class TimingOverride {
        public final Integer packetSizeOverride;
        public final Double fullMagazineTargetSecondsOverride;
        public final Double packetRegenSecondsOverride;
        public final Double safetyMultiplierOverride;
        public final Boolean disableSafetyFloor;

        public TimingOverride(Integer packetSizeOverride,
                              Double fullMagazineTargetSecondsOverride,
                              Double packetRegenSecondsOverride,
                              Double safetyMultiplierOverride,
                              Boolean disableSafetyFloor) {
            this.packetSizeOverride = packetSizeOverride;
            this.fullMagazineTargetSecondsOverride = fullMagazineTargetSecondsOverride;
            this.packetRegenSecondsOverride = packetRegenSecondsOverride;
            this.safetyMultiplierOverride = safetyMultiplierOverride;
            this.disableSafetyFloor = disableSafetyFloor;
        }

        public boolean hasAny() {
            return packetSizeOverride != null || fullMagazineTargetSecondsOverride != null ||
                    packetRegenSecondsOverride != null || safetyMultiplierOverride != null ||
                    disableSafetyFloor != null;
        }
    }

    public static final class Timing {
        public final int packetSize;
        public final int packetsPerBaseMagazine;
        public final double adjustedFullMagazineTargetSeconds;
        public final double nominalPacketSeconds;
        public final double baseFireCycleSeconds;
        public final double safetyFloorSeconds;
        public final double finalPacketSeconds;
        public final double actualFullMagazineSeconds;
        public final boolean safetyFloorBinding;
        public final int baseMagazineRemainder;
        public final boolean safetyFloorEnabled;
        public final double effectiveSafetyMultiplier;
        public final boolean overrideApplied;

        Timing(int packetSize, int packetsPerBaseMagazine,
               double adjustedFullMagazineTargetSeconds, double nominalPacketSeconds,
               double baseFireCycleSeconds, double safetyFloorSeconds,
               double finalPacketSeconds, double actualFullMagazineSeconds,
               boolean safetyFloorBinding, int baseMagazineRemainder,
               boolean safetyFloorEnabled, double effectiveSafetyMultiplier,
               boolean overrideApplied) {
            this.packetSize = packetSize;
            this.packetsPerBaseMagazine = packetsPerBaseMagazine;
            this.adjustedFullMagazineTargetSeconds = adjustedFullMagazineTargetSeconds;
            this.nominalPacketSeconds = nominalPacketSeconds;
            this.baseFireCycleSeconds = baseFireCycleSeconds;
            this.safetyFloorSeconds = safetyFloorSeconds;
            this.finalPacketSeconds = finalPacketSeconds;
            this.actualFullMagazineSeconds = actualFullMagazineSeconds;
            this.safetyFloorBinding = safetyFloorBinding;
            this.baseMagazineRemainder = baseMagazineRemainder;
            this.safetyFloorEnabled = safetyFloorEnabled;
            this.effectiveSafetyMultiplier = effectiveSafetyMultiplier;
            this.overrideApplied = overrideApplied;
        }
    }
}
