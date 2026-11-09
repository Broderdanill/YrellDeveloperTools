package se.yrell.developertools.midtier;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchWindow;
import com.bmc.arsys.studio.model.item.APIItem;
import se.yrell.developertools.ToolsPreferences;

public final class MidTierFlushPropertyTester extends PropertyTester {
    @Override public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
        if (!ToolsPreferences.isMidTierCacheEnabled()) return false;
        if ("supportsSelectedFlush".equals(property)) {
            MidTierEditorContext ctx = MidTierEditorContext.captureSelectedObject(receiver);
            return ctx != null && MidTierFlushAvailability.configured(ctx.server(), ToolsPreferences.getMidTierServerMappings());
        }
        if ("supportsCacheFlush".equals(property)) {
            if (!(receiver instanceof IEditorPart editor)) return false;
            MidTierEditorContext ctx = MidTierEditorContext.capture(editor);
            return ctx != null && MidTierFlushAvailability.configured(ctx.server(), ToolsPreferences.getMidTierServerMappings());
        }
        if ("toolbarAvailable".equals(property))
            return receiver instanceof IWorkbenchWindow window && MidTierFlushAvailability.showToolbar(window);
        return false;
    }
}
