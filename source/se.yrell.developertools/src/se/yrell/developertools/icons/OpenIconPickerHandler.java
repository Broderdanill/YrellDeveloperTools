package se.yrell.developertools.icons;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

import se.yrell.developertools.Log;

/**
 * Standalone entry point for the PWA icon picker.
 *
 * This makes the picker testable even when Developer Studio's property grid is
 * not a standard SWT Tree/Table where we can reliably inject the inline "..."
 * button.
 */
public class OpenIconPickerHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        if (shell == null || shell.isDisposed()) {
            shell = org.eclipse.swt.widgets.Display.getDefault().getActiveShell();
        }
        if (shell == null || shell.isDisposed()) {
            Log.warn("Could not open PWA Icon Picker because no active shell was available");
            return null;
        }

        IconPickerDialog dialog = new IconPickerDialog(shell, "");
        if (dialog.open() == Dialog.OK) {
            String value = dialog.getSelectedIconName();
            if (value != null && value.trim().length() > 0) {
                boolean updated = IconPropertySetter.setIconValue(value.trim(), null);
                if (updated) {
                    Log.info("PWA Icon Picker set active Icon property to " + value.trim() + " and copied it to clipboard.");
                } else {
                    Log.info("PWA Icon Picker copied " + value.trim() + " to clipboard; no active Icon property source was found.");
                }
            }
        }
        return null;
    }
}
