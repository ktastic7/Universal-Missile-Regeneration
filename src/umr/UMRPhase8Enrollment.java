package umr;

import com.fs.starfarer.api.ModSpecAPI;

/**
 * Broad Phase 8 source-attributed gameplay gate.
 *
 * This class no longer contains a stack-specific cohort. Source-attributed
 * weapons may reach the normal classifier whenever third-party gameplay is
 * enabled. Hard/native/system/special safety policy remains in the production
 * registry and rule engine.
 */
public final class UMRPhase8Enrollment {
    private UMRPhase8Enrollment() {}

    public static Decision decide(boolean thirdPartyGameplayEnabled, ModSpecAPI source) {
        if (!thirdPartyGameplayEnabled) return Decision.DISABLED;
        if (source == null) return Decision.SOURCE_MISSING;
        return Decision.ENROLL;
    }

    public enum Decision {
        ENROLL(true),
        DISABLED(false),
        SOURCE_MISSING(false);

        public final boolean enroll;
        Decision(boolean enroll) { this.enroll = enroll; }
    }
}
