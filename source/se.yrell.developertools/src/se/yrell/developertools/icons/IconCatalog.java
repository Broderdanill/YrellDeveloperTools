package se.yrell.developertools.icons;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
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
    private static final Pattern CSS_ICON_PATTERN = Pattern.compile(
            "\\.d-icon-(?!left-|right-)([A-Za-z0-9_]+):before[^\\{]*\\{\\s*content\\s*:\\s*\"\\\\([0-9a-fA-F]{4,6})\"",
            Pattern.DOTALL);
    private static final Pattern CSS_URL_PATTERN = Pattern.compile("url\\((['\\\"]?)([^)'\\\"]+)(['\\\"]?)\\)");

    private static volatile List<IconEntry> cache;
    private static volatile String cacheKey;
    private static volatile String activeCssUrl;
    private static volatile String activeCssBaseUrl;
    private static volatile String activeBrowserCss;
    private static volatile String activeStatus = "No CSS URL configured";

    private IconCatalog() {
    }

    public static List<IconEntry> getIcons() {
        String cssUrl = normalizeCssUrl(ToolsPreferences.getPwaIconCatalogUrl());
        String key = cssUrl.length() == 0 ? "none" : cssUrl;
        List<IconEntry> local = cache;
        if (local != null && key.equals(cacheKey)) {
            return local;
        }
        synchronized (IconCatalog.class) {
            local = cache;
            if (local != null && key.equals(cacheKey)) {
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
                    activeStatus = "Loaded " + loaded.size() + " icons from " + cssUrl;
                    Log.info("Loaded PWA icon catalog from styles.css URL with " + loaded.size() + " icons: " + cssUrl);
                } catch (IOException e) {
                    activeStatus = "Could not load styles.css: " + e.getMessage();
                    Log.warn("Could not load PWA icon catalog from styles.css URL " + cssUrl + ": " + e.getMessage());
                    loaded = Collections.emptyList();
                }
            } else {
                Log.warn("PWA Icon Helper has no CSS URL configured. Configure a direct BMC PWA styles.*.css URL in Yrell Developer Tools preferences.");
            }

            cache = Collections.unmodifiableList(loaded);
            cacheKey = key;
            return cache;
        }
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
        try (InputStream in = url.openStream()) {
            bytes = readAllBytes(in);
        }
        String css = new String(bytes, StandardCharsets.UTF_8);
        String base = baseUrl(cssUrl);
        String rewritten = rewriteCssUrls(css, base);
        return new CssCatalog(readCssCatalogText(css), rewritten);
    }

    private static List<IconEntry> readCssCatalogText(String css) {
        Map<String, IconEntry> entries = new LinkedHashMap<String, IconEntry>();
        Matcher matcher = CSS_ICON_PATTERN.matcher(css == null ? "" : css);
        while (matcher.find()) {
            String shortName = matcher.group(1);
            String codePoint = matcher.group(2);
            String name = "d-icon-" + shortName;
            if (!entries.containsKey(name)) {
                entries.put(name, new IconEntry(name, "", -1, -1, -1, codePoint));
            }
        }
        return new ArrayList<IconEntry>(entries.values());
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
        try (InputStream in = new URL(absoluteUrl).openStream()) {
            byte[] bytes = readAllBytes(in);
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

    private static byte[] readAllBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
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
