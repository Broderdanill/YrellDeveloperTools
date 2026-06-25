package se.yrell.developertools;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;

public final class Log {
    private Log() {
    }

    public static void info(String message) {
        log(IStatus.INFO, message, null);
    }

    public static void warn(String message) {
        log(IStatus.WARNING, message, null);
    }

    public static void error(String message, Throwable t) {
        log(IStatus.ERROR, message, t);
    }

    private static void log(int severity, String message, Throwable t) {
        try {
            Bundle bundle = Platform.getBundle(ToolsConstants.PLUGIN_ID);
            if (bundle != null) {
                Platform.getLog(bundle).log(new Status(severity, ToolsConstants.PLUGIN_ID, message, t));
                return;
            }
        } catch (Throwable ignored) {
            // Fall back to stdout.
        }
        String prefix = severity == IStatus.ERROR ? "ERROR" : severity == IStatus.WARNING ? "WARN" : "INFO";
        System.out.println("[Yrell Developer Tools] " + prefix + " " + message);
        if (t != null) {
            t.printStackTrace(System.out);
        }
    }
}
