package se.yrell.developertools.midtier;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import se.yrell.developertools.ToolsPreferences;

public final class MidTierFlushHandler extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        if (!ToolsPreferences.isMidTierCacheEnabled()) {
            MessageDialog.openInformation(shell, "MidTier Cache Flush", "Enable MidTier Cache Flush in Yrell Developer Tools preferences first.");
            return null;
        }
        IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindow(event);
        MidTierEditorContext context = MidTierFlushAvailability.current(window);
        if (context == null) {
            MessageDialog.openInformation(shell, "MidTier Cache Flush",
                    "Select a configured AR server in Object List or open a form/workflow for that server."
                    + " With multiple connections, select the intended server first.");
            return null;
        }
        MidTierConfiguration.Mapping target = MidTierConfiguration.lookup(
                ToolsPreferences.getMidTierServerMappings(), context.server());
        if (target == null) {
            MessageDialog.openInformation(shell, "MidTier Cache Flush", "No MidTier URL is configured for AR server '"
                    + context.server() + "'. Configure the server in Window > Preferences > Yrell Developer Tools > MidTier Cache Flush.");
            return null;
        }
        try {
            new MidTierFlushDialog(shell, context, target).open();
        } catch (SWTError | RuntimeException ex) {
            MessageDialog.openError(shell, "MidTier Cache Flush",
                    "Could not open an embedded browser. Check that SWT Browser is available on this Windows installation.\n"
                    + ex.getMessage() + "\n\nManual URL: " + MidTierConfiguration.pageUrl(target, true));
        }
        return null;
    }
}
