package se.yrell.developertools.internal.weaving;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class CustomSuffixTransformer implements Opcodes {
    private static final String HELPER_OWNER = "com/bmc/arsys/studio/model/internal/helper/Helper";
    private static final String CAN_APPEND = "canAppendCustomString";
    private static final String CAN_APPEND_DESC = "(Lcom/bmc/arsys/studio/model/store/IStore;)Z";

    private static final String RUNTIME_OWNER = "se/yrell/developertools/runtime/CustomSuffixRuntime";
    private static final String RUNTIME_METHOD = "allowAppendCustomString";
    private static final String RUNTIME_DESC = "(Z)Z";

    private CustomSuffixTransformer() {
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
            return new MethodVisitor(ASM9, methodVisitor) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor, boolean isInterface) {
                    super.visitMethodInsn(opcode, owner, methodName, methodDescriptor, isInterface);
                    if (opcode == INVOKESTATIC
                            && HELPER_OWNER.equals(owner)
                            && CAN_APPEND.equals(methodName)
                            && CAN_APPEND_DESC.equals(methodDescriptor)) {
                        super.visitMethodInsn(INVOKESTATIC, RUNTIME_OWNER, RUNTIME_METHOD, RUNTIME_DESC, false);
                        transformed = true;
                    }
                }
            };
        }
    }
}
