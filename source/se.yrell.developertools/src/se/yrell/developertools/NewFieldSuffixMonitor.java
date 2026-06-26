package se.yrell.developertools;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.bmc.arsys.studio.model.ModelState;
import com.bmc.arsys.studio.model.internal.helper.IFieldChangeListener.FieldChangeEvent;
import com.bmc.arsys.studio.model.store.IFieldObject;
import com.bmc.arsys.studio.model.store.IFormObject;
import com.bmc.arsys.studio.model.store.IModelObject;

/**
 * Non-weaving safety net for BMC's automatic __c suffix.
 *
 * The weaving hook is still the preferred path, but some Developer Studio model/UI
 * classes can already be loaded before a drop-in plugin is activated. This monitor
 * only touches NEW model objects, so it does not rename existing persisted fields.
 */
public final class NewFieldSuffixMonitor {
    private static final String SUFFIX = "__c";
    private static volatile boolean installed;
    private static volatile boolean scanScheduled;
    private static Display display;

    private NewFieldSuffixMonitor() {
    }

    public static synchronized void install() {
        if (installed) {
            return;
        }
        display = Display.getDefault();
        if (display == null || display.isDisposed()) {
            return;
        }
        installed = true;
        Listener listener = new Listener() {
            @Override
            public void handleEvent(Event event) {
                scheduleScan();
            }
        };
        display.addFilter(SWT.Selection, listener);
        display.addFilter(SWT.MouseUp, listener);
        display.addFilter(SWT.FocusIn, listener);
        display.addFilter(SWT.Modify, listener);
        display.addFilter(SWT.Paint, listener);
        display.addFilter(SWT.Activate, listener);
        display.addFilter(SWT.Show, listener);
        schedulePeriodicScan();
        scheduleScan();
        Log.info("Installed NEW-field __c suffix monitor");
    }

    private static void schedulePeriodicScan() {
        if (display == null || display.isDisposed()) {
            return;
        }
        display.timerExec(1200, new Runnable() {
            @Override
            public void run() {
                if (display == null || display.isDisposed()) {
                    return;
                }
                scheduleScan();
                schedulePeriodicScan();
            }
        });
    }

    private static void scheduleScan() {
        if (display == null || display.isDisposed() || scanScheduled) {
            return;
        }
        scanScheduled = true;
        display.timerExec(120, new Runnable() {
            @Override
            public void run() {
                scanScheduled = false;
                if (display == null || display.isDisposed()) {
                    return;
                }
                try {
                    scanNow();
                } catch (Throwable t) {
                    Log.warn("NEW-field __c suffix monitor scan failed: " + t.getMessage());
                }
            }
        });
    }

    private static void scanNow() {
        if (!ToolsPreferences.isRemoveCustomSuffixEnabled()) {
            return;
        }
        Map<Object, Boolean> visited = new IdentityHashMap<Object, Boolean>();
        scanWorkbenchSelection(visited);
        scanShellData(visited);
    }

