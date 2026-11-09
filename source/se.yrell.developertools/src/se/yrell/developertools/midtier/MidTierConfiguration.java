package se.yrell.developertools.midtier;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Non-secret, per-AR-server Mid Tier mapping. No credentials or cfgStok are stored. */
public final class MidTierConfiguration {
    private MidTierConfiguration() { }

    public record Mapping(String server, String baseUrl) { }

    /** Accept a Mid Tier context root or an existing Configuration Tool page URL. */
    public static String normalizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("MidTier URL is required.");
        try {
            URI u = new URI(raw.trim());
            String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || u.getHost() == null
                    || u.getUserInfo() != null || u.getRawQuery() != null || u.getRawFragment() != null
                    || u.getPort() > 65535 || u.getPort() < -1) {
                throw new IllegalArgumentException("Specify an http(s) MidTier URL without username, query or fragment.");
            }
            String path = u.getPath() == null ? "" : u.getPath();
            // Allow normal paths without path traversal or ambiguous encodings.
            if (path.contains("..") || u.getRawPath().contains("%")) {
                throw new IllegalArgumentException("URL path cannot contain '..' or encoded characters.");
            }
            String marker = "/shared/config/";
            int idx = path.indexOf(marker);
            if (idx >= 0) path = path.substring(0, idx);
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.isEmpty()) path = "/arsys";
            if (path.endsWith(".jsp") || path.endsWith("/servlet/GoatConfigServlet")) {
                throw new IllegalArgumentException("Use the MidTier context URL, e.g. https://host/arsys.");
            }
            return new URI(scheme, null, u.getHost().toLowerCase(Locale.ROOT), u.getPort(), path, null, null).toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid MidTier URL.", e);
        }
    }

    public static String normalizeServer(String server) {
        String result = server == null ? "" : server.trim();
        if (result.isEmpty() || result.length() > 255 || result.indexOf('\n') >= 0 || result.indexOf('\r') >= 0
                || result.indexOf('\t') >= 0) {
            throw new IllegalArgumentException("Choose a valid AR Server name.");
        }
        return result;
    }

    public static String pageUrl(Mapping mapping, boolean formCleanup) {
        return normalizeBaseUrl(mapping.baseUrl()) + (formCleanup
                ? "/shared/config/config_cache_cleanup.jsp" : "/shared/config/config_cache.jsp");
    }

    public static boolean isExpectedPage(String actualUrl, Mapping mapping, boolean formCleanup) {
        try {
            URI actual = new URI(actualUrl);
            URI expected = new URI(pageUrl(mapping, formCleanup));
            return actual.getScheme().equalsIgnoreCase(expected.getScheme())
                    && actual.getHost().equalsIgnoreCase(expected.getHost())
                    && actual.getPort() == expected.getPort()
                    && actual.getPath().equals(expected.getPath())
                    && actual.getRawQuery() == null && actual.getRawFragment() == null;
        } catch (Exception e) { return false; }
    }

    public static Mapping lookup(Collection<Mapping> entries, String server) {
        if (server == null || entries == null) return null;
        for (Mapping e : entries) if (e.server().equalsIgnoreCase(server.trim())) return e;
        return null;
    }

    /** Stable, versioned plain-string storage with Base64URL fields to avoid delimiters. */
    public static String encode(Collection<Mapping> entries) {
        StringBuilder result = new StringBuilder("v1\n");
        Map<String, Mapping> unique = new LinkedHashMap<>();
        for (Mapping entry : entries) {
            String server = normalizeServer(entry.server());
            String baseUrl = normalizeBaseUrl(entry.baseUrl());
            String key = server.toLowerCase(Locale.ROOT);
            if (unique.containsKey(key)) throw new IllegalArgumentException("Duplicate AR Server: " + server);
            unique.put(key, new Mapping(server, baseUrl));
        }
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        for (Mapping entry : unique.values()) {
            result.append(encoder.encodeToString(entry.server().getBytes(StandardCharsets.UTF_8))).append(':')
                    .append(encoder.encodeToString(entry.baseUrl().getBytes(StandardCharsets.UTF_8))).append('\n');
        }
        return result.toString();
    }

    public static List<Mapping> decode(String text) {
        List<Mapping> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        String[] lines = text.split("\\r?\\n");
        if (lines.length < 1 || !"v1".equals(lines[0])) return result;
        Base64.Decoder decoder = Base64.getUrlDecoder();
        for (int i = 1; i < lines.length; i++) {
            int div = lines[i].indexOf(':');
            if (div < 0) continue;
            try {
                String server = normalizeServer(new String(decoder.decode(lines[i].substring(0, div)), StandardCharsets.UTF_8));
                String url = normalizeBaseUrl(new String(decoder.decode(lines[i].substring(div + 1)), StandardCharsets.UTF_8));
                if (lookup(result, server) == null) result.add(new Mapping(server, url));
            } catch (IllegalArgumentException ignored) { /* Skip corrupted row, never invent a target. */ }
        }
        return result;
    }
}
