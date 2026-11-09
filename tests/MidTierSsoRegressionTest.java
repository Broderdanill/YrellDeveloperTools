import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import se.yrell.developertools.midtier.MidTierBrowserScript;
import se.yrell.developertools.midtier.MidTierConfiguration;
import se.yrell.developertools.midtier.MidTierHttpClient;

public final class MidTierSsoRegressionTest {
    private static int tests;
    static void verify(boolean pass, String test) {
        tests++;
        if (!pass) throw new AssertionError(test);
    }
    public static void main(String[] args) throws Exception {
        var m = new MidTierConfiguration.Mapping("arserver", "https://mid.example/arsys", false,
                "https://sso.example/rsso", MidTierConfiguration.AuthenticationMethod.SSO);
        String encoded = MidTierConfiguration.encode(List.of(m));
        verify(encoded.startsWith("v4\n"), "v4 settings format");
        verify(MidTierConfiguration.decode(encoded).equals(List.of(m)), "v4 settings roundtrip");
        verify(MidTierConfiguration.decode("v3\nYXJzZXJ2ZXI:aHR0cHM6Ly9taWQuZXhhbXBsZS9hcnN5cw:0:\n").get(0)
                .authenticationMethod() == MidTierConfiguration.AuthenticationMethod.AUTO, "v3 maps default AUTO");
        verify(new MidTierConfiguration.Mapping("a", "https://m/arsys").authenticationMethod()
                == MidTierConfiguration.AuthenticationMethod.AUTO, "old constructors default AUTO");
        verify(MidTierBrowserScript.preflight(m.baseUrl(), 0, m.server()).contains("cfgStok"), "checks real token");
        verify(MidTierBrowserScript.preflight(m.baseUrl(), 0, m.server()).contains("form_cleanup"), "checks form endpoint");
        verify(MidTierBrowserScript.submit(m.baseUrl(), 0, "arserver", "User\";alert(1)//")
                .contains("User\\\";alert(1)//"), "escapes form-name injection");
        verify(MidTierBrowserScript.submit(m.baseUrl(), 0, "arserver", "User").contains("preflight") == false,
                "compiled submission includes inline real validation, not a JavaScript dependency");
        try (var f = new MidTierCacheRegressionTest.Fixture()) {
            f.ssoRedirectBeforeLogin = true;
            f.ssoAutoLogin = false;
            AtomicInteger accessed = new AtomicInteger();
            var client = MidTierHttpClient.forTarget(new MidTierConfiguration.Mapping("arserver", f.base(), true));
            boolean browserRequired = false;
            try {
                client.flush(0, "User", true, () -> { accessed.incrementAndGet(); return null; }, true);
            } catch (MidTierHttpClient.BrowserAuthenticationRequired expected) { browserRequired = true; }
            verify(browserRequired, "AUTO redirects to browser when RSSO cannot be completed in Java");
            verify(accessed.get() == 0 && f.logins.get() == 0 && f.flushes.get() == 0,
                    "AUTO does not read or submit AR credentials on SSO pages");
        }
        try (var f = new MidTierCacheRegressionTest.Fixture()) {
            f.ssoRedirectBeforeLogin = true;
            f.ssoAutoLogin = true;
            var client = MidTierHttpClient.forTarget(new MidTierConfiguration.Mapping("arserver", f.base(), true));
            var result = client.flush(0, "User", true,
                    () -> { throw new AssertionError("No AR password should be requested"); }, true);
            verify(result.outcome() == MidTierHttpClient.Outcome.CLEANED && f.flushes.get() == 1,
                    "AUTO accepts RSSO GET roundtrip without Studio credentials");
        }
        try (var f = new MidTierCacheRegressionTest.Fixture()) {
            var client = MidTierHttpClient.forTarget(new MidTierConfiguration.Mapping("arserver", f.base(), true));
            var result = client.flush(0, "User", true, MidTierSsoRegressionTest::credentials, true);
            verify(result.outcome() == MidTierHttpClient.Outcome.CLEANED && f.logins.get() == 1,
                    "AUTO still supports ordinary AR login");
        }
        try (var f = new MidTierCacheRegressionTest.Fixture()) {
            f.ssoRedirectBeforeLogin = true;
            f.externalSsoRedirect = true;
            var client = MidTierHttpClient.forTarget(new MidTierConfiguration.Mapping("arserver", f.base(), true));
            boolean browserRequired = false;
            try { client.flush(0, "User", true,
                    () -> { throw new AssertionError("No AR credentials on external redirect"); }, true); }
            catch (MidTierHttpClient.BrowserAuthenticationRequired expected) { browserRequired = true; }
            verify(browserRequired && f.logins.get() == 0, "AUTO hands external SSO redirect to browser");
        }
        System.out.println("MidTier SSO tests: " + tests + " PASS");
    }
    private static MidTierHttpClient.Credentials credentials() {
        return new MidTierHttpClient.Credentials("tester", "secret", 46262, "");
    }
}
