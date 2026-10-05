package umr;

import com.fs.starfarer.api.combat.AmmoTrackerAPI;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import java.util.IdentityHashMap;
import java.util.TreeMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validated production-regeneration engine with Phase 6 diagnostics, Phase 7A
 * Autoloader policy, and Phase 9 read-only AI/performance telemetry.
 *
 * Phase 9 does not change regeneration timing, eligibility, ammo mutation, or AI
 * hints. It measures the existing engine and records post-refill AI use.
 */
public class UMRCombatRegenerationPlugin extends BaseEveryFrameCombatPlugin {
    private static final float EPSILON = 0.0001f;
    private static int nextBattleNumber = 1;

    private final Map<WeaponAPI, RegenState> states = new IdentityHashMap<WeaponAPI, RegenState>();
    private final Map<WeaponAPI, BaselineState> aiBaselineStates = new IdentityHashMap<WeaponAPI, BaselineState>();
    private final Map<ShipAPI, Boolean> shipsObserved = new IdentityHashMap<ShipAPI, Boolean>();
    private final Map<ShipAPI, Boolean> autoloaderCheckedShips = new IdentityHashMap<ShipAPI, Boolean>();

    private CombatEngineAPI engine;
    private float combatTime = 0f;
    private boolean summaryLogged = false;
    private String battleId = "B0000";
    private boolean performanceTelemetryEnabled = false;
    private boolean aiTelemetryEnabled = false;

    private long perfMeasuredFrames = 0L;
    private long perfTotalNanos = 0L;
    private long perfMaxNanos = 0L;
    private long perfShipVisits = 0L;
    private long perfEligibleShipVisits = 0L;
    private long perfWeaponVisits = 0L;
    private long perfManagedWeaponVisits = 0L;
    private long perfAutoloaderFirstChecks = 0L;
    private int perfPeakManagedStates = 0;
    private int perfPeakBaselineStates = 0;

    private int candidates = 0;
    private int playerControlledCandidates = 0;
    private int aiControlledCandidates = 0;
    private int owner0Candidates = 0;
    private int owner1Candidates = 0;
    private int otherOwnerCandidates = 0;
    private int timerStarts = 0;
    private int timerContinues = 0;
    private int fullRefills = 0;
    private int partialTopoffs = 0;
    private int completeNoDeficit = 0;
    private int externalAmmoIncreases = 0;
    private int runtimeCapChanges = 0;
    private int immediatePasses = 0;
    private int immediateFailures = 0;
    private int frameErrors = 0;
    private int totalAmmoRestored = 0;
    private int owner0Refills = 0;
    private int owner1Refills = 0;
    private int otherOwnerRefills = 0;
    private int autoloaderShips = 0;
    private int autoloaderSuppressionShips = 0;
    private int autoloaderManagedWeaponsSuppressed = 0;
    private int autoloaderFallbackWeaponsPreserved = 0;
    private int autoloaderDataCreated = 0;
    private int autoloaderSuppressionErrors = 0;

