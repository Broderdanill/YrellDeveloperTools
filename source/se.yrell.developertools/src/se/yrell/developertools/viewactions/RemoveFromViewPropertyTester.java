package se.yrell.developertools.viewactions;

import org.eclipse.core.expressions.PropertyTester;

public class RemoveFromViewPropertyTester extends PropertyTester {
    @Override
    public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
        if (!"canRemoveFromView".equals(property)) {
            return false;
        }
        return RemoveFromViewSupport.canRemoveFromView(receiver);
    }
}
