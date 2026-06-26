package se.yrell.developertools.runtime;

import com.bmc.arsys.studio.model.store.IFieldObject;
import com.bmc.arsys.studio.model.store.IFormObject;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

public final class CustomSuffixRuntime {
    private CustomSuffixRuntime() {
    }

    /**
     * Wraps BMC Helper.canAppendCustomString(...). When the tool is enabled,
     * custom suffix appending is suppressed. When disabled, original BMC
     * behavior is preserved.
     */
    public static boolean allowAppendCustomString(boolean originalValue) {
        if (!originalValue) {
            return false;
        }
        try {
            if (!ToolsPreferences.isRemoveCustomSuffixEnabled()) {
                return originalValue;
            }
            // Suppress BMC's automatic __c only while Developer Studio is creating
            // new forms/default fields or new fields in the form designer. We do not
            // suppress it during save, rename or existing-object load paths.
            return !isSafeCreationStackForSuffixRemoval();
        } catch (Throwable ignored) {
            // Fail open to Developer Studio's original behavior if preferences are unavailable.
            return originalValue;
        }
    }

    private static boolean isSafeCreationStackForSuffixRemoval() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : stack) {
            String cn = e.getClassName();
            String mn = e.getMethodName();

            // Drag/drop or palette-created fields in the form editor.
            if ("com.bmc.arsys.studio.ui.editors.form.model.UIFieldFactory".equals(cn)
                    && ("createNewARField".equals(mn) || "generateUniqueFieldName".equals(mn)
                        || "createUIField".equals(mn) || "createNewUIField".equals(mn))) {
                return true;
            }

            // FormHelper.generateUniqueName is used by field creation naming.
            if ("com.bmc.arsys.studio.model.internal.helper.FormHelper".equals(cn)
                    && ("generateUniqueName".equals(mn) || "getUniqueName".equals(mn))) {
                return true;
            }

            // Default fields created by the model factory when a new form is created.
            if ("com.bmc.arsys.studio.model.factories.ARFieldFactory".equals(cn)
                    && ("createField".equals(mn) || "createRequestIDField".equals(mn)
                        || "createAssignedToField".equals(mn) || "createSubmitterField".equals(mn)
                        || "createLastModifiedByField".equals(mn) || "createCreateDateField".equals(mn)
                        || "createModifiedDateField".equals(mn) || "createShortDescriptionField".equals(mn)
                        || "createStatusField".equals(mn) || "createStatusHistoryField".equals(mn)
                        || "createRecordIdField".equals(mn))) {
                return true;
            }

            // New-form factory paths. These methods are used while constructing a new form
            // and its default fields, not for saving an existing form.
            if ("com.bmc.arsys.studio.model.factories.ARFormFactory".equals(cn)
                    && (mn.startsWith("create") || "generateUniqueFieldName".equals(mn)
                        || "initializeObjectProperties".equals(mn) || "processSubAdmin".equals(mn))) {
                return true;
            }
        }
        return false;
    }



    /**
     * Cleans only the generated name for a newly created field. This is the preferred
     * path for the __c feature because it runs before the field is added to the form
     * and it does not touch form-save, rename or persisted-object paths.
     */
    public static String cleanNewFieldName(IFormObject form, String generatedName) {
        try {
            if (!ToolsPreferences.isRemoveCustomSuffixEnabled()) {
                return generatedName;
            }
            String cleanedName = stripCustomSuffix(generatedName);
            if (generatedName == null || generatedName.equals(cleanedName)) {
                return generatedName;
            }
            String uniqueName = makeUniqueCleanName(form, null, cleanedName);
            if (!cleanedName.equals(uniqueName)) {
                Log.info("Removed __c from generated new-field name '" + generatedName + "' and made it unique as '" + uniqueName + "'.");
            } else {
                Log.info("Removed __c from generated new-field name '" + generatedName + "' -> '" + cleanedName + "'.");
            }
            return uniqueName;
        } catch (Throwable t) {
            Log.error("Failed to remove __c from generated new-field name. Leaving Developer Studio default name unchanged.", t);
            return generatedName;
        }
    }

    /**
     * Safety hook for the form designer drag-and-drop path. Developer Studio can
     * generate the initial field name through FormHelper.generateUniqueName(...),
     * and that class may already be loaded before our generic canAppend hook is
     * installed. This method is called from UIFieldFactory immediately after the
     * new AR field has been initialized, but before it is added to the form.
     */
    public static void cleanCreatedField(IFormObject form, IFieldObject field, Class<?> fieldClass) {
        if (form == null || field == null) {
            return;
        }
        try {
            if (!ToolsPreferences.isRemoveCustomSuffixEnabled()) {
                return;
            }
            String currentName = field.getName();
            String cleanedName = stripCustomSuffix(currentName);
            if (currentName == null || currentName.equals(cleanedName)) {
                return;
            }
            String uniqueName = makeUniqueCleanName(form, field, cleanedName);
            field.setName(uniqueName);
            field.setNewName(uniqueName);
            field.setDirty(true);
            if (!cleanedName.equals(uniqueName)) {
                Log.info("Removed __c from newly created field '" + currentName + "' and made it unique as '" + uniqueName + "'.");
            } else {
                Log.info("Removed __c from newly created field '" + currentName + "' -> '" + cleanedName + "'.");
            }
        } catch (Throwable t) {
            Log.error("Failed to remove __c from newly created field. Leaving Developer Studio default name unchanged.", t);
        }
    }


    /**
     * Called only from AddFieldToViewCommand for the UI field currently being added
     * to the form designer. This is intentionally broader than the model-state monitor
     * but still safe because the command path represents a just-added UI field.
     */
    public static void cleanUiField(Object uiField) {
        if (uiField == null) {
            return;
        }
        try {
            if (!ToolsPreferences.isRemoveCustomSuffixEnabled()) {
                return;
            }
            java.lang.reflect.Method getField = uiField.getClass().getMethod("getField");
            Object value = getField.invoke(uiField);
            if (value instanceof IFieldObject) {
                IFormObject form = null;
                try {
                    java.lang.reflect.Method getForm = uiField.getClass().getMethod("getARForm");
                    Object formObj = getForm.invoke(uiField);
                    if (formObj instanceof IFormObject) {
                        form = (IFormObject) formObj;
                    }
                } catch (Throwable ignored) {
                    // Form is only needed for collision checks.
                }
                cleanCreatedField(form, (IFieldObject) value, uiField.getClass());
            }
        } catch (Throwable t) {
            Log.warn("Could not remove __c from newly added UI field: " + t.getMessage());
        }
    }

    public static String stripCustomSuffix(String value) {
        if (value != null && value.length() > 3 && value.endsWith("__c")) {
            return value.substring(0, value.length() - 3);
        }
        return value;
    }

    private static String makeUniqueCleanName(IFormObject form, IFieldObject currentField, String baseName) {
        if (baseName == null || baseName.length() == 0) {
            return baseName;
        }
        try {
            if (!fieldNameExists(form, currentField, baseName)) {
                return baseName;
            }
            // BMC can generate "Character Field__c" when "Character Field" already exists.
            // In that case we still remove __c, but pick a clean unique name instead of
            // leaving BMC's suffix in place. Use a simple visible numeric suffix and keep
            // the logic limited to newly created fields only.
            for (int i = 1; i < 10000; i++) {
                String candidate = baseName + " " + i;
                if (!fieldNameExists(form, currentField, candidate)) {
                    return candidate;
                }
            }
        } catch (Throwable ignored) {
            // If we cannot inspect the form, return the cleaned name. This path only runs
            // while a new field is being created, never during save/post-save.
        }
        return baseName;
    }

    private static boolean fieldNameExists(IFormObject form, IFieldObject currentField, String candidateName) {
        try {
            java.util.Collection<IFieldObject> fields = form.getFields();
            if (fields == null || fields.isEmpty()) {
                return false;
            }
            for (IFieldObject existing : fields) {
                if (existing == null || existing == currentField) {
                    continue;
                }
                String existingName = existing.getName();
                if (candidateName.equals(existingName)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // If we cannot inspect existing fields, still clean the newly generated name.
            // BMC has already generated a unique base name, and this hook is limited to
            // new-field creation only.
            return false;
        }
        return false;
    }
}
