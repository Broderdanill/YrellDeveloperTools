package se.yrell.developertools.fastagent;

import java.io.File;
import java.net.URL;
import java.security.ProtectionDomain;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

/** Java agent entry point. Keep this class small; transformed BMC code calls FastFormsBridge. */
public final class FastFormsAgent {
    private static final String BRIDGE = "se/yrell/developertools/fastagent/FastFormsBridge";

    private static final String AR_DYNAMIC_PROVIDER = "com/bmc/arsys/studio/model/internal/providers/ARDynamicNamedListProvider";
    private static final String AR_BASE_NAMED_PROVIDER = "com/bmc/arsys/studio/model/internal/providers/ARBaseNamedListProvider";
    private static final String FORM_LIST_PROVIDER = "com/bmc/arsys/studio/model/internal/providers/FormListProvider";
    private static final String CHECKBOX_CONFIG = "com/bmc/arsys/studio/ui/views/objectlist/ObjectListCheckboxFilterManager$PropertyTypeCheckBoxConfiguration";
    private static final String FILTERING_SECTION = "com/bmc/arsys/studio/ui/views/objectlist/FilteringSection";
    private static final String FILTERING_SECTION_SELECTION = "com/bmc/arsys/studio/ui/views/objectlist/FilteringSection$8";
    private static final String OBJECT_LIST_VIEW = "com/bmc/arsys/studio/ui/views/objectlist/ObjectListView";
    private static final String OBJECT_LIST_VIEW_FILTER = "com/bmc/arsys/studio/ui/views/objectlist/ObjectListView$12";
    private static final String OBJECT_LIST_COMPONENT = "com/bmc/arsys/studio/commonui/common/objectlist/ObjectListComponent";
    private static final String OVERLAY_OBJECT_LIST_FILTER = "com/bmc/arsys/studio/commonui/common/objectlist/OverlayObjectListFilter";
    private static final String PROPERTY_FILTER = "com/bmc/arsys/studio/ui/views/objectlist/PropertyFilter";
    private static final String SQL_COMMANDS = "com/bmc/arsys/studio/model/internal/providers/SQLCommands";

    private FastFormsAgent() {}

    private static final java.util.List<JarFile> BOOTSTRAP_JARS = new java.util.ArrayList<JarFile>();

    public static void premain(String args, Instrumentation inst) {
        System.setProperty("se.yrell.developertools.fastFormsAgent.active", "true");
        appendBridgeOnlyToBootstrap(inst);
        FastFormsBridge.logAlways("loaded v10; enabled=" + FastFormsBridge.isEnabledForAgent() +
            ", values=" + FastFormsBridge.valuesForAgent() +
            ", logFile=" + FastFormsBridge.logFilePathForAgent() +
            ", bootstrapBridge=bridge-only");
        inst.addTransformer(new Transformer(), false);
    }

    public static void agentmain(String args, Instrumentation inst) {
        premain(args, inst);
    }

