package umr;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetInflater;
import com.fs.starfarer.api.campaign.listeners.FleetInflationListener;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.DefaultFleetInflater;
import com.fs.starfarer.api.impl.campaign.ids.HullMods;
import com.fs.starfarer.api.loading.VariantSource;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Phase 7C NPC autofit convenience policy.
 *
 * Uses RC8's post-inflation FleetInflationListener hook. This is deliberately
 * conservative: UMR removes Missile Autoloader only when it can prove that the
 * current NPC REFIT variant gained a removable copy that was absent from the
 * recorded original/goal variant. Built-ins, permanent mods, S-mods, authored
 * originals, stations, player fleets, custom/non-default inflaters, and
 * ambiguous provenance are preserved.
 */
public final class UMRAutoloaderNPCGeneration implements FleetInflationListener {
    private static final UMRAutoloaderNPCGeneration INSTANCE = new UMRAutoloaderNPCGeneration();

    private static volatile boolean suppressRandomNpcAutoloader = true;
    private static volatile boolean listenerRegistered;
    private static long callbackCount;
    private static boolean hookLogged;

    private UMRAutoloaderNPCGeneration() {
    }

    public static synchronized void register() {
        if (Global.getSector() == null) {
            listenerRegistered = false;
            UMRLogger.get().warn("[UMR][PHASE7C_NPC_AUTOLOADER_LISTENER] result=FAIL reason=sector_unavailable");
            return;
        }

        try {
            ListenerManagerAPI manager = Global.getSector().getListenerManager();
            boolean already = manager.hasListenerOfClass(UMRAutoloaderNPCGeneration.class);
            if (!already) {
                manager.addListener(INSTANCE, true);
            }
            listenerRegistered = true;
            UMRLogger.get().info("[UMR][PHASE7C_NPC_AUTOLOADER_LISTENER] result=PASS registered=true" +
                    " transient=true alreadyPresent=" + already +
                    " suppressRandomNpcAutoloader=" + suppressRandomNpcAutoloader);
        } catch (Throwable t) {
            listenerRegistered = false;
            UMRLogger.get().warn("[UMR][PHASE7C_NPC_AUTOLOADER_LISTENER] result=FAIL reason=registration_exception" +
                    " exception=" + t.getClass().getName() + " message=" + safe(t.getMessage()));
        }
    }

    public static void setSuppressRandomNpcAutoloader(boolean suppress, String source) {
        suppressRandomNpcAutoloader = suppress;
        UMRLogger.get().info("[UMR][PHASE7C_NPC_AUTOLOADER_SETTING] suppressRandomNpcAutoloader=" + suppress +
                " source=" + safe(source) + " listenerRegistered=" + listenerRegistered);
    }

    public static boolean isSuppressRandomNpcAutoloader() {
        return suppressRandomNpcAutoloader;
    }

    public static boolean isListenerRegistered() {
        return listenerRegistered;
    }

    public static long getCallbackCount() {
        return callbackCount;
    }

    @Override
    public void reportFleetInflated(CampaignFleetAPI fleet, FleetInflater inflater) {
        callbackCount++;

        if (fleet == null || inflater == null) return;
        boolean defaultInflater = inflater instanceof DefaultFleetInflater;
        if (!shouldProcessFleet(suppressRandomNpcAutoloader, fleet.isPlayerFleet(), defaultInflater)) {
            return;
        }

        if (!hookLogged) {
            hookLogged = true;
            UMRLogger.get().info("[UMR][PHASE7C_NPC_AUTOLOADER_HOOK] result=PASS callback=" + callbackCount +
                    " fleet=" + safe(fleet.getName()) +
                    " faction=" + safe(fleet.getFaction() == null ? null : fleet.getFaction().getId()) +
                    " inflater=" + inflater.getClass().getName());
        }

        int autoloaderSeen = 0;
        int removed = 0;
        int preservedAuthored = 0;
        int preservedProtected = 0;
        int preservedAmbiguous = 0;
        int skippedStations = 0;
        int errors = 0;

        List<FleetMemberAPI> members;
        try {
            members = fleet.getFleetData().getMembersListCopy();
        } catch (Throwable t) {
            UMRLogger.get().warn("[UMR][PHASE7C_NPC_AUTOLOADER_SUMMARY] result=FAIL reason=member_list_exception" +
                    " fleet=" + safe(fleet.getName()) +
                    " exception=" + t.getClass().getName() + " message=" + safe(t.getMessage()));
            return;
        }

        for (FleetMemberAPI member : members) {
            if (member == null) continue;
            if (member.isStation()) {
                ShipVariantAPI stationVariant = member.getVariant();
                if (stationVariant != null && stationVariant.hasHullMod(HullMods.MISSILE_AUTOLOADER)) {
                    autoloaderSeen++;
                    skippedStations++;
                }
                continue;
            }

            ShipVariantAPI variant = member.getVariant();
            if (variant == null || !variant.hasHullMod(HullMods.MISSILE_AUTOLOADER)) continue;
            autoloaderSeen++;

            try {
                Decision decision = classifyVariant(variant);
                if (decision == Decision.REMOVE_PROVEN_GENERATED) {
                    variant.removeMod(HullMods.MISSILE_AUTOLOADER);
                    member.updateStats();
                    removed++;
                    UMRLogger.get().info("[UMR][PHASE7C_NPC_AUTOLOADER_REMOVED] result=PASS" +
                            " fleet=" + safe(fleet.getName()) +
                            " member=" + safe(member.getShipName()) +
                            " hull=" + safe(member.getHullId()) +
                            " variant=" + safe(variant.getHullVariantId()) +
                            " originalVariant=" + safe(variant.getOriginalVariant()) +
                            " reason=proven_post_inflation_non_authored_copy");
                } else if (decision == Decision.PRESERVE_AUTHORED_ORIGINAL) {
                    preservedAuthored++;
                } else if (decision == Decision.PRESERVE_PROTECTED) {
                    preservedProtected++;
                } else if (decision == Decision.PRESERVE_AMBIGUOUS) {
                    preservedAmbiguous++;
                }
            } catch (Throwable t) {
                errors++;
                UMRLogger.get().warn("[UMR][PHASE7C_NPC_AUTOLOADER_MEMBER] result=FAIL" +
                        " fleet=" + safe(fleet.getName()) +
                        " member=" + safe(member.getShipName()) +
                        " hull=" + safe(member.getHullId()) +
                        " exception=" + t.getClass().getName() + " message=" + safe(t.getMessage()));
            }
        }

        if (removed > 0) {
            try {
                fleet.getFleetData().setSyncNeeded();
                fleet.getFleetData().syncIfNeeded();
            } catch (Throwable t) {
                errors++;
                UMRLogger.get().warn("[UMR][PHASE7C_NPC_AUTOLOADER_SYNC] result=FAIL" +
                        " fleet=" + safe(fleet.getName()) +
                        " exception=" + t.getClass().getName() + " message=" + safe(t.getMessage()));
            }
        }

        if (autoloaderSeen > 0 || errors > 0) {
            UMRLogger.get().info("[UMR][PHASE7C_NPC_AUTOLOADER_SUMMARY] result=" + (errors == 0 ? "PASS" : "FAIL") +
                    " fleet=" + safe(fleet.getName()) +
                    " faction=" + safe(fleet.getFaction() == null ? null : fleet.getFaction().getId()) +
                    " autoloaderSeen=" + autoloaderSeen +
                    " removed=" + removed +
                    " preservedAuthored=" + preservedAuthored +
                    " preservedProtected=" + preservedProtected +
                    " preservedAmbiguous=" + preservedAmbiguous +
                    " skippedStations=" + skippedStations +
                    " errors=" + errors);
        }
    }

