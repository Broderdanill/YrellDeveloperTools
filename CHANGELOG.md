## 0.1.57 — contextual quick flush, Eclipse Progress, and older SWT Browser support (2026-11-09)

- Added **Flush MidTier Object** to the context menu of AR Form and workflow objects, and as an unbound Eclipse command for user-selected shortcuts in Preferences > General > Keys. Both submit a form flush directly without opening the action picker or requesting a second confirmation. For workflow objects use the primary form only. The popup uses the actual context-menu selection, not a stale open editor.
- Extended the main-toolbar toilet icon visibility to configured connected AR server selection in Object List, not only an active form/workflow editor. A unique configured connected server is also supported when no object is open; its toolbar picker offers full cache/all pods only. Multiple connections without a unique target never guess.
- Added preferences-time workbench reevaluation to refresh toolbar visibility when server mappings change.
- Replaced blocking flush execution with user-visible Eclipse Progress Jobs and a **Details** action; the compact toolbar action picker closes as soon as the job starts. A contextual quick flush has no initial dialog even in the SSO path. Its browser view can be revealed from Progress if an interactive login is needed.
- Corrected cache-result interpretation: `doesn't exists in cache` is normal **Already empty** and `is cleaned !` is success. Replaced `querySelectorAll` and `querySelector` in injected result/login scripts with legacy-compatible DOM traversal.
- Added cancellable browser coordination to avoid a pending SSO page submitting a flush after the Progress Job was canceled.
- Tested Java 17 compilation against supplied Developer Studio 26.1.1 build001 binaries, existing ASM and -javaagent audit, HTTP mock tests and ES5 browser simulation. Real Windows/SWT Browser and SSO integration still needs a live test.

## 0.1.57 — MidTier form actions, server selection and collapsible browser (2026-11-09)

- Correct the SWT Browser `ERROR: Unexpected form action` problem for MidTier Configuration Tool served behind a reverse proxy / TLS terminator. Validate the original BMC servlet path, POST method, `cfgStok` and mode-specific query parameters, then rewrite the form action to the configured MidTier origin before submission. Never submit a token to a foreign or internal action origin.
- Apply the same safe action normalization for selected-form flush, full MidTier flush and all-pods flush.
- For a form flush, select the sole non-empty Server List option when only one exists; otherwise require a unique match for the current Developer Studio AR-server. The form name is populated before submitting BMC's original form. Fail rather than guessing when multiple servers are ambiguous.
- Keep the embedded SSO browser collapsed by default. Add **Show browser / login** and **Hide browser / login** toggle; preserving its live session across toggles. The status guides the user to expand it when manual login is required.
- The browser still requires the expected BMC servlet path and token, does not bypass Configuration Tool authorization, and does not send credentials to an IdP. Automatic login still needs verification on a real Windows RSSO system.
- Extend ES5 DOM regression tests with reverse-proxy/internal HTTP actions, wrong servlet paths, single-/multiple-server selection, and full-cache action normalization.

# Changelog

## 0.1.57 — legacy SWT Browser JavaScript compatibility (2026-11-09)

- Fixed `org.eclipse.swt.SWTException: Property or method 'includes' is not supported` when the Windows SWT Browser uses MSHTML / an older Internet Explorer JavaScript engine.
- Replaced JavaScript `String.includes`, `Array.from`, the `URL` constructor and `URL.searchParams` throughout the dynamically injected MidTier browser scripts with ES5-compatible alternatives.
- Kept strict same-origin, exact form action and query parameter verification before posting AR credentials or cache flush; duplicate, unexpected and cross-origin action parameters are rejected.
- Added regression coverage that actually evaluates the generated scripts in a simulated legacy JavaScript environment with modern browser APIs disabled. Covered login, authorization rejection, form-specific flush, full flush, all-pods flush and confirmation parsing (44 checks).
- Preserved RSSO/browser flow and Configuration Tool authorization handling introduced in 0.1.54, as well as Automatic Field IDs and other existing features.
- A real Windows SWT Browser and live RSSO login are still needed for end-to-end verification; tests simulate legacy browser API availability, not a real IE/MSHTML runtime.

