package se.yrell.developertools.midtier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.IEvaluationService;

import se.yrell.developertools.ToolsPreferences;

/** Single source of truth for toolbar scope and server mapping; never guess among servers. */
public final class MidTierFlushAvailability {
    private MidTierFlushAvailability() { }

    public static boolean configured(String server, Collection<MidTierConfiguration.Mapping> mappings) {
        return server != null && MidTierConfiguration.lookup(mappings, server) != null;
    }

    /** Pure matching rule, testable without SWT/Workbench. */
    public static String soleConfiguredConnected(Collection<String> connected,
            Collection<MidTierConfiguration.Mapping> mappings) {
        List<String> matched = new ArrayList<>();
        for (String server : connected) {
            if (configured(server, mappings)) matched.add(server);
        }
        return matched.size() == 1 ? matched.get(0) : null;
    }

    public static MidTierEditorContext current(IWorkbenchPart part, IEditorPart editor,
            ISelection selection, Collection<MidTierConfiguration.Mapping> mappings,
            Collection<String> connected) {
        MidTierEditorContext context;
        // The currently focused Object List selection takes precedence over a stale editor.
        if (part != null && !(part instanceof IEditorPart)) {
            context = MidTierEditorContext.captureServerSelection(selection);
            if (context != null) return configured(context.server(), mappings) ? context : null;
        }
        context = MidTierEditorContext.capture(editor);
        if (context != null) return configured(context.server(), mappings) ? context : null;
        context = MidTierEditorContext.captureServerSelection(selection);
        if (context != null) return configured(context.server(), mappings) ? context : null;
        String sole = soleConfiguredConnected(connected, mappings);
        return sole == null ? null : MidTierEditorContext.forServer(sole);
    }

    public static MidTierEditorContext current(IWorkbenchWindow window) {
        if (window == null || !ToolsPreferences.isMidTierCacheEnabled()) return null;
        IWorkbenchPage page = window.getActivePage();
        IWorkbenchPart part = page == null ? null : page.getActivePart();
        IEditorPart editor = page == null ? null : page.getActiveEditor();
        ISelection selection = window.getSelectionService() == null ? null
                : window.getSelectionService().getSelection();
        return current(part, editor, selection, ToolsPreferences.getMidTierServerMappings(),
                MidTierEditorContext.connectedServers());
    }

    public static boolean showToolbar(IWorkbenchWindow window) {
        return current(window) != null;
    }

    /** Reevaluate the toolbar as soon as the user saves server mappings. */
    public static void refreshToolbarVisibility() {
        try {
            if (!PlatformUI.isWorkbenchRunning()) return;
            IEvaluationService eval = PlatformUI.getWorkbench().getService(IEvaluationService.class);
            if (eval != null) {
                eval.requestEvaluation("se.yrell.developertools.toolbarAvailable");
                eval.requestEvaluation("se.yrell.developertools.supportsCacheFlush");
                eval.requestEvaluation("se.yrell.developertools.supportsSelectedFlush");
            }
        } catch (RuntimeException ignored) {
            // A preference change must not break Developer Studio's workbench.
        }
    }
}
