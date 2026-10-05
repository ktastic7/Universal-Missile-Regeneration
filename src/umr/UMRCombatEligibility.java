package umr;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

/** Shared live-combat eligibility rules for UMR production regeneration and integrations. */
public final class UMRCombatEligibility {
    private UMRCombatEligibility() {}

    public static boolean isEligibleShip(ShipAPI ship) {
        return ship != null &&
                !ship.isFighter() &&
                !ship.isDrone() &&
                !ship.isHulk() &&
                !ship.isStationModule();
    }

    public static boolean isEligibleLiveWeapon(WeaponAPI weapon, UMRProductionRegistry.Entry entry) {
        if (weapon == null || entry == null ||
                weapon.getType() != WeaponAPI.WeaponType.MISSILE || !weapon.usesAmmo()) return false;

        WeaponSpecAPI original = weapon.getOriginalSpec();
        if (original == null || !original.usesAmmo() || original.getMaxAmmo() <= 0) return false;
        if (original.getAmmoPerSecond() > 0f) return false;
        if (original.getMaxAmmo() != entry.baseMaxAmmo) return false;
        if (Math.max(1, original.getBurstSize()) != entry.baseBurstSize) return false;

        WeaponSlotAPI slot = weapon.getSlot();
        return slot == null || (!slot.isSystemSlot() && !slot.isDecorative());
    }

    public static UMRProductionRegistry.Entry getManagedEntry(ShipAPI ship, WeaponAPI weapon) {
        if (!isEligibleShip(ship) || weapon == null) return null;
        UMRProductionRegistry.Entry entry = UMRProductionRegistry.get(weapon.getId());
        return isEligibleLiveWeapon(weapon, entry) ? entry : null;
    }

    public static boolean isManagedLiveWeapon(ShipAPI ship, WeaponAPI weapon) {
        return getManagedEntry(ship, weapon) != null;
    }
}
