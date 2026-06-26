package se.yrell.developertools;

import org.eclipse.ui.IStartup;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;

import se.yrell.developertools.icons.IconPropertyButtonInstaller;
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
            Log.info("Startup initialized");
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
