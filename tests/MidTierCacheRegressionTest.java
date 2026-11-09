import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import se.yrell.developertools.midtier.MidTierConfiguration;
import se.yrell.developertools.midtier.MidTierHttpClient;

public final class MidTierCacheRegressionTest {
    private static int assertions;
    private static void check(boolean good, String why) {
        assertions++;
        if (!good) throw new AssertionError(why);
    }
    private static void invalid(String url) {
        boolean rejected = false;
        try { MidTierConfiguration.normalizeBaseUrl(url); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Reject invalid URL: " + url);
    }
    private static void invalidSso(String url) {
        boolean rejected = false;
        try { MidTierConfiguration.normalizeSsoUrl(url); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Reject invalid SSO context: " + url);
    }
    static class Fixture implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger logins = new AtomicInteger();
        final AtomicInteger flushes = new AtomicInteger();
        final AtomicReference<String> body = new AtomicReference<>("");
        boolean badAction;
        boolean wrongServer;
        boolean crossOrigin;
        boolean crossOriginFull;
        boolean manyServers;
        boolean loginFormAlways;
        boolean ssoRedirectBeforeLogin;
        boolean ssoAutoLogin;
        boolean externalSsoRedirect;
        boolean loginRedirect307;
        final AtomicInteger ssoVisits = new AtomicInteger();
        Fixture() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/arsys/", this::serve);
            server.createContext("/rsso/", this::serve);
            server.start();
        }
        String base() { return "http://127.0.0.1:" + server.getAddress().getPort() + "/arsys"; }
        private boolean authenticated(HttpExchange ex) {
            return ex.getRequestHeaders().getFirst("Cookie") != null
                    && ex.getRequestHeaders().getFirst("Cookie").contains("SESSION=ok");
        }
        void send(HttpExchange ex, int code, String html) throws Exception {
            byte[] data = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(code, data.length);
            try (var out = ex.getResponseBody()) { out.write(data); }
        }
        static final String LOGIN = "<html><form name='loginForm' action='/arsys/servlet/ConfigServlet?action=logon' method='post'>"
                + "<input name='servername' value='arserver'><input name='serverport' value='46262'>"
                + "<input name='username'><input name='pwd'><input name='timezone'>"
                + "<input name='isFromCfgTool' value='true'></form></html>";
        private void serve(HttpExchange ex) {
            try {
                String path = ex.getRequestURI().getPath();
                String query = ex.getRequestURI().getRawQuery();
                if (path.startsWith("/rsso/")) {
                    ssoVisits.incrementAndGet();
                    if (ssoAutoLogin) {
                        ex.getResponseHeaders().add("Set-Cookie", "SESSION=ok; Path=/arsys; HttpOnly");
                        ex.getResponseHeaders().set("Location", "/arsys/shared/config/config_cache_cleanup.jsp");
                        ex.sendResponseHeaders(302, -1); ex.close();
                    } else send(ex, 200, "<html>RSSO browser login (requires interaction)</html>");
                } else if (path.endsWith("/config.jsp")) {
                    if (externalSsoRedirect) {
                        ex.getResponseHeaders().set("Location", "https://sso.invalid/rsso/start?token=secret");
                        ex.sendResponseHeaders(302, -1); ex.close();
                    } else send(ex, 200, LOGIN);
                } else if (path.endsWith("/config_cache_cleanup.jsp")) {
                    if (ssoRedirectBeforeLogin && !authenticated(ex)) {
                        ex.getResponseHeaders().set("Location", externalSsoRedirect
                                ? "https://sso.invalid/rsso/start?token=secret" : "/rsso/start");
                        ex.sendResponseHeaders(302, -1); ex.close(); return;
                    }
                    if (!authenticated(ex) || loginFormAlways) { send(ex, 200, LOGIN); return; }
                    String endpoint = crossOrigin ? "http://evil.invalid/arsys/servlet/GoatConfigServlet?action=Cache&amp;op_type=form_cleanup"
                            : badAction ? "/arsys/servlet/BadServlet?action=Cache&amp;op_type=form_cleanup"
                            : "/arsys/servlet/GoatConfigServlet?action=Cache&amp;op_type=form_cleanup";
                    send(ex, 200, "<html><form name='cache_clenaup_form' action='" + endpoint + "' method='post'>"
                        + "<input name='form_flush' value='Yes'><input name='cfgStok' value='token123'>"
                        + "<select name='server'><option value=''>-- choose --</option><option value='"
                        + (manyServers ? "other'>other</option><option value='third" : wrongServer ? "badserver" : "arserver")
                        + "'>arserver</option></select>"
                        + "<input name='form_name' value=''></form></html>");
                } else if (path.endsWith("/config_cache.jsp")) {
                    if (!authenticated(ex) || loginFormAlways) { send(ex, 200, LOGIN); return; }
                    send(ex, 200, "<html><form name='flush_cache_form' action='"
                        + (crossOriginFull ? "http://backend.internal:8080/arsys/servlet/GoatConfigServlet?action=Cache"
                             : "/arsys/servlet/GoatConfigServlet?action=Cache") + "' method='post'>"
                        + "<input name='cache_flush' value='No'><input name='cache_flush_allpods' value='No'>"
                        + "<input name='cfgStok' value='token123'></form></html>");
                } else if (path.endsWith("/ConfigServlet") && "action=logon".equals(query)) {
                    String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (form.contains("username=tester") && form.contains("pwd=secret") && form.contains("servername=arserver")) {
                        logins.incrementAndGet();
                        ex.getResponseHeaders().add("Set-Cookie", "SESSION=ok; Path=/arsys; HttpOnly");
                        ex.getResponseHeaders().set("Location", loginRedirect307 ? "/rsso/start" : "/arsys/shared/config/config.jsp");
                        ex.sendResponseHeaders(loginRedirect307 ? 307 : 302, -1); ex.close();
                    } else send(ex, 200, LOGIN);
                } else if (path.endsWith("/GoatConfigServlet") && authenticated(ex)) {
                    String post = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    body.set(post);
                    if (!post.contains("cfgStok=token123")) { send(ex, 403, "bad token"); return; }
                    int previous = flushes.getAndIncrement();
                    String message = "action=Cache&op_type=form_cleanup".equals(query)
                        ? previous == 0 ? "Form User for server arserver is cleaned !"
                                : "Form User for server arserver doesn't exists in cache ..."
                        : "Cache flush request accepted";
                    send(ex, 200, "<html><table><tr><td class='status'>" + message + "</td></tr></table></html>");
                } else send(ex, 404, "not found");
            } catch (Exception e) { throw new RuntimeException(e); }
        }
        @Override public void close() { server.stop(0); }
    }
    private static MidTierHttpClient.Credentials credentials() {
        return new MidTierHttpClient.Credentials("tester", "secret", 46262, "");
    }
    public static void main(String[] args) throws Exception {
        var normalized = MidTierConfiguration.normalizeBaseUrl("http://ARS.SANDBOX:8080/arsys/shared/config/config_cache_cleanup.jsp");
        check("http://ars.sandbox:8080/arsys".equals(normalized), "Normalize config path");
        invalid("ftp://host/arsys");
        invalid("http://alice:password@host/arsys");
        invalid("http://host/arsys?token=secret");
        invalid("https://host/arsys/../other");
        invalid("https://host/arsys/%2e%2e/root");
        var original = List.of(new MidTierConfiguration.Mapping("arserver", normalized, true),
                new MidTierConfiguration.Mapping("other", "https://other/arsys"));
        check(original.equals(MidTierConfiguration.decode(MidTierConfiguration.encode(original))), "v3 round-trip with allow HTTP flag");
        var withSso = new MidTierConfiguration.Mapping("prod", "https://mid.example.com/arsys", false,
                "https://login.example.com/rsso");
        check(withSso.equals(MidTierConfiguration.decode(MidTierConfiguration.encode(List.of(withSso))).get(0)),
                "v3 round-trip with optional trusted SSO URL");
        check(MidTierConfiguration.decode("v2\nYXJzZXJ2ZXI:aHR0cDovL2Fycy5zYW5kYm94OjgwODAvYXJzeXM:1\n")
                .get(0).allowHttpCredentials(), "v2 mappings remain readable");
        invalidSso("http://sso.example.com/rsso");
        invalidSso("https://sso.example.com/rsso/../bad");
        invalidSso("https://sso.example.com/");
        check(MidTierConfiguration.decode("v1\nYXJzZXJ2ZXI:aHR0cDovL2Fycy5zYW5kYm94OjgwODAvYXJzeXM\n")
                .get(0).allowHttpCredentials() == false, "Existing v1 settings safely default to HTTPS-only");
        check(MidTierConfiguration.lookup(original,"ARSERVER") != null, "case insensitive lookup");
        check(!MidTierConfiguration.isExpectedPage("http://bad/arsys/shared/config/config_cache_cleanup.jsp", original.get(0),true), "Wrong origin");
        try (Fixture f = new Fixture()) {
            var mapping = new MidTierConfiguration.Mapping("arserver", f.base(), true);
            var client = MidTierHttpClient.forTarget(mapping);
            boolean blocked = false;
            try { client.flush(0, "User", false, MidTierCacheRegressionTest::credentials); }
            catch (java.io.IOException e) { blocked = e.getMessage().contains("HTTP"); }
            check(blocked && f.logins.get() == 0 && f.flushes.get() == 0, "Do not send credentials on HTTP without explicit opt-in");
            var result = client.flush(0, "User", true, MidTierCacheRegressionTest::credentials);
            check(result.outcome() == MidTierHttpClient.Outcome.CLEANED, "Real form response parsed");
            check(f.logins.get() == 1 && f.flushes.get() == 1, "Login and flush reached mock server");
            check(f.body.get().contains("form_name=User") && f.body.get().contains("server=arserver"), "Correct form and server");
            result = client.flush(0, "User", true, () -> { throw new AssertionError("Must reuse active MidTier session"); });
            check(result.outcome() == MidTierHttpClient.Outcome.ALREADY_EMPTY, "Empty cache response parsed");
            f.wrongServer = true;
            result = client.flush(0, "User", true, MidTierCacheRegressionTest::credentials);
            check(f.body.get().contains("server=badserver") && f.flushes.get() == 3,
                    "The only Server List value is selected even if Studio uses another alias");
            f.wrongServer = false;
            f.manyServers = true;
            boolean rejected = false;
            try { client.flush(0, "User", true, MidTierCacheRegressionTest::credentials); }
            catch (java.io.IOException e) { rejected = e.getMessage().contains("Multiple AR servers"); }
            check(rejected && f.flushes.get() == 3, "Ambiguous multiple servers refused before POST");
            f.manyServers = false;
            f.crossOrigin = true;
            result = client.flush(0, "User", true, MidTierCacheRegressionTest::credentials);
            check(f.flushes.get() == 4 && f.body.get().contains("form_name=User"),
                    "Backend/foreign absolute servlet action is POSTed to configured origin only");
            f.crossOrigin = false;
            f.badAction = true;
            rejected = false;
            try { client.flush(0, "User", true, MidTierCacheRegressionTest::credentials); }
            catch (java.io.IOException e) { rejected = e.getMessage().contains("Unexpected"); }
            check(rejected && f.flushes.get() == 4, "Wrong servlet path refused");
            f.badAction = false;
            f.crossOriginFull = true;
            result = client.flush(2, "", true, MidTierCacheRegressionTest::credentials);
            check(result.outcome() == MidTierHttpClient.Outcome.ACCEPTED_UNVERIFIED, "Full cache not falsely marked confirmed");
            check(f.body.get().contains("cache_flush_allpods=Yes") && f.body.get().contains("cache_flush=No"), "All-pods switch");
            check(f.flushes.get() == 5, "Backend absolute action normalized for all-pods flush");
            result = client.flush(1, "", true, MidTierCacheRegressionTest::credentials);
            check(f.body.get().contains("cache_flush=Yes") && f.body.get().contains("cache_flush_allpods=No"), "Full flush switch");
        }
        // Check external SSO origin allowlisting and POST blocking without contacting a real IdP.
        {
            var client = MidTierHttpClient.forTarget(new MidTierConfiguration.Mapping(
                    "arserver", "https://midtier.example.com/arsys", false,
                    "https://login.example.com/rsso"));
            var verify = MidTierHttpClient.class.getDeclaredMethod("verifyNavigable", java.net.URI.class, boolean.class);
            verify.setAccessible(true);
            verify.invoke(client, java.net.URI.create("https://login.example.com/rsso/start?state=opaque"), false);
            check(true, "Explicitly configured HTTPS SSO redirect may be followed by GET");
            boolean denied = false;
            try { verify.invoke(client, java.net.URI.create("https://login.example.com/rsso/start"), true); }
            catch (java.lang.reflect.InvocationTargetException e) { denied = e.getCause() instanceof java.io.IOException; }
            check(denied, "AR credentials must never POST to an SSO origin");
            denied = false;
            try { verify.invoke(client, java.net.URI.create("https://login.example.com/rsso-other/start"), false); }
            catch (java.lang.reflect.InvocationTargetException e) { denied = e.getCause() instanceof java.io.IOException; }
            check(denied, "SSO path prefix escape is blocked");
            denied = false;
            try { verify.invoke(client, java.net.URI.create("https://untrusted.example.com/rsso/start"), false); }
            catch (java.lang.reflect.InvocationTargetException e) { denied = e.getCause() instanceof java.io.IOException; }
            check(denied, "Unconfigured SSO hosts are blocked");
            denied = false;
            try { verify.invoke(client, java.net.URI.create("https://midtier.example.com/arsys-evil/servlet/ConfigServlet"), false); }
            catch (java.lang.reflect.InvocationTargetException e) { denied = e.getCause() instanceof java.io.IOException; }
            check(denied, "MidTier context path lookalikes are blocked");
        }
        try (Fixture f = new Fixture()) {
            f.ssoRedirectBeforeLogin = true;
            f.ssoAutoLogin = true;
            var client = MidTierHttpClient.forTarget(new MidTierConfiguration.Mapping("arserver", f.base(), true));
            var result = client.flush(0, "User", true, () -> { throw new AssertionError("SSO auto-login should not require AR credentials"); });
            check(result.outcome() == MidTierHttpClient.Outcome.CLEANED && f.ssoVisits.get() == 1
                    && f.logins.get() == 0, "Same-origin RSSO redirect roundtrip can auto-login without password");
        }
        try (Fixture f = new Fixture()) {
            f.ssoRedirectBeforeLogin = true;
            f.ssoAutoLogin = false;
            var client = MidTierHttpClient.forTarget(new MidTierConfiguration.Mapping("arserver", f.base(), true));
            var result = client.flush(0, "User", true, MidTierCacheRegressionTest::credentials);
            check(result.outcome() == MidTierHttpClient.Outcome.CLEANED && f.logins.get() == 1,
                    "Interactive same-origin SSO page triggers direct BMC Config Tool login fallback");
        }
        try (Fixture f = new Fixture()) {
            f.ssoRedirectBeforeLogin = true;
            f.externalSsoRedirect = true;
            var client = MidTierHttpClient.forTarget(new MidTierConfiguration.Mapping("arserver", f.base(), true));
            boolean rejected = false;
            try { client.flush(0, "User", true, MidTierCacheRegressionTest::credentials); }
            catch (java.io.IOException e) { rejected = e.getMessage().contains("sso.invalid")
                    && !e.getMessage().contains("token=secret"); }
            check(rejected && f.logins.get() == 0 && f.flushes.get() == 0,
                    "Unconfigured external RSSO redirect must not be followed or leak query tokens");
        }
        try (Fixture f = new Fixture()) {
            f.loginRedirect307 = true;
            var client = MidTierHttpClient.forTarget(new MidTierConfiguration.Mapping("arserver", f.base(), true));
            boolean rejected = false;
            try { client.flush(0, "User", true, MidTierCacheRegressionTest::credentials); }
            catch (java.io.IOException e) { rejected = e.getMessage().contains("POST-preserving"); }
            check(rejected && f.ssoVisits.get() == 0 && f.flushes.get() == 0,
                    "Never re-POST AR credentials to RSSO on 307 redirect");
        }
        System.out.println("MidTier cache regression tests: " + assertions + " PASS (embedded mock HTTP server)");
    }
}
