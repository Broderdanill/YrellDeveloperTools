# Yrell Developer Tools 0.1.41

Developer Studio helper plugin for BMC Helix/AR System Developer Studio.

## Install

1. Close Developer Studio.
2. Remove older `se.yrell.developertools_*.jar` from `x64/plugins`.
3. Copy `install/se.yrell.developertools_0.1.41.jar` to `x64/plugins`.
4. If Fast object lists is used as Java agent, update `DeveloperStudio.ini` to point at `se.yrell.developertools_0.1.41.jar`.
5. Start Developer Studio with `-clean -consoleLog`.

Fast object lists still requires the same jar to be loaded with `-javaagent` in `DeveloperStudio.ini` if you want the initial server-side object-list request to be filtered before BMC classes load.

## Features

All features are disabled by default and must be enabled in:

`Window -> Preferences -> Yrell Developer Tools`

### Custom suffix cleanup

Removes BMC's automatic `__c` suffix while new objects/fields are created. It does not run post-save cleanup and does not rename existing saved objects.

0.1.40 also cleans `__c` if Developer Studio updates the database name from a changed label on a new unsaved field.

### Default naming

Can automatically set database names for table columns using a configurable pattern.

### Automatic field IDs

Can assign custom field IDs using the format `<Developer ID><YY><MM><DD><NN>`.

### PWA icon helper

Adds the Icon picker button next to Icon properties and can preload the CSS-based PWA icon catalog.

### Fast object lists

Filters list loading by Customization Type values such as `2,4` for Overlay and Custom.

Important: true initial server-side filtering requires the jar as Java agent. Without `-javaagent`, Developer Studio may load all objects first and filter afterwards.

0.1.41 fixes the Java agent default so it follows the plugin principle: Fast object lists is disabled unless `bmc.ds.fastForms.enabled=true` is configured or the saved agent properties file enables it.

### Keepalive

Can periodically call `verifyUser()` on connected AR Server sessions. It does not load forms or object lists.

### Object Insight

Optional view that follows the selected Developer Studio object.

Current display:

- Field permissions as one row per group, with group name, group ID and permission value.
- Table field permissions also when the table is selected from the form canvas/outline editpart.
- Table server/form as separate rows for selected table fields.
- Table qualification for selected table fields.
- Table sort columns for selected table fields, ordered by sort sequence and split into readable rows instead of one long raw property string.

The view keeps the last real Developer Studio selection as its own selection provider so the normal Properties view should not clear just because Object Insight receives focus.

### Remove from view

Optional right-click command: `Remove from view`.

The command is visible only when:

- the feature is enabled in settings,
- the selection is a form-editor UI field,
- the field exists in at least one other view.

It removes only the current view instance. The AR field remains on the form and remains in the other views.

0.1.40 uses BMC's own `RemoveFieldFromViewCommand` path when available so the current form view refreshes visually immediately and participates in the editor command stack.

## Build notes

- Bundle version: `0.1.41`
- Java bytecode: 17 / major version 61
- Same jar can be used both as Eclipse plugin and Fast object lists Java agent.