    static boolean shouldProcessFleet(boolean suppress, boolean playerFleet, boolean defaultInflater) {
        return suppress && !playerFleet && defaultInflater;
    }

    static Decision classifyVariant(ShipVariantAPI variant) {
        if (variant == null || !variant.hasHullMod(HullMods.MISSILE_AUTOLOADER)) {
            return Decision.NONE;
        }

        boolean nonBuiltIn = contains(variant.getNonBuiltInHullmods(), HullMods.MISSILE_AUTOLOADER);
        boolean permanent = contains(variant.getPermaMods(), HullMods.MISSILE_AUTOLOADER);
        boolean sMod = contains(variant.getSMods(), HullMods.MISSILE_AUTOLOADER);

        if (!nonBuiltIn || permanent || sMod || variant.getHullSpec().isBuiltInMod(HullMods.MISSILE_AUTOLOADER)) {
            return Decision.PRESERVE_PROTECTED;
        }

        if (variant.getSource() != VariantSource.REFIT) {
            return Decision.PRESERVE_AMBIGUOUS;
        }

        String originalId = variant.getOriginalVariant();
        if (originalId == null || originalId.trim().isEmpty()) {
            return Decision.PRESERVE_AMBIGUOUS;
        }

        ShipVariantAPI original;
        try {
            original = Global.getSettings().getVariant(originalId);
        } catch (Throwable t) {
            return Decision.PRESERVE_AMBIGUOUS;
        }
        if (original == null) {
            return Decision.PRESERVE_AMBIGUOUS;
        }

        if (original.hasHullMod(HullMods.MISSILE_AUTOLOADER)) {
            return Decision.PRESERVE_AUTHORED_ORIGINAL;
        }

        return Decision.REMOVE_PROVEN_GENERATED;
    }

    static Decision classifyFacts(boolean hasAutoloader,
                                  boolean nonBuiltIn,
                                  boolean permanent,
                                  boolean sMod,
                                  boolean hullBuiltIn,
                                  boolean refitSource,
                                  boolean hasOriginalId,
                                  boolean originalResolved,
                                  boolean originalHasAutoloader) {
        if (!hasAutoloader) return Decision.NONE;
        if (!nonBuiltIn || permanent || sMod || hullBuiltIn) return Decision.PRESERVE_PROTECTED;
        if (!refitSource || !hasOriginalId || !originalResolved) return Decision.PRESERVE_AMBIGUOUS;
        if (originalHasAutoloader) return Decision.PRESERVE_AUTHORED_ORIGINAL;
        return Decision.REMOVE_PROVEN_GENERATED;
    }

    private static boolean contains(Collection<String> values, String id) {
        return values != null && values.contains(id);
    }

    private static String safe(String value) {
        if (value == null) return "null";
        return value.replace('\n', ' ').replace('\r', ' ');
    }

    enum Decision {
        NONE,
        REMOVE_PROVEN_GENERATED,
        PRESERVE_AUTHORED_ORIGINAL,
        PRESERVE_PROTECTED,
        PRESERVE_AMBIGUOUS
    }
}