    @Override
    public void init(CombatEngineAPI engine) {
        this.engine = engine;
        this.battleId = nextBattleId();
        this.performanceTelemetryEnabled = UMRConfig.isPerformanceTelemetryEnabled();
        this.aiTelemetryEnabled = UMRConfig.isAiTelemetryEnabled();
        UMRLogger.get().info(
                "[UMR][PHASE6_BATTLE_START] battle=" + battleId +
                " version=" + UMRBuildInfo.getVersion() +
                " phase=" + UMRBuildInfo.getPhase() +
                " productionTiming=true playerAndNpc=true" +
                " managedCount=" + UMRProductionRegistry.size() +
                " managed=" + joinManagedIds() +
                " stateModel=phase1c_validated runtimeMaxCeiling=true" +
                " autoloaderPolicy=managed_cooldown_gate" +
                " autoloaderGlobalSpecMutation=false" +
                " phase9PerformanceTelemetry=" + performanceTelemetryEnabled +
                " phase9AiTelemetry=" + aiTelemetryEnabled);
        if (performanceTelemetryEnabled || aiTelemetryEnabled) {
            UMRLogger.get().info("[UMR][PHASE9_TELEMETRY_START] battle=" + battleId +
                    " performanceTelemetry=" + performanceTelemetryEnabled +
                    " aiTelemetry=" + aiTelemetryEnabled +
                    " loggingLevel=" + UMRConfig.getLoggingLevel() +
                    " gameplayMutation=false aiHintsMutation=false");
        }
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        if (engine == null || engine.isPaused()) return;

        combatTime += amount;

        if (engine.isCombatOver()) {
            logSummaryOnce();
            return;
        }

        long perfStart = performanceTelemetryEnabled ? System.nanoTime() : 0L;
        try {
            List<ShipAPI> ships = engine.getShips();
            if (ships == null) return;

            if (performanceTelemetryEnabled) perfShipVisits += ships.size();

            for (ShipAPI ship : ships) {
                if (!UMRCombatEligibility.isEligibleShip(ship)) continue;
                if (performanceTelemetryEnabled) perfEligibleShipVisits++;
                shipsObserved.put(ship, Boolean.TRUE);
                installAutoloaderSuppressionOnce(ship);

                List<WeaponAPI> weapons = ship.getAllWeapons();
                if (weapons == null) continue;
                if (performanceTelemetryEnabled) perfWeaponVisits += weapons.size();

                boolean playerControlled = ship == engine.getPlayerShip();
                for (WeaponAPI weapon : weapons) {
                    UMRProductionRegistry.Entry entry = UMRCombatEligibility.getManagedEntry(ship, weapon);
                    if (entry == null) {
                        if (aiTelemetryEnabled && !playerControlled) advanceAiBaseline(weapon);
                        continue;
                    }

                    if (performanceTelemetryEnabled) perfManagedWeaponVisits++;
                    RegenState state = states.get(weapon);
                    if (state == null) {
                        state = createState(ship, weapon, entry);
                        states.put(weapon, state);
                        if (states.size() > perfPeakManagedStates) perfPeakManagedStates = states.size();
                    }
                    advanceState(ship, weapon, state, amount);
                }
            }
        } catch (Throwable t) {
            frameErrors++;
            UMRLogger.get().error("[UMR][PHASE6_FRAME_ERROR] battle=" + battleId + " action=skip_failed_frame", t);
        } finally {
            if (performanceTelemetryEnabled) {
                long elapsed = System.nanoTime() - perfStart;
                if (elapsed < 0L) elapsed = 0L;
                perfMeasuredFrames++;
                perfTotalNanos += elapsed;
                if (elapsed > perfMaxNanos) perfMaxNanos = elapsed;
            }
        }
    }

    private void installAutoloaderSuppressionOnce(ShipAPI ship) {
        if (autoloaderCheckedShips.containsKey(ship)) return;
        autoloaderCheckedShips.put(ship, Boolean.TRUE);
        if (performanceTelemetryEnabled) perfAutoloaderFirstChecks++;

        UMRAutoloaderSuppression.InstallStatus status = UMRAutoloaderSuppression.ensureInstalled(ship);
        if (!status.hasAutoloader) return;

        autoloaderShips++;
        autoloaderFallbackWeaponsPreserved += status.fallbackAffected;
        if (status.installed || status.alreadyInstalled) {
            autoloaderSuppressionShips++;
            autoloaderManagedWeaponsSuppressed += status.managedAffected;
            if (status.dataCreated) autoloaderDataCreated++;
            UMRLogger.get().info(
                    "[UMR][PHASE7A_AUTOLOADER_POLICY]" + shipContext(ship) +
                    " t=" + fmt(combatTime) +
                    " managedAffected=" + status.managedAffected +
                    " fallbackAffected=" + status.fallbackAffected +
                    " dataCreated=" + status.dataCreated +
                    " priorTracker=" + safe(status.priorTrackerClass) +
                    " globalSpecMutation=false" +
                    " result=PASS");
        } else if (status.conflict) {
            autoloaderSuppressionErrors++;
            UMRLogger.get().error(
                    "[UMR][PHASE7A_AUTOLOADER_POLICY]" + shipContext(ship) +
                    " t=" + fmt(combatTime) +
                    " managedAffected=" + status.managedAffected +
                    " fallbackAffected=" + status.fallbackAffected +
                    " globalSpecMutation=false" +
                    " reason=" + safe(status.reason) +
                    " result=FAIL");
        } else {
            UMRLogger.get().info(
                    "[UMR][PHASE7A_AUTOLOADER_POLICY]" + shipContext(ship) +
                    " t=" + fmt(combatTime) +
                    " managedAffected=0" +
                    " fallbackAffected=" + status.fallbackAffected +
                    " mode=vanilla_fallback_only" +
                    " globalSpecMutation=false" +
                    " result=PASS");
        }
    }

