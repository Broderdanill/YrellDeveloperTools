package se.yrell.developertools.internal.preferences;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import se.yrell.developertools.ToolsActivator;
import se.yrell.developertools.ToolsConstants;
import se.yrell.developertools.keepalive.KeepAliveService;

public class DeveloperStudioToolsPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
    private static final int MIN_DEVELOPER_ID = 10;
    private static final int MAX_DEVELOPER_ID = 21;

    private BooleanFieldEditor autoFieldEnabledEditor;
    private StringFieldEditor developerIdEditor;
    private StringFieldEditor iconCatalogUrlEditor;
    private StringFieldEditor tableColumnPatternEditor;
    private StringFieldEditor fastFormsValuesEditor;
    private IntegerFieldEditor keepAliveIntervalEditor;

    public DeveloperStudioToolsPreferencePage() {
        super(GRID);
        ToolsActivator activator = ToolsActivator.getDefault();
        if (activator != null) {
            setPreferenceStore(activator.getPreferenceStore());
        }
        setDescription("Developer Studio helper tools for BMC suffix cleanup, default naming, automatic field IDs and CSS-based PWA icon selection.");
    }

    @Override
    public void init(IWorkbench workbench) {
        // No workbench-specific state needed.
    }

    @Override
    protected void createFieldEditors() {
        Composite parent = getFieldEditorParent();

        addSection(parent, "Custom suffix cleanup");
        addField(new BooleanFieldEditor(ToolsConstants.PREF_REMOVE_CUSTOM_SUFFIX_ENABLED,
                "Remove BMC's automatic __c suffix", parent));
        addInfo(parent, "When enabled, proposed object/field/view names are cleaned while you type. Post-save cleanup has been removed to avoid changing saved objects unexpectedly.");

        addSection(parent, "Default naming");
        addField(new BooleanFieldEditor(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED,
                "Table columns: set database name automatically", parent));
        tableColumnPatternEditor = new StringFieldEditor(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN,
                "Table column pattern", parent);
        tableColumnPatternEditor.setEmptyStringAllowed(false);
        addField(tableColumnPatternEditor);
        addInfo(parent, "Default: col_{remote_form}_{remote_field_name}. Supported tokens: {form}, {remote_form}, {field_name}, {remote_field_name}, {field_id}. The generated database name is normalized to lower-case ASCII with underscores. This section is designed so more default-name rules can be added later as separate checkboxes/patterns.");

        addSection(parent, "Automatic field IDs");
        autoFieldEnabledEditor = new BooleanFieldEditor(ToolsConstants.PREF_AUTO_FIELD_ID_ENABLED,
                "Enable automatic field ID assignment", parent);
        addField(autoFieldEnabledEditor);

        developerIdEditor = new StringFieldEditor(ToolsConstants.PREF_AUTO_FIELD_ID_DEVELOPER_ID,
                "Developer ID (10-21)", parent);
        developerIdEditor.setEmptyStringAllowed(true);
        developerIdEditor.setTextLimit(2);
        addField(developerIdEditor);

        addField(new BooleanFieldEditor(ToolsConstants.PREF_AUTO_FIELD_ID_SKIP_PANELS,
                "Skip panels/pages", parent));
        addInfo(parent, "Field ID format: <Developer ID><YY><MM><DD><NN>, for example 1226062301. The plugin calculates the next unused value for the current day from the AR metadata form 'field'. If a whole day range is full it rolls to the next day.");

        addSection(parent, "PWA icon helper");
        addField(new BooleanFieldEditor(ToolsConstants.PREF_PWA_ICON_HELPER_ENABLED,
                "Show icon picker button next to Icon properties", parent));
        iconCatalogUrlEditor = new StringFieldEditor(ToolsConstants.PREF_PWA_ICON_CATALOG_URL,
                "CSS icon catalog URL", parent);
        iconCatalogUrlEditor.setEmptyStringAllowed(false);
        addField(iconCatalogUrlEditor);
        addInfo(parent, "Required for icon preview. Example: https://<midtier>/arsys/pwa/styles.xxxxxxx.css. The plugin reads d-icon-* classes and embeds referenced dpl-iconfont .woff/.woff2 fonts in the preview.");


        addSection(parent, "Fast object lists");
        addField(new BooleanFieldEditor(ToolsConstants.PREF_FAST_FORMS_ENABLED,
                "Load object lists with Custom/Overlay filter by default", parent));
        fastFormsValuesEditor = new StringFieldEditor(ToolsConstants.PREF_FAST_FORMS_VALUES,
                "Customization Type values", parent);
        fastFormsValuesEditor.setEmptyStringAllowed(false);
        addField(fastFormsValuesEditor);
        addField(new BooleanFieldEditor(ToolsConstants.PREF_FAST_FORMS_DEBUG,
                "Debug logging for Fast object lists", parent));
        addInfo(parent, "Default: 2,4. Values: 0=Base, 1=Overlaid, 2=Overlay, 4=Custom. The normal recommended value is 2,4 so Base objects are not loaded by default. This replaces the old separate devstudio-fastforms Java agent and requires no devstudio.ini -javaagent line. Restart Developer Studio with -clean after enabling/disabling this feature because the BMC list-provider classes must be woven before they are loaded.");

        addSection(parent, "Keepalive");
        addField(new BooleanFieldEditor(ToolsConstants.PREF_KEEPALIVE_ENABLED,
                "Keep AR server sessions alive", parent));
        keepAliveIntervalEditor = new IntegerFieldEditor(ToolsConstants.PREF_KEEPALIVE_INTERVAL_SECONDS,
                "Keepalive interval (seconds)", parent);
        keepAliveIntervalEditor.setValidRange(ToolsConstants.MIN_KEEPALIVE_INTERVAL_SECONDS,
                ToolsConstants.MAX_KEEPALIVE_INTERVAL_SECONDS);
        addField(keepAliveIntervalEditor);
        addInfo(parent, "When enabled, the plugin periodically calls verifyUser() on already connected AR Server sessions. This is intentionally lightweight and does not load object lists or forms. Default interval: 120 seconds.");
    }

    private void addSection(Composite parent, String text) {
        Label label = new Label(parent, SWT.NONE);
        label.setText(text);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        data.verticalIndent = 8;
        label.setLayoutData(data);
    }

    private void addInfo(Composite parent, String text) {
        Label label = new Label(parent, SWT.WRAP);
        label.setText(text);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        data.widthHint = 560;
        data.verticalIndent = 2;
        label.setLayoutData(data);
    }

    @Override
    public boolean performOk() {
        String developerIdText = developerIdEditor == null ? "" : developerIdEditor.getStringValue().trim();
        boolean enabled = autoFieldEnabledEditor != null && autoFieldEnabledEditor.getBooleanValue();

        if (enabled && !isValidDeveloperId(developerIdText)) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "Developer ID must be set when Automatic Field ID is enabled. It must be a number from 10 to 21.\n\n22 and above cannot be used because AR field IDs are signed integers and the generated 10-digit value would exceed the maximum allowed value.");
            return false;
        }

        String pattern = tableColumnPatternEditor == null ? "" : tableColumnPatternEditor.getStringValue().trim();
        if (pattern.length() == 0) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "Table column pattern cannot be empty. Example: col_{remote_form}_{remote_field_name}");
            return false;
        }

        String cssUrl = iconCatalogUrlEditor == null ? "" : iconCatalogUrlEditor.getStringValue().trim();
        if (cssUrl.length() == 0) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "CSS icon catalog URL is required for the PWA icon helper.\n\nExample:\nhttps://<midtier>/arsys/pwa/styles.xxxxxxx.css");
            return false;
        }
        if (!looksLikeUrl(cssUrl)) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "CSS icon catalog must be an http:// or https:// URL.\n\nExample:\nhttps://<midtier>/arsys/pwa/styles.xxxxxxx.css");
            return false;
        }


        String fastValues = fastFormsValuesEditor == null ? "" : fastFormsValuesEditor.getStringValue().trim();
        if (!isValidFastFormsValues(fastValues)) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "Customization Type values must be a comma-separated list using 0, 1, 2 and/or 4.\n\nExample: 2,4");
            return false;
        }

        int interval = keepAliveIntervalEditor == null ? ToolsConstants.DEFAULT_KEEPALIVE_INTERVAL_SECONDS
                : keepAliveIntervalEditor.getIntValue();
        if (interval < ToolsConstants.MIN_KEEPALIVE_INTERVAL_SECONDS
                || interval > ToolsConstants.MAX_KEEPALIVE_INTERVAL_SECONDS) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "Keepalive interval must be between "
                            + ToolsConstants.MIN_KEEPALIVE_INTERVAL_SECONDS + " and "
                            + ToolsConstants.MAX_KEEPALIVE_INTERVAL_SECONDS + " seconds.");
            return false;
        }

        boolean ok = super.performOk();
        if (ok) {
            KeepAliveService.getInstance().reconfigure();
        }
        return ok;
    }

    private boolean isValidDeveloperId(String developerIdText) {
        try {
            int developerId = Integer.parseInt(developerIdText);
            return developerId >= MIN_DEVELOPER_ID && developerId <= MAX_DEVELOPER_ID;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    private boolean isValidFastFormsValues(String value) {
        if (value == null || value.trim().length() == 0) {
            return false;
        }
        String[] parts = value.split(",");
        for (String part : parts) {
            String p = part.trim();
            if (!("0".equals(p) || "1".equals(p) || "2".equals(p) || "4".equals(p)
                    || "base".equalsIgnoreCase(p) || "overlaid".equalsIgnoreCase(p)
                    || "overlay".equalsIgnoreCase(p) || "custom".equalsIgnoreCase(p))) {
                return false;
            }
        }
        return true;
    }

    private boolean looksLikeUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }
}
