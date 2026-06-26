package se.yrell.developertools.inspector;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
 * Reflection-based collector for the selected Developer Studio object.
 *
 * The BMC model uses several internal classes that vary between Developer Studio
 * versions, so this class intentionally avoids compile-time dependencies on BMC
 * field/table classes. It reads the normal Eclipse property source when available
 * and supplements it with safe getter-method inspection.
 */
final class ObjectInsightCollector {
    private static final int MAX_VALUE_LENGTH = 2000;
    private static final int MAX_COLLECTION_ITEMS = 80;
    private static final String[] PROPERTY_KEYWORDS = new String[] {
            "permission", "qualification", "qual", "sort", "database", "db name", "field id", "field type",
            "source form", "remote form", "table", "column", "display type", "enum", "label", "owner", "view"
    };
    private static final String[] METHOD_KEYWORDS = new String[] {
            "permission", "qualification", "qualifier", "sort", "schema", "remote", "source", "table", "column",
            "database", "db", "field", "view", "owner"
    };

    List<InsightRow> collect(Object selection) {
        Object target = unwrap(selection);
        if (target == null) {
            return Collections.singletonList(new InsightRow("Selection", "Status", "No selected Developer Studio object."));
        }

        List<InsightRow> rows = new ArrayList<InsightRow>();
        addBasicRows(rows, target);
        addPropertySourceRows(rows, target);
        addMethodRows(rows, target);
        deduplicate(rows);
        if (rows.size() <= 2) {
            rows.add(new InsightRow("Selection", "Hint", "Select a form field, table field or another object in Developer Studio to show permissions and hidden property values here."));
        }
        return rows;
    }

