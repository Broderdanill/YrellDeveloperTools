package se.yrell.developertools.internal.weaving;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Weaves Developer Studio's FilteringSection so the object-list search field can be enhanced. */
final class ObjectListSearchTransformer implements Opcodes {
    private static final String CREATE_CLIENT = "createClient";
    private static final String CREATE_CLIENT_DESC = "(Lorg/eclipse/ui/forms/widgets/FormToolkit;)V";
    private static final String HANDLE_COLUMN_CHANGE = "handleColumnChange";
    private static final String HANDLE_COLUMN_CHANGE_DESC = "()V";

    private static final String UPDATABLE_TEXT_OWNER = "com/bmc/arsys/studio/commonui/common/widgets/UpdatableText";
    private static final String TEXT_OWNER = "org/eclipse/swt/widgets/Text";
    private static final String RUNTIME_OWNER = "se/yrell/developertools/runtime/ObjectListSearchRuntime";

    private ObjectListSearchTransformer() {
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
            if (CREATE_CLIENT.equals(name) && CREATE_CLIENT_DESC.equals(descriptor)) {
                return new CreateClientVisitor(methodVisitor, this);
            }
            if (HANDLE_COLUMN_CHANGE.equals(name) && HANDLE_COLUMN_CHANGE_DESC.equals(descriptor)) {
                return new HandleColumnChangeVisitor(methodVisitor, this);
            }
            return methodVisitor;
        }
    }

    private static final class CreateClientVisitor extends MethodVisitor {
        private final TransformingClassVisitor owner;
        private boolean awaitingUpdatableTextInit;

        CreateClientVisitor(MethodVisitor methodVisitor, TransformingClassVisitor owner) {
            super(ASM9, methodVisitor);
            this.owner = owner;
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            super.visitIntInsn(opcode, operand);
            if ((opcode == SIPUSH || opcode == BIPUSH) && operand == 2048) {
                // The next UpdatableText(parent, style) call receives the possibly enhanced style.
                super.visitMethodInsn(INVOKESTATIC, RUNTIME_OWNER, "enhanceSearchTextStyle", "(I)I", false);
                awaitingUpdatableTextInit = true;
                owner.transformed = true;
            }
        }

        @Override
        public void visitMethodInsn(int opcode, String ownerName, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(opcode, ownerName, name, descriptor, isInterface);
            if (awaitingUpdatableTextInit
                    && opcode == INVOKESPECIAL
                    && UPDATABLE_TEXT_OWNER.equals(ownerName)
                    && "<init>".equals(name)
                    && "(Lorg/eclipse/swt/widgets/Composite;I)V".equals(descriptor)) {
                awaitingUpdatableTextInit = false;
            }
        }

        @Override
        public void visitInsn(int opcode) {
            if (opcode == RETURN) {
                super.visitVarInsn(ALOAD, 0);
                super.visitMethodInsn(INVOKESTATIC, RUNTIME_OWNER, "installSearchClearButton", "(Ljava/lang/Object;)V", false);
                owner.transformed = true;
            }
            super.visitInsn(opcode);
        }
    }

    private static final class HandleColumnChangeVisitor extends MethodVisitor {
        private final TransformingClassVisitor owner;
        private boolean replacedClear;

        HandleColumnChangeVisitor(MethodVisitor methodVisitor, TransformingClassVisitor owner) {
            super(ASM9, methodVisitor);
            this.owner = owner;
        }

        @Override
        public void visitMethodInsn(int opcode, String ownerName, String name, String descriptor, boolean isInterface) {
            if (!replacedClear
                    && opcode == INVOKEVIRTUAL
                    && TEXT_OWNER.equals(ownerName)
                    && "setText".equals(name)
                    && "(Ljava/lang/String;)V".equals(descriptor)) {
                super.visitMethodInsn(INVOKESTATIC, RUNTIME_OWNER, "setSearchTextDuringColumnChange",
                        "(Lorg/eclipse/swt/widgets/Text;Ljava/lang/String;)V", false);
                replacedClear = true;
                owner.transformed = true;
                return;
            }
            super.visitMethodInsn(opcode, ownerName, name, descriptor, isInterface);
        }
    }
}
