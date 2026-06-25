package se.yrell.developertools.internal.weaving;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Hooks BMC's AddFieldToViewCommand so defaults can be applied when a UI field
 * is materialized through a path other than UIFieldFactory.createNewARField.
 */
final class AddFieldToViewCommandTransformer implements Opcodes {
    private static final String TARGET_OWNER = "com/bmc/arsys/studio/ui/editors/form/commands/AddFieldToViewCommand";
    private static final String TARGET_METHOD = "redo";
    private static final String TARGET_DESC = "()V";
    private static final String NEW_FIELD_NAME = "newField";
    private static final String NEW_FIELD_DESC = "Lcom/bmc/arsys/studio/ui/editors/form/model/UIField;";

    private static final String SUFFIX_OWNER = "se/yrell/developertools/runtime/CustomSuffixRuntime";
    private static final String SUFFIX_METHOD = "cleanUiField";
    private static final String SUFFIX_DESC = "(Ljava/lang/Object;)V";

    private static final String DEFAULT_NAME_OWNER = "se/yrell/developertools/runtime/DefaultNameRuntime";
    private static final String DEFAULT_NAME_METHOD = "applyUiFieldDefaults";
    private static final String DEFAULT_NAME_DESC = "(Ljava/lang/Object;)V";

    private AddFieldToViewCommandTransformer() {
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
            if (TARGET_METHOD.equals(name) && TARGET_DESC.equals(descriptor)) {
                return new MethodVisitor(ASM9, methodVisitor) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        super.visitFieldInsn(opcode, owner, name, descriptor);
                        if (opcode == PUTFIELD && TARGET_OWNER.equals(owner) && NEW_FIELD_NAME.equals(name) && NEW_FIELD_DESC.equals(descriptor)) {
                            super.visitVarInsn(ALOAD, 0);
                            super.visitFieldInsn(GETFIELD, TARGET_OWNER, NEW_FIELD_NAME, NEW_FIELD_DESC);
                            super.visitMethodInsn(INVOKESTATIC, SUFFIX_OWNER, SUFFIX_METHOD, SUFFIX_DESC, false);

                            super.visitVarInsn(ALOAD, 0);
                            super.visitFieldInsn(GETFIELD, TARGET_OWNER, NEW_FIELD_NAME, NEW_FIELD_DESC);
                            super.visitMethodInsn(INVOKESTATIC, DEFAULT_NAME_OWNER, DEFAULT_NAME_METHOD, DEFAULT_NAME_DESC, false);
                            transformed = true;
                        }
                    }
                };
            }
            return methodVisitor;
        }
    }
}
