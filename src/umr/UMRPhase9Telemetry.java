package umr;

/** Pure helpers/state for Phase 9 AI-use and performance telemetry. */
public final class UMRPhase9Telemetry {
    public static final double FRAME_BUDGET_MICROS_60FPS = 1000000.0 / 60.0;

    private UMRPhase9Telemetry() {}

    public static double averageMicros(long totalNanos, long measuredFrames) {
        if (totalNanos <= 0L || measuredFrames <= 0L) return 0.0;
        return ((double) totalNanos / 1000.0) / (double) measuredFrames;
    }

    public static double nanosToMicros(long nanos) {
        if (nanos <= 0L) return 0.0;
        return (double) nanos / 1000.0;
    }

    public static double frameBudgetPercent60Fps(double micros) {
        if (micros <= 0.0) return 0.0;
        return micros * 100.0 / FRAME_BUDGET_MICROS_60FPS;
    }

    public static double percent(long numerator, long denominator) {
        if (numerator <= 0L || denominator <= 0L) return 0.0;
        return (double) numerator * 100.0 / (double) denominator;
    }

    /**
     * Tracks whether ammo made available by a refill is followed by a later
     * ammo-spend event. Multiple refills before the next spend are one
     * availability episode rather than multiple false "unused" episodes.
     */
    public static final class UsageTracker {
        private boolean pending;
        private float firstRefillAt;
        private int refillEvents;
        private int availabilityEpisodes;
        private int episodesUsed;
        private double useDelayTotalSeconds;
        private float maxUseDelaySeconds;

        public void onRefill(float combatTime) {
            refillEvents++;
            if (!pending) {
                pending = true;
                firstRefillAt = combatTime;
                availabilityEpisodes++;
            }
        }

        /** Returns true only when a pending regenerated-ammo episode is consumed. */
        public boolean onAmmoSpend(float combatTime) {
            if (!pending) return false;
            pending = false;
            episodesUsed++;
            float delay = Math.max(0f, combatTime - firstRefillAt);
            useDelayTotalSeconds += delay;
            if (delay > maxUseDelaySeconds) maxUseDelaySeconds = delay;
            return true;
        }

        public int getRefillEvents() { return refillEvents; }
        public int getAvailabilityEpisodes() { return availabilityEpisodes; }
        public int getEpisodesUsed() { return episodesUsed; }
        public int getPendingEpisodes() { return pending ? 1 : 0; }
        public double getUseDelayTotalSeconds() { return useDelayTotalSeconds; }
        public float getMaxUseDelaySeconds() { return maxUseDelaySeconds; }
    }
}
