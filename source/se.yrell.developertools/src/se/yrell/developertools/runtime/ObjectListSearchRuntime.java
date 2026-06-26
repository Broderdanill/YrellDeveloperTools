package se.yrell.developertools.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Text;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/** Runtime helpers for Developer Studio's built-in object-list search field. */
public final class ObjectListSearchRuntime {
    private static final String DATA_KEY = "se.yrell.developertools.objectListSearchEnhancer.installed";

    private ObjectListSearchRuntime() {
    }

    public static int enhanceSearchTextStyle(int originalStyle) {
        if (!isEnabled()) {
            return originalStyle;
        }
        return originalStyle | SWT.SEARCH | SWT.ICON_CANCEL;
    }

    public static void setSearchTextDuringColumnChange(Text text, String value) {
        if (text == null || text.isDisposed()) {
            return;
        }
        if (!isEnabled()) {
            text.setText(value == null ? "" : value);
        }
        // When enabled, keep the existing text. Developer Studio then changes only
        // the selected search column and fires the object-list refresh using the
        // current SelectionCriteria value.
    }

    public static void installSearchClearButton(Object filteringSection) {
        if (!isEnabled() || filteringSection == null) {
            return;
        }
        try {
            Text text = findSearchText(filteringSection);
            if (text == null || text.isDisposed()) {
                return;
            }
            if (Boolean.TRUE.equals(text.getData(DATA_KEY))) {
                return;
            }
            text.setData(DATA_KEY, Boolean.TRUE);
            text.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    handleSelection(text, e);
                }

                @Override
                public void widgetDefaultSelected(SelectionEvent e) {
                    handleSelection(text, e);
                }
            });
        } catch (Throwable t) {
            Log.warn("Object list search helper could not install the clear handler: " + t.getMessage());
        }
    }

    private static void handleSelection(Text text, SelectionEvent event) {
        if (!isEnabled() || text == null || text.isDisposed()) {
            return;
        }
        if (event != null && (event.detail == SWT.ICON_CANCEL || event.detail == SWT.CANCEL)) {
            text.setText("");
            text.setFocus();
        }
    }

    private static Text findSearchText(Object filteringSection) throws Exception {
        Object nameText = readField(filteringSection, "nameText");
        Object textWidget = callNoArg(nameText, "getTextWidget");
        return textWidget instanceof Text ? (Text) textWidget : null;
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
            return ToolsPreferences.isObjectListSearchEnhancerEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }
}
