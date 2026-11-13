package se.yrell.developertools.icons;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/**
 * Dynamic PWA icon catalog loader.
 *
 * Version 0.1.16 intentionally supports only the real BMC PWA CSS/font based
 * catalog. The earlier embedded PDF/PNG fallback catalog was removed so the
 * icon list always reflects the configured Mid Tier/PWA version.
 */
public final class IconCatalog {
    // BMC PWA icon classes can share a single CSS rule with comma-separated selectors.
    private static final Pattern CSS_RULE_PATTERN = Pattern.compile("([^{}]+)\\{([^{}]*)\\}", Pattern.DOTALL);
    private static final Pattern CSS_ICON_SELECTOR_PATTERN = Pattern.compile(
            "\\.d-icon-([A-Za-z0-9_-]+)\\s*::?before\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CONTENT_PATTERN = Pattern.compile(
            "(?:^|;)\\s*content\\s*:\\s*(['\"])\\\\([0-9a-fA-F]{1,6})\\s?\\1", Pattern.CASE_INSENSITIVE);
    private static final Pattern FONT_FACE_PATTERN = Pattern.compile(
            "@font-face\\s*\\{[^{}]*\\}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CSS_URL_PATTERN = Pattern.compile("url\\((['\\\"]?)([^)'\\\"]+)(['\\\"]?)\\)");

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 10000;
    private static final int MAX_CSS_BYTES = 12 * 1024 * 1024;
    private static final int MAX_FONT_BYTES = 3 * 1024 * 1024;
    // A successful catalog remains cached. Empty/error results are retried
    // periodically instead of becoming permanently stuck for the same URL.
    private static final long EMPTY_CACHE_RETRY_MS = 30_000L;
    private static volatile long lastCacheReadTimeMs;
    private static volatile List<IconEntry> cache;
    private static volatile String cacheKey;
    private static volatile String activeCssUrl;
    private static volatile String activeCssBaseUrl;
    private static volatile String activeBrowserCss;
    private static volatile String activeStatus = "No CSS URL configured";

    private IconCatalog() {
    }


    public static void preloadAsync() {
        final String cssUrl = normalizeCssUrl(ToolsPreferences.getPwaIconCatalogUrl());
        if (cssUrl.length() == 0) {
            return;
        }
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    long start = System.currentTimeMillis();
                    List<IconEntry> icons = getIcons();
                    Log.info("Preloaded PWA icon catalog with " + icons.size() + " icons in " + (System.currentTimeMillis() - start) + " ms.");
                } catch (Throwable ex) {
                    Log.warn("Could not preload PWA icon catalog: " + ex.getMessage());
                }
            }
        }, "Yrell-PWA-IconCatalog-Preload");
        t.setDaemon(true);
        t.start();
    }

    public static List<IconEntry> getIcons() {
        String cssUrl = normalizeCssUrl(ToolsPreferences.getPwaIconCatalogUrl());
        String key = cssUrl.length() == 0 ? "none" : cssUrl;
        List<IconEntry> local = cache;
        if (isCacheUsable(local, key)) {
            return local;
        }
        synchronized (IconCatalog.class) {
            local = cache;
            if (isCacheUsable(local, key)) {
                return local;
            }
            activeCssUrl = cssUrl;
            activeCssBaseUrl = baseUrl(cssUrl);
            activeBrowserCss = "";
            activeStatus = cssUrl.length() == 0 ? "No CSS URL configured" : "Not loaded";

            List<IconEntry> loaded = Collections.emptyList();
            if (cssUrl.length() > 0) {
                try {
                    CssCatalog catalog = readCssCatalog(cssUrl);
                    loaded = catalog.icons;
                    activeBrowserCss = catalog.rewrittenCss;
                    if (loaded.isEmpty()) {
                        activeStatus = "CSS received but no d-icon-* :before/::before glyphs found. "
                                + "Check CSS content and login/SSO response; will retry.";
                        Log.warn("PWA stylesheet has no matching d-icon glyph declarations: " + cssUrl);
                    } else {
                        activeStatus = "Loaded " + loaded.size() + " icons from " + cssUrl;
                        Log.info("Loaded PWA icon catalog with " + loaded.size() + " icons: " + cssUrl);
                    }
                } catch (IOException e) {
                    activeStatus = "Could not load styles.css: " + e.getMessage();
                    Log.warn("Could not load PWA icon catalog from styles.css URL " + cssUrl + ": " + e.getMessage());
                    loaded = Collections.emptyList();
                }
            } else {
                Log.warn("PWA Icon Helper has no CSS URL configured. Configure a direct BMC PWA styles.*.css URL in Yrell Developer Tools preferences.");
            }

            cache = Collections.unmodifiableList(loaded);
            lastCacheReadTimeMs = System.currentTimeMillis();
            cacheKey = key;
            return cache;
        }
    }

    private static boolean isCacheUsable(List<IconEntry> value, String key) {
        return value != null && key.equals(cacheKey)
                && (!value.isEmpty() || System.currentTimeMillis() - lastCacheReadTimeMs < EMPTY_CACHE_RETRY_MS);
    }

    public static boolean isCssCatalogActive() {
        return !getIcons().isEmpty() && activeBrowserCss != null && activeBrowserCss.length() > 0;
    }

    public static String getActiveCssUrl() {
        getIcons();
        return activeCssUrl == null ? "" : activeCssUrl;
    }

    public static String getActiveCssBaseUrl() {
        getIcons();
        return activeCssBaseUrl == null ? "" : activeCssBaseUrl;
    }

    public static String getActiveBrowserCss() {
        getIcons();
        return activeBrowserCss == null ? "" : activeBrowserCss;
    }

    public static String getActiveStatus() {
        getIcons();
        return activeStatus == null ? "" : activeStatus;
    }

    public static String normalizeCssUrl(String configured) {
        String value = configured == null ? "" : configured.trim();
        if (value.length() == 0) {
            return "";
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".css") || lower.contains(".css?")) {
            return value;
        }
        if (!value.endsWith("/")) {
            value = value + "/";
        }
        return value + "styles.css";
    }

    private static CssCatalog readCssCatalog(String cssUrl) throws IOException {
        URL url = new URL(cssUrl);
        byte[] bytes;
        try (InputStream in = openBoundedConnection(url)) {
            bytes = readAllBytes(in, MAX_CSS_BYTES);
        }
        String css = new String(bytes, StandardCharsets.UTF_8);
        if (looksLikeHtml(css)) {
            throw new IOException("URL returned HTML rather than PWA CSS (possibly redirected to login/SSO)");
        }
        List<IconEntry> entries = readCssCatalogText(css);
        // Do NOT inline the entire webpack/Angular styles bundle into SWT
        // Browser.setText(). Large styles + base64 fonts can render blank in
        // the embedded browser despite setText() returning true. Only the
        // icon font-face and generated glyph rules are needed for a preview.
        String previewCss = buildPreviewCss(css, entries);
        String rewritten = rewriteCssUrls(previewCss, baseUrl(cssUrl));
        return new CssCatalog(entries, rewritten);
    }

    private static boolean looksLikeHtml(String response) {
        String trimmed = response == null ? "" : response.trim().toLowerCase(java.util.Locale.ROOT);
        return trimmed.startsWith("<!doctype html") || trimmed.startsWith("<html")
                || trimmed.startsWith("<head") || trimmed.startsWith("<body");
    }

    private static List<IconEntry> readCssCatalogText(String css) {
        Map<String, IconEntry> entries = new LinkedHashMap<String, IconEntry>();
        Matcher rules = CSS_RULE_PATTERN.matcher(css == null ? "" : css);
        while (rules.find()) {
            String selectors = rules.group(1);
            String declarations = rules.group(2);
            Matcher content = CSS_CONTENT_PATTERN.matcher(declarations);
            if (!content.find()) {
                continue;
            }
            String codePoint = content.group(2);
            Matcher selector = CSS_ICON_SELECTOR_PATTERN.matcher(selectors);
            while (selector.find()) {
                String name = "d-icon-" + selector.group(1);
                if (!entries.containsKey(name)) {
                    entries.put(name, new IconEntry(name, "", -1, -1, -1, codePoint));
                }
            }
        }
        return new ArrayList<IconEntry>(entries.values());
    }

    private static String buildPreviewCss(String originalCss, List<IconEntry> icons) {
        StringBuilder preview = new StringBuilder(4096 + icons.size() * 55);
        // Keep only the BMC icon font (not the full styles bundle, which may
        // contain unrelated fonts, assets and thousands of layout rules).
        Matcher fonts = FONT_FACE_PATTERN.matcher(originalCss == null ? "" : originalCss);
        while (fonts.find()) {
            String face = fonts.group();
            String lower = face.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("dpl-icon-font") || lower.contains("dpl-iconfont")) {
                preview.append(face).append('\n');
            }
        }
        // Older and newer BMC versions use :before and/or ::before. The font
        // family is declared explicitly so the preview does not depend on
        // other unrelated global selectors from the original stylesheet.
        preview.append("[class^=\"d-icon-\"]:before,[class*=\" d-icon-\"]:before,")
               .append("[class^=\"d-icon-\"]::before,[class*=\" d-icon-\"]::before")
               .append("{font-family:dpl-icon-font!important;font-style:normal;font-weight:normal;")
               .append("font-variant:normal;text-transform:none;display:inline-block;}\n");
        for (IconEntry icon : icons) {
            preview.append('.').append(icon.getName()).append(":before{content:\"");
            preview.append('\\').append(icon.getCodePoint()).append("\";}\n");
        }
        return preview.toString();
    }

    private static String rewriteCssUrls(String css, String baseUrl) {
        if (css == null || css.length() == 0 || baseUrl == null || baseUrl.length() == 0) {
            return css == null ? "" : css;
        }
        Matcher matcher = CSS_URL_PATTERN.matcher(css);
        StringBuffer out = new StringBuffer(css.length() + 1024);
        while (matcher.find()) {
            String original = matcher.group(2).trim();
            String absolute = absoluteUrl(baseUrl, original);
            String dataUrl = tryReadFontAsDataUrl(absolute);
            String finalUrl = dataUrl.length() > 0 ? dataUrl : absolute;
            String replacement = "url(\"" + finalUrl.replace("\\", "\\\\").replace("\"", "\\\"") + "\")";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String tryReadFontAsDataUrl(String absoluteUrl) {
        if (absoluteUrl == null || absoluteUrl.length() == 0) {
            return "";
        }
        String lower = absoluteUrl.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.contains(".woff2") || lower.contains(".woff"))) {
            return "";
        }
        try (InputStream in = openBoundedConnection(new URL(absoluteUrl))) {
            byte[] bytes = readAllBytes(in, MAX_FONT_BYTES);
            String mime = lower.contains(".woff2") ? "font/woff2" : "font/woff";
            String encoded = Base64.getEncoder().encodeToString(bytes);
            Log.info("Embedded PWA icon font for preview: " + absoluteUrl + " (" + bytes.length + " bytes).");
            return "data:" + mime + ";base64," + encoded;
        } catch (Throwable t) {
            Log.warn("Could not embed PWA icon font from " + absoluteUrl + ": " + t.getMessage());
            return "";
        }
    }

    private static String absoluteUrl(String baseUrl, String value) {
        if (value == null || value.length() == 0) {
            return value == null ? "" : value;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("file:/") || lower.startsWith("data:") || lower.startsWith("about:")) {
            return value;
        }
        try {
            return new URL(new URL(baseUrl), value).toExternalForm();
        } catch (MalformedURLException e) {
            return value;
        }
    }

    private static String baseUrl(String cssUrl) {
        if (cssUrl == null || cssUrl.length() == 0) {
            return "";
        }
        try {
            URL url = new URL(cssUrl);
            String path = url.getPath();
            int slash = path.lastIndexOf('/');
            String basePath = slash >= 0 ? path.substring(0, slash + 1) : "/";
            return new URL(url.getProtocol(), url.getHost(), url.getPort(), basePath).toExternalForm();
        } catch (MalformedURLException e) {
            int slash = cssUrl.lastIndexOf('/');
            return slash >= 0 ? cssUrl.substring(0, slash + 1) : cssUrl;
        }
    }

    private static InputStream openBoundedConnection(URL url) throws IOException {
        String protocol = url.getProtocol();
        if (!("http".equalsIgnoreCase(protocol) || "https".equalsIgnoreCase(protocol))) {
            throw new IOException("Only HTTPS/HTTP icon CSS and font URLs are supported");
        }
        URLConnection connection = IconTlsSupport.open(url);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(true);
        try {
            return connection.getInputStream();
        } catch (IOException e) {
            throw new IOException(IconTlsSupport.describeHandshakeFailure(url, e), e);
        }
    }

    private static byte[] readAllBytes(InputStream in, int maxBytes) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            if (out.size() + read > maxBytes) {
                throw new IOException("PWA resource exceeds " + maxBytes + " byte safety limit");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static final class CssCatalog {
        final List<IconEntry> icons;
        final String rewrittenCss;

        CssCatalog(List<IconEntry> icons, String rewrittenCss) {
            this.icons = icons == null ? Collections.<IconEntry>emptyList() : icons;
            this.rewrittenCss = rewrittenCss == null ? "" : rewrittenCss;
        }
    }
}
