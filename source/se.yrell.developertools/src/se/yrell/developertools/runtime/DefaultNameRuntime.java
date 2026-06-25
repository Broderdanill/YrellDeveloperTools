package se.yrell.developertools.runtime;

import java.lang.reflect.Method;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.bmc.arsys.api.ColumnFieldLimit;
import com.bmc.arsys.api.FieldLimit;
import com.bmc.arsys.api.TableFieldLimit;
import com.bmc.arsys.studio.model.internal.helper.FieldHelper;
import com.bmc.arsys.studio.model.store.IFieldObject;
import com.bmc.arsys.studio.model.store.IFormObject;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/**
 * Runtime defaults for names where Developer Studio/BMC uses weak generated values.
 */
public final class DefaultNameRuntime {
    private static final int MAX_COLUMN_NAME_LENGTH = 254;
    private static final int DATA_TYPE_COLUMN = 32;

    private DefaultNameRuntime() {
    }

    public static void applyNewFieldDefaults(IFormObject form, IFieldObject field, Class<?> fieldClass) {
        if (form == null || field == null) {
            return;
        }
        applyTableColumnDatabaseName(form, field, fieldClass, "createNewARField", null, null);
    }

    /**
     * Called from UI/AddFieldToView paths where table columns can be materialized without the normal createNewARField path.
     * Kept Object-based so the woven class only needs a dynamic import of this runtime package.
     */
    public static void applyUiFieldDefaults(Object uiField) {
        if (uiField == null) {
            return;
        }
        try {
            Object fieldObject = invokeNoArg(uiField, "getField");
            if (!(fieldObject instanceof IFieldObject)) {
                return;
            }
            IFieldObject field = (IFieldObject) fieldObject;
            IFormObject form = null;
            Object formView = invokeNoArg(uiField, "getFormView");
            if (formView != null) {
                Object formObject = tryInvokeNoArg(formView, "getARForm");
                if (formObject instanceof IFormObject) {
                    form = (IFormObject) formObject;
                }
                if (form == null) {
                    formObject = tryInvokeNoArg(formView, "getFormObject");
                    if (formObject instanceof IFormObject) {
                        form = (IFormObject) formObject;
                    }
                }
                if (form == null) {
                    Object parentForm = tryInvokeNoArg(formView, "getForm");
                    if (parentForm instanceof IFormObject) {
                        form = (IFormObject) parentForm;
                    }
                }
            }
            if (form == null) {
                form = tryGetFormFromField(field);
            }
            if (form == null) {
                Log.warn("Default naming could not resolve parent form from UI field " + uiField.getClass().getName() + ".");
                return;
            }
            applyTableColumnDatabaseName(form, field, uiField.getClass(), "addUIField", uiField, null);
        } catch (Throwable t) {
            Log.warn("Could not apply UI field default naming: " + t.getMessage());
        }
    }

    /**
     * Called from UITableField.addColumn(tableColumnData). This gives us direct access
     * to both the parent table UI field and the created UI column field, which is the
     * most reliable path for table-column default names.
     */
    public static void applyTableColumnDefaults(Object tableUiField, Object columnUiField) {
        if (columnUiField == null) {
            return;
        }
        try {
            Object fieldObject = invokeNoArg(columnUiField, "getField");
            if (!(fieldObject instanceof IFieldObject)) {
                return;
            }
            IFieldObject columnField = (IFieldObject) fieldObject;
            IFieldObject tableField = null;
            if (tableUiField != null) {
                Object tableFieldObject = tryInvokeNoArg(tableUiField, "getField");
                if (tableFieldObject instanceof IFieldObject) {
                    tableField = (IFieldObject) tableFieldObject;
                }
            }
            IFormObject form = resolveFormFromUiField(columnUiField);
            if (form == null && tableUiField != null) {
                form = resolveFormFromUiField(tableUiField);
            }
            if (form == null) {
                form = tryGetFormFromField(columnField);
            }
            if (form == null && tableField != null) {
                form = tryGetFormFromField(tableField);
            }
            if (form == null) {
                Log.warn("Default naming could not resolve parent form for table column added through UITableField.addColumn.");
                return;
            }
            applyTableColumnDatabaseName(form, columnField, columnUiField.getClass(), "tableAddColumn", columnUiField, tableField);
        } catch (Throwable t) {
            Log.warn("Could not apply table-column default naming from UITableField.addColumn: " + t.getMessage());
        }
    }

