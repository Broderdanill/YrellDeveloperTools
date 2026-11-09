# Changelog

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
