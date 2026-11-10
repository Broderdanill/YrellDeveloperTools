## 0.1.60 manual suffix cleanup

Right-click a saved form under Object List → Forms to preview and confirm renaming persisted field or view names ending __c. The two actions are independent and run as Eclipse Progress jobs. Close the form editor before using them.

## New in 0.1.60 — reliable Eclipse keyboard shortcuts

- **Native binding fix:** the optional quick-flush shortcut is now installed through `IBindingService.savePreferences()` as an Eclipse `Binding.USER` entry in the active key scheme and workbench-window context. The previous global SWT key filter has been removed. Changing the shortcut in Preferences updates the Eclipse binding immediately; it also synchronizes at startup. Other user bindings are preserved.
- **Startup isolation:** keybinding setup no longer depends on icon, keepalive and other startup helpers succeeding first.
- **Diagnostics:** successful registration and detected collisions are logged to Eclipse Error Log; if no workbench/binding service is available, the shortcut fails visibly in logs rather than appearing silently configured.
- **Fast object lists:** unchanged. A UI-only `Show all` toggle would be incorrect when the object list was populated through server-side filtering; BMC provider refresh/invalidations require a verified integration test.
- **Keepalive:** unchanged. `ARServerUser.verifyUser()` is a lightweight session check, not an object/cache preloader. The first slow operation after idle needs correlation with Java/API logs and a thread dump before changing AR client/session behavior.

### Existing 0.1.58 enhancements, retained

- **Object List right-click:** BMC Developer Studio builds its Object List context menus through `com.bmc.arsys.studio.commonui.genericaction`; 0.1.60 registers `MidTierObjectListActionProvider` with that *native* extension point. On a selected **Form** (or workflow with an identified primary form), **Flush MidTier Object** starts directly in **Progress**, without the action chooser. The generic Eclipse context menu remains available for open editors only; Object List gets no duplicate item.
- **Clearer icon:** a new hand-drawn, high-contrast toilet silhouette is supplied as 16/24/32 px PNG; `source/se.yrell.developertools/icons/toilet.svg` is the editable source. Tooltip remains “Flush MidTier cache ...”.
- **Shortcut UI:** BMC Developer Studio doesn't show Eclipse **General > Keys** in this installation. Instead open **Window > Preferences > Yrell Developer Tools > MidTier Cache Flush** and enter **Quick flush shortcut (optional)**. Example `CTRL+ALT+F8`. Empty = disabled (default). The program requires Ctrl+Alt plus an optional Shift and A–Z or F1–F12, and refuses to save a combination already registered with Eclipse's binding service. Because some native BMC shortcuts may not be registered with Eclipse, confirm in your own Studio environment that the chosen combination is unused.
- **Scope:** the shortcut targets a form/workflow Object List selection or active supported editor; the handler rejects other active views to prevent acting on stale selections. No popup chooser is displayed, and the job runs in Progress. A separate SSO authentication step can still require attention in Progress details.
- This release has been compiled and regression-tested against Developer Studio 26.1.1; the **actual Windows key dispatch and BMC UI require live verification**.

# Yrell Developer Tools 0.1.60 — BMC Developer Studio 26.1.1

Complete Eclipse plugin source, installable JAR, Java 17 build scripts and regression tests. Preserves 0.1.49 robustness fixes and 0.1.50's MidTier toolbar/preferences.

## Package

