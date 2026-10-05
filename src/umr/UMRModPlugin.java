package umr;

import com.fs.starfarer.api.BaseModPlugin;

/** Universal Missile Regeneration broad eligible third-party enrollment/field-telemetry build. */
public class UMRModPlugin extends BaseModPlugin {
    @Override
    public void onApplicationLoad() throws Exception {
        UMRConfig.initialize();
        UMRBuildInfo.initialize();
        UMRLogger.initialize();
        UMRConfig.logLoadedConfiguration();
        UMRAutoloaderUIVisibility.initialize();
        UMRLunaIntegration.initialize();
        UMRBlacklist.initialize();
        UMROverrides.initialize();
        UMRThirdPartyConfig.initialize();
        UMRPhase8CompatibilityAudit.initialize();
        UMRLogger.get().info("[UMR][LIFECYCLE] onApplicationLoad starting phase=" + UMRBuildInfo.getPhase() +
                " version=" + UMRBuildInfo.getVersion() + " core=" + UMRBuildInfo.getCore());
        UMRProductionRegistry.initialize();
        UMRLogger.get().info("[UMR][SESSION_REGISTRY] " + UMRProductionRegistry.diagnosticSummary());
        UMRLogger.get().info("[UMR][SESSION_CONFIGURATION] localBlacklistKnown=" + UMRBlacklist.size() +
                " localBlacklistUnknown=" + UMRBlacklist.getUnknownIds().size() +
                " localOverrideRows=" + UMROverrides.size() +
                " localOverrideEffective=" + UMROverrides.effectiveSize() +
                " localOverrideUnknown=" + UMROverrides.getUnknownIds().size() +
                " thirdPartyBlacklist=" + UMRThirdPartyConfig.blacklistSize() +
                " thirdPartyOverrides=" + UMRThirdPartyConfig.overrideSize() +
                " thirdPartyConflicts=" + (UMRThirdPartyConfig.getBlacklistConflicts() + UMRThirdPartyConfig.getOverrideConflicts()) +
                " managedEligible=" + UMRProductionRegistry.size());
        UMRLogger.get().info("[UMR][LIFECYCLE] onApplicationLoad completed phase=" + UMRBuildInfo.getPhase() + " registryReady=" +
                UMRProductionRegistry.size() + " blacklistKnown=" + UMRBlacklist.size() +
                " blacklistUnknown=" + UMRBlacklist.getUnknownIds().size() +
                " overrideRows=" + UMROverrides.size() +
                " overrideEffectiveRows=" + UMROverrides.effectiveSize() +
                " overrideUnknown=" + UMROverrides.getUnknownIds().size() +
                " thirdPartyBlacklist=" + UMRThirdPartyConfig.blacklistSize() +
                " thirdPartyOverrides=" + UMRThirdPartyConfig.overrideSize() +
                " autoloaderPickerWrapper=" + UMRAutoloaderUIVisibility.isWrapperInstalled() +
                " hideAutoloaderPicker=" + UMRAutoloaderUIVisibility.isHideFromOrdinaryPicker() +
                " suppressNpcRandomAutoloader=" + UMRAutoloaderNPCGeneration.isSuppressRandomNpcAutoloader() +
                " phase8Audit=" + UMRPhase8CompatibilityAudit.diagnosticSummary() +
                " gameplayMutationInCombat=true thirdPartyGameplay=" + UMRConfig.isThirdPartyGameplayEnabled());
    }

    @Override
    public void onGameLoad(boolean newGame) {
        UMRAutoloaderNPCGeneration.register();
        UMRLogger.get().info("[UMR][LIFECYCLE] onGameLoad newGame=" + newGame +
                " version=" + UMRBuildInfo.getVersion() +
                " phase=" + UMRBuildInfo.getPhase() +
                " core=" + UMRBuildInfo.getCore() + " registryReady=" + UMRProductionRegistry.size() +
                " blacklistKnown=" + UMRBlacklist.size() +
                " overrideEffectiveRows=" + UMROverrides.effectiveSize() +
                " thirdPartyBlacklist=" + UMRThirdPartyConfig.blacklistSize() +
                " thirdPartyOverrides=" + UMRThirdPartyConfig.overrideSize() +
                " autoloaderPickerWrapper=" + UMRAutoloaderUIVisibility.isWrapperInstalled() +
                " hideAutoloaderPicker=" + UMRAutoloaderUIVisibility.isHideFromOrdinaryPicker() +
                " suppressNpcRandomAutoloader=" + UMRAutoloaderNPCGeneration.isSuppressRandomNpcAutoloader() +
                " npcGenerationListener=" + UMRAutoloaderNPCGeneration.isListenerRegistered() +
                " phase8Audit=" + UMRPhase8CompatibilityAudit.diagnosticSummary() +
                " combatRegeneration=true thirdPartyGameplay=" + UMRConfig.isThirdPartyGameplayEnabled() +
                " loggingLevel=" +
                UMRConfig.getLoggingLevel());
    }
}
