import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import java.util.List;
import se.yrell.developertools.icons.IconCatalog;
import se.yrell.developertools.icons.IconEntry;

/** Isolated JDK-only regression tests; no Developer Studio installation required. */
public final class PwaIconCatalogRegressionTest {
    private static int assertions;

    @SuppressWarnings("unchecked")
    private static List<IconEntry> parse(String css) throws Exception {
        Method m = IconCatalog.class.getDeclaredMethod("readCssCatalogText", String.class);
        m.setAccessible(true);
        return (List<IconEntry>) m.invoke(null, css);
    }

    private static String preview(String css, List<IconEntry> icons) throws Exception {
        Method m = IconCatalog.class.getDeclaredMethod("buildPreviewCss", String.class, List.class);
        m.setAccessible(true);
        return (String) m.invoke(null, css, icons);
    }

    private static void yes(boolean condition, String reason) {
        if (!condition) throw new AssertionError(reason);
        assertions++;
    }

    private static boolean has(List<IconEntry> entries, String name, String codePoint) {
        for (IconEntry e : entries) {
            if (name.equals(e.getName()) && codePoint.equals(e.getCodePoint())) return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        String css = "@font-face{font-family:'dpl-icon-font';src:url(dpl-iconfont.abcd.woff2?v=2.142.0) format('woff2');}" +
             ".d-icon-arrow_down_circle:before,.d-icon-add:before, .d-icon-search:before {content:\"\\e607\";}" +
             ".d-icon-alert::before,.d-icon-more_2::before{content:'\\e660';}" +
             ".d-icon-edit:before{color:red;content:\"\\e614\";}" +
             ".d-icon-left_arrow:before,.d-icon-right_arrow:before {content:'\\e60a';}" +
             ".some-other:before{content:'\\e999';}" +
             "@media (min-width:100px){.d-icon-nested:before{content:'\\e611';}}";
        List<IconEntry> icons = parse(css);
        yes(icons.size() == 9, "Expected 9 parsed icons, got " + icons.size());
        yes(has(icons, "d-icon-arrow_down_circle", "e607"), "missing first grouped icon");
        yes(has(icons, "d-icon-add", "e607"), "missing middle grouped icon");
        yes(has(icons, "d-icon-search", "e607"), "missing last grouped icon");
        yes(has(icons, "d-icon-alert", "e660"), "missing double colon pseudo class");
        yes(has(icons, "d-icon-more_2", "e660"), "missing underscore icon");
        yes(has(icons, "d-icon-edit", "e614"), "content not first declaration");
        yes(has(icons, "d-icon-left_arrow", "e60a"), "missing left arrow icon");
        yes(has(icons, "d-icon-right_arrow", "e60a"), "missing right arrow icon");
        yes(has(icons, "d-icon-nested", "e611"), "missing media icon");
        String generated = preview(css, icons);
        yes(generated.contains("@font-face") && generated.contains("dpl-iconfont.abcd.woff2"), "font-face excluded");
        yes(generated.contains(".d-icon-arrow_down_circle:before{content:\"\\e607\";}"), "generated glyph wrong");
        yes(!generated.contains(".some-other:before"), "preview included unrelated CSS");
        // Browser.setText must not receive huge bundles; preview grows only with icon declarations.
        String huge = css + ".unrelated{color:black}".repeat(200_000);
        String optimized = preview(huge, parse(huge));
        yes(optimized.length() < 3000, "preview retained the huge original bundle");
        yes(parse("<!doctype html><html><body>login</body></html>").isEmpty(), "HTML parsed as glyphs");
        // End-to-end stylesheet retrieval and font URL embedding, using a local
        // HTTP server rather than a live BMC system.
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        final byte[] cssBytes = css.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server.createContext("/pwa/styles.abc.css", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/css; charset=utf-8");
            exchange.sendResponseHeaders(200, cssBytes.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(cssBytes); }
        });
        server.createContext("/pwa/dpl-iconfont.abcd.woff2", exchange -> {
            byte[] font = "fakefont-test".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, font.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(font); }
        });
        server.createContext("/pwa/login.css", exchange -> {
            byte[] html = "<!DOCTYPE html><html>SSO</html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, html.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) { out.write(html); }
        });
        server.start();
        try {
            Method load = IconCatalog.class.getDeclaredMethod("readCssCatalog", String.class);
            load.setAccessible(true);
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/pwa/";
            Object catalog = load.invoke(null, url + "styles.abc.css");
            Field entriesField = catalog.getClass().getDeclaredField("icons");
            entriesField.setAccessible(true);
            Field cssField = catalog.getClass().getDeclaredField("rewrittenCss");
            cssField.setAccessible(true);
            yes(((List<?>) entriesField.get(catalog)).size() == 9, "end-to-end stylesheet load lost icons");
            String browserCss = (String) cssField.get(catalog);
            yes(browserCss.contains("data:font/woff2;base64,"), "relative font was not embedded");
            yes(browserCss.contains(".d-icon-add:before"), "compact preview omitted icon CSS");
            boolean rejectedHtml = false;
            try {
                load.invoke(null, url + "login.css");
            } catch (java.lang.reflect.InvocationTargetException ex) {
                rejectedHtml = ex.getCause() instanceof java.io.IOException &&
                     ex.getCause().getMessage().contains("HTML rather than PWA CSS");
            }
            yes(rejectedHtml, "SSO login response was not recognized as HTML");
        } finally {
            server.stop(0);
        }
        System.out.println("PwaIconCatalogRegressionTest: " + assertions + " PASS");
    }
}
