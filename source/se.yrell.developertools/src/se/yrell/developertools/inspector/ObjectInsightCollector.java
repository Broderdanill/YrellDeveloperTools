package se.yrell.developertools.inspector;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;

/**
 * Small, focused collector for Object Insight.
 *
 * The panel should only show values that are otherwise hidden behind ellipsis
 * dialogs in Developer Studio. Keep it deliberately narrow: permissions for the
 * selected object and table qualification for selected table fields.
 */
final class ObjectInsightCollector {
    private static final int MAX_VALUE_LENGTH = 4000;
    private static final int MAX_COLLECTION_ITEMS = 200;

    List<InsightRow> collect(Object selection) {
        Object target = unwrap(selection);
        if (target == null) {
            return Collections.singletonList(new InsightRow("Info", "Status", "No selected Developer Studio object."));
        }

        List<InsightRow> rows = new ArrayList<InsightRow>();
        addPermissions(rows, target);
        addTableQualification(rows, target);
        deduplicate(rows);
        if (rows.isEmpty()) {
            rows.add(new InsightRow("Info", "Status", "No permissions or table qualification found for the selected object."));
        }
        return rows;
    }

    private Object unwrap(Object value) {
        Object current = value;
        for (int i = 0; i < 5 && current != null; i++) {
            if (propertySource(current) != null) {
                return current;
            }
            Object next = callFirst(current,
                    "getModel", "getModelObject", "getObject", "getItem", "getField", "getFormObject", "getData", "getElement");
            if (next == null || next == current) {
                return current;
            }
            current = next;
        }
        return current;
    }

    private void addPermissions(List<InsightRow> rows, Object target) {
        List<String> parts = new ArrayList<String>();

        IPropertySource source = propertySource(target);
        if (source != null) {
            IPropertyDescriptor[] descriptors = safeDescriptors(source);
            if (descriptors != null) {
                for (int i = 0; i < descriptors.length; i++) {
                    IPropertyDescriptor descriptor = descriptors[i];
                    if (descriptor == null || !isPermissionDescriptor(descriptor)) {
                        continue;
                    }
                    Object value = safePropertyValue(source, descriptor);
                    addFormattedLines(parts, formatPermissionValue(value, new IdentityHashMap<Object, Boolean>(), 0));
                }
            }
        }

        Object direct = callFirst(target,
                "getPermissions", "getPermission", "getPermissionList", "getFieldPermissions", "getAccessPermissions");
        addFormattedLines(parts, formatPermissionValue(direct, new IdentityHashMap<Object, Boolean>(), 0));

        String value = joinUnique(parts, "\n");
        if (value.length() > 0) {
            rows.add(new InsightRow("Permissions", "Groups", value));
        }
    }

    private void addTableQualification(List<InsightRow> rows, Object target) {
        List<String> parts = new ArrayList<String>();

        IPropertySource source = propertySource(target);
        if (source != null) {
            IPropertyDescriptor[] descriptors = safeDescriptors(source);
            if (descriptors != null) {
                for (int i = 0; i < descriptors.length; i++) {
                    IPropertyDescriptor descriptor = descriptors[i];
                    if (descriptor == null || !isQualificationDescriptor(descriptor)) {
                        continue;
                    }
                    Object value = safePropertyValue(source, descriptor);
                    addFormattedLines(parts, formatGeneralValue(value, new IdentityHashMap<Object, Boolean>(), 0));
                }
            }
        }

        Object direct = callFirst(target,
                "getQualification", "getQualifier", "getTableQualification", "getTableQualifier", "getQuery");
        addFormattedLines(parts, formatGeneralValue(direct, new IdentityHashMap<Object, Boolean>(), 0));

        String value = joinUnique(parts, "\n");
        if (value.length() > 0) {
            rows.add(new InsightRow("Table", "Qualification", value));
        }
    }

    private boolean isPermissionDescriptor(IPropertyDescriptor descriptor) {
        String text = descriptorText(descriptor);
        return contains(text, "permission");
    }

    private boolean isQualificationDescriptor(IPropertyDescriptor descriptor) {
        String text = descriptorText(descriptor);
        return contains(text, "qualification") || contains(text, "qualifier");
    }

