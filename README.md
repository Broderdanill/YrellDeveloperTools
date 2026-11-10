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

# Yrell Developer Tools 0.1.66 — Save warning popup (both FormEditor branches)

BMC Developer Studio 26.1.1 `FormEditor.saveForm()` has **two** call sites with
identical `Save successful with warnings.` captions: an ordinary save path
and a `ModelException`/warning-status path. Version 0.1.64 guarded only the
first call site. This is confirmed in the BMC 26.1.1 build001 class bytecode.

Version 0.1.66 instruments BOTH call sites. In the actual success branch,
it checks the original `IInfoResource` markers *and* the displayed
`IInfoContext`; the popup is skipped only when neither contains errors or
unconfigured warnings and both are readable. The exception branch is
**diagnostic-only**: BMC explicitly sets `SAVE_STATE.FAILED` there, so its
warning-caption dialog is not hidden even if the text sounds successful.
This prevents a failed save from appearing successful. The DocumentPage
warning guard is retained, and BMC controls all save/validation behavior.

**Diagnostics:** when the form-save guard runs with Message Suppressor enabled,
`%USERPROFILE%\.yrell-developertools\message-suppression-diagnostics.log`
records `form-success` or `form-model-exception-save-failed` and `action=SHOW/SKIP`, without
object names or warning text. A missing entry while the popup appears may
indicate that Developer Studio is running an older `-javaagent` or that the
BMC class was loaded before the hook attached. The startup log now checks
that the agent reports release **0.1.66**; verify any `-javaagent` path in
`devstudio.ini` uses the same JAR version as the installed plugin.

Automatic unit/bytecode tests pass against the supplied BMC Developer Studio
26.1.1 build001 dependencies, including Java 17 `-Xverify:all`. The live
Windows save popup still requires validation; no success is assumed in advance.

# Yrell Developer Tools 0.1.62 — Message Suppressor

New optional **Message Suppressor** in **Window > Preferences > Yrell Developer Tools**.
Tick its entry to enable, then edit **Suppress warning codes** (default: `5033,5384`).
The preference controls **warning presentation only**, not AR validation, server requests,
server-side logs, save failures, or error severity. The module is **off by default**.

- BMC `Info.open(int severity, int code, String text)` uses the exact numeric code, and suppresses it **only** if severity is WARNING (2).
- BMC `Info.showProblems(IInfoContext, String)` hides selected WARNING markers in the presentation of that dialog. If all markers are hidden it does not open the dialog; with mixed markers it shows only the remaining messages. No source markers are deleted.
- 5033 is a BMC warning about missing field labels. 5384 is documented as ARERR *The alarm cannot be saved*; errors with that number **must remain visible**, even though 5384 is in the configured list.
- The optional Java agent hooks the early-loaded BMC Info class. The Equinox weaving path is the fallback when no `-javaagent` is present and BMC Info was not already loaded. A single `-clean` restart after installation is recommended.
- The module uses `~/.yrell-developertools/message-suppression.properties` for the early Java agent (only an enable flag and code list); it is rewritten from the plugin's Preferences on startup and on Apply/OK. It contains no credentials.
- Other warnings, errors, warnings without numeric code, or alternate BMC dialogs that bypass these two entry points are not affected.

**Integration-test limit:** Unit and bytecode tests pass against the BMC Developer Studio 26.1.1 Info class, and startup with `-javaagent` is tested. Actual Windows UI save dialogs still need live verification.

## 0.1.61 — Optional MidTier hotkey retired

The configurable keyboard shortcut was removed because its Eclipse binding
validation failed with `NullPointerException` in Developer Studio. The plugin
no longer shows or validates a hotkey in Preferences. It attempts to remove
any previous Yrell USER binding at startup without touching unrelated keys.

**Flush MidTier Object** remains available through BMC's Object List right-click
menu; it starts directly in Eclipse Progress. The toolbar button still provides
form, full-cache and all-pods actions. All other 0.1.60 functionality remains.

## 0.1.61 manual suffix cleanup

Right-click a saved form under Object List → Forms to preview and confirm renaming persisted field or view names ending __c. The two actions are independent and run as Eclipse Progress jobs. Close the form editor before using them.

### Existing 0.1.58 enhancements, retained

- **Object List right-click:** BMC Developer Studio builds its Object List context menus through `com.bmc.arsys.studio.commonui.genericaction`; 0.1.61 registers `MidTierObjectListActionProvider` with that *native* extension point. On a selected **Form** (or workflow with an identified primary form), **Flush MidTier Object** starts directly in **Progress**, without the action chooser. The generic Eclipse context menu remains available for open editors only; Object List gets no duplicate item.
- **Clearer icon:** a new hand-drawn, high-contrast toilet silhouette is supplied as 16/24/32 px PNG; `source/se.yrell.developertools/icons/toilet.svg` is the editable source. Tooltip remains “Flush MidTier cache ...”.
- This release has been compiled and regression-tested against Developer Studio 26.1.1; the **actual Windows BMC UI requires live verification**.

