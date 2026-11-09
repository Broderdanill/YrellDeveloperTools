package se.yrell.developertools.midtier;

import java.net.URI;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.LocationAdapter;
import org.eclipse.swt.browser.LocationEvent;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.widgets.Composite;

/** SWT's real browser engine handles RSSO redirects, scripts and form auto-POSTs.
 * The ONLY scripted POST happens when the top-level browser is at the exact
 * configured MidTier cache JSP and its real form action + cfgStok are validated.
 * IdP pages are not inspected or instrumented. No AR credentials are used here.
 */
public final class MidTierSsoBrowser {
    private final Browser browser;
    private final MidTierConfiguration.Mapping target;
    private final URI base;
    private final URI page;
    private final int mode;
    private final String formName;
    private final Consumer<String> status;
    private final Consumer<MidTierHttpClient.Result> completed;
    private final Consumer<String> failed;
    private boolean submitted;
    private boolean finished;
    private int returnAttempts;

    public MidTierSsoBrowser(Composite parent, MidTierConfiguration.Mapping target,
            int mode, String formName, Consumer<String> status,
            Consumer<MidTierHttpClient.Result> completed, Consumer<String> failed) {
        this.target = target;
        this.base = URI.create(MidTierConfiguration.normalizeBaseUrl(target.baseUrl()) + "/");
        this.page = URI.create(MidTierConfiguration.pageUrl(target, mode == 0));
        this.mode = mode;
        this.formName = formName;
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
                // Login providers may be on unrelated HTTPS origins. Browser manages
                // their normal redirects, but never permit a third-party HTTP downgrade.
                try {
                    URI next = URI.create(e.location);
                    String scheme = next.getScheme();
                    if (scheme == null || "about".equalsIgnoreCase(scheme)) return;
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
        status.accept("Opening MidTier in embedded browser. Complete RSSO login if prompted...");
        if (!browser.setUrl(page.toASCIIString())) fail("Unable to open MidTier cache page in SWT browser.");
    }

    private void onLoaded() {
        if (browser.isDisposed() || finished) return;
        String current = browser.getUrl();
        try {
            URI uri = URI.create(current);
            if (!sameOrigin(base, uri) || !inside(uri.getPath(), base.getPath())) {
                status.accept("Waiting for RSSO/identity provider sign-in. Use the browser pane if prompted.");
                return; // never evaluate JS on identity-provider pages
            }
            if (submitted) {
                String message = evaluate(MidTierBrowserScript.resultProbe());
                if ("LOGIN_REQUIRED".equals(message)) {
                    fail("MidTier rejected or expired the browser session after the request. Result unknown.");
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
            if (samePage(uri, page)) {
                String check = evaluate(MidTierBrowserScript.preflight(target.baseUrl(), mode, target.server()));
                if ("LOGIN_REQUIRED".equals(check)) {
                    status.accept("Log in to MidTier Configuration Tool in the browser pane.");
                } else if (!"READY".equals(check)) {
                    fail("MidTier browser preflight failed: " + check + ". No flush was sent.");
                } else {
                    // A form-specific flush needs no second confirmation.
                    submitted = true;
                    status.accept("Session verified. Submitting cache flush...");
                    if (!browser.execute(MidTierBrowserScript.submit(target.baseUrl(), mode, target.server(), formName))) {
                        submitted = false;
                        fail("Unable to submit MidTier browser form. No flush was sent.");
                    }
                }
            } else {
                // Common RSSO outcome: login returns to Configuration Tool overview.
                // Only retry the target cache page on the configured MidTier origin.
                String loginForm = evaluate("(function(){return document.forms.namedItem('loginForm')?'YES':'NO';})()");
                if ("YES".equals(loginForm)) {
                    status.accept("Complete MidTier Configuration Tool login in the browser pane.");
                } else if (returnAttempts++ < 2) {
                    status.accept("SSO returned to MidTier. Opening the cache page...");
                    browser.setUrl(page.toASCIIString());
                } else {
                    fail("SSO did not return to the authenticated cache page after two attempts.");
                }
            }
        } catch (Exception e) {
            fail("Unable to verify the browser's MidTier page: " + e.getClass().getSimpleName());
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
