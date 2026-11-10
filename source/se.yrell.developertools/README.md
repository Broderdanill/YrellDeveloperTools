# Yrell Developer Tools 0.1.66 — Message Suppressor diagnostics

This version leaves suppression decisions and save handling **unchanged** from 0.1.65.
A user reported `form-success source=3 display=3 action=SHOW` even though the
save-warning dialog was empty. Code `3` means at least one marker appears
unsuppressed; it is **not** the marker count. We must identify the actual
codes/severities before altering the decision to avoid silently hiding real errors.

When Message Suppressor is enabled, the diagnostic log now includes
`form-success-details` showing counts and numeric codes plus severity of
both the `IInfoResource` markers and the `IInfoContext` markers. A separate
`presentation` line shows the marker state when `Info.showProblems()` runs,
including how many remain after filtering. Only codes and severity are logged;
no form names, message strings, server names, URLs or authentication data.

Log location on Windows:
`%USERPROFILE%\.yrell-developertools\message-suppression-diagnostics.log`.
Run one save that produces the empty popup, then share only the latest
`form-success`, `form-success-details` and `presentation` log lines. This
version is a **diagnostic build**, not a claim that the popup is fixed.

---

## 0.1.66 — Message Suppressor: empty Problems dialog fix

When suppression is enabled and the configured codes are valid, an empty BMC
InfoContext (`getMarkers()` returns null or an empty list) no longer opens a
modal OK dialog. Other messages and disabled-state behavior are unaffected.

## 0.1.61 — Optional MidTier hotkey retired

The configurable keyboard shortcut was removed because its Eclipse binding
validation failed with `NullPointerException` in Developer Studio. The plugin
no longer shows or validates a hotkey in Preferences. It attempts to remove
any previous Yrell USER binding at startup without touching unrelated keys.

**Flush MidTier Object** remains available through BMC's Object List right-click
menu; it starts directly in Eclipse Progress. The toolbar button still provides
form, full-cache and all-pods actions. All other 0.1.60 functionality remains.

0.1.61 additionally provides two explicit saved-object cleanup actions when right-clicking a non-placeholder form in BMC Object List: Remove __c Suffix from Field Names... and Remove __c Suffix from View Names.... Preview and confirm each; job targets only selected AR server and form. Close any editor of the form first. Automatic __c suppression for new fields is unchanged.

## 0.1.61 — Default object sorting

One opt-in global sorting preference for BMC Object List and compatible object selectors. Defaults to Modified descending, uses BMC's own `ColumnSorter`, makes the requested column visible using a component-local cloned descriptor, and does not mutate BMC stored column settings or run additional server queries. Disabled for selectors where BMC does not support sorting or the chosen field is absent. New components require an Eclipse `-clean` restart to ensure weaving hooks are installed.

## 0.1.61 quick actions


## 0.1.61 MidTier update

Safe reverse-proxy action normalization for form/full/all-pods flush, live Server List selection, and collapsible SWT Browser. See top-level CHANGELOG.md for details.

## MidTier 0.1.61

RSSO and MidTier Configuration Tool have separate authentication. The embedded browser now recognizes the `Configuration Tool authorization failed` page, navigates to the genuine Configuration Tool login page, and in Automatic mode may reuse Developer Studio AR credentials only against the verified MidTier login form. Manual administrator login is available in the browser. No flush occurs without a verified cache form and cfgStok.

# Yrell Developer Tools 0.1.61 — BMC Developer Studio 26.1.1

Complete Eclipse plugin source, installable JAR, Java 17 build scripts and regression tests. Preserves 0.1.49 robustness fixes and 0.1.50's MidTier toolbar/preferences.

## Package

- `source/se.yrell.developertools/`: all 65 Java source files, plugin XML, OSGi manifest and icons.
- `install/se.yrell.developertools_0.1.66.jar`: Windows Developer Studio plugin/optional Fast object lists Java agent.
- `scripts/build.sh` and `scripts/test.sh`: Java 17 compilation and tests.
- `tests/`: Java regression tests including an embedded mock MidTier HTTP server.
- `CHANGELOG.md` / `TEST-RESULTS.txt`.

Only YOUR plugin source is included; BMC Developer Studio's sources and binary libraries are **not** distributed.

## Rebuild on Ubuntu

After installing this release into `~/Git/YrellDeveloperTools`, use the previously extracted Developer Studio binary libraries:

```bash
cd ~/Git/YrellDeveloperTools
./scripts/build.sh "$PWD/.build/devstudio/x64"
./scripts/test.sh "$PWD/.build/devstudio/x64"
cp -v install/se.yrell.developertools_0.1.66.jar ~/Downloads/
```

If the `.build` cache has been removed, use the original ZIP once:

```bash
./scripts/build.sh "$HOME/Downloads/ARSuiteKitDeveloperWindows26.1.01/Disk1/files/DeveloperStudio/componentDeveloperStudioWindows.zip"
```

Build requirements: Java JDK 17+, `unzip`. The build targets BMC Developer Studio 26.1.1 build001 and Java 17 bytecode.

## Install on Windows