    private static void applyTableColumnDatabaseName(IFormObject form, IFieldObject field, Class<?> sourceClass, String source, Object uiField, IFieldObject parentTableOverride) {
        if (form == null || field == null) {
            return;
        }
        if (!ToolsPreferences.isTableColumnDbNameEnabled()) {
            return;
        }
        if (!isTableColumn(field, sourceClass)) {
            return;
        }
        try {
            String current = safeTrim(FieldHelper.getFieldName(field));
            if (!shouldSetColumnDatabaseName(field, current)) {
                Log.info("Default naming left table column database name unchanged ('" + current + "') because it does not look like a newly generated value.");
                return;
            }

            ColumnContext ctx = readColumnContext(form, field, uiField, parentTableOverride);
            String pattern = ToolsPreferences.getTableColumnDbNamePattern();
            String dbName = renderPattern(pattern, ctx);
            if (dbName.length() == 0) {
                return;
            }
            if (dbName.equals(current)) {
                return;
            }

            FieldHelper.setFieldName(field, dbName);
            field.setDirty(true);
            Log.info("Set table column database name to '" + dbName + "' for column '" + ctx.fieldName + "' using " + source + ".");
        } catch (Throwable t) {
            Log.warn("Could not set default table column database name: " + t.getMessage());
        }
    }

    private static boolean isTableColumn(IFieldObject field, Class<?> sourceClass) {
        try {
            if (field.getDataType() == DATA_TYPE_COLUMN) {
                return true;
            }
        } catch (Throwable ignored) {
            // Continue with class-name checks.
        }
        String name = sourceClass == null ? "" : sourceClass.getSimpleName();
        return "UIColumnField".equals(name) || "UIPageDataField".equals(name) || name.toLowerCase(Locale.ROOT).contains("column");
    }

    private static boolean shouldSetColumnDatabaseName(IFieldObject field, String current) {
        if (current.length() == 0) {
            return true;
        }
        try {
            Object state = field.getState();
            if (state != null && "NEW".equalsIgnoreCase(String.valueOf(state))) {
                return true;
            }
        } catch (Throwable ignored) {
            // Fall back to weak-name logic.
        }
        return isWeakColumnName(current);
    }

