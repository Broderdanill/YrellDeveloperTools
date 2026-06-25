# Yrell Developer Tools 0.1.28

> 0.1.28: Fixes a startup ClassCircularityError in the integrated Fast object lists weaving by excluding Developer Tools, ASM and OSGi infrastructure classes from the weaving hook. Restart with `-clean` after replacing the jar.
Yrell Developer Tools is a single drop-in Eclipse/BMC Helix Developer Studio plugin for small productivity fixes and controlled Developer Studio behaviour changes.

Install only one version of this plugin at a time.

## Installation

1. Close Developer Studio.
2. Remove old plugin jars from `x64/plugins`, for example:
   - `se.yrell.developertools_*.jar`
   - `se.yrell.devstudio.keepalive_*.jar`
   - `devstudio-fastforms-agent.jar` related javaagent setup if you no longer want the old agent.
3. Copy `install/se.yrell.developertools_0.1.28.jar` to Developer Studio `x64/plugins`.
4. Start Developer Studio once with `-clean -consoleLog`.

The plugin is compiled as Java 17 bytecode and is intended to run on both JDK 17 and JDK 21.

## Settings

Open:

```text
Window -> Preferences -> Yrell Developer Tools
```

## Custom suffix cleanup

Setting:

```text
Remove BMC's automatic __c suffix
```

When enabled, the plugin removes BMC's automatically generated `__c` suffix while new objects are being created in the form designer.

Current scope:

- New fields added to an existing form.
- Default fields created when a new form is created.
- Proposed names typed in Developer Studio UI fields.

Safety rules:

- It does not run a post-save cleanup.
- It does not rename old existing fields just because they already end with `__c`.
- It only targets creation flows and newly added UI fields.

## Default naming

Setting:

```text
Table columns: set database name automatically
```

Pattern setting:

```text
Table column pattern
```

Default pattern:

```text
col_{remote_form}_{remote_field_name}
```

Supported tokens:

- `{form}` - current form name.
- `{remote_form}` - table field target/remote form when Developer Studio exposes it.
- `{field_name}` - local column field name.
- `{remote_field_name}` - remote field name when available.
- `{field_id}` - field id.

The generated value is normalized to lower-case ASCII with underscores. This section is intentionally structured so more default-name rules can be added later as separate checkbox + pattern pairs.

## Automatic field IDs

Settings:

```text
Enable automatic field ID assignment
Developer ID (10-21)
Skip panels/pages
```

Format:

```text
<developer id><YY><MM><DD><sequence>
```

Example:

```text
1226062301
```

When enabled, Developer ID must be set. The plugin reads the global AR metadata form to find the next available field id for the current day and developer id. It does not intentionally reuse an id just because you moved to another form.

The metadata lookup targets:

```text
AR System Metadata: field
```

with fallback to:

```text
field
```

If a whole day range is full, the allocator rolls to the next day.

Copied fields keep their existing ids.

## PWA icon helper

Settings:

```text
Show icon picker button next to Icon properties
CSS icon catalog URL
```

Example CSS URL:

```text
https://<midtier>/arsys/pwa/styles.xxxxxxx.css
```

The plugin reads BMC's PWA CSS icon catalog dynamically. It extracts `d-icon-*` classes and uses the referenced `dpl-icon-font` `.woff` / `.woff2` files to render previews.

Main access path:

```text
Icon property picker button
```

The plugin tries to attach a `...` picker button to properties whose name/id is exactly `Icon`. Developer Studio's property grid is BMC-specific, so the inline button is best-effort, but it is the intended access path. The old top-level `Yrell Developer Tools` menu has been removed.

When an icon is selected, the class name is copied to the clipboard and the dialog closes without an extra confirmation dialog.

## Fast object lists

Settings:

```text
Load object lists with Custom/Overlay filter by default
Customization Type values
Debug logging for Fast object lists
```

Default values:

```text
2,4
```

Known customization type values:

- `0` = Base
- `1` = Overlaid
- `2` = Overlay
- `4` = Custom

This feature integrates the old FastForms Java agent into the normal Developer Tools plugin. It uses the same general idea as the old agent: when enabled, Developer Studio object lists are filtered toward the configured customization types so Base objects are not loaded/displayed by default.

The integrated implementation uses the plugin's OSGi weaving hook instead of a `-javaagent` line in `devstudio.ini`. You should remove the old FastForms javaagent settings if you use this integrated option.

Recommended value for normal custom development:

```text
2,4
```

## Keepalive

Settings:

```text
Keep AR server sessions alive
Keepalive interval (seconds)
```

Default interval:

```text
120
```

Allowed range:

```text
30-3600 seconds
```

When enabled, the plugin periodically calls `verifyUser()` on already connected AR Server sessions. It does not load forms, workflow or object lists.

## Logging

Useful startup arguments while testing:

```text
-clean -consoleLog
```

Developer Studio log file:

```text
<workspace>/.metadata/.log
```

Search for:

```text
Yrell Developer Tools
se.yrell.developertools
Fast Forms hook applied
Removed __c
Keepalive
```

## Notes for version 0.1.27

- The old top-level Developer Studio menu `Yrell Developer Tools` has been removed. The plugin is still configured through `Window -> Preferences -> Yrell Developer Tools`.
- The PWA Icon picker now has a `Clear` button. It writes an empty value to the selected `Icon` property so an existing icon can be removed.
