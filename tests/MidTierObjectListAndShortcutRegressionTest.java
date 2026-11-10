import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.eclipse.swt.SWT;
import se.yrell.developertools.midtier.MidTierQuickFlushShortcut;

/** Static BMC extension contract + pure hotkey dispatch checks (no SWT native UI required). */
public class MidTierObjectListAndShortcutRegressionTest {
    private static int checks;
    private static void check(boolean ok, String detail) {
        if (!ok) throw new AssertionError(detail);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Path xmlFile = Path.of(args[0]);
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var doc = factory.newDocumentBuilder().parse(xmlFile.toFile());
        var extensions = doc.getElementsByTagName("extension");
        boolean providerFound = false;
        for (int i=0;i<extensions.getLength();i++) {
            var ext = (org.w3c.dom.Element) extensions.item(i);
            if (!"com.bmc.arsys.studio.commonui.genericaction".equals(ext.getAttribute("point"))) continue;
            var providers = ext.getElementsByTagName("genericActionProvider");
            for (int j=0;j<providers.getLength();j++) {
                var p = (org.w3c.dom.Element) providers.item(j);
                if ("se.yrell.developertools.midtier.MidTierObjectListActionProvider".equals(p.getAttribute("class")))
                    providerFound=true;
            }
        }
        check(providerFound,"native BMC Object List generic action provider declared");
        check(Class.forName("se.yrell.developertools.midtier.MidTierObjectListActionProvider") != null, "native provider in plugin JAR");
        String plugin = Files.readString(xmlFile);
        check(!plugin.contains("org.eclipse.ui.bindings"),"no collision-prone default shortcut");
        check(plugin.contains("icons/toilet_16.png"),"shared toilet icon registered");
        check(MidTierQuickFlushShortcut.normalize(" ctrl+alt+f8 ").equals("CTRL+ALT+F8"), "shortcut normalization");
        check(MidTierQuickFlushShortcut.validate("") == null,"disabled by default");
        check(MidTierQuickFlushShortcut.validate("CTRL+ALT+F8") == null,"valid function key");
        check(MidTierQuickFlushShortcut.validate("CTRL+ALT+SHIFT+F12") == null,"valid modifier combination");
        check(MidTierQuickFlushShortcut.validate("CTRL+F8") != null,"rejects basic modifier");
        check(MidTierQuickFlushShortcut.validate("ALT+F4") != null,"rejects window close shortcut");
        check(MidTierQuickFlushShortcut.validate("CTRL+ALT+F13") != null,"rejects unknown function key");
        check(MidTierQuickFlushShortcut.matches("CTRL+ALT+F8", SWT.CTRL|SWT.ALT, SWT.F8),"matches F8");
        check(!MidTierQuickFlushShortcut.matches("CTRL+ALT+F8", SWT.CTRL|SWT.ALT|SWT.SHIFT, SWT.F8),"rejects extra shift");
        check(MidTierQuickFlushShortcut.matches("CTRL+ALT+SHIFT+A", SWT.CTRL|SWT.ALT|SWT.SHIFT, 'a'),"case insensitive letter");
        check(!MidTierQuickFlushShortcut.matches("CTRL+ALT+SHIFT+A", SWT.CTRL|SWT.ALT, 'a'),"requires shift");
        System.out.println("Object List + hotkey tests: " + checks + " PASS");
    }
}