    private RegenState createState(ShipAPI ship, WeaponAPI weapon, UMRProductionRegistry.Entry entry) {
        boolean playerControlled = ship == engine.getPlayerShip();
        RegenState state = new RegenState(
                entry.packetSize,
                (float) entry.intervalSeconds,
                weapon.getAmmo(),
                weapon.getMaxAmmo(),
                playerControlled,
                weapon.getId());
        candidates++;
        if (playerControlled) playerControlledCandidates++; else aiControlledCandidates++;
        incrementCandidateOwner(ship.getOwner());

        if (UMRLogger.verbose()) UMRLogger.get().info(
                "[UMR][PHASE6_CANDIDATE]" + context(ship, weapon) +
                " t=" + fmt(combatTime) +
                " baseMax=" + entry.baseMaxAmmo +
                " runtimeMax=" + weapon.getMaxAmmo() +
                " current=" + weapon.getAmmo() +
                " runtimeRemainder=" + (state.packetSize <= 0 ? 0 : weapon.getMaxAmmo() % state.packetSize) +
                " packet=" + state.packetSize +
                " intervalSec=" + fmt(state.intervalSeconds) +
                " safetyBinding=" + entry.safetyFloorBinding +
                " overrideApplied=" + entry.overrideApplied +
                " overrideAuthority=" + safe(entry.overrideAuthority) +
                " overrideSource=" + safe(entry.overrideSource) +
                " productionTiming=true");
        return state;
    }

    private void advanceState(ShipAPI ship, WeaponAPI weapon, RegenState state, float amount) {
        int current = weapon.getAmmo();
        int runtimeMax = weapon.getMaxAmmo();
        boolean firing = weapon.isFiring();

        if (aiTelemetryEnabled) {
            state.telemetrySamples++;
            if (current <= 0) state.zeroAmmoSamples++;
            if (runtimeMax > 0 && current >= runtimeMax) state.fullAmmoSamples++;
            if (current < state.lastAmmo) {
                state.ammoSpendEvents++;
                state.usage.onAmmoSpend(combatTime);
            }
        }

        if (runtimeMax != state.lastRuntimeMax) {
            runtimeCapChanges++;
            if (UMRLogger.verbose()) UMRLogger.get().info(
                    "[UMR][PHASE6_CAP_CHANGE]" + context(ship, weapon) +
                    " t=" + fmt(combatTime) +
                    " before=" + state.lastRuntimeMax +
                    " after=" + runtimeMax +
                    " timerActive=" + state.active +
                    " progress=" + fmt(state.progress) +
                    " intervalUnchanged=" + fmt(state.intervalSeconds));
        }

        if (current > state.lastAmmo) {
            externalAmmoIncreases++;
            if (UMRLogger.verbose()) UMRLogger.get().info(
                    "[UMR][PHASE6_EXTERNAL_AMMO_INCREASE]" + context(ship, weapon) +
                    " t=" + fmt(combatTime) +
                    " before=" + state.lastAmmo +
                    " after=" + current +
                    " timerActive=" + state.active +
                    " progressPreserved=" + fmt(state.progress));
        } else if (current < state.lastAmmo && state.active) {
            if (UMRLogger.verbose()) UMRLogger.get().info(
                    "[UMR][PHASE6_FIRE_DURING_TIMER]" + context(ship, weapon) +
                    " t=" + fmt(combatTime) +
                    " ammo=" + state.lastAmmo + "->" + current +
                    " firing=" + firing +
                    " progressPreserved=" + fmt(state.progress) +
                    " intervalSec=" + fmt(state.intervalSeconds));
        }

        int deficit = Math.max(0, runtimeMax - current);

        if (!state.active) {
            if (deficit >= state.packetSize) {
                startChain(ship, weapon, state, current, runtimeMax, deficit, firing);
            } else {
                updateLast(state, current, runtimeMax);
                return;
            }
        }

        state.progress += amount;
        logProgressThresholds(ship, weapon, state);

        if (state.progress + EPSILON >= state.intervalSeconds) {
            completeOneInterval(ship, weapon, state);
        }

        updateLast(state, weapon.getAmmo(), weapon.getMaxAmmo());
    }

    private void startChain(ShipAPI ship, WeaponAPI weapon, RegenState state,
                            int current, int runtimeMax, int deficit, boolean firing) {
        state.active = true;
        state.progress = 0f;
        state.nextProgressFraction = 0.25f;
        state.chainNumber++;
        state.cycleNumber = 1;
        timerStarts++;

        if (UMRLogger.verbose()) UMRLogger.get().info(
                "[UMR][PHASE6_TIMER_START]" + context(ship, weapon) +
                " t=" + fmt(combatTime) +
                " chain=" + state.chainNumber +
                " current=" + current +
                " runtimeMax=" + runtimeMax +
                " deficit=" + deficit +
                " packet=" + state.packetSize +
                " intervalSec=" + fmt(state.intervalSeconds) +
                " etaCombatT=" + fmt(combatTime + state.intervalSeconds) +
                " firing=" + firing);
    }

