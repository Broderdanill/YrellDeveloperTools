package se.yrell.developertools.internal.weaving;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class FormHelperNameTransformer implements Opcodes {
    private static final String TARGET_METHOD = "generateUniqueName";
    private static final String TARGET_DESC = "(Ljava/lang/String;Lcom/bmc/arsys/studio/model/store/IFormObject;)Ljava/lang/String;";
    private static final String RUNTIME_OWNER = "se/yrell/developertools/runtime/CustomSuffixRuntime";
    private static final String RUNTIME_METHOD = "cleanNewFieldName";
    private static final String RUNTIME_DESC = "(Lcom/bmc/arsys/studio/model/store/IFormObject;Ljava/lang/String;)Ljava/lang/String;";

    private FormHelperNameTransformer() {
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

        TransformingClassVisitor(ClassVisitor classVisitor) {
            super(ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (!TARGET_METHOD.equals(name) || !TARGET_DESC.equals(descriptor)) {
                return mv;
            }
            return new MethodVisitor(ASM9, mv) {
                @Override
                public void visitInsn(int opcode) {
                    if (opcode == ARETURN) {
                        // Stack: generatedName. Add form arg and call cleanNewFieldName(form, generatedName).
                        super.visitVarInsn(ALOAD, 2);
                        super.visitInsn(SWAP);
                        super.visitMethodInsn(INVOKESTATIC, RUNTIME_OWNER, RUNTIME_METHOD, RUNTIME_DESC, false);
                        transformed = true;
                    }
                    super.visitInsn(opcode);
                }
            };
        }
    }
}
