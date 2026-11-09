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
    static class Fixture implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger logins = new AtomicInteger();
        final AtomicInteger flushes = new AtomicInteger();
        final AtomicReference<String> body = new AtomicReference<>("");
        boolean badAction;
        boolean wrongServer;
        boolean crossOrigin;
        boolean loginFormAlways;
        Fixture() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/arsys/", this::serve);
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
                if (path.endsWith("/config.jsp")) {
                    send(ex, 200, LOGIN);
                } else if (path.endsWith("/config_cache_cleanup.jsp")) {
                    if (!authenticated(ex) || loginFormAlways) { send(ex, 200, LOGIN); return; }
                    String endpoint = crossOrigin ? "http://evil.invalid/arsys/servlet/GoatConfigServlet?action=Cache&amp;op_type=form_cleanup"
                            : badAction ? "/arsys/servlet/BadServlet?action=Cache&amp;op_type=form_cleanup"
                            : "/arsys/servlet/GoatConfigServlet?action=Cache&amp;op_type=form_cleanup";
                    send(ex, 200, "<html><form name='cache_clenaup_form' action='" + endpoint + "' method='post'>"
                        + "<input name='form_flush' value='Yes'><input name='cfgStok' value='token123'>"
                        + "<select name='server'><option value=''>-- choose --</option><option value='"
                        + (wrongServer ? "badserver" : "arserver") + "'>arserver</option></select>"
                        + "<input name='form_name' value=''></form></html>");
                } else if (path.endsWith("/config_cache.jsp")) {
                    if (!authenticated(ex) || loginFormAlways) { send(ex, 200, LOGIN); return; }
                    send(ex, 200, "<html><form name='flush_cache_form' action='/arsys/servlet/GoatConfigServlet?action=Cache' method='post'>"
                        + "<input name='cache_flush' value='No'><input name='cache_flush_allpods' value='No'>"
                        + "<input name='cfgStok' value='token123'></form></html>");
                } else if (path.endsWith("/ConfigServlet") && "action=logon".equals(query)) {
                    String form = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (form.contains("username=tester") && form.contains("pwd=secret") && form.contains("servername=arserver")) {
                        logins.incrementAndGet();
                        ex.getResponseHeaders().add("Set-Cookie", "SESSION=ok; Path=/arsys; HttpOnly");
                        ex.getResponseHeaders().set("Location", "/arsys/shared/config/config.jsp");
                        ex.sendResponseHeaders(302, -1); ex.close();
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
        check(original.equals(MidTierConfiguration.decode(MidTierConfiguration.encode(original))), "v2 round-trip with allow HTTP flag");
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
            boolean rejected = false;
            try { client.flush(0, "User", true, MidTierCacheRegressionTest::credentials); }
            catch (java.io.IOException e) { rejected = e.getMessage().contains("server list"); }
            check(rejected && f.flushes.get() == 2, "Wrong server is refused before POST");
            f.wrongServer = false;
            f.crossOrigin = true;
            rejected = false;
            try { client.flush(0, "User", true, MidTierCacheRegressionTest::credentials); }
            catch (java.io.IOException e) { rejected = e.getMessage().contains("outside"); }
            check(rejected && f.flushes.get() == 2, "Cross-origin form action refused");
            f.crossOrigin = false;
            f.badAction = true;
            rejected = false;
            try { client.flush(0, "User", true, MidTierCacheRegressionTest::credentials); }
            catch (java.io.IOException e) { rejected = e.getMessage().contains("Unexpected"); }
            check(rejected && f.flushes.get() == 2, "Wrong endpoint refused");
            f.badAction = false;
            result = client.flush(2, "", true, MidTierCacheRegressionTest::credentials);
            check(result.outcome() == MidTierHttpClient.Outcome.ACCEPTED_UNVERIFIED, "Full cache not falsely marked confirmed");
            check(f.body.get().contains("cache_flush_allpods=Yes") && f.body.get().contains("cache_flush=No"), "All-pods switch");
            result = client.flush(1, "", true, MidTierCacheRegressionTest::credentials);
            check(f.body.get().contains("cache_flush=Yes") && f.body.get().contains("cache_flush_allpods=No"), "Full flush switch");
        }
        System.out.println("MidTier cache regression tests: " + assertions + " PASS (embedded mock HTTP server)");
    }
}