    private void logProgressThresholds(ShipAPI ship, WeaponAPI weapon, RegenState state) {
        while (state.nextProgressFraction < 1.0f) {
            float threshold = state.intervalSeconds * state.nextProgressFraction;
            if (state.progress + EPSILON < threshold) break;

            if (UMRLogger.trace()) UMRLogger.get().info(
                    "[UMR][PHASE6_TIMER_PROGRESS]" + context(ship, weapon) +
                    " t=" + fmt(combatTime) +
                    " chain=" + state.chainNumber +
                    " cycle=" + state.cycleNumber +
                    " percent=" + Math.round(state.nextProgressFraction * 100f) +
                    " progress=" + fmt(state.progress) +
                    "/" + fmt(state.intervalSeconds) +
                    " current=" + weapon.getAmmo() +
                    " runtimeMax=" + weapon.getMaxAmmo() +
                    " firing=" + weapon.isFiring());
            state.nextProgressFraction += 0.25f;
        }
    }

    private void completeOneInterval(ShipAPI ship, WeaponAPI weapon, RegenState state) {
        float carried = Math.max(0f, state.progress - state.intervalSeconds);
        int before = weapon.getAmmo();
        int runtimeMax = weapon.getMaxAmmo();
        int deficit = Math.max(0, runtimeMax - before);

        if (deficit <= 0) {
            completeNoDeficit++;
            if (UMRLogger.verbose()) UMRLogger.get().info(
                    "[UMR][PHASE6_TIMER_COMPLETE_NO_DEFICIT]" + context(ship, weapon) +
                    " t=" + fmt(combatTime) +
                    " chain=" + state.chainNumber +
                    " current=" + before +
                    " runtimeMax=" + runtimeMax +
                    " action=stop_chain staleCreditDiscarded=true");
            stopChain(state);
            return;
        }

        int add = Math.min(state.packetSize, deficit);
        boolean partial = add < state.packetSize;
        int target = Math.min(runtimeMax, before + add);
        boolean firingBefore = weapon.isFiring();
        float cooldownBefore = weapon.getCooldownRemaining();
        float chargeBefore = weapon.getChargeLevel();

        weapon.setAmmo(target);

        int immediate = weapon.getAmmo();
        AmmoTrackerAPI tracker = weapon.getAmmoTracker();
        int trackerAmmo = tracker == null ? -1 : tracker.getAmmo();
        boolean pass = immediate == target &&
                immediate <= runtimeMax &&
                (tracker == null || trackerAmmo == target);

        if (pass) {
            immediatePasses++;
            totalAmmoRestored += add;
            incrementRefillOwner(ship.getOwner());
            if (aiTelemetryEnabled) state.usage.onRefill(combatTime);
        } else {
            immediateFailures++;
        }
        if (partial) partialTopoffs++; else fullRefills++;

        int completedCycle = state.cycleNumber;
        int remaining = Math.max(0, weapon.getMaxAmmo() - weapon.getAmmo());

        if (UMRLogger.verbose()) UMRLogger.get().info(
                "[UMR][PHASE6_REFILL]" + context(ship, weapon) +
                " t=" + fmt(combatTime) +
                " chain=" + state.chainNumber +
                " cycle=" + completedCycle +
                " before=" + before +
                " runtimeMax=" + runtimeMax +
                " liveDeficit=" + deficit +
                " packet=" + state.packetSize +
                " intervalSec=" + fmt(state.intervalSeconds) +
                " added=" + add +
                " after=" + immediate +
                " trackerAmmo=" + trackerAmmo +
                " remainingDeficit=" + remaining +
                " refillType=" + (partial ? "PARTIAL_CAP_TOP_OFF" : "FULL_BURST") +
                " firingBefore=" + firingBefore +
                " firingAfter=" + weapon.isFiring() +
                " cooldownBefore=" + fmt(cooldownBefore) +
                " cooldownAfter=" + fmt(weapon.getCooldownRemaining()) +
                " chargeBefore=" + fmt(chargeBefore) +
                " chargeAfter=" + fmt(weapon.getChargeLevel()) +
                " result=" + (pass ? "PASS" : "FAIL"));

        if (!pass) {
            stopChain(state);
            return;
        }

        if (remaining > 0) {
            state.active = true;
            state.progress = carried;
            state.nextProgressFraction = nextProgressFraction(carried, state.intervalSeconds);
            state.cycleNumber++;
            timerContinues++;
            if (UMRLogger.verbose()) UMRLogger.get().info(
                    "[UMR][PHASE6_TIMER_CONTINUE]" + context(ship, weapon) +
                    " t=" + fmt(combatTime) +
                    " chain=" + state.chainNumber +
                    " nextCycle=" + state.cycleNumber +
                    " remainingDeficit=" + remaining +
                    " packet=" + state.packetSize +
                    " topoffPending=" + (remaining < state.packetSize) +
                    " progressCarried=" + fmt(state.progress) +
                    " intervalSec=" + fmt(state.intervalSeconds));
        } else {
            if (UMRLogger.verbose()) UMRLogger.get().info(
                    "[UMR][PHASE6_CHAIN_COMPLETE]" + context(ship, weapon) +
                    " t=" + fmt(combatTime) +
                    " chain=" + state.chainNumber +
                    " current=" + weapon.getAmmo() +
                    " runtimeMax=" + weapon.getMaxAmmo());
            stopChain(state);
        }
    }

