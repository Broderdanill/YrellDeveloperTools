import java.io.*;
import java.lang.reflect.*;
import java.lang.instrument.ClassFileTransformer;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
public class TransformAudit {
    private static final LinkedHashMap<String,String> map = new LinkedHashMap<>();
    static {
      map.put("UIFieldFactoryTransformer", "com/bmc/arsys/studio/ui/editors/form/model/UIFieldFactory");
      map.put("AddFieldToViewCommandTransformer", "com/bmc/arsys/studio/ui/editors/form/commands/AddFieldToViewCommand");
      map.put("UITableFieldTransformer", "com/bmc/arsys/studio/ui/editors/form/model/UITableField");
      map.put("FormHelperNameTransformer", "com/bmc/arsys/studio/model/internal/helper/FormHelper");
      map.put("UIFieldPropertyDescriptorTransformer", "com/bmc/arsys/studio/ui/editors/form/model/UIField");
      map.put("ItemPropertySourceTransformer", "com/bmc/arsys/studio/ui/common/properties/ItemPropertySource");
      map.put("ObjectListSearchTransformer", "com/bmc/arsys/studio/ui/views/objectlist/FilteringSection");
      map.put("WorkflowFieldMapLayoutTransformer", "com/bmc/arsys/studio/ui/common/fieldmap/FieldMapWidget");
    }
    private static final String[] fast = {
     "com/bmc/arsys/studio/model/internal/providers/ARDynamicNamedListProvider",
     "com/bmc/arsys/studio/model/internal/providers/ARBaseNamedListProvider",
     "com/bmc/arsys/studio/model/internal/providers/FormListProvider",
     "com/bmc/arsys/studio/ui/views/objectlist/ObjectListCheckboxFilterManager$PropertyTypeCheckBoxConfiguration",
     "com/bmc/arsys/studio/ui/views/objectlist/FilteringSection",
     "com/bmc/arsys/studio/ui/views/objectlist/FilteringSection$8",
     "com/bmc/arsys/studio/ui/views/objectlist/ObjectListView",
     "com/bmc/arsys/studio/ui/views/objectlist/ObjectListView$12",
     "com/bmc/arsys/studio/commonui/common/objectlist/ObjectListComponent",
     "com/bmc/arsys/studio/commonui/common/objectlist/OverlayObjectListFilter",
     "com/bmc/arsys/studio/ui/views/objectlist/PropertyFilter",
     "com/bmc/arsys/studio/model/internal/providers/SQLCommands"
    };
    private static byte[] source(String internal) throws Exception {try (InputStream i=ClassLoader.getSystemResourceAsStream(internal+".class")){if(i==null)throw new FileNotFoundException(internal);return i.readAllBytes();}}
    private static String info(byte[] b){ if(b==null)throw new AssertionError("Expected a transformation but got null"); new ClassReader(b).accept(new ClassVisitor(Opcodes.ASM9){},ClassReader.SKIP_CODE);return "MATCH ("+b.length+" bytes)"; }
    public static void main(String[] a) throws Exception {
     for(var e:map.entrySet()) {
       try {
        var c=Class.forName("se.yrell.developertools.internal.weaving."+e.getKey());
        var m=c.getDeclaredMethod("transform",byte[].class);m.setAccessible(true);
        var transformed=(byte[])m.invoke(null,(Object)source(e.getValue()));
        System.out.println("STATIC "+e.getKey()+" on "+e.getValue()+": "+info(transformed));
       }catch(Throwable t) {throw new AssertionError("STATIC " + e.getKey(), t);}
     }
     var c=Class.forName("se.yrell.developertools.internal.weaving.FastFormsTransformer");
     var m=c.getDeclaredMethod("transform",String.class,byte[].class);m.setAccessible(true);
     for(String cn: fast){try{byte[] v=(byte[])m.invoke(null,cn,source(cn));System.out.println("WEAVING-FAST "+cn+": "+info(v));}catch(Throwable t){throw new AssertionError("WEAVING-FAST " + cn, t);}}
     var ac=Class.forName("se.yrell.developertools.fastagent.FastFormsAgent$Transformer");
     var ctor=ac.getDeclaredConstructor();ctor.setAccessible(true);
     ClassFileTransformer at=(ClassFileTransformer)ctor.newInstance();
     for(String cn: fast){try{byte[] v=at.transform(null,null,cn,null,null,source(cn));System.out.println("AGENT-FAST "+cn+": "+info(v));}catch(Throwable t){throw new AssertionError("AGENT-FAST " + cn, t);}}
    }
}
