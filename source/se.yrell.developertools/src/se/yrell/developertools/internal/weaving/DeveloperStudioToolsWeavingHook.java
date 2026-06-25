package se.yrell.developertools.internal.weaving;

import java.nio.charset.StandardCharsets;

import org.osgi.framework.hooks.weaving.WeavingException;
import org.osgi.framework.hooks.weaving.WeavingHook;
import org.osgi.framework.hooks.weaving.WovenClass;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

public class DeveloperStudioToolsWeavingHook implements WeavingHook {
    private static final String FIELD_FACTORY_CLASS = "com.bmc.arsys.studio.ui.editors.form.model.UIFieldFactory";
    private static final String ADD_FIELD_TO_VIEW_COMMAND_CLASS = "com.bmc.arsys.studio.ui.editors.form.commands.AddFieldToViewCommand";
    private static final String UI_TABLE_FIELD_CLASS = "com.bmc.arsys.studio.ui.editors.form.model.UITableField";
    private static final String FORM_HELPER_CLASS = "com.bmc.arsys.studio.model.internal.helper.FormHelper";
    private static final String UI_FIELD_CLASS = "com.bmc.arsys.studio.ui.editors.form.model.UIField";
    private static final String ITEM_PROPERTY_SOURCE_CLASS = "com.bmc.arsys.studio.ui.common.properties.ItemPropertySource";
    private static final String BMC_PREFIX = "com.bmc.arsys.studio.";
    private static final String RUNTIME_PACKAGE = "se.yrell.developertools.runtime";
    private static final String CAN_APPEND = "canAppendCustomString";

