package se.yrell.developertools.internal.weaving;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Weaves FieldMapWidget so value columns can stretch to use empty table space. */
final class WorkflowFieldMapLayoutTransformer implements Opcodes {
    private static final String CREATE_CLIENT_AREA = "createClientArea";
    private static final String CREATE_CLIENT_AREA_DESC = "(Lorg/eclipse/ui/forms/widgets/FormToolkit;)V";
    private static final String RUNTIME_OWNER = "se/yrell/developertools/runtime/WorkflowFieldMapLayoutRuntime";

    private WorkflowFieldMapLayoutTransformer() {
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
            if (CREATE_CLIENT_AREA.equals(name) && CREATE_CLIENT_AREA_DESC.equals(descriptor)) {
                return new MethodVisitor(ASM9, methodVisitor) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == RETURN) {
                            super.visitVarInsn(ALOAD, 0);
                            super.visitMethodInsn(INVOKESTATIC, RUNTIME_OWNER, "installFieldMapLayout", "(Ljava/lang/Object;)V", false);
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
