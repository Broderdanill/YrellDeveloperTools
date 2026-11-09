import java.lang.reflect.Method;
import java.util.List;
import se.yrell.developertools.midtier.MidTierConfiguration;
import se.yrell.developertools.midtier.MidTierConfiguration.Mapping;

public class MidTierCacheRegressionTest {
    private static int tests;
    private static void check(boolean condition, String description) {
        tests++;
        if (!condition) throw new AssertionError(description);
    }
    private static void invalid(String url) {
        boolean threw = false;
        try { MidTierConfiguration.normalizeBaseUrl(url); }
        catch (IllegalArgumentException e) { threw = true; }
        check(threw, "should reject unsafe URL: " + url);
    }
    public static void main(String[] args) throws Exception {
        String normalized = MidTierConfiguration.normalizeBaseUrl("http://ARS.SANDBOX:8080/arsys/shared/config/config_cache_cleanup.jsp");
        check("http://ars.sandbox:8080/arsys".equals(normalized), "normalize config page to context root");
        check("https://myhost/arsys".equals(MidTierConfiguration.normalizeBaseUrl("https://myhost")), "default /arsys");
        check("https://a.example.org/custom".equals(MidTierConfiguration.normalizeBaseUrl("https://a.example.org/custom/")), "custom context root");
        invalid("ftp://ars.sandbox:8080/arsys");
        invalid("http://x/arsys?token=secret");
        invalid("http://alice:pass@host/arsys");
        invalid("https://host/arsys/../other");
        invalid("https://host/arsys/%2e%2e/root");
        invalid("https://host/arsys#part");
        invalid("somewhere/arsys");
        List<Mapping> mappings = List.of(new Mapping("arserver", normalized), new Mapping("Lab-Server", "https://lab.example.org/arsys"));
        check(MidTierConfiguration.decode(MidTierConfiguration.encode(mappings)).equals(mappings), "roundtrip non-secret mappings");
        check(MidTierConfiguration.lookup(mappings,"ARSERVER") != null, "case insensitive AR server lookup");
        check(MidTierConfiguration.lookup(mappings,"other") == null, "unknown target not guessed");
        check(MidTierConfiguration.decode("v1\nbadline\n").isEmpty(), "corrupt row ignored");
        boolean duplicateRejected = false;
        try { MidTierConfiguration.encode(List.of(mappings.get(0), new Mapping("ARSERVER", normalized))); }
        catch (IllegalArgumentException e) { duplicateRejected = true; }
        check(duplicateRejected, "duplicate servers rejected");
        check(MidTierConfiguration.pageUrl(mappings.get(0),true).endsWith("/shared/config/config_cache_cleanup.jsp"), "form endpoint");
        check(MidTierConfiguration.pageUrl(mappings.get(0),false).endsWith("/shared/config/config_cache.jsp"), "full cache endpoint");
        check(MidTierConfiguration.isExpectedPage("http://ars.sandbox:8080/arsys/shared/config/config_cache_cleanup.jsp",mappings.get(0),true), "expected page");
        check(!MidTierConfiguration.isExpectedPage("http://evil.example.com/arsys/shared/config/config_cache_cleanup.jsp",mappings.get(0),true), "reject foreign origin");
        check(!MidTierConfiguration.isExpectedPage("http://ars.sandbox:8080/arsys/shared/config/config_cache.jsp",mappings.get(0),true), "reject wrong operation page");
        Method make = Class.forName("se.yrell.developertools.midtier.MidTierFlushDialog")
                .getDeclaredMethod("makeSubmissionScript",int.class,String.class,String.class,String.class);
        make.setAccessible(true);
        String formScript = (String)make.invoke(null, 0,"arserver","HLX:O'Brien", "/arsys");
        String fullScript = (String)make.invoke(null, 1,"arserver","HLX:Test", "/arsys");
        String podScript = (String)make.invoke(null, 2,"arserver","HLX:Test", "/arsys");
        check(formScript.contains("cache_clenaup_form"), "BMC's actual misspelled form name preserved");
        check(formScript.contains("form_flush"), "form flush parameter");
        check(formScript.contains("NO_TOKEN"), "must require live cfgStok");
        check(formScript.contains("NO_SERVER"), "must match MidTier's AR server dropdown");
        check(formScript.contains("O\\'Brien"), "form name escaped in JS");
        check(fullScript.contains("cache_flush"), "full flush parameter");
        check(podScript.contains("cache_flush_allpods"), "all pods parameter");
        check(!podScript.contains("form_cleanup"), "pods do not use form servlet");
        System.out.println("MidTier cache regression tests: " + tests + " PASS");
        // Optional JS execution verification by scripts/test-midtier-js.js.
        System.out.println("FORM_JS_BASE64=" + java.util.Base64.getEncoder().encodeToString(formScript.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        System.out.println("FULL_JS_BASE64=" + java.util.Base64.getEncoder().encodeToString(fullScript.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        System.out.println("PODS_JS_BASE64=" + java.util.Base64.getEncoder().encodeToString(podScript.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
