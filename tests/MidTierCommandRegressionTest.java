import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import se.yrell.developertools.midtier.MidTierConfiguration;
import se.yrell.developertools.midtier.MidTierFlushAvailability;

/** Runs without an Eclipse workbench or server. */
public class MidTierCommandRegressionTest {
    private static int checks;
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        List<MidTierConfiguration.Mapping> mapped = List.of(
            new MidTierConfiguration.Mapping("arserver", "https://mt.example/arsys"),
            new MidTierConfiguration.Mapping("prod", "https://prod.example/arsys"));
        check(MidTierFlushAvailability.configured("arserver", mapped), "mapped server");
        check(MidTierFlushAvailability.configured("ARSERVER", mapped), "case insensitive server");
        check(!MidTierFlushAvailability.configured("unknown", mapped), "unmapped server");
        check("arserver".equals(MidTierFlushAvailability.soleConfiguredConnected(List.of("arserver"), mapped)), "single server");
        check("prod".equals(MidTierFlushAvailability.soleConfiguredConnected(List.of("unused", "prod"), mapped)), "sole mapped connection");
        check(MidTierFlushAvailability.soleConfiguredConnected(List.of("arserver", "prod"), mapped) == null, "never guess between configured servers");
        check(MidTierFlushAvailability.soleConfiguredConnected(List.of("unmapped"), mapped) == null, "unmapped connection not offered");
        String xml = Files.readString(Path.of(args[0]));
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.newDocumentBuilder().parse(Path.of(args[0]).toFile());
        check(xml.contains("se.yrell.developertools.commands.midTierFlushObject"), "quick command declared");
        check(xml.contains("MidTierFlushObjectHandler"), "quick handler registered");
        check(xml.contains("toolbarAvailable"), "toolbar server availability registered");
        check(xml.contains("popup:org.eclipse.ui.popup.any"), "context menu command registered");
        check(!xml.contains("org.eclipse.ui.bindings"), "no default shortcut collision");
        System.out.println("MidTier toolbar/command assertions: " + checks + " PASS");
    }
}