    private String shipContext(ShipAPI ship) {
        boolean playerControlled = engine != null && ship == engine.getPlayerShip();
        String hullId = ship == null || ship.getHullSpec() == null ? "unknown" : ship.getHullSpec().getHullId();
        String variantId = "unknown";
        try {
            ShipVariantAPI variant = ship == null ? null : ship.getVariant();
            if (variant != null) variantId = variant.getHullVariantId();
        } catch (Throwable ignored) {}
        UMRProvenance.SourceInfo hullSource = UMRProvenance.forHull(ship == null ? null : ship.getHullSpec());
        return " battle=" + battleId +
                " owner=" + (ship == null ? -1 : ship.getOwner()) +
                " control=" + (playerControlled ? "PLAYER_CONTROLLED" : "AI_CONTROLLED") +
                " ship=" + safe(ship == null ? null : ship.getName()) +
                " hull=" + safe(hullId) +
                " variant=" + safe(variantId) +
                hullSource.logFields("hull");
    }

    private String context(ShipAPI ship, WeaponAPI weapon) {
        boolean playerControlled = engine != null && ship == engine.getPlayerShip();
        String hullId = ship == null || ship.getHullSpec() == null ? "unknown" : ship.getHullSpec().getHullId();
        String variantId = "unknown";
        try {
            ShipVariantAPI variant = ship == null ? null : ship.getVariant();
            if (variant != null) variantId = variant.getHullVariantId();
        } catch (Throwable ignored) {}
        UMRProvenance.SourceInfo hullSource = UMRProvenance.forHull(ship == null ? null : ship.getHullSpec());
        UMRProvenance.SourceInfo weaponSource = UMRProvenance.forWeapon(weapon == null ? null : weapon.getOriginalSpec());
        return " battle=" + battleId +
                " owner=" + (ship == null ? -1 : ship.getOwner()) +
                " control=" + (playerControlled ? "PLAYER_CONTROLLED" : "AI_CONTROLLED") +
                " ship=" + safe(ship == null ? null : ship.getName()) +
                " hull=" + safe(hullId) +
                " variant=" + safe(variantId) +
                hullSource.logFields("hull") +
                " weapon=" + safe(weapon == null ? null : weapon.getId()) +
                weaponSource.logFields("weapon") +
                " slot=" + safe(slotId(weapon));
    }

    private void stopChain(RegenState state) {
        state.active = false;
        state.progress = 0f;
        state.nextProgressFraction = 0.25f;
        state.cycleNumber = 0;
    }

    private void logSummaryOnce() {
        if (summaryLogged) return;
        summaryLogged = true;
        int unusualEvents = partialTopoffs + completeNoDeficit + externalAmmoIncreases + runtimeCapChanges + immediateFailures + frameErrors + autoloaderSuppressionErrors;
        int errorCount = immediateFailures + frameErrors + autoloaderSuppressionErrors;
        UMRLogger.get().info(
                "[UMR][PHASE6_BATTLE_SUMMARY]" +
                " battle=" + battleId +
                " combatTime=" + fmt(combatTime) +
                " shipsObserved=" + shipsObserved.size() +
                " eligibleWeaponInstances=" + candidates +
                " playerControlledCandidates=" + playerControlledCandidates +
                " aiControlledCandidates=" + aiControlledCandidates +
                " owner0Candidates=" + owner0Candidates +
                " owner1Candidates=" + owner1Candidates +
                " otherOwnerCandidates=" + otherOwnerCandidates +
                " timerStarts=" + timerStarts +
                " timerContinues=" + timerContinues +
                " refillEvents=" + (fullRefills + partialTopoffs) +
                " fullRefills=" + fullRefills +
                " partialTopoffs=" + partialTopoffs +
                " totalAmmoRestored=" + totalAmmoRestored +
                " owner0Refills=" + owner0Refills +
                " owner1Refills=" + owner1Refills +
                " otherOwnerRefills=" + otherOwnerRefills +
                " completeNoDeficit=" + completeNoDeficit +
                " externalAmmoIncreases=" + externalAmmoIncreases +
                " runtimeCapChanges=" + runtimeCapChanges +
                " immediatePasses=" + immediatePasses +
                " immediateFailures=" + immediateFailures +
                " unusualEvents=" + unusualEvents +
                " errorCount=" + errorCount +
                " frameErrors=" + frameErrors +
                " autoloaderShips=" + autoloaderShips +
                " autoloaderSuppressionShips=" + autoloaderSuppressionShips +
                " autoloaderManagedWeaponsSuppressed=" + autoloaderManagedWeaponsSuppressed +
                " autoloaderFallbackWeaponsPreserved=" + autoloaderFallbackWeaponsPreserved +
                " autoloaderDataCreated=" + autoloaderDataCreated +
                " autoloaderSuppressionErrors=" + autoloaderSuppressionErrors +
                " managedCount=" + UMRProductionRegistry.size());
        logPhase9Telemetry();
    }

