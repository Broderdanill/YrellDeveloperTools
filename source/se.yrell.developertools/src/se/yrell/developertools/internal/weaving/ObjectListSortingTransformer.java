package se.yrell.developertools.internal.weaving;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Hooks BMC's shared object-list component using its existing JFace sorter. */
final class ObjectListSortingTransformer implements Opcodes {
    static final String COMPONENT = "com/bmc/arsys/studio/commonui/common/objectlist/ObjectListComponent";
    static final String CONTENT_PROVIDER = COMPONENT + "$ViewContentProvider";
    private static final String RUNTIME = "se/yrell/developertools/runtime/ObjectListSortingRuntime";

    private ObjectListSortingTransformer() { }

    static byte[] transform(String className, byte[] input) {
        if (!COMPONENT.equals(className) && !CONTENT_PROVIDER.equals(className)) return null;
        ClassReader reader = new ClassReader(input);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        Visitor visitor = new Visitor(writer, className);
        reader.accept(visitor, 0);
        return visitor.modified ? writer.toByteArray() : null;
    }

    private static final class Visitor extends ClassVisitor {
        private final String className;
        boolean modified;
        Visitor(ClassVisitor output, String className) { super(ASM9, output); this.className = className; }
        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                    String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            if (COMPONENT.equals(className) && "getColumnInformation".equals(name)
                    && "(Ljava/util/List;)Ljava/util/List;".equals(descriptor)) {
                modified = true;
                return new MethodVisitor(ASM9, mv) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == ARETURN) {
                            // Stack [list]. Pass the owning BMC component along with the result.
                            super.visitVarInsn(ALOAD, 0);
                            super.visitInsn(SWAP);
                            super.visitMethodInsn(INVOKESTATIC, RUNTIME, "prepareColumns",
                                    "(Ljava/lang/Object;Ljava/util/List;)Ljava/util/List;", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
            if (COMPONENT.equals(className) && "setItems".equals(name)
                    && "(Lcom/bmc/arsys/studio/model/item/ItemList;)V".equals(descriptor)) {
                modified = true;
                return new MethodVisitor(ASM9, mv) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == RETURN) {
                            super.visitVarInsn(ALOAD, 0);
                            super.visitMethodInsn(INVOKESTATIC, RUNTIME, "afterInputChanged",
                                    "(Ljava/lang/Object;)V", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
            if (CONTENT_PROVIDER.equals(className) && "inputChanged".equals(name)
                    && "(Lorg/eclipse/jface/viewers/Viewer;Ljava/lang/Object;Ljava/lang/Object;)V".equals(descriptor)) {
                modified = true;
                return new MethodVisitor(ASM9, mv) {
                    @Override public void visitInsn(int opcode) {
                        if (opcode == RETURN) {
                            super.visitVarInsn(ALOAD, 0);
                            super.visitFieldInsn(GETFIELD, CONTENT_PROVIDER, "this$0", "L" + COMPONENT + ";");
                            super.visitMethodInsn(INVOKESTATIC, RUNTIME, "afterInputChanged",
                                    "(Ljava/lang/Object;)V", false);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
            return mv;
        }
    }
}
