package se.yrell.developertools.icons;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.custom.TableEditor;
import org.eclipse.swt.custom.TreeEditor;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/**
 * Adds a small "..." picker button next to visible PWA Icon property rows.
 *
 * Developer Studio uses different property-grid implementations in different
 * editor/property pages. Some are normal Tree controls with columns, some are
 * column-less trees, and some are tables. Version 0.1.16 supports
 * all three by using SWT TreeEditor/TableEditor instead of a sibling overlay
 * button. This makes the button follow the row when the property view scrolls or
 * when BMC recreates the row contents.
 */
public final class IconPropertyButtonInstaller {
    private static final String STATE_KEY = IconPropertyButtonInstaller.class.getName() + ".state";
    private static final String DISPOSE_LISTENER_KEY = IconPropertyButtonInstaller.class.getName() + ".disposeListener";
    private static final String ICON_ITEM_DATA_KEY = IconPropertyButtonInstaller.class.getName() + ".iconItem";
    private static final int BUTTON_WIDTH = 28;
    private static final int BUTTON_HEIGHT = 22;

    private static boolean installed;
    private static Display display;
    private static boolean scanScheduled;
    private static boolean loggedTreeIconProperty;
    private static boolean loggedTableIconProperty;
    private static boolean loggedGenericIconProperty;
    private static boolean loggedFocusedIconProperty;
    private static final Map<Tree, TreeState> treeStates = new HashMap<Tree, TreeState>();
    private static final Map<Table, TableState> tableStates = new HashMap<Table, TableState>();
    private static final Map<Control, OverlayState> overlayStates = new HashMap<Control, OverlayState>();
    private static final Map<Control, GridOverlayState> gridOverlayStates = new HashMap<Control, GridOverlayState>();

    private IconPropertyButtonInstaller() {
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
        display.asyncExec(new Runnable() {
            @Override
            public void run() {
                try {
                    installFilters();
                    scheduleScan();
                    Log.info("Installed PWA Icon property button helper");
                } catch (Throwable t) {
                    Log.error("Could not install PWA Icon property button helper", t);
                }
            }
        });
    }

    private static void installFilters() {
        Listener listener = new Listener() {
            @Override
            public void handleEvent(Event event) {
                scheduleScan();
            }
        };
        display.addFilter(SWT.Selection, listener);
        display.addFilter(SWT.FocusIn, listener);
        display.addFilter(SWT.FocusOut, listener);
        display.addFilter(SWT.MouseDown, listener);
        display.addFilter(SWT.MouseUp, listener);
        display.addFilter(SWT.MouseMove, listener);
        display.addFilter(SWT.Paint, listener);
        display.addFilter(SWT.Resize, listener);
        display.addFilter(SWT.Show, listener);
        display.addFilter(SWT.Activate, listener);
        display.addFilter(SWT.Deactivate, listener);
        display.addFilter(SWT.Expand, listener);
        display.addFilter(SWT.Collapse, listener);
        display.addFilter(SWT.Modify, listener);
        schedulePeriodicScan();
    }

