package se.yrell.developertools.viewactions;

import java.lang.reflect.Method;
import java.util.Collections;

import org.eclipse.gef.GraphicalViewer;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.bmc.arsys.studio.model.store.IFieldObject;
import com.bmc.arsys.studio.ui.common.properties.AddRemoveViewsForFieldDetails;
import com.bmc.arsys.studio.ui.editors.form.model.FormViewLayout;
import com.bmc.arsys.studio.ui.editors.form.model.FormViewManager;
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
            IFieldObject arField = field.getField();
            if (arField == null) {
                return false;
            }
            int fieldId = arField.getFieldID();
            AddRemoveViewsForFieldDetails details = field.getViews();
            if (details == null || details.currentView == null || details.selectedViews == null || details.selectedViews.isEmpty()) {
                return false;
            }
            FormViewLayout currentView = details.currentView;

            if (executeBmcRemoveCommand(currentView, fieldId)) {
                refreshCurrentView(currentView, fieldId);
                Log.info("Removed selected field " + fieldId + " from current view " + currentView.getVui() + " using BMC RemoveFieldFromViewCommand.");
                return true;
            }

            // Fallback for Developer Studio variants where the GEF command path is not available.
            Integer vui = Integer.valueOf(currentView.getVui());
            details.addedViews.clear();
            details.removedViews.clear();
            details.removedViews.add(vui);
            field.setViews(details);
            currentView.removeUIField(fieldId);
            safeCall(field, "setDirty", Boolean.TRUE);
            safeCall(field, "validate");
            safeCall(currentView, "reorderChildren");
            safeCall(currentView, "validate");
            refreshCurrentView(currentView, fieldId);
            Log.info("Removed selected field " + fieldId + " from current view VUI " + vui + " using fallback update.");
            return true;
        } catch (Throwable t) {
            Log.error("Could not remove selected field from current view", t);
            return false;
        }
    }

    private static boolean executeBmcRemoveCommand(FormViewLayout currentView, int fieldId) {
        try {
            CompoundCommand command = new CompoundCommand("Remove from view");
            FormViewManager.populateCommandsToRemoveFieldsFromView(command, currentView, Collections.singletonList(Integer.valueOf(fieldId)));
            if (command.isEmpty() || !command.canExecute()) {
                return false;
            }
            GraphicalViewer viewer = currentView.getGraphicalViewer();
            if (viewer != null && viewer.getEditDomain() != null && viewer.getEditDomain().getCommandStack() != null) {
                CommandStack stack = viewer.getEditDomain().getCommandStack();
                stack.execute(command);
            } else {
                command.execute();
            }
            return true;
        } catch (Throwable t) {
            Log.warn("BMC RemoveFieldFromViewCommand path failed, using fallback: " + t.getMessage());
            return false;
        }
    }

    private static void refreshCurrentView(FormViewLayout currentView, int fieldId) {
        try {
            currentView.setSelection(Collections.emptyList());
        } catch (Throwable ignored) {
        }
        try {
            currentView.reorderChildren();
        } catch (Throwable ignored) {
        }
        try {
            currentView.validate();
        } catch (Throwable ignored) {
        }
        try {
            GraphicalViewer viewer = currentView.getGraphicalViewer();
            if (viewer != null) {
                viewer.deselectAll();
                viewer.flush();
                Control control = viewer.getControl();
                if (control != null && !control.isDisposed()) {
                    control.redraw();
                    control.update();
                }
            }
        } catch (Throwable ignored) {
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
