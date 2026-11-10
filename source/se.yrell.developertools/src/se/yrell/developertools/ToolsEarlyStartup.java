package se.yrell.developertools;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

import se.yrell.developertools.icons.IconPropertyButtonInstaller;
import se.yrell.developertools.midtier.LegacyMidTierShortcutCleanup;
import se.yrell.developertools.icons.IconCatalog;
import se.yrell.developertools.keepalive.KeepAliveService;
import se.yrell.developertools.inspector.ObjectInsightViewSupport;

/**
 * Forces early bundle activation and installs UI/runtime fallbacks.
 */
public class ToolsEarlyStartup implements IStartup {
    @Override
    public void earlyStartup() {
        try {
            Log.info("Startup initialized (Yrell Developer Tools 0.1.65)");
            if (Boolean.getBoolean("se.yrell.developertools.fastFormsAgent.active")) {
                String agentVersion = System.getProperty("se.yrell.developertools.agent.version", "unknown");
                if (!"0.1.65".equals(agentVersion)) {
                    Log.warn("Yrell Developer Tools 0.1.65 has an older/unrecognized -javaagent ("
                            + agentVersion + "). Replace its devstudio.ini -javaagent JAR path"
                            + " with se.yrell.developertools_0.1.65.jar, then restart with -clean.");
                } else {
                    Log.info("Message Suppressor agent version verified: " + agentVersion);
                }
            }
            // One-time, non-blocking removal of USER bindings left by 0.1.59/0.1.60.
            LegacyMidTierShortcutCleanup.schedule();
            SwtSuffixFilter.install();
            // Post-save suffix cleanup was removed in 0.1.17. The runtime/weaving hooks
            // and SWT name filter handle __c before values are saved.
            PlatformUI.getWorkbench();
            IconPropertyButtonInstaller.install();
            IconCatalog.preloadAsync();
            NewFieldSuffixMonitor.install();
            KeepAliveService.getInstance().start();
            ObjectInsightViewSupport.openIfEnabledAsync();
        } catch (Throwable t) {
            Log.error("Startup failed", t);
        }
    }
}
