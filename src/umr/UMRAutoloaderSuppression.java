package umr;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.hullmods.MissileAutoloader;
import com.fs.starfarer.api.impl.hullmods.MissileAutoloader.MissileAutoloaderData;
import com.fs.starfarer.api.impl.hullmods.MissileAutoloader.ReloadCapacityData;
import com.fs.starfarer.api.util.TimeoutTracker;
import java.util.List;

/**
 * Phase 7A Missile Autoloader double-dip prevention.
 *
 * The vanilla RC8 Missile Autoloader skips a weapon whenever its per-ship
 * cooldown tracker reports that the weapon is present. UMR wraps that tracker
 * and reports managed live weapons as virtually present, while delegating all
 * real cooldown state and operations unchanged. This avoids global WeaponSpec
 * tag mutation and preserves normal Autoloader behavior for unmanaged weapons
 * on the same ship.
 */
public final class UMRAutoloaderSuppression {
    private UMRAutoloaderSuppression() {}

    public static InstallStatus ensureInstalled(ShipAPI ship) {
        if (!UMRCombatEligibility.isEligibleShip(ship) || !hasAutoloader(ship)) {
            return InstallStatus.notApplicable(false, 0, 0);
        }

        int managedAffected = 0;
        int fallbackAffected = 0;
        List<WeaponAPI> weapons = ship.getAllWeapons();
        if (weapons != null) {
            for (WeaponAPI weapon : weapons) {
                if (!isAutoloaderAffected(weapon)) continue;
                if (isManagedAutoloaderWeapon(ship, weapon)) managedAffected++;
                else fallbackAffected++;
            }
        }

        // If this ship has no weapon that UMR needs to shield, leave vanilla
        // Autoloader state completely untouched. This is the blacklisted/
        // unmanaged fallback path.
        if (managedAffected <= 0) {
            return InstallStatus.notApplicable(true, managedAffected, fallbackAffected);
        }

        try {
            Object raw = ship.getCustomData().get(MissileAutoloader.MA_DATA_KEY);
            MissileAutoloaderData data;
            boolean dataCreated = false;
            if (raw == null) {
                data = createVanillaData(ship);
                ship.setCustomData(MissileAutoloader.MA_DATA_KEY, data);
                dataCreated = true;
            } else if (raw instanceof MissileAutoloaderData) {
                data = (MissileAutoloaderData) raw;
            } else {
                return InstallStatus.conflict(managedAffected, fallbackAffected,
                        "unexpected_custom_data_type_" + raw.getClass().getName());
            }

            if (data.cooldown instanceof SuppressionTracker) {
                SuppressionTracker tracker = (SuppressionTracker) data.cooldown;
                if (tracker.ship == ship) {
                    return InstallStatus.alreadyInstalled(dataCreated, managedAffected, fallbackAffected,
                            tracker.delegate.getClass().getName());
                }
            }

            TimeoutTracker<WeaponAPI> prior = data.cooldown;
            if (prior == null) prior = new TimeoutTracker<WeaponAPI>();
            String priorClass = prior.getClass().getName();
            data.cooldown = new SuppressionTracker(prior, ship);
            return InstallStatus.installed(dataCreated, managedAffected, fallbackAffected, priorClass);
        } catch (Throwable t) {
            return InstallStatus.conflict(managedAffected, fallbackAffected,
                    "exception_" + t.getClass().getName());
        }
    }

    /** True only for actual live weapons that both systems would otherwise manage. */
    public static boolean isManagedAutoloaderWeapon(ShipAPI ship, WeaponAPI weapon) {
        return UMRCombatEligibility.isManagedLiveWeapon(ship, weapon) && isAutoloaderAffected(weapon);
    }

    public static boolean isAutoloaderAffected(WeaponAPI weapon) {
        try { return MissileAutoloader.isAffected(weapon); }
        catch (Throwable ignored) { return false; }
    }

