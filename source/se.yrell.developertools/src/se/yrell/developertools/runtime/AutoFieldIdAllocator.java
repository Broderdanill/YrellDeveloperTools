package se.yrell.developertools.runtime;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.bmc.arsys.api.ARServerUser;
import com.bmc.arsys.api.Entry;
import com.bmc.arsys.api.Field;
import com.bmc.arsys.api.OutputInteger;
import com.bmc.arsys.api.QualifierInfo;
import com.bmc.arsys.api.Value;
import com.bmc.arsys.studio.model.store.IEntryStore;
import com.bmc.arsys.studio.model.store.IFieldObject;
import com.bmc.arsys.studio.model.store.IFormObject;
import com.bmc.arsys.studio.model.store.IStore;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/**
 * Called from woven Developer Studio code just after the base AR field object has
 * been initialized, but before IFormObject.addField(...) assigns Developer
 * Studio's normal next ID.
 */
public final class AutoFieldIdAllocator {
    private static final int MIN_DEVELOPER_ID = 10;
    private static final int MAX_DEVELOPER_ID = 21; // AR field id is a signed int. 22YYMMDDNN would overflow.
    private static final int FIRST_SEQUENCE = 1;
    private static final int LAST_SEQUENCE = 99;
    private static final int MAX_DAY_ROLLOVER_ATTEMPTS = 370;
    private static final String METADATA_FIELD_FORM = "AR System Metadata: field";
    private static final String METADATA_FIELD_FORM_LEGACY = "field";
    private static final String METADATA_FIELD_ID_NAME = "Field ID";

    private static final Object LOCK = new Object();
    private static final Map<String, DayCache> DAY_CACHE = new HashMap<String, DayCache>();
    private static final Map<String, Integer> FIELD_ID_FIELD_CACHE = new HashMap<String, Integer>();

    private AutoFieldIdAllocator() {
    }

    public static void assignIfNeeded(IFormObject form, IFieldObject field, Class<?> fieldClass) {
        if (form == null || field == null || fieldClass == null) {
            return;
        }

        if (!ToolsPreferences.isAutoFieldIdEnabled()) {
            return;
        }

        String developerIdText = ToolsPreferences.getAutoFieldDeveloperId();
        int developerId = parseDeveloperId(developerIdText);
        if (developerId < 0) {
            Log.warn("Auto Field ID is enabled, but Developer ID is invalid or missing. Expected 10-21, got '" + developerIdText + "'.");
            return;
        }

        if (ToolsPreferences.isAutoFieldIdSkipPanelsEnabled() && isPanelClass(fieldClass)) {
            return;
        }

        synchronized (LOCK) {
            try {
                int existingId = field.getFieldID();

                // Do not override copied fields or BMC's hard-coded reserved fields, for example Results List 1020 and Alert List 706.
                // New drag-and-drop fields have ID 0 here; copied fields normally already have an ID and should keep it.
                if (existingId != 0) {
                    observeExistingId(existingId, developerId, form);
                    return;
                }

                int nextId = calculateNextId(form, developerId);
                if (nextId <= 0) {
                    Log.warn("Could not allocate an Auto Field ID for developer " + developerId + ". Leaving Developer Studio default behavior unchanged.");
                    return;
                }
                if (form.getField(nextId) != null) {
                    Log.warn("Calculated Auto Field ID " + nextId + " already exists on the current form. Leaving Developer Studio default behavior unchanged.");
                    return;
                }

                field.setFieldID(nextId);
                field.setReservedIDOK(false);
                field.setDirty(true);
                rememberAssignedId(nextId, developerId, form);

                int followingId = calculateFollowingId(nextId, developerId);
                if (followingId > 0) {
                    ToolsPreferences.setAutoFieldNextId(Integer.toString(followingId));
                }

                Log.info("Assigned field id " + nextId + " to '" + safeName(field) + "' on form '" + safeFormName(form) + "'. Next calculated Field ID is " + ToolsPreferences.getAutoFieldNextId() + ".");
            } catch (Throwable t) {
                Log.error("Failed to assign Auto Field ID. Leaving Developer Studio default behavior unchanged.", t);
            }
        }
    }

