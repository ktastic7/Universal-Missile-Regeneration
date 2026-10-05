package umr.luna;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import umr.UMRAutoloaderNPCGeneration;
import umr.UMRAutoloaderUIVisibility;
import umr.UMRLogger;

/** Exact LunaLib 2.0.5 settings bridge, loaded only after the Luna mod gate. */
public final class UMRLunaBridge implements LunaSettingsListener {
    public static final String UMR_MOD_ID = "universal_missile_regeneration";
    public static final String HIDE_AUTOLOADER_FIELD_ID = "umr_hide_missile_autoloader";
    public static final boolean DEFAULT_HIDE_AUTOLOADER = true;
    public static final String SUPPRESS_NPC_AUTOLOADER_FIELD_ID = "umr_suppress_npc_random_missile_autoloader";
    public static final boolean DEFAULT_SUPPRESS_NPC_AUTOLOADER = true;

    private UMRLunaBridge() {
    }

    public static void initialize() {
        applyCurrentValues("lunalib_initial_load");
        if (!LunaSettings.hasSettingsListenerOfClass(UMRLunaBridge.class)) {
            LunaSettings.addSettingsListener(new UMRLunaBridge());
            UMRLogger.get().info("[UMR][PHASE7B_LUNA] present=true listenerRegistered=true");
        } else {
            UMRLogger.get().info("[UMR][PHASE7B_LUNA] present=true listenerRegistered=false reason=already_registered");
        }
    }

    @Override
    public void settingsChanged(String modID) {
        if (UMR_MOD_ID.equals(modID)) {
            applyCurrentValues("lunalib_settings_changed");
        }
    }

    private static void applyCurrentValues(String source) {
        Boolean uiValue = LunaSettings.getBoolean(UMR_MOD_ID, HIDE_AUTOLOADER_FIELD_ID);
        boolean uiResolved = uiValue == null ? DEFAULT_HIDE_AUTOLOADER : uiValue.booleanValue();
        UMRAutoloaderUIVisibility.setHideFromOrdinaryPicker(uiResolved,
                uiValue == null ? source + "_missing_default" : source);
        UMRLogger.get().info("[UMR][PHASE7B_LUNA] present=true result=PASS field=" +
                HIDE_AUTOLOADER_FIELD_ID + " raw=" + uiValue + " resolved=" + uiResolved);

        Boolean npcValue = LunaSettings.getBoolean(UMR_MOD_ID, SUPPRESS_NPC_AUTOLOADER_FIELD_ID);
        boolean npcResolved = npcValue == null ? DEFAULT_SUPPRESS_NPC_AUTOLOADER : npcValue.booleanValue();
        UMRAutoloaderNPCGeneration.setSuppressRandomNpcAutoloader(npcResolved,
                npcValue == null ? source + "_missing_default" : source);
        UMRLogger.get().info("[UMR][PHASE7C_LUNA] present=true result=PASS field=" +
                SUPPRESS_NPC_AUTOLOADER_FIELD_ID + " raw=" + npcValue + " resolved=" + npcResolved);
    }
}
