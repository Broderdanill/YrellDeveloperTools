# Yrell Developer Tools 0.1.30

A single Eclipse/Developer Studio plugin for BMC Helix Developer Studio helper functions.

Install the jar from `install/` into Developer Studio `x64/plugins`, remove older `se.yrell.developertools_*.jar` files, and start Developer Studio once with `-clean -consoleLog`.

Settings are available in:

`Window -> Preferences -> Yrell Developer Tools`

## Custom suffix cleanup

Setting: `Remove BMC's automatic __c suffix`

When enabled, the plugin removes BMC's automatically appended `__c` suffix only during new object/field creation paths. It is intentionally not a post-save cleanup feature.

Affected creation paths:

- default fields when a new form is created
- new fields dragged/added to an existing form
- field-name generation paths where BMC tries to append `__c`

Safety behavior:

- existing saved fields are not scanned or renamed
- form save/post-save does not rename fields
- if the clean name already exists, the plugin generates a clean unique name such as `Character Field 1` instead of leaving `Character Field__c`

## Default naming

Setting: `Table columns: set database name automatically`

Pattern setting: `Table column pattern`

Default pattern:

`col_{remote_form}_{remote_field_name}`

Supported tokens:

- `{form}`: current form name when available
- `{remote_form}`: the form that the table points to when available
- `{field_name}`: local field name
- `{remote_field_name}`: remote/source field name when available
- `{field_id}`: field ID when available

Generated names are normalized to lower-case ASCII with underscores. This area is structured so future default-name rules can be added as separate checkboxes and patterns.

## Automatic field IDs

Setting: `Enable automatic field ID assignment`

Required setting: `Developer ID (10-21)`

Format:

`<Developer ID><YY><MM><DD><NN>`

Example:

`1226062301`

The plugin calculates the next unused ID from `AR System Metadata: field` for the current server/day. It searches the current day range first, then rolls forward to the next day if the entire 01-99 range is full. It also remembers IDs assigned during the current Developer Studio session before the form is saved.

## PWA icon helper

Setting: `Show icon picker button next to Icon properties`

Required setting: `CSS icon catalog URL`

Example:

`https://<midtier>/arsys/pwa/styles.xxxxxxx.css`

The plugin reads the configured PWA CSS file, extracts `d-icon-*` classes, downloads/embeds the referenced `dpl-iconfont` `.woff`/`.woff2` fonts, and shows the CSS-based picker for `Icon` properties.

The icon catalog is preloaded in the background at Developer Studio startup when the URL is configured, so the first picker open should be faster.

The picker supports:

- search
- paging
- direct selection on click/double-click depending on the control
- `Clear` to set the Icon value to empty
- copy to clipboard when a value is selected or cleared

## Fast object lists

Setting: `Load object lists with Custom/Overlay filter by default`

Values setting: `Customization Type values`

Default:

`2,4`

Values:

- `0` = Base
- `1` = Overlaid
- `2` = Overlay
- `4` = Custom

This feature is intended to keep object lists focused on the configured customization types. In 0.1.30 the plugin also hooks `ARBaseNamedListProvider.getPartialObjects(...)` so Active Links, Filters and similar object lists can ask the server for names matching the configured Object Property overlay values before partial objects are loaded. This is intended to avoid the slow pattern where Developer Studio first loads all objects and only filters the UI afterwards. The configured values are authoritative, so Base is not accepted unless `0` is included in the setting.

Important: this uses OSGi weaving. To avoid loading Base objects at the server-query level, Developer Studio must load this plugin before BMC's object-list provider classes are loaded. After enabling/disabling this setting, restart Developer Studio with `-clean`. If those BMC classes were already loaded before the hook was installed, the plugin can still filter displayed results, but it cannot retroactively prevent the earlier server fetch in that already-started session. With debug logging enabled you should see log lines such as `server-side Fast object list filter used for ...`; if you only see UI filtering, the server-side hook did not run early enough or that object type uses a different provider path.

## Keepalive

Setting: `Keep AR server sessions alive`

Setting: `Keepalive interval (seconds)`

Default interval: `120`

Allowed range: `30-3600`

The plugin periodically calls `verifyUser()` on already connected AR Server sessions. It does not load object lists, forms or workflow.

## Logging

Useful startup command:

`DeveloperStudio.exe -clean -consoleLog`

The Eclipse workspace log is normally here:

`<workspace>/.metadata/.log`

