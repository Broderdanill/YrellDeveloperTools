# Yrell Developer Tools 0.1.50 – Developer Studio 26.1.1

Complete source, compiled Eclipse/OSGi plugin and Java agent. This release preserves all functions from 0.1.49 and adds **MidTier Cache Flush**.

## Contents

- `install/se.yrell.developertools_0.1.50.jar` – installable Eclipse plugin, also used as `-javaagent` for Fast object lists.
- `source/se.yrell.developertools/src/` – full Java source (51 files).
- `source/se.yrell.developertools/plugin.xml`, `META-INF/MANIFEST.MF`, `icons/` – plugin descriptors and toilet toolbar icon.
- `scripts/build.sh` – build with JDK 17+ against the **provided** Developer Studio 26.1.1 build001 libraries.
- `scripts/test.sh`, `scripts/test-midtier-js.js`, `tests/` – regression tests. Node is optional for browser-script tests.
- `CHANGELOG.md` and `TEST-RESULTS.txt` – changes, security boundaries and test scope.

## Build on Ubuntu

Requires JDK 17 or newer, `unzip`, and `zip` (`javac`, `jar` part of JDK).

```bash
unzip YrellDeveloperTools-0.1.50-FullSource.zip
cd YrellDeveloperTools-0.1.50
./scripts/build.sh /absolute/path/componentDeveloperStudioWindows.zip
./scripts/test.sh /absolute/path/componentDeveloperStudioWindows.zip
```

Alternatively, build against an extracted Developer Studio installation (the folder containing `x64/plugins`):

```bash
./scripts/build.sh /absolute/path/DeveloperStudio/x64
./scripts/test.sh /absolute/path/DeveloperStudio/x64
```

Output: `install/se.yrell.developertools_0.1.50.jar`. The build extracts BMC JARs to `.build/devstudio/x64/plugins` when given the ZIP. **BMC JARs are not included in the released plugin or source archive.**

## Install in Developer Studio on Windows

1. Close Developer Studio and back up `x64/devstudio.ini` and the previous plugin JAR.
2. Remove the older `se.yrell.developertools_*.jar` files from `x64/plugins` (keep backup outside plugins).
3. Copy `install/se.yrell.developertools_0.1.50.jar` to `x64/plugins`.
4. If you use Fast object lists via Java agent, change its existing path **after** the `-vmargs` line in `x64/devstudio.ini` to the new JAR. Do not remove unrelated JVM arguments or add a duplicate `-vmargs` line.
5. Start Developer Studio with `-clean -consoleLog` once; verify **Window > Preferences > Yrell Developer Tools**.

Example **single added JVM argument** (adjust your installation directory):

```ini
-javaagent:C:/BMC/DeveloperStudio/x64/plugins/se.yrell.developertools_0.1.50.jar
```

## MidTier Cache Flush – setup

1. Open **Window > Preferences > Yrell Developer Tools**.
2. Select **MidTier Cache Flush** from the feature table. Click **Add server...** for each AR server you use.
3. Select the name from the connected AR Server list (or type an exact server name), then enter its MidTier context URL, for example `http://ars.sandbox:8080/arsys` or `https://midtier.example.com/arsys`.
4. Check the feature's **Enabled** box and select **Apply / OK**. No restart is required for the mapping; reopening the editor may be needed to refresh toolbar visibility.
5. Open a form or workflow editor. Use the **toilet icon** in the main Eclipse toolbar.
6. In the dialog choose one operation: **Flush selected form**, **Flush complete MidTier cache**, or **Flush complete cache on all pods**. With a workflow referring to multiple forms, use the form dropdown to choose the right one.
7. Sign in to the embedded **MidTier Configuration Tool** browser if needed. After login, click **Reload cache page** to load the page required for the selected operation, then click **Execute flush** and confirm. Inspect the server's returned page for the outcome.

The server name in Developer Studio must correspond to the **Server List** value offered by MidTier. If they differ, configure a mapping for the correct AR server identity or adjust the MidTier configuration; the plugin refuses to guess a server on a form flush.

### Implementation and security behavior

- The plugin reuses the current BMC `GoatConfigServlet?action=Cache` HTML form **inside an authenticated SWT Browser** and the page's fresh `cfgStok` security token. It does not invent an undocumented REST API or reuse an expired token from this release's HTML examples.
- A Developer Studio AR session is **not automatically** a MidTier Configuration Tool session. MidTier may require separate authentication; **no password or configuration token is stored in plugin preferences**. Web browser session cookies are controlled by the Windows SWT Browser engine.
- On a per-form flush, the request is the equivalent of `form_flush=Yes`, `server=<server>`, `form_name=<form>` to `/arsys/servlet/GoatConfigServlet?action=Cache&op_type=form_cleanup`. A complete flush uses `cache_flush=Yes`; all pods uses `cache_flush_allpods=Yes` on the other Configuration Tool page. The configured URL may use a custom context path instead of `/arsys`.
- A confirmation is required for each submitted action. Requests are blocked when the loaded configuration page's origin, servlet action or security token does not match expectations. Full-cache flushes can affect every AR server/tenant on the MidTier and trigger repopulation; test carefully.
- A flush response is shown in the embedded browser. A successful **request submission is not proof** that cache eviction completed or propagated to every pod.
- Load balancers may route requests to different MidTier instances. Configure a specific MidTier instance if you intend to target one; use the **All pods** button only when the cluster's own functionality is configured appropriately. BMC documentation recommends a direct instance address for the Configuration Tool.
- HTTP is allowed for lab setups but transmits credentials without transport encryption. Use HTTPS for non-isolated deployments.
- The toolbar command is available for the BMC form and workflow editor types in Developer Studio **26.1.1**. If a workflow has no resolvable associated form, the plugin does not attempt a flush.

## Scope of validation

Java 17 compile, 0.1.49 regression suite, bytecode transforms, Java agent bootstrap, MidTier URL/mapping tests, and browser JavaScript DOM simulations passed against the supplied libraries. Actual Windows SWT Browser login, command-toolbar visibility and live MidTier servlet responses require a connected test installation. See `TEST-RESULTS.txt`.
