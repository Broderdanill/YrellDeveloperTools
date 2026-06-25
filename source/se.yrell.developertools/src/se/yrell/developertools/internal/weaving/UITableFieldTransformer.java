package se.yrell.developertools.internal.weaving;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Hooks UITableField.addColumn(...) so table-column database-name defaults can
 * use the actual parent table UI field. This is more reliable than trying to
 * infer the table field from the created column alone.
 */
final class UITableFieldTransformer implements Opcodes {
    private static final String TARGET_METHOD = "addColumn";
    private static final String TARGET_DESC = "(Lcom/bmc/arsys/studio/ui/editors/form/model/UITableField$ColumnData;)Lcom/bmc/arsys/studio/ui/editors/form/model/UIColumnField;";

    private static final String DEFAULT_NAME_OWNER = "se/yrell/developertools/runtime/DefaultNameRuntime";
    private static final String DEFAULT_NAME_METHOD = "applyTableColumnDefaults";
    private static final String DEFAULT_NAME_DESC = "(Ljava/lang/Object;Ljava/lang/Object;)V";

    private UITableFieldTransformer() {
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
                    public void visitInsn(int opcode) {
                        if (opcode == ARETURN) {
                            // Stack before ARETURN: [UIColumnField]
                            // Leave return value on stack while calling runtime(this, returnedColumn).
                            super.visitInsn(DUP);
                            super.visitVarInsn(ALOAD, 0);
                            super.visitInsn(SWAP);
                            super.visitMethodInsn(INVOKESTATIC, DEFAULT_NAME_OWNER, DEFAULT_NAME_METHOD, DEFAULT_NAME_DESC, false);
                            transformed = true;
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
            return methodVisitor;
        }
    }
}
