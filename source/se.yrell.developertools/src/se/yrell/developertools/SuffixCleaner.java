package se.yrell.developertools;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.bmc.arsys.api.Value;
import com.bmc.arsys.api.ViewDisplayPropertyMap;
import com.bmc.arsys.studio.model.ar.ARView;
import com.bmc.arsys.studio.model.store.IFieldObject;
import com.bmc.arsys.studio.model.store.IFormObject;
import com.bmc.arsys.studio.model.store.IModelObject;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.type.IARSystemTypes;

public final class SuffixCleaner {
    private static final String SUFFIX = "__c";
    private static final Integer VIEW_LABEL_PROPERTY = Integer.valueOf(20);

    private SuffixCleaner() {
    }

    public static int cleanAndPersist(IFormObject form) {
        if (!ToolsPreferences.isRemoveCustomSuffixEnabled()
                || !ToolsPreferences.isRemoveCustomSuffixPostSaveCleanupEnabled()
                || form == null || form.getStore() == null) {
            return 0;
        }
        int changed = 0;
        changed += cleanFormName(form);
        changed += cleanFields(form);
        changed += cleanViews(form);
        return changed;
    }

    private static int cleanFormName(IFormObject form) {
        String name = form.getName();
        String stripped = strip(name);
        if (name == null || name.equals(stripped)) {
            return 0;
        }
        try {
            IStore store = form.getStore();
            // Let Developer Studio/AR Server perform a real rename when possible.
            if (!objectExists(store, stripped)) {
                store.renameObject(IARSystemTypes.FORM, name, stripped);
                form.setNewName(stripped);
                form.setName(stripped);
                return 1;
            }
        } catch (Throwable t) {
            Log.error("Could not rename form " + name + " to " + stripped, t);
        }
        return 0;
    }

    private static int cleanFields(IFormObject form) {
        int changed = 0;
        Collection<IFieldObject> fields = form.getFields();
        if (fields == null || fields.isEmpty()) {
            return 0;
        }
        Set<String> baseNames = new HashSet<String>();
        for (IFieldObject field : fields) {
            String name = safeName(field);
            if (name != null && !name.endsWith(SUFFIX)) {
                baseNames.add(name);
            }
        }
        for (IFieldObject field : fields) {
            String name = safeName(field);
            String stripped = strip(name);
            if (name == null || name.equals(stripped) || baseNames.contains(stripped)) {
                continue;
            }
            try {
                field.setNewName(stripped);
                field.setName(stripped);
                field.setDirty(true);
                form.getStore().storeObject(IARSystemTypes.FIELD, (IModelObject) field, true, true);
                baseNames.add(stripped);
                changed++;
            } catch (Throwable t) {
                Log.error("Could not clean field " + name + " to " + stripped, t);
            }
        }
        return changed;
    }

    private static int cleanViews(IFormObject form) {
        int changed = 0;
        Collection<ARView> views = form.getViews();
        if (views == null || views.isEmpty()) {
            return 0;
        }
        Set<String> baseNames = new HashSet<String>();
        for (ARView view : views) {
            String name = safeName(view);
            if (name != null && !name.endsWith(SUFFIX)) {
                baseNames.add(name);
            }
        }
        for (ARView view : views) {
            boolean viewChanged = false;
            String name = safeName(view);
            String stripped = strip(name);
            if (name != null && !name.equals(stripped) && !baseNames.contains(stripped)) {
                try {
                    view.setNewName(stripped);
                    view.setName(stripped);
                    view.setDirty(true);
                    baseNames.add(stripped);
                    viewChanged = true;
                } catch (Throwable t) {
                    Log.error("Could not update view name " + name + " to " + stripped, t);
                }
            }
            if (cleanViewDisplayLabel(view)) {
                viewChanged = true;
            }
            if (viewChanged) {
                try {
                    form.getStore().storeObject(IARSystemTypes.VIEW, view, true, true);
                    changed++;
                } catch (Throwable t) {
                    Log.error("Could not persist cleaned view " + safeName(view), t);
                }
            }
        }
        return changed;
    }

    private static boolean cleanViewDisplayLabel(ARView view) {
        try {
            ViewDisplayPropertyMap properties = view.getDisplayProperties();
            if (properties == null || properties.isEmpty()) {
                return false;
            }
            Object value = properties.get(VIEW_LABEL_PROPERTY);
            if (!(value instanceof Value)) {
                return false;
            }
            String text = ((Value) value).toString();
            String stripped = strip(text);
            if (text == null || text.equals(stripped)) {
                return false;
            }
            properties.put(VIEW_LABEL_PROPERTY, new Value(stripped));
            view.setDisplayProperties(properties);
            view.setDirty(true);
            return true;
        } catch (Throwable t) {
            Log.error("Could not clean view display label", t);
            return false;
        }
    }

    private static boolean objectExists(IStore store, String formName) {
        try {
            return store.getObject(IARSystemTypes.FORM, formName) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String safeName(IModelObject object) {
        try {
            return object == null ? null : object.getName();
        } catch (Throwable t) {
            return null;
        }
    }

    private static String strip(String value) {
        if (value != null && value.length() > SUFFIX.length() && value.endsWith(SUFFIX)) {
            return value.substring(0, value.length() - SUFFIX.length());
        }
        return value;
    }
}
