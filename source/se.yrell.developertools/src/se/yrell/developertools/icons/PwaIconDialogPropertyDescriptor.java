package se.yrell.developertools.icons;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.DialogCellEditor;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import com.bmc.arsys.studio.documenteditor.common.properties.FieldTextPropertyDescriptor;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/** Dialog-cell editor used only for properties whose name/id is exactly Icon. */
public class PwaIconDialogPropertyDescriptor extends FieldTextPropertyDescriptor {
    private boolean readOnly;

    public PwaIconDialogPropertyDescriptor(Object id, String displayName) {
        super(id, displayName);
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
        super.setReadOnly(readOnly);
    }

    @Override
    public boolean isReadOnly() {
        return readOnly || super.isReadOnly();
    }

    @Override
    public CellEditor createPropertyEditor(Composite parent) {
        if (isReadOnly()) {
            return null;
        }
        return new PwaIconCellEditor(parent);
    }

    private static final class PwaIconCellEditor extends DialogCellEditor {
        PwaIconCellEditor(Composite parent) {
            super(parent);
        }

        @Override
        protected Object openDialogBox(Control cellEditorWindow) {
            Object current = doGetValue();
            String currentText = current == null ? "" : String.valueOf(current);
            Shell shell = cellEditorWindow == null || cellEditorWindow.isDisposed() ? null : cellEditorWindow.getShell();
            if (shell == null || shell.isDisposed()) {
                return current;
            }
            if (!ToolsPreferences.isPwaIconHelperEnabled()) {
                return current;
            }
            IconPickerDialog dialog = new IconPickerDialog(shell, currentText);
            if (dialog.open() == org.eclipse.jface.dialogs.Dialog.OK) {
                String value = dialog.getSelectedIconName();
                if (value != null) {
                    value = value.trim();
                    copyToClipboard(shell, value);
                    return value;
                }
            }
            return current;
        }

        @Override
        protected void updateContents(Object value) {
            super.updateContents(value == null ? "" : String.valueOf(value));
        }

        private static void copyToClipboard(Shell shell, String value) {
            Clipboard clipboard = null;
            try {
                clipboard = new Clipboard(shell.getDisplay());
                clipboard.setContents(new Object[] { value }, new Transfer[] { TextTransfer.getInstance() });
            } catch (Throwable t) {
                Log.warn("Could not copy selected PWA icon to clipboard: " + t.getMessage());
            } finally {
                if (clipboard != null) {
                    clipboard.dispose();
                }
            }
        }
    }
}
