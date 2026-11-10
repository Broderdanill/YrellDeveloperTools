import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import com.bmc.arsys.studio.commonui.common.objectlist.ColumnInformation;
import se.yrell.developertools.ToolsConstants;
import se.yrell.developertools.runtime.ObjectListSortingRuntime;

public class ObjectListSortingRegressionTest {
    static int checks;
    static void assertTrue(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); checks++; }
    static byte[] getClassBytes(String internalName) throws Exception {
        try (InputStream in = ClassLoader.getSystemResourceAsStream(internalName + ".class")) {
            if (in == null) throw new IllegalStateException("Missing BMC class: " + internalName);
            return in.readAllBytes();
        }
    }
    static byte[] weave(String internal, byte[] src) throws Exception {
        Class<?> c=Class.forName("se.yrell.developertools.internal.weaving.ObjectListSortingTransformer");
        Method m=c.getDeclaredMethod("transform", String.class, byte[].class);
        m.setAccessible(true);
        return (byte[]) m.invoke(null, internal, src);
    }
    static int countCalls(byte[] bytecode, String name) {
        int[] result={0};
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String n, String d, String signature, String[] ex) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int opcode, String owner, String target, String desc, boolean itf) {
                        if (owner.equals("se/yrell/developertools/runtime/ObjectListSortingRuntime") && target.equals(name)) result[0]++;
                    }
                };
            }
        },0);
        return result[0];
    }
    public static void main(String[] args) throws Exception {
        String comp="com/bmc/arsys/studio/commonui/common/objectlist/ObjectListComponent";
        String content=comp+"$ViewContentProvider";
        byte[] componentSource=getClassBytes(comp);
        byte[] contentSource=getClassBytes(content);
        byte[] component=weave(comp,componentSource);
        byte[] provider=weave(content,contentSource);
        assertTrue(component!=null,"ObjectListComponent transformed");
        assertTrue(provider!=null,"ContentProvider transformed");
        assertTrue(countCalls(component,"prepareColumns")>=2,"All getColumnInformation return paths guarded");
        assertTrue(countCalls(component,"afterInputChanged")==1,"BMC setItems completion guarded");
        assertTrue(countCalls(provider,"afterInputChanged")==1,"BMC inputChanged completion guarded");
        assertTrue(weave("unrelated/Type",componentSource)==null,"Only expected classes woven");
        assertTrue("LASTUPDATETIME".equals(ToolsConstants.DEFAULT_OBJECT_SORT_COLUMN),"Default is Modified");
        assertTrue("DESC".equals(ToolsConstants.DEFAULT_OBJECT_SORT_DIRECTION),"Default is newest first");
        assertTrue(!ToolsConstants.DEFAULT_OBJECT_SORT_ENABLED,"New feature is opt-in");
        assertTrue(ObjectListSortingRuntime.prepareColumns(null,null)==null,"Null list fails open");
        List<Object> list = new ArrayList<Object>();
        list.add("not a BMC column");
        assertTrue(ObjectListSortingRuntime.prepareColumns(null,list)==list,"Unrecognized context left unchanged");
        // Compose with the current Fast Forms javaagent transformation. Two independent hooks
        // target setItems and must both be structurally valid after composition.
        Class<?> a=Class.forName("se.yrell.developertools.fastagent.FastFormsAgent$Transformer");
        var ctor=a.getDeclaredConstructor();ctor.setAccessible(true);
        java.lang.instrument.ClassFileTransformer transformer=(java.lang.instrument.ClassFileTransformer)ctor.newInstance();
        byte[] agent=transformer.transform(null,null,comp,null,null,componentSource);
        assertTrue(agent!=null,"Java agent still supports this component");
        byte[] compound=weave(comp,agent);
        assertTrue(compound!=null && countCalls(compound,"afterInputChanged")==1,
                "Sorting hook composes with Fast Forms java agent");
        System.out.println("ObjectListSortingRegressionTest: PASS " + checks);
    }
}