    private static void schedulePeriodicScan() {
        if (display == null || display.isDisposed()) {
            return;
        }
        display.timerExec(700, new Runnable() {
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
        display.timerExec(80, new Runnable() {
            @Override
            public void run() {
                scanScheduled = false;
                if (display == null || display.isDisposed()) {
                    return;
                }
                try {
                    scanAllShells();
                } catch (Throwable t) {
                    Log.warn("PWA Icon property button scan failed: " + t.getMessage());
                }
            }
        });
    }

    private static void scanAllShells() {
        if (!ToolsPreferences.isPwaIconHelperEnabled()) {
            hideAllButtons();
            return;
        }
        Shell[] shells = display.getShells();
        for (Shell shell : shells) {
            if (shell != null && !shell.isDisposed()) {
                scanControl(shell);
            }
        }
        cleanupOverlayStates();
    }

    private static void scanControl(Control control) {
        if (control == null || control.isDisposed() || !control.isVisible()) {
            return;
        }
        if (control instanceof Tree) {
            updateTree((Tree) control);
        } else if (control instanceof Table) {
            updateTable((Table) control);
        }
        updateFocusedIconValueControl(control);
        if (control instanceof Composite) {
            updateGenericComposite((Composite) control);
            Control[] children = ((Composite) control).getChildren();
            for (Control child : children) {
                scanControl(child);
            }
        }
    }

    private static void updateTree(final Tree tree) {
        if (tree.isDisposed() || !tree.isVisible()) {
            hideTreeButton(tree);
            return;
        }
        TreeMatch match = findVisibleIconTreeItem(tree);
        if (match == null || match.item == null || match.item.isDisposed()) {
            hideTreeButton(tree);
            return;
        }
        if (!loggedTreeIconProperty) {
            loggedTreeIconProperty = true;
            Log.info("PWA Icon helper found an Icon property in a Tree control. Columns=" + tree.getColumnCount() + ", button column=" + match.valueColumn + ".");
        }
        TreeState state = treeStates.get(tree);
        if (state == null || state.button == null || state.button.isDisposed()) {
            state = createTreeState(tree);
            treeStates.put(tree, state);
            tree.setData(STATE_KEY, state);
            ensureTreeDisposeListener(tree);
        }
        state.button.setData(ICON_ITEM_DATA_KEY, match.item);
        state.button.setEnabled(true);
        state.button.setVisible(true);
        state.editor.horizontalAlignment = SWT.RIGHT;
        state.editor.verticalAlignment = SWT.CENTER;
        state.editor.grabHorizontal = false;
        state.editor.minimumWidth = BUTTON_WIDTH;
        state.editor.minimumHeight = BUTTON_HEIGHT;
        state.editor.setEditor(state.button, match.item, match.valueColumn);
        showGridOverlayButton(tree, match.item, match.valueColumn);
    }

    private static TreeState createTreeState(final Tree tree) {
        final Button button = new Button(tree, SWT.PUSH | SWT.FLAT);
        button.setText("...");
        button.setToolTipText("Choose PWA icon");
        final TreeEditor editor = new TreeEditor(tree);
        button.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event event) {
                Object data = button.getData(ICON_ITEM_DATA_KEY);
                TreeItem item = data instanceof TreeItem ? (TreeItem) data : null;
                if (item == null || item.isDisposed()) {
                    TreeMatch match = findVisibleIconTreeItem(tree);
                    item = match == null ? null : match.item;
                }
                openPicker(item);
            }
        });
        return new TreeState(button, editor);
    }

    private static void updateTable(final Table table) {
        if (table.isDisposed() || !table.isVisible()) {
            hideTableButton(table);
            return;
        }
        TableMatch match = findVisibleIconTableItem(table);
        if (match == null || match.item == null || match.item.isDisposed()) {
            hideTableButton(table);
            return;
        }
        if (!loggedTableIconProperty) {
            loggedTableIconProperty = true;
            Log.info("PWA Icon helper found an Icon property in a Table control. Columns=" + table.getColumnCount() + ", button column=" + match.valueColumn + ".");
        }
        TableState state = tableStates.get(table);
        if (state == null || state.button == null || state.button.isDisposed()) {
            state = createTableState(table);
            tableStates.put(table, state);
            table.setData(STATE_KEY, state);
            ensureTableDisposeListener(table);
        }
        state.button.setData(ICON_ITEM_DATA_KEY, match.item);
        state.button.setEnabled(true);
        state.button.setVisible(true);
        state.editor.horizontalAlignment = SWT.RIGHT;
        state.editor.verticalAlignment = SWT.CENTER;
        state.editor.grabHorizontal = false;
        state.editor.minimumWidth = BUTTON_WIDTH;
        state.editor.minimumHeight = BUTTON_HEIGHT;
        state.editor.setEditor(state.button, match.item, match.valueColumn);
        showGridOverlayButton(table, match.item, match.valueColumn);
    }

    private static TableState createTableState(final Table table) {
        final Button button = new Button(table, SWT.PUSH | SWT.FLAT);
        button.setText("...");
        button.setToolTipText("Choose PWA icon");
        final TableEditor editor = new TableEditor(table);
        button.addListener(SWT.Selection, new Listener() {
            @Override
            public void handleEvent(Event event) {
                Object data = button.getData(ICON_ITEM_DATA_KEY);
                TableItem item = data instanceof TableItem ? (TableItem) data : null;
                if (item == null || item.isDisposed()) {
                    TableMatch match = findVisibleIconTableItem(table);
                    item = match == null ? null : match.item;
                }
                openPicker(item);
            }
        });
        return new TableState(button, editor);
    }

    private static void openPicker(Item item) {
        Shell shell = display == null ? null : display.getActiveShell();
        if ((shell == null || shell.isDisposed()) && item != null && !item.isDisposed()) {
            if (item instanceof TreeItem) {
                shell = ((TreeItem) item).getParent().getShell();
            } else if (item instanceof TableItem) {
                shell = ((TableItem) item).getParent().getShell();
            }
        }
        if (shell == null || shell.isDisposed()) {
            return;
        }
        String currentValue = readValueColumn(item);
        IconPickerDialog dialog = new IconPickerDialog(shell, currentValue);
        if (dialog.open() == Dialog.OK) {
            String value = dialog.getSelectedIconName();
            if (value != null) {
                boolean modelUpdated = IconPropertySetter.setIconValue(value.trim(), item);
                if (!modelUpdated) {
                    Log.warn("PWA icon was copied to clipboard and written visually, but the active property source was not found. Click inside the Icon value field and paste if the model did not update.");
                }
                scheduleScan();
            }
        }
    }


    private static void updateFocusedIconValueControl(final Control control) {
        if (control == null || control.isDisposed() || !control.isVisible() || !isValueControl(control)) {
            return;
        }
        if (display == null || display.isDisposed() || display.getFocusControl() != control) {
            return;
        }
        if (!isLikelyIconValueControl(control)) {
            return;
        }
        showOverlayButtonNearValue(control);
    }

    private static boolean isLikelyIconValueControl(Control control) {
        String value = readValueControl(control);
        if (value != null && value.trim().startsWith("d-icon-")) {
            return true;
        }
        Shell shell = control == null || control.isDisposed() ? null : control.getShell();
        return shell != null && hasSelectedIconPropertyRow(shell);
    }

    private static boolean hasSelectedIconPropertyRow(Control root) {
        if (root == null || root.isDisposed()) {
            return false;
        }
        try {
            if (root instanceof Tree) {
                TreeItem[] selection = ((Tree) root).getSelection();
                for (TreeItem item : selection) {
                    if (iconMatch(item) != null) {
                        return true;
                    }
                }
            } else if (root instanceof Table) {
                TableItem[] selection = ((Table) root).getSelection();
                for (TableItem item : selection) {
                    if (iconMatch(item) != null) {
                        return true;
                    }
                }
            }
            if (root instanceof Composite) {
                Control[] children = ((Composite) root).getChildren();
                for (Control child : children) {
                    if (hasSelectedIconPropertyRow(child)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            return false;
        }
        return false;
    }

    private static void showOverlayButtonNearValue(final Control valueControl) {
        if (valueControl == null || valueControl.isDisposed() || !(valueControl.getParent() instanceof Composite)) {
            return;
        }
        final Composite parent = valueControl.getParent();
        if (parent == null || parent.isDisposed()) {
            return;
        }
        OverlayState state = overlayStates.get(valueControl);
        if (state == null || state.button == null || state.button.isDisposed()) {
            final Button button = new Button(parent, SWT.PUSH | SWT.FLAT);
            button.setText("...");
            button.setToolTipText("Choose PWA icon");
            state = new OverlayState(valueControl, button);
            overlayStates.put(valueControl, state);
            ensureOverlayDisposeListeners(valueControl, parent);
            button.addListener(SWT.Selection, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    openPicker(valueControl);
                }
            });
        }
        if (!loggedFocusedIconProperty) {
            loggedFocusedIconProperty = true;
            Log.info("PWA Icon helper found a focused Icon value editor. Value control=" + valueControl.getClass().getName() + ".");
        }
        positionOverlayButton(state);
    }



    private static void showGridOverlayButton(final Control grid, final Item item, final int valueColumn) {
        if (grid == null || grid.isDisposed() || item == null || item.isDisposed() || !(grid.getParent() instanceof Composite)) {
            return;
        }
        final Composite parent = grid.getParent();
        GridOverlayState state = gridOverlayStates.get(grid);
        if (state == null || state.button == null || state.button.isDisposed()) {
            final Button button = new Button(parent, SWT.PUSH | SWT.FLAT);
            button.setText("...");
            button.setToolTipText("Choose PWA icon");
            state = new GridOverlayState(grid, button);
            gridOverlayStates.put(grid, state);
            ensureGridOverlayDisposeListeners(grid, parent);
            button.addListener(SWT.Selection, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    GridOverlayState current = gridOverlayStates.get(grid);
                    Item currentItem = current == null ? item : current.item;
                    openPicker(currentItem == null ? item : currentItem);
                }
            });
        }
        state.item = item;
        state.valueColumn = valueColumn;
        positionGridOverlayButton(state);
    }

    private static void positionGridOverlayButton(GridOverlayState state) {
        if (state == null || state.button == null || state.button.isDisposed() || state.grid == null || state.grid.isDisposed()
                || state.item == null || state.item.isDisposed() || !(state.grid.getParent() instanceof Composite)) {
            if (state != null) {
                state.hide();
            }
            return;
        }
        try {
            Rectangle b;
            if (state.item instanceof TreeItem && state.grid instanceof Tree) {
                Tree tree = (Tree) state.grid;
                int col = Math.max(0, Math.min(state.valueColumn, Math.max(0, tree.getColumnCount() - 1)));
                b = ((TreeItem) state.item).getBounds(col);
                if (b.width <= 2 && tree.getColumnCount() > 1) {
                    int x = tree.getColumn(0).getWidth();
                    int w = Math.max(80, tree.getClientArea().width - x);
                    b = new Rectangle(x, b.y, w, Math.max(b.height, tree.getItemHeight()));
                }
            } else if (state.item instanceof TableItem && state.grid instanceof Table) {
                Table table = (Table) state.grid;
                int col = Math.max(0, Math.min(state.valueColumn, Math.max(0, table.getColumnCount() - 1)));
                b = ((TableItem) state.item).getBounds(col);
                if (b.width <= 2 && table.getColumnCount() > 1) {
                    int x = table.getColumn(0).getWidth();
                    int w = Math.max(80, table.getClientArea().width - x);
                    b = new Rectangle(x, b.y, w, Math.max(b.height, table.getItemHeight()));
                }
            } else {
                state.hide();
                return;
            }
            if (b.height <= 0) {
                state.hide();
                return;
            }
            Composite parent = state.grid.getParent();
            Point p = parent.toControl(state.grid.toDisplay(b.x, b.y));
            int height = Math.max(18, Math.min(BUTTON_HEIGHT, b.height));
            int width = BUTTON_WIDTH;
            int x = p.x + Math.max(2, b.width - width - 2);
            int y = p.y + Math.max(0, (b.height - height) / 2);
            state.button.setBounds(x, y, width, height);
            state.button.setVisible(true);
            state.button.setEnabled(true);
            state.button.moveAbove(null);
        } catch (Throwable t) {
            state.hide();
        }
    }

    private static void ensureGridOverlayDisposeListeners(final Control grid, final Composite parent) {
        if (grid.getData(DISPOSE_LISTENER_KEY + ".gridOverlay") == null) {
            DisposeListener listener = new DisposeListener() {
                @Override
                public void widgetDisposed(DisposeEvent e) {
                    GridOverlayState state = gridOverlayStates.remove(grid);
                    if (state != null) {
                        state.dispose();
                    }
                }
            };
            grid.addDisposeListener(listener);
            grid.setData(DISPOSE_LISTENER_KEY + ".gridOverlay", listener);
        }
        if (parent.getData(DISPOSE_LISTENER_KEY + ".gridOverlayParent") == null) {
            DisposeListener listener = new DisposeListener() {
                @Override
                public void widgetDisposed(DisposeEvent e) {
                    cleanupOverlayStates();
                }
            };
            parent.addDisposeListener(listener);
            parent.setData(DISPOSE_LISTENER_KEY + ".gridOverlayParent", listener);
        }
    }

    private static void updateGenericComposite(final Composite composite) {
        if (composite == null || composite.isDisposed() || !composite.isVisible()) {
            return;
        }
        Control[] children;
        try {
            children = composite.getChildren();
        } catch (Throwable t) {
            return;
        }
        for (Control label : children) {
            if (label == null || label.isDisposed() || !label.isVisible()) {
                continue;
            }
            String text = textOf(label);
            if (!isIconLabel(text)) {
                continue;
            }
            Control value = findValueControlForLabel(composite, label, children);
            if (value != null) {
                showOverlayButton(composite, label, value);
            }
        }
    }

    private static Control findValueControlForLabel(Composite composite, Control label, Control[] children) {
        Rectangle labelBounds;
        try {
            labelBounds = label.getBounds();
        } catch (Throwable t) {
            return null;
        }
        int labelCenter = labelBounds.y + labelBounds.height / 2;
        Control best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Control child : children) {
            if (child == null || child == label || child.isDisposed() || !child.isVisible()) {
                continue;
            }
            if (!isValueControl(child)) {
                continue;
            }
            Rectangle b;
            try {
                b = child.getBounds();
            } catch (Throwable t) {
                continue;
            }
            int childCenter = b.y + b.height / 2;
            int verticalDistance = Math.abs(childCenter - labelCenter);
            int verticalLimit = Math.max(18, Math.max(labelBounds.height, b.height));
            if (verticalDistance > verticalLimit) {
                continue;
            }
            if (b.x + b.width < labelBounds.x) {
                continue;
            }
            int horizontalDistance = Math.max(0, b.x - (labelBounds.x + labelBounds.width));
            int score = verticalDistance * 1000 + horizontalDistance;
            if (score < bestScore) {
                best = child;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean isValueControl(Control control) {
        if (control instanceof Text || control instanceof Combo || control instanceof CCombo || control instanceof StyledText) {
            return true;
        }
        String className = control.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return className.contains("text") || className.contains("combo");
    }

    private static void showOverlayButton(final Composite parent, final Control label, final Control valueControl) {
        if (parent == null || parent.isDisposed() || valueControl == null || valueControl.isDisposed()) {
            return;
        }
        OverlayState state = overlayStates.get(valueControl);
        if (state == null || state.button == null || state.button.isDisposed()) {
            final Button button = new Button(parent, SWT.PUSH | SWT.FLAT);
            button.setText("...");
            button.setToolTipText("Choose PWA icon");
            state = new OverlayState(valueControl, button);
            overlayStates.put(valueControl, state);
            ensureOverlayDisposeListeners(valueControl, parent);
            button.addListener(SWT.Selection, new Listener() {
                @Override
                public void handleEvent(Event event) {
                    openPicker(valueControl);
                }
            });
        }
        if (!loggedGenericIconProperty) {
            loggedGenericIconProperty = true;
            Log.info("PWA Icon helper found an Icon property in generic controls. Label=" + label.getClass().getName() + ", value=" + valueControl.getClass().getName() + ".");
        }
        positionOverlayButton(state);
    }

    private static void positionOverlayButton(OverlayState state) {
        if (state == null || state.button == null || state.button.isDisposed() || state.valueControl == null || state.valueControl.isDisposed()) {
            return;
        }
        try {
            Rectangle b = state.valueControl.getBounds();
            int height = Math.max(18, Math.min(BUTTON_HEIGHT, b.height));
            int width = BUTTON_WIDTH;
            int x = b.x + b.width - width - 1;
            int y = b.y + Math.max(0, (b.height - height) / 2);
            state.button.setBounds(x, y, width, height);
            state.button.setVisible(true);
            state.button.setEnabled(true);
            state.button.moveAbove(null);
        } catch (Throwable ignored) {
            state.hide();
        }
    }

    private static void openPicker(final Control valueControl) {
        Shell shell = display == null ? null : display.getActiveShell();
        if ((shell == null || shell.isDisposed()) && valueControl != null && !valueControl.isDisposed()) {
            shell = valueControl.getShell();
        }
        if (shell == null || shell.isDisposed()) {
            return;
        }
        String currentValue = readValueControl(valueControl);
        IconPickerDialog dialog = new IconPickerDialog(shell, currentValue);
        if (dialog.open() == Dialog.OK) {
            String value = dialog.getSelectedIconName();
            if (value != null) {
                boolean modelUpdated = IconPropertySetter.setIconValue(value.trim(), null, valueControl);
                if (!modelUpdated) {
                    Log.warn("PWA icon was written to the active Icon control and copied to clipboard, but the active property source was not found.");
                }
                scheduleScan();
            }
        }
    }

    private static String readValueControl(Control control) {
        if (control == null || control.isDisposed()) {
            return "";
        }
        try {
            if (control instanceof Text) {
                return ((Text) control).getText();
            }
            if (control instanceof Combo) {
                return ((Combo) control).getText();
            }
            if (control instanceof CCombo) {
                return ((CCombo) control).getText();
            }
            if (control instanceof StyledText) {
                return ((StyledText) control).getText();
            }
            try {
                java.lang.reflect.Method method = control.getClass().getMethod("getText");
                Object result = method.invoke(control);
                return result == null ? "" : String.valueOf(result);
            } catch (Throwable ignored) {
                return "";
            }
        } catch (Throwable t) {
            return "";
        }
    }

    private static void ensureOverlayDisposeListeners(final Control valueControl, final Composite parent) {
        if (valueControl.getData(DISPOSE_LISTENER_KEY) == null) {
            DisposeListener listener = new DisposeListener() {
                @Override
                public void widgetDisposed(DisposeEvent e) {
                    OverlayState state = overlayStates.remove(valueControl);
                    if (state != null) {
                        state.dispose();
                    }
                }
            };
            valueControl.addDisposeListener(listener);
            valueControl.setData(DISPOSE_LISTENER_KEY, listener);
        }
        if (parent.getData(DISPOSE_LISTENER_KEY + ".overlay") == null) {
            DisposeListener listener = new DisposeListener() {
                @Override
                public void widgetDisposed(DisposeEvent e) {
                    cleanupOverlayStates();
                }
            };
            parent.addDisposeListener(listener);
            parent.setData(DISPOSE_LISTENER_KEY + ".overlay", listener);
        }
    }

    private static void cleanupOverlayStates() {
        java.util.Iterator<Map.Entry<Control, GridOverlayState>> git = gridOverlayStates.entrySet().iterator();
        while (git.hasNext()) {
            Map.Entry<Control, GridOverlayState> entry = git.next();
            Control grid = entry.getKey();
            GridOverlayState state = entry.getValue();
            if (grid == null || grid.isDisposed() || !grid.isVisible()) {
                if (state != null) {
                    state.dispose();
                }
                git.remove();
            } else if (state != null && state.button != null && !state.button.isDisposed()) {
                positionGridOverlayButton(state);
            }
        }

        java.util.Iterator<Map.Entry<Control, OverlayState>> it = overlayStates.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Control, OverlayState> entry = it.next();
            Control value = entry.getKey();
            OverlayState state = entry.getValue();
            if (value == null || value.isDisposed() || !value.isVisible()) {
                if (state != null) {
                    state.dispose();
                }
                it.remove();
            } else if (state != null && state.button != null && !state.button.isDisposed()) {
                positionOverlayButton(state);
            }
        }
    }

    private static String textOf(Control control) {
        if (control == null || control.isDisposed()) {
            return "";
        }
        try {
            if (control instanceof org.eclipse.swt.widgets.Label) {
                return safeTrim(((org.eclipse.swt.widgets.Label) control).getText());
            }
            if (control instanceof CLabel) {
                return safeTrim(((CLabel) control).getText());
            }
            try {
                java.lang.reflect.Method method = control.getClass().getMethod("getText");
                Object result = method.invoke(control);
                return safeTrim(result == null ? "" : String.valueOf(result));
            } catch (Throwable ignored) {
                return "";
            }
        } catch (Throwable t) {
            return "";
        }
    }

    private static String readValueColumn(Item item) {
        try {
            if (item instanceof TreeItem) {
                TreeItem treeItem = (TreeItem) item;
                int columnCount = treeItem.getParent().getColumnCount();
                if (columnCount > 1) {
                    return treeItem.getText(1);
                }
                return treeItem.getText();
            }
            if (item instanceof TableItem) {
                TableItem tableItem = (TableItem) item;
                int columnCount = tableItem.getParent().getColumnCount();
                if (columnCount > 1) {
                    return tableItem.getText(1);
                }
                return tableItem.getText();
            }
        } catch (Throwable ignored) {
            // Best effort only.
        }
        return "";
    }

    private static TreeMatch findVisibleIconTreeItem(Tree tree) {
        TreeItem[] items = tree.getItems();
        for (TreeItem item : items) {
            TreeMatch found = findVisibleIconTreeItem(item);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static TreeMatch findVisibleIconTreeItem(TreeItem item) {
        if (item == null || item.isDisposed()) {
            return null;
        }
        TreeMatch match = iconMatch(item);
        if (match != null) {
            return match;
        }
        for (TreeItem child : item.getItems()) {
            TreeMatch found = findVisibleIconTreeItem(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static TreeMatch iconMatch(TreeItem item) {
        try {
            Tree tree = item.getParent();
            int columnCount = Math.max(1, tree.getColumnCount());
            for (int column = 0; column < columnCount; column++) {
                String text = safeTrim(item.getText(column));
                if (isIconLabel(text)) {
                    int valueColumn = tree.getColumnCount() > column + 1 ? column + 1 : column;
                    return new TreeMatch(item, valueColumn);
                }
            }
            if (isIconItemData(item.getData())) {
                int valueColumn = tree.getColumnCount() > 1 ? 1 : 0;
                return new TreeMatch(item, valueColumn);
            }
            String text = safeTrim(item.getText());
            if (isIconLabel(text) || isIconItemData(item.getData())) {
                int valueColumn = tree.getColumnCount() > 1 ? 1 : 0;
                return new TreeMatch(item, valueColumn);
            }
        } catch (Throwable ignored) {
            // Some BMC virtual rows can throw while being rebuilt. Ignore this pass.
        }
        return null;
    }

    private static TableMatch findVisibleIconTableItem(Table table) {
        TableItem[] items = table.getItems();
        for (TableItem item : items) {
            TableMatch match = iconMatch(item);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static TableMatch iconMatch(TableItem item) {
        try {
            Table table = item.getParent();
            int columnCount = Math.max(1, table.getColumnCount());
            for (int column = 0; column < columnCount; column++) {
                String text = safeTrim(item.getText(column));
                if (isIconLabel(text)) {
                    int valueColumn = table.getColumnCount() > column + 1 ? column + 1 : column;
                    return new TableMatch(item, valueColumn);
                }
            }
            if (isIconItemData(item.getData())) {
                int valueColumn = table.getColumnCount() > 1 ? 1 : 0;
                return new TableMatch(item, valueColumn);
            }
            String text = safeTrim(item.getText());
            if (isIconLabel(text) || isIconItemData(item.getData())) {
                int valueColumn = table.getColumnCount() > 1 ? 1 : 0;
                return new TableMatch(item, valueColumn);
            }
        } catch (Throwable ignored) {
            // Ignore this pass.
        }
        return null;
    }

    private static boolean isIconItemData(Object data) {
        if (data == null) {
            return false;
        }
        try {
            if (isIconLabel(invokeString(data, "getDisplayName"))) {
                return true;
            }
            if (isIconLabel(invokeString(data, "getName"))) {
                return true;
            }
            Object descriptor = invoke(data, "getDescriptor");
            if (descriptor != null && isIconItemData(descriptor)) {
                return true;
            }
            Object propertyDescriptor = invoke(data, "getPropertyDescriptor");
            if (propertyDescriptor != null && isIconItemData(propertyDescriptor)) {
                return true;
            }
            String id = invokeString(data, "getId");
            return id != null && id.toLowerCase(java.util.Locale.ROOT).endsWith("icon");
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String invokeString(Object target, String methodName) {
        Object value = invoke(target, methodName);
        return value == null ? "" : String.valueOf(value);
    }

    private static Object invoke(Object target, String methodName) {
        if (target == null || methodName == null) {
            return null;
        }
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeTrim(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').trim();
    }

    private static boolean isIconLabel(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.replace('\u00a0', ' ').trim();
        while (normalized.endsWith(":")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return "Icon".equals(normalized);
    }

    private static void ensureTreeDisposeListener(final Tree tree) {
        if (tree.getData(DISPOSE_LISTENER_KEY) != null) {
            return;
        }
        DisposeListener listener = new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                TreeState state = treeStates.remove(tree);
                if (state != null) {
                    state.dispose();
                }
            }
        };
        tree.addDisposeListener(listener);
        tree.setData(DISPOSE_LISTENER_KEY, listener);
    }

    private static void ensureTableDisposeListener(final Table table) {
        if (table.getData(DISPOSE_LISTENER_KEY) != null) {
            return;
        }
        DisposeListener listener = new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                TableState state = tableStates.remove(table);
                if (state != null) {
                    state.dispose();
                }
            }
        };
        table.addDisposeListener(listener);
        table.setData(DISPOSE_LISTENER_KEY, listener);
    }

    private static void hideTreeButton(Tree tree) {
        TreeState state = treeStates.get(tree);
        if (state == null) {
            Object data = tree.getData(STATE_KEY);
            if (data instanceof TreeState) {
                state = (TreeState) data;
            }
        }
        if (state != null) {
            state.hide();
        }
    }

    private static void hideTableButton(Table table) {
        TableState state = tableStates.get(table);
        if (state == null) {
            Object data = table.getData(STATE_KEY);
            if (data instanceof TableState) {
                state = (TableState) data;
            }
        }
        if (state != null) {
            state.hide();
        }
    }

    private static void hideAllButtons() {
        for (TreeState state : treeStates.values()) {
            if (state != null) {
                state.hide();
            }
        }
        for (TableState state : tableStates.values()) {
            if (state != null) {
                state.hide();
            }
        }
        for (OverlayState state : overlayStates.values()) {
            if (state != null) {
                state.hide();
            }
        }
        for (GridOverlayState state : gridOverlayStates.values()) {
            if (state != null) {
                state.hide();
            }
        }
    }

    private static final class TreeMatch {
        final TreeItem item;
        final int valueColumn;

        TreeMatch(TreeItem item, int valueColumn) {
            this.item = item;
            this.valueColumn = valueColumn;
        }
    }

    private static final class TableMatch {
        final TableItem item;
        final int valueColumn;

        TableMatch(TableItem item, int valueColumn) {
            this.item = item;
            this.valueColumn = valueColumn;
        }
    }

    private static final class TreeState {
        final Button button;
        final TreeEditor editor;

        TreeState(Button button, TreeEditor editor) {
            this.button = button;
            this.editor = editor;
        }

        void hide() {
            try {
                editor.setEditor(null, null, 0);
            } catch (Throwable ignored) {
                // Ignore.
            }
            if (button != null && !button.isDisposed()) {
                button.setVisible(false);
            }
        }

        void dispose() {
            try {
                editor.dispose();
            } catch (Throwable ignored) {
                // Ignore.
            }
            if (button != null && !button.isDisposed()) {
                button.dispose();
            }
        }
    }

    private static final class TableState {
        final Button button;
        final TableEditor editor;

        TableState(Button button, TableEditor editor) {
            this.button = button;
            this.editor = editor;
        }

        void hide() {
            try {
                editor.setEditor(null, null, 0);
            } catch (Throwable ignored) {
                // Ignore.
            }
            if (button != null && !button.isDisposed()) {
                button.setVisible(false);
            }
        }

        void dispose() {
            try {
                editor.dispose();
            } catch (Throwable ignored) {
                // Ignore.
            }
            if (button != null && !button.isDisposed()) {
                button.dispose();
            }
        }
    }
    private static final class OverlayState {
        final Control valueControl;
        final Button button;

        OverlayState(Control valueControl, Button button) {
            this.valueControl = valueControl;
            this.button = button;
        }

        void hide() {
            if (button != null && !button.isDisposed()) {
                button.setVisible(false);
            }
        }

        void dispose() {
            if (button != null && !button.isDisposed()) {
                button.dispose();
            }
        }
    }

    private static final class GridOverlayState {
        final Control grid;
        final Button button;
        Item item;
        int valueColumn;

        GridOverlayState(Control grid, Button button) {
            this.grid = grid;
            this.button = button;
        }

        void hide() {
            if (button != null && !button.isDisposed()) {
                button.setVisible(false);
            }
        }

        void dispose() {
            if (button != null && !button.isDisposed()) {
                button.dispose();
            }
        }
    }


}
