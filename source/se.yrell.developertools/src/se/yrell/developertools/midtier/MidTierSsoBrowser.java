package se.yrell.developertools.midtier;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.widgets.Composite;

import se.yrell.developertools.Log;

/**
 * Browser-based RSSO/Configuration Tool flow.
 *
 * A valid RSSO session is NOT by itself an authorized MidTier Configuration
 * Tool session. Handle the latter's own login/authorization page before trying
 * the cache JSP. Never send passwords or cfgStok to an identity provider.
 */
public final class MidTierSsoBrowser {
    private final Browser browser;
    private final MidTierConfiguration.Mapping target;
    private final URI base;
    private final URI page;
    private final URI configLogin;
    private final int mode;
    private final String formName;
    private final Supplier<MidTierHttpClient.Credentials> credentialsSupplier;
    private final Consumer<String> status;
    private final Consumer<MidTierHttpClient.Result> completed;
    private final Consumer<String> failed;
    private boolean submitted;
    private boolean finished;
    private boolean loginPosted;
    private boolean redirectedToConfigLogin;
    private int cachePageRetries;

    public MidTierSsoBrowser(Composite parent, MidTierConfiguration.Mapping target,
            int mode, String formName, Supplier<MidTierHttpClient.Credentials> credentialsSupplier,
            Consumer<String> status, Consumer<MidTierHttpClient.Result> completed, Consumer<String> failed) {
        this.target = target;
        this.base = URI.create(MidTierConfiguration.normalizeBaseUrl(target.baseUrl()) + "/");
        this.page = URI.create(MidTierConfiguration.pageUrl(target, mode == 0));
        this.configLogin = base.resolve("shared/config/config.jsp");
        this.mode = mode;
        this.formName = formName;
        this.credentialsSupplier = credentialsSupplier;
        this.status = status;
        this.completed = completed;
        this.failed = failed;
        try {
            this.browser = new Browser(parent, SWT.NONE);
        } catch (SWTError e) {
            throw new IllegalStateException("An embedded SWT browser is not available. "
                    + "Check the Windows WebView2/browser runtime.", e);
        }
        browser.addLocationListener(new LocationAdapter() {
            @Override public void changing(LocationEvent e) {
                if (!e.top || finished) return;
                try {
                    URI next = URI.create(e.location);
                    String scheme = next.getScheme();
                    if (scheme == null || "about".equalsIgnoreCase(scheme)) return;
                    // Browser may follow normal external HTTPS SSO/IdP navigation.
                    // Never allow an external HTTP downgrade.
                    boolean secure = "https".equalsIgnoreCase(scheme);
                    if (!secure && !("http".equalsIgnoreCase(scheme)
                            && "http".equalsIgnoreCase(base.getScheme())
                            && sameOrigin(base, next) && inside(next.getPath(), base.getPath()))) {
                        e.doit = false;
                        fail("SSO tried to navigate to a non-HTTPS/untrusted URL. No flush was sent.");
                    }
                } catch (RuntimeException ex) {
                    e.doit = false;
                    fail("SSO supplied an invalid browser URL. No flush was sent.");
                }
            }
        });
        browser.addProgressListener(new ProgressAdapter() {
            @Override public void completed(ProgressEvent event) { onLoaded(); }
        });
    }

    public Browser control() { return browser; }

    public void start() {
        status.accept("Opening MidTier. Complete RSSO/Configuration Tool login if prompted...");
        if (!browser.setUrl(page.toASCIIString())) fail("Unable to open MidTier cache page in SWT browser.");
    }

    private void onLoaded() {
        if (browser.isDisposed() || finished) return;
        try {
            URI uri = URI.create(browser.getUrl());
            if (!sameOrigin(base, uri) || !inside(uri.getPath(), base.getPath())) {
                status.accept("Waiting for RSSO/identity provider sign-in. Use the browser pane if prompted.");
                return; // Never read or execute scripts on SSO/IdP pages.
            }

            // The servlet can return an authorization error with HTTP 200.
            // Detect it before attempting to submit (or report) a cache flush.
            String state = evaluate(MidTierBrowserScript.pageState());
            if (submitted) {
                if ("CONFIG_LOGIN".equals(state) || "CONFIG_DENIED".equals(state)) {
                    fail("MidTier Configuration Tool rejected the flush session. Result unknown; no success assumed.");
                    return;
                }
                String message = evaluate(MidTierBrowserScript.resultProbe());
                if ("LOGIN_REQUIRED".equals(message) || "CONFIG_DENIED".equals(message)) {
                    fail("MidTier Configuration Tool authorization was not valid after the request. Result unknown.");
                    return;
                }
                String prefix = "Form " + formName + " for server " + target.server();
                if (mode == 0 && Pattern.compile(Pattern.quote(prefix) + "\\s+is cleaned\\s*!", Pattern.CASE_INSENSITIVE)
                        .matcher(message).find()) {
                    success(MidTierHttpClient.Outcome.CLEANED, message);
                } else if (mode == 0 && Pattern.compile(Pattern.quote(prefix)
                        + "\\s+doesn['’]?t exists? in cache", Pattern.CASE_INSENSITIVE).matcher(message).find()) {
                    success(MidTierHttpClient.Outcome.ALREADY_EMPTY, message);
                } else {
                    success(MidTierHttpClient.Outcome.ACCEPTED_UNVERIFIED,
                            message.isBlank() ? "No recognizable MidTier result message." : message);
                }
                return;
            }

            if ("CONFIG_DENIED".equals(state)) {
                if (!redirectedToConfigLogin) {
                    redirectedToConfigLogin = true;
                    status.accept("RSSO completed, but Configuration Tool needs a separate login. Opening its login page...");
                    browser.setUrl(configLogin.toASCIIString());
                } else {
                    // Keep the browser visible so that the user can follow BMC's
                    // Configuration Tool link / troubleshoot their administrator role.
                    status.accept("Configuration Tool still denies access after login. Use its Configuration Tool link "
                            + "to sign in as an AR administrator; cache flush has NOT been sent.");
                }
                return;
            }
            if ("CONFIG_LOGIN".equals(state)) {
                handleConfigLogin(uri);
                return;
            }
            if (samePage(uri, page)) {
                String check = evaluate(MidTierBrowserScript.preflight(target.baseUrl(), mode, target.server()));
                if ("READY".equals(check)) {
                    submitted = true;
                    status.accept("Configuration Tool session verified. Submitting cache flush...");
                    if (!browser.execute(MidTierBrowserScript.submit(target.baseUrl(), mode, target.server(), formName))) {
                        submitted = false;
                        fail("Unable to submit MidTier browser form. No flush was sent.");
                    }
                } else if ("LOGIN_REQUIRED".equals(check) || "CONFIG_DENIED".equals(check)) {
                    // Covers pages that return the login document at the cache URL.
                    handleConfigLogin(uri);
                } else {
                    status.accept("MidTier cache page unavailable (" + check + "). "
                            + "Complete Configuration Tool login in the browser pane; no flush was sent.");
                }
                return;
            }

            // BMC may return to Configuration Tool overview after successful login.
            // Only navigate to the cache page on our configured origin.
            if (cachePageRetries++ < 3) {
                status.accept("MidTier Configuration Tool loaded. Opening cache page...");
                browser.setUrl(page.toASCIIString());
            } else {
                status.accept("Configuration Tool did not open its authenticated cache page. "
                        + "Use the browser pane to log in as an AR administrator. No flush was sent.");
            }
        } catch (Exception e) {
            Log.error("MidTier browser Configuration Tool flow failed", e);
            fail("Unable to verify the browser's MidTier page: " + e.getClass().getSimpleName());
        }
    }