    @Override
    public void weave(WovenClass wovenClass) throws WeavingException {
        String className = wovenClass.getClassName();
        if (className == null) {
            return;
        }

        // Never weave our own plugin classes or ASM/OSGi internals.
        // Loading transformer helper classes while a weave call is active can otherwise
        // re-enter this hook and cause ClassCircularityError, especially for
        // FastFormsTransformer$SafeClassWriter.
        if (isOwnOrInfrastructureClass(className)) {
            return;
        }

        byte[] originalBytes = wovenClass.getBytes();
        if (originalBytes == null || originalBytes.length == 0) {
            return;
        }

        byte[] currentBytes = originalBytes;
        boolean modified = false;

        String internalClassName = className.replace('.', '/');
        if (ToolsPreferences.isFastFormsEnabled() && FastFormsTransformer.shouldTransform(internalClassName)) {
            try {
                byte[] transformed = FastFormsTransformer.transform(internalClassName, currentBytes);
                if (transformed != null) {
                    currentBytes = transformed;
                    modified = true;
                    Log.info("Fast Forms hook applied to " + className + ".");
                }
            } catch (Throwable t) {
                Log.error("Failed to weave Fast Forms hook into " + className + ".", t);
                throw new WeavingException("Failed to weave Fast Forms hook", t);
            }
        }

        if (FIELD_FACTORY_CLASS.equals(className)) {
            try {
                byte[] transformed = UIFieldFactoryTransformer.transform(currentBytes);
                if (transformed != null) {
                    currentBytes = transformed;
                    modified = true;
                    Log.info("UI field creation hook applied to " + FIELD_FACTORY_CLASS + ".");
                } else {
                    Log.warn("UI field creation hook did not find expected bytecode in " + FIELD_FACTORY_CLASS + ".");
                }
            } catch (Throwable t) {
                Log.error("Failed to weave UI field creation hook into " + FIELD_FACTORY_CLASS + ".", t);
                throw new WeavingException("Failed to weave UI field creation hook", t);
            }
        }

        if (ADD_FIELD_TO_VIEW_COMMAND_CLASS.equals(className)) {
            try {
                byte[] transformed = AddFieldToViewCommandTransformer.transform(currentBytes);
                if (transformed != null) {
                    currentBytes = transformed;
                    modified = true;
                    Log.info("Default naming hook applied to " + ADD_FIELD_TO_VIEW_COMMAND_CLASS + ".");
                } else {
                    Log.warn("Default naming hook did not find expected bytecode in " + ADD_FIELD_TO_VIEW_COMMAND_CLASS + ".");
                }
            } catch (Throwable t) {
                Log.error("Failed to weave default naming hook into " + ADD_FIELD_TO_VIEW_COMMAND_CLASS + ".", t);
                throw new WeavingException("Failed to weave default naming hook", t);
            }
        }

        if (UI_TABLE_FIELD_CLASS.equals(className)) {
            try {
                byte[] transformed = UITableFieldTransformer.transform(currentBytes);
                if (transformed != null) {
                    currentBytes = transformed;
                    modified = true;
                    Log.info("Default naming table-column hook applied to " + UI_TABLE_FIELD_CLASS + ".");
                } else {
                    Log.warn("Default naming table-column hook did not find expected bytecode in " + UI_TABLE_FIELD_CLASS + ".");
                }
            } catch (Throwable t) {
                Log.error("Failed to weave table-column default naming hook into " + UI_TABLE_FIELD_CLASS + ".", t);
                throw new WeavingException("Failed to weave table-column default naming hook", t);
            }
        }


        if (FORM_HELPER_CLASS.equals(className)) {
            try {
                byte[] transformed = FormHelperNameTransformer.transform(currentBytes);
                if (transformed != null) {
                    currentBytes = transformed;
                    modified = true;
                    Log.info("FormHelper generated-name cleanup hook applied to " + FORM_HELPER_CLASS + ".");
                } else {
                    Log.warn("FormHelper generated-name cleanup hook did not find expected bytecode in " + FORM_HELPER_CLASS + ".");
                }
            } catch (Throwable t) {
                Log.error("Failed to weave FormHelper generated-name cleanup hook into " + FORM_HELPER_CLASS + ".", t);
                throw new WeavingException("Failed to weave FormHelper generated-name cleanup hook", t);
            }
        }

        if (UI_FIELD_CLASS.equals(className)) {
            try {
                byte[] transformed = UIFieldPropertyDescriptorTransformer.transform(currentBytes);
                if (transformed != null) {
                    currentBytes = transformed;
                    modified = true;
                    Log.info("PWA Icon property descriptor hook applied to " + UI_FIELD_CLASS + ".");
                } else {
                    Log.warn("PWA Icon property descriptor hook did not find expected bytecode in " + UI_FIELD_CLASS + ".");
                }
            } catch (Throwable t) {
                Log.error("Failed to weave PWA Icon property descriptor hook into " + UI_FIELD_CLASS + ".", t);
                throw new WeavingException("Failed to weave PWA Icon property descriptor hook", t);
            }
        }

        if (ITEM_PROPERTY_SOURCE_CLASS.equals(className)) {
            try {
                byte[] transformed = ItemPropertySourceTransformer.transform(currentBytes);
                if (transformed != null) {
                    currentBytes = transformed;
                    modified = true;
                    Log.info("PWA Icon item-property descriptor hook applied to " + ITEM_PROPERTY_SOURCE_CLASS + ".");
                } else {
                    Log.warn("PWA Icon item-property descriptor hook did not find expected bytecode in " + ITEM_PROPERTY_SOURCE_CLASS + ".");
                }
            } catch (Throwable t) {
                Log.error("Failed to weave PWA Icon item-property descriptor hook into " + ITEM_PROPERTY_SOURCE_CLASS + ".", t);
                throw new WeavingException("Failed to weave PWA Icon item-property descriptor hook", t);
            }
        }

        // Targeted __c removal: we still weave every BMC class that calls Helper.canAppendCustomString(...),
        // but the runtime method only suppresses the suffix on new object/field creation stacks. This catches
        // UIFieldFactory, FormHelper, ARFieldFactory and ARFormFactory paths without touching save/rename paths.
        if (className.startsWith(BMC_PREFIX) && containsAscii(currentBytes, CAN_APPEND)) {
            try {
                byte[] transformed = CustomSuffixTransformer.transform(currentBytes);
                if (transformed != null) {
                    currentBytes = transformed;
                    modified = true;
                    Log.info("Custom suffix creation guard hook applied to " + className + ".");
                }
            } catch (Throwable t) {
                Log.error("Failed to weave custom suffix creation guard hook into " + className + ".", t);
                throw new WeavingException("Failed to weave custom suffix creation guard hook", t);
            }
        }

        if (modified) {
            wovenClass.setBytes(currentBytes);
            if (!wovenClass.getDynamicImports().contains(RUNTIME_PACKAGE)) {
                wovenClass.getDynamicImports().add(RUNTIME_PACKAGE);
            }
        }
    }

    private static boolean isOwnOrInfrastructureClass(String className) {
        return className.startsWith("se.yrell.developertools.")
            || className.startsWith("org.objectweb.asm.")
            || className.startsWith("org.osgi.")
            || className.startsWith("org.eclipse.osgi.");
    }

    private static boolean containsAscii(byte[] bytes, String text) {
        byte[] pattern = text.getBytes(StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= bytes.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (bytes[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
