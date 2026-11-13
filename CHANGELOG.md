## 0.1.68 — PWA Icon Helper catalog and preview regression fix (2026-11-13)
- Fix CSS parsing that discarded most icons in comma-separated selector declarations.
- Accept `::before`, single-quoted glyph content, and left/right icon names.
- Render with compact icon-only CSS and embedded icon font instead of the full PWA bundle.
- Restore unconditional file-backed SWT Browser preview rather than `Browser.setText`.
- Retry empty/failed catalog after 30 seconds; identify HTML login responses separately from CSS.
- Add 19 isolated regression assertions including local HTTP font retrieval and HTML response detection.
- No change to other plugin features or user preferences.
- Production JAR patched from user-supplied 0.1.67 full-source JAR and the modified IconCatalog; no BMC SDK binaries bundled.

## 0.1.67 — Default warning suppression codes (2026-11-10)
- New default code list: `5026,5033,5384,5385,5386`.
- Message Suppressor remains opt-in; only WARNING severity is filtered.
- Explicitly saved user preferences are not overwritten when updating.
- Updated Preferences help and added regression checks for all default codes and ERROR preservation.
- No changes to the suppression runtime, BMC save workflow, or other plugin modules.

## 0.1.66 — Diagnose remaining blank save-warning popup (2026-11-10)

- No change to warning suppression or save semantics. Empty popup remains under investigation.
- Log marker counts, numeric codes, severity and suppressibility for both BMC save-success sources.
- Log the marker inventory when BMC Info.showProblems presents a warning dialog.
- Avoid message text, object names and URLs in diagnostics. Fail open when unreadable.

## 0.1.66 — Second FormEditor warning dialog path + diagnostics (2026-11-10)

- Confirmed Developer Studio 26.1.1 `FormEditor.saveForm` has **two** `Save successful with warnings.` dialog call sites. 0.1.64 guarded only the first, leaving the `ModelException` warning-only branch able to open a modal despite suppression.
- Instrument both call sites. Guard the ordinary success call using original source markers and the context actually presented. In the `ModelException` call, BMC marks the operation `SAVE_STATE.FAILED`; preserve its popup and log it explicitly rather than hiding a failed save. Preserve ERROR/FATAL/unknown markers and retain `DocumentPage` safeguards.
- When Message Suppressor is enabled, write a small, privacy-restricted log to `%USERPROFILE%\.yrell-developertools\message-suppression-diagnostics.log` for each actual FormEditor guard invocation, including `action=SHOW/SKIP`, branch and marker state.
- Record `-javaagent` release version and report mismatches at Eclipse startup; tests now assert two FormEditor guards instead of one, with additional warning/error/empty context cases.
- Tested against the real BMC 26.1.1 class bytecode with the Java 17 verifier and -javaagent. Actual Windows UI behavior still requires an integration test.

## 0.1.66 — Prevent empty "Save successful with warnings." dialogs (2026-11-10)

- Root cause confirmed from real Developer Studio 26.1.1 bytecode: `FormEditor.saveForm` decides to invoke `Info.showProblems` based on `infoResource.getMarkers()` BEFORE the presentation-only suppression runs; `DocumentPage.internalSave` has a similar check on unfiltered `StatusInfo` entries. Filtering just `InfoContext` cannot reliably prevent this outer dialog invocation.
- Add narrowly scoped guards to the **successful save-warning** call sites, examining their ORIGINAL warning markers/status entries. If *every* entry is a configured WARNING, BMC's modal is never opened. Mixed warnings, ERRORs and unknown markers remain visible. The FormEditor failed-save branch and DocumentPage's failed-save flag never suppress a dialog.
- Existing `Info.open` and `Info.showProblems` presentation filtering stays in place. No AR save, status collection, source markers or validation state is modified.
- Add regression tests for the real BMC save-class bytecode, status/marker combinations and fail-open behavior. Windows integration still required.

## 0.1.63 — Null-marker InfoContext handling (2026-11-10)