    private static ColumnContext readColumnContext(IFormObject form, IFieldObject field, Object uiField, IFieldObject parentTableOverride) {
        ColumnContext ctx = new ColumnContext();
        ctx.form = safeFormName(form);
        ctx.fieldName = safeTrim(FieldHelper.getFieldName(field));
        if (ctx.fieldName.length() == 0) {
            ctx.fieldName = safeTrim(field.getName());
        }
        if (ctx.fieldName.length() == 0) {
            ctx.fieldName = "field";
        }
        try {
            ctx.fieldId = Integer.toString(field.getFieldID());
        } catch (Throwable ignored) {
            ctx.fieldId = "";
        }

        try {
            FieldLimit limit = field.getFieldLimit();
            if (limit instanceof ColumnFieldLimit) {
                ColumnFieldLimit columnLimit = (ColumnFieldLimit) limit;
                IFieldObject parent = parentTableOverride;
                if (parent == null) {
                    parent = safeGetField(form, columnLimit.getParent());
                }
                if (parent == null && uiField != null) {
                    parent = readParentTableFieldFromUiColumn(uiField, form);
                }
                if (parent != null) {
                    ctx.remoteForm = readTableRemoteForm(parent);
                }
                IFieldObject remote = readRemoteFieldObject(field, form);
                if (remote == null && uiField != null) {
                    remote = readRemoteFieldObjectFromUiColumn(uiField);
                }
                if (remote != null) {
                    ctx.remoteFieldName = safeTrim(FieldHelper.getFieldName(remote));
                    if (ctx.remoteFieldName.length() == 0) {
                        ctx.remoteFieldName = safeTrim(remote.getName());
                    }
                    if (ctx.remoteForm.length() == 0) {
                        IFormObject remoteForm = tryGetFormFromField(remote);
                        if (remoteForm != null) {
                            ctx.remoteForm = safeFormName(remoteForm);
                        }
                    }
                    if (isWeakColumnName(ctx.fieldName)) {
                        ctx.fieldName = ctx.remoteFieldName;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Best effort; defaults below handle missing values.
        }

        if (ctx.remoteForm.length() == 0) {
            ctx.remoteForm = ctx.form;
        }
        if (ctx.remoteFieldName.length() == 0) {
            ctx.remoteFieldName = ctx.fieldName;
        }
        if (isWeakColumnName(ctx.fieldName) && ctx.remoteFieldName.length() > 0) {
            ctx.fieldName = ctx.remoteFieldName;
        }
        return ctx;
    }

    private static IFormObject resolveFormFromUiField(Object uiField) {
        if (uiField == null) {
            return null;
        }
        try {
            Object formView = tryInvokeNoArg(uiField, "getFormView");
            if (formView != null) {
                Object formObject = tryInvokeNoArg(formView, "getARForm");
                if (formObject instanceof IFormObject) {
                    return (IFormObject) formObject;
                }
                formObject = tryInvokeNoArg(formView, "getFormObject");
                if (formObject instanceof IFormObject) {
                    return (IFormObject) formObject;
                }
            }
            Object parentForm = tryInvokeNoArg(uiField, "getForm");
            if (parentForm instanceof IFormObject) {
                return (IFormObject) parentForm;
            }
        } catch (Throwable ignored) {
            // Best effort.
        }
        return null;
    }

    private static IFieldObject readParentTableFieldFromUiColumn(Object uiField, IFormObject form) {
        try {
            Object parentId = tryInvokeNoArg(uiField, "getDisplayParentId");
            if (parentId instanceof Number && form != null) {
                IFieldObject parent = safeGetField(form, ((Number) parentId).intValue());
                if (parent != null) {
                    return parent;
                }
            }
        } catch (Throwable ignored) {
            // Continue.
        }
        String[] methods = new String[] { "getParent", "getParentField", "getTableField", "getTable", "getContainer" };
        for (String method : methods) {
            try {
                Object candidate = tryInvokeNoArg(uiField, method);
                IFieldObject field = fieldObjectFromUiObject(candidate);
                if (field != null) {
                    return field;
                }
            } catch (Throwable ignored) {
                // Continue.
            }
        }
        return null;
    }

    private static IFieldObject readRemoteFieldObjectFromUiColumn(Object uiField) {
        try {
            Object remote = tryInvokeNoArg(uiField, "getRemoteFieldObject");
            return remote instanceof IFieldObject ? (IFieldObject) remote : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IFieldObject fieldObjectFromUiObject(Object uiObject) {
        if (uiObject == null) {
            return null;
        }
        if (uiObject instanceof IFieldObject) {
            return (IFieldObject) uiObject;
        }
        try {
            Object field = tryInvokeNoArg(uiObject, "getField");
            return field instanceof IFieldObject ? (IFieldObject) field : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String readTableRemoteForm(IFieldObject parent) {
        try {
            Method method = parent.getClass().getMethod("getRemoteFormName");
            Object value = method.invoke(parent);
            if (value != null && String.valueOf(value).trim().length() > 0) {
                return String.valueOf(value).trim();
            }
        } catch (Throwable ignored) {
            // Continue.
        }
        try {
            FieldLimit limit = parent.getFieldLimit();
            if (limit instanceof TableFieldLimit) {
                String value = safeTrim(((TableFieldLimit) limit).getForm());
                if (value.length() > 0) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
            // Continue.
        }
        return "";
    }

    private static IFieldObject readRemoteFieldObject(IFieldObject field, IFormObject form) {
        try {
            Method method = field.getClass().getMethod("getRemoteFieldObject", IFormObject.class);
            Object remote = method.invoke(field, form);
            return remote instanceof IFieldObject ? (IFieldObject) remote : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IFieldObject safeGetField(IFormObject form, int id) {
        try {
            return form.getField(id);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static IFormObject tryGetFormFromField(IFieldObject field) {
        try {
            Object form = invokeNoArg(field, "getOriginalForm");
            return form instanceof IFormObject ? (IFormObject) form : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) throws Exception {
        Method method = target.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(target);
    }

    private static Object tryInvokeNoArg(Object target, String methodName) {
        try {
            return invokeNoArg(target, methodName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String renderPattern(String pattern, ColumnContext ctx) {
        Map<String, String> tokens = new HashMap<String, String>();
        tokens.put("form", ctx.form);
        tokens.put("remote_form", ctx.remoteForm);
        tokens.put("remoteForm", ctx.remoteForm);
        tokens.put("field", ctx.fieldName);
        tokens.put("field_name", ctx.fieldName);
        tokens.put("fieldName", ctx.fieldName);
        tokens.put("remote_field", ctx.remoteFieldName);
        tokens.put("remote_field_name", ctx.remoteFieldName);
        tokens.put("remoteField", ctx.remoteFieldName);
        tokens.put("remoteFieldName", ctx.remoteFieldName);
        tokens.put("field_id", ctx.fieldId);
        tokens.put("fieldId", ctx.fieldId);

        String result = pattern == null || pattern.trim().length() == 0 ? "col_{remote_form}_{field_name}" : pattern.trim();
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            result = result.replace("{" + token.getKey() + "}", token.getValue() == null ? "" : token.getValue());
            result = result.replace("${" + token.getKey() + "}", token.getValue() == null ? "" : token.getValue());
        }
        result = normalizeWholeName(result);
        if (result.length() > MAX_COLUMN_NAME_LENGTH) {
            result = result.substring(0, MAX_COLUMN_NAME_LENGTH);
            result = trimUnderscores(result);
        }
        return result;
    }

    private static boolean isWeakColumnName(String value) {
        String v = normalizePart(value);
        return v.length() == 0 || v.matches("column_?\\d*") || v.matches("col_?\\d*") || v.matches("field_?\\d*") || v.matches("new_?column_?\\d*");
    }

    private static String normalizeWholeName(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() == 0) {
            return "";
        }
        text = normalizeUnicode(text).toLowerCase(Locale.ROOT);
        text = text.replaceAll("[^a-z0-9]+", "_");
        while (text.contains("__")) {
            text = text.replace("__", "_");
        }
        return trimUnderscores(text);
    }

    private static String normalizePart(String value) {
        String text = value == null ? "" : value.trim();
        if (text.length() == 0) {
            return "value";
        }
        text = normalizeUnicode(text).toLowerCase(Locale.ROOT);
        text = text.replaceAll("[^a-z0-9]+", "_");
        text = trimUnderscores(text);
        return text.length() == 0 ? "value" : text;
    }

    private static String normalizeUnicode(String text) {
        String result = text.replace('å', 'a').replace('ä', 'a').replace('ö', 'o')
                .replace('Å', 'A').replace('Ä', 'A').replace('Ö', 'O');
        return Normalizer.normalize(result, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static String trimUnderscores(String value) {
        String text = value == null ? "" : value;
        while (text.startsWith("_")) {
            text = text.substring(1);
        }
        while (text.endsWith("_")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safeFormName(IFormObject form) {
        try {
            return form.getName();
        } catch (Throwable ignored) {
            return "form";
        }
    }

    private static final class ColumnContext {
        String form = "form";
        String remoteForm = "";
        String fieldName = "field";
        String remoteFieldName = "";
        String fieldId = "";
    }
}
