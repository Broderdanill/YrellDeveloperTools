package se.yrell.developertools.viewactions;

import java.lang.reflect.Method;

import org.eclipse.swt.widgets.Shell;

import com.bmc.arsys.studio.ui.common.properties.AddRemoveViewsForFieldDetails;
import com.bmc.arsys.studio.ui.editors.form.model.FormViewLayout;
import com.bmc.arsys.studio.ui.editors.form.model.UIField;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

public final class RemoveFromViewSupport {
    private RemoveFromViewSupport() {
    }

    public static boolean canRemoveFromView(Object selected) {
        if (!ToolsPreferences.isRemoveFromViewEnabled()) {
            return false;
        }
        UIField field = asUiField(selected);
        if (field == null) {
            return false;
        }
        try {
            AddRemoveViewsForFieldDetails details = field.getViews();
            return details != null
                    && details.currentView != null
                    && details.selectedViews != null
                    && !details.selectedViews.isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean removeFromCurrentView(Object selected, Shell shell) {
        UIField field = asUiField(selected);
        if (field == null) {
            return false;
        }
        try {
            AddRemoveViewsForFieldDetails details = field.getViews();
            if (details == null || details.currentView == null || details.selectedViews == null || details.selectedViews.isEmpty()) {
                return false;
            }
            FormViewLayout currentView = details.currentView;
            Integer vui = Integer.valueOf(currentView.getVui());

            details.addedViews.clear();
            details.removedViews.clear();
            details.removedViews.add(vui);
            field.setViews(details);
            safeCall(field, "setDirty", Boolean.TRUE);
            safeCall(field, "validate");
            Object formView = safeCall(field, "getFormView");
            safeCall(formView, "validate");
            Log.info("Removed selected field from current view VUI " + vui + "; field remains in other views.");
            return true;
        } catch (Throwable t) {
            Log.error("Could not remove selected field from current view", t);
            return false;
        }
    }

    public static UIField asUiField(Object selected) {
        Object current = selected;
        for (int i = 0; i < 8 && current != null; i++) {
            if (current instanceof UIField) {
                return (UIField) current;
            }
            Object next = first(current, "getModel", "getModelObject", "getObject", "getData", "getElement", "getItem");
            if (next == null || next == current) {
                return null;
            }
            current = next;
        }
        return null;
    }

    private static Object first(Object target, String... methodNames) {
        for (int i = 0; i < methodNames.length; i++) {
            Object value = safeCall(target, methodNames[i]);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object safeCall(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }
        try {
            Method m = target.getClass().getMethod(methodName, new Class[0]);
            m.setAccessible(true);
            return m.invoke(target, new Object[0]);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object safeCall(Object target, String methodName, Object arg) {
        if (target == null || methodName == null) {
            return null;
        }
        try {
            Method[] methods = target.getClass().getMethods();
            for (int i = 0; i < methods.length; i++) {
                Method m = methods[i];
                if (!methodName.equals(m.getName()) || m.getParameterTypes().length != 1) {
                    continue;
                }
                Class<?> p = m.getParameterTypes()[0];
                if ((p == boolean.class || p == Boolean.class) && arg instanceof Boolean) {
                    m.setAccessible(true);
                    return m.invoke(target, new Object[] { arg });
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
