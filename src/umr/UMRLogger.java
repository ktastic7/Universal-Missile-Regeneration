package umr;

import com.fs.starfarer.api.Global;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.RollingFileAppender;

/** Dedicated development logger for Universal Missile Regeneration. */
public final class UMRLogger {
    private static final String LOGGER_NAME = "umr.dedicated";
    private static final String APPENDER_NAME = "UMR_ROLLING_FILE";
    private static final String FILE_NAME = "UniversalMissileRegeneration.log";
    private static final String LOG_DIR_PROPERTY = "com.fs.starfarer.settings.paths.logs";

    private static Logger dedicated;
    private static String resolvedPath;

    private UMRLogger() {}

    public static synchronized void initialize() {
        if (dedicated != null) return;

        Logger main = Global.getLogger(UMRLogger.class);
        try {
            String logPath = resolveLogPath();
            main.info("[UMR] Dedicated log candidate path: " + logPath);

            Logger logger = Logger.getLogger(LOGGER_NAME);
            logger.setAdditivity(false);
            logger.setLevel(UMRConfig.LOG_OFF.equals(UMRConfig.getLoggingLevel()) ? Level.OFF : Level.INFO);
            logger.removeAppender(APPENDER_NAME);

            PatternLayout layout = new PatternLayout("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p [%t] %m%n");
            RollingFileAppender appender = new RollingFileAppender(layout, logPath, true);
            appender.setName(APPENDER_NAME);
            appender.setMaxFileSize("10MB");
            appender.setMaxBackupIndex(5);
            logger.addAppender(appender);

            dedicated = logger;
            resolvedPath = logPath;

            if (!UMRConfig.LOG_OFF.equals(UMRConfig.getLoggingLevel())) {
                dedicated.info("============================================================");
                dedicated.info("[UMR][SESSION_START] version=" + UMRBuildInfo.getVersion() +
                        " settingsVersion=" + UMRBuildInfo.getSettingsVersion() +
                        " identityConsistent=" + UMRBuildInfo.isIdentityConsistent() +
                        " phase=" + UMRBuildInfo.getPhase() +
                        " core=" + UMRBuildInfo.getCore() +
                        " gameVersion=" + UMRBuildInfo.getGameVersion() +
                        " javaRuntime=" + UMRBuildInfo.getJavaVersion() +
                        " classTarget=8 dedicatedLog=true");
                if (!UMRBuildInfo.isIdentityConsistent()) {
                    dedicated.warn("[UMR][BUILD_IDENTITY_MISMATCH] modInfoVersion=" + UMRBuildInfo.getVersion() +
                            " settingsVersion=" + UMRBuildInfo.getSettingsVersion() +
                            " action=diagnostic_warning_only");
                }
                dedicated.info("[UMR][LOG_PATH] " + resolvedPath);
                dedicated.info("[UMR][ENABLED_MODS] count=" + UMRBuildInfo.getEnabledModCount() +
                        " mods=" + UMRBuildInfo.enabledModsCompact());
                dedicated.info("[UMR][GAMEPLAY] productionRegen=true vanillaEligibleOnly=false thirdPartyGameplay=" +
                        UMRConfig.isThirdPartyGameplayEnabled() +
                        " reportOnly=false combatPlugin=true gameplayMutation=true");
                dedicated.info("[UMR][LOGGING] level=" + UMRConfig.getLoggingLevel() +
                        " OFF=disabled SUMMARY=bounded VERBOSE=events TRACE=events+progress");
            }
            main.info("[UMR] Dedicated development log initialized: " + resolvedPath +
                    " level=" + UMRConfig.getLoggingLevel());
        } catch (Throwable t) {
            dedicated = null;
            resolvedPath = null;
            main.error("[UMR] Dedicated log initialization failed; falling back to the main Starsector logger.", t);
        }
    }

    public static Logger get() {
        return dedicated != null ? dedicated : Global.getLogger(UMRLogger.class);
    }

    public static boolean verbose() {
        return UMRConfig.isVerboseEnabled();
    }

    public static boolean trace() {
        return UMRConfig.isTraceEnabled();
    }

    public static String getResolvedPath() {
        return resolvedPath;
    }

    private static String resolveLogPath() {
        String configured = System.getProperty(LOG_DIR_PROPERTY);
        if (configured != null && !configured.trim().isEmpty()) {
            return joinPath(configured.trim(), FILE_NAME);
        }
        String userDir = System.getProperty("user.dir");
        if (userDir == null || userDir.trim().isEmpty()) return FILE_NAME;
        return joinPath(userDir.trim(), FILE_NAME);
    }

    private static String joinPath(String dir, String file) {
        if (dir.endsWith("/") || dir.endsWith("\\")) return dir + file;
        String separator = dir.indexOf('\\') >= 0 ? "\\" : "/";
        return dir + separator + file;
    }
}
