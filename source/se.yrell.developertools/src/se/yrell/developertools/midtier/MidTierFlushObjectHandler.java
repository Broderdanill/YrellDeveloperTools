package se.yrell.developertools.midtier;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;

import se.yrell.developertools.ToolsPreferences;

/** Single-click/context menu/key command. Starts form flush directly in Progress. */
public final class MidTierFlushObjectHandler extends AbstractHandler {
    @Override public Object execute(ExecutionEvent event) throws ExecutionException {
        Shell shell = HandlerUtil.getActiveShell(event);
        if (!ToolsPreferences.isMidTierCacheEnabled()) return null;
        ISelection menuSelection = HandlerUtil.getActiveMenuSelection(event);
        ISelection selection = HandlerUtil.getCurrentSelection(event);
        IWorkbenchPart activePart = HandlerUtil.getActivePart(event);
        // A context-menu selection is authoritative; a keyboard shortcut in an
        // active editor instead uses the active form/workflow rather than a stale
        // Object List selection from another server.
        MidTierEditorContext context = MidTierEditorContext.captureSelected(menuSelection);
        if (context == null && activePart instanceof IEditorPart editor)
            context = MidTierEditorContext.capture(editor);
        if (context == null) context = MidTierEditorContext.captureSelected(selection);
        if (context == null || context.forms().isEmpty()) {
            MessageDialog.openInformation(shell, "MidTier cache", "Select a form or a workflow with a primary form.");
            return null;
        }
        if (!startDirect(shell, context))
            MessageDialog.openInformation(shell, "MidTier cache", "No MidTier URL configured for " + context.server());
        return null;
    }

    /** Starts a direct form flush without a chooser, usable by Object List and hotkeys. */
    public static boolean startDirect(Shell shell, MidTierEditorContext context) {
        if (!ToolsPreferences.isMidTierCacheEnabled() || context == null || context.forms().isEmpty()) return false;
        MidTierConfiguration.Mapping target = MidTierConfiguration.lookup(
                ToolsPreferences.getMidTierServerMappings(), context.server());
        if (target == null) return false;
        // Workflow primary form is first. Never flush every associated form implicitly.
        MidTierFlushJob.start(shell, context, target, 0, context.forms().get(0));
        return true;
    }
}