    private void advanceAiBaseline(WeaponAPI weapon) {
        if (weapon == null || weapon.isDecorative()) return;
        WeaponSlotAPI slot = weapon.getSlot();
        if (slot != null && (slot.isSystemSlot() || slot.isDecorative())) return;

        BaselineState state = aiBaselineStates.get(weapon);
        if (state == null) {
            WeaponSpecAPI spec = weapon.getOriginalSpec();
            if (spec == null || !spec.usesAmmo() || spec.getMaxAmmo() <= 0) return;
            BaselineType type;
            if (spec.getAmmoPerSecond() > 0f) {
                type = BaselineType.NATIVE_REGEN;
            } else if (weapon.getType() != WeaponAPI.WeaponType.MISSILE) {
                type = BaselineType.ORDINARY_FINITE;
            } else {
                return;
            }
            state = new BaselineState(type, weapon.getAmmo(), weapon.getMaxAmmo());
            aiBaselineStates.put(weapon, state);
            if (aiBaselineStates.size() > perfPeakBaselineStates) perfPeakBaselineStates = aiBaselineStates.size();
        }

        int current = weapon.getAmmo();
        int max = weapon.getMaxAmmo();
        state.samples++;
        if (current <= 0) state.zeroAmmoSamples++;
        if (max > 0 && current >= max) state.fullAmmoSamples++;
        if (current < state.lastAmmo) state.ammoSpendEvents++;
        else if (current > state.lastAmmo) state.ammoIncreaseEvents++;
        if (weapon.isFiring()) state.firingSamples++;
        state.lastAmmo = current;
        state.lastRuntimeMax = max;
    }

    private void logPhase9Telemetry() {
        if (performanceTelemetryEnabled) logPhase9PerformanceSummary();
        if (aiTelemetryEnabled) logPhase9AiSummary();
    }

    private void logPhase9PerformanceSummary() {
        double avgMicros = UMRPhase9Telemetry.averageMicros(perfTotalNanos, perfMeasuredFrames);
        double maxMicros = UMRPhase9Telemetry.nanosToMicros(perfMaxNanos);
        UMRLogger.get().info("[UMR][PHASE9_PERF_SUMMARY]" +
                " battle=" + battleId +
                " loggingLevel=" + UMRConfig.getLoggingLevel() +
                " performanceTelemetry=" + performanceTelemetryEnabled +
                " aiTelemetry=" + aiTelemetryEnabled +
                " combatTimeSec=" + fmt(combatTime) +
                " measuredFrames=" + perfMeasuredFrames +
                " totalWorkMs=" + fmtDouble((double) perfTotalNanos / 1000000.0) +
                " avgWorkMicrosPerFrame=" + fmtDouble(avgMicros) +
                " maxWorkMicros=" + fmtDouble(maxMicros) +
                " avgFrameBudgetPct60Fps=" + fmtDouble(UMRPhase9Telemetry.frameBudgetPercent60Fps(avgMicros)) +
                " maxFrameBudgetPct60Fps=" + fmtDouble(UMRPhase9Telemetry.frameBudgetPercent60Fps(maxMicros)) +
                " shipVisits=" + perfShipVisits +
                " eligibleShipVisits=" + perfEligibleShipVisits +
                " weaponVisits=" + perfWeaponVisits +
                " managedWeaponVisits=" + perfManagedWeaponVisits +
                " autoloaderFirstChecks=" + perfAutoloaderFirstChecks +
                " managedStatesFinal=" + states.size() +
                " managedStatesPeak=" + perfPeakManagedStates +
                " baselineStatesFinal=" + aiBaselineStates.size() +
                " baselineStatesPeak=" + perfPeakBaselineStates +
                " registryManaged=" + UMRProductionRegistry.size() +
                " result=MEASURED");
    }

