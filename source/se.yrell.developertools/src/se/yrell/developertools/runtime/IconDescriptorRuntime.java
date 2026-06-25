package se.yrell.developertools.runtime;

import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.PropertyDescriptor;

import com.bmc.arsys.studio.commonui.common.properties.IUIPropertyDescriptor;
import com.bmc.arsys.studio.commonui.common.properties.EditableConditionType;

import se.yrell.developertools.icons.PwaIconDialogPropertyDescriptor;

public final class IconDescriptorRuntime {
    private IconDescriptorRuntime() {
    }

    public static IPropertyDescriptor[] wrapPropertyDescriptors(IPropertyDescriptor[] descriptors) {
        if (descriptors == null || descriptors.length == 0) {
            return descriptors;
        }
        IPropertyDescriptor[] copy = null;
        for (int i = 0; i < descriptors.length; i++) {
            IPropertyDescriptor wrapped = wrapDescriptor(descriptors[i]);
            if (wrapped != descriptors[i]) {
                if (copy == null) {
                    copy = new IPropertyDescriptor[descriptors.length];
                    System.arraycopy(descriptors, 0, copy, 0, descriptors.length);
                }
                copy[i] = wrapped;
            }
        }
        return copy == null ? descriptors : copy;
    }

    public static IUIPropertyDescriptor wrapUiDescriptor(IUIPropertyDescriptor descriptor, Object richMetaDataProperty) {
        if (descriptor == null || !isIconDescriptor(descriptor, richMetaDataProperty)) {
            return descriptor;
        }
        PwaIconDialogPropertyDescriptor replacement = new PwaIconDialogPropertyDescriptor(descriptor.getId(), descriptor.getDisplayName());
        copyCommon(descriptor, replacement);
        copyUi(descriptor, replacement);
        return replacement;
    }

    private static IPropertyDescriptor wrapDescriptor(IPropertyDescriptor descriptor) {
        if (descriptor == null || !isIconDescriptor(descriptor, null)) {
            return descriptor;
        }
        PwaIconDialogPropertyDescriptor replacement = new PwaIconDialogPropertyDescriptor(descriptor.getId(), descriptor.getDisplayName());
        copyCommon(descriptor, replacement);
        if (descriptor instanceof IUIPropertyDescriptor) {
            copyUi((IUIPropertyDescriptor) descriptor, replacement);
        }
        return replacement;
    }

    private static boolean isIconDescriptor(IPropertyDescriptor descriptor, Object richMetaDataProperty) {
        try {
            if (isIconText(descriptor.getDisplayName()) || isIconText(String.valueOf(descriptor.getId()))) {
                return true;
            }
            if (richMetaDataProperty != null) {
                String id = invokeString(richMetaDataProperty, "getId");
                String label = invokeString(richMetaDataProperty, "getLabel");
                return isIconText(id) || isIconText(label);
            }
        } catch (Throwable ignored) {
            // Not an icon descriptor.
        }
        return false;
    }

    private static boolean isIconText(String text) {
        if (text == null) {
            return false;
        }
        String value = text.replace('\u00a0', ' ').trim();
        while (value.endsWith(":")) {
            value = value.substring(0, value.length() - 1).trim();
        }
        return "Icon".equals(value);
    }

    private static String invokeString(Object target, String methodName) {
        try {
            java.lang.reflect.Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            Object result = method.invoke(target);
            return result == null ? "" : String.valueOf(result);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static void copyCommon(IPropertyDescriptor source, PropertyDescriptor target) {
        try { target.setCategory(source.getCategory()); } catch (Throwable ignored) {}
        try { target.setDescription(source.getDescription()); } catch (Throwable ignored) {}
        try { target.setFilterFlags(source.getFilterFlags()); } catch (Throwable ignored) {}
        try { target.setHelpContextIds(source.getHelpContextIds()); } catch (Throwable ignored) {}
        try {
            if (source.getLabelProvider() != null) {
                target.setLabelProvider(source.getLabelProvider());
            }
        } catch (Throwable ignored) {}
    }

    private static void copyUi(IUIPropertyDescriptor source, PwaIconDialogPropertyDescriptor target) {
        try { target.setReadOnly(source.isReadOnly()); } catch (Throwable ignored) {}
        try { target.setRules(source.getRules()); } catch (Throwable ignored) {}
        try {
            EditableConditionType cond = source.getPropertyEditableOverlayConditionEnum();
            if (cond != null) {
                target.setPropertyEditableOverlayCondition(cond);
            }
        } catch (Throwable ignored) {}
    }
}
