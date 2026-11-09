package se.yrell.developertools;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Widget;

/**
 * UI fallback: removes Developer Studio's proposed __c suffix from simple name
 * input controls, for example new form/view/field dialogs.
 */
public final class SwtSuffixFilter {
    private static final String SUFFIX = "__c";
    private static final String DATA_KEY = SwtSuffixFilter.class.getName() + ".updating";
    private static volatile boolean installed;

    private SwtSuffixFilter() {
    }

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        Display display = Display.getDefault();
        Listener listener = SwtSuffixFilter::handleEvent;
        display.addFilter(SWT.Modify, listener);
        display.addFilter(SWT.FocusOut, listener);
        Log.info("Installed SWT __c suffix filter");
    }

    private static void handleEvent(Event event) {
        if (!ToolsPreferences.isRemoveCustomSuffixEnabled()) {
            return;
        }
        Widget widget = event.widget;
        if (widget == null || Boolean.TRUE.equals(widget.getData(DATA_KEY))
                || !(widget instanceof Control) || !isKnownCreationNameControl((Control) widget)) {
            return;
        }
        try {
            if (widget instanceof Text) {
                Text text = (Text) widget;
                if ((text.getStyle() & SWT.MULTI) != 0 || (text.getStyle() & SWT.PASSWORD) != 0 || !text.getEditable()) {
                    return;
                }
                sanitizeText(text);
            } else if (widget instanceof Combo) {
                sanitizeCombo((Combo) widget);
            } else if (widget instanceof CCombo) {
                sanitizeCCombo((CCombo) widget);
            }
        } catch (Throwable t) {
            Log.error("SWT suffix filter failed", t);
        }
    }

    /**
     * Strict UI fallback. We only touch an explicitly labelled name field inside a
     * new/create dialog. Other editable fields, search boxes and property editors
     * must never be silently rewritten even when their value happens to end __c.
     * Model-level cleanup remains the primary mechanism.
     */
    private static boolean isKnownCreationNameControl(Control control) {
        if (control.isDisposed()) return false;
        Shell shell = control.getShell();
        if (shell == null || shell.isDisposed()) return false;
        String title = shell.getText().trim().toLowerCase(java.util.Locale.ROOT);
        if (!(title.startsWith("new ") || title.startsWith("create ")
                || title.startsWith("add ") || title.startsWith("ny ")
                || title.startsWith("skapa "))) return false;
        Composite parent = control.getParent();
        if (parent == null || parent.isDisposed()) return false;
        Control[] siblings = parent.getChildren();
        for (int i = 0; i < siblings.length; i++) {
            if (siblings[i] != control) continue;
            // Only trust the label immediately before the field, never arbitrary
            // labels elsewhere in the dialog.
            if (i == 0 || !(siblings[i - 1] instanceof Label)) return false;
            String label = ((Label) siblings[i - 1]).getText().trim()
                    .toLowerCase(java.util.Locale.ROOT).replace(":", "").trim();
            return label.equals("name") || label.equals("database name")
                    || label.equals("field name") || label.equals("form name")
                    || label.equals("view name") || label.equals("namn")
                    || label.equals("databasnamn");
        }
        return false;
    }

    private static String strip(String value) {
        if (value != null && value.length() > SUFFIX.length() && value.endsWith(SUFFIX)) {
            return value.substring(0, value.length() - SUFFIX.length());
        }
        return value;
    }

    private static void sanitizeText(Text text) {
        String current = text.getText();
        String stripped = strip(current);
        if (current.equals(stripped)) {
            return;
        }
        text.setData(DATA_KEY, Boolean.TRUE);
        try {
            int caret = Math.min(text.getCaretPosition(), stripped.length());
            text.setText(stripped);
            text.setSelection(caret);
        } finally {
            text.setData(DATA_KEY, null);
        }
    }

    private static void sanitizeCombo(Combo combo) {
        String current = combo.getText();
        String stripped = strip(current);
        if (current.equals(stripped)) {
            return;
        }
        combo.setData(DATA_KEY, Boolean.TRUE);
        try {
            combo.setText(stripped);
            combo.setSelection(new org.eclipse.swt.graphics.Point(stripped.length(), stripped.length()));
        } finally {
            combo.setData(DATA_KEY, null);
        }
    }

    private static void sanitizeCCombo(CCombo combo) {
        String current = combo.getText();
        String stripped = strip(current);
        if (current.equals(stripped)) {
            return;
        }
        combo.setData(DATA_KEY, Boolean.TRUE);
        try {
            combo.setText(stripped);
            combo.setSelection(new org.eclipse.swt.graphics.Point(stripped.length(), stripped.length()));
        } finally {
            combo.setData(DATA_KEY, null);
        }
    }
}
