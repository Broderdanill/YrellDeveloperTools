package se.yrell.developertools;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.weaving.WeavingHook;

import se.yrell.developertools.internal.weaving.DeveloperStudioToolsWeavingHook;
import se.yrell.developertools.keepalive.KeepAliveService;

public class ToolsActivator extends AbstractUIPlugin {
    private static ToolsActivator plugin;
    private ServiceRegistration<WeavingHook> weavingHookRegistration;

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        plugin = this;
        startOptionalBundle("com.sun.jna");
        startOptionalBundle("com.sun.jna.platform");
        registerWeavingHook(context);
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        try {
            KeepAliveService.getInstance().stop();
        } catch (Throwable ignored) {
            // Ignore shutdown noise.
        }
        if (weavingHookRegistration != null) {
            try {
                weavingHookRegistration.unregister();
            } catch (Throwable ignored) {
                // Ignore shutdown noise.
            }
            weavingHookRegistration = null;
        }
        plugin = null;
        super.stop(context);
    }

    private void startOptionalBundle(String symbolicName) {
        try {
            Bundle bundle = org.eclipse.core.runtime.Platform.getBundle(symbolicName);
            if (bundle != null && bundle.getState() != Bundle.ACTIVE) {
                bundle.start(Bundle.START_TRANSIENT);
                Log.info("Started optional bundle " + symbolicName + " for SWT Browser/JNA support.");
            }
        } catch (Throwable t) {
            Log.warn("Could not start optional bundle " + symbolicName + ": " + t.getMessage());
        }
    }

    private synchronized void registerWeavingHook(BundleContext context) {
        if (weavingHookRegistration == null) {
            weavingHookRegistration = context.registerService(WeavingHook.class, new DeveloperStudioToolsWeavingHook(), null);
            Log.info("Registered Yrell Developer Tools weaving hook");
        }
    }

    public static ToolsActivator getDefault() {
        return plugin;
    }
}
