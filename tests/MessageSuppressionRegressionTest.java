import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.net.URI;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.bmc.arsys.studio.commonui.common.info.*;
import se.yrell.developertools.runtime.MessageSuppressionBridge;
import se.yrell.developertools.internal.weaving.MessageSuppressionTransformer;

public class MessageSuppressionRegressionTest {
    private static int count;
    private static void ok(boolean value, String label) {
        count++;
        if (!value) throw new AssertionError(label);
    }

    public static final class Marker implements IInfoMarker {
        private final int code;
        private final Type severity;
        Marker(int code, Type severity) { this.code = code; this.severity = severity; }
        public IInfoResource getResource() { return null; }
        public int getCode() { return code; }
        public void setMessage(String text) { }
        public String getMessage() { return "Code " + code; }
        public Type getType() { return severity; }
        public IInfoLocator getLocator() { return null; }
    }

    public static final class SaveResource {
        private final List<IInfoMarker> markers;
        SaveResource(IInfoMarker... values) { markers = Arrays.asList(values); }
        public List<IInfoMarker> getMarkers() { return markers; }
    }

    public static final class FakeStatus {
        private final long number;
        private final int type;
        FakeStatus(long number, int type) { this.number = number; this.type = type; }
        public long getMessageNum() { return number; }
        public int getMessageType() { return type; }
    }

    public static class Context implements IInfoContext {
        List<IInfoMarker> list;
        Context(IInfoMarker... values) { list = new ArrayList<IInfoMarker>(Arrays.asList(values)); }
        public String getContextDescription() { return "test"; }
        public void setContextDescription(String text) { }
        public void addResourceReference(URI uri) { }
        public Set<URI> getResourceReferences() { return java.util.Collections.emptySet(); }
        public void clearResourceReferences() { }
        public void addMechanism(IInfoMechanism mechanism,IInfoPolicy policy) { }
        public void removeMechanism(IInfoMechanism mechanism) { }
        public List<IInfoMechanism> getMechanisms() { return java.util.Collections.emptyList(); }
        public IInfoPolicy getPolicyForMechanism(IInfoMechanism mechanism) { return null; }
        public List<IInfoMarker> getMarkers() { return list; }
        public void dispose() { }
    }