- A BMC `InfoContext` can return `null` rather than an empty list, so `Info.showProblems` now skips empty or null-marker dialogs when Message Suppressor is enabled. This was insufficient by itself for the "Save successful with warnings." dialog, whose caller pre-checks the unfiltered warnings.
- Preserve all errors and unfiltered warnings; no underlying model changes.

## 0.1.62 — Message Suppressor (2026-11-10)

- Added optional **Message Suppressor** to Yrell Developer Tools Preferences.
- Default code list: **5033,5384**; disabled by default until explicitly enabled.
- Hooks the verified BMC `Info.open(int,int,String)` and `Info.showProblems(IInfoContext,String)` presentation methods; filters numeric code only when its BMC severity is **WARNING**, never ERROR/FATAL/INFO.
- Suppressed warnings are removed only from the UI view. Validation, AR save operations, the real model, and failures are not altered.
- BMC's 5033 is a warning for a missing field label; 5384 is documented as an ARERR for failure to save an alarm and remains visible in that case.
- Supports both early `-javaagent` injection and fallback Equinox weaving; code list changes take effect without reweaving, via an atomically written local preference file.
- Regression tests use the real 26.1.1 BMC Info bytecode and verify filter/agent injection, mixed-warning presentation, error preservation and default-disabled behaviour.
- **Requires Windows integration testing** to confirm the actual save-warning path. Alternative BMC dialogs beyond the two verified methods are deliberately not instrumented.

## 0.1.61 (2026-11-10)

- Removed the optional MidTier hotkey feature in response to Developer Studio
  throwing `Could not verify Eclipse shortcut: NullPointerException`.
- Removed the shortcut input and validation from Preferences, all runtime keybinding
  installation and refresh calls, and the unused binding implementation.
- Added one-time best-effort cleanup of former Eclipse USER bindings owned by the
  plugin. It does not install keyboard shortcuts or modify unrelated bindings.
  If the BMC binding service is unavailable, Developer Studio continues normally.
- Native Object List right-click **Flush MidTier Object**, toolbar cache flush,
  Progress, default sorting, Automatic Field IDs, and `__c` actions remain.

## 0.1.60 (2026-11-10)

- Added **Remove __c Suffix from Field Names...** and **Remove __c Suffix from View Names...** to a single selected form's native BMC Object List context menu. The two actions are independent of MidTier cache configuration.
- Preview exact proposed name changes before confirmation; preserve existing case-insensitive collision checks. The operation never renames the form itself, never changes the other category, and never runs automatically or as a bulk action across forms.
- Perform server reads/writes in user-visible Eclipse Progress jobs. Refuse to modify a form open in an editor; skip ambiguous/colliding target names; stop on server write failure, report partial changes, and log diagnostics. This is a manual action for existing saved names, separate from automatic __c removal during new object creation.
- Added explicit suffix planner/menu regression tests. Needs real Developer Studio 26.1.1/AR Server integration test for persistence and menu visibility.


- Added one global, optional **Default object sorting** setting independent of Fast object lists. The rule has a field (**Modified** default, Name, or Object type) and direction (**descending** default, or ascending), applying to compatible object types without individual mappings.
- Hooked BMC's shared `ObjectListComponent` and its `ViewContentProvider` via the existing OSGi weaving service. The plugin uses BMC's timestamp-aware `ColumnSorter` after columns load, rather than another comparator or another AR query. Applied to Object List and shared selectors when sorting is enabled.
- The selected sorting column is made visible **only in the current component** by cloning BMC's `ColumnInformation`; this does not overwrite BMC's persisted column configuration. Unsupported selectors or lists missing the selected metadata are unchanged.
- Added tests for the BMC 26.1.1 bytecode hooks and composition with the existing Fast Forms Java agent. Existing tests retained. Live Windows UI integration still requires confirmation.

## 0.1.59 (2026-11-09)

- Fixed missing context-menu action in **BMC Object List** by registering BMC's own `com.bmc.arsys.studio.commonui.genericaction` provider rather than relying on Eclipse `popup:any` contributions. The action uses the exact one-item selection and launches an immediate, non-blocking Progress job without opening a chooser.
- Replaced blurry toilet toolbar icons with a simplified high-contrast toilet silhouette in 16/24/32 px PNG, plus editable SVG source.
- Added an optional, per-plugin shortcut setting under MidTier Cache Flush preferences because Developer Studio does not expose General > Keys. No default is assigned. Validates against registered Eclipse bindings to reduce collisions and only dispatches in form/workflow editors or Object List with an unambiguous selection.
- Added static native extension registration and keyboard matching tests; all prior regressions retained.

