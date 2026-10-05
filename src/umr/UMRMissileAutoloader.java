package umr;

import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.hullmods.MissileAutoloader;

/**
 * Vanilla Missile Autoloader with one Phase 7B UI-only override.
 * All gameplay, applicability, OP, tooltip, S-mod, and combat behavior is
 * inherited directly from the exact RC8 MissileAutoloader implementation.
 */
public class UMRMissileAutoloader extends MissileAutoloader {
    @Override
    public boolean showInRefitScreenModPickerFor(ShipAPI ship) {
        if (UMRAutoloaderUIVisibility.isHideFromOrdinaryPicker()) {
            return false;
        }
        return super.showInRefitScreenModPickerFor(ship);
    }
}