## 0.1.54 — Configuration Tool authorization after RSSO (2026-11-09)

- Corrected the two-attempt RSSO loop that treated a MidTier `Configuration Tool authorization failed` page as an ordinary browser redirect. RSSO authentication and Configuration Tool administrator authentication are separate states.
- On this explicit authorization response, the embedded browser opens the configured MidTier's own `/arsys/shared/config/config.jsp`, rather than repeatedly opening the cache JSP. No cache flush is submitted while access is denied.
- In **Automatic** mode, if BMC's genuine login form appears on the exact `config.jsp` location and posts to the expected local `ConfigServlet?action=logon`, the plugin attempts one administrator login with Developer Studio's existing AR credentials. HTTPS is required unless the existing per-server test-only HTTP override is enabled. The browser retains its own session cookies.
- In **RSSO / SSO browser** mode, the separate Configuration Tool login remains interactive and never reads Developer Studio's password. If AR credentials are not reusable or do not authorize Configuration Tool, the same browser pane allows manual administrator login.
- MidTier authorization failures after a cache POST no longer count as a successful or unverified flush. No automatic cache form submission occurs before `cfgStok`, AR server and form action validation.
- Added 13 Configuration Tool authorization regression assertions and 11 JavaScript browser-DOM checks (Node.js if available) in addition to existing tests.
- No changes to Automatic Field IDs, Default naming, Fast object lists, PWA Icon Helper, or other plugin features since 0.1.53.


## 0.1.53 — browser-based RSSO and Automatic Field ID restoration (2026-11-09)

- Added per-MidTier authentication mode **Automatic**, **AR Server credentials**, or **RSSO / SSO browser**; previous mapping formats remain readable (new v4 format on save).
- Automatic mode probes Configuration Tool normally. If RSSO requires a browser/interactive login, the dialog opens a real SWT Browser instead of posting AR credentials to the identity provider. Explicit SSO mode skips Studio credentials.
- The browser follows its own login redirects and JavaScript form submissions. Only the exact configured MidTier cache page can run flush JavaScript, after validation of form action, token and AR server; browser IdP pages are not instrumented.
- Fixed **Automatic Field IDs** regression introduced in 0.1.49: inability to access AR metadata no longer silently disables ID generation. The current-form plus in-session fallback remains active and logs a once-per-server/day warning that cross-form/global uniqueness is not guaranteed.
- Avoids prematurely saving a candidate next ID before actual field assignment.
- New Auto Field ID regression suite (5 checks) and SSO regression suite (13 checks), in addition to the previous tests.
- Still requires Windows/real MidTier SSO integration testing. Java/ASM static checks are not a substitute for an actual login.


## 0.1.52 — RSSO-safe redirects and MidTier diagnostics (2026-11-09)

- Fixes generic `MidTier attempted to navigate outside the configured context` during legitimate RSSO redirect discovery. GET-only SSO redirects are supported to same-origin `/rsso`/`/hsso`, or to an optional explicitly configured HTTPS SSO URL per MidTier mapping.
- Retains strict same-origin and `/arsys` checks for both login and flush POST targets; no AR credentials or `cfgStok` are forwarded through redirects. Blocks HTTP 307/308 for POST, downgrade/cross-origin GETs not in the allowlist, and similarly prefixed non-context paths.
- When the cache page leads to SSO that requires interaction, attempts direct BMC Configuration Tool login using Studio credentials before failing; does not claim Java HttpClient can execute arbitrary browser/SSO flows.
- Errors now include a safe, query-stripped redirect destination and a full exception/stack trace in Eclipse Error Log. No POST body, password or cookie is logged intentionally.
- Preferences can optionally store an RSSO HTTPS context root per AR-server mapping. v1/v2 entries remain compatible; mappings persist as v3.
- Extended embedded mock-server and navigation-policy regression tests: 35 MidTier assertions; Java 17 compile, original ASM tests and -javaagent JVM startup passed.
- **Still requires Windows/live testing** for actual RSSO behavior; not all identity providers support headless HTTP or expose reusable credentials.

## 0.1.51 — automatic MidTier login using Developer Studio credentials (2026-11-09)