    /** Test/diagnostic surface: whether UMR's virtual cooldown gate blocks this weapon. */
    public static boolean isSuppressedByUMR(ShipAPI ship, WeaponAPI weapon) {
        if (ship == null || weapon == null) return false;
        try {
            Object raw = ship.getCustomData().get(MissileAutoloader.MA_DATA_KEY);
            if (!(raw instanceof MissileAutoloaderData)) return false;
            TimeoutTracker<WeaponAPI> cooldown = ((MissileAutoloaderData) raw).cooldown;
            if (!(cooldown instanceof SuppressionTracker)) return false;
            SuppressionTracker tracker = (SuppressionTracker) cooldown;
            return tracker.ship == ship && tracker.isVirtuallySuppressed(weapon);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean hasAutoloader(ShipAPI ship) {
        if (ship == null) return false;
        try {
            ShipVariantAPI variant = ship.getVariant();
            return variant != null && variant.hasHullMod(HullMods.MISSILE_AUTOLOADER);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static MissileAutoloaderData createVanillaData(ShipAPI ship) {
        MissileAutoloaderData data = new MissileAutoloaderData();
        ReloadCapacityData cap = MissileAutoloader.getCapacityData(ship);
        if (cap != null) {
            data.opLeft = cap.capacity;
        } else {
            data.showExhaustedStatus = 0f;
        }
        return data;
    }

    interface SuppressionDecider {
        boolean shouldSuppress(ShipAPI ship, WeaponAPI weapon);
    }

    private static final SuppressionDecider PRODUCTION_DECIDER = new SuppressionDecider() {
        @Override
        public boolean shouldSuppress(ShipAPI ship, WeaponAPI weapon) {
            return isManagedAutoloaderWeapon(ship, weapon);
        }
    };

    /**
     * Delegating wrapper: no existing vanilla or third-party timeout state is
     * copied, discarded, or reinterpreted. Only contains() gains UMR's virtual
     * managed-weapon gate.
     */
    static final class SuppressionTracker extends TimeoutTracker<WeaponAPI> {
        final TimeoutTracker<WeaponAPI> delegate;
        final ShipAPI ship;
        final SuppressionDecider decider;

        SuppressionTracker(TimeoutTracker<WeaponAPI> delegate, ShipAPI ship) {
            this(delegate, ship, PRODUCTION_DECIDER);
        }

        SuppressionTracker(TimeoutTracker<WeaponAPI> delegate, ShipAPI ship, SuppressionDecider decider) {
            this.delegate = delegate == null ? new TimeoutTracker<WeaponAPI>() : delegate;
            this.ship = ship;
            this.decider = decider == null ? PRODUCTION_DECIDER : decider;
        }

        boolean isVirtuallySuppressed(WeaponAPI weapon) {
            return decider.shouldSuppress(ship, weapon);
        }

        @Override public void add(WeaponAPI item, float time) { delegate.add(item, time); }
        @Override public void add(WeaponAPI item, float time, float limit) { delegate.add(item, time, limit); }
        @Override public void set(WeaponAPI item, float time) { delegate.set(item, time); }
        @Override public float getRemaining(WeaponAPI item) { return delegate.getRemaining(item); }
        @Override public void remove(WeaponAPI item) { delegate.remove(item); }
        @Override public List<WeaponAPI> getItems() { return delegate.getItems(); }
        @Override public void clear() { delegate.clear(); }
        @Override public void advance(float amount) { delegate.advance(amount); }
        @Override public boolean contains(WeaponAPI item) {
            return isVirtuallySuppressed(item) || delegate.contains(item);
        }
    }

    public static final class InstallStatus {
        public final boolean hasAutoloader;
        public final boolean installed;
        public final boolean alreadyInstalled;
        public final boolean dataCreated;
        public final boolean conflict;
        public final int managedAffected;
        public final int fallbackAffected;
        public final String priorTrackerClass;
        public final String reason;

        private InstallStatus(boolean hasAutoloader, boolean installed, boolean alreadyInstalled,
                              boolean dataCreated, boolean conflict, int managedAffected,
                              int fallbackAffected, String priorTrackerClass, String reason) {
            this.hasAutoloader = hasAutoloader;
            this.installed = installed;
            this.alreadyInstalled = alreadyInstalled;
            this.dataCreated = dataCreated;
            this.conflict = conflict;
            this.managedAffected = managedAffected;
            this.fallbackAffected = fallbackAffected;
            this.priorTrackerClass = priorTrackerClass == null ? "none" : priorTrackerClass;
            this.reason = reason == null ? "none" : reason;
        }

        static InstallStatus notApplicable(boolean hasAutoloader, int managed, int fallback) {
            return new InstallStatus(hasAutoloader, false, false, false, false,
                    managed, fallback, "none", "none");
        }
        static InstallStatus installed(boolean created, int managed, int fallback, String prior) {
            return new InstallStatus(true, true, false, created, false,
                    managed, fallback, prior, "none");
        }
        static InstallStatus alreadyInstalled(boolean created, int managed, int fallback, String prior) {
            return new InstallStatus(true, false, true, created, false,
                    managed, fallback, prior, "none");
        }
        static InstallStatus conflict(int managed, int fallback, String reason) {
            return new InstallStatus(true, false, false, false, true,
                    managed, fallback, "unknown", reason);
        }
    }
}