    public static void main(String[] args) throws Exception {
        String old = System.getProperty("user.home");
        File home = Files.createTempDirectory("yrell-suppress-test").toFile();
        try {
            System.setProperty("user.home", home.getAbsolutePath());
            File dir = new File(home, ".yrell-developertools");
            ok(dir.mkdirs(), "test config dir");
            File config = new File(dir, "message-suppression.properties");

            ok(MessageSuppressionBridge.isValidCodeList("5033,5384"), "valid default codes");
            ok(MessageSuppressionBridge.isValidCodeList(" 5033 ; 5384 "), "separator handling");
            ok(!MessageSuppressionBridge.isValidCodeList("5033,bad"), "reject invalid code");
            ok(!MessageSuppressionBridge.isValidCodeList("0"), "reject zero");
            ok(!MessageSuppressionBridge.isValidCodeList(""), "reject empty");
            ok(!MessageSuppressionBridge.shouldSuppress(2,5033), "not active by default");
            // Empty dialogs must be preserved when the suppressor is disabled.
            Context emptyContext = new Context();
            Context nullMarkerContext = new Context() {
                @Override public List<IInfoMarker> getMarkers() { return null; }
            };
            ok(MessageSuppressionBridge.filterProblemsContext(emptyContext)==emptyContext,
                    "disabled: empty context retains BMC behavior");
            ok(MessageSuppressionBridge.filterProblemsContext(nullMarkerContext)==nullMarkerContext,
                    "disabled: null markers retain BMC behavior");

            write(config,false,"5033,5384");
            ok(!MessageSuppressionBridge.shouldSuppress(2,5033), "disabled must not suppress");
            write(config,true,"5033,5384");
            ok(MessageSuppressionBridge.shouldSuppress(2,5033), "5033 WARNING");
            ok(MessageSuppressionBridge.shouldSuppress(2,5384), "5384 WARNING, if reported as such");
            ok(!MessageSuppressionBridge.shouldSuppress(4,5384), "5384 ERROR is never suppressed");
            ok(!MessageSuppressionBridge.shouldSuppress(4,5033), "5033 ERROR is never suppressed");
            ok(!MessageSuppressionBridge.shouldSuppress(1,5033), "INFO not suppressed");
            ok(!MessageSuppressionBridge.shouldSuppress(2,9999), "other warning shown");
            ok(MessageSuppressionBridge.filterProblemsContext(emptyContext)==null,
                    "enabled: zero markers must NOT open an empty InfoDialog");
            ok(MessageSuppressionBridge.filterProblemsContext(nullMarkerContext)==null,
                    "enabled: BMC null markers must NOT open an empty InfoDialog");
            // Real BMC 26.1.1 InfoContext uses null, not an empty list,
            // when no resource markers were collected.
            com.bmc.arsys.studio.commonui.common.info.InfoContext bmcEmpty =
                    new com.bmc.arsys.studio.commonui.common.info.InfoContext("empty context");
            ok(bmcEmpty.getMarkers()==null, "BMC InfoContext returns null with no markers");
            ok(MessageSuppressionBridge.filterProblemsContext(bmcEmpty)==null,
                    "real BMC empty context must NOT open a modal dialog");
            write(config,true,"");
            ok(MessageSuppressionBridge.filterProblemsContext(nullMarkerContext)==nullMarkerContext,
                    "empty suppress list: preserve BMC dialog");
            write(config,true,"5033,5384");

            // The real BMC FormEditor tests infoResource.getMarkers BEFORE
            // calling Info.showProblems, not the filtered infoContext.
            SaveResource onlySuppressedSave = new SaveResource(
                    new Marker(5033,IInfoMarker.Type.WARNING),
                    new Marker(5384,IInfoMarker.Type.WARNING));
            ok(!MessageSuppressionBridge.shouldShowFormSaveWarnings(onlySuppressedSave),
                    "form save: all selected warnings should skip original modal");
            ok(MessageSuppressionBridge.shouldShowFormSaveWarnings(new SaveResource(
                    new Marker(5033,IInfoMarker.Type.WARNING),
                    new Marker(5384,IInfoMarker.Type.ERROR))),
                    "form save: same-code ERROR retains original modal");
            ok(MessageSuppressionBridge.shouldShowFormSaveWarnings(new SaveResource(
                    new Marker(5033,IInfoMarker.Type.WARNING),
                    new Marker(7777,IInfoMarker.Type.WARNING))),
                    "form save: unsuppressed WARNING retains modal");
            ok(MessageSuppressionBridge.shouldShowFormSaveWarnings(new SaveResource()),
                    "form save: no original markers fails open");
            ok(MessageSuppressionBridge.shouldShowFormSaveWarnings(null),
                    "form save: unavailable resource fails open");
            // The TWO save-warning locations in BMC 26.1.1 require distinct guards.
            Context noMessages = new Context();
            ok(!MessageSuppressionBridge.shouldShowFormSaveWarnings(onlySuppressedSave, noMessages),
                    "normal form save skips when suppressed source and empty visible context");
            ok(!MessageSuppressionBridge.shouldShowFormSaveWarnings(new SaveResource(), noMessages),
                    "normal form save skips a genuine empty popup");
            ok(MessageSuppressionBridge.shouldShowFormSaveWarnings(new SaveResource(
                    new Marker(5033,IInfoMarker.Type.WARNING),
                    new Marker(5384,IInfoMarker.Type.ERROR)), noMessages),
                    "normal form save must retain errors even if dialog would be empty");
            ok(MessageSuppressionBridge.shouldShowFormSaveWarnings(onlySuppressedSave,
                    new Context(new Marker(7777,IInfoMarker.Type.WARNING))),
                    "normal form save must retain a visible nonconfigured warning");
            ok(MessageSuppressionBridge.shouldShowFormSaveWarnings(null,noMessages),
                    "normal form save with unknown marker source fails open");
            List<FakeStatus> onlyStatusWarnings = Arrays.asList(new FakeStatus(5033,1),new FakeStatus(5384,1));
            ok(!MessageSuppressionBridge.shouldShowDocumentSaveWarnings(onlyStatusWarnings,false),
                    "workflow save: all selected AR WARNING statuses suppress dialog");
            ok(MessageSuppressionBridge.shouldShowDocumentSaveWarnings(onlyStatusWarnings,true),
                    "workflow save: failed save always keeps modal");
            ok(MessageSuppressionBridge.shouldShowDocumentSaveWarnings(Arrays.asList(new FakeStatus(5384,2)),false),
                    "workflow save: ERROR always visible");
            ok(MessageSuppressionBridge.shouldShowDocumentSaveWarnings(Arrays.asList(new FakeStatus(9999,1)),false),
                    "workflow save: other warning visible");
            ok(MessageSuppressionBridge.shouldShowDocumentSaveWarnings(null,false),
                    "workflow save: unknown statuses retain dialog");
            ok(MessageSuppressionBridge.shouldShowFormExceptionWarnings(onlyStatusWarnings,noMessages),
                    "second FormEditor save-warning path is a FAILED save and must keep popup");
            ok(MessageSuppressionBridge.shouldShowFormExceptionWarnings(
                    Arrays.asList(new FakeStatus(5384,2)),noMessages),
                    "second FormEditor save-warning path: ERROR status always opens popup");
            ok(MessageSuppressionBridge.shouldShowFormExceptionWarnings(
                    Arrays.asList(new FakeStatus(7777,1)),noMessages),
                    "second FormEditor save-warning path: nonconfigured warning opens popup");
            ok(MessageSuppressionBridge.shouldShowFormExceptionWarnings(onlyStatusWarnings,
                    new Context(new Marker(7777,IInfoMarker.Type.WARNING))),
                    "second FormEditor save-warning path: remaining context warning opens popup");
            ok(MessageSuppressionBridge.shouldShowFormExceptionWarnings(null,noMessages),
                    "second FormEditor save-warning path: unknown status list fails open");

            Context only = new Context(new Marker(5033,IInfoMarker.Type.WARNING),
                                       new Marker(5384,IInfoMarker.Type.WARNING));
            ok(MessageSuppressionBridge.filterProblemsContext(only)==null, "suppress all-only-warning dialog");
            ok(only.getMarkers().size()==2, "no source marker removed");

            Context mixed = new Context(new Marker(5033,IInfoMarker.Type.WARNING),
                                        new Marker(5384,IInfoMarker.Type.ERROR),
                                        new Marker(7777,IInfoMarker.Type.WARNING));
            Object filtered = MessageSuppressionBridge.filterProblemsContext(mixed);
            ok(filtered instanceof IInfoContext, "mixed context remains available");
            List<IInfoMarker> shown = ((IInfoContext)filtered).getMarkers();
            ok(shown.size()==2, "only warning marker hidden");
            ok(shown.get(0).getType()==IInfoMarker.Type.ERROR && shown.get(0).getCode()==5384,
                    "same-code error preserved");
            ok(shown.get(1).getCode()==7777, "other warning preserved");
            ok(mixed.getMarkers().size()==3, "original model intact");
            ok(MessageSuppressionBridge.filterProblemsContext(new Context(new Marker(5000,IInfoMarker.Type.ERROR)))
                    instanceof Context, "unaffected error context returned verbatim");
            ok(MessageSuppressionBridge.filterProblemsContext(null)==null, "null safe");

            InputStream in = MessageSuppressionRegressionTest.class.getClassLoader().getResourceAsStream(
                    "com/bmc/arsys/studio/commonui/common/info/Info.class");
            ok(in!=null, "real BMC 26.1.1 Info class available");
            byte[] original = in.readAllBytes();
            byte[] modified = MessageSuppressionTransformer.transform(
                    "com/bmc/arsys/studio/commonui/common/info/Info",original);
            ok(modified!=null && modified.length>original.length, "real BMC Info transformed");
            final int[] calls = new int[2];
            new ClassReader(modified).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String sig,String[] exceptions) {
                    MethodVisitor base=super.visitMethod(access,name,descriptor,sig,exceptions);
                    final int index = "open".equals(name)&&"(IILjava/lang/String;)V".equals(descriptor) ? 0
                                    : "showProblems".equals(name)&&descriptor.contains("IInfoContext;") ? 1 : -1;
                    if(index<0) return base;
                    return new MethodVisitor(Opcodes.ASM9,base) {
                        @Override public void visitMethodInsn(int opcode,String owner,String called,String desc,boolean itf) {
                            if (owner.equals("se/yrell/developertools/runtime/MessageSuppressionBridge")) calls[index]++;
                            super.visitMethodInsn(opcode,owner,called,desc,itf);
                        }
                    };
                }
            },0);
            ok(calls[0]==1 && calls[1]==1, "two correctly scoped Info presentation hooks");
            ok(MessageSuppressionTransformer.transform("other/Class",original)==null, "not a broad BMC patch");

            // Bytecode-level regression: instrument the verified 26.1.1
            // save-success dialog invocations, NEVER save-failed handling.
            String[] targets = {
                MessageSuppressionTransformer.FORM_EDITOR_CLASS,
                MessageSuppressionTransformer.DOCUMENT_PAGE_CLASS
            };
            for (String target : targets) {
                InputStream targetIn = MessageSuppressionRegressionTest.class.getClassLoader()
                        .getResourceAsStream(target + ".class");
                ok(targetIn != null, "BMC 26.1.1 save class available: " + target);
                byte[] originalTarget = targetIn.readAllBytes();
                byte[] transformedTarget = MessageSuppressionTransformer.transform(target, originalTarget);
                ok(transformedTarget != null, "BMC save-warning callsite patched: " + target);
                final int[] guards = new int[1];
                final int[] showCalls = new int[1];
                final int[] unsuppressedFailed = new int[1];
                final int[] catchGuards = new int[1];
                new ClassReader(transformedTarget).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access,String name,String descriptor,
                                                                  String signature,String[] exceptions) {
                        final boolean saveMethod = "saveForm".equals(name) || "internalSave".equals(name);
                        return new MethodVisitor(Opcodes.ASM9,super.visitMethod(access,name,descriptor,signature,exceptions)) {
                            public void visitMethodInsn(int opcode,String owner,String method,String desc,boolean itf) {
                                if (saveMethod && owner.equals("se/yrell/developertools/runtime/MessageSuppressionBridge")
                                        && method.startsWith("shouldShow")) guards[0]++;
                                if (saveMethod && owner.equals("se/yrell/developertools/runtime/MessageSuppressionBridge")
                                        && method.equals("shouldShowFormExceptionWarnings")) catchGuards[0]++;
                                if (saveMethod && owner.equals(MessageSuppressionTransformer.INFO_CLASS)
                                        && method.equals("showProblems")) showCalls[0]++;
                                super.visitMethodInsn(opcode,owner,method,desc,itf);
                            }
                            public void visitLdcInsn(Object value) {
                                if (saveMethod && "Save failed for form {0}.".equals(value)) unsuppressedFailed[0]++;
                                super.visitLdcInsn(value);
                            }
                        };
                    }
                },0);
                ok(guards[0] == (target.equals(MessageSuppressionTransformer.FORM_EDITOR_CLASS) ? 2 : 1),
                        "both FormEditor warning calls and the DocumentPage warning call guarded: " + target);
                ok(showCalls[0] == (target.equals(MessageSuppressionTransformer.FORM_EDITOR_CLASS) ? 3 : 2),
                        "original BMC dialog calls retained: " + target);
                if (target.equals(MessageSuppressionTransformer.FORM_EDITOR_CLASS)) {
                    ok(unsuppressedFailed[0] == 1, "form save-failed branch preserved");
                    ok(catchGuards[0] == 1, "the previously missed warning-only ModelException call is guarded");
                }
            }
            ok(!MessageSuppressionTransformer.supports("org/eclipse/other/Class"),
                    "no broad instrumentation of arbitrary classes");

            if (Boolean.getBoolean("yrell.verifyAgentInfoHook")) {
                Class.forName("com.bmc.arsys.studio.commonui.common.info.Info", false,
                        MessageSuppressionRegressionTest.class.getClassLoader());
                ok(Boolean.getBoolean("se.yrell.developertools.messageSuppressionAgent.active"),
                        "javaagent transformed BMC Info before class load");
            }
            System.out.println("MessageSuppressionRegressionTest PASS " + count);
        } finally {
            if(old!=null) System.setProperty("user.home",old);
        }
    }

    private static void write(File target,boolean enabled,String codes) throws Exception {
        Properties p=new Properties();
        p.setProperty("enabled",Boolean.toString(enabled));
        p.setProperty("codes",codes);
        try(FileOutputStream out=new FileOutputStream(target)){p.store(out,"test");}
    }
}
