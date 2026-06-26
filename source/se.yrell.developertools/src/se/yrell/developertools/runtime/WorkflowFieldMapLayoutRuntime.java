package se.yrell.developertools.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/** Runtime helpers for workflow field-map tables such as Set Fields and Push Fields. */
public final class WorkflowFieldMapLayoutRuntime {
    private static final String DATA_KEY = "se.yrell.developertools.workflowFieldMapLayout.installed";
    private static final int MIN_VALUE_COLUMN_WIDTH = 400;
    private static final int TABLE_MARGIN = 6;

    private WorkflowFieldMapLayoutRuntime() {
    }

    public static void installFieldMapLayout(Object fieldMapWidget) {
        if (!isEnabled() || fieldMapWidget == null) {
            return;
        }
        try {
            Table table = findTable(fieldMapWidget);
            if (table == null || table.isDisposed()) {
                return;
            }
            if (!Boolean.TRUE.equals(table.getData(DATA_KEY))) {
                table.setData(DATA_KEY, Boolean.TRUE);
                table.addControlListener(new ControlAdapter() {
                    @Override
                    public void controlResized(ControlEvent e) {
                        adjustAsync(table);
                    }
                });
            }
            adjustAsync(table);
        } catch (Throwable t) {
            Log.warn("Workflow field-map layout helper could not install table resizing: " + t.getMessage());
        }
    }

    private static void adjustAsync(final Table table) {
        if (table == null || table.isDisposed()) {
            return;
        }
        Display display = table.getDisplay();
        if (display == null || display.isDisposed()) {
            return;
        }
        display.asyncExec(new Runnable() {
            @Override
            public void run() {
                adjustNow(table);
            }
        });
    }

    private static void adjustNow(Table table) {
        if (!isEnabled() || table == null || table.isDisposed()) {
            return;
        }
        TableColumn[] columns = table.getColumns();
        if (columns == null || columns.length < 2) {
            return;
        }
        Rectangle area = table.getClientArea();
        if (area == null || area.width <= 0) {
            return;
        }
        int fixed = 0;
        for (int i = 0; i < columns.length - 1; i++) {
            fixed += columns[i].getWidth();
        }
        int target = area.width - fixed - TABLE_MARGIN;
        if (target < MIN_VALUE_COLUMN_WIDTH) {
            target = MIN_VALUE_COLUMN_WIDTH;
        }
        TableColumn last = columns[columns.length - 1];
        if (!last.isDisposed() && Math.abs(last.getWidth() - target) > 2) {
            last.setWidth(target);
        }
    }

    private static Table findTable(Object fieldMapWidget) throws Exception {
        Object direct = readField(fieldMapWidget, "innerTable");
        if (direct instanceof Table) {
            return (Table) direct;
        }
        Object viewer = callNoArg(fieldMapWidget, "getTableViewer");
        Object table = callNoArg(viewer, "getTable");
        return table instanceof Table ? (Table) table : null;
    }

    private static Object readField(Object target, String name) throws Exception {
        if (target == null || name == null) {
            return null;
        }
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        return null;
    }

    private static Object callNoArg(Object target, String methodName) throws Exception {
        if (target == null || methodName == null) {
            return null;
        }
        Method method = target.getClass().getMethod(methodName, new Class[0]);
        method.setAccessible(true);
        return method.invoke(target, new Object[0]);
    }

    private static boolean isEnabled() {
        try {
            return ToolsPreferences.isWorkflowFieldMapLayoutEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