## 0.1.59 — contextual quick flush, Eclipse Progress, and older SWT Browser support (2026-11-09)

- Added **Flush MidTier Object** to the context menu of AR Form and workflow objects, and as an unbound Eclipse command for user-selected shortcuts in Preferences > General > Keys. Both submit a form flush directly without opening the action picker or requesting a second confirmation. For workflow objects use the primary form only. The popup uses the actual context-menu selection, not a stale open editor.
- Extended the main-toolbar toilet icon visibility to configured connected AR server selection in Object List, not only an active form/workflow editor. A unique configured connected server is also supported when no object is open; its toolbar picker offers full cache/all pods only. Multiple connections without a unique target never guess.
- Added preferences-time workbench reevaluation to refresh toolbar visibility when server mappings change.
- Replaced blocking flush execution with user-visible Eclipse Progress Jobs and a **Details** action; the compact toolbar action picker closes as soon as the job starts. A contextual quick flush has no initial dialog even in the SSO path. Its browser view can be revealed from Progress if an interactive login is needed.
- Corrected cache-result interpretation: `doesn't exists in cache` is normal **Already empty** and `is cleaned !` is success. Replaced `querySelectorAll` and `querySelector` in injected result/login scripts with legacy-compatible DOM traversal.
- Added cancellable browser coordination to avoid a pending SSO page submitting a flush after the Progress Job was canceled.
- Tested Java 17 compilation against supplied Developer Studio 26.1.1 build001 binaries, existing ASM and -javaagent audit, HTTP mock tests and ES5 browser simulation. Real Windows/SWT Browser and SSO integration still needs a live test.

## 0.1.59 — MidTier form actions, server selection and collapsible browser (2026-11-09)

- Correct the SWT Browser `ERROR: Unexpected form action` problem for MidTier Configuration Tool served behind a reverse proxy / TLS terminator. Validate the original BMC servlet path, POST method, `cfgStok` and mode-specific query parameters, then rewrite the form action to the configured MidTier origin before submission. Never submit a token to a foreign or internal action origin.
- Apply the same safe action normalization for selected-form flush, full MidTier flush and all-pods flush.
- For a form flush, select the sole non-empty Server List option when only one exists; otherwise require a unique match for the current Developer Studio AR-server. The form name is populated before submitting BMC's original form. Fail rather than guessing when multiple servers are ambiguous.
- Keep the embedded SSO browser collapsed by default. Add **Show browser / login** and **Hide browser / login** toggle; preserving its live session across toggles. The status guides the user to expand it when manual login is required.
- The browser still requires the expected BMC servlet path and token, does not bypass Configuration Tool authorization, and does not send credentials to an IdP. Automatic login still needs verification on a real Windows RSSO system.
- Extend ES5 DOM regression tests with reverse-proxy/internal HTTP actions, wrong servlet paths, single-/multiple-server selection, and full-cache action normalization.

# Changelog

## 0.1.59 — legacy SWT Browser JavaScript compatibility (2026-11-09)

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


## 0.1.59 (2026-11-10)
- Fix: configurable MidTier quick-flush shortcut now uses Eclipse `IBindingService.savePreferences()` with a USER KeyBinding in the active workbench scheme and window context.
- Registered shortcut applies immediately when saved and at startup; unrelated Eclipse/BMC bindings are retained. No binding exists until explicitly configured.
- Startup: shortcut initialization isolated from other plugin startup features.
- Safety: keyboard command does not act on stale Object List selections if another view is active.
- Review only: Fast object lists remain unchanged. Server-filtered objects cannot be reliably displayed by checkboxes alone without requerying BMC providers.
- Review only: keepalive remains unchanged pending evidence from API logs and UI thread dumps; `verifyUser()` may not warm metadata/cache or recover a stale connection.