1. Exit Developer Studio. Back up `x64/devstudio.ini` and the previous plugin JAR.
2. Delete/relocate previous `se.yrell.developertools_*.jar` files from `x64/plugins`.
3. Copy `se.yrell.developertools_0.1.66.jar` into `x64/plugins`.
4. If Fast object lists uses `-javaagent`, adjust the existing agent argument **after** the `-vmargs` line in `x64/devstudio.ini`:

   ```ini
   -javaagent:C:/BMC/DeveloperStudio/x64/plugins/se.yrell.developertools_0.1.66.jar
   ```

5. Restart with `-clean` once. Do not add a second `-vmargs` line.

## MidTier Cache Flush — v0.1.61

In **Window > Preferences > Yrell Developer Tools**, enable **MidTier Cache Flush**. Under this feature, add/edit a mapping for each connected AR Server (e.g. `arserver` -> `http://ars.sandbox:8080/arsys`). The server name must match the Server List on the MidTier Configuration Tool page.

**Existing v0.1.50/v0.1.51 mappings are retained** (v1/v2 settings are read and migrated to v3 when saved). For a test environment using `http://` (not HTTPS), use **Edit server** and explicitly check **Allow sending Studio credentials over unencrypted HTTP (test only)**, then Apply/OK. The option defaults to OFF for every existing mapping. **HTTPS should be used in production.** No password is stored in preferences or config files.

Open a form/workflow editor, click the **toilet icon**, choose the form and click **Flush now**. The Java HTTP client:

1. Reads the username/password, port and authentication source from BMC's *currently connected* `IAuthenticationStore`/`ARServerStore` only when a MidTier Configuration Tool session is needed.
2. Opens `config.jsp` and submits the login form to `ConfigServlet?action=logon` with a per-operation, in-memory cookie jar. This creates a **new MidTier Configuration Tool session**, not an attempt to reuse the Studio AR ticket.
3. Fetches the live `config_cache_cleanup.jsp`, checks `cfgStok`, server list and POST URL, and submits the `User`/selected form to `GoatConfigServlet?action=Cache&op_type=form_cleanup`.
4. Interprets MidTier's exact `is cleaned !` and `doesn't exists in cache` messages as `CLEANED` and `ALREADY_EMPTY`. Other HTTP 2xx responses are **not** declared successful unless an explicit result is recognized.

**Authentication (0.1.61):** In **Edit server**, choose **Automatic (default)**, **AR Server credentials**, or **RSSO / SSO browser**. Old mapping formats (v1/v2/v3) remain readable; on save, mappings are stored in v4. Automatic first probes MidTier with Java HttpClient: if the Configuration Tool login form is available, it uses the current Developer Studio AR credentials; if an RSSO/SSO login is required, it opens an embedded SWT Browser with the live RSSO login and continues with the form flush after the browser returns to the exact MidTier cache page. **RSSO / SSO browser** skips the Java AR credential path entirely. The browser retains its own HttpOnly cookies and handles IdP JavaScript or form redirects. AR credentials, session cookies and cfgStok are never stored in plugin settings. The browser does not inject or read JavaScript on identity-provider pages. The optional separate HTTPS SSO context is used by the Java preflight allowlist.

**Legacy SWT Browser compatibility (0.1.61):** Embedded MidTier JavaScript now targets ES5 to support Windows SWT/MSHTML (including the reported unsupported `includes()` error). URL endpoints are checked without ES6 APIs. A simulated legacy browser environment is covered by 44 JavaScript regression assertions.

**SSO limitations:** Live browser SSO still requires verification in your Windows Developer Studio and IdP environment. Some SSO policies do not support embedded browsers or require a separate system browser. This version does not share Developer Studio AR tickets with RSSO or assume all SSO systems permit unattended login.

**Automatic Field IDs (0.1.61):** Fixed a regression from 0.1.49 in which unavailable AR metadata would silently disable generated IDs. When metadata cannot be queried, the allocator now uses its previous local/current-form plus in-session collision checks (with a once-per-server/day warning). IDs are never reserved in preferences before successful assignment. Other forms and concurrent clients are not checked in this fallback mode, so global uniqueness is not guaranteed. The original developer-ID range (10-21) and ID format are preserved.

**Error diagnostics:** Full exception chain is now logged in Eclipse **Error Log**, usually also in the workspace's `.metadata/.log`; sensitive POST form values, cookies and token are not deliberately logged.

No additional Yes/OK confirmation appears for individual forms. Full-instance flush and all-pods flush still require an explicit warning/confirmation because of the impact on all users. Network calls run in an Eclipse background Job, not SWT's UI thread. Authentication data, cookies and `cfgStok` are not persisted or logged.

**Limitations:** Studio's `getPassword()` may be empty after RSSO/SSO or passwordless login. In that case the plugin reports that automatic authentication cannot be completed; no flush is sent. Connecting with a reusable AR password or logging in manually in Configuration Tool is a fallback. A new Java HTTP cookie session is created for each invocation. If the MidTier uses its own separate administrator credentials, the Studio AR credentials may not be accepted. Do not share passwords or tokens in logs.

**Validation:** Java 17 build, 14 Default naming assertions, 6 Fast Forms assertions, 32 bytecode matches and agent bootstrap; 35 MidTier regression assertions and 13 SSO assertions against embedded mock HTTP server; real downloaded MidTier login/cache HTML was parsed with the new Java parser. The Windows UI, embedded RSSO browser, and a live POST through Java HttpClient remain unverified; browser-console testing has validated the HTTP protocol but not this plugin's JVM in Windows.

## 0.1.61 Quick MidTier flush