# Yrell Developer Tools 0.1.62 — BMC Developer Studio 26.1.1

Complete Eclipse plugin source, installable JAR, Java 17 build scripts and regression tests. Preserves 0.1.49 robustness fixes and 0.1.50's MidTier toolbar/preferences.

## Package

- `source/se.yrell.developertools/`: all 67 Java source files, plugin XML, OSGi manifest and icons.
- `install/se.yrell.developertools_0.1.66.jar`: Windows Developer Studio plugin/optional Fast object lists Java agent.
- `scripts/build.sh` and `scripts/test.sh`: Java 17 compilation and tests.
- `tests/`: Java regression tests including an embedded mock MidTier HTTP server.
- `CHANGELOG.md` / `TEST-RESULTS.txt`.

Only your plugin source is included; BMC Developer Studio's sources and binary libraries are **not** distributed.

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

## Default object sorting — 0.1.61

Open **Window > Preferences > Yrell Developer Tools**, enable **Default object sorting** in the feature table, and select the global rule:

- **Sort column:** Modified (default), Name, or Object type.
- **Sort direction:** Descending / newest first (default), or Ascending.

The single rule applies to **all** BMC `ObjectListComponent` instances when BMC provides that column and enables sorting, including the main Object List and compatible form/workflow selectors. It does **not** affect AR server queries, Fast object lists customization filtering, or the sequence of workflow actions in guides. If a chosen column is hidden, the plugin shows it in that component only, by cloning BMC's column metadata; BMC's persisted column preferences are unchanged. If a column is absent or the selector disables sorting, the plugin leaves BMC behavior untouched. The user can still click a column header to change sorting. Reopen a list after changing settings. Restart Developer Studio once with `-clean` after installation so the new OSGi weaving hooks have a chance to attach before BMC loads these classes.

**Integration-test limitation:** the shared class implementation and bytecode transforms were verified against Developer Studio 26.1.1 build001, but actual Windows SWT table sorting and selector behavior need validation on a running Developer Studio installation.

## MidTier quick actions and Progress — 0.1.61

- **Right-click** an AR Form or supported workflow in **Object List** (or in a form/workflow editor): **Flush MidTier Object** submits the selected/primary form directly in the background. **No dialog or confirmation** is shown. With workflow objects, the primary form is used; it does not silently flush every associated form.
- **Toilet toolbar button**: visible when MidTier Cache Flush is enabled and a MidTier URL mapping matches the active AR server in the editor/Object List. With just one connected configured server, the toolbar remains available even without an open form; in that case the toolbar offers only full-cache and all-pods options. Multiple connections with no unambiguous current server never guess a target.
- **Progress view**: all operations are Eclipse user Jobs. Open the job's **Details** action for the current result or expanded SSO browser. No initial dialog opens for the quick action, even when the job needs browser authentication; if interactive SSO login is required, use Progress > Details to reveal the browser. The live SSO session remains in the background while the panel is collapsed.
- **Results**: `Form … is cleaned !` is success, `doesn't exists in cache` is **Already empty** (not error), and unrecognized results are **Unverified** (not claimed successful). The embedded legacy-browser scripts avoid `querySelector`/`querySelectorAll` and other modern DOM APIs.

## MidTier Cache Flush — 0.1.61 changes

The Configuration Tool can emit an absolute `form.action` with an internal host/scheme when accessed through an HTTPS reverse proxy. The browser integration checks the BMC `GoatConfigServlet` path, exact query for the operation, POST method and live `cfgStok`; then it submits to the **configured MidTier HTTPS origin**, so the token is not sent to a foreign/internal origin. This applies to form, whole MidTier, and all-pods flush. When the MidTier Server List has exactly one server, it is selected automatically; with multiple servers a unique match to the connected AR server is required. The BMC form's own `form_name` and `form_flush` fields are populated before submission.

The RSSO browser is now **collapsed by default**. Use the **Show browser / login** button to expand it when needed, and **Hide browser / login** to collapse it again. Hiding does not dispose the browser or its active session. If SSO requires an interactive login, expand the browser to complete it.

## MidTier Cache Flush — v0.1.61

In **Window > Preferences > Yrell Developer Tools**, enable **MidTier Cache Flush**. Under this feature, add/edit a mapping for each connected AR Server (e.g. `arserver` -> `http://ars.sandbox:8080/arsys`). The server name must match the Server List on the MidTier Configuration Tool page.

**Existing v0.1.50/v0.1.51 mappings are retained** (v1/v2 settings are read and migrated to v3 when saved). For a test environment using `http://` (not HTTPS), use **Edit server** and explicitly check **Allow sending Studio credentials over unencrypted HTTP (test only)**, then Apply/OK. The option defaults to OFF for every existing mapping. **HTTPS should be used in production.** No password is stored in preferences or config files.