    private void logPhase9AiSummary() {
        UsageAggregate ai = new UsageAggregate();
        UsageAggregate player = new UsageAggregate();
        TreeMap<String, UsageAggregate> byWeapon = new TreeMap<String, UsageAggregate>();

        for (RegenState state : states.values()) {
            UsageAggregate target = state.playerControlled ? player : ai;
            target.add(state);
            if (!state.playerControlled) {
                UsageAggregate weapon = byWeapon.get(state.weaponId);
                if (weapon == null) {
                    weapon = new UsageAggregate();
                    byWeapon.put(state.weaponId, weapon);
                }
                weapon.add(state);
            }
        }

        BaselineAggregate ordinary = new BaselineAggregate();
        BaselineAggregate nativeRegen = new BaselineAggregate();
        for (BaselineState state : aiBaselineStates.values()) {
            if (state.type == BaselineType.NATIVE_REGEN) nativeRegen.add(state);
            else ordinary.add(state);
        }

        UMRLogger.get().info("[UMR][PHASE9_AI_SUMMARY]" +
                " battle=" + battleId +
                " managedAiInstances=" + ai.instances +
                " aiRefillEvents=" + ai.refillEvents +
                " aiAvailabilityEpisodes=" + ai.availabilityEpisodes +
                " aiEpisodesUsed=" + ai.episodesUsed +
                " aiEpisodesPendingAtBattleEnd=" + ai.pendingEpisodes +
                " aiUseRatePct=" + fmtDouble(UMRPhase9Telemetry.percent(ai.episodesUsed, ai.availabilityEpisodes)) +
                " aiAvgUseDelaySec=" + fmtDouble(ai.averageUseDelay()) +
                " aiMaxUseDelaySec=" + fmtDouble(ai.maxUseDelaySeconds) +
                " aiAmmoSpendEvents=" + ai.ammoSpendEvents +
                " aiZeroAmmoSamplePct=" + fmtDouble(UMRPhase9Telemetry.percent(ai.zeroAmmoSamples, ai.samples)) +
                " aiFullAmmoSamplePct=" + fmtDouble(UMRPhase9Telemetry.percent(ai.fullAmmoSamples, ai.samples)) +
                " managedPlayerInstances=" + player.instances +
                " playerAvailabilityEpisodes=" + player.availabilityEpisodes +
                " playerEpisodesUsed=" + player.episodesUsed +
                " playerUseRatePct=" + fmtDouble(UMRPhase9Telemetry.percent(player.episodesUsed, player.availabilityEpisodes)) +
                " ordinaryFiniteBaselineInstances=" + ordinary.instances +
                " nativeRegenBaselineInstances=" + nativeRegen.instances +
                " result=MEASURED");

        logBaseline("ORDINARY_FINITE", ordinary);
        logBaseline("NATIVE_REGEN", nativeRegen);

        for (Map.Entry<String, UsageAggregate> entry : byWeapon.entrySet()) {
            UsageAggregate w = entry.getValue();
            UMRLogger.get().info("[UMR][PHASE9_AI_WEAPON]" +
                    " battle=" + battleId +
                    " weapon=" + safe(entry.getKey()) +
                    " aiInstances=" + w.instances +
                    " refillEvents=" + w.refillEvents +
                    " availabilityEpisodes=" + w.availabilityEpisodes +
                    " episodesUsed=" + w.episodesUsed +
                    " pendingAtBattleEnd=" + w.pendingEpisodes +
                    " useRatePct=" + fmtDouble(UMRPhase9Telemetry.percent(w.episodesUsed, w.availabilityEpisodes)) +
                    " avgUseDelaySec=" + fmtDouble(w.averageUseDelay()) +
                    " maxUseDelaySec=" + fmtDouble(w.maxUseDelaySeconds) +
                    " ammoSpendEvents=" + w.ammoSpendEvents +
                    " zeroAmmoSamplePct=" + fmtDouble(UMRPhase9Telemetry.percent(w.zeroAmmoSamples, w.samples)) +
                    " fullAmmoSamplePct=" + fmtDouble(UMRPhase9Telemetry.percent(w.fullAmmoSamples, w.samples)) +
                    " result=MEASURED");
        }
    }

    private void logBaseline(String type, BaselineAggregate b) {
        UMRLogger.get().info("[UMR][PHASE9_AI_BASELINE]" +
                " battle=" + battleId +
                " type=" + type +
                " instances=" + b.instances +
                " samples=" + b.samples +
                " firingSamples=" + b.firingSamples +
                " ammoSpendEvents=" + b.ammoSpendEvents +
                " ammoIncreaseEvents=" + b.ammoIncreaseEvents +
                " zeroAmmoSamplePct=" + fmtDouble(UMRPhase9Telemetry.percent(b.zeroAmmoSamples, b.samples)) +
                " fullAmmoSamplePct=" + fmtDouble(UMRPhase9Telemetry.percent(b.fullAmmoSamples, b.samples)) +
                " result=MEASURED");
    }

    private void incrementCandidateOwner(int owner) {
        if (owner == 0) owner0Candidates++;
        else if (owner == 1) owner1Candidates++;
        else otherOwnerCandidates++;
    }

    private void incrementRefillOwner(int owner) {
        if (owner == 0) owner0Refills++;
        else if (owner == 1) owner1Refills++;
        else otherOwnerRefills++;
    }

