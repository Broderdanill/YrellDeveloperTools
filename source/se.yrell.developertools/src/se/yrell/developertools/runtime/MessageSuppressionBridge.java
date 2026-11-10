package se.yrell.developertools.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Bootstrap-safe bridge for BMC's Info presentation methods.
 *
 * This intentionally has no Eclipse, BMC or OSGi dependencies: the same class
 * can be appended to the bootstrap class path by the optional early Java agent,
 * or used through our exported runtime package by Equinox weaving.
 *
 * BMC's actual validation, AR calls, error reporting and save semantics are untouched.
 */
public final class MessageSuppressionBridge {
    private static final int BMC_WARNING = 2; // Info.open(int severity, int code, String text)

    private MessageSuppressionBridge() { }

    public static boolean isValidCodeList(String codes) {
        if (codes == null || codes.trim().isEmpty()) return false;
        String[] values = codes.trim().split("[,;\\s]+", -1);
        for (String value : values) {
            if (value.isEmpty() || !value.matches("[0-9]{1,9}")) return false;
            try {
                if (Integer.parseInt(value) <= 0) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    public static boolean shouldSuppress(int severity, int code) {
        return severity == BMC_WARNING && matchesCode(code);
    }

    private static boolean matchesCode(int code) {
        if (code <= 0) return false;
        try {
            String home = System.getProperty("user.home", "");
            if (home.trim().isEmpty()) return false;
            File file = new File(new File(home, ".yrell-developertools"), "message-suppression.properties");
            if (!file.isFile()) return false;
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            }
            if (!Boolean.parseBoolean(p.getProperty("enabled", "false"))) return false;
            String[] configured = p.getProperty("codes", "").split("[,;\\s]+");
            for (String part : configured) {
                if (!part.isEmpty() && Integer.toString(code).equals(part)) return true;
            }
        } catch (Throwable ignored) {
            // Fail open: BMC must show messages if configuration cannot be read.
        }
        return false;
    }

    /**
     * Whether the UI suppressor is enabled and has a valid configured code list.
     * Used only for *empty* Problems dialogs. If config is unavailable, BMC's
     * normal dialog handling is preserved (fail open).
     */
    private static boolean isSuppressionActive() {
        try {
            String home = System.getProperty("user.home", "");
            if (home.trim().isEmpty()) return false;
            File file = new File(new File(home, ".yrell-developertools"),
                    "message-suppression.properties");
            if (!file.isFile()) return false;
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(file)) {
                p.load(in);
            }
            return Boolean.parseBoolean(p.getProperty("enabled", "false"))
                    && isValidCodeList(p.getProperty("codes", ""));
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Called only when BMC is about to show its Problems dialog. Returns null
     * if every marker is an explicitly ignored WARNING; otherwise returns a
     * filtered view of the context, preserving all errors, infos and other warnings.
     * No markers are ever removed from BMC's source context or model.
     */
    public static Object filterProblemsContext(Object context) {
        if (context == null) return null;
        try {
            // Invoke through BMC's public interface: the concrete context can
            // be an anonymous/non-public implementation in Developer Studio.
            Class<?> contract = findContextContract(context.getClass());
            Method getter = (contract != null ? contract : context.getClass())
                    .getMethod("getMarkers");
            Object raw = getter.invoke(context);
            // BMC InfoContext.getMarkers() returns null (not an empty List)
            // when no resources have reported markers. In 0.1.62 this path
            // escaped the suppressor and opened an empty, modal InfoDialog.
            // Only suppress a genuinely empty dialog while the feature is on.
            if (raw == null) return isSuppressionActive() ? null : context;
            if (!(raw instanceof List<?>)) return context; // Unknown implementation: fail open.
            List<?> markers = (List<?>) raw;
            if (markers.isEmpty()) return isSuppressionActive() ? null : context;
            List<Object> visible = filterMarkers(markers);
            if (visible.size() == markers.size()) return context;
            if (visible.isEmpty()) return null;

            if (contract == null) return context; // fail open rather than risk UI mismatch
            return Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[] {contract},
                    new FilterContextHandler(context));
        } catch (Throwable ignored) {
            return context;
        }
    }

    private static Class<?> findContextContract(Class<?> impl) {
        return findContract(impl, "com.bmc.arsys.studio.commonui.common.info.IInfoContext");
    }

    private static Class<?> findContract(Class<?> impl, String interfaceName) {
        for (Class<?> type = impl; type != null; type = type.getSuperclass()) {
            for (Class<?> itf : type.getInterfaces()) {
                if (interfaceName.equals(itf.getName())) return itf;
            }
        }
        return null;
    }

    private static List<Object> filterMarkers(List<?> markers) {
        List<Object> visible = new ArrayList<Object>(markers.size());
        for (Object marker : markers) {
            if (!isSuppressedWarningMarker(marker)) visible.add(marker);
        }
        return visible;
    }

    private static boolean isSuppressedWarningMarker(Object marker) {
        if (marker == null) return false;
        try {
            // Public interface invocation also supports package-private markers.
            Class<?> markerContract = findContract(marker.getClass(),
                    "com.bmc.arsys.studio.commonui.common.info.IInfoMarker");
            Class<?> accessor = markerContract != null ? markerContract : marker.getClass();
            Method getType = accessor.getMethod("getType");
            Object type = getType.invoke(marker);
            if (type == null || !"WARNING".equals(type.toString())) return false;
            Method getCode = accessor.getMethod("getCode");
            Object code = getCode.invoke(marker);
            return code instanceof Number && matchesCode(((Number) code).intValue());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Guard BMC FormEditor.saveForm's *save-success* warning dialog before
     * it opens. FormEditor counts unfiltered infoResource markers, whereas the
     * Info dialog uses infoContext; these are not necessarily synchronized.
     * Only omit the dialog when all resource markers are configured WARNINGs.
     * Any unknown marker type or reflection problem keeps the BMC dialog.
     */
    public static boolean shouldShowFormSaveWarnings(Object infoResource) {
        if (!isSuppressionActive() || infoResource == null) return true;
        try {
            Class<?> contract = findContract(infoResource.getClass(),
                    "com.bmc.arsys.studio.commonui.common.info.IInfoResource");
            Class<?> accessor = contract != null ? contract : infoResource.getClass();
            Object raw = accessor.getMethod("getMarkers").invoke(infoResource);
            if (!(raw instanceof List<?>)) return true;
            List<?> markers = (List<?>) raw;
            if (markers.isEmpty()) return true; // Unknown cause of the warning dialog.
            for (Object marker : markers) {
                if (!isSuppressedWarningMarker(marker)) return true;
            }
            return false;
        } catch (Throwable ignored) {
            return true; // Fail open: BMC dialog is safer than a hidden error.
        }
    }

    /**
     * DocumentPage.doSave uses a List<StatusInfo> from the AR API, not the
     * FormEditor marker resource. AR StatusInfo message type 1 is WARNING;
     * message types 2/3/4 must never be dismissed.
     */
    public static boolean shouldShowDocumentSaveWarnings(Object statuses, boolean saveFailed) {
        if (saveFailed || !isSuppressionActive() || !(statuses instanceof List<?>)) return true;
        List<?> entries = (List<?>) statuses;
        if (entries.isEmpty()) return true;
        try {
            for (Object status : entries) {
                if (status == null) return true;
                Method getType = status.getClass().getMethod("getMessageType");
                Method getNumber = status.getClass().getMethod("getMessageNum");
                Object type = getType.invoke(status);
                Object number = getNumber.invoke(status);
                if (!(type instanceof Number) || ((Number) type).intValue() != 1
                        || !(number instanceof Number)
                        || !matchesCode(((Number) number).intValue())) return true;
            }
            return false;
        } catch (Throwable ignored) {
            return true;
        }
    }

    /** Only the dialog's getMarkers() is filtered; the underlying source stays intact. */
    public static final class FilterContextHandler implements InvocationHandler {
        private final Object original;

        public FilterContextHandler(Object original) {
            this.original = original;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                Object result = method.invoke(original, args);
                if ("getMarkers".equals(method.getName()) && method.getParameterCount() == 0
                        && result instanceof List<?>) {
                    return filterMarkers((List<?>) result);
                }
                return result;
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
