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
     * Called only when BMC is about to show its Problems dialog. Returns null
     * if every marker is an explicitly ignored WARNING; otherwise returns a
     * filtered view of the context, preserving all errors, infos and other warnings.
     * No markers are ever removed from BMC's source context or model.
     */
    public static Object filterProblemsContext(Object context) {
        if (context == null) return null;
        try {
            Method getter = context.getClass().getMethod("getMarkers");
            Object raw = getter.invoke(context);
            if (!(raw instanceof List<?>)) return context;
            List<?> markers = (List<?>) raw;
            if (markers.isEmpty()) return context;
            List<Object> visible = filterMarkers(markers);
            if (visible.size() == markers.size()) return context;
            if (visible.isEmpty()) return null;

            Class<?> contract = findContextContract(context.getClass());
            if (contract == null) return context; // fail open rather than risk UI mismatch
            return Proxy.newProxyInstance(contract.getClassLoader(), new Class<?>[] {contract},
                    new FilterContextHandler(context));
        } catch (Throwable ignored) {
            return context;
        }
    }

    private static Class<?> findContextContract(Class<?> impl) {
        for (Class<?> type = impl; type != null; type = type.getSuperclass()) {
            for (Class<?> itf : type.getInterfaces()) {
                if ("com.bmc.arsys.studio.commonui.common.info.IInfoContext".equals(itf.getName())) {
                    return itf;
                }
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
            Method getType = marker.getClass().getMethod("getType");
            Object type = getType.invoke(marker);
            if (type == null || !"WARNING".equals(type.toString())) return false;
            Method getCode = marker.getClass().getMethod("getCode");
            Object code = getCode.invoke(marker);
            return code instanceof Number && matchesCode(((Number) code).intValue());
        } catch (Throwable ignored) {
            return false;
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