    private Object unwrap(Object value) {
        Object current = value;
        for (int i = 0; i < 5 && current != null; i++) {
            if (current instanceof IAdaptable) {
                try {
                    Object adapted = ((IAdaptable) current).getAdapter(IPropertySource.class);
                    if (adapted != null && adapted != current) {
                        return current;
                    }
                } catch (Throwable ignored) {
                }
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

    private void addBasicRows(List<InsightRow> rows, Object target) {
        rows.add(new InsightRow("Selection", "Runtime type", target.getClass().getName()));
        addIfPresent(rows, "Selection", "Name", callFirst(target, "getName", "getDisplayName", "getLabel"));
        addIfPresent(rows, "Selection", "Field ID", callFirst(target, "getFieldId", "getFieldID", "getId", "getID"));
        addIfPresent(rows, "Selection", "Database name", callFirst(target, "getDatabaseName", "getDbName", "getDBName"));
        addIfPresent(rows, "Selection", "Field type", callFirst(target, "getFieldType", "getType", "getDataType"));
    }

    private void addPropertySourceRows(List<InsightRow> rows, Object target) {
        IPropertySource source = propertySource(target);
        if (source == null) {
            return;
        }
        IPropertyDescriptor[] descriptors;
        try {
            descriptors = source.getPropertyDescriptors();
        } catch (Throwable t) {
            rows.add(new InsightRow("Property source", "Error", shortText(t.getClass().getSimpleName() + ": " + t.getMessage())));
            return;
        }
        if (descriptors == null) {
            return;
        }
        for (int i = 0; i < descriptors.length; i++) {
            IPropertyDescriptor descriptor = descriptors[i];
            if (descriptor == null) {
                continue;
            }
            String displayName = safeString(callNoArg(descriptor, "getDisplayName"));
            String category = safeString(callNoArg(descriptor, "getCategory"));
            String idText = safeString(descriptor.getId());
            String searchable = (displayName + " " + category + " " + idText).toLowerCase(Locale.ROOT);
            boolean important = containsAny(searchable, PROPERTY_KEYWORDS);
            Object propertyValue = null;
            boolean complex = false;
            try {
                propertyValue = source.getPropertyValue(descriptor.getId());
                complex = isComplex(propertyValue);
            } catch (Throwable t) {
                if (important) {
                    rows.add(new InsightRow(displayCategory(category, searchable), label(displayName, idText),
                            shortText(t.getClass().getSimpleName() + ": " + t.getMessage())));
                }
                continue;
            }
            if (important || complexNeedsDisplay(propertyValue)) {
                String displayCategory = displayCategory(category, searchable);
                String attr = label(displayName, idText);
                String value = formatValue(propertyValue, new IdentityHashMap<Object, Boolean>(), 0);
                if (value.length() > 0) {
                    rows.add(new InsightRow(displayCategory, attr, value));
                }
            }
        }
    }

    private void addMethodRows(List<InsightRow> rows, Object target) {
        Method[] methods;
        try {
            methods = target.getClass().getMethods();
        } catch (Throwable t) {
            return;
        }
        List<Method> sorted = new ArrayList<Method>();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (method.getParameterTypes().length != 0) {
                continue;
            }
            String name = method.getName();
            if (!(name.startsWith("get") || name.startsWith("is"))) {
                continue;
            }
            if ("getClass".equals(name)) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (!containsAny(lower, METHOD_KEYWORDS)) {
                continue;
            }
            sorted.add(method);
        }
        Collections.sort(sorted, new Comparator<Method>() {
            @Override
            public int compare(Method a, Method b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (int i = 0; i < sorted.size() && i < 80; i++) {
            Method method = sorted.get(i);
            try {
                Object value = method.invoke(target);
                String formatted = formatValue(value, new IdentityHashMap<Object, Boolean>(), 0);
                if (formatted.length() > 0) {
                    rows.add(new InsightRow(methodCategory(method.getName()), humanize(method.getName()), formatted));
                }
            } catch (Throwable ignored) {
            }
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
        Object result = callFirst(target, "getPropertySource", "getAdapter");
        if (result instanceof IPropertySource) {
            return (IPropertySource) result;
        }
        return null;
    }

    private void addIfPresent(List<InsightRow> rows, String category, String attribute, Object value) {
        String formatted = formatValue(value, new IdentityHashMap<Object, Boolean>(), 0);
        if (formatted.length() > 0) {
            rows.add(new InsightRow(category, attribute, formatted));
        }
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

    private boolean containsAny(String text, String[] keywords) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < keywords.length; i++) {
            if (text.indexOf(keywords[i]) >= 0) {
                return true;
            }
        }
        return false;
    }

    private String displayCategory(String category, String searchable) {
        String lower = searchable == null ? "" : searchable.toLowerCase(Locale.ROOT);
        if (lower.indexOf("permission") >= 0) {
            return "Permissions";
        }
        if (lower.indexOf("qualification") >= 0 || lower.indexOf("qual") >= 0) {
            return "Table field";
        }
        if (lower.indexOf("sort") >= 0) {
            return "Table field";
        }
        if (category != null && category.trim().length() > 0) {
            return category.trim();
        }
        return "Properties";
    }

    private String methodCategory(String methodName) {
        String lower = methodName == null ? "" : methodName.toLowerCase(Locale.ROOT);
        if (lower.indexOf("permission") >= 0) {
            return "Permissions";
        }
        if (lower.indexOf("qualification") >= 0 || lower.indexOf("qualifier") >= 0 || lower.indexOf("sort") >= 0
                || lower.indexOf("table") >= 0 || lower.indexOf("column") >= 0 || lower.indexOf("remote") >= 0
                || lower.indexOf("source") >= 0) {
            return "Table field";
        }
        return "Model details";
    }

    private String label(String displayName, String idText) {
        if (displayName != null && displayName.trim().length() > 0) {
            return displayName.trim();
        }
        if (idText != null && idText.trim().length() > 0) {
            return idText.trim();
        }
        return "Property";
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

    private boolean complexNeedsDisplay(Object value) {
        if (value == null) {
            return false;
        }
        if (!isComplex(value)) {
            return false;
        }
        String className = value.getClass().getName().toLowerCase(Locale.ROOT);
        return containsAny(className, new String[] { "permission", "qualification", "sort", "table", "column" });
    }

    private boolean isComplex(Object value) {
        if (value == null) {
            return false;
        }
        Class<?> type = value.getClass();
        return type.isArray() || value instanceof Collection || value instanceof Map || !(value instanceof CharSequence)
                && !(value instanceof Number) && !(value instanceof Boolean) && !(value instanceof Character)
                && !type.isEnum();
    }

    private String formatValue(Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
        if (value == null) {
            return "";
        }
        if (depth > 3) {
            return shortText(safeString(value));
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Character
                || value.getClass().isEnum()) {
            return shortText(String.valueOf(value));
        }
        if (seen.containsKey(value)) {
            return "<recursive>";
        }
        seen.put(value, Boolean.TRUE);
        try {
            Class<?> type = value.getClass();
            if (type.isArray()) {
                int length = Array.getLength(value);
                List<String> parts = new ArrayList<String>();
                for (int i = 0; i < length && i < MAX_COLLECTION_ITEMS; i++) {
                    parts.add(formatValue(Array.get(value, i), seen, depth + 1));
                }
                if (length > MAX_COLLECTION_ITEMS) {
                    parts.add("... +" + (length - MAX_COLLECTION_ITEMS) + " more");
                }
                return shortText(join(parts, "\n"));
            }
            if (value instanceof Collection) {
                Collection<?> collection = (Collection<?>) value;
                List<String> parts = new ArrayList<String>();
                int count = 0;
                for (Object item : collection) {
                    if (count++ >= MAX_COLLECTION_ITEMS) {
                        parts.add("... +" + (collection.size() - MAX_COLLECTION_ITEMS) + " more");
                        break;
                    }
                    parts.add(formatValue(item, seen, depth + 1));
                }
                return shortText(join(parts, "\n"));
            }
            if (value instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) value;
                List<String> parts = new ArrayList<String>();
                int count = 0;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (count++ >= MAX_COLLECTION_ITEMS) {
                        parts.add("... +" + (map.size() - MAX_COLLECTION_ITEMS) + " more");
                        break;
                    }
                    parts.add(formatValue(entry.getKey(), seen, depth + 1) + " = "
                            + formatValue(entry.getValue(), seen, depth + 1));
                }
                return shortText(join(parts, "\n"));
            }

            String bean = formatBean(value, seen, depth);
            if (bean.length() > 0) {
                return shortText(bean);
            }
            return shortText(safeString(value));
        } finally {
            seen.remove(value);
        }
    }

    private String formatBean(Object value, IdentityHashMap<Object, Boolean> seen, int depth) {
        Set<String> preferred = new LinkedHashSet<String>();
        preferred.add("getGroupId");
        preferred.add("getGroupID");
        preferred.add("getGroupName");
        preferred.add("getPermission");
        preferred.add("getPermissions");
        preferred.add("getFieldId");
        preferred.add("getFieldID");
        preferred.add("getFieldName");
        preferred.add("getName");
        preferred.add("getOrder");
        preferred.add("getSortOrder");
        preferred.add("isDescending");
        preferred.add("getQualification");
        preferred.add("getQualifier");
        preferred.add("getQuery");
        preferred.add("getFormName");
        preferred.add("getServerName");
        List<String> parts = new ArrayList<String>();
        for (String methodName : preferred) {
            Object result = callNoArg(value, methodName);
            String formatted = formatValue(result, seen, depth + 1);
            if (formatted.length() > 0) {
                parts.add(humanize(methodName) + "=" + formatted.replace('\n', ' '));
            }
        }
        if (!parts.isEmpty()) {
            return join(parts, ", ");
        }
        String asString = safeString(value);
        if (asString != null && asString.length() > 0 && asString.indexOf('@') < 0) {
            return asString;
        }
        return "";
    }

    private String humanize(String methodName) {
        String name = methodName == null ? "" : methodName;
        if (name.startsWith("get") && name.length() > 3) {
            name = name.substring(3);
        } else if (name.startsWith("is") && name.length() > 2) {
            name = name.substring(2);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && Character.isLowerCase(name.charAt(i - 1))) {
                out.append(' ');
            }
            out.append(c);
        }
        return out.toString();
    }

    private String join(List<String> parts, String separator) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i) == null || parts.get(i).length() == 0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(separator);
            }
            out.append(parts.get(i));
        }
        return out.toString();
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
