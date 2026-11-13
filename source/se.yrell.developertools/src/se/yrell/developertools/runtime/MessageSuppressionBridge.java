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
            if (raw == null) {
                auditPresentation(context, "null", "EMPTY");
                return isSuppressionActive() ? null : context;
            }
            if (!(raw instanceof List<?>)) {
                auditPresentation(context, "unknown", "KEEP");
                return context; // Unknown implementation: fail open.
            }
            List<?> markers = (List<?>) raw;
            if (markers.isEmpty()) {
                auditPresentation(context, "0", "EMPTY");
                return isSuppressionActive() ? null : context;
            }
            List<Object> visible = filterMarkers(markers);
            auditPresentation(context, Integer.toString(visible.size()),
                    visible.isEmpty() ? "DROP" : visible.size() == markers.size() ? "KEEP" : "FILTER");
            if (visible.size() == markers.size()) return context;
            if (visible.isEmpty()) return null;

            if (contract == null) return context; // fail open rather than risk UI mismatch
            return Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[] {contract},
                    new FilterContextHandler(context));
        } catch (Throwable ignored) {
            auditPresentation(context, "unreadable", "KEEP");
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

    // This is intentionally tri-state rather than assuming every unreadable
    // object represents an empty list: a reflection failure must never hide a
    // BMC error or an unconfigured warning.
    private static final int MARKERS_UNKNOWN = 0;
    private static final int MARKERS_EMPTY = 1;
    private static final int MARKERS_ALL_SUPPRESSED = 2;
    private static final int MARKERS_VISIBLE = 3;

    private static int markerState(Object source, String interfaceName) {
        if (source == null) return MARKERS_UNKNOWN;
        try {
            Class<?> contract = findContract(source.getClass(), interfaceName);
            Class<?> accessor = contract != null ? contract : source.getClass();
            Object raw = accessor.getMethod("getMarkers").invoke(source);
            if (raw == null) return MARKERS_EMPTY;
            if (!(raw instanceof List<?>)) return MARKERS_UNKNOWN;
            List<?> markers = (List<?>) raw;
            if (markers.isEmpty()) return MARKERS_EMPTY;
            for (Object marker : markers) {
                if (!isSuppressedWarningMarker(marker)) return MARKERS_VISIBLE;
            }
            return MARKERS_ALL_SUPPRESSED;
        } catch (Throwable ignored) {
            return MARKERS_UNKNOWN;
        }
    }

    /**
     * Ordinary BMC FormEditor.saveForm success path: the original resource
     * and displayed IInfoContext can disagree. Skip only when BOTH sources
     * are readable and neither has an unsuppressed warning or error. Empty
     * marker lists count as no warnings to display, not a reason to pop up.
     */
    public static boolean shouldShowFormSaveWarnings(Object infoResource, Object infoContext) {
        if (!isSuppressionActive()) return true;
        int source = markerState(infoResource,
                "com.bmc.arsys.studio.commonui.common.info.IInfoResource");
        int display = markerState(infoContext,
                "com.bmc.arsys.studio.commonui.common.info.IInfoContext");
        boolean shouldShow = source == MARKERS_UNKNOWN || display == MARKERS_UNKNOWN
                || source == MARKERS_VISIBLE || display == MARKERS_VISIBLE;
        auditSaveGuard("form-success", source, display, shouldShow);
        auditSaveGuardDetails(infoResource, infoContext);
        return shouldShow;
    }

    /**
     * Legacy single-argument guard retained for regression compatibility.
     */
    public static boolean shouldShowFormSaveWarnings(Object infoResource) {
        if (!isSuppressionActive()) return true;
        int source = markerState(infoResource,
                "com.bmc.arsys.studio.commonui.common.info.IInfoResource");
        return source == MARKERS_UNKNOWN || source == MARKERS_EMPTY || source == MARKERS_VISIBLE;
    }

    /**
     * FormEditor has a SECOND call with the SAME warning caption inside its
     * ModelException handler. Crucially BMC sets SAVE_STATE.FAILED in this
     * branch even when the captured StatusInfo objects are only warnings.
     * Never hide this dialog: otherwise a failed save could look successful.
     * Log the branch so we can distinguish it from the ordinary save path.
     */
    public static boolean shouldShowFormExceptionWarnings(Object statuses, Object infoContext) {
        if (isSuppressionActive()) {
            int original = statusState(statuses);
            int display = markerState(infoContext,
                    "com.bmc.arsys.studio.commonui.common.info.IInfoContext");
            auditSaveGuard("form-model-exception-save-failed", original, display, true);
        }
        return true;
    }

    private static int statusState(Object statuses) {
        if (!(statuses instanceof List<?>)) return MARKERS_UNKNOWN;
        List<?> entries = (List<?>) statuses;
        if (entries.isEmpty()) return MARKERS_EMPTY;
        try {
            for (Object status : entries) {
                if (status == null) return MARKERS_VISIBLE;
                Method getType = status.getClass().getMethod("getMessageType");
                Method getNumber = status.getClass().getMethod("getMessageNum");
                Object type = getType.invoke(status);
                Object number = getNumber.invoke(status);
                if (!(type instanceof Number) || ((Number) type).intValue() != 1
                        || !(number instanceof Number)
                        || !matchesCode(((Number) number).intValue())) return MARKERS_VISIBLE;
            }
            return MARKERS_ALL_SUPPRESSED;
        } catch (Throwable ignored) {
            return MARKERS_UNKNOWN;
        }
    }

    /**
     * Diagnose the actual marker metadata, not message text. A state value of 3
     * means that at least one marker was not suppressed; it is NOT a count.
     * Only numeric codes and the three BMC marker severities are recorded.
     */
    private static String markerSnapshot(Object source, String interfaceName) {
        if (source == null) return "null";
        try {
            Class<?> contract = findContract(source.getClass(), interfaceName);
            Class<?> accessor = contract != null ? contract : source.getClass();
            Object raw = accessor.getMethod("getMarkers").invoke(source);
            if (raw == null) return "null-markers";
            if (!(raw instanceof List<?>)) return "unexpected-marker-container";
            List<?> markers = (List<?>) raw;
            StringBuilder out = new StringBuilder();
            out.append("count=").append(markers.size()).append("[");
            int max = Math.min(markers.size(), 16);
            for (int i = 0; i < max; ++i) {
                if (i > 0) out.append(',');
                Object marker = markers.get(i);
                if (marker == null) {
                    out.append("null");
                    continue;
                }
                try {
                    Class<?> markerContract = findContract(marker.getClass(),
                            "com.bmc.arsys.studio.commonui.common.info.IInfoMarker");
                    Class<?> markerAccessor = markerContract != null ? markerContract : marker.getClass();
                    Object codeRaw = markerAccessor.getMethod("getCode").invoke(marker);
                    Object typeRaw = markerAccessor.getMethod("getType").invoke(marker);
                    String severity = typeRaw instanceof Enum<?>
                            ? ((Enum<?>) typeRaw).name() : "UNKNOWN";
                    int code = codeRaw instanceof Number ? ((Number) codeRaw).intValue() : 0;
                    out.append(severity).append(':').append(code);
                    out.append(isSuppressedWarningMarker(marker) ? "/SUPPRESSED" : "/KEPT");
                } catch (Throwable ignored) {
                    out.append("unreadable-marker");
                }
            }
            if (markers.size() > max) out.append(",remaining=").append(markers.size() - max);
            return out.append(']').toString();
        } catch (Throwable ignored) {
            return "unreadable-source";
        }
    }

    private static void auditPresentation(Object context, String displayedCount, String action) {
        if (!isSuppressionActive()) return;
        appendDiagnostic("presentation action=" + action
                + " displayedCount=" + displayedCount
                + " input=" + markerSnapshot(context,
                "com.bmc.arsys.studio.commonui.common.info.IInfoContext"));
    }

    private static void appendDiagnostic(String detail) {
        try {
            File root = new File(System.getProperty("user.home", ""), ".yrell-developertools");
            if (!root.isDirectory()) return;
            File log = new File(root, "message-suppression-diagnostics.log");
            if (log.isFile() && log.length() > 262144L) {
                java.nio.file.Files.deleteIfExists(log.toPath());
            }
            String line = java.time.Instant.now().toString() + " 0.1.68 " + detail
                    + System.lineSeparator();
            java.nio.file.Files.write(log.toPath(), line.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
            // Observability must never change Developer Studio's save semantics.
        }
    }

    /**
     * Small bounded diagnostic file, recording no object names, credentials or
     * message text. If a blank dialog remains we can determine which callsite
     * was active and whether the hook actually executed in Developer Studio.
     */
    private static void auditSaveGuard(String path, int original, int visible, boolean show) {
        // No message text, object/form names, user names, hostnames or credentials.
        // FormEditor success-branch metadata is recorded through a separate
        // diagnostic overload with access to the original objects.
        appendDiagnostic(path + " source=" + original + " display=" + visible
                + " action=" + (show ? "SHOW" : "SKIP"));
    }

    private static void auditSaveGuardDetails(Object infoResource, Object infoContext) {
        appendDiagnostic("form-success-details sourceMarkers="
                + markerSnapshot(infoResource,
                "com.bmc.arsys.studio.commonui.common.info.IInfoResource")
                + " displayMarkers="
                + markerSnapshot(infoContext,
                "com.bmc.arsys.studio.commonui.common.info.IInfoContext"));
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
