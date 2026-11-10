package se.yrell.developertools.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import com.bmc.arsys.studio.commonui.common.objectlist.ColumnInformation;
import com.bmc.arsys.studio.commonui.common.objectlist.ObjectListComponent;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/**
 * Applies a single, optional default sorting rule to BMC's real ObjectListComponent.
 *
 * No remote query, new comparator, or background list mutation. The existing BMC
 * ColumnSorter is used, after BMC's content provider has finished rebuilding columns.
 * Unsupported/unsortable selectors are left completely unchanged.
 */
public final class ObjectListSortingRuntime {
    private static final String MARKER_KEY = "se.yrell.developertools.objectSort.applied";

    private ObjectListSortingRuntime() { }

    /** Called as getColumnInformation(List) returns, before BMC creates the table headers. */
    @SuppressWarnings("unchecked")
    public static List<?> prepareColumns(Object owner, List<?> source) {
        if (source == null || !(owner instanceof ObjectListComponent)
                || !ToolsPreferences.isObjectSortEnabled()) return source;
        try {
            ObjectListComponent component = (ObjectListComponent) owner;
            TableViewer viewer = component.getTableViewer();
            if (!hasBmcSorter(viewer)) return source; // Respect enableSorting=false.
            String id = ToolsPreferences.getObjectSortColumn();
            for (int i = 0; i < source.size(); i++) {
                Object value = source.get(i);
                if (!(value instanceof ColumnInformation)) continue;
                ColumnInformation info = (ColumnInformation) value;
                if (!id.equals(info.getId())) continue;
                if (info.isDisplay() || info.isRequired()) return source;
                // BMC might share registry ColumnInformation objects across components.
                // Never modify the original metadata or a persisted BMC preference.
                ColumnInformation copy = info.clone();
                copy.setDisplay(true);
                List<ColumnInformation> result = new ArrayList<ColumnInformation>((List<ColumnInformation>) source);
                result.set(i, copy);
                return result;
            }
        } catch (Throwable t) {
            Log.warn("Object sort: could not prepare the selected column: " + t.getMessage());
        }
        return source;
    }

    /** Called after BMC's ViewContentProvider.inputChanged has populated the headers. */
    public static void afterInputChanged(Object owner) {
        if (!(owner instanceof ObjectListComponent) || !ToolsPreferences.isObjectSortEnabled()) return;
        try {
            ObjectListComponent component = (ObjectListComponent) owner;
            TableViewer viewer = component.getTableViewer();
            if (!hasBmcSorter(viewer)) return;
            Table table = viewer.getTable();
            if (table == null || table.isDisposed() || table.getItemCount() == 0) return;
            if (table.getDisplay().getThread() != Thread.currentThread()) return;
            String id = ToolsPreferences.getObjectSortColumn();
            int direction = ToolsPreferences.isObjectSortDescending() ? SWT.DOWN : SWT.UP;
            TableColumn target = findColumn(table, id);
            if (target == null) return; // Column omitted by BMC, do not guess or query.
            Object input = viewer.getInput();
            if (input == null) return;
            SortMarker previous = (SortMarker) table.getData(MARKER_KEY);
            if (previous != null && previous.input == input && previous.column == target
                    && previous.direction == direction) return;
            ViewerSorter sorter = viewer.getSorter();
            // BMC's sorter class is package-private; call its PUBLIC method reflectively.
            Method setDirection = sorter.getClass().getMethod("setSortDirection", int.class);
            setDirection.setAccessible(true);
            setDirection.invoke(sorter, Integer.valueOf(direction));
            component.setSortColumn(table.indexOf(target));
            table.setSortColumn(target);
            table.setSortDirection(direction);
            viewer.refresh();
            table.setData(MARKER_KEY, new SortMarker(input, target, direction));
        } catch (Throwable t) {
            // Sorting is cosmetic; never prevent a BMC list or selector from opening.
            Log.warn("Object sort: BMC default was retained: " + t.getMessage());
        }
    }

    private static boolean hasBmcSorter(TableViewer viewer) {
        return viewer != null && viewer.getTable() != null && !viewer.getTable().isDisposed()
                && viewer.getSorter() != null
                && "com.bmc.arsys.studio.commonui.common.objectlist.ObjectListComponent$ColumnSorter"
                    .equals(viewer.getSorter().getClass().getName());
    }

    private static TableColumn findColumn(Table table, String fieldId) {
        for (TableColumn column : table.getColumns()) {
            Object info = column.getData();
            if (info instanceof ColumnInformation && fieldId.equals(((ColumnInformation) info).getId())) {
                return column;
            }
        }
        return null;
    }

    private static final class SortMarker {
        final Object input;
        final TableColumn column;
        final int direction;
        SortMarker(Object input, TableColumn column, int direction) {
            this.input = input;
            this.column = column;
            this.direction = direction;
        }
    }
}