    /**
     * Make only FastFormsBridge visible to Eclipse/BMC bundle class loaders.
     *
     * v4 appended the whole agent jar to the bootstrap class loader. That also exposed ASM
     * to bootstrap and could split org.objectweb.asm classes between bootstrap and the
     * application class loader. ASM has package-private classes, so that split caused
     * IllegalAccessError when ClassWriter tried to access SymbolTable. v8 appends a tiny
     * temporary jar containing only FastFormsBridge.class; ASM stays with the agent.
     */
    private static void appendBridgeOnlyToBootstrap(Instrumentation inst) {
        JarFile jf = null;
        try {
            java.io.InputStream in = FastFormsAgent.class.getResourceAsStream("/se/yrell/developertools/fastagent/FastFormsBridge.class");
            if (in == null) throw new IllegalStateException("FastFormsBridge.class was not found in agent jar");

            File bootJar = File.createTempFile("devstudio-fastforms-bridge-", ".jar");
            bootJar.deleteOnExit();
            java.util.jar.JarOutputStream out = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(bootJar));
            try {
                java.util.jar.JarEntry entry = new java.util.jar.JarEntry("se/yrell/developertools/fastagent/FastFormsBridge.class");
                out.putNextEntry(entry);
                byte[] buf = new byte[8192];
                int n;
                try {
                    while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                } finally {
                    in.close();
                }
                out.closeEntry();
            } finally {
                out.close();
            }

            jf = new JarFile(bootJar);
            inst.appendToBootstrapClassLoaderSearch(jf);
            BOOTSTRAP_JARS.add(jf); // Keep JarFile alive for the duration of the process.
        } catch (Throwable t) {
            // Do not use FastFormsBridge here; if bootstrap append failed it may not be visible to BMC classes.
            System.err.println("[Yrell Developer Tools FastForms Agent] could not append bridge-only jar to bootstrap class loader: " + t);
            try { if (jf != null) jf.close(); } catch (Throwable ignored) {}
        }
    }

    private static final class Transformer implements ClassFileTransformer {
        @Override
        public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            if (!shouldTransform(className)) return null;
            try {
                ClassReader cr = new ClassReader(classfileBuffer);
                ClassWriter cw = new SafeClassWriter(cr, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                PatchClassVisitor cv = new PatchClassVisitor(cw, className);
                cr.accept(cv, ClassReader.EXPAND_FRAMES);
                if (cv.patched) {
                    FastFormsBridge.logAlways("transformed " + className.replace('/', '.'));
                    return cw.toByteArray();
                }
                FastFormsBridge.debugForAgent("no matching methods found in " + className.replace('/', '.'));
                return null;
            } catch (Throwable t) {
                FastFormsBridge.logAlways("transform failed for " + className + ": " + t);
                FastFormsBridge.printStackForAgent(t);
                return null;
            }
        }

        private boolean shouldTransform(String className) {
            return AR_DYNAMIC_PROVIDER.equals(className)
                || AR_BASE_NAMED_PROVIDER.equals(className)
                || FORM_LIST_PROVIDER.equals(className)
                || CHECKBOX_CONFIG.equals(className)
                || FILTERING_SECTION.equals(className)
                || FILTERING_SECTION_SELECTION.equals(className)
                || OBJECT_LIST_VIEW.equals(className)
                || OBJECT_LIST_VIEW_FILTER.equals(className)
                || OBJECT_LIST_COMPONENT.equals(className)
                || OVERLAY_OBJECT_LIST_FILTER.equals(className)
                || PROPERTY_FILTER.equals(className)
                || SQL_COMMANDS.equals(className);
        }
    }

    private static final class SafeClassWriter extends ClassWriter {
        SafeClassWriter(ClassReader classReader, int flags) { super(classReader, flags); }
        @Override protected String getCommonSuperClass(String type1, String type2) { return "java/lang/Object"; }
    }

    private static final class PatchClassVisitor extends ClassVisitor {
        private final String className;
        boolean patched;

        PatchClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (AR_DYNAMIC_PROVIDER.equals(className)
                && "getListFromDynamicQuery".equals(name)
                && "(ZZ)Lcom/bmc/arsys/studio/model/internal/providers/CompoundItemList;".equals(descriptor)) {
                patched = true;
                return new DynamicListMethodAdapter(api, mv, access, name, descriptor);
            }
            if (AR_BASE_NAMED_PROVIDER.equals(className)
                && "getEntries".equals(name)
                && "(ILcom/bmc/arsys/api/QualifierInfo;[ILjava/util/List;)Ljava/util/List;".equals(descriptor)) {
                patched = true;
                return new GetEntriesMethodAdapter(api, mv, access, name, descriptor);
            }
            if (AR_BASE_NAMED_PROVIDER.equals(className)
                && "getPartialObjects".equals(name)
                && "(Ljava/util/List;JLcom/bmc/arsys/api/ObjectBaseCriteria;)Ljava/util/List;".equals(descriptor)) {
                patched = true;
                return new GetPartialObjectsMethodAdapter(api, mv, access, name, descriptor);
            }
            if (FORM_LIST_PROVIDER.equals(className)
                && "getDynamicQuery".equals(name)
                && "(Lcom/bmc/arsys/api/Timestamp;Z)Lcom/bmc/arsys/api/RegularQuery;".equals(descriptor)) {
                patched = true;
                return new FormQueryMethodAdapter(api, mv, access, name, descriptor);
            }
            if (FORM_LIST_PROVIDER.equals(className)
                && (("getListForUser".equals(name) && "(Z)Lcom/bmc/arsys/studio/model/item/ItemList;".equals(descriptor))
                    || ("getListFromSQL".equals(name) && "(Z)Lcom/bmc/arsys/studio/model/item/ItemList;".equals(descriptor))
                    || ("getListFromForm".equals(name) && "(Z)Lcom/bmc/arsys/studio/model/internal/providers/CompoundItemList;".equals(descriptor)))) {
                patched = true;
                return new ProviderResultFilterAdapter(api, mv, access, name, descriptor);
            }
            if (AR_DYNAMIC_PROVIDER.equals(className)
                && "getListFromDynamicQuery".equals(name)
                && "(ZZ)Lcom/bmc/arsys/studio/model/internal/providers/CompoundItemList;".equals(descriptor)) {
                // This method is already patched for query injection above; no second adapter here.
            }
            if (CHECKBOX_CONFIG.equals(className)
                && "defaultSelected".equals(name)
                && "()Z".equals(descriptor)) {
                patched = true;
                return new DefaultSelectedAdapter(api, mv, access, name, descriptor);
            }
            if (FILTERING_SECTION.equals(className)
                && "<init>".equals(name)
                && descriptor.contains("Lcom/bmc/arsys/studio/ui/views/objectlist/ObjectListView;")) {
                patched = true;
                return new FilteringSectionConstructorAdapter(api, mv, access, name, descriptor);
            }
            if (FILTERING_SECTION.equals(className)
                && "getSelectedCheckboxNames".equals(name)
                && "()Ljava/util/List;".equals(descriptor)) {
                patched = true;
                return new SelectedCheckboxNamesAdapter(api, mv, access, name, descriptor);
            }

            if (FILTERING_SECTION_SELECTION.equals(className)
                && "widgetSelected".equals(name)
                && "(Lorg/eclipse/swt/events/SelectionEvent;)V".equals(descriptor)) {
                patched = true;
                return new CheckboxSelectionEventAdapter(api, mv, access, name, descriptor);
            }
            if (OBJECT_LIST_VIEW.equals(className)
                && ("createTypeListContent".equals(name) || "createFormListContent".equals(name))
                && "()V".equals(descriptor)) {
                patched = true;
                return new ObjectListViewContentCreatedAdapter(api, mv, access, name, descriptor);
            }
            if (PROPERTY_FILTER.equals(className)
                && "select".equals(name)
                && "(Lorg/eclipse/jface/viewers/Viewer;Ljava/lang/Object;Ljava/lang/Object;)Z".equals(descriptor)) {
                patched = true;
                return new PropertyFilterSelectAdapter(api, mv, access, name, descriptor);
            }
            if (OBJECT_LIST_VIEW_FILTER.equals(className)
                && "select".equals(name)
                && "(Lorg/eclipse/jface/viewers/Viewer;Ljava/lang/Object;Ljava/lang/Object;)Z".equals(descriptor)) {
                patched = true;
                return new ObjectListViewSelectAdapter(api, mv, access, name, descriptor);
            }
            if (OBJECT_LIST_COMPONENT.equals(className)
                && "setItems".equals(name)
                && "(Lcom/bmc/arsys/studio/model/item/ItemList;)V".equals(descriptor)) {
                patched = true;
                return new SetItemsAdapter(api, mv, access, name, descriptor);
            }
            if (OVERLAY_OBJECT_LIST_FILTER.equals(className)
                && "select".equals(name)
                && "(Lorg/eclipse/jface/viewers/Viewer;Ljava/lang/Object;Ljava/lang/Object;)Z".equals(descriptor)) {
                patched = true;
                return new OverlayListSelectAdapter(api, mv, access, name, descriptor);
            }
            if (OVERLAY_OBJECT_LIST_FILTER.equals(className)
                && "filter".equals(name)
                && "(Lorg/eclipse/jface/viewers/Viewer;Ljava/lang/Object;[Ljava/lang/Object;)[Ljava/lang/Object;".equals(descriptor)) {
                patched = true;
                return new OverlayListFilterAdapter(api, mv, access, name, descriptor);
            }
            if (SQL_COMMANDS.equals(className)
                && "modifySQLForOverlay".equals(name)
                && "(Ljava/lang/String;Lcom/bmc/arsys/studio/model/store/IStore;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;".equals(descriptor)) {
                patched = true;
                return new ModifySqlForOverlayAdapter(api, mv, access, name, descriptor);
            }
            return mv;
        }
    }

    private static final class DynamicListMethodAdapter extends AdviceAdapter {
        DynamicListMethodAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            if (opcode == INVOKEVIRTUAL && "getDynamicQuery".equals(name)
                && "(Lcom/bmc/arsys/api/Timestamp;Z)Lcom/bmc/arsys/api/RegularQuery;".equals(descriptor)) {
                dup();
                loadThis();
                swap();
                visitMethodInsn(INVOKESTATIC, BRIDGE, "applyOverlayFilterToRegularQuery", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
            }
        }
    }

    private static final class FormQueryMethodAdapter extends AdviceAdapter {
        FormQueryMethodAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodExit(int opcode) {
            if (opcode == ARETURN) {
                dup();
                loadThis();
                swap();
                visitMethodInsn(INVOKESTATIC, BRIDGE, "applyOverlayFilterToRegularQuery", "(Ljava/lang/Object;Ljava/lang/Object;)V", false);
            }
        }
    }

    private static final class GetEntriesMethodAdapter extends AdviceAdapter {
        GetEntriesMethodAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodEnter() {
            loadThis();
            loadArg(1);
            visitMethodInsn(INVOKESTATIC, BRIDGE, "augmentEntryQualifier", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
            checkCast(Type.getObjectType("com/bmc/arsys/api/QualifierInfo"));
            storeArg(1);
        }
    }


    private static final class GetPartialObjectsMethodAdapter extends AdviceAdapter {
        GetPartialObjectsMethodAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodEnter() {
            loadThis();
            loadArg(0);
            loadArg(1);
            loadArg(2);
            visitMethodInsn(INVOKESTATIC, BRIDGE, "getPartialObjectsWithServerCustomizationFilter", "(Ljava/lang/Object;Ljava/lang/Object;JLjava/lang/Object;)Ljava/lang/Object;", false);
            dup();
            Label cont = new Label();
            visitJumpInsn(IFNULL, cont);
            checkCast(Type.getObjectType("java/util/List"));
            returnValue();
            visitLabel(cont);
            pop();
        }
    }

    private static final class DefaultSelectedAdapter extends AdviceAdapter {
        DefaultSelectedAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodEnter() {
            loadThis();
            visitMethodInsn(INVOKESTATIC, BRIDGE, "shouldDeselectBaseCheckbox", "(Ljava/lang/Object;)Z", false);
            Label cont = new Label();
            visitJumpInsn(IFEQ, cont);
            push(false);
            returnValue();
            visitLabel(cont);
        }
    }

    private static final class FilteringSectionConstructorAdapter extends AdviceAdapter {
        FilteringSectionConstructorAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodExit(int opcode) {
            if (opcode == RETURN) {
                loadArg(4);
                visitMethodInsn(INVOKESTATIC, BRIDGE, "scheduleTriggerFilters", "(Ljava/lang/Object;)V", false);
            }
        }
    }

    private static final class ObjectListViewContentCreatedAdapter extends AdviceAdapter {
        ObjectListViewContentCreatedAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodExit(int opcode) {
            if (opcode == RETURN) {
                loadThis();
                visitMethodInsn(INVOKESTATIC, BRIDGE, "scheduleTriggerFilters", "(Ljava/lang/Object;)V", false);
            }
        }
    }

    private static final class PropertyFilterSelectAdapter extends AdviceAdapter {
        PropertyFilterSelectAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodEnter() {
            loadThis();
            loadArg(2);
            visitMethodInsn(INVOKESTATIC, BRIDGE, "propertyFilterDecision", "(Ljava/lang/Object;Ljava/lang/Object;)I", false);
            dup();
            Label cont = new Label();
            visitJumpInsn(IFLT, cont);
            push(1);
            visitInsn(IAND);
            returnValue();
            visitLabel(cont);
            pop();
        }
    }

    private static final class ProviderResultFilterAdapter extends AdviceAdapter {
        private final String returnType;
        ProviderResultFilterAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) {
            super(api, mv, access, name, descriptor);
            this.returnType = Type.getReturnType(descriptor).getInternalName();
        }
        @Override protected void onMethodExit(int opcode) {
            if (opcode == ARETURN) {
                loadThis();
                swap();
                visitMethodInsn(INVOKESTATIC, BRIDGE, "filterProviderResult", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", false);
                checkCast(Type.getObjectType(returnType));
            }
        }
    }

    private static final class SelectedCheckboxNamesAdapter extends AdviceAdapter {
        SelectedCheckboxNamesAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodExit(int opcode) {
            if (opcode == ARETURN) {
                visitMethodInsn(INVOKESTATIC, BRIDGE, "sanitizeSelectedCheckboxNames", "(Ljava/util/List;)Ljava/util/List;", false);
            }
        }
    }

    private static final class ObjectListViewSelectAdapter extends AdviceAdapter {
        ObjectListViewSelectAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodEnter() {
            loadArg(2);
            visitMethodInsn(INVOKESTATIC, BRIDGE, "shouldRejectBaseModelItem", "(Ljava/lang/Object;)Z", false);
            Label cont = new Label();
            visitJumpInsn(IFEQ, cont);
            push(false);
            returnValue();
            visitLabel(cont);
        }
    }

    private static final class SetItemsAdapter extends AdviceAdapter {
        SetItemsAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodEnter() {
            loadArg(0);
            visitMethodInsn(INVOKESTATIC, BRIDGE, "probeItemListForDiagnostics", "(Ljava/lang/Object;)V", false);
            loadArg(0);
            visitMethodInsn(INVOKESTATIC, BRIDGE, "filterItemListForDisplay", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
            checkCast(Type.getObjectType("com/bmc/arsys/studio/model/item/ItemList"));
            storeArg(0);
        }
    }

    private static final class CheckboxSelectionEventAdapter extends AdviceAdapter {
        CheckboxSelectionEventAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodEnter() {
            loadArg(0);
            visitMethodInsn(INVOKESTATIC, BRIDGE, "syncCheckboxSelectionEvent", "(Ljava/lang/Object;)V", false);
        }
    }

    private static final class OverlayListSelectAdapter extends AdviceAdapter {
        OverlayListSelectAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodEnter() {
            loadArg(2);
            visitMethodInsn(INVOKESTATIC, BRIDGE, "shouldRejectForOverlayGate", "(Ljava/lang/Object;)Z", false);
            Label cont = new Label();
            visitJumpInsn(IFEQ, cont);
            push(false);
            returnValue();
            visitLabel(cont);
        }
    }

    private static final class OverlayListFilterAdapter extends AdviceAdapter {
        OverlayListFilterAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodExit(int opcode) {
            if (opcode == ARETURN) {
                visitMethodInsn(INVOKESTATIC, BRIDGE, "filterModelItemArrayForOverlayGate", "([Ljava/lang/Object;)[Ljava/lang/Object;", false);
            }
        }
    }

    private static final class ModifySqlForOverlayAdapter extends AdviceAdapter {
        ModifySqlForOverlayAdapter(int api, MethodVisitor mv, int access, String name, String descriptor) { super(api, mv, access, name, descriptor); }
        @Override protected void onMethodExit(int opcode) {
            if (opcode == ARETURN) {
                loadArg(1);
                loadArg(2);
                loadArg(3);
                visitMethodInsn(INVOKESTATIC, BRIDGE, "filterOverlaySql", "(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;", false);
            }
        }
    }

}
