package se.yrell.developertools.viewactions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

public class RemoveFromViewHandler extends AbstractHandler {
    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        Object selected = firstSelected(HandlerUtil.getCurrentSelection(event));
        Shell shell = HandlerUtil.getActiveShell(event);
        if (!RemoveFromViewSupport.canRemoveFromView(selected)) {
            return null;
        }
        boolean ok = RemoveFromViewSupport.removeFromCurrentView(selected, shell);
        if (!ok && shell != null) {
            MessageDialog.openError(shell, "Yrell Developer Tools", "Could not remove the selected object from the current view. See the Eclipse Error Log for details.");
        }
        return null;
    }

    private Object firstSelected(ISelection selection) {
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection s = (IStructuredSelection) selection;
            if (!s.isEmpty()) {
                return s.getFirstElement();
            }
        }
        return null;
    }
}