    private String descriptorText(IPropertyDescriptor descriptor) {
        String displayName = safeString(callNoArg(descriptor, "getDisplayName"));
        String category = safeString(callNoArg(descriptor, "getCategory"));
        String idText = safeString(descriptor.getId());
        return (displayName + " " + category + " " + idText).toLowerCase(Locale.ROOT);
    }

    private IPropertyDescriptor[] safeDescriptors(IPropertySource source) {
        try {
            return source.getPropertyDescriptors();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object safePropertyValue(IPropertySource source, IPropertyDescriptor descriptor) {
        try {
            return source.getPropertyValue(descriptor.getId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private IPropertySource propertySource(Object target) {
        if (target instanceof IPropertySource) {
            return (IPropertySource) target;
        }
        if (target instanceof IAdaptable) {
            try {
                Object adapted = ((IAdaptable) target).getAdapter(IPropertySource.class);
                if (adapted instanceof IPropertySource) {
                    return (IPropertySource) adapted;
                }
            } catch (Throwable ignored) {
            }
        }
        Object result = callFirst(target, "getPropertySource");
        if (result instanceof IPropertySource) {
            return (IPropertySource) result;
        }
        return null;
    }

    private Object callFirst(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (int i = 0; i < methodNames.length; i++) {
            Object value = callNoArg(target, methodNames[i]);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Object callNoArg(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }
        try {
            Method method = target.getClass().getMethod(methodName, new Class[0]);
            method.setAccessible(true);
            return method.invoke(target, new Object[0]);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String formatPermissionValue(Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
        if (value == null || depth > 5) {
            return "";
        }
        if (isSimple(value)) {
            return shortText(String.valueOf(value));
        }
        if (seen.containsKey(value)) {
            return "";
        }
        seen.put(value, Boolean.TRUE);
        try {
            Class<?> type = value.getClass();
            if (type.isArray()) {
                int length = Array.getLength(value);
                List<String> lines = new ArrayList<String>();
                for (int i = 0; i < length && i < MAX_COLLECTION_ITEMS; i++) {
                    addFormattedLines(lines, formatPermissionValue(Array.get(value, i), seen, depth + 1));
                }
                if (length > MAX_COLLECTION_ITEMS) {
                    lines.add("... +" + (length - MAX_COLLECTION_ITEMS) + " more");
                }
                return joinUnique(lines, "\n");
            }
            if (value instanceof Collection) {
                Collection<?> collection = (Collection<?>) value;
                List<String> lines = new ArrayList<String>();
                int count = 0;
                for (Object item : collection) {
                    if (count++ >= MAX_COLLECTION_ITEMS) {
                        lines.add("... +" + (collection.size() - MAX_COLLECTION_ITEMS) + " more");
                        break;
                    }
                    addFormattedLines(lines, formatPermissionValue(item, seen, depth + 1));
                }
                return joinUnique(lines, "\n");
            }
            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                List<String> lines = new ArrayList<String>();
                int count = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (count++ >= MAX_COLLECTION_ITEMS) {
                        lines.add("... +" + (map.size() - MAX_COLLECTION_ITEMS) + " more");
                        break;
                    }
                    String key = formatGeneralValue(entry.getKey(), seen, depth + 1);
                    String val = formatGeneralValue(entry.getValue(), seen, depth + 1);
                    if (key.length() > 0 || val.length() > 0) {
                        lines.add(key + (val.length() > 0 ? " = " + val : ""));
                    }
                }
                return joinUnique(lines, "\n");
            }

            String group = firstNonEmpty(
                    methodText(value, "getGroupName"),
                    methodText(value, "getGroup"),
                    methodText(value, "getName"),
                    methodText(value, "getGroupId"),
                    methodText(value, "getGroupID"),
                    methodText(value, "getGroupIDValue"));
            String permission = firstNonEmpty(
                    methodText(value, "getPermission"),
                    methodText(value, "getPermissions"),
                    methodText(value, "getAccess"),
                    methodText(value, "getAccessRight"),
                    methodText(value, "getType"));
            if (group.length() > 0 || permission.length() > 0) {
                if (group.length() == 0) {
                    return permission;
                }
                if (permission.length() == 0 || group.equals(permission)) {
                    return group;
                }
                return group + " = " + permission;
            }
            return formatGeneralValue(value, seen, depth + 1);
        } finally {
            seen.remove(value);
        }
    }

    private String formatGeneralValue(Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
        if (value == null || depth > 4) {
            return "";
        }
        if (isSimple(value)) {
            return shortText(String.valueOf(value));
        }
        if (seen.containsKey(value)) {
            return "";
        }
        seen.put(value, Boolean.TRUE);
        try {
            Class<?> type = value.getClass();
            if (type.isArray()) {
                int length = Array.getLength(value);
                List<String> parts = new ArrayList<String>();
                for (int i = 0; i < length && i < MAX_COLLECTION_ITEMS; i++) {
                    addFormattedLines(parts, formatGeneralValue(Array.get(value, i), seen, depth + 1));
                }
                return joinUnique(parts, "\n");
            }
            if (value instanceof Collection) {
                List<String> parts = new ArrayList<String>();
                for (Object item : (Collection<?>) value) {
                    addFormattedLines(parts, formatGeneralValue(item, seen, depth + 1));
                }
                return joinUnique(parts, "\n");
            }
            if (value instanceof Map) {
                List<String> parts = new ArrayList<String>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                    String key = formatGeneralValue(entry.getKey(), seen, depth + 1);
                    String val = formatGeneralValue(entry.getValue(), seen, depth + 1);
                    if (key.length() > 0 || val.length() > 0) {
                        parts.add(key + (val.length() > 0 ? " = " + val : ""));
                    }
                }
                return joinUnique(parts, "\n");
            }
            String bean = firstNonEmpty(
                    methodText(value, "getQualification"),
                    methodText(value, "getQualifier"),
                    methodText(value, "getQuery"),
                    methodText(value, "getExpression"),
                    methodText(value, "getValue"));
            if (bean.length() > 0) {
                return shortText(bean);
            }
            String asString = safeString(value);
            if (asString.indexOf('@') < 0) {
                return shortText(asString);
            }
            return "";
        } finally {
            seen.remove(value);
        }
    }

    private String methodText(Object value, String methodName) {
        Object result = callNoArg(value, methodName);
        if (result == null || result == value) {
            return "";
        }
        return formatGeneralValue(result, new IdentityHashMap<Object, Boolean>(), 0);
    }

    private boolean isSimple(Object value) {
        return value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Character
                || value.getClass().isEnum();
    }

    private boolean contains(String text, String needle) {
        return text != null && text.indexOf(needle) >= 0;
    }

    private String safeString(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return String.valueOf(value);
        } catch (Throwable t) {
            return value.getClass().getName();
        }
    }

    private String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] != null && values[i].trim().length() > 0) {
                return values[i].trim();
            }
        }
        return "";
    }

    private void addFormattedLines(List<String> target, String text) {
        if (text == null) {
            return;
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.length() > 0) {
                target.add(line);
            }
        }
    }

    private String joinUnique(List<String> parts, String separator) {
        Set<String> seen = new LinkedHashSet<String>();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            if (part == null) {
                continue;
            }
            String cleaned = shortText(part);
            if (cleaned.length() == 0 || !seen.add(cleaned)) {
                continue;
            }
            if (out.length() > 0) {
                out.append(separator);
            }
            out.append(cleaned);
        }
        return shortText(out.toString());
    }

    private String shortText(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (cleaned.length() > MAX_VALUE_LENGTH) {
            return cleaned.substring(0, MAX_VALUE_LENGTH) + " ...";
        }
        return cleaned;
    }

    private void deduplicate(List<InsightRow> rows) {
        Set<String> seen = new LinkedHashSet<String>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            InsightRow row = rows.get(i);
            String key = (row.category + "\u0000" + row.attribute + "\u0000" + row.value).toLowerCase(Locale.ROOT);
            if (seen.contains(key)) {
                rows.remove(i);
            } else {
                seen.add(key);
            }
        }
    }
}