    private static synchronized String nextBattleId() {
        return String.format(Locale.ROOT, "B%04d", nextBattleNumber++);
    }

    private static float nextProgressFraction(float carried, float interval) {
        if (interval <= 0f) return 0.25f;
        float fraction = carried / interval;
        if (fraction < 0.25f) return 0.25f;
        if (fraction < 0.50f) return 0.50f;
        if (fraction < 0.75f) return 0.75f;
        return 1.0f;
    }

    private static void updateLast(RegenState state, int ammo, int runtimeMax) {
        state.lastAmmo = ammo;
        state.lastRuntimeMax = runtimeMax;
    }

    private static String slotId(WeaponAPI weapon) {
        WeaponSlotAPI slot = weapon == null ? null : weapon.getSlot();
        return slot == null ? "null" : slot.getId();
    }

    private static String joinManagedIds() {
        StringBuilder b = new StringBuilder();
        for (String id : UMRProductionRegistry.getManagedIds()) {
            if (b.length() > 0) b.append(',');
            b.append(id);
        }
        return b.toString();
    }

    private static String safe(String value) {
        if (value == null || value.trim().isEmpty()) return "unknown";
        return value.replace(' ', '_').replace('\n', '_').replace('\r', '_').replace('\t', '_');
    }

    private static String fmt(float value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String fmtDouble(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static final class RegenState {
        final int packetSize;
        final float intervalSeconds;
        final boolean playerControlled;
        final String weaponId;
        final UMRPhase9Telemetry.UsageTracker usage = new UMRPhase9Telemetry.UsageTracker();
        int lastAmmo;
        int lastRuntimeMax;
        boolean active;
        float progress;
        float nextProgressFraction = 0.25f;
        int chainNumber;
        int cycleNumber;
        long telemetrySamples;
        long zeroAmmoSamples;
        long fullAmmoSamples;
        long ammoSpendEvents;

        RegenState(int packetSize, float intervalSeconds,
                   int lastAmmo, int lastRuntimeMax,
                   boolean playerControlled, String weaponId) {
            this.packetSize = packetSize;
            this.intervalSeconds = intervalSeconds;
            this.lastAmmo = lastAmmo;
            this.lastRuntimeMax = lastRuntimeMax;
            this.playerControlled = playerControlled;
            this.weaponId = weaponId == null ? "unknown" : weaponId;
        }
    }

    private enum BaselineType { ORDINARY_FINITE, NATIVE_REGEN }

    private static final class BaselineState {
        final BaselineType type;
        int lastAmmo;
        int lastRuntimeMax;
        long samples;
        long zeroAmmoSamples;
        long fullAmmoSamples;
        long firingSamples;
        long ammoSpendEvents;
        long ammoIncreaseEvents;

        BaselineState(BaselineType type, int lastAmmo, int lastRuntimeMax) {
            this.type = type;
            this.lastAmmo = lastAmmo;
            this.lastRuntimeMax = lastRuntimeMax;
        }
    }

    private static final class UsageAggregate {
        long instances;
        long samples;
        long zeroAmmoSamples;
        long fullAmmoSamples;
        long ammoSpendEvents;
        long refillEvents;
        long availabilityEpisodes;
        long episodesUsed;
        long pendingEpisodes;
        double useDelayTotalSeconds;
        double maxUseDelaySeconds;

        void add(RegenState state) {
            instances++;
            samples += state.telemetrySamples;
            zeroAmmoSamples += state.zeroAmmoSamples;
            fullAmmoSamples += state.fullAmmoSamples;
            ammoSpendEvents += state.ammoSpendEvents;
            refillEvents += state.usage.getRefillEvents();
            availabilityEpisodes += state.usage.getAvailabilityEpisodes();
            episodesUsed += state.usage.getEpisodesUsed();
            pendingEpisodes += state.usage.getPendingEpisodes();
            useDelayTotalSeconds += state.usage.getUseDelayTotalSeconds();
            maxUseDelaySeconds = Math.max(maxUseDelaySeconds, state.usage.getMaxUseDelaySeconds());
        }

        double averageUseDelay() {
            return episodesUsed <= 0L ? 0.0 : useDelayTotalSeconds / (double) episodesUsed;
        }
    }

    private static final class BaselineAggregate {
        long instances;
        long samples;
        long zeroAmmoSamples;
        long fullAmmoSamples;
        long firingSamples;
        long ammoSpendEvents;
        long ammoIncreaseEvents;

        void add(BaselineState state) {
            instances++;
            samples += state.samples;
            zeroAmmoSamples += state.zeroAmmoSamples;
            fullAmmoSamples += state.fullAmmoSamples;
            firingSamples += state.firingSamples;
            ammoSpendEvents += state.ammoSpendEvents;
            ammoIncreaseEvents += state.ammoIncreaseEvents;
        }
    }
}
