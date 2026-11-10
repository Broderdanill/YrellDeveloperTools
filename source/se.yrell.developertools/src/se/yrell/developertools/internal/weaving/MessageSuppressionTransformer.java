package se.yrell.developertools.internal.weaving;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Presentation-only instrumentation of BMC's central Info class.
 * BMC 26.1.1: Info.open(int severity,int code,String message) and
 * Info.showProblems(IInfoContext,String).
 */
public final class MessageSuppressionTransformer implements Opcodes {
    public static final String INFO_CLASS = "com/bmc/arsys/studio/commonui/common/info/Info";
    private static final String BRIDGE = "se/yrell/developertools/runtime/MessageSuppressionBridge";
    private static final String CONTEXT = "com/bmc/arsys/studio/commonui/common/info/IInfoContext";

    private MessageSuppressionTransformer() { }

    /** Used by both Equinox weaving and the -javaagent early class-load path. */
    public static byte[] transform(String className, byte[] input) {
        if (!INFO_CLASS.equals(className) || input == null || input.length == 0) return null;
        ClassReader reader = new ClassReader(input);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override protected String getCommonSuperClass(String left, String right) {
                return "java/lang/Object"; // No BMC class loading from the transformer.
            }
        };
        InfoVisitor visitor = new InfoVisitor(writer);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        return visitor.warningsPatched && visitor.problemsPatched ? writer.toByteArray() : null;
    }

    private static final class InfoVisitor extends ClassVisitor {
        boolean warningsPatched;
        boolean problemsPatched;
        InfoVisitor(ClassVisitor next) { super(ASM9, next); }

        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                   String signature, String[] exceptions) {
            MethodVisitor original = super.visitMethod(access, name, descriptor, signature, exceptions);
            if ("open".equals(name) && "(IILjava/lang/String;)V".equals(descriptor)
                    && (access & ACC_STATIC) != 0) {
                warningsPatched = true;
                return new MethodVisitor(ASM9, original) {
                    @Override public void visitCode() {
                        super.visitCode();
                        Label continueBmc = new Label();
                        super.visitVarInsn(ILOAD, 0);
                        super.visitVarInsn(ILOAD, 1);
                        super.visitMethodInsn(INVOKESTATIC, BRIDGE, "shouldSuppress", "(II)Z", false);
                        super.visitJumpInsn(IFEQ, continueBmc);
                        super.visitInsn(RETURN);
                        super.visitLabel(continueBmc);
                    }
                };
            }
            if ("showProblems".equals(name) && ("(L"+CONTEXT+";Ljava/lang/String;)V").equals(descriptor)
                    && (access & ACC_STATIC) != 0) {
                problemsPatched = true;
                return new MethodVisitor(ASM9, original) {
                    @Override public void visitCode() {
                        super.visitCode();
                        Label continueBmc = new Label();
                        super.visitVarInsn(ALOAD, 0);
                        super.visitMethodInsn(INVOKESTATIC, BRIDGE, "filterProblemsContext",
                                "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                        super.visitTypeInsn(CHECKCAST, CONTEXT);
                        super.visitVarInsn(ASTORE, 0);
                        super.visitVarInsn(ALOAD, 0);
                        super.visitJumpInsn(IFNONNULL, continueBmc);
                        super.visitInsn(RETURN);
                        super.visitLabel(continueBmc);
                    }
                };
            }
            return original;
        }
    }
}
