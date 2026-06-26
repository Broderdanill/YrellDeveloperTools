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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import se.yrell.developertools.ToolsActivator;
import se.yrell.developertools.ToolsConstants;
import se.yrell.developertools.ToolsPreferences;
import se.yrell.developertools.keepalive.KeepAliveService;
import se.yrell.developertools.inspector.ObjectInsightViewSupport;

public class DeveloperStudioToolsPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
    private static final int MIN_DEVELOPER_ID = 10;
    private static final int MAX_DEVELOPER_ID = 21;

    private BooleanFieldEditor autoFieldEnabledEditor;
    private StringFieldEditor developerIdEditor;
    private BooleanFieldEditor defaultTableColumnsEnabledEditor;
    private StringFieldEditor iconCatalogUrlEditor;
    private BooleanFieldEditor iconHelperEnabledEditor;
    private StringFieldEditor tableColumnPatternEditor;
    private BooleanFieldEditor fastFormsEnabledEditor;
    private Label fastFormsAgentStatusLabel;
    private StringFieldEditor fastFormsValuesEditor;
    private BooleanFieldEditor fastFormsDebugEditor;
    private BooleanFieldEditor keepAliveEnabledEditor;
    private StringFieldEditor keepAliveIntervalEditor;
    private BooleanFieldEditor objectInsightEnabledEditor;
    private BooleanFieldEditor removeFromViewEnabledEditor;

    public DeveloperStudioToolsPreferencePage() {
        super(GRID);
        ToolsActivator activator = ToolsActivator.getDefault();
        if (activator != null) {
            setPreferenceStore(activator.getPreferenceStore());
        }
        setDescription("Developer Studio helper tools. All features are disabled by default and must be enabled explicitly.");
    }

    @Override
    public void init(IWorkbench workbench) {
        // No workbench-specific state needed.
    }

    @Override
    protected void createFieldEditors() {
        Composite parent = getFieldEditorParent();

        Composite suffixGroup = createGroup(parent, "Custom suffix cleanup");
        addField(new BooleanFieldEditor(ToolsConstants.PREF_REMOVE_CUSTOM_SUFFIX_ENABLED,
                "Remove BMC's automatic __c suffix", suffixGroup));
        addInfo(suffixGroup, "Removes BMC's automatic __c only while new forms/default fields or new fields are created. It does not run post-save cleanup and does not rename existing saved fields.");

        Composite namingGroup = createGroup(parent, "Default naming");
        defaultTableColumnsEnabledEditor = new BooleanFieldEditor(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED,
                "Table columns: set database name automatically", namingGroup);
        addField(defaultTableColumnsEnabledEditor);
        tableColumnPatternEditor = new StringFieldEditor(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN,
                "Table column pattern", namingGroup);
        tableColumnPatternEditor.setEmptyStringAllowed(true);
        addField(tableColumnPatternEditor);
        addInfo(namingGroup, "Default: col_{remote_form}_{remote_field_name}. Supported tokens: {form}, {remote_form}, {field_name}, {remote_field_name}, {field_id}. The result is normalized to lower-case ASCII with underscores.");

        Composite fieldIdGroup = createGroup(parent, "Automatic field IDs");
        autoFieldEnabledEditor = new BooleanFieldEditor(ToolsConstants.PREF_AUTO_FIELD_ID_ENABLED,
                "Enable automatic field ID assignment", fieldIdGroup);
        addField(autoFieldEnabledEditor);
        developerIdEditor = new StringFieldEditor(ToolsConstants.PREF_AUTO_FIELD_ID_DEVELOPER_ID,
                "Developer ID (10-21)", fieldIdGroup);
        developerIdEditor.setEmptyStringAllowed(true);
        developerIdEditor.setTextLimit(2);
        addField(developerIdEditor);
        addField(new BooleanFieldEditor(ToolsConstants.PREF_AUTO_FIELD_ID_SKIP_PANELS,
                "Skip panels/pages", fieldIdGroup));
        addInfo(fieldIdGroup, "Field ID format: <Developer ID><YY><MM><DD><NN>, for example 1226062301. The plugin calculates the next unused value for the current day from AR System Metadata: field and rolls to the next day if needed.");

        Composite iconGroup = createGroup(parent, "PWA icon helper");
        iconHelperEnabledEditor = new BooleanFieldEditor(ToolsConstants.PREF_PWA_ICON_HELPER_ENABLED,
                "Show icon picker button next to Icon properties", iconGroup);
        addField(iconHelperEnabledEditor);
        iconCatalogUrlEditor = new StringFieldEditor(ToolsConstants.PREF_PWA_ICON_CATALOG_URL,
                "CSS icon catalog URL", iconGroup);
        iconCatalogUrlEditor.setEmptyStringAllowed(true);
        addField(iconCatalogUrlEditor);
        addInfo(iconGroup, "Required for icon preview. Example: https://<midtier>/arsys/pwa/styles.xxxxxxx.css. The icon catalog is preloaded at startup when this URL is set.");

        Composite fastGroup = createGroup(parent, "Fast object lists");
        fastFormsEnabledEditor = new BooleanFieldEditor(ToolsConstants.PREF_FAST_FORMS_ENABLED,
                "Load object lists with Custom/Overlay filter by default", fastGroup);
        addField(fastFormsEnabledEditor);
        fastFormsValuesEditor = new StringFieldEditor(ToolsConstants.PREF_FAST_FORMS_VALUES,
                "Customization Type values", fastGroup);
        fastFormsValuesEditor.setEmptyStringAllowed(true);
        addField(fastFormsValuesEditor);
        fastFormsDebugEditor = new BooleanFieldEditor(ToolsConstants.PREF_FAST_FORMS_DEBUG,
                "Debug logging for Fast object lists", fastGroup);
        addField(fastFormsDebugEditor);
        addImportant(fastGroup, "IMPORTANT: Fast object lists only becomes truly fast when the jar is also loaded as a Java agent before Developer Studio loads BMC list classes. Without -javaagent, Developer Studio can first load all objects and only filter afterwards, which can be slower. Add this line to DeveloperStudio.ini, restart with -clean, then enable this feature:\n-javaagent:<path-to-plugins>/se.yrell.developertools_0.1.38.jar");
        fastFormsAgentStatusLabel = addInfo(fastGroup, fastFormsAgentStatusText());
        addCopyAgentButton(fastGroup);

        Composite keepAliveGroup = createGroup(parent, "Keepalive");
        keepAliveEnabledEditor = new BooleanFieldEditor(ToolsConstants.PREF_KEEPALIVE_ENABLED,
                "Keep AR server sessions alive", keepAliveGroup);
        addField(keepAliveEnabledEditor);
        keepAliveIntervalEditor = new StringFieldEditor(ToolsConstants.PREF_KEEPALIVE_INTERVAL_SECONDS,
                "Keepalive interval (seconds)", keepAliveGroup);
        keepAliveIntervalEditor.setEmptyStringAllowed(true);
        addField(keepAliveIntervalEditor);
        addInfo(keepAliveGroup, "When enabled, the plugin periodically calls verifyUser() on already connected AR Server sessions. This is lightweight and does not load object lists or forms. Default interval: 120 seconds.");

        Composite insightGroup = createGroup(parent, "Object insight panel");
        objectInsightEnabledEditor = new BooleanFieldEditor(ToolsConstants.PREF_OBJECT_INSIGHT_ENABLED,
                "Show selected object details panel", insightGroup);
        addField(objectInsightEnabledEditor);
        addInfo(insightGroup, "Opens the Object Insight view. It shows field permissions as one row per group, plus table qualification and sort when a table field is selected.");

        Composite viewActionsGroup = createGroup(parent, "Form view actions");
        removeFromViewEnabledEditor = new BooleanFieldEditor(ToolsConstants.PREF_REMOVE_FROM_VIEW_ENABLED,
                "Enable right-click action: Remove from view", viewActionsGroup);
        addField(removeFromViewEnabledEditor);
        addInfo(viewActionsGroup, "Adds Remove from view on form-editor selections when the selected field also exists in another view. It removes only the current view instance; the field remains on the form and in other views.");
    }

    private Composite createGroup(Composite parent, String text) {
        Group group = new Group(parent, SWT.NONE);
        group.setText(text);
        group.setLayout(new org.eclipse.swt.layout.GridLayout(2, false));
        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.verticalIndent = 8;
        group.setLayoutData(data);
        return group;
    }

    private Label addInfo(Composite parent, String text) {
        Label label = new Label(parent, SWT.WRAP);
        label.setText(text);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        data.widthHint = 720;
        data.verticalIndent = 2;
        label.setLayoutData(data);
        return label;
    }

    private Label addImportant(Composite parent, String text) {
        Label label = new Label(parent, SWT.WRAP);
        label.setText(text);
        GridData data = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
        data.widthHint = 720;
        data.verticalIndent = 4;
        label.setLayoutData(data);
        return label;
    }

    private void addCopyAgentButton(Composite parent) {
        Button button = new Button(parent, SWT.PUSH);
        button.setText("Copy suggested -javaagent line");
        GridData data = new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1);
        button.setLayoutData(data);
        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                Clipboard clipboard = new Clipboard(getShell().getDisplay());
                try {
                    clipboard.setContents(new Object[] { suggestedJavaAgentLine() }, new Transfer[] { TextTransfer.getInstance() });
                    MessageDialog.openInformation(getShell(), "Yrell Developer Tools", "Suggested -javaagent line copied to clipboard. Add it to DeveloperStudio.ini, then restart Developer Studio with -clean.");
                } finally {
                    clipboard.dispose();
                }
            }
        });
    }

    private String fastFormsAgentStatusText() {
        boolean active = Boolean.parseBoolean(System.getProperty("se.yrell.developertools.fastFormsAgent.active", "false"));
        if (active) {
            return "Fast object lists agent status: ACTIVE. Initial server-side object list filtering can be applied after restart with this feature enabled.";
        }
        return "Fast object lists agent status: NOT ACTIVE. The checkbox can still write agent settings, but true initial server-side filtering will not happen until Developer Studio is started with -javaagent.";
    }

    private String suggestedJavaAgentLine() {
        try {
            ToolsActivator activator = ToolsActivator.getDefault();
            if (activator != null && activator.getBundle() != null) {
                String location = activator.getBundle().getLocation();
                String path = location;
                if (path.startsWith("reference:")) {
                    path = path.substring("reference:".length());
                }
                if (path.startsWith("file:/")) {
                    java.net.URI uri = java.net.URI.create(path);
                    path = new java.io.File(uri).getAbsolutePath();
                }
                return "-javaagent:" + path;
            }
        } catch (Throwable ignored) {
            // Fall through to generic example.
        }
        return "-javaagent:C:\\Temp\\se.yrell.developertools_0.1.38.jar";
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
        boolean defaultNamingEnabled = defaultTableColumnsEnabledEditor != null && defaultTableColumnsEnabledEditor.getBooleanValue();
        if (defaultNamingEnabled && pattern.length() == 0) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "Table column pattern cannot be empty when table-column default naming is enabled. Example: col_{remote_form}_{remote_field_name}");
            return false;
        }

        String cssUrl = iconCatalogUrlEditor == null ? "" : iconCatalogUrlEditor.getStringValue().trim();
        boolean iconHelperEnabled = iconHelperEnabledEditor != null && iconHelperEnabledEditor.getBooleanValue();
        if (iconHelperEnabled && cssUrl.length() == 0) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "CSS icon catalog URL is required when the PWA icon helper is enabled.\n\nExample:\nhttps://<midtier>/arsys/pwa/styles.xxxxxxx.css");
            return false;
        }
        if (iconHelperEnabled && !looksLikeUrl(cssUrl)) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "CSS icon catalog must be an http:// or https:// URL.\n\nExample:\nhttps://<midtier>/arsys/pwa/styles.xxxxxxx.css");
            return false;
        }


        String fastValues = fastFormsValuesEditor == null ? "" : fastFormsValuesEditor.getStringValue().trim();
        boolean fastEnabledForValidation = fastFormsEnabledEditor != null && fastFormsEnabledEditor.getBooleanValue();
        if (fastEnabledForValidation && !isValidFastFormsValues(fastValues)) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "Customization Type values must be a comma-separated list using 0, 1, 2 and/or 4 when Fast object lists is enabled.\n\nExample: 2,4");
            return false;
        }

        boolean keepAliveEnabled = keepAliveEnabledEditor != null && keepAliveEnabledEditor.getBooleanValue();
        String intervalText = keepAliveIntervalEditor == null ? "" : keepAliveIntervalEditor.getStringValue().trim();
        if (keepAliveEnabled) {
            int interval;
            try {
                interval = Integer.parseInt(intervalText);
            } catch (NumberFormatException e) {
                MessageDialog.openError(getShell(), "Yrell Developer Tools",
                        "Keepalive interval must be a number between "
                                + ToolsConstants.MIN_KEEPALIVE_INTERVAL_SECONDS + " and "
                                + ToolsConstants.MAX_KEEPALIVE_INTERVAL_SECONDS + " seconds.");
                return false;
            }
            if (interval < ToolsConstants.MIN_KEEPALIVE_INTERVAL_SECONDS
                    || interval > ToolsConstants.MAX_KEEPALIVE_INTERVAL_SECONDS) {
                MessageDialog.openError(getShell(), "Yrell Developer Tools",
                        "Keepalive interval must be between "
                                + ToolsConstants.MIN_KEEPALIVE_INTERVAL_SECONDS + " and "
                                + ToolsConstants.MAX_KEEPALIVE_INTERVAL_SECONDS + " seconds.");
                return false;
            }
        }

        boolean ok = super.performOk();
        if (ok) {
            KeepAliveService.getInstance().reconfigure();
            boolean fastEnabled = fastFormsEnabledEditor != null && fastFormsEnabledEditor.getBooleanValue();
            boolean fastDebug = fastFormsDebugEditor != null && fastFormsDebugEditor.getBooleanValue();
            ToolsPreferences.writeFastFormsAgentProperties(fastEnabled, fastValues, fastDebug);
            if (fastFormsAgentStatusLabel != null && !fastFormsAgentStatusLabel.isDisposed()) {
                fastFormsAgentStatusLabel.setText(fastFormsAgentStatusText());
                fastFormsAgentStatusLabel.getParent().layout(true, true);
            }
            if (objectInsightEnabledEditor != null && objectInsightEnabledEditor.getBooleanValue()) {
                ObjectInsightViewSupport.openAsync();
            } else {
                ObjectInsightViewSupport.hideAsync();
            }
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