- Replaced SWT Browser login with a Java 17 `HttpClient` based on the verified MidTier Configuration Tool workflow.
- Uses the connected BMC `IAuthenticationStore.getUser()/getPassword()` and server port/authentication source, with no reflection or password in preferences.
- Authenticates through BMC's `ConfigServlet?action=logon`, preserving only in-memory cookies; acquires fresh `cfgStok` from each cache page.
- Adds strict same-origin, action path, token, server dropdown, bounded timeout and redirect checks before POST.
- Forms: `is cleaned !` -> cleaned, `doesn't exists in cache` -> already empty; other responses remain unverified.
- Removes second confirmation for form flush. Global/All-Pods flush still prompts due potential service impact.
- Per-AR-server opt-in for automatic credentials over HTTP, off by default; existing v1 mappings are migrated safely to v2.
- Native dialog reports asynchronous status, no embedded browser; job is off the SWT UI thread.
- New mock-server integration tests for auth, CSRF token, cookies, safe URL, normal and empty responses, full/pods switches; Java parser verified against user-provided saved MidTier HTML (not included in ZIP).
- **Not verified in Windows:** live `getPassword()` contents under user's authentication mode, Java HTTP request acceptance by live MidTier, Eclipse UI behavior. RSSO/SSO may not expose a usable password.

## 0.1.50 – MidTier Cache Flush (2026-11-09)

- **New: MidTier Cache Flush** in the existing feature table, disabled by default, with an Add/Edit/Remove mapping table for connected AR servers and their MidTier context URLs.
- **New:** Toilet icon in the Eclipse main toolbar when a BMC FormEditor or WorkflowEditorBase editor is active and the feature is enabled.
- **New:** Resolve AR server and form from actual BMC editor APIs; for workflows, prefer primary form and offer all associated forms when available.
- **New:** Embedded SWT Browser to authenticate to BMC MidTier Configuration Tool, read only the *live session's* form and `cfgStok`, and submit BMC's existing cache form after a local confirmation.
- **Operations:** form-specific flush, full cache flush, and full cache flush on all pods. Selection always navigates to the correct Configuration Tool page; actions use exact HTML field names verified from the supplied 26.x MidTier pages.
- **Safety:** No password, bearer token or `cfgStok` saved in preferences. URL/origin/action/token validation before submission. No guessed server in form flush. No success message without observing an actual server response; after submitting, response remains visible for inspection.
- **Tests:** model URL/serialization/mapping regression suite; JavaScript DOM exercise for all modes and safety rejection scenarios; existing ASM, Java agent and naming tests.
- **Compatibility:** BMC Developer Studio 26.1.1 build001 and Java 17.

### Known limitations / Windows verification

- The SWT Browser requires the Windows installation's browser engine. It may ask for an independent MidTier Configuration Tool login; Developer Studio's AR session does not automatically authenticate to the web UI.
- Eclipse's dynamic `activeEditor` toolbar visibility and login/POST response behavior must be verified in Windows; GUI/live server are not runnable from the Linux build environment.
- MidTier can return a response that indicates a rejected or incomplete operation; the plugin deliberately reports **submitted**, not **completed**, until the server response is evaluated by the user.
- For a full flush, all tenant/server caches may be evicted and prefetch may restart. All-pods behavior depends on the actual MidTier deployment configuration. No operational claim is inferred from a static HTML export.
- Browser authentication can fail when redirects move to a different origin, or when the MidTier form posts to a different host than the configured base URL; the plugin blocks mismatched cross-origin POSTs by design.
- Multiple MidTier instances per one AR server are not currently configured as separate rows: one target URL is stored per AR server. BMC's **Flush all pods** operation is available as a distinct choice.
- Existing Fast object lists state isolation and Object Insight UI-thread considerations from 0.1.49 still require full real-world integration tests.

## 0.1.49

See previous release history included in the original archive. Changes carried forward: strictly column-only Default naming; safe __c fallback; improved Fast object lists instrumentation; asynchronous Object Insight; safer Keepalive, Icon Helper, automatic field ID allocation and Remove from view.