- `source/se.yrell.developertools/`: all 65 Java source files, plugin XML, OSGi manifest and icons.
- `install/se.yrell.developertools_0.1.60.jar`: Windows Developer Studio plugin/optional Fast object lists Java agent.
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
cp -v install/se.yrell.developertools_0.1.60.jar ~/Downloads/
```

If the `.build` cache has been removed, use the original ZIP once:

```bash
./scripts/build.sh "$HOME/Downloads/ARSuiteKitDeveloperWindows26.1.01/Disk1/files/DeveloperStudio/componentDeveloperStudioWindows.zip"
```

Build requirements: Java JDK 17+, `unzip`. The build targets BMC Developer Studio 26.1.1 build001 and Java 17 bytecode.

## Install on Windows

1. Exit Developer Studio. Back up `x64/devstudio.ini` and the previous plugin JAR.
2. Delete/relocate previous `se.yrell.developertools_*.jar` files from `x64/plugins`.
3. Copy `se.yrell.developertools_0.1.60.jar` into `x64/plugins`.
4. If Fast object lists uses `-javaagent`, adjust the existing agent argument **after** the `-vmargs` line in `x64/devstudio.ini`:

   ```ini
   -javaagent:C:/BMC/DeveloperStudio/x64/plugins/se.yrell.developertools_0.1.60.jar
   ```

5. Restart with `-clean` once. Do not add a second `-vmargs` line.

## Default object sorting — 0.1.60

Open **Window > Preferences > Yrell Developer Tools**, enable **Default object sorting** in the feature table, and select the global rule:

- **Sort column:** Modified (default), Name, or Object type.
- **Sort direction:** Descending / newest first (default), or Ascending.

The single rule applies to **all** BMC `ObjectListComponent` instances when BMC provides that column and enables sorting, including the main Object List and compatible form/workflow selectors. It does **not** affect AR server queries, Fast object lists customization filtering, or the sequence of workflow actions in guides. If a chosen column is hidden, the plugin shows it in that component only, by cloning BMC's column metadata; BMC's persisted column preferences are unchanged. If a column is absent or the selector disables sorting, the plugin leaves BMC behavior untouched. The user can still click a column header to change sorting. Reopen a list after changing settings. Restart Developer Studio once with `-clean` after installation so the new OSGi weaving hooks have a chance to attach before BMC loads these classes.

**Integration-test limitation:** the shared class implementation and bytecode transforms were verified against Developer Studio 26.1.1 build001, but actual Windows SWT table sorting and selector behavior need validation on a running Developer Studio installation.

## MidTier quick actions and Progress — 0.1.60

- **Right-click** an AR Form or supported workflow in **Object List** (or in a form/workflow editor): **Flush MidTier Object** submits the selected/primary form directly in the background. **No dialog or confirmation** is shown. With workflow objects, the primary form is used; it does not silently flush every associated form.
- **Keyboard shortcut**: **Window > Preferences > Yrell Developer Tools > MidTier Cache Flush > Quick flush shortcut**, find **Flush MidTier Object**, and assign your own shortcut. No default binding is installed, so existing Developer Studio shortcuts are preserved. The shortcut triggers the exact same direct-form-flush job.
- **Toilet toolbar button**: visible when MidTier Cache Flush is enabled and a MidTier URL mapping matches the active AR server in the editor/Object List. With just one connected configured server, the toolbar remains available even without an open form; in that case the toolbar offers only full-cache and all-pods options. Multiple connections with no unambiguous current server never guess a target.
- **Toolbar picker**: form and action only, then closes after **Start flush**; full cache and all pods still require confirmation. Context menu and hotkey bypass this picker.
- **Progress view**: all operations are Eclipse user Jobs. Open the job's **Details** action for the current result or expanded SSO browser. No initial dialog opens for the quick action, even when the job needs browser authentication; if interactive SSO login is required, use Progress > Details to reveal the browser. The live SSO session remains in the background while the panel is collapsed.
- **Results**: `Form … is cleaned !` is success, `doesn't exists in cache` is **Already empty** (not error), and unrecognized results are **Unverified** (not claimed successful). The embedded legacy-browser scripts avoid `querySelector`/`querySelectorAll` and other modern DOM APIs.

## MidTier Cache Flush — 0.1.60 changes

The Configuration Tool can emit an absolute `form.action` with an internal host/scheme when accessed through an HTTPS reverse proxy. The browser integration checks the BMC `GoatConfigServlet` path, exact query for the operation, POST method and live `cfgStok`; then it submits to the **configured MidTier HTTPS origin**, so the token is not sent to a foreign/internal origin. This applies to form, whole MidTier, and all-pods flush. When the MidTier Server List has exactly one server, it is selected automatically; with multiple servers a unique match to the connected AR server is required. The BMC form's own `form_name` and `form_flush` fields are populated before submission.

The RSSO browser is now **collapsed by default**. Use the **Show browser / login** button to expand it when needed, and **Hide browser / login** to collapse it again. Hiding does not dispose the browser or its active session. If SSO requires an interactive login, expand the browser to complete it.

## MidTier Cache Flush — v0.1.60

In **Window > Preferences > Yrell Developer Tools**, enable **MidTier Cache Flush**. Under this feature, add/edit a mapping for each connected AR Server (e.g. `arserver` -> `http://ars.sandbox:8080/arsys`). The server name must match the Server List on the MidTier Configuration Tool page.

**Existing v0.1.50/v0.1.51 mappings are retained** (v1/v2 settings are read and migrated to v3 when saved). For a test environment using `http://` (not HTTPS), use **Edit server** and explicitly check **Allow sending Studio credentials over unencrypted HTTP (test only)**, then Apply/OK. The option defaults to OFF for every existing mapping. **HTTPS should be used in production.** No password is stored in preferences or config files.

Open a form/workflow editor, click the **toilet icon**, choose the form and click **Flush now**. The Java HTTP client:

1. Reads the username/password, port and authentication source from BMC's *currently connected* `IAuthenticationStore`/`ARServerStore` only when a MidTier Configuration Tool session is needed.
2. Opens `config.jsp` and submits the login form to `ConfigServlet?action=logon` with a per-operation, in-memory cookie jar. This creates a **new MidTier Configuration Tool session**, not an attempt to reuse the Studio AR ticket.
3. Fetches the live `config_cache_cleanup.jsp`, checks `cfgStok`, server list and POST URL, and submits the `User`/selected form to `GoatConfigServlet?action=Cache&op_type=form_cleanup`.
4. Interprets MidTier's exact `is cleaned !` and `doesn't exists in cache` messages as `CLEANED` and `ALREADY_EMPTY`. Other HTTP 2xx responses are **not** declared successful unless an explicit result is recognized.

**Authentication (0.1.60):** In **Edit server**, choose **Automatic (default)**, **AR Server credentials**, or **RSSO / SSO browser**. Old mapping formats (v1/v2/v3) remain readable; on save, mappings are stored in v4. Automatic first probes MidTier with Java HttpClient: if the Configuration Tool login form is available, it uses the current Developer Studio AR credentials; if an RSSO/SSO login is required, it opens an embedded SWT Browser with the live RSSO login and continues with the form flush after the browser returns to the exact MidTier cache page. **RSSO / SSO browser** skips the Java AR credential path entirely. The browser retains its own HttpOnly cookies and handles IdP JavaScript or form redirects. AR credentials, session cookies and cfgStok are never stored in plugin settings. The browser does not inject or read JavaScript on identity-provider pages. The optional separate HTTPS SSO context is used by the Java preflight allowlist.

**Separate Configuration Tool authentication (0.1.60):** A successful RSSO login does **not** necessarily authorize `/arsys/shared/config/`. When BMC displays `Configuration Tool authorization failed. Click Configuration Tool to log in.`, the embedded browser now opens the configured server's `config.jsp` rather than retrying the cache JSP twice. In **Automatic** mode, it attempts a one-time AR administrator login using the *current* Developer Studio credentials only if BMC's actual Configuration Tool login form is present with the verified, same-origin `ConfigServlet?action=logon` action. The password is not persisted or sent to RSSO. If the login cannot be verified, the browser remains visible so the administrator can complete it manually. In explicit **RSSO / SSO browser** mode, no Studio password is read. A valid Configuration Tool session and `cfgStok` are required before cache flush. If your account lacks Configuration Tool administrator privileges, neither RSSO nor the plugin can bypass that policy.

**Legacy SWT Browser compatibility (0.1.60):** The browser JavaScript was rewritten to ES5 for Windows SWT implementations that use MSHTML/Internet Explorer instead of a modern WebView2 engine. The embedded scripts no longer rely on `includes()`, `Array.from()`, `new URL()` or `URL.searchParams`; form endpoints remain strictly validated. A simulated legacy JavaScript engine runs 44 regression checks. This does not guarantee that a modern third-party IdP itself supports embedded legacy browsers.

**SSO limitations:** Live browser SSO still requires verification in your Windows Developer Studio and IdP environment. Some SSO policies do not support embedded browsers or require a separate system browser. This version does not share Developer Studio AR tickets with RSSO or assume all SSO systems permit unattended login.

**Automatic Field IDs (0.1.60):** Fixed a regression from 0.1.49 in which unavailable AR metadata would silently disable generated IDs. When metadata cannot be queried, the allocator now uses its previous local/current-form plus in-session collision checks (with a once-per-server/day warning). IDs are never reserved in preferences before successful assignment. Other forms and concurrent clients are not checked in this fallback mode, so global uniqueness is not guaranteed. The original developer-ID range (10-21) and ID format are preserved.

**Error diagnostics:** Full exception chain is now logged in Eclipse **Error Log**, usually also in the workspace's `.metadata/.log`; sensitive POST form values, cookies and token are not deliberately logged.

No additional Yes/OK confirmation appears for individual forms. Full-instance flush and all-pods flush still require an explicit warning/confirmation because of the impact on all users. Network calls run in an Eclipse background Job, not SWT's UI thread. Authentication data, cookies and `cfgStok` are not persisted or logged.

**Limitations:** Studio's `getPassword()` may be empty after RSSO/SSO or passwordless login. In that case the plugin reports that automatic authentication cannot be completed; no flush is sent. Connecting with a reusable AR password or logging in manually in Configuration Tool is a fallback. A new Java HTTP cookie session is created for each invocation. If the MidTier uses its own separate administrator credentials, the Studio AR credentials may not be accepted. Do not share passwords or tokens in logs.

**Validation for 0.1.60:** Java 17 compilation of 61 source files, all Java regression tests, native key-binding source contract checks (19 assertions), 37 mock MidTier HTTP tests, 13 SSO tests, 21 Configuration Tool tests, and 60 JavaScript DOM assertions passed. An upgrade-script test from the 0.1.58 source project also passed. The live Windows workbench key dispatcher and SSO integration still require testing in Developer Studio; passing headless tests is not proof of live UI behavior.