    private void handleConfigLogin(URI uri) {
        // A login document at a cache URL is handled by opening the known login
        // JSP instead of submitting credentials to an unexpected page.
        if (!samePage(uri, configLogin)) {
            if (!redirectedToConfigLogin) {
                redirectedToConfigLogin = true;
                browser.setUrl(configLogin.toASCIIString());
            } else {
                status.accept("Configuration Tool login required. Use its login page in the browser pane.");
            }
            return;
        }
        String check = evaluate(MidTierBrowserScript.configLoginCheck(target.baseUrl()));
        if (!"READY".equals(check)) {
            status.accept("Configuration Tool login form could not be verified (" + check
                    + "). Sign in manually; no credentials were submitted by the plugin.");
            return;
        }
        if (loginPosted) {
            status.accept("Automatic Configuration Tool login did not complete. "
                    + "Use the displayed BMC login form to sign in as an administrator.");
            return;
        }
        if (credentialsSupplier == null) {
            status.accept("Complete the separate Configuration Tool administrator login in the browser pane.");
            return;
        }
        if (!"https".equalsIgnoreCase(base.getScheme()) && !target.allowHttpCredentials()) {
            status.accept("Automatic Configuration Tool login requires HTTPS. "
                    + "Use the login form manually or allow HTTP credentials for this test server.");
            return;
        }
        try {
            MidTierHttpClient.Credentials credentials = credentialsSupplier.get();
            if (credentials == null || credentials.username().isBlank() || credentials.password().isEmpty()) {
                status.accept("No reusable Studio password. Sign in to Configuration Tool manually.");
                return;
            }
            loginPosted = true;
            status.accept("Submitting Developer Studio credentials to MidTier Configuration Tool...");
            // Only execute on exact config.jsp, after checking BMC's local
            // ConfigServlet form action. No password is sent to any SSO URL.
            if (!browser.execute(MidTierBrowserScript.submitConfigLogin(target.baseUrl(),
                    target.server(), credentials.username(), credentials.password()))) {
                loginPosted = false;
                status.accept("Automatic Configuration Tool login could not be submitted. "
                        + "Sign in using the browser pane.");
            }
        } catch (RuntimeException e) {
            // Absence of Studio password is normal for some RSSO connections.
            status.accept("Automatic Configuration Tool login unavailable. "
                    + "Sign in manually as an AR administrator in the browser pane.");
            Log.info("MidTier Configuration Tool requires manual login for the current Studio session.");
        }
    }

    private String evaluate(String script) {
        Object value = browser.evaluate("return " + script + ";");
        return value == null ? "" : value.toString();
    }
    private void success(MidTierHttpClient.Outcome outcome, String message) {
        finished = true;
        completed.accept(new MidTierHttpClient.Result(outcome, message, 200));
    }
    private void fail(String error) {
        if (finished) return;
        finished = true;
        failed.accept(error);
    }
    private static boolean samePage(URI a, URI b) {
        return sameOrigin(a, b) && a.getPath().equals(b.getPath())
                && a.getRawQuery() == null && a.getRawFragment() == null;
    }
    private static boolean sameOrigin(URI a, URI b) {
        return a.getHost() != null && b.getHost() != null
                && a.getScheme().equalsIgnoreCase(b.getScheme())
                && a.getHost().equalsIgnoreCase(b.getHost())
                && effectivePort(a) == effectivePort(b);
    }
    private static int effectivePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
    }
    private static boolean inside(String path, String root) {
        String prefix = root.endsWith("/") ? root.substring(0, root.length() - 1) : root;
        return path != null && (path.equals(prefix) || path.startsWith(prefix + "/"));
    }
}
