package se.yrell.developertools.midtier;

import java.io.IOException;
import java.io.StringReader;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Authenticated MidTier Configuration Tool calls. Uses Studio's connected AR credentials on demand.
 * No password/token in preferences; cookies live in RAM only, scoped by configured URL+AR server.
 */
public final class MidTierHttpClient {
    public record Credentials(String username, String password, int port, String authentication) { }
    public enum Outcome { CLEANED, ALREADY_EMPTY, ACCEPTED_UNVERIFIED }
    /** Signals that an SSO browser is needed; credentials have not been posted to an IdP. */
    public static final class BrowserAuthenticationRequired extends IOException {
        private static final long serialVersionUID = 1L;
        public BrowserAuthenticationRequired(String detail) { super(detail); }
    }
    public record Result(Outcome outcome, String message, int httpStatus) { }
    private static final Duration TIMEOUT = Duration.ofSeconds(25);
    private static final int MAX_BODY = 1024 * 1024;
    private final URI base;
    private final String server;
    private final URI allowedSsoBase;
    private final CookieManager cookies;
    private final HttpClient client;

    private MidTierHttpClient(String baseUrl, String server, String ssoBaseUrl) {
        this.base = URI.create(MidTierConfiguration.normalizeBaseUrl(baseUrl) + "/");
        this.server = MidTierConfiguration.normalizeServer(server);
        String allowed = MidTierConfiguration.normalizeSsoUrl(ssoBaseUrl);
        this.allowedSsoBase = allowed.isBlank() ? null : URI.create(allowed + "/");
        cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    public static MidTierHttpClient forTarget(MidTierConfiguration.Mapping mapping) {
        return new MidTierHttpClient(mapping.baseUrl(), mapping.server(), mapping.ssoBaseUrl());
    }

    public Result flush(int mode, String formName, boolean allowHttpCredentials,
            Supplier<Credentials> credentialsSupplier) throws IOException, InterruptedException {
        return flush(mode, formName, allowHttpCredentials, credentialsSupplier, false);
    }
    /** AUTO mode: hand off browser-only SSO before trying AR password fallback. */
    public Result flush(int mode, String formName, boolean allowHttpCredentials,
            Supplier<Credentials> credentialsSupplier, boolean browserFallback) throws IOException, InterruptedException {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Invalid cache flush mode.");
        if (mode == 0 && (formName == null || formName.isBlank())) {
            throw new IllegalArgumentException("A form name is required.");
        }
        // One operation at a time per MidTier / AR Server cookie jar.
        synchronized (this) {
            String pagePath = mode == 0 ? "shared/config/config_cache_cleanup.jsp" : "shared/config/config_cache.jsp";
            URI pageUri = base.resolve(pagePath);
            String formId = mode == 0 ? "cache_clenaup_form" : "flush_cache_form";
            HttpResponse<String> page = null;
            Parsed html = null;
            HtmlForm form = null;
            IOException initialSsoRedirect = null;
            try {
                page = request(pageUri, null);
                // A successful SSO round-trip MUST finish back in the configured MidTier.
                if (isMidTierUri(page.uri())) {
                    html = parse(page.body());
                    form = html.forms.get(formId);
                }
            } catch (SsoRedirectException e) {
                // The config JSP is usually exempt from the RSSO agent filter.
                // Try direct BMC Config Tool login before failing the operation.
                initialSsoRedirect = e;
            }
            if (browserFallback && (initialSsoRedirect != null
                    || (page != null && !isMidTierUri(page.uri())))) {
                throw new BrowserAuthenticationRequired("MidTier requires browser-based SSO. No AR credentials were sent to SSO.");
            }
            if (form == null || (html != null && isLogin(html))) {
                // Only read BMC Studio's password if there is not already a valid MidTier session.
                if ("http".equalsIgnoreCase(base.getScheme()) && !allowHttpCredentials) {
                    throw new IOException("Automatic login would transmit AR credentials over HTTP. Enable 'Allow HTTP credentials' for this mapping (test environments only) or use HTTPS.");
                }
                Credentials credentials = credentialsSupplier.get();
                if (credentials == null || credentials.username() == null || credentials.username().isBlank()
                        || credentials.password() == null || credentials.password().isEmpty()) {
                    throw new IOException("Developer Studio did not provide a reusable AR password. No flush was sent.");
                }
                try {
                    authenticate(credentials);
                    page = request(pageUri, null);
                } catch (SsoRedirectException e) {
                    if (browserFallback) throw new BrowserAuthenticationRequired("MidTier requires browser-based SSO after AR login.");
                    throw new IOException("MidTier still requires SSO authentication after the BMC login attempt. "
                            + e.getMessage() + " No flush was sent.", e);
                }
                if (!isMidTierUri(page.uri())) {
                    if (browserFallback) throw new BrowserAuthenticationRequired("SSO needs an interactive browser session.");
                    throw new IOException("The SSO journey did not return to MidTier. "
                            + "Interactive SSO or browser-only sessions cannot be completed by Java HttpClient. No flush was sent.");
                }
                html = parse(page.body());
                form = html.forms.get(formId);
            }
            if (!isSuccessful(page) || form == null || isLogin(html)) {
                String reason = initialSsoRedirect == null ? "" : " First redirect: " + initialSsoRedirect.getMessage();
                throw new IOException("MidTier Configuration Tool did not provide its authenticated cache form (HTTP "
                        + page.statusCode() + "). No flush was sent." + reason);
            }
            if (form.fields.getOrDefault("cfgStok", "").isBlank()) {
                throw new IOException("MidTier cfgStok is missing. No flush was sent.");
            }
            if (mode == 0 && !form.servers.contains(server)) {
                throw new IOException("AR Server '" + server + "' is missing from MidTier's server list. No flush was sent.");
            }
            URI action = resolveFormAction(page.uri(), form.action,
                    "servlet/GoatConfigServlet", "Cache", mode == 0 ? "form_cleanup" : null);
            Map<String, String> fields = new LinkedHashMap<>(form.fields);
            if (mode == 0) {
                fields.put("form_flush", "Yes");
                fields.put("server", server);
                fields.put("form_name", formName);
            } else {
                fields.put("cache_flush", mode == 1 ? "Yes" : "No");
                fields.put("cache_flush_allpods", mode == 2 ? "Yes" : "No");
            }
            HttpResponse<String> response = request(action, encode(fields));
            Parsed result = parse(response.body());
            if (!isSuccessful(response) || isLogin(result)) {
                throw new IOException("MidTier rejected the flush or session expired (HTTP " + response.statusCode() + "). No success is assumed.");
            }
            String feedback = String.join(" ", result.statusTexts).replaceAll("\\s+", " ").trim();
            if (mode == 0) {
                String prefix = "Form " + formName + " for server " + server;
                if (Pattern.compile(Pattern.quote(prefix) + "\\s+is cleaned\\s*!", Pattern.CASE_INSENSITIVE)
                        .matcher(feedback).find()) return new Result(Outcome.CLEANED, feedback, response.statusCode());
                if (Pattern.compile(Pattern.quote(prefix) + "\\s+doesn['’]?t exists? in cache", Pattern.CASE_INSENSITIVE)
                        .matcher(feedback).find()) return new Result(Outcome.ALREADY_EMPTY, feedback, response.statusCode());
            }
            // HTTP 200 is not sufficient evidence of a completed flush.
            return new Result(Outcome.ACCEPTED_UNVERIFIED, feedback.isBlank() ? "No recognized MidTier result message." : feedback,
                    response.statusCode());
        }
    }

    private void authenticate(Credentials credentials) throws IOException, InterruptedException {
        HttpResponse<String> loginPage = request(base.resolve("shared/config/config.jsp"), null);
        if (!isMidTierUri(loginPage.uri())) {
            throw new IOException("MidTier Configuration Tool login redirected to SSO without returning to MidTier. "
                    + "A browser-based login may be required. No credentials were posted.");
        }
        Parsed login = parse(loginPage.body());
        HtmlForm loginForm = login.forms.get("loginForm");
        if (!isSuccessful(loginPage) || loginForm == null) {
            throw new IOException("Unable to read the MidTier Configuration Tool login form.");
        }
        URI action = resolveFormAction(loginPage.uri(), loginForm.action, "servlet/ConfigServlet", "logon", null);
        Map<String, String> fields = new LinkedHashMap<>(loginForm.fields);
        fields.put("username", credentials.username());
        fields.put("pwd", credentials.password());
        fields.put("servername", server);
        if (credentials.port() > 0) fields.put("serverport", Integer.toString(credentials.port()));
        if (fields.containsKey("auth") && credentials.authentication() != null) fields.put("auth", credentials.authentication());
        fields.put("timezone", ZoneId.systemDefault().getId());
        fields.put("isFromCfgTool", "true");
        HttpResponse<String> loggedIn = request(action, encode(fields));
        if (!isSuccessful(loggedIn)) {
            throw new IOException("MidTier login failed (HTTP " + loggedIn.statusCode() + "). No flush was sent.");
        }
        // The authenticated cache page is verified by flush(), not by login HTTP 200.
    }

    private HttpResponse<String> request(URI target, String postData) throws IOException, InterruptedException {
        URI current = target;
        String body = postData;
        for (int redirects = 0; redirects <= 12; redirects++) {
            verifyNavigable(current, body != null);
            HttpRequest.Builder req = HttpRequest.newBuilder(current).timeout(TIMEOUT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("User-Agent", "Mozilla/5.0 (compatible; YrellDeveloperTools/0.1.55; Java/17)");
            if (body != null) {
                String path = current.getPath().endsWith("/ConfigServlet")
                        ? "shared/config/config.jsp"
                        : current.getRawQuery() != null && current.getRawQuery().contains("op_type=form_cleanup")
                        ? "shared/config/config_cache_cleanup.jsp" : "shared/config/config_cache.jsp";
                req.header("Referer", base.resolve(path).toASCIIString());
            }
            if (body == null) req.GET();
            else req.header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            HttpResponse<String> response = client.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.body().length() > MAX_BODY) throw new IOException("MidTier response exceeded safety limit.");
            int code = response.statusCode();
            if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) return response;
            String location = response.headers().firstValue("location").orElseThrow(() -> new IOException("Redirect without Location."));
            URI next;
            try { next = current.resolve(location); }
            catch (IllegalArgumentException e) { throw new IOException("Invalid MidTier redirect URL.", e); }
            // Never send any POST body (credentials or cfgStok) through a redirect.
            if (body != null && (code == 307 || code == 308)) {
                throw new IOException("MidTier requested a POST-preserving redirect (HTTP " + code
                        + "). Credentials and cfgStok were not forwarded.");
            }
            verifyNavigable(next, false);
            current = next;
            body = null;
        }
        throw new IOException("Too many MidTier redirects.");
    }
    private boolean isMidTierUri(URI uri) {
        return sameOrigin(base, uri) && pathWithin(uri.getPath(), base.getPath());
    }
    private void verifyOriginAndPath(URI uri) throws IOException {
        if (!isMidTierUri(uri)) {
            throw new IOException("MidTier form action is outside its configured context. No POST was sent.");
        }
    }
    private void verifyNavigable(URI uri, boolean isPost) throws IOException {
        if (uri.getUserInfo() != null || uri.getRawFragment() != null
                || uri.getHost() == null || !("https".equalsIgnoreCase(uri.getScheme())
                || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new SsoRedirectException("Invalid or untrusted redirect target. No credentials were sent.");
        }
        if (isMidTierUri(uri)) return;
        // Only GETs may follow an SSO redirect. Never forward the AR password
        // or cfgStok outside the configured /arsys context.
        if (!isPost && isApprovedSsoUrl(uri)) return;
        String safeTarget = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
        throw new SsoRedirectException("Redirect to " + safeTarget + " is not allowed. "
                + "If this is your RSSO/SSO endpoint, configure its exact HTTPS context in the server mapping. "
                + "Only GET requests can follow SSO redirects; no credentials were forwarded.");
    }
    private boolean isApprovedSsoUrl(URI uri) {
        if (sameOrigin(base, uri) && (pathWithin(uri.getPath(), "/rsso/")
                || pathWithin(uri.getPath(), "/hsso/"))) return true;
        return allowedSsoBase != null && "https".equalsIgnoreCase(uri.getScheme())
                && sameOrigin(allowedSsoBase, uri) && pathWithin(uri.getPath(), allowedSsoBase.getPath());
    }
    private static boolean pathWithin(String path, String context) {
        if (path == null || context == null) return false;
        String root = context.endsWith("/") ? context.substring(0, context.length() - 1) : context;
        return path.equals(root) || path.startsWith(root + "/");
    }
    private static final class SsoRedirectException extends IOException {
        private static final long serialVersionUID = 1L;
        SsoRedirectException(String message) { super(message); }
    }
    private static boolean sameOrigin(URI a, URI b) {
        return a.getScheme().equalsIgnoreCase(b.getScheme()) && a.getHost().equalsIgnoreCase(b.getHost())
                && effectivePort(a) == effectivePort(b);
    }
    private static int effectivePort(URI u) {
        return u.getPort() != -1 ? u.getPort() : ("https".equalsIgnoreCase(u.getScheme()) ? 443 : 80);
    }
    private URI resolveFormAction(URI document, String rawAction, String expectedPath, String actionValue, String opType)
            throws IOException {
        if (rawAction == null || rawAction.isBlank()) throw new IOException("No form action supplied by MidTier.");
        URI action = document.resolve(rawAction.replace("&amp;", "&"));
        verifyOriginAndPath(action);
        if (!action.getPath().equals(base.resolve(expectedPath).getPath())) throw new IOException("Unexpected MidTier form endpoint.");
        String query = action.getRawQuery() == null ? "" : action.getRawQuery();
        Map<String, String> params = new LinkedHashMap<>();
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0) {
                if (params.putIfAbsent(kv.substring(0, i), kv.substring(i + 1)) != null)
                    throw new IOException("Ambiguous MidTier action query.");
            }
        }
        if (!actionValue.equals(params.get("action")) ||
                (opType != null && !opType.equals(params.get("op_type")))) {
            throw new IOException("Unexpected MidTier form action parameters.");
        }
        return action;
    }
    private static boolean isSuccessful(HttpResponse<?> response) { return response.statusCode() >= 200 && response.statusCode() < 300; }
    private static boolean isLogin(Parsed page) { return page.forms.containsKey("loginForm"); }
    private static String encode(Map<String, String> fields) {
        StringBuilder result = new StringBuilder();
        fields.forEach((key, value) -> {
            if (result.length() > 0) result.append('&');
            result.append(URLEncoder.encode(key, StandardCharsets.UTF_8)).append('=')
                    .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
        });
        return result.toString();
    }

    static Parsed parse(String html) throws IOException {
        Parsed page = new Parsed();
        HTMLEditorKit.ParserCallback callback = new HTMLEditorKit.ParserCallback() {
            HtmlForm current;
            String selected;
            boolean inStatus;
            StringBuilder status;
            private String attr(MutableAttributeSet a, HTML.Attribute key) {
                Object value = a.getAttribute(key);
                return value == null ? null : value.toString();
            }
            private void start(HTML.Tag tag, MutableAttributeSet attributes) {
                if (tag == HTML.Tag.FORM) {
                    String name = attr(attributes, HTML.Attribute.NAME);
                    current = new HtmlForm();
                    current.action = attr(attributes, HTML.Attribute.ACTION);
                    if (name != null) page.forms.put(name, current);
                } else if (current != null && tag == HTML.Tag.INPUT) {
                    String name = attr(attributes, HTML.Attribute.NAME);
                    if (name != null) current.fields.put(name, attr(attributes, HTML.Attribute.VALUE) == null ? "" : attr(attributes, HTML.Attribute.VALUE));
                } else if (current != null && tag == HTML.Tag.SELECT) {
                    selected = attr(attributes, HTML.Attribute.NAME);
                } else if (current != null && tag == HTML.Tag.OPTION && "server".equals(selected)) {
                    String serverOption = attr(attributes, HTML.Attribute.VALUE);
                    if (serverOption != null && !serverOption.isBlank()) current.servers.add(serverOption);
                } else if (tag == HTML.Tag.TD || tag == HTML.Tag.DIV) {
                    String clazz = attr(attributes, HTML.Attribute.CLASS);
                    if (clazz != null && Pattern.compile("(^|\\s)status(\\s|$)", Pattern.CASE_INSENSITIVE).matcher(clazz).find()) {
                        inStatus = true;
                        status = new StringBuilder();
                    }
                }
            }
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int position) { start(tag, attrs); }
            @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attrs, int position) { start(tag, attrs); }
            @Override public void handleText(char[] text, int position) { if (inStatus) status.append(text); }
            @Override public void handleEndTag(HTML.Tag tag, int position) {
                if (tag == HTML.Tag.FORM) current = null;
                if (tag == HTML.Tag.SELECT) selected = null;
                if (inStatus && (tag == HTML.Tag.TD || tag == HTML.Tag.DIV)) {
                    if (status != null && !status.toString().isBlank()) page.statusTexts.add(status.toString().trim());
                    inStatus = false;
                }
            }
        };
        new ParserDelegator().parse(new StringReader(html), callback, true);
        return page;
    }
    static final class Parsed {
        final Map<String, HtmlForm> forms = new LinkedHashMap<>();
        final List<String> statusTexts = new ArrayList<>();
    }
    static final class HtmlForm {
        String action;
        final Map<String, String> fields = new LinkedHashMap<>();
        final Set<String> servers = new LinkedHashSet<>();
    }
}
