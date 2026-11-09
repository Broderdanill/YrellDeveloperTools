# Yrell Developer Tools 0.1.54 — BMC Developer Studio 26.1.1

Complete Eclipse plugin source, installable JAR, Java 17 build scripts and regression tests. Preserves 0.1.49 robustness fixes and 0.1.50's MidTier toolbar/preferences.

## Package

- `source/se.yrell.developertools/`: all 55 Java source files, plugin XML, OSGi manifest and icons.
- `install/se.yrell.developertools_0.1.54.jar`: Windows Developer Studio plugin/optional Fast object lists Java agent.
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
cp -v install/se.yrell.developertools_0.1.54.jar ~/Downloads/
```

If the `.build` cache has been removed, use the original ZIP once:

```bash
./scripts/build.sh "$HOME/Downloads/ARSuiteKitDeveloperWindows26.1.01/Disk1/files/DeveloperStudio/componentDeveloperStudioWindows.zip"
```

Build requirements: Java JDK 17+, `unzip`. The build targets BMC Developer Studio 26.1.1 build001 and Java 17 bytecode.

## Install on Windows

1. Exit Developer Studio. Back up `x64/devstudio.ini` and the previous plugin JAR.
2. Delete/relocate previous `se.yrell.developertools_*.jar` files from `x64/plugins`.
3. Copy `se.yrell.developertools_0.1.54.jar` into `x64/plugins`.
4. If Fast object lists uses `-javaagent`, adjust the existing agent argument **after** the `-vmargs` line in `x64/devstudio.ini`:

   ```ini
   -javaagent:C:/BMC/DeveloperStudio/x64/plugins/se.yrell.developertools_0.1.54.jar
   ```

5. Restart with `-clean` once. Do not add a second `-vmargs` line.

## MidTier Cache Flush — v0.1.54

In **Window > Preferences > Yrell Developer Tools**, enable **MidTier Cache Flush**. Under this feature, add/edit a mapping for each connected AR Server (e.g. `arserver` -> `http://ars.sandbox:8080/arsys`). The server name must match the Server List on the MidTier Configuration Tool page.

**Existing v0.1.50/v0.1.51 mappings are retained** (v1/v2 settings are read and migrated to v3 when saved). For a test environment using `http://` (not HTTPS), use **Edit server** and explicitly check **Allow sending Studio credentials over unencrypted HTTP (test only)**, then Apply/OK. The option defaults to OFF for every existing mapping. **HTTPS should be used in production.** No password is stored in preferences or config files.

Open a form/workflow editor, click the **toilet icon**, choose the form and click **Flush now**. The Java HTTP client:

1. Reads the username/password, port and authentication source from BMC's *currently connected* `IAuthenticationStore`/`ARServerStore` only when a MidTier Configuration Tool session is needed.
2. Opens `config.jsp` and submits the login form to `ConfigServlet?action=logon` with a per-operation, in-memory cookie jar. This creates a **new MidTier Configuration Tool session**, not an attempt to reuse the Studio AR ticket.
3. Fetches the live `config_cache_cleanup.jsp`, checks `cfgStok`, server list and POST URL, and submits the `User`/selected form to `GoatConfigServlet?action=Cache&op_type=form_cleanup`.
4. Interprets MidTier's exact `is cleaned !` and `doesn't exists in cache` messages as `CLEANED` and `ALREADY_EMPTY`. Other HTTP 2xx responses are **not** declared successful unless an explicit result is recognized.

**Authentication (0.1.54):** In **Edit server**, choose **Automatic (default)**, **AR Server credentials**, or **RSSO / SSO browser**. Old mapping formats (v1/v2/v3) remain readable; on save, mappings are stored in v4. Automatic first probes MidTier with Java HttpClient: if the Configuration Tool login form is available, it uses the current Developer Studio AR credentials; if an RSSO/SSO login is required, it opens an embedded SWT Browser with the live RSSO login and continues with the form flush after the browser returns to the exact MidTier cache page. **RSSO / SSO browser** skips the Java AR credential path entirely. The browser retains its own HttpOnly cookies and handles IdP JavaScript or form redirects. AR credentials, session cookies and cfgStok are never stored in plugin settings. The browser does not inject or read JavaScript on identity-provider pages. The optional separate HTTPS SSO context is used by the Java preflight allowlist.

**Separate Configuration Tool authentication (0.1.54):** A successful RSSO login does **not** necessarily authorize `/arsys/shared/config/`. When BMC displays `Configuration Tool authorization failed. Click Configuration Tool to log in.`, the embedded browser now opens the configured server's `config.jsp` rather than retrying the cache JSP twice. In **Automatic** mode, it attempts a one-time AR administrator login using the *current* Developer Studio credentials only if BMC's actual Configuration Tool login form is present with the verified, same-origin `ConfigServlet?action=logon` action. The password is not persisted or sent to RSSO. If the login cannot be verified, the browser remains visible so the administrator can complete it manually. In explicit **RSSO / SSO browser** mode, no Studio password is read. A valid Configuration Tool session and `cfgStok` are required before cache flush. If your account lacks Configuration Tool administrator privileges, neither RSSO nor the plugin can bypass that policy.

**SSO limitations:** Live browser SSO still requires verification in your Windows Developer Studio and IdP environment. Some SSO policies do not support embedded browsers or require a separate system browser. This version does not share Developer Studio AR tickets with RSSO or assume all SSO systems permit unattended login.

**Automatic Field IDs (0.1.54):** Fixed a regression from 0.1.49 in which unavailable AR metadata would silently disable generated IDs. When metadata cannot be queried, the allocator now uses its previous local/current-form plus in-session collision checks (with a once-per-server/day warning). IDs are never reserved in preferences before successful assignment. Other forms and concurrent clients are not checked in this fallback mode, so global uniqueness is not guaranteed. The original developer-ID range (10-21) and ID format are preserved.

**Error diagnostics:** Full exception chain is now logged in Eclipse **Error Log**, usually also in the workspace's `.metadata/.log`; sensitive POST form values, cookies and token are not deliberately logged.

No additional Yes/OK confirmation appears for individual forms. Full-instance flush and all-pods flush still require an explicit warning/confirmation because of the impact on all users. Network calls run in an Eclipse background Job, not SWT's UI thread. Authentication data, cookies and `cfgStok` are not persisted or logged.

**Limitations:** Studio's `getPassword()` may be empty after RSSO/SSO or passwordless login. In that case the plugin reports that automatic authentication cannot be completed; no flush is sent. Connecting with a reusable AR password or logging in manually in Configuration Tool is a fallback. A new Java HTTP cookie session is created for each invocation. If the MidTier uses its own separate administrator credentials, the Studio AR credentials may not be accepted. Do not share passwords or tokens in logs.

**Validation:** Java 17 build, 14 Default naming assertions, 6 Fast Forms assertions, 32 bytecode matches and agent bootstrap; 35 MidTier regression assertions, 13 SSO assertions, 13 Configuration Tool authorization assertions and 11 optional JavaScript DOM checks against embedded mock HTTP server; real downloaded MidTier login/cache HTML was parsed with the new Java parser. The Windows UI, embedded RSSO browser, and a live POST through Java HttpClient remain unverified; browser-console testing has validated the HTTP protocol but not this plugin's JVM in Windows.