Open a form/workflow editor, click the **toilet icon**, choose the form and click **Flush now**. The Java HTTP client:

1. Reads the username/password, port and authentication source from BMC's *currently connected* `IAuthenticationStore`/`ARServerStore` only when a MidTier Configuration Tool session is needed.
2. Opens `config.jsp` and submits the login form to `ConfigServlet?action=logon` with a per-operation, in-memory cookie jar. This creates a **new MidTier Configuration Tool session**, not an attempt to reuse the Studio AR ticket.
3. Fetches the live `config_cache_cleanup.jsp`, checks `cfgStok`, server list and POST URL, and submits the `User`/selected form to `GoatConfigServlet?action=Cache&op_type=form_cleanup`.
4. Interprets MidTier's exact `is cleaned !` and `doesn't exists in cache` messages as `CLEANED` and `ALREADY_EMPTY`. Other HTTP 2xx responses are **not** declared successful unless an explicit result is recognized.

**Authentication (0.1.61):** In **Edit server**, choose **Automatic (default)**, **AR Server credentials**, or **RSSO / SSO browser**. Old mapping formats (v1/v2/v3) remain readable; on save, mappings are stored in v4. Automatic first probes MidTier with Java HttpClient: if the Configuration Tool login form is available, it uses the current Developer Studio AR credentials; if an RSSO/SSO login is required, it opens an embedded SWT Browser with the live RSSO login and continues with the form flush after the browser returns to the exact MidTier cache page. **RSSO / SSO browser** skips the Java AR credential path entirely. The browser retains its own HttpOnly cookies and handles IdP JavaScript or form redirects. AR credentials, session cookies and cfgStok are never stored in plugin settings. The browser does not inject or read JavaScript on identity-provider pages. The optional separate HTTPS SSO context is used by the Java preflight allowlist.

**Separate Configuration Tool authentication (0.1.61):** A successful RSSO login does **not** necessarily authorize `/arsys/shared/config/`. When BMC displays `Configuration Tool authorization failed. Click Configuration Tool to log in.`, the embedded browser now opens the configured server's `config.jsp` rather than retrying the cache JSP twice. In **Automatic** mode, it attempts a one-time AR administrator login using the *current* Developer Studio credentials only if BMC's actual Configuration Tool login form is present with the verified, same-origin `ConfigServlet?action=logon` action. The password is not persisted or sent to RSSO. If the login cannot be verified, the browser remains visible so the administrator can complete it manually. In explicit **RSSO / SSO browser** mode, no Studio password is read. A valid Configuration Tool session and `cfgStok` are required before cache flush. If your account lacks Configuration Tool administrator privileges, neither RSSO nor the plugin can bypass that policy.

**Legacy SWT Browser compatibility (0.1.61):** The browser JavaScript was rewritten to ES5 for Windows SWT implementations that use MSHTML/Internet Explorer instead of a modern WebView2 engine. The embedded scripts no longer rely on `includes()`, `Array.from()`, `new URL()` or `URL.searchParams`; form endpoints remain strictly validated. A simulated legacy JavaScript engine runs 44 regression checks. This does not guarantee that a modern third-party IdP itself supports embedded legacy browsers.

**SSO limitations:** Live browser SSO still requires verification in your Windows Developer Studio and IdP environment. Some SSO policies do not support embedded browsers or require a separate system browser. This version does not share Developer Studio AR tickets with RSSO or assume all SSO systems permit unattended login.

**Automatic Field IDs (0.1.61):** Fixed a regression from 0.1.49 in which unavailable AR metadata would silently disable generated IDs. When metadata cannot be queried, the allocator now uses its previous local/current-form plus in-session collision checks (with a once-per-server/day warning). IDs are never reserved in preferences before successful assignment. Other forms and concurrent clients are not checked in this fallback mode, so global uniqueness is not guaranteed. The original developer-ID range (10-21) and ID format are preserved.

**Error diagnostics:** Full exception chain is now logged in Eclipse **Error Log**, usually also in the workspace's `.metadata/.log`; sensitive POST form values, cookies and token are not deliberately logged.

No additional Yes/OK confirmation appears for individual forms. Full-instance flush and all-pods flush still require an explicit warning/confirmation because of the impact on all users. Network calls run in an Eclipse background Job, not SWT's UI thread. Authentication data, cookies and `cfgStok` are not persisted or logged.

**Limitations:** Studio's `getPassword()` may be empty after RSSO/SSO or passwordless login. In that case the plugin reports that automatic authentication cannot be completed; no flush is sent. Connecting with a reusable AR password or logging in manually in Configuration Tool is a fallback. A new Java HTTP cookie session is created for each invocation. If the MidTier uses its own separate administrator credentials, the Studio AR credentials may not be accepted. Do not share passwords or tokens in logs.
