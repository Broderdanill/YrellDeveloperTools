# Yrell Developer Tools 0.1.34

Developer Studio helper plugin for BMC Helix/Remedy Developer Studio.

All features are **disabled by default**. Enable only the parts you want under:

`Window -> Preferences -> Yrell Developer Tools`

The plugin is compiled as Java 17 bytecode and is intended to run on JDK 17 and JDK 21.

## Installation

1. Close Developer Studio.
2. Remove older versions of this plugin and older standalone helper plugins, for example:
   - `se.yrell.developertools_*.jar`
   - `se.yrell.devstudio.keepalive_*.jar`
   - old `devstudio-fastforms` agent jars if they are no longer used separately
3. Copy `install/se.yrell.developertools_0.1.34.jar` to Developer Studio's `plugins` folder.
4. Start Developer Studio once with `-clean -consoleLog`.

## Fast object lists and the required Java agent step

Fast object lists can only make the initial object fetch fast if the jar is also loaded as a Java agent before BMC's list provider classes are loaded.

Add the same jar to `DeveloperStudio.ini`, for example:

```text
-javaagent:C:\Temp\se.yrell.developertools_0.1.34.jar
```

or, if the path works in your installation:

```text
-javaagent:C:\Program Files\BMC Software_25_3_Beta\DeveloperStudio\plugins\se.yrell.developertools_0.1.34.jar
```

If the jar is only installed as a normal Eclipse plugin, Developer Studio may first load all objects and only filter the result afterwards. That can be slower than not using the feature. The Preferences page now shows an IMPORTANT note and an agent-status line so this step is harder to miss.

After changing Fast object lists settings, restart Developer Studio with `-clean`.

## Custom suffix cleanup

Setting:

`Remove BMC's automatic __c suffix`

When enabled, the plugin removes BMC's automatic `__c` suffix during new object/name generation. It is intended to affect:

- new forms and their default fields
- new fields added to existing forms
- proposed names in UI creation flows

It does **not** run a post-save cleanup and must not rename old, already-saved fields. This avoids breaking workflow or references that already depend on an existing `__c` name.

## Default naming

Setting:

`Table columns: set database name automatically`

Pattern setting:

`Table column pattern`

Default pattern:

```text
col_{remote_form}_{remote_field_name}
```

Supported tokens:

- `{form}`
- `{remote_form}`
- `{field_name}`
- `{remote_field_name}`
- `{field_id}`

The generated database name is normalized to lower-case ASCII with underscores.

## Automatic field IDs

Setting:

`Enable automatic field ID assignment`

Developer ID is required when this feature is enabled. Valid range is currently `10-21` because generated IDs are ten-digit signed integer values.

Format:

```text
<Developer ID><YY><MM><DD><NN>
```

Example:

```text
1226062301
```

The plugin calculates the next value from the current day and checks AR metadata for already-used IDs. If the current day is full, it rolls forward to the next day.

## PWA icon helper

Setting:

`Show icon picker button next to Icon properties`

Required when enabled:

```text
CSS icon catalog URL
```

Example:

```text
https://<midtier>/arsys/pwa/styles.xxxxxxx.css
```

The plugin reads `d-icon-*` definitions from the PWA CSS and embeds referenced `dpl-iconfont` `.woff/.woff2` fonts in the picker preview. The icon picker opens from the `Icon` property button. It also supports clearing the value.

## Keepalive

Setting:

`Keep AR server sessions alive`

Interval range: `30-3600` seconds.

The current implementation calls `verifyUser()` on already connected AR Server sessions. It is intentionally lightweight and does not load forms, workflow or object lists.

Keepalive can reduce idle-session problems, but it is not a full fix for every hang. Developer Studio can also pause because of object-list/cache refreshes, RPC/API timeouts, authentication/token expiry, server-side transaction timeouts, RSSO/SSO idle timeout, load balancers, or network idle connection handling.



## Preference validation note

Version 0.1.34 fixes the Preferences page runtime error caused by compiling SWT Group.setLayout against the wrong stub signature. The Preferences page now uses the SWT Layout signature expected by Developer Studio, so the page can open again. Conditional fields are still validated only when their corresponding feature is enabled.