    private static int calculateNextId(IFormObject form, int developerId) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        Set<Integer> currentFormUsed = collectCurrentFormUsedFieldIds(form);
        for (int dayOffset = 0; dayOffset < MAX_DAY_ROLLOVER_ATTEMPTS; dayOffset++) {
            LocalDate day = today.plusDays(dayOffset);
            long first = buildId(developerId, day, FIRST_SEQUENCE);
            long last = buildId(developerId, day, LAST_SEQUENCE);
            if (first <= 0 || last > Integer.MAX_VALUE) {
                continue;
            }
            Set<Integer> used = new HashSet<Integer>();
            used.addAll(currentFormUsed);
            used.addAll(fetchSessionAssignedIdsForDay(form, developerId, day));
            used.addAll(fetchMetadataUsedIdsForDay(form, developerId, day));

            for (int sequence = FIRST_SEQUENCE; sequence <= LAST_SEQUENCE; sequence++) {
                long candidateLong = buildId(developerId, day, sequence);
                if (candidateLong > 0 && candidateLong <= Integer.MAX_VALUE) {
                    int candidate = (int) candidateLong;
                    if (!used.contains(Integer.valueOf(candidate))) {
                        ToolsPreferences.setAutoFieldNextId(Integer.toString(candidate));
                        return candidate;
                    }
                }
            }
        }
        return -1;
    }

    private static Set<Integer> fetchSessionAssignedIdsForDay(IFormObject form, int developerId, LocalDate day) {
        String key = sessionKey(form, developerId, day);
        DayCache cached = DAY_CACHE.get(key);
        return cached == null ? new HashSet<Integer>() : new HashSet<Integer>(cached.usedIds);
    }

    private static Set<Integer> fetchMetadataUsedIdsForDay(IFormObject form, int developerId, LocalDate day) {
        Set<Integer> used = new HashSet<Integer>();
        try {
            ARServerUser context = getContext(form);
            IStore store = safeStore(form);
            if (context == null && !(store instanceof IEntryStore)) {
                Log.warn("Could not read ARServerUser or IEntryStore context; Auto Field ID will only check current form and session-assigned ids this time.");
                return used;
            }

            long first = buildId(developerId, day, FIRST_SEQUENCE);
            long upperExclusive = buildId(developerId, day, LAST_SEQUENCE + 1);
            String qualification = "'Field ID' >= " + first + " AND 'Field ID' < " + upperExclusive;

            String[] formNames = new String[] { METADATA_FIELD_FORM, METADATA_FIELD_FORM_LEGACY };
            Throwable lastFailure = null;
            for (String metadataForm : formNames) {
                try {
                    int fieldIdField = getFieldIdFieldId(context, store, metadataForm);
                    if (fieldIdField <= 0) {
                        continue;
                    }

                    QualifierInfo qual = context != null
                            ? context.parseQualification(metadataForm, qualification)
                            : parseQualificationViaStore(store, metadataForm, qualification);
                    int[] fields = new int[] { fieldIdField };
                    OutputInteger numMatches = new OutputInteger();
                    List<Entry> entries;
                    if (store instanceof IEntryStore) {
                        entries = ((IEntryStore) store).getListEntryObjects(metadataForm, qual, 0, 0, null, fields, false, numMatches);
                    } else {
                        entries = context.getListEntryObjects(metadataForm, qual, 0, 0, null, fields, false, numMatches);
                    }
                    if (entries != null) {
                        for (Entry entry : entries) {
                            addFieldIdFromEntry(used, entry, fieldIdField, developerId, day);
                        }
                    }
                    Log.info("Auto Field ID metadata scan on '" + metadataForm + "' found " + used.size() + " used id(s) for " + developerId + " on " + day + " at " + safeServerKey(form) + ".");
                    return used;
                } catch (Throwable t) {
                    lastFailure = t;
                }
            }

            if (lastFailure != null) {
                Log.warn("Could not scan AR metadata form for Auto Field ID. Tried '" + METADATA_FIELD_FORM + "' and '" + METADATA_FIELD_FORM_LEGACY + "'. Current-form and session-assigned checks will still be used. Cause: " + lastFailure.getMessage());
            }
        } catch (Throwable t) {
            Log.warn("Could not scan AR metadata for Auto Field ID. Current-form and session-assigned checks will still be used. Cause: " + t.getMessage());
        }
        return used;
    }

    private static void addFieldIdFromEntry(Set<Integer> used, Entry entry, int fieldIdField, int developerId, LocalDate day) {
        if (entry == null) {
            return;
        }
        Value value = entry.get(Integer.valueOf(fieldIdField));
        if (value == null) {
            // Best-effort fallback if the server returned more fields than requested.
            for (Value candidate : entry.values()) {
                addIfGeneratedId(used, candidate, developerId, day);
            }
            return;
        }
        addIfGeneratedId(used, value, developerId, day);
    }

    private static void addIfGeneratedId(Set<Integer> used, Value value, int developerId, LocalDate day) {
        if (value == null || value.getValue() == null) {
            return;
        }
        try {
            int id;
            Object raw = value.getValue();
            if (raw instanceof Number) {
                id = ((Number) raw).intValue();
            } else {
                id = Integer.parseInt(String.valueOf(raw).trim());
            }
            if (isIdForDay(id, developerId, day)) {
                used.add(Integer.valueOf(id));
            }
        } catch (Throwable ignored) {
            // Ignore non-integer values.
        }
    }

    private static int getFieldIdFieldId(ARServerUser context, IStore store, String metadataForm) throws Exception {
        String key = safeServerKey(store, context) + ":" + metadataForm;
        Integer cached = FIELD_ID_FIELD_CACHE.get(key);
        if (cached != null && cached.intValue() > 0) {
            return cached.intValue();
        }
        if (context == null) {
            return -1;
        }
        List<Field> fields = context.getListFieldObjects(metadataForm);
        if (fields != null) {
            for (Field field : fields) {
                if (field != null && METADATA_FIELD_ID_NAME.equalsIgnoreCase(field.getName())) {
                    int id = field.getFieldID();
                    FIELD_ID_FIELD_CACHE.put(key, Integer.valueOf(id));
                    return id;
                }
            }
        }
        return -1;
    }

    private static QualifierInfo parseQualificationViaStore(IStore store, String formName, String qualification) throws Exception {
        ARServerUser context = getContext(store);
        if (context != null) {
            return context.parseQualification(formName, qualification);
        }
        throw new IllegalStateException("No ARServerUser context available for parsing metadata qualification");
    }

    private static ARServerUser getContext(IFormObject form) {
        return getContext(safeStore(form));
    }

    private static ARServerUser getContext(IStore store) {
        if (store == null) {
            return null;
        }
        try {
            Method method = store.getClass().getMethod("getContext");
            Object result = method.invoke(store);
            return result instanceof ARServerUser ? (ARServerUser) result : null;
        } catch (Throwable ignored) {
            // Continue with field lookup fallback below.
        }
        try {
            java.lang.reflect.Field field = store.getClass().getDeclaredField("context");
            field.setAccessible(true);
            Object result = field.get(store);
            return result instanceof ARServerUser ? (ARServerUser) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IStore safeStore(IFormObject form) {
        try {
            return form == null ? null : form.getStore();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Set<Integer> collectCurrentFormUsedFieldIds(IFormObject form) {
        Set<Integer> usedIds = new HashSet<Integer>();
        addFieldIds(usedIds, safeFields(form));
        try {
            addFieldIds(usedIds, form.getDeletedFields());
        } catch (Throwable ignored) {
            // Deleted fields are best-effort. Normal fields are enough for safety in most cases.
        }
        return usedIds;
    }

    private static void observeExistingId(int existingId, int developerId, IFormObject form) {
        if (!isValidGeneratedId(existingId, developerId)) {
            return;
        }
        rememberAssignedId(existingId, developerId, form);
    }

    private static void rememberAssignedId(int id, int developerId, IFormObject form) {
        LocalDate day;
        try {
            day = dateFromGeneratedId(id);
        } catch (Throwable t) {
            return;
        }
        String key = sessionKey(form, developerId, day);
        DayCache cached = DAY_CACHE.get(key);
        if (cached == null) {
            cached = new DayCache(new HashSet<Integer>());
            DAY_CACHE.put(key, cached);
        }
        cached.usedIds.add(Integer.valueOf(id));
    }

    private static int calculateFollowingId(int id, int developerId) {
        if (!isValidGeneratedId(id, developerId)) {
            return firstIdForToday(developerId);
        }
        LocalDate day = dateFromGeneratedId(id);
        int sequence = id % 100;
        if (sequence < LAST_SEQUENCE) {
            long next = ((long) id) + 1L;
            return next <= Integer.MAX_VALUE ? (int) next : -1;
        }

        for (int dayOffset = 1; dayOffset <= MAX_DAY_ROLLOVER_ATTEMPTS; dayOffset++) {
            LocalDate nextDay = day.plusDays(dayOffset);
            long candidate = buildId(developerId, nextDay, FIRST_SEQUENCE);
            if (candidate > 0 && candidate <= Integer.MAX_VALUE) {
                return (int) candidate;
            }
        }
        return -1;
    }

    private static int firstIdForToday(int developerId) {
        LocalDate day = LocalDate.now(ZoneId.systemDefault());
        for (int dayOffset = 0; dayOffset < MAX_DAY_ROLLOVER_ATTEMPTS; dayOffset++) {
            LocalDate candidateDay = day.plusDays(dayOffset);
            long candidate = buildId(developerId, candidateDay, FIRST_SEQUENCE);
            if (candidate > 0 && candidate <= Integer.MAX_VALUE) {
                return (int) candidate;
            }
        }
        return -1;
    }

    private static long buildId(int developerId, LocalDate day, int sequence) {
        int yy = day.getYear() % 100;
        long prefix = developerId * 1_000_000L
                + yy * 10_000L
                + day.getMonthValue() * 100L
                + day.getDayOfMonth();
        return prefix * 100L + sequence;
    }

    private static boolean isIdForDay(int id, int developerId, LocalDate day) {
        long first = buildId(developerId, day, FIRST_SEQUENCE);
        long last = buildId(developerId, day, LAST_SEQUENCE);
        return id >= first && id <= last;
    }

    private static boolean isValidGeneratedId(int id, int developerId) {
        return parseNextFieldId(Integer.toString(id), developerId) > 0;
    }

    private static int parseNextFieldId(String nextIdText, int developerId) {
        if (nextIdText == null) {
            return -1;
        }
        String text = nextIdText.trim();
        if (!text.matches("\\d{10}") || !text.startsWith(String.format("%02d", developerId))) {
            return -1;
        }
        try {
            long value = Long.parseLong(text);
            if (value <= 0L || value > Integer.MAX_VALUE) {
                return -1;
            }
            int yy = Integer.parseInt(text.substring(2, 4));
            int mm = Integer.parseInt(text.substring(4, 6));
            int dd = Integer.parseInt(text.substring(6, 8));
            int sequence = Integer.parseInt(text.substring(8, 10));
            if (sequence < FIRST_SEQUENCE || sequence > LAST_SEQUENCE) {
                return -1;
            }
            LocalDate.of(2000 + yy, mm, dd);
            return (int) value;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static LocalDate dateFromGeneratedId(int id) {
        String text = String.format("%010d", id);
        int yy = Integer.parseInt(text.substring(2, 4));
        int mm = Integer.parseInt(text.substring(4, 6));
        int dd = Integer.parseInt(text.substring(6, 8));
        return LocalDate.of(2000 + yy, mm, dd);
    }

    private static void addFieldIds(Set<Integer> usedIds, Collection<IFieldObject> fields) {
        if (fields == null) {
            return;
        }
        for (IFieldObject existing : fields) {
            if (existing != null) {
                int id = existing.getFieldID();
                if (id > 0) {
                    usedIds.add(Integer.valueOf(id));
                }
            }
        }
    }

    private static Collection<IFieldObject> safeFields(IFormObject form) {
        try {
            return form.getFields();
        } catch (Throwable t) {
            Log.warn("Could not read fields from form while calculating Auto Field ID: " + t.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private static int parseDeveloperId(String developerIdText) {
        if (developerIdText == null || developerIdText.trim().isEmpty()) {
            return -1;
        }
        try {
            int developerId = Integer.parseInt(developerIdText.trim());
            if (developerId < MIN_DEVELOPER_ID || developerId > MAX_DEVELOPER_ID) {
                return -1;
            }
            return developerId;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isPanelClass(Class<?> fieldClass) {
        String simpleName = fieldClass.getSimpleName();
        return "UIPageField".equals(simpleName) || simpleName.startsWith("UIPageHolder");
    }

    private static String safeName(IFieldObject field) {
        try {
            return field.getName();
        } catch (Throwable ignored) {
            return "<unknown>";
        }
    }

    private static String safeFormName(IFormObject form) {
        try {
            return form.getName();
        } catch (Throwable ignored) {
            return "<unknown>";
        }
    }

    private static String safeServerKey(IFormObject form) {
        IStore store = safeStore(form);
        return safeServerKey(store, getContext(store));
    }

    private static String sessionKey(IFormObject form, int developerId, LocalDate day) {
        return safeServerKey(form) + ":" + developerId + ":" + day.toString();
    }

    private static String safeServerKey(IStore store, ARServerUser context) {
        try {
            if (context != null) {
                return String.valueOf(context.getServer()) + ":" + context.getPort() + ":" + context.getUser();
            }
        } catch (Throwable ignored) {
            // Fall back to store name.
        }
        try {
            return store == null ? "<unknown>" : String.valueOf(store.getName());
        } catch (Throwable ignored) {
            return "<unknown>";
        }
    }

    private static final class DayCache {
        final long loadedAt;
        final Set<Integer> usedIds;

        DayCache(Set<Integer> usedIds) {
            this.loadedAt = System.currentTimeMillis();
            this.usedIds = usedIds == null ? new HashSet<Integer>() : new HashSet<Integer>(usedIds);
        }
    }
}