    private static void scanWorkbenchSelection(Map<Object, Boolean> visited) {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) {
                return;
            }
            ISelectionService service = window.getSelectionService();
            if (service == null) {
                return;
            }
            org.eclipse.jface.viewers.ISelection selection = service.getSelection();
            if (selection instanceof IStructuredSelection) {
                for (Object element : ((IStructuredSelection) selection).toArray()) {
                    scanObject(element, visited, 0);
                }
            } else if (selection instanceof StructuredSelection) {
                scanObject(((StructuredSelection) selection).getFirstElement(), visited, 0);
            }
        } catch (Throwable ignored) {
            // Selection service is best-effort only.
        }
    }

    private static void scanShellData(Map<Object, Boolean> visited) {
        try {
            Shell[] shells = display.getShells();
            for (Shell shell : shells) {
                scanControl(shell, visited, 0);
            }
        } catch (Throwable ignored) {
            // Best effort only.
        }
    }

    private static void scanControl(Control control, Map<Object, Boolean> visited, int depth) {
        if (control == null || control.isDisposed() || depth > 24) {
            return;
        }
        try {
            scanObject(control.getData(), visited, 0);
            if (control instanceof org.eclipse.swt.widgets.Composite) {
                for (Control child : ((org.eclipse.swt.widgets.Composite) control).getChildren()) {
                    scanControl(child, visited, depth + 1);
                }
            }
        } catch (Throwable ignored) {
            // Ignore invalid/disposing widgets.
        }
    }

    private static void scanObject(Object object, Map<Object, Boolean> visited, int depth) {
        if (object == null || depth > 6 || visited.containsKey(object)) {
            return;
        }
        visited.put(object, Boolean.TRUE);
        try {
            if (object instanceof IFieldObject) {
                cleanNewField((IFieldObject) object);
                return;
            }
            if (object instanceof IFormObject) {
                cleanNewFormDefaultFields((IFormObject) object);
                return;
            }

            // Common Developer Studio UIField path.
            Object field = invokeNoArg(object, "getField");
            if (field instanceof IFieldObject) {
                cleanNewField((IFieldObject) field);
            }
            Object arForm = invokeNoArg(object, "getARForm");
            if (arForm instanceof IFormObject) {
                cleanNewFormDefaultFields((IFormObject) arForm);
            }
            Object form = invokeNoArg(object, "getForm");
            if (form instanceof IFormObject) {
                cleanNewFormDefaultFields((IFormObject) form);
            }
            Object modelObject = invokeNoArg(object, "getModelObject");
            if (modelObject != null && modelObject != object) {
                scanObject(modelObject, visited, depth + 1);
            }
            Object editableValue = invokeNoArg(object, "getEditableValue");
            if (editableValue != null && editableValue != object) {
                scanObject(editableValue, visited, depth + 1);
            }
            Object adapter = invokeAdapter(object, "com.bmc.arsys.studio.model.store.IFieldObject");
            if (adapter instanceof IFieldObject) {
                cleanNewField((IFieldObject) adapter);
            }
            Object formAdapter = invokeAdapter(object, "com.bmc.arsys.studio.model.store.IFormObject");
            if (formAdapter instanceof IFormObject) {
                cleanNewFormDefaultFields((IFormObject) formAdapter);
            }
            Object children = invokeNoArg(object, "getChildren");
            if (children instanceof Iterable<?>) {
                for (Object child : (Iterable<?>) children) {
                    scanObject(child, visited, depth + 1);
                }
            }
        } catch (Throwable ignored) {
            // Never let helper code break Developer Studio.
        }
    }

    private static void cleanNewFormDefaultFields(IFormObject form) {
        if (form == null) {
            return;
        }
        boolean formIsNew = isNewModelObject(form);
        Collection<IFieldObject> fields;
        try {
            fields = form.getFields();
        } catch (Throwable ignored) {
            return;
        }
        if (fields == null) {
            return;
        }
        Set<String> existingNames = new HashSet<String>();
        for (IFieldObject field : fields) {
            try {
                if (field != null && field.getName() != null && !field.getName().endsWith(SUFFIX)) {
                    existingNames.add(field.getName());
                }
            } catch (Throwable ignored) {
                // Ignore.
            }
        }
        for (IFieldObject field : fields) {
            if (field == null) {
                continue;
            }
            if (formIsNew || isNewModelObject(field)) {
                cleanNewField(field, existingNames);
            }
        }
    }

    public static void cleanNewField(IFieldObject field) {
        cleanNewField(field, null);
    }

    private static void cleanNewField(IFieldObject field, Set<String> existingNames) {
        if (field == null || !isNewModelObject(field)) {
            return;
        }
        try {
            String current = field.getName();
            if (current == null || !current.endsWith(SUFFIX) || current.length() <= SUFFIX.length()) {
                return;
            }
            String cleaned = current.substring(0, current.length() - SUFFIX.length());
            String unique = makeUniqueCleanName(cleaned, existingNames);
            field.setName(unique);
            field.setNewName(unique);
            field.setDirty(true);
            try {
                field.fireChange(new FieldChangeEvent(field, "Name", current, unique));
                field.fireChange(new FieldChangeEvent(field, "Database", current, unique));
                field.fireChange(new FieldChangeEvent(field, "Database Name", current, unique));
            } catch (Throwable ignored) {
                // Some Developer Studio versions refresh from the model without this.
            }
            if (existingNames != null) {
                existingNames.add(unique);
            }
            Log.info("Removed __c from NEW field database name '" + current + "' -> '" + unique + "'.");
        } catch (Throwable t) {
            Log.warn("Could not remove __c from NEW field: " + t.getMessage());
        }
    }

    private static String makeUniqueCleanName(String baseName, Set<String> existingNames) {
        if (baseName == null || baseName.length() == 0 || existingNames == null || !existingNames.contains(baseName)) {
            return baseName;
        }
        for (int i = 1; i < 10000; i++) {
            String candidate = baseName + " " + i;
            if (!existingNames.contains(candidate)) {
                return candidate;
            }
        }
        return baseName;
    }

    private static boolean isNewModelObject(Object object) {
        try {
            if (object instanceof IModelObject) {
                return ((IModelObject) object).getState() == ModelState.NEW;
            }
            Object state = invokeNoArg(object, "getState");
            return state != null && "NEW".equals(String.valueOf(state));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method m = target.getClass().getMethod(methodName);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invokeAdapter(Object target, String className) {
        try {
            Method m = target.getClass().getMethod("getAdapter", Class.class);
            Class<?> clazz = target.getClass().getClassLoader().loadClass(className);
            return m.invoke(target, clazz);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
