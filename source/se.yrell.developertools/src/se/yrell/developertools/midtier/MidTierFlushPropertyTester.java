package se.yrell.developertools.midtier;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.ui.IEditorPart;
import se.yrell.developertools.ToolsPreferences;

public final class MidTierFlushPropertyTester extends PropertyTester {
    @Override public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
        return ToolsPreferences.isMidTierCacheEnabled()
                && receiver instanceof IEditorPart editor && MidTierEditorContext.supports(editor);
    }
}
