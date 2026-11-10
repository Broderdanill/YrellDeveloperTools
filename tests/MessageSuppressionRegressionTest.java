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

    public static final class Context implements IInfoContext {
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

            write(config,false,"5033,5384");
            ok(!MessageSuppressionBridge.shouldSuppress(2,5033), "disabled must not suppress");
            write(config,true,"5033,5384");
            ok(MessageSuppressionBridge.shouldSuppress(2,5033), "5033 WARNING");
            ok(MessageSuppressionBridge.shouldSuppress(2,5384), "5384 WARNING, if reported as such");
            ok(!MessageSuppressionBridge.shouldSuppress(4,5384), "5384 ERROR is never suppressed");
            ok(!MessageSuppressionBridge.shouldSuppress(4,5033), "5033 ERROR is never suppressed");
            ok(!MessageSuppressionBridge.shouldSuppress(1,5033), "INFO not suppressed");
            ok(!MessageSuppressionBridge.shouldSuppress(2,9999), "other warning shown");

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
