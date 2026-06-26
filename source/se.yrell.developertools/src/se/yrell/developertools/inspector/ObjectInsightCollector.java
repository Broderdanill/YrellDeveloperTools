package se.yrell.developertools.inspector;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;

import com.bmc.arsys.api.GroupInfo;
import com.bmc.arsys.api.PermissionInfo;
import com.bmc.arsys.api.QualifierInfo;
import com.bmc.arsys.api.TableFieldLimit;
import com.bmc.arsys.studio.model.store.IStore;

import se.yrell.developertools.Log;

/**
 * Small, focused collector for Object Insight.
 *
 * It intentionally shows only the values Daniel asked for right now:
 * permissions as one row per group, table qualification, and table sort columns.
 */
final class ObjectInsightCollector {
    private static final int MAX_TEXT_LENGTH = 4000;
    private static final int MAX_COLLECTION_ITEMS = 300;

    private static final Map<String, Map<Integer, String>> GROUP_CACHE = new HashMap<String, Map<Integer, String>>();

    List<InsightRow> collect(Object selection) {
        Object target = unwrap(selection);
        if (target == null) {
            return Collections.singletonList(new InsightRow("Info", "Status", "", "No selected Developer Studio object."));
        }

        List<InsightRow> rows = new ArrayList<InsightRow>();
        addPermissionRows(rows, target);
        addTableRows(rows, target);
        deduplicate(rows);
        if (rows.isEmpty()) {
            rows.add(new InsightRow("Info", "Status", "", "No permissions, table qualification or table sort found for the selected object."));
        }
        return rows;
    }

