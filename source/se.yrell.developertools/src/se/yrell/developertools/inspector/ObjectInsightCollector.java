package se.yrell.developertools.inspector;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<Object, Boolean>();
        for (int i = 0; i < 10 && current != null; i++) {
            if (seen.containsKey(current)) {
                return current;
            }
            seen.put(current, Boolean.TRUE);

            if (isUiOrModelObject(current)) {
                return current;
            }

            // Canvas/outline selections are often GEF edit parts or property wrappers.
            // They can have an Eclipse property source, but the real permissions/table
            // metadata lives on the BMC UI/model object behind getModel()/getField().
            Object next = callFirst(current,
                    "getModel", "getModelObject", "getObject", "getItem", "getField", "getFormObject", "getData", "getElement");
            if (next != null && next != current) {
                current = next;
                continue;
            }

            if (propertySource(current) != null) {
                return current;
            }
            return current;
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
                || name.startsWith("com.bmc.arsys.studio.model.store.");
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
        TableFieldLimit limit = tableLimit(field, target);
        if (!isTableCandidate(target, field, treeProps, sortProps, limit)) {
            return;
        }

        addTableSourceRows(rows, treeProps, limit);

        QualifierInfo qualifier = firstQualifier(treeProps, sortProps, field, target);
        if (qualifier != null) {
            rows.add(new InsightRow("Table", "Qualification", "", formatQualification(store, qualifier)));
        }

        addSortRows(rows, sortProps);

        // Property-source fallback for older/variant table model objects where the methods above are hidden.
        if (!hasTableForm(rows) || qualifier == null || !hasCategory(rows, "Table sort")) {
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
                        if (!hasTableForm(rows) && contains(text, "form")) {
                            String formatted = formatGeneralValue(value, new IdentityHashMap<Object, Boolean>(), 0);
                            if (formatted.length() > 0 && !contains(formatted.toLowerCase(Locale.ROOT), "@")) {
                                rows.add(new InsightRow("Table", "Form", "", shortText(formatted)));
                            }
                        }
                        if (qualifier == null && (contains(text, "qualification") || contains(text, "qualifier"))) {
                            String formatted = formatGeneralValue(value, new IdentityHashMap<Object, Boolean>(), 0);
                            if (formatted.length() > 0) {
                                if (addStructuredTableTextRows(rows, formatted, true)) {
                                    qualifier = new QualifierInfo(); // marker: avoid adding more fallbacks
                                } else {
                                    rows.add(new InsightRow("Table", "Qualification", "", cleanQualificationText(formatted)));
                                    qualifier = new QualifierInfo(); // marker: avoid adding more fallbacks
                                }
                            }
                        }
                        if (!hasCategory(rows, "Table sort") && (contains(text, "sort") || contains(text, "level"))) {
                            String formatted = formatGeneralValue(value, new IdentityHashMap<Object, Boolean>(), 0);
                            if (!addStructuredTableTextRows(rows, formatted, false)) {
                                addTextRows(rows, "Table sort", formatted);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isTableCandidate(Object target, Object field, Object treeProps, Object sortProps, TableFieldLimit limit) {
        if (treeProps != null || sortProps != null || limit != null) {
            return true;
        }
        String targetClass = target == null ? "" : target.getClass().getName();
        String fieldClass = field == null ? "" : field.getClass().getName();
        return targetClass.indexOf("UITable") >= 0
                || targetClass.indexOf("TableField") >= 0
                || fieldClass.indexOf("TableField") >= 0;
    }

    private void addTableSourceRows(List<InsightRow> rows, Object treeProps, TableFieldLimit limit) {
        String form = firstNonEmpty(
                safeString(callFirst(treeProps, "getForm")),
                limit == null ? "" : safeString(limit.getForm()));
        String server = firstNonEmpty(
                safeString(callFirst(treeProps, "getServer")),
                limit == null ? "" : safeString(limit.getServer()));
        String sampleForm = firstNonEmpty(
                safeString(callFirst(treeProps, "getSampleForm")),
                limit == null ? "" : safeString(limit.getSampleForm()));
        String sampleServer = firstNonEmpty(
                safeString(callFirst(treeProps, "getSampleServer")),
                limit == null ? "" : safeString(limit.getSampleServer()));

        if (server.length() > 0) {
            rows.add(new InsightRow("Table", "Server", "", server));
        }
        if (form.length() > 0) {
            rows.add(new InsightRow("Table", "Form", "", form));
        }
        if (sampleServer.length() > 0 && !sampleServer.equals(server)) {
            rows.add(new InsightRow("Table", "Sample server", "", sampleServer));
        }
        if (sampleForm.length() > 0 && !sampleForm.equals(form)) {
            rows.add(new InsightRow("Table", "Sample form", "", sampleForm));
        }
    }

    private TableFieldLimit tableLimit(Object field, Object target) {
        Object limit = callFirst(field, "getFieldLimit");
        if (limit instanceof TableFieldLimit) {
            return (TableFieldLimit) limit;
        }
        limit = callFirst(target, "getFieldLimit");
        return limit instanceof TableFieldLimit ? (TableFieldLimit) limit : null;
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
        List<Object> sorted = new ArrayList<Object>();
        int count = 0;
        for (Object column : (Collection<Object>) columns) {
            if (column == null || count++ >= MAX_COLLECTION_ITEMS) {
                break;
            }
            int sequence = intValue(callFirst(column, "getSortSequence"), 0);
            long direction = longValue(callFirst(column, "getSortDirection"), 0L);
            if (sequence > 0 || direction != 0L) {
                sorted.add(column);
            }
        }
        Collections.sort(sorted, new Comparator<Object>() {
            @Override
            public int compare(Object left, Object right) {
                int a = intValue(callFirst(left, "getSortSequence"), Integer.MAX_VALUE);
                int b = intValue(callFirst(right, "getSortSequence"), Integer.MAX_VALUE);
                if (a != b) {
                    return a < b ? -1 : 1;
                }
                long ao = longValue(callFirst(left, "getColumnOrder"), 0L);
                long bo = longValue(callFirst(right, "getColumnOrder"), 0L);
                return ao == bo ? 0 : (ao < bo ? -1 : 1);
            }
        });

        for (Object column : sorted) {
            int sequence = intValue(callFirst(column, "getSortSequence"), 0);
            long direction = longValue(callFirst(column, "getSortDirection"), 0L);
            String label = firstNonEmpty(
                    safeString(callFirst(column, "getColumnLabel")),
                    safeString(callFirst(column, "getColumnName")),
                    "Field " + safeString(callFirst(column, "getDataFieldID")),
                    "Column " + safeString(callFirst(column, "getColumnID")));
            String id = firstNonEmpty(
                    safeString(callFirst(column, "getDataFieldID")),
                    safeString(callFirst(column, "getColumnID")));
            List<String> parts = new ArrayList<String>();
            parts.add("Column " + label);
            if (direction != 0L) {
                parts.add(sortDirectionText(direction));
            }
            rows.add(new InsightRow("Table sort", sequence > 0 ? "Sort " + sequence : "Sort", id, join(parts, ", ")));
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

    /**
     * Developer Studio's table/sort property objects often have a dense toString()
     * like "... Qualification nullColumns [Status = OID = 123Name = Login Name...".
     * Turn that into readable Object Insight rows instead of one long line.
     */
    private boolean addStructuredTableTextRows(List<InsightRow> rows, String text, boolean allowQualification) {
        if (text == null) {
            return false;
        }
        String compact = text.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (compact.length() == 0) {
            return false;
        }
        String lower = compact.toLowerCase(Locale.ROOT);
        boolean added = false;

        int columnsPos = lower.indexOf("columns");
        if (allowQualification && lower.indexOf("qualification") >= 0 && !hasTableQualification(rows)) {
            int qPos = lower.indexOf("qualification");
            int qStart = qPos + "qualification".length();
            int qEnd = columnsPos > qStart ? columnsPos : compact.length();
            String q = cleanQualificationText(compact.substring(qStart, qEnd));
            if (q.length() > 0) {
                rows.add(new InsightRow("Table", "Qualification", "", q));
                added = true;
            }
        }

        if (columnsPos >= 0) {
            String colText = compact.substring(columnsPos + "columns".length()).trim();
            String[] chunks = colText.split("\\[");
            for (int i = 0; i < chunks.length; i++) {
                String chunk = chunks[i] == null ? "" : chunks[i].trim();
                if (chunk.length() == 0) {
                    continue;
                }
                int close = chunk.indexOf(']');
                if (close >= 0) {
                    chunk = chunk.substring(0, close).trim();
                }
                if (addStructuredSortRow(rows, chunk)) {
                    added = true;
                }
            }
        }
        return added;
    }

    private boolean hasTableForm(List<InsightRow> rows) {
        for (InsightRow row : rows) {
            if ("Table".equals(row.category) && "Form".equals(row.name)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasTableQualification(List<InsightRow> rows) {
        for (InsightRow row : rows) {
            if ("Table".equals(row.category) && "Qualification".equals(row.name)) {
                return true;
            }
        }
        return false;
    }

    private boolean addStructuredSortRow(List<InsightRow> rows, String chunk) {
        if (chunk == null) {
            return false;
        }
        String text = chunk.trim();
        if (text.length() == 0) {
            return false;
        }
        String oid = extractStructuredValue(text, "OID");
        String name = firstNonEmpty(extractStructuredValue(text, "Name"), extractColumnPrefix(text));
        String order = extractStructuredValue(text, "Order");
        String sequence = extractStructuredValue(text, "Sequence");
        String sortDir = firstNonEmpty(extractStructuredValue(text, "Sort dir"), extractStructuredValue(text, "Sort direction"), extractStructuredValue(text, "Direction"));

        if (name.length() == 0 && oid.length() == 0 && order.length() == 0 && sequence.length() == 0 && sortDir.length() == 0) {
            return false;
        }
        if (name.length() == 0) {
            name = "Column";
        }
        List<String> parts = new ArrayList<String>();
        parts.add("Column " + name);
        if (order.length() > 0) {
            parts.add("Column order " + order);
        }
        if (sortDir.length() > 0) {
            Long dir = toLong(sortDir);
            parts.add(dir == null ? sortDir : sortDirectionText(dir.longValue()));
        }
        String rowName = sequence.length() > 0 ? "Sort " + sequence : "Sort";
        rows.add(new InsightRow("Table sort", rowName, oid, join(parts, ", ")));
        return true;
    }

    private String extractColumnPrefix(String text) {
        int firstKey = firstKeyPosition(text);
        String prefix = firstKey <= 0 ? text : text.substring(0, firstKey);
        prefix = prefix.replace('[', ' ').replace(']', ' ').trim();
        while (prefix.endsWith("=") || prefix.endsWith(":")) {
            prefix = prefix.substring(0, prefix.length() - 1).trim();
        }
        return prefix;
    }

    private int firstKeyPosition(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int best = -1;
        String[] keys = structuredKeys();
        for (int i = 0; i < keys.length; i++) {
            int pos = lower.indexOf(keys[i].toLowerCase(Locale.ROOT));
            if (pos >= 0 && (best < 0 || pos < best)) {
                best = pos;
            }
        }
        return best;
    }

    private String extractStructuredValue(String text, String key) {
        if (text == null || key == null) {
            return "";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        String needle = key.toLowerCase(Locale.ROOT);
        int keyPos = lower.indexOf(needle);
        if (keyPos < 0) {
            return "";
        }
        int start = keyPos + key.length();
        int equals = text.indexOf('=', start);
        if (equals >= 0 && equals - start < 8) {
            start = equals + 1;
        }
        int end = text.length();
        String[] keys = structuredKeys();
        for (int i = 0; i < keys.length; i++) {
            if (keys[i].equalsIgnoreCase(key)) {
                continue;
            }
            int pos = lower.indexOf(keys[i].toLowerCase(Locale.ROOT), start);
            if (pos >= 0 && pos < end) {
                end = pos;
            }
        }
        String value = text.substring(start, end).trim();
        while (value.startsWith("=") || value.startsWith(":")) {
            value = value.substring(1).trim();
        }
        while (value.endsWith(",") || value.endsWith(";") || value.endsWith("]")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return value;
    }

    private String[] structuredKeys() {
        return new String[] { "Sort direction", "Sort dir", "Sequence", "Order", "Name", "OID", "Column ID", "Field ID", "Direction" };
    }

    private String cleanQualificationText(String text) {
        String q = safeString(text);
        q = q.replaceFirst("(?i)^qualification\\s*", "").trim();
        q = q.replaceFirst("(?i)^=", "").trim();
        if (q.length() == 0 || "null".equalsIgnoreCase(q)) {
            return "No qualification";
        }
        return shortText(q);
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
        return cleanQualificationText(safeString(qualifier));
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
        Long parsed = toLong(value);
        return parsed == null ? fallback : parsed.longValue();
    }

    private Long toLong(Object value) {
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        try {
            if (value != null) {
                String s = String.valueOf(value).trim();
                if (s.length() > 0) {
                    return Long.valueOf(Long.parseLong(s));
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
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
