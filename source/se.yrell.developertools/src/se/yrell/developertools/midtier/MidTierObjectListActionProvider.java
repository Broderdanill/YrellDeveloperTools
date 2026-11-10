package se.yrell.developertools.midtier;

import java.util.Collections;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import com.bmc.arsys.studio.model.item.FormItem;
import com.bmc.arsys.studio.ui.views.objectlist.actions.BaseObjectListAction;
import com.bmc.arsys.studio.ui.views.objectlist.actions.ObjectListActionProvider;
import com.bmc.arsys.studio.ui.views.objectlist.actions.ObjectListActionService;

import se.yrell.developertools.ToolsConstants;
import se.yrell.developertools.ManualSuffixCleanup;
import se.yrell.developertools.ManualSuffixCleanupJob;
import se.yrell.developertools.ToolsPreferences;

/**
 * BMC's Object List builds its own menu through StudioTypeRegistry generic action
 * providers. Eclipse popup:any menu contributions do NOT reliably reach it.
 */
public final class MidTierObjectListActionProvider extends ObjectListActionProvider {
    @Override
    protected void doFillContextMenu(IMenuManager menu) {
        if (!(getSelection() instanceof org.eclipse.jface.viewers.IStructuredSelection structured)
                || structured.size() != 1) return;
        // Capture the actual context-menu selection, never a stale open editor.
        Object selected = structured.getFirstElement();
        if (selected instanceof FormItem form && !form.isPlaceHolder()) {
            appendSuffixAction(menu, form, ManualSuffixCleanup.Kind.FIELD_NAMES,
                    "Remove __c Suffix from Field Names...");
            appendSuffixAction(menu, form, ManualSuffixCleanup.Kind.VIEW_NAMES,
                    "Remove __c Suffix from View Names...");
        }
        if (!ToolsPreferences.isMidTierCacheEnabled()) return;
        final MidTierEditorContext context = MidTierEditorContext.captureSelected(getSelection());
        if (context == null || context.forms().isEmpty()) return;
        if (selected instanceof FormItem form && form.isPlaceHolder()) return;
        if (!MidTierFlushAvailability.configured(context.server(), ToolsPreferences.getMidTierServerMappings())) return;

        Action flush = new Action("Flush MidTier Object", IAction.AS_PUSH_BUTTON) {
            @Override public void run() {
                // Always use the captured right-click selection. Do not substitute a stale editor.
                MidTierFlushObjectHandler.startDirect(site == null ? null : site.getShell(), context);
            }
        };
        ImageDescriptor image = AbstractUIPlugin.imageDescriptorFromPlugin(ToolsConstants.PLUGIN_ID, "icons/toilet_16.png");
        if (image != null) flush.setImageDescriptor(image);
        flush.setToolTipText("Flush MidTier cache for " + context.forms().get(0));
        if (menu.find(ObjectListActionService.GROUP_ADDITIONS) != null)
            menu.appendToGroup(ObjectListActionService.GROUP_ADDITIONS, flush);
        else
            menu.add(flush);
    }

    private void appendSuffixAction(IMenuManager menu, FormItem form,
                                    ManualSuffixCleanup.Kind kind, String label) {
        Action action = new Action(label, IAction.AS_PUSH_BUTTON) {
            @Override public void run() {
                ManualSuffixCleanupJob.start(site == null ? null : site.getShell(), form, kind);
            }
        };
        action.setToolTipText("Preview and confirm the explicit __c rename on selected form '" + form.getName() + "'");
        if (menu.find(ObjectListActionService.GROUP_ADDITIONS) != null)
            menu.appendToGroup(ObjectListActionService.GROUP_ADDITIONS, action);
        else menu.add(action);
    }

    @Override public List<BaseObjectListAction> getHookableActions(String id) {
        return Collections.emptyList();
    }
}
