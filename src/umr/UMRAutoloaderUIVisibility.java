package umr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.HullModEffect;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.impl.hullmods.MissileAutoloader;
import com.fs.starfarer.api.loading.HullModSpecAPI;

/**
 * Phase 7B player-refit visibility policy for vanilla Missile Autoloader.
 *
 * The RC8 HullModEffect API exposes showInRefitScreenModPickerFor(), which is
 * the narrow UI-only hook required here. UMR therefore replaces only the
 * effect class with a subclass of vanilla MissileAutoloader; the subclass
 * inherits all gameplay behavior unchanged and overrides only the picker
 * visibility method.
 *
 * UMR deliberately does not use HullModSpecAPI.setHidden()/setHiddenEverywhere()
 * because those flags are consulted outside the refit picker (for example by
 * hullmod-drop/Codex-related code) and would exceed Phase 7B's scope.
 */
public final class UMRAutoloaderUIVisibility {
    static final String VANILLA_EFFECT_CLASS = MissileAutoloader.class.getName();
    static final String UMR_EFFECT_CLASS = UMRMissileAutoloader.class.getName();

    private static volatile boolean hideFromOrdinaryPicker = true;
    private static volatile boolean wrapperInstalled;
    private static String originalEffectClass;

    private UMRAutoloaderUIVisibility() {
    }

    public static synchronized void initialize() {
        HullModSpecAPI spec;
        try {
            spec = Global.getSettings().getHullModSpec(HullMods.MISSILE_AUTOLOADER);
        } catch (Throwable t) {
            wrapperInstalled = false;
            UMRLogger.get().warn("[UMR][PHASE7B_AUTOLOADER_UI] result=FAIL reason=spec_lookup_exception" +
                    " exception=" + t.getClass().getName() + " message=" + safe(t.getMessage()));
            return;
        }

        if (spec == null) {
            wrapperInstalled = false;
            UMRLogger.get().warn("[UMR][PHASE7B_AUTOLOADER_UI] result=FAIL reason=spec_missing" +
                    " hullmod=" + HullMods.MISSILE_AUTOLOADER);
            return;
        }

        boolean hiddenBefore = spec.isHidden();
        boolean hiddenEverywhereBefore = spec.isHiddenEverywhere();
        boolean alwaysUnlockedBefore = spec.isAlwaysUnlocked();
        InstallResult result = installForSpec(spec);

        UMRLogger.get().info("[UMR][PHASE7B_AUTOLOADER_UI] result=" + (result.installed ? "PASS" : "FAIL") +
                " hullmod=" + HullMods.MISSILE_AUTOLOADER +
                " originalEffect=" + safe(result.originalEffectClass) +
                " activeEffect=" + safe(result.activeEffectClass) +
                " wrapperInstalled=" + result.installed +
                " hideFromOrdinaryPicker=" + hideFromOrdinaryPicker +
                " hiddenBefore=" + hiddenBefore +
                " hiddenAfter=" + spec.isHidden() +
                " hiddenEverywhereBefore=" + hiddenEverywhereBefore +
                " hiddenEverywhereAfter=" + spec.isHiddenEverywhere() +
                " alwaysUnlockedBefore=" + alwaysUnlockedBefore +
                " alwaysUnlockedAfter=" + spec.isAlwaysUnlocked() +
                " reason=" + result.reason);
    }

    /**
     * Isolated for static harness testing with a proxy HullModSpecAPI.
     * This method intentionally touches only effectClass/getEffect.
     */
    static synchronized InstallResult installForSpec(HullModSpecAPI spec) {
        if (spec == null) {
            wrapperInstalled = false;
            return new InstallResult(false, null, null, "spec_missing");
        }

        String before = spec.getEffectClass();
        originalEffectClass = before;

        if (UMR_EFFECT_CLASS.equals(before)) {
            HullModEffect existing = spec.getEffect();
            boolean active = existing instanceof UMRMissileAutoloader;
            wrapperInstalled = active;
            return new InstallResult(active, before,
                    existing == null ? null : existing.getClass().getName(),
                    active ? "already_wrapped" : "effect_instance_mismatch");
        }

        // Fail soft rather than overriding another mod's custom Autoloader effect.
        if (!VANILLA_EFFECT_CLASS.equals(before)) {
            wrapperInstalled = false;
            return new InstallResult(false, before, null, "unexpected_effect_class");
        }

        spec.setEffectClass(UMR_EFFECT_CLASS);
        HullModEffect activeEffect = spec.getEffect();
        boolean active = activeEffect instanceof UMRMissileAutoloader;
        String activeClass = activeEffect == null ? null : activeEffect.getClass().getName();

        if (!active) {
            // Keep metadata internally consistent if RC8 has already cached a different effect.
            spec.setEffectClass(before);
            wrapperInstalled = false;
            return new InstallResult(false, before, activeClass, "effect_instance_mismatch_reverted");
        }

        wrapperInstalled = true;
        return new InstallResult(true, before, activeClass, "wrapper_installed");
    }

    public static void setHideFromOrdinaryPicker(boolean hide, String source) {
        hideFromOrdinaryPicker = hide;
        UMRLogger.get().info("[UMR][PHASE7B_AUTOLOADER_UI_SETTING] hideFromOrdinaryPicker=" + hide +
                " source=" + safe(source) + " wrapperInstalled=" + wrapperInstalled);
    }

    public static boolean isHideFromOrdinaryPicker() {
        return hideFromOrdinaryPicker;
    }

    public static boolean isWrapperInstalled() {
        return wrapperInstalled;
    }

    public static String getOriginalEffectClass() {
        return originalEffectClass;
    }

    private static String safe(String value) {
        if (value == null) return "null";
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    static final class InstallResult {
        final boolean installed;
        final String originalEffectClass;
        final String activeEffectClass;
        final String reason;

        InstallResult(boolean installed, String originalEffectClass, String activeEffectClass, String reason) {
            this.installed = installed;
            this.originalEffectClass = originalEffectClass;
            this.activeEffectClass = activeEffectClass;
            this.reason = reason;
        }
    }
}
