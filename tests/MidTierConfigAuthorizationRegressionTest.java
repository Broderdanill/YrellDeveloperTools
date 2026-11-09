import se.yrell.developertools.midtier.MidTierBrowserScript;

/** Deterministic checks for the separate RSSO and Configuration Tool login states. */
public final class MidTierConfigAuthorizationRegressionTest {
    private static int checks;
    private static void check(boolean result, String description) {
        checks++;
        if (!result) throw new AssertionError(description);
    }
    public static void main(String[] args) {
        String states = MidTierBrowserScript.pageState();
        check(states.contains("CONFIG_DENIED"), "detect Configuration Tool authorization failure page");
        check(states.contains("configuration tool authorization failed"), "recognize actual BMC denial text");
        check(states.contains("CONFIG_LOGIN"), "recognize separate Configuration Tool login form");
        check(states.indexOf("CONFIG_DENIED") < states.indexOf("CACHE_FORM"),
                "authorization error wins over cache form when both appear");
        String loginCheck = MidTierBrowserScript.configLoginCheck("https://mt.example/arsys");
        check(loginCheck.contains("ConfigServlet"), "authenticate via BMC's ConfigServlet");
        check(loginCheck.contains("UNEXPECTED_LOGIN_ACTION"), "reject other form actions");
        check(loginCheck.contains("NOT_CONFIG_TOOL_LOGIN"), "verify Configuration Tool login form indicator");
        String submit = MidTierBrowserScript.submitConfigLogin("https://mt.example/arsys", "arserver", "user", "s\\\";alert(1)//");
        check(submit.contains("s\\\\\\\";alert(1)//"), "password escaped in JavaScript string");
        check(submit.contains("configLoginCheck") == false, "standalone JavaScript for SWT Browser");
        check(submit.contains("f.submit()"), "send BMC form in same browser session");
        check(submit.contains("\"https://mt.example/arsys\""), "bound to configured MidTier URL");
        check(MidTierBrowserScript.resultProbe().contains("CONFIG_DENIED"), "no false success on denied POST");
        check(MidTierBrowserScript.preflight("https://mt.example/arsys", 0, "arserver").contains("cfgStok"),
                "still require configuration session token before cache flush");
        System.out.println("MidTier Configuration Tool authorization tests: " + checks + " PASS");
    }
}
