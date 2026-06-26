# Yrell Developer Tools 0.1.31

Yrell Developer Tools is a single Eclipse/BMC Developer Studio plugin bundle with helper functions for BMC Helix Developer Studio.

The jar can be used in two ways:

1. As a normal Developer Studio plugin in `plugins/`.
2. As an optional Java agent for the **Fast object lists** feature. This is the only reliable way to affect Developer Studio's first server-side object-list query before BMC list-provider classes are loaded.

The jar is compiled for Java 17 bytecode and is intended to run on both JDK 17 and JDK 21.

## Installation

Close Developer Studio and remove older versions before installing:

```text
se.yrell.developertools_*.jar
se.yrell.devstudio.keepalive_*.jar
se.yrell.devstudio.nocustomsuffix_*.jar
se.arsbmc.devstudio.nocustomsuffix_*.jar
devstudio-fastforms-agent*.jar
```

Copy the new jar to:

```text
<DeveloperStudio>/plugins/se.yrell.developertools_0.1.31.jar
```

Start once with:

```text
-clean -consoleLog
```

Settings are available under:

```text
Window -> Preferences -> Yrell Developer Tools
```

## Custom suffix cleanup

Setting:

```text
Remove BMC's automatic __c suffix
```

When enabled, the plugin removes BMC's automatic `__c` suffix during new object/field creation flows.

It is intended to affect:

- default fields created while creating a new form
- new fields dragged into or added to a form
- generated field/view names while they are still new/unsaved

It intentionally does **not** run a post-save cleanup and does **not** rename old saved fields. This avoids breaking dependencies on existing objects.

If a generated base name already exists, the plugin tries to choose a clean unique name rather than keeping BMC's `__c` suffix. For example, if `Character Field` already exists and BMC generates `Character Field__c`, the plugin attempts to use a clean unique variant such as `Character Field 1`.

## Default naming

Settings:

```text
Table columns: set database name automatically
Table column pattern
```

Default pattern:

```text
col_{remote_form}_{remote_field_name}
```

Supported tokens:

```text
{form}
{remote_form}
{field_name}
{remote_field_name}
{field_id}
```

The generated database name is normalized to lower-case ASCII with underscores. The feature is designed so more default-name rules can be added later as separate checkboxes and patterns.

## Automatic field IDs

Settings:

```text
Enable automatic field ID assignment
Developer ID (10-21)
Skip panels/pages
```

Field ID format:

```text
<Developer ID><YY><MM><DD><NN>
```

Example:

```text
1226062301
```

When enabled, Developer ID is required. The plugin calculates the next unused value from AR metadata for the current day and rolls to the next day if the current day range is full.

Copied fields keep their existing field ID. New fields receive a generated ID unless they are panel/page fields and the skip setting is enabled.

## PWA icon helper

Settings:

```text
Show icon picker button next to Icon properties
CSS icon catalog URL
```

Example URL:

```text
https://<midtier>/arsys/pwa/styles.xxxxxxx.css
```

The icon helper reads BMC PWA `d-icon-*` classes from the configured CSS and embeds the referenced `dpl-iconfont` `.woff/.woff2` files in the preview. It does not include any PDF fallback or bundled icon images.

The icon picker is opened from the `Icon` property in the Properties view. Select an icon to write that class name to the property and to the clipboard. Use **Clear** to set the value to blank.

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

Meaning:

```text
0 = Base
1 = Overlaid
2 = Overlay
4 = Custom
```

### Important: true initial filtering requires agent mode

The old FastForms implementation worked because it was a Java agent. Java agents are loaded before BMC Developer Studio classes, so they can patch the initial server-side query path.

A normal Eclipse plugin can register a weaving hook only after the plugin is activated. By then Developer Studio may already have loaded BMC list-provider classes. In that case, the plugin can still filter displayed results, but it cannot reliably stop the initial 60,000-object server load.

To get the same early behavior as the old working plugin, use this same jar as a Java agent in addition to installing it as a plugin.

Add a line like this to the Developer Studio `.ini` before `-vmargs` if possible, or under `-vmargs` depending on your launcher setup:

```text
-javaagent:C:\Program Files\BMC Software\DeveloperStudio\plugins\se.yrell.developertools_0.1.31.jar
```

If the path contains spaces and the launcher does not accept it, use a path without spaces or the Windows short path form.

The Preferences page writes the agent settings to:

```text
%USERPROFILE%\.yrell-developertools\fastforms-agent.properties
```

The agent reads that file at JVM startup. After changing Fast object lists settings, restart Developer Studio for the agent to use the new values.

You can also override the file location with:

```text
-Dse.yrell.developertools.fastforms.config=C:\path\fastforms-agent.properties
```

Useful debug options:

```text
-Dbmc.ds.fastForms.debug=true
-Dbmc.ds.fastForms.logFile=C:\Temp\devstudio-fastforms.log
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

When enabled, the plugin periodically calls `verifyUser()` on already connected AR Server sessions. It does not load object lists or forms.

## Troubleshooting

Start Developer Studio with:

```text
-clean -consoleLog
```

Check:

```text
<workspace>/.metadata/.log
```

Useful messages to search for:

```text
Registered Yrell Developer Tools weaving hook
Startup initialized
[Yrell Developer Tools FastForms Agent] loaded v8
```

For Fast object lists, if the agent line is missing, Developer Studio may still load all objects first and then filter the UI. Agent mode is required to reliably alter the initial object-list query.
