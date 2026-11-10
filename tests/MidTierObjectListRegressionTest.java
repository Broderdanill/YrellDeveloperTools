import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;

/** Contract: native BMC Object List integration remains; shortcut UI/runtime removed. */
public class MidTierObjectListRegressionTest {
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
        for (int i = 0; i < extensions.getLength(); i++) {
            var ext = (org.w3c.dom.Element) extensions.item(i);
            if (!"com.bmc.arsys.studio.commonui.genericaction".equals(ext.getAttribute("point"))) continue;
            var providers = ext.getElementsByTagName("genericActionProvider");
            for (int j = 0; j < providers.getLength(); j++) {
                var p = (org.w3c.dom.Element) providers.item(j);
                if ("se.yrell.developertools.midtier.MidTierObjectListActionProvider".equals(p.getAttribute("class")))
                    providerFound = true;
            }
        }
        check(providerFound, "BMC Object List native action provider retained");
        check(Class.forName("se.yrell.developertools.midtier.MidTierObjectListActionProvider") != null,
                "native action provider compiled");
        String xml = Files.readString(xmlFile);
        check(xml.contains("icons/toilet_16.png"), "toolbar icon retained");
        check(xml.contains("midTierFlushObject"), "direct flush handler remains");
        check(!xml.contains("org.eclipse.ui.bindings"), "plugin declares no keyboard binding");
        var root = Path.of("source/se.yrell.developertools/src/se/yrell/developertools");
        var prefs = Files.readString(root.resolve("internal/preferences/DeveloperStudioToolsPreferencePage.java"));
        check(!prefs.contains("MidTierQuickFlushShortcut"), "no hotkey validation in Preferences");
        check(!prefs.contains("Quick flush shortcut"), "hotkey UI removed");
        check(!Files.exists(root.resolve("midtier/MidTierQuickFlushShortcut.java")), "binding implementation removed");
        var startup = Files.readString(root.resolve("ToolsEarlyStartup.java"));
        check(startup.contains("LegacyMidTierShortcutCleanup.schedule()"), "old USER bindings cleaned at startup");
        check(!startup.contains("MidTierQuickFlushShortcut.install()"), "no new bindings registered");
        String migration = Files.readString(root.resolve("midtier/LegacyMidTierShortcutCleanup.java"));
        check(migration.contains("binding.getType() == Binding.USER"), "cleanup targets USER binding only");
        check(migration.contains("LEGACY_COMMAND.equals(binding.getParameterizedCommand().getId())"),
              "cleanup targets exactly our former command");
        check(!migration.contains("new KeyBinding"), "migration never adds keybindings");
        System.out.println("Object List / legacy hotkey removal tests: " + checks + " PASS");
    }
}
