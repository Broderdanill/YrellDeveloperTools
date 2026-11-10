package se.yrell.developertools.internal.weaving;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Presentation-only suppression in BMC 26.1.1.
 *
 * Info.showProblems filters the dialog's contents, but FormEditor and
 * DocumentPage decide to OPEN their save-warning dialogs from unfiltered
 * marker/status lists. Hence a completely empty modal dialog could remain.
 * This adds an additional guard to only the "Save successful with warnings."
 * call sites, leaving all save logic, failed-save dialogs and validation intact.
 */
public final class MessageSuppressionTransformer implements Opcodes {
    public static final String INFO_CLASS = "com/bmc/arsys/studio/commonui/common/info/Info";
    public static final String FORM_EDITOR_CLASS = "com/bmc/arsys/studio/ui/editors/form/FormEditor";
    public static final String DOCUMENT_PAGE_CLASS =
            "com/bmc/arsys/studio/documenteditor/common/documenteditor/DocumentPage";
    private static final String BRIDGE = "se/yrell/developertools/runtime/MessageSuppressionBridge";
    private static final String CONTEXT = "com/bmc/arsys/studio/commonui/common/info/IInfoContext";
    private static final String RESOURCE = "com/bmc/arsys/studio/commonui/common/info/IInfoResource";
    private static final String SAVE_WARNING_CAPTION = "Save successful with warnings.";
    private static final String SHOW_PROBLEMS_DESC = "(L"+CONTEXT+";Ljava/lang/String;)V";

    private MessageSuppressionTransformer() { }

    public static boolean supports(String name) {
        return INFO_CLASS.equals(name) || FORM_EDITOR_CLASS.equals(name) || DOCUMENT_PAGE_CLASS.equals(name);
    }

    /** Used by both Equinox weaving and -javaagent, before the BMC class is defined. */
    public static byte[] transform(String className, byte[] input) {
        if (!supports(className) || input == null || input.length == 0) return null;
        ClassReader reader = new ClassReader(input);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override protected String getCommonSuperClass(String left, String right) {
                return "java/lang/Object"; // Never load BMC classes during weaving.
            }
        };
        if (INFO_CLASS.equals(className)) {
            InfoVisitor visitor = new InfoVisitor(writer);
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            return visitor.warningsPatched && visitor.problemsPatched ? writer.toByteArray() : null;
        }
        SaveDialogVisitor visitor = new SaveDialogVisitor(writer, className);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        int expected = 1; // Only successful form-save path; leave failure paths unchanged.
        return visitor.patched == expected ? writer.toByteArray() : null;
    }

    private static final class SaveDialogVisitor extends ClassVisitor {
        private final String target;
        int patched;
        SaveDialogVisitor(ClassVisitor next, String target) {
            super(ASM9, next);
            this.target = target;
        }
        @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                   String signature, String[] exceptions) {
            MethodVisitor original = super.visitMethod(access, name, descriptor, signature, exceptions);
            boolean inSaveMethod = (FORM_EDITOR_CLASS.equals(target) && "saveForm".equals(name))
                    || (DOCUMENT_PAGE_CLASS.equals(target) && "internalSave".equals(name));
            if (!inSaveMethod) return original;
            return new MethodVisitor(ASM9, original) {
                private boolean saveWarningsCaption;
                private int successfulCaptionCount;

                @Override public void visitLdcInsn(Object constant) {
                    if (constant instanceof String) {
                        saveWarningsCaption = SAVE_WARNING_CAPTION.equals(constant);
                        if (saveWarningsCaption) successfulCaptionCount++;
                    }
                    super.visitLdcInsn(constant);
                }

                @Override public void visitMethodInsn(int opcode, String owner, String method,
                                                      String desc, boolean isInterface) {
                    if (opcode == INVOKESTATIC && INFO_CLASS.equals(owner)
                            && "showProblems".equals(method) && SHOW_PROBLEMS_DESC.equals(desc)
                            && saveWarningsCaption
                            && (!FORM_EDITOR_CLASS.equals(target) || successfulCaptionCount == 1)) {
                        // Stack on entry: [IInfoContext, String caption].
                        // Skip just this UI call if the ORIGINAL save warnings are
                        // all suppressed; never change success/failure handling.
                        if (FORM_EDITOR_CLASS.equals(target)) {
                            super.visitVarInsn(ALOAD, 0);
                            super.visitFieldInsn(GETFIELD, FORM_EDITOR_CLASS, "infoResource", "L"+RESOURCE+";");
                            super.visitMethodInsn(INVOKESTATIC, BRIDGE, "shouldShowFormSaveWarnings",
                                    "(Ljava/lang/Object;)Z", false);
                        } else {
                            // BMC DocumentPage.internalSave stores StatusInfo in local #10.
                            // Local #9 marks a save error: never suppress those dialogs.
                            super.visitVarInsn(ALOAD, 10);
                            super.visitVarInsn(ILOAD, 9);
                            super.visitMethodInsn(INVOKESTATIC, BRIDGE, "shouldShowDocumentSaveWarnings",
                                    "(Ljava/lang/Object;Z)Z", false);
                        }
                        Label showDialog = new Label();
                        Label afterDialog = new Label();
                        super.visitJumpInsn(IFNE, showDialog);
                        super.visitInsn(POP2); // Discard context and caption when suppressed.
                        super.visitJumpInsn(GOTO, afterDialog);
                        super.visitLabel(showDialog);
                        super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                        super.visitLabel(afterDialog);
                        patched++;
                        saveWarningsCaption = false;
                        return;
                    }
                    super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                }
            };
        }
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
            if ("showProblems".equals(name) && SHOW_PROBLEMS_DESC.equals(descriptor)
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
