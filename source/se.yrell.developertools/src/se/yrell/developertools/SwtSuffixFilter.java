package se.yrell.developertools;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.widgets.Combo;
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
        if (widget == null || Boolean.TRUE.equals(widget.getData(DATA_KEY))) {
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
