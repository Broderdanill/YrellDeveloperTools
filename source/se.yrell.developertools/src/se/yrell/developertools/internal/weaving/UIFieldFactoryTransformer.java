package se.yrell.developertools.internal.weaving;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class UIFieldFactoryTransformer implements Opcodes {
    private static final String TARGET_OWNER = "com/bmc/arsys/studio/ui/editors/form/model/UIFieldFactory";
    private static final String TARGET_METHOD = "createNewARField";
    private static final String TARGET_DESC = "(Lcom/bmc/arsys/studio/model/store/IFormObject;Ljava/lang/Class;)Lcom/bmc/arsys/studio/model/store/IFieldObject;";


    private static final String GENERATE_UNIQUE_METHOD = "generateUniqueFieldName";
    private static final String GENERATE_UNIQUE_DESC = "(Lcom/bmc/arsys/studio/model/store/IFormObject;Ljava/lang/Class;)Ljava/lang/String;";
    private static final String FORM_OBJECT_OWNER = "com/bmc/arsys/studio/model/store/IFormObject";
    private static final String FORM_GENERATE_UNIQUE_NAME = "generateUniqueName";
    private static final String FORM_GENERATE_UNIQUE_NAME_DESC = "(Ljava/lang/String;)Ljava/lang/String;";

    private static final String SUFFIX_RUNTIME_NAME_METHOD = "cleanNewFieldName";
    private static final String SUFFIX_RUNTIME_NAME_DESC = "(Lcom/bmc/arsys/studio/model/store/IFormObject;Ljava/lang/String;)Ljava/lang/String;";

    private static final String INIT_METHOD = "initARField";
    private static final String INIT_DESC = "(Ljava/lang/String;Lcom/bmc/arsys/studio/model/store/IFormObject;Lcom/bmc/arsys/studio/model/store/IFieldObject;Lcom/bmc/arsys/api/DataType;Ljava/lang/Class;)V";

    private static final String SUFFIX_RUNTIME_OWNER = "se/yrell/developertools/runtime/CustomSuffixRuntime";
    private static final String SUFFIX_RUNTIME_METHOD = "cleanCreatedField";
    private static final String SUFFIX_RUNTIME_DESC = "(Lcom/bmc/arsys/studio/model/store/IFormObject;Lcom/bmc/arsys/studio/model/store/IFieldObject;Ljava/lang/Class;)V";

    private static final String DEFAULT_NAME_OWNER = "se/yrell/developertools/runtime/DefaultNameRuntime";
    private static final String DEFAULT_NAME_METHOD = "applyNewFieldDefaults";
    private static final String DEFAULT_NAME_DESC = "(Lcom/bmc/arsys/studio/model/store/IFormObject;Lcom/bmc/arsys/studio/model/store/IFieldObject;Ljava/lang/Class;)V";

    private static final String ALLOCATOR_OWNER = "se/yrell/developertools/runtime/AutoFieldIdAllocator";
    private static final String ALLOCATOR_METHOD = "assignIfNeeded";
    private static final String ALLOCATOR_DESC = "(Lcom/bmc/arsys/studio/model/store/IFormObject;Lcom/bmc/arsys/studio/model/store/IFieldObject;Ljava/lang/Class;)V";

    private UIFieldFactoryTransformer() {
    }

    static byte[] transform(byte[] originalBytes) {
        ClassReader reader = new ClassReader(originalBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        TransformingClassVisitor visitor = new TransformingClassVisitor(writer);
        reader.accept(visitor, 0);
        return visitor.transformed ? writer.toByteArray() : null;
    }

    private static final class TransformingClassVisitor extends ClassVisitor {
        private boolean transformed;

        private TransformingClassVisitor(ClassVisitor classVisitor) {
            super(ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (GENERATE_UNIQUE_METHOD.equals(name) && GENERATE_UNIQUE_DESC.equals(descriptor)) {
                return new MethodVisitor(ASM9, methodVisitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                        if (opcode == INVOKEINTERFACE
                                && FORM_OBJECT_OWNER.equals(owner)
                                && FORM_GENERATE_UNIQUE_NAME.equals(methodName)
                                && FORM_GENERATE_UNIQUE_NAME_DESC.equals(methodDescriptor)) {
                            // Stack after IFormObject.generateUniqueName(...): generated name.
                            // Clean immediately for every field type, including UIDataCharacterField,
                            // before the method stores it in the local generated-name variable.
                            super.visitVarInsn(ALOAD, 0);
                            super.visitInsn(SWAP);
                            super.visitMethodInsn(INVOKESTATIC, SUFFIX_RUNTIME_OWNER, SUFFIX_RUNTIME_NAME_METHOD, SUFFIX_RUNTIME_NAME_DESC, false);
                            transformed = true;
                        }
                    }
                };
            }
            if (TARGET_METHOD.equals(name) && TARGET_DESC.equals(descriptor)) {
                return new MethodVisitor(ASM9, methodVisitor) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);

                        if (opcode == INVOKESTATIC && TARGET_OWNER.equals(owner) && GENERATE_UNIQUE_METHOD.equals(name) && GENERATE_UNIQUE_DESC.equals(descriptor)) {
                            // Stack after original call: generated field name.
                            // Rewrite only this newly generated field name before initARField(...) receives it.
                            // This is safer than the old broad canAppendCustomString hook and does not affect form-save.
                            super.visitVarInsn(ALOAD, 0);
                            super.visitInsn(SWAP);
                            super.visitMethodInsn(INVOKESTATIC, SUFFIX_RUNTIME_OWNER, SUFFIX_RUNTIME_NAME_METHOD, SUFFIX_RUNTIME_NAME_DESC, false);
                            transformed = true;
                            return;
                        }

                        if (opcode == INVOKESTATIC && TARGET_OWNER.equals(owner) && INIT_METHOD.equals(name) && INIT_DESC.equals(descriptor)) {
                            // createNewARField is static. Locals:
                            // 0 = IFormObject form, 1 = Class<? extends UIField> fieldClass, 4 = IFieldObject new field.
                            // Also clean the just-created field directly as a second safety net.
                            super.visitVarInsn(ALOAD, 0);
                            super.visitVarInsn(ALOAD, 4);
                            super.visitVarInsn(ALOAD, 1);
                            super.visitMethodInsn(INVOKESTATIC, SUFFIX_RUNTIME_OWNER, SUFFIX_RUNTIME_METHOD, SUFFIX_RUNTIME_DESC, false);

                            super.visitVarInsn(ALOAD, 0);
                            super.visitVarInsn(ALOAD, 4);
                            super.visitVarInsn(ALOAD, 1);
                            super.visitMethodInsn(INVOKESTATIC, DEFAULT_NAME_OWNER, DEFAULT_NAME_METHOD, DEFAULT_NAME_DESC, false);

                            super.visitVarInsn(ALOAD, 0);
                            super.visitVarInsn(ALOAD, 4);
                            super.visitVarInsn(ALOAD, 1);
                            super.visitMethodInsn(INVOKESTATIC, ALLOCATOR_OWNER, ALLOCATOR_METHOD, ALLOCATOR_DESC, false);
                            transformed = true;
                        }
                    }
                };
            }
            return methodVisitor;
        }
    }
}
