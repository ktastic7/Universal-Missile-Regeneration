package umr;

import com.fs.starfarer.api.Global;
import umr.luna.UMRLunaBridge;

/** Optional LunaLib bootstrap. Luna-linked API types are isolated in the bridge. */
public final class UMRLunaIntegration {
    public static final String LUNALIB_MOD_ID = "lunalib";

    private UMRLunaIntegration() {
    }

    public static void initialize() {
        boolean lunaEnabled = false;
        try {
            lunaEnabled = Global.getSettings().getModManager().isModEnabled(LUNALIB_MOD_ID);
        } catch (Throwable t) {
            UMRLogger.get().warn("[UMR][PHASE7B_LUNA] result=FALLBACK reason=mod_manager_check_failed" +
                    " exception=" + t.getClass().getName());
        }

        if (!lunaEnabled) {
            UMRAutoloaderUIVisibility.setHideFromOrdinaryPicker(true, "phase7b_default_lunalib_absent");
            UMRAutoloaderNPCGeneration.setSuppressRandomNpcAutoloader(true, "phase7c_default_lunalib_absent");
            UMRLogger.get().info("[UMR][PHASE7B_LUNA] present=false result=PASS" +
                    " settingSource=default hideMissileAutoloader=true");
            UMRLogger.get().info("[UMR][PHASE7C_LUNA] present=false result=PASS" +
                    " settingSource=default suppressNpcRandomMissileAutoloader=true");
            return;
        }

        try {
            UMRLunaBridge.initialize();
        } catch (Throwable t) {
            // Optional integration must not make UMR unloadable if Luna is broken/incompatible.
            UMRAutoloaderUIVisibility.setHideFromOrdinaryPicker(true, "phase7b_default_lunalib_bridge_failure");
            UMRAutoloaderNPCGeneration.setSuppressRandomNpcAutoloader(true, "phase7c_default_lunalib_bridge_failure");
            UMRLogger.get().warn("[UMR][PHASE7B_LUNA] present=true result=FALLBACK reason=bridge_failure" +
                    " exception=" + t.getClass().getName() + " message=" + safe(t.getMessage()) +
                    " hideMissileAutoloader=true");
            UMRLogger.get().warn("[UMR][PHASE7C_LUNA] present=true result=FALLBACK reason=bridge_failure" +
                    " exception=" + t.getClass().getName() + " message=" + safe(t.getMessage()) +
                    " suppressNpcRandomMissileAutoloader=true");
        }
    }

    private static String safe(String value) {
        if (value == null) return "null";
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
