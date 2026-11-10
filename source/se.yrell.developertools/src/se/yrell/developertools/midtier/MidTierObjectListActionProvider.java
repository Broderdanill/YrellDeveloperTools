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
import se.yrell.developertools.ToolsPreferences;

/**
 * BMC's Object List builds its own menu through StudioTypeRegistry generic action
 * providers. Eclipse popup:any menu contributions do NOT reliably reach it.
 */
public final class MidTierObjectListActionProvider extends ObjectListActionProvider {
    @Override
    protected void doFillContextMenu(IMenuManager menu) {
        if (!ToolsPreferences.isMidTierCacheEnabled()) return;
        final MidTierEditorContext context = MidTierEditorContext.captureSelected(getSelection());
        if (context == null || context.forms().isEmpty()) return;
        // A placeholder is a browse-only row and cannot be flushed as a real form.
        Object selected = ((org.eclipse.jface.viewers.IStructuredSelection) getSelection()).getFirstElement();
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

    @Override public List<BaseObjectListAction> getHookableActions(String id) {
        return Collections.emptyList();
    }
}