    private Object unwrap(Object value) {
        Object current = value;
        for (int i = 0; i < 8 && current != null; i++) {
            if (isUiOrModelObject(current)) {
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

    private boolean isUiOrModelObject(Object value) {
        if (value == null) {
            return false;
        }
        String name = value.getClass().getName();
        return name.startsWith("com.bmc.arsys.studio.ui.editors.form.model.")
                || name.startsWith("com.bmc.arsys.studio.model.ar.")
                || propertySource(value) != null;
    }

    private void addPermissionRows(List<InsightRow> rows, Object target) {
        Object field = callFirst(target, "getField");
        IStore store = findStore(target, field);

        Map<Integer, Integer> permissions = new LinkedHashMap<Integer, Integer>();
        collectPermissionInfos(permissions, callFirst(target,
                "getAssignedGroup", "getAssignedGroups", "getPermissions", "getPermissionList", "getFieldPermissions", "getAccessPermissions"));
        collectPermissionInfos(permissions, callFirst(field,
                "getAssignedGroup", "getAssignedGroups", "getPermissions", "getPermissionList", "getFieldPermissions", "getAccessPermissions"));

        // Last fallback: inspect the property source, but only use values that actually contain PermissionInfo objects.
        IPropertySource source = propertySource(target);
        if (source != null) {
            IPropertyDescriptor[] descriptors = safeDescriptors(source);
            if (descriptors != null) {
                for (int i = 0; i < descriptors.length; i++) {
                    IPropertyDescriptor descriptor = descriptors[i];
                    if (descriptor != null && isPermissionDescriptor(descriptor)) {
                        collectPermissionInfos(permissions, safePropertyValue(source, descriptor));
                    }
                }
            }
        }

        for (Map.Entry<Integer, Integer> entry : permissions.entrySet()) {
            int groupId = entry.getKey().intValue();
            int permission = entry.getValue() == null ? -1 : entry.getValue().intValue();
            String groupName = groupName(store, groupId);
            rows.add(new InsightRow("Permission", groupName, String.valueOf(groupId), permissionText(permission)));
        }
    }

    @SuppressWarnings("unchecked")
    private void collectPermissionInfos(Map<Integer, Integer> target, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof PermissionInfo) {
            PermissionInfo info = (PermissionInfo) value;
            target.put(Integer.valueOf(info.getGroupID()), Integer.valueOf(info.getPermissionValue()));
            return;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length && i < MAX_COLLECTION_ITEMS; i++) {
                collectPermissionInfos(target, Array.get(value, i));
            }
            return;
        }
        if (value instanceof Collection) {
            int count = 0;
            for (Object item : (Collection<Object>) value) {
                if (count++ >= MAX_COLLECTION_ITEMS) {
                    break;
                }
                collectPermissionInfos(target, item);
            }
            return;
        }
        // Reflection fallback for BMC wrapper classes around PermissionInfo.
        Object groupId = callFirst(value, "getGroupID", "getGroupId", "getId");
        Object permission = callFirst(value, "getPermissionValue", "getPermission", "getAccess", "getAccessRight");
        Integer gid = toInteger(groupId);
        Integer perm = toInteger(permission);
        if (gid != null) {
            target.put(gid, perm == null ? Integer.valueOf(-1) : perm);
        }
    }

    private void addTableRows(List<InsightRow> rows, Object target) {
        Object field = callFirst(target, "getField");
        IStore store = findStore(target, field);

        Object treeProps = callFirst(target, "getTreeTableProperties");
        Object sortProps = callFirst(target, "getSortProperties");

        QualifierInfo qualifier = firstQualifier(treeProps, sortProps, field, target);
        if (qualifier != null) {
            rows.add(new InsightRow("Table", "Qualification", "", formatQualification(store, qualifier)));
        }

        addSortRows(rows, sortProps);

        // Property-source fallback for older/variant table model objects where the methods above are hidden.
        if (qualifier == null || !hasCategory(rows, "Table sort")) {
            IPropertySource source = propertySource(target);
            if (source != null) {
                IPropertyDescriptor[] descriptors = safeDescriptors(source);
                if (descriptors != null) {
                    for (int i = 0; i < descriptors.length; i++) {
                        IPropertyDescriptor descriptor = descriptors[i];
                        if (descriptor == null) {
                            continue;
                        }
                        String text = descriptorText(descriptor);
                        Object value = safePropertyValue(source, descriptor);
                        if (qualifier == null && (contains(text, "qualification") || contains(text, "qualifier"))) {
                            String formatted = formatGeneralValue(value, new IdentityHashMap<Object, Boolean>(), 0);
                            if (formatted.length() > 0) {
                                rows.add(new InsightRow("Table", "Qualification", "", formatted));
                                qualifier = new QualifierInfo(); // marker: avoid adding more fallbacks
                            }
                        }
                        if (!hasCategory(rows, "Table sort") && (contains(text, "sort") || contains(text, "level"))) {
                            String formatted = formatGeneralValue(value, new IdentityHashMap<Object, Boolean>(), 0);
                            addTextRows(rows, "Table sort", formatted);
                        }
                    }
                }
            }
        }
    }

    private QualifierInfo firstQualifier(Object treeProps, Object sortProps, Object field, Object target) {
        Object value = callFirst(treeProps, "getQualifierInfo", "getQualifier", "getQualification");
        if (value instanceof QualifierInfo) {
            return (QualifierInfo) value;
        }
        value = callFirst(sortProps, "getQualifierInfo", "getQualifier", "getQualification");
        if (value instanceof QualifierInfo) {
            return (QualifierInfo) value;
        }
        Object limit = callFirst(field, "getFieldLimit");
        if (limit instanceof TableFieldLimit) {
            return ((TableFieldLimit) limit).getQualifier();
        }
        limit = callFirst(target, "getFieldLimit");
        if (limit instanceof TableFieldLimit) {
            return ((TableFieldLimit) limit).getQualifier();
        }
        Object direct = callFirst(target, "getQualifierInfo", "getQualifier", "getQualification", "getTableQualification", "getTableQualifier", "getQuery");
        return direct instanceof QualifierInfo ? (QualifierInfo) direct : null;
    }

    @SuppressWarnings("unchecked")
    private void addSortRows(List<InsightRow> rows, Object sortProps) {
        Object columns = callFirst(sortProps, "getSortedColumns", "getColumns");
        if (!(columns instanceof Collection)) {
            return;
        }
        int count = 0;
        for (Object column : (Collection<Object>) columns) {
            if (column == null || count++ >= MAX_COLLECTION_ITEMS) {
                break;
            }
            int sequence = intValue(callFirst(column, "getSortSequence"), 0);
            long direction = longValue(callFirst(column, "getSortDirection"), 0L);
            if (sequence <= 0 && direction == 0L) {
                continue;
            }
            String label = firstNonEmpty(
                    safeString(callFirst(column, "getColumnLabel")),
                    safeString(callFirst(column, "getColumnName")),
                    "Field " + safeString(callFirst(column, "getDataFieldID")),
                    "Column " + safeString(callFirst(column, "getColumnID")));
            String id = firstNonEmpty(
                    safeString(callFirst(column, "getDataFieldID")),
                    safeString(callFirst(column, "getColumnID")));
            String value = "Sequence " + sequence + ", " + sortDirectionText(direction);
            rows.add(new InsightRow("Table sort", label, id, value));
        }
    }

    private void addTextRows(List<InsightRow> rows, String category, String text) {
        if (text == null) {
            return;
        }
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.length() > 0) {
                rows.add(new InsightRow(category, line, "", ""));
            }
        }
    }

    private IStore findStore(Object target, Object field) {
        Object store = callFirst(target, "getStore");
        if (store instanceof IStore) {
            return (IStore) store;
        }
        store = callFirst(field, "getStore");
        if (store instanceof IStore) {
            return (IStore) store;
        }
        Object form = callFirst(target, "getARForm", "getFormObject", "getForm");
        store = callFirst(form, "getStore");
        return store instanceof IStore ? (IStore) store : null;
    }

    private String groupName(IStore store, int groupId) {
        String builtIn = builtInGroupName(groupId);
        if (builtIn.length() > 0) {
            return builtIn;
        }
        Map<Integer, String> map = groupMap(store);
        String name = map.get(Integer.valueOf(groupId));
        return name == null || name.length() == 0 ? "Group " + groupId : name;
    }

    private String builtInGroupName(int groupId) {
        switch (groupId) {
        case 0:
            return "Public";
        case 1:
            return "Administrator";
        case 2:
            return "Customize";
        case 3:
            return "Submitter";
        case 4:
            return "Assignee";
        case 5:
            return "Sub Administrator";
        case 6:
            return "Flashboards Administrator";
        case 7:
            return "Assignee Group";
        case 60988:
            return "Assignee Group Access";
        default:
            return "";
        }
    }

    private Map<Integer, String> groupMap(IStore store) {
        if (store == null) {
            return Collections.emptyMap();
        }
        String key = safeString(callFirst(store, "getName"));
        if (key.length() == 0) {
            key = String.valueOf(System.identityHashCode(store));
        }
        synchronized (GROUP_CACHE) {
            Map<Integer, String> cached = GROUP_CACHE.get(key);
            if (cached != null) {
                return cached;
            }
            Map<Integer, String> map = new HashMap<Integer, String>();
            try {
                List<GroupInfo> groups = store.getListGroup();
                if (groups != null) {
                    for (GroupInfo group : groups) {
                        if (group != null) {
                            map.put(Integer.valueOf(group.getId()), group.getName());
                        }
                    }
                }
            } catch (Throwable t) {
                Log.warn("Object Insight could not load group names for store " + key + ": " + t.getMessage());
            }
            GROUP_CACHE.put(key, map);
            return map;
        }
    }

    private String permissionText(int permission) {
        if (permission == 1) {
            return "View";
        }
        if (permission == 2) {
            return "Change";
        }
        if (permission == 0) {
            return "None";
        }
        if (permission < 0) {
            return "";
        }
        return "Permission " + permission;
    }

    private String sortDirectionText(long direction) {
        if (direction == 1L) {
            return "Ascending";
        }
        if (direction == 2L) {
            return "Descending";
        }
        if (direction == 0L) {
            return "Default";
        }
        return "Direction " + direction;
    }

    private String formatQualification(IStore store, QualifierInfo qualifier) {
        if (qualifier == null) {
            return "";
        }
        try {
            if (store != null) {
                String encoded = store.encodeQualification(qualifier);
                if (encoded != null && encoded.trim().length() > 0) {
                    return shortText(encoded);
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            if (store != null) {
                String formatted = store.formatQualification(qualifier,
                        Collections.emptyList(), Collections.emptyList(), 0, false);
                if (formatted != null && formatted.trim().length() > 0) {
                    return shortText(formatted);
                }
            }
        } catch (Throwable ignored) {
        }
        return shortText(safeString(qualifier));
    }

    private boolean isPermissionDescriptor(IPropertyDescriptor descriptor) {
        return contains(descriptorText(descriptor), "permission");
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
                    addNonEmpty(parts, formatGeneralValue(Array.get(value, i), seen, depth + 1));
                }
                return join(parts, "\n");
            }
            if (value instanceof Collection) {
                List<String> parts = new ArrayList<String>();
                int count = 0;
                for (Object item : (Collection<?>) value) {
                    if (count++ >= MAX_COLLECTION_ITEMS) {
                        break;
                    }
                    addNonEmpty(parts, formatGeneralValue(item, seen, depth + 1));
                }
                return join(parts, "\n");
            }
            String bean = firstNonEmpty(
                    safeString(callFirst(value, "getQualification", "getQualifier", "getQuery", "getExpression", "getValue", "getLabel", "getName")),
                    safeString(value));
            if (bean.indexOf('@') >= 0 && bean.startsWith(value.getClass().getName())) {
                return "";
            }
            return shortText(bean);
        } finally {
            seen.remove(value);
        }
    }

    private boolean hasCategory(List<InsightRow> rows, String category) {
        for (InsightRow row : rows) {
            if (row.category.equals(category)) {
                return true;
            }
        }
        return false;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        try {
            if (value != null) {
                return Integer.valueOf(String.valueOf(value).trim());
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private int intValue(Object value, int fallback) {
        Integer i = toInteger(value);
        return i == null ? fallback : i.intValue();
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            if (value != null) {
                return Long.parseLong(String.valueOf(value).trim());
            }
        } catch (Throwable ignored) {
        }
        return fallback;
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
            return String.valueOf(value).trim();
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

    private void addNonEmpty(List<String> target, String text) {
        if (text != null && text.trim().length() > 0) {
            target.add(text.trim());
        }
    }

    private String join(List<String> parts, String separator) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.trim().length() == 0) {
                continue;
            }
            if (out.length() > 0) {
                out.append(separator);
            }
            out.append(part.trim());
        }
        return shortText(out.toString());
    }

    private void deduplicate(List<InsightRow> rows) {
        Map<String, InsightRow> unique = new LinkedHashMap<String, InsightRow>();
        for (InsightRow row : rows) {
            String key = row.category + "\u0001" + row.name + "\u0001" + row.id + "\u0001" + row.value;
            if (!unique.containsKey(key)) {
                unique.put(key, row);
            }
        }
        rows.clear();
        rows.addAll(unique.values());
    }

    private String shortText(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (cleaned.length() > MAX_TEXT_LENGTH) {
            return cleaned.substring(0, MAX_TEXT_LENGTH) + " ...";
        }
        return cleaned;
    }
}
