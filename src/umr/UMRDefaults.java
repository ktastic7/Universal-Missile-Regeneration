package umr;

/** Validated production defaults and Phase 5 configuration fallbacks. */
public final class UMRDefaults {
    public static final double BASE_FULL_MAG_SECONDS = 90.0;
    public static final double SINGLE_AMMO_REDUCTION_SECONDS = 20.0;
    public static final double MEDIUM_ADDITION_SECONDS = 25.0;
    public static final double LARGE_ADDITION_SECONDS = 45.0;
    public static final boolean SAFETY_FLOOR_ENABLED = true;
    public static final double SAFETY_MULTIPLIER = 1.4;
    public static final String LOGGING_LEVEL = "SUMMARY";
    public static final boolean NON_MISSILE_PLACEHOLDER_ENABLED = false;
    public static final boolean THIRD_PARTY_GAMEPLAY_ENABLED = true;
    public static final boolean PERFORMANCE_TELEMETRY_ENABLED = true;
    public static final boolean AI_TELEMETRY_ENABLED = true;

    private UMRDefaults() {}
}
