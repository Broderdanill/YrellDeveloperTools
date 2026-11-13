package se.yrell.developertools.internal.preferences;

import java.io.File;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsActivator;
import se.yrell.developertools.ToolsConstants;
import se.yrell.developertools.ToolsPreferences;
import se.yrell.developertools.inspector.ObjectInsightViewSupport;
import se.yrell.developertools.keepalive.KeepAliveService;
import se.yrell.developertools.midtier.MidTierConfiguration;
import se.yrell.developertools.midtier.MidTierFlushAvailability;
import se.yrell.developertools.midtier.MidTierMappingDialog;

/**
 * Single-page settings UI with a compact feature table on the left and contextual
 * configuration/details on the right. This avoids an ever-growing wall of
 * checkboxes as more Developer Studio helper modules are added.
 */
public class DeveloperStudioToolsPreferencePage extends PreferencePage implements IWorkbenchPreferencePage {
    private static final int MIN_DEVELOPER_ID = 10;
    private static final int MAX_DEVELOPER_ID = 21;
    private static final String GITHUB_URL = "https://github.com/Broderdanill/YrellDeveloperTools";

    private static final String FEATURE_SUFFIX = "suffix";
    private static final String FEATURE_TABLE_NAMES = "tableNames";
    private static final String FEATURE_AUTO_IDS = "autoIds";
    private static final String FEATURE_PWA_ICONS = "pwaIcons";
    private static final String FEATURE_FAST_OBJECTS = "fastObjects";
    private static final String FEATURE_OBJECT_SORTING = "objectSorting";
    private static final String FEATURE_MIDTIER_CACHE = "midTierCache";
    private static final String FEATURE_KEEPALIVE = "keepalive";
    private static final String FEATURE_MESSAGE_SUPPRESSION = "messageSuppression";
    private static final String FEATURE_INSIGHT = "insight";
    private static final String FEATURE_VIEW_ACTIONS = "viewActions";
    private static final String FEATURE_OBJECT_LIST_SEARCH = "objectListSearch";
    private static final String FEATURE_WORKFLOW_FIELD_MAP_LAYOUT = "workflowFieldMapLayout";

    private final Map<String, Feature> features = new LinkedHashMap<String, Feature>();

    private Table featureTable;
    private Composite detailHost;
    private String selectedFeatureId = FEATURE_SUFFIX;

    private boolean suffixEnabled;
    private boolean tableNamesEnabled;
    private String tableColumnPattern;
    private boolean autoIdsEnabled;
    private String developerId;
    private boolean skipPanels;
    private boolean pwaIconsEnabled;
    private String iconCssUrl;
    private boolean fastObjectsEnabled;
    private String fastObjectValues;
    private boolean fastObjectDebug;
    private boolean objectSortingEnabled;
    private String objectSortColumn;
    private String objectSortDirection;
    private boolean midTierCacheEnabled;
    private final List<MidTierConfiguration.Mapping> midTierMappings = new ArrayList<>();
    private boolean messageSuppressionEnabled;
    private String messageSuppressionCodes;
    private boolean keepAliveEnabled;
    private String keepAliveInterval;
    private boolean insightEnabled;
    private boolean removeFromViewEnabled;
    private boolean objectListSearchEnhancerEnabled;
    private boolean workflowFieldMapLayoutEnabled;

    public DeveloperStudioToolsPreferencePage() {
        ToolsActivator activator = ToolsActivator.getDefault();
        if (activator != null) {
            setPreferenceStore(activator.getPreferenceStore());
        }
        setTitle("Yrell Developer Tools");
        setDescription("Select a tool to configure it. All modules are disabled by default and must be enabled explicitly.");
        initFeatures();
    }

    @Override
    public void init(IWorkbench workbench) {
        // No workbench-specific state needed.
    }

    @Override
    protected Control createContents(Composite parent) {
        loadValues();

        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(1, false));
        root.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createHeader(root);

        Composite body = new Composite(root, SWT.NONE);
        GridLayout bodyLayout = new GridLayout(2, false);
        bodyLayout.marginWidth = 0;
        bodyLayout.marginHeight = 0;
        bodyLayout.horizontalSpacing = 12;
        body.setLayout(bodyLayout);
        body.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createFeatureTable(body);
        createDetailHost(body);

        selectFeature(selectedFeatureId);
        Dialog.applyDialogFont(root);
        return root;
    }

    private void createHeader(Composite parent) {
        Composite header = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout(1, false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        header.setLayout(layout);
        header.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        Label title = new Label(header, SWT.NONE);
        title.setText("Developer Studio helper tools");

        Link link = new Link(header, SWT.NONE);
        link.setText("Source code: <a href=\"" + GITHUB_URL + "\">" + GITHUB_URL + "</a>");
        link.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        link.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                Program.launch(GITHUB_URL);
            }
        });
    }

    private void createFeatureTable(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Settings");
        group.setLayout(new GridLayout(1, false));
        GridData groupData = new GridData(SWT.FILL, SWT.FILL, false, true);
        groupData.widthHint = 340;
        group.setLayoutData(groupData);

        featureTable = new Table(group, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.CHECK);
        featureTable.setHeaderVisible(true);
        featureTable.setLinesVisible(true);
        featureTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        TableColumn nameColumn = new TableColumn(featureTable, SWT.LEFT);
        nameColumn.setText("Setting name");
        nameColumn.setWidth(230);
        TableColumn enabledColumn = new TableColumn(featureTable, SWT.LEFT);
        enabledColumn.setText("Enabled");
        enabledColumn.setWidth(80);

        for (Feature feature : features.values()) {
            TableItem item = new TableItem(featureTable, SWT.NONE);
            item.setText(new String[] { feature.name, enabledText(feature.id) });
            item.setChecked(isFeatureEnabled(feature.id));
            item.setData(feature);
        }

        featureTable.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                TableItem item = (TableItem) e.item;
                if (item == null) {
                    return;
                }
                Feature feature = (Feature) item.getData();
                if (feature == null) {
                    return;
                }
                if (e.detail == SWT.CHECK) {
                    setFeatureEnabled(feature.id, item.getChecked());
                    item.setText(1, enabledText(feature.id));
                }
                selectedFeatureId = feature.id;
                renderDetails(feature.id);
            }
        });

        addSmallInfo(group, "Tick a row to enable the module. Select a row to edit its configuration and see restart notes.");
    }

    private void createDetailHost(Composite parent) {
        Group group = new Group(parent, SWT.NONE);
        group.setText("Selected setting");
        group.setLayout(new GridLayout(1, false));
        group.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        detailHost = new Composite(group, SWT.NONE);
        detailHost.setLayout(new GridLayout(1, false));
        detailHost.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
    }

    private void selectFeature(String id) {
        if (featureTable == null || featureTable.isDisposed()) {
            return;
        }
        TableItem[] items = featureTable.getItems();
        for (int i = 0; i < items.length; i++) {
            Feature feature = (Feature) items[i].getData();
            if (feature != null && feature.id.equals(id)) {
                featureTable.setSelection(i);
                selectedFeatureId = id;
                renderDetails(id);
                return;
            }
        }
        if (items.length > 0) {
            featureTable.setSelection(0);
            Feature feature = (Feature) items[0].getData();
            selectedFeatureId = feature.id;
            renderDetails(feature.id);
        }
    }

    private void renderDetails(String featureId) {
        if (detailHost == null || detailHost.isDisposed()) {
            return;
        }
        Control[] children = detailHost.getChildren();
        for (int i = 0; i < children.length; i++) {
            children[i].dispose();
        }
        Feature feature = features.get(featureId);
        if (feature == null) {
            return;
        }

        Label title = new Label(detailHost, SWT.WRAP);
        title.setText(feature.name + (isFeatureEnabled(feature.id) ? "  (enabled)" : "  (disabled)"));
        title.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        addInfo(detailHost, feature.description);
        if (feature.restartNote != null && feature.restartNote.length() > 0) {
            addImportant(detailHost, feature.restartNote);
        }

        if (FEATURE_SUFFIX.equals(featureId)) {
            addInfo(detailHost, "Cleans BMC's automatic __c suffix on proposed new object and field names. Existing saved objects are not mass-renamed and there is no post-save cleanup.");
        } else if (FEATURE_MESSAGE_SUPPRESSION.equals(featureId)) {
            addTextSetting(detailHost, "Suppress warning codes", messageSuppressionCodes, new ValueSetter() {
                public void set(String value) { messageSuppressionCodes = value; }
            });
            addInfo(detailHost, "Comma-separated BMC codes (default 5026,5033,5384,5385,5386). Only messages with severity WARNING are hidden. Errors, fatal errors and save failures are never suppressed.");
            addImportant(detailHost, "Warning popups and the Problems dialog are affected; BMC's validation and AR save result are not modified. Restart Developer Studio with -clean after installation.");
        } else if (FEATURE_TABLE_NAMES.equals(featureId)) {
            addTextSetting(detailHost, "Table column database-name pattern", tableColumnPattern, new ValueSetter() {
                public void set(String value) { tableColumnPattern = value; }
            });
            addInfo(detailHost, "Default: col_{remote_form}_{remote_field_name}. Supported tokens: {form}, {remote_form}, {field_name}, {remote_field_name}, {field_id}. The generated database name is normalized to lower-case ASCII with underscores.");
        } else if (FEATURE_AUTO_IDS.equals(featureId)) {
            addTextSetting(detailHost, "Developer ID (10-21)", developerId, new ValueSetter() {
                public void set(String value) { developerId = value; }
            });
            addCheckSetting(detailHost, "Skip panels/pages", skipPanels, new BooleanSetter() {
                public void set(boolean value) { skipPanels = value; }
            });
            addInfo(detailHost, "Field ID format: <Developer ID><YY><MM><DD><NN>, for example 1226062301. The next value is calculated from AR System Metadata: field and rolls to the next day if a day range is full.");
        } else if (FEATURE_PWA_ICONS.equals(featureId)) {
            addTextSetting(detailHost, "CSS icon catalog URL", iconCssUrl, new ValueSetter() {
                public void set(String value) { iconCssUrl = value; }
            });
            addInfo(detailHost, "Example: https://<midtier>/arsys/pwa/styles.xxxxxxx.css. The catalog is preloaded at startup when this URL is set.");
        } else if (FEATURE_FAST_OBJECTS.equals(featureId)) {
            addTextSetting(detailHost, "Customization Type values", fastObjectValues, new ValueSetter() {
                public void set(String value) { fastObjectValues = value; }
            });
            addCheckSetting(detailHost, "Debug logging for Fast object lists", fastObjectDebug, new BooleanSetter() {
                public void set(boolean value) { fastObjectDebug = value; }
            });
            addImportant(detailHost, fastFormsAgentStatusText());
            addImportant(detailHost, "IMPORTANT: This feature is only truly fast when the same jar is loaded with -javaagent in DeveloperStudio.ini. Without -javaagent, Developer Studio can load all objects first and filter afterwards, which can be slower.");
            Button copy = new Button(detailHost, SWT.PUSH);
            copy.setText("Copy suggested -javaagent line");
            copy.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
            copy.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(SelectionEvent e) {
                    copyAgentLine();
                }
            });
            addInfo(detailHost, "Values: 0=Base, 1=Overlaid, 2=Overlay, 4=Custom. Normal recommendation is 2,4. Change values, Apply, then restart Developer Studio with -clean.");
        } else if (FEATURE_OBJECT_SORTING.equals(featureId)) {
            addComboSetting(detailHost, "Default sort column", new String[] {"Modified", "Name", "Object type"},
                    new String[] {"LASTUPDATETIME", "NAME", "__OBJECT_TYPE_COLUMN__"},
                    objectSortColumn, new ValueSetter() {
                public void set(String value) { objectSortColumn = value; }
            });
            addComboSetting(detailHost, "Sort direction", new String[] {"Newest / Z-A first (descending)", "Oldest / A-Z first (ascending)"},
                    new String[] {"DESC", "ASC"}, objectSortDirection, new ValueSetter() {
                public void set(String value) { objectSortDirection = value; }
            });
            addInfo(detailHost, "One rule for all object types in Object List, searches and compatible object selection dialogs. Modified is displayed when BMC provides its timestamp metadata. Lists where BMC disables sorting or omits the selected field are left unchanged. Manual column sorting continues to work.");
            addImportant(detailHost, "Restart Developer Studio with -clean after installation so the BMC list hooks are registered before list components load. Applying a changed rule takes effect for newly loaded object lists.");
        } else if (FEATURE_MIDTIER_CACHE.equals(featureId)) {
            createMidTierSettings(detailHost);
        } else if (FEATURE_KEEPALIVE.equals(featureId)) {
            addTextSetting(detailHost, "Keepalive interval (seconds)", keepAliveInterval, new ValueSetter() {
                public void set(String value) { keepAliveInterval = value; }
            });
            addInfo(detailHost, "Periodically calls verifyUser() on already connected AR Server sessions. It does not load object lists or forms. Use this only if idle sessions drop unexpectedly.");
        } else if (FEATURE_INSIGHT.equals(featureId)) {
            addInfo(detailHost, "Opens the Object Insight view. Current focus: field permissions as one row per group, plus table qualification and table sort when a table field is selected.");
        } else if (FEATURE_VIEW_ACTIONS.equals(featureId)) {
            addInfo(detailHost, "Adds right-click action Remove from view when a selected field exists in another view. It removes only the selected field from the current view; the field remains on the form and in other views.");
        } else if (FEATURE_OBJECT_LIST_SEARCH.equals(featureId)) {
            addInfo(detailHost, "Keeps the text in Developer Studio's built-in object-list search field when you change Display items where. The search is immediately applied to the newly selected column instead of being cleared.");
            addInfo(detailHost, "When the search field is created after this module is enabled, it is created with SWT's native search/cancel icon so the small X clears the text and keeps focus in the field.");
        } else if (FEATURE_WORKFLOW_FIELD_MAP_LAYOUT.equals(featureId)) {
            addInfo(detailHost, "Stretches the Value column in workflow field-map tables, for example Set Fields and Push Fields, so the large empty area to the right is used for the expression/value text instead of looking like an empty extra column.");
        }

        detailHost.layout(true, true);
        detailHost.getParent().layout(true, true);
    }

    private void createMidTierSettings(Composite parent) {
        addInfo(parent, "Map each connected AR server to a specific MidTier URL. The toolbar button is available in form and workflow editors. For multi-form workflows you can select the associated form.");
        addImportant(parent, "Per server choose Automatic, AR credentials, or RSSO / SSO browser. Automatic detects redirects to SSO and opens an embedded browser; it never passes AR credentials to the SSO domain. HTTP credentials require explicit opt-in. Full flushes affect all users.");
        final Table mappingsTable = new Table(parent, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE);
        mappingsTable.setHeaderVisible(true);
        mappingsTable.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, false);
        tableData.heightHint = 145;
        mappingsTable.setLayoutData(tableData);
        TableColumn serverColumn = new TableColumn(mappingsTable, SWT.LEFT);
        serverColumn.setText("AR Server");
        serverColumn.setWidth(190);
        TableColumn urlColumn = new TableColumn(mappingsTable, SWT.LEFT);
        urlColumn.setText("MidTier URL");
        urlColumn.setWidth(355);
        TableColumn authColumn = new TableColumn(mappingsTable, SWT.LEFT);
        authColumn.setText("Authentication");
        authColumn.setWidth(160);
        TableColumn ssoColumn = new TableColumn(mappingsTable, SWT.LEFT);
        ssoColumn.setText("SSO URL (optional)");
        ssoColumn.setWidth(250);
        for (MidTierConfiguration.Mapping m : midTierMappings) {
            TableItem row = new TableItem(mappingsTable, SWT.NONE);
            row.setText(new String[] {m.server(), m.baseUrl(), m.authenticationMethod().label(), m.ssoBaseUrl()});
        }
        Composite buttons = new Composite(parent, SWT.NONE);
        buttons.setLayout(new GridLayout(3, false));
        buttons.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        Button add = new Button(buttons, SWT.PUSH);
        add.setText("Add server...");
        add.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                MidTierMappingDialog dialog = new MidTierMappingDialog(getShell(), null);
                if (dialog.open() != org.eclipse.jface.window.Window.OK) return;
                MidTierConfiguration.Mapping mapping = dialog.getResult();
                if (MidTierConfiguration.lookup(midTierMappings, mapping.server()) != null) {
                    MessageDialog.openError(getShell(), "MidTier Cache Flush", "AR server already configured: " + mapping.server());
                    return;
                }
                midTierMappings.add(mapping);
                TableItem row = new TableItem(mappingsTable, SWT.NONE);
                row.setText(new String[] {mapping.server(), mapping.baseUrl(), mapping.authenticationMethod().label(), mapping.ssoBaseUrl()});
                mappingsTable.setSelection(row);
            }
        });
        Button edit = new Button(buttons, SWT.PUSH);
        edit.setText("Edit...");
        edit.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                int index = mappingsTable.getSelectionIndex();
                if (index < 0 || index >= midTierMappings.size()) return;
                MidTierMappingDialog dialog = new MidTierMappingDialog(getShell(), midTierMappings.get(index));
                if (dialog.open() != org.eclipse.jface.window.Window.OK) return;
                MidTierConfiguration.Mapping mapping = dialog.getResult();
                for (int i = 0; i < midTierMappings.size(); i++) {
                    if (i != index && midTierMappings.get(i).server().equalsIgnoreCase(mapping.server())) {
                        MessageDialog.openError(getShell(), "MidTier Cache Flush", "AR server already configured: " + mapping.server());
                        return;
                    }
                }
                midTierMappings.set(index, mapping);
                mappingsTable.getItem(index).setText(new String[] {mapping.server(), mapping.baseUrl(), mapping.authenticationMethod().label(), mapping.ssoBaseUrl()});
            }
        });
        Button remove = new Button(buttons, SWT.PUSH);
        remove.setText("Remove");
        remove.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                int index = mappingsTable.getSelectionIndex();
                if (index < 0 || index >= midTierMappings.size()) return;
                midTierMappings.remove(index);
                mappingsTable.remove(index);
            }
        });
        addInfo(parent, "For example, map arserver to http://ars.sandbox:8080/arsys. Use the URL of a specific pod/instance if you need to target one. HTTP is supported for local labs; HTTPS is recommended elsewhere.");
    }

    private void addTextSetting(Composite parent, String labelText, String value, final ValueSetter setter) {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Label label = new Label(row, SWT.NONE);
        label.setText(labelText);
        GridData labelData = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        labelData.widthHint = 220;
        label.setLayoutData(labelData);
        Text text = new Text(row, SWT.BORDER);
        text.setText(value == null ? "" : value);
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        text.addModifyListener(new ModifyListener() {
            public void modifyText(ModifyEvent e) {
                setter.set(((Text) e.widget).getText());
            }
        });
    }

    private void addComboSetting(Composite parent, String labelText, String[] labels, String[] values,
                                 String selected, final ValueSetter setter) {
        Composite row = new Composite(parent, SWT.NONE);
        row.setLayout(new GridLayout(2, false));
        row.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        Label label = new Label(row, SWT.NONE);
        label.setText(labelText);
        GridData ld = new GridData(SWT.LEFT, SWT.CENTER, false, false);
        ld.widthHint = 220;
        label.setLayoutData(ld);
        final Combo combo = new Combo(row, SWT.DROP_DOWN | SWT.READ_ONLY);
        combo.setItems(labels);
        combo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        int index = 0;
        for (int i = 0; i < values.length; i++) if (values[i].equals(selected)) { index = i; break; }
        combo.select(index);
        combo.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                int i = combo.getSelectionIndex();
                if (i >= 0 && i < values.length) setter.set(values[i]);
            }
        });
    }

    private void addCheckSetting(Composite parent, String text, boolean value, final BooleanSetter setter) {
        Button button = new Button(parent, SWT.CHECK);
        button.setText(text);
        button.setSelection(value);
        button.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        button.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                setter.set(((Button) e.widget).getSelection());
            }
        });
    }

    private Label addInfo(Composite parent, String text) {
        Label label = new Label(parent, SWT.WRAP);
        label.setText(text == null ? "" : text);
        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.widthHint = 680;
        data.verticalIndent = 4;
        label.setLayoutData(data);
        return label;
    }

    private Label addSmallInfo(Composite parent, String text) {
        Label label = new Label(parent, SWT.WRAP);
        label.setText(text == null ? "" : text);
        GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
        data.widthHint = 300;
        data.verticalIndent = 4;
        label.setLayoutData(data);
        return label;
    }

    private Label addImportant(Composite parent, String text) {
        Label label = addInfo(parent, text);
        try {
            Color color = parent.getDisplay().getSystemColor(SWT.COLOR_DARK_RED);
            label.setForeground(color);
        } catch (Throwable ignored) {
        }
        return label;
    }

    @Override
    public boolean performOk() {
        if (!validateValues()) {
            return false;
        }
        saveValues();
        postSaveActions();
        MidTierFlushAvailability.refreshToolbarVisibility();
        return true;
    }

    @Override
    protected void performApply() {
        performOk();
    }

    @Override
    protected void performDefaults() {
        messageSuppressionEnabled = ToolsConstants.DEFAULT_MESSAGE_SUPPRESSION_ENABLED;
        messageSuppressionCodes = ToolsConstants.DEFAULT_MESSAGE_SUPPRESSION_CODES;
        suffixEnabled = ToolsConstants.DEFAULT_REMOVE_CUSTOM_SUFFIX_ENABLED;
        tableNamesEnabled = ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED;
        tableColumnPattern = ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN;
        autoIdsEnabled = ToolsConstants.DEFAULT_AUTO_FIELD_ID_ENABLED;
        developerId = "";
        skipPanels = ToolsConstants.DEFAULT_AUTO_FIELD_ID_SKIP_PANELS;
        pwaIconsEnabled = ToolsConstants.DEFAULT_PWA_ICON_HELPER_ENABLED;
        iconCssUrl = "";
        fastObjectsEnabled = ToolsConstants.DEFAULT_FAST_FORMS_ENABLED;
        fastObjectValues = ToolsConstants.DEFAULT_FAST_FORMS_VALUES;
        fastObjectDebug = ToolsConstants.DEFAULT_FAST_FORMS_DEBUG;
        objectSortingEnabled = ToolsConstants.DEFAULT_OBJECT_SORT_ENABLED;
        objectSortColumn = ToolsConstants.DEFAULT_OBJECT_SORT_COLUMN;
        objectSortDirection = ToolsConstants.DEFAULT_OBJECT_SORT_DIRECTION;
        midTierCacheEnabled = ToolsConstants.DEFAULT_MIDTIER_CACHE_ENABLED;
        midTierMappings.clear();
        keepAliveEnabled = ToolsConstants.DEFAULT_KEEPALIVE_ENABLED;
        keepAliveInterval = String.valueOf(ToolsConstants.DEFAULT_KEEPALIVE_INTERVAL_SECONDS);
        insightEnabled = ToolsConstants.DEFAULT_OBJECT_INSIGHT_ENABLED;
        removeFromViewEnabled = ToolsConstants.DEFAULT_REMOVE_FROM_VIEW_ENABLED;
        objectListSearchEnhancerEnabled = ToolsConstants.DEFAULT_OBJECT_LIST_SEARCH_ENHANCER_ENABLED;
        workflowFieldMapLayoutEnabled = ToolsConstants.DEFAULT_WORKFLOW_FIELD_MAP_LAYOUT_ENABLED;
        refreshFeatureTable();
        renderDetails(selectedFeatureId);
        super.performDefaults();
    }

    private boolean validateValues() {
        try {
            MidTierConfiguration.encode(midTierMappings);
        } catch (IllegalArgumentException e) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools", "Invalid MidTier mapping: " + e.getMessage());
            selectFeature(FEATURE_MIDTIER_CACHE);
            return false;
        }
        if (messageSuppressionEnabled && !se.yrell.developertools.runtime.MessageSuppressionBridge.isValidCodeList(messageSuppressionCodes)) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "Message Suppressor codes must be comma-separated integers, e.g. 5026,5033,5384,5385,5386.");
            selectFeature(FEATURE_MESSAGE_SUPPRESSION);
            return false;
        }
        if (midTierCacheEnabled && midTierMappings.isEmpty()) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools", "Configure at least one AR server / MidTier URL mapping before enabling MidTier Cache Flush.");
            selectFeature(FEATURE_MIDTIER_CACHE);
            return false;
        }
        if (autoIdsEnabled && !isValidDeveloperId(developerId)) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "Developer ID must be set when Automatic Field IDs is enabled. It must be a number from 10 to 21.\n\n22 and above cannot be used because AR field IDs are signed integers and the generated 10-digit value would exceed the maximum allowed value.");
            selectFeature(FEATURE_AUTO_IDS);
            return false;
        }
        if (tableNamesEnabled && trim(tableColumnPattern).length() == 0) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "Table column database-name pattern cannot be empty when Default naming is enabled. Example: col_{remote_form}_{remote_field_name}");
            selectFeature(FEATURE_TABLE_NAMES);
            return false;
        }
        if (pwaIconsEnabled && trim(iconCssUrl).length() == 0) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "CSS icon catalog URL is required when PWA icon helper is enabled.\n\nExample:\nhttps://<midtier>/arsys/pwa/styles.xxxxxxx.css");
            selectFeature(FEATURE_PWA_ICONS);
            return false;
        }
        if (pwaIconsEnabled && !looksLikeUrl(iconCssUrl)) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "CSS icon catalog URL must start with http:// or https://.");
            selectFeature(FEATURE_PWA_ICONS);
            return false;
        }
        if (fastObjectsEnabled && !isValidFastFormsValues(fastObjectValues)) {
            MessageDialog.openError(getShell(), "Yrell Developer Tools",
                    "Customization Type values must be a comma-separated list using 0, 1, 2 and/or 4 when Fast object lists is enabled.\n\nExample: 2,4");
            selectFeature(FEATURE_FAST_OBJECTS);
            return false;
        }
        if (keepAliveEnabled) {
            int interval;
            try {
                interval = Integer.parseInt(trim(keepAliveInterval));
            } catch (NumberFormatException e) {
                MessageDialog.openError(getShell(), "Yrell Developer Tools", keepAliveErrorMessage());
                selectFeature(FEATURE_KEEPALIVE);
                return false;
            }
            if (interval < ToolsConstants.MIN_KEEPALIVE_INTERVAL_SECONDS || interval > ToolsConstants.MAX_KEEPALIVE_INTERVAL_SECONDS) {
                MessageDialog.openError(getShell(), "Yrell Developer Tools", keepAliveErrorMessage());
                selectFeature(FEATURE_KEEPALIVE);
                return false;
            }
        }
        return true;
    }

    private String keepAliveErrorMessage() {
        return "Keepalive interval must be a number between "
                + ToolsConstants.MIN_KEEPALIVE_INTERVAL_SECONDS + " and "
                + ToolsConstants.MAX_KEEPALIVE_INTERVAL_SECONDS + " seconds.";
    }

    private void saveValues() {
        IPreferenceStore store = getPreferenceStore();
        if (store != null) {
            store.setValue(ToolsConstants.PREF_REMOVE_CUSTOM_SUFFIX_ENABLED, suffixEnabled);
            store.setValue(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED, tableNamesEnabled);
            store.setValue(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN, trimOrDefault(tableColumnPattern, ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN));
            store.setValue(ToolsConstants.PREF_AUTO_FIELD_ID_ENABLED, autoIdsEnabled);
            store.setValue(ToolsConstants.PREF_AUTO_FIELD_ID_DEVELOPER_ID, trim(developerId));
            store.setValue(ToolsConstants.PREF_AUTO_FIELD_ID_SKIP_PANELS, skipPanels);
            store.setValue(ToolsConstants.PREF_PWA_ICON_HELPER_ENABLED, pwaIconsEnabled);
            store.setValue(ToolsConstants.PREF_PWA_ICON_CATALOG_URL, trim(iconCssUrl));
            store.setValue(ToolsConstants.PREF_FAST_FORMS_ENABLED, fastObjectsEnabled);
            store.setValue(ToolsConstants.PREF_FAST_FORMS_VALUES, trimOrDefault(fastObjectValues, ToolsConstants.DEFAULT_FAST_FORMS_VALUES));
            store.setValue(ToolsConstants.PREF_FAST_FORMS_DEBUG, fastObjectDebug);
            store.setValue(ToolsConstants.PREF_OBJECT_SORT_ENABLED, objectSortingEnabled);
            store.setValue(ToolsConstants.PREF_OBJECT_SORT_COLUMN, objectSortColumn);
            store.setValue(ToolsConstants.PREF_OBJECT_SORT_DIRECTION, objectSortDirection);
            store.setValue(ToolsConstants.PREF_MESSAGE_SUPPRESSION_ENABLED, messageSuppressionEnabled);
            store.setValue(ToolsConstants.PREF_MESSAGE_SUPPRESSION_CODES, messageSuppressionCodes.trim());
            store.setValue(ToolsConstants.PREF_MIDTIER_CACHE_ENABLED, midTierCacheEnabled);
            store.setValue(ToolsConstants.PREF_MIDTIER_CACHE_MAPPINGS, MidTierConfiguration.encode(midTierMappings));
            store.setValue(ToolsConstants.PREF_KEEPALIVE_ENABLED, keepAliveEnabled);
            store.setValue(ToolsConstants.PREF_KEEPALIVE_INTERVAL_SECONDS, parseIntOrDefault(keepAliveInterval, ToolsConstants.DEFAULT_KEEPALIVE_INTERVAL_SECONDS));
            store.setValue(ToolsConstants.PREF_OBJECT_INSIGHT_ENABLED, insightEnabled);
            store.setValue(ToolsConstants.PREF_REMOVE_FROM_VIEW_ENABLED, removeFromViewEnabled);
            store.setValue(ToolsConstants.PREF_OBJECT_LIST_SEARCH_ENHANCER_ENABLED, objectListSearchEnhancerEnabled);
            store.setValue(ToolsConstants.PREF_WORKFLOW_FIELD_MAP_LAYOUT_ENABLED, workflowFieldMapLayoutEnabled);
        }

        Preferences node = ToolsPreferences.node();
        node.putBoolean(ToolsConstants.PREF_REMOVE_CUSTOM_SUFFIX_ENABLED, suffixEnabled);
        node.putBoolean(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED, tableNamesEnabled);
        node.put(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN, trimOrDefault(tableColumnPattern, ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN));
        node.putBoolean(ToolsConstants.PREF_AUTO_FIELD_ID_ENABLED, autoIdsEnabled);
        node.put(ToolsConstants.PREF_AUTO_FIELD_ID_DEVELOPER_ID, trim(developerId));
        node.putBoolean(ToolsConstants.PREF_AUTO_FIELD_ID_SKIP_PANELS, skipPanels);
        node.putBoolean(ToolsConstants.PREF_PWA_ICON_HELPER_ENABLED, pwaIconsEnabled);
        node.put(ToolsConstants.PREF_PWA_ICON_CATALOG_URL, trim(iconCssUrl));
        node.putBoolean(ToolsConstants.PREF_FAST_FORMS_ENABLED, fastObjectsEnabled);
        node.put(ToolsConstants.PREF_FAST_FORMS_VALUES, trimOrDefault(fastObjectValues, ToolsConstants.DEFAULT_FAST_FORMS_VALUES));
        node.putBoolean(ToolsConstants.PREF_FAST_FORMS_DEBUG, fastObjectDebug);
        node.putBoolean(ToolsConstants.PREF_OBJECT_SORT_ENABLED, objectSortingEnabled);
        node.put(ToolsConstants.PREF_OBJECT_SORT_COLUMN, objectSortColumn);
        node.put(ToolsConstants.PREF_OBJECT_SORT_DIRECTION, objectSortDirection);
        node.putBoolean(ToolsConstants.PREF_MESSAGE_SUPPRESSION_ENABLED, messageSuppressionEnabled);
        node.put(ToolsConstants.PREF_MESSAGE_SUPPRESSION_CODES, messageSuppressionCodes.trim());
        node.putBoolean(ToolsConstants.PREF_MIDTIER_CACHE_ENABLED, midTierCacheEnabled);
        node.put(ToolsConstants.PREF_MIDTIER_CACHE_MAPPINGS, MidTierConfiguration.encode(midTierMappings));
        node.putBoolean(ToolsConstants.PREF_KEEPALIVE_ENABLED, keepAliveEnabled);
        node.putInt(ToolsConstants.PREF_KEEPALIVE_INTERVAL_SECONDS, parseIntOrDefault(keepAliveInterval, ToolsConstants.DEFAULT_KEEPALIVE_INTERVAL_SECONDS));
        node.putBoolean(ToolsConstants.PREF_OBJECT_INSIGHT_ENABLED, insightEnabled);
        node.putBoolean(ToolsConstants.PREF_REMOVE_FROM_VIEW_ENABLED, removeFromViewEnabled);
        node.putBoolean(ToolsConstants.PREF_OBJECT_LIST_SEARCH_ENHANCER_ENABLED, objectListSearchEnhancerEnabled);
        node.putBoolean(ToolsConstants.PREF_WORKFLOW_FIELD_MAP_LAYOUT_ENABLED, workflowFieldMapLayoutEnabled);
        try {
            node.flush();
        } catch (BackingStoreException e) {
            Log.warn("Could not flush Yrell Developer Tools preferences: " + e.getMessage());
        }
    }

    private void postSaveActions() {
        ToolsPreferences.writeMessageSuppressionProperties(messageSuppressionEnabled, messageSuppressionCodes);
        KeepAliveService.getInstance().reconfigure();
        ToolsPreferences.writeFastFormsAgentProperties(fastObjectsEnabled,
                trimOrDefault(fastObjectValues, ToolsConstants.DEFAULT_FAST_FORMS_VALUES), fastObjectDebug);
        if (insightEnabled) {
            ObjectInsightViewSupport.openAsync();
        } else {
            ObjectInsightViewSupport.hideAsync();
        }
        refreshFeatureTable();
        renderDetails(selectedFeatureId);
    }

    private void loadValues() {
        IPreferenceStore store = getPreferenceStore();
        suffixEnabled = getBoolean(store, ToolsConstants.PREF_REMOVE_CUSTOM_SUFFIX_ENABLED, ToolsConstants.DEFAULT_REMOVE_CUSTOM_SUFFIX_ENABLED);
        tableNamesEnabled = getBoolean(store, ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED, ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED);
        tableColumnPattern = getString(store, ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN, ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN);
        autoIdsEnabled = getBoolean(store, ToolsConstants.PREF_AUTO_FIELD_ID_ENABLED, ToolsConstants.DEFAULT_AUTO_FIELD_ID_ENABLED);
        developerId = getString(store, ToolsConstants.PREF_AUTO_FIELD_ID_DEVELOPER_ID, "");
        skipPanels = getBoolean(store, ToolsConstants.PREF_AUTO_FIELD_ID_SKIP_PANELS, ToolsConstants.DEFAULT_AUTO_FIELD_ID_SKIP_PANELS);
        pwaIconsEnabled = getBoolean(store, ToolsConstants.PREF_PWA_ICON_HELPER_ENABLED, ToolsConstants.DEFAULT_PWA_ICON_HELPER_ENABLED);
        iconCssUrl = getString(store, ToolsConstants.PREF_PWA_ICON_CATALOG_URL, "");
        fastObjectsEnabled = getBoolean(store, ToolsConstants.PREF_FAST_FORMS_ENABLED, ToolsConstants.DEFAULT_FAST_FORMS_ENABLED);
        fastObjectValues = getString(store, ToolsConstants.PREF_FAST_FORMS_VALUES, ToolsConstants.DEFAULT_FAST_FORMS_VALUES);
        fastObjectDebug = getBoolean(store, ToolsConstants.PREF_FAST_FORMS_DEBUG, ToolsConstants.DEFAULT_FAST_FORMS_DEBUG);
        objectSortingEnabled = getBoolean(store, ToolsConstants.PREF_OBJECT_SORT_ENABLED, ToolsConstants.DEFAULT_OBJECT_SORT_ENABLED);
        objectSortColumn = getString(store, ToolsConstants.PREF_OBJECT_SORT_COLUMN, ToolsConstants.DEFAULT_OBJECT_SORT_COLUMN);
        objectSortDirection = getString(store, ToolsConstants.PREF_OBJECT_SORT_DIRECTION, ToolsConstants.DEFAULT_OBJECT_SORT_DIRECTION);
        messageSuppressionEnabled = getBoolean(store, ToolsConstants.PREF_MESSAGE_SUPPRESSION_ENABLED,
                ToolsConstants.DEFAULT_MESSAGE_SUPPRESSION_ENABLED);
        messageSuppressionCodes = getString(store, ToolsConstants.PREF_MESSAGE_SUPPRESSION_CODES,
                ToolsConstants.DEFAULT_MESSAGE_SUPPRESSION_CODES);
        midTierCacheEnabled = getBoolean(store, ToolsConstants.PREF_MIDTIER_CACHE_ENABLED, ToolsConstants.DEFAULT_MIDTIER_CACHE_ENABLED);
        midTierMappings.clear();
        midTierMappings.addAll(MidTierConfiguration.decode(getString(store, ToolsConstants.PREF_MIDTIER_CACHE_MAPPINGS, "")));
        keepAliveEnabled = getBoolean(store, ToolsConstants.PREF_KEEPALIVE_ENABLED, ToolsConstants.DEFAULT_KEEPALIVE_ENABLED);
        keepAliveInterval = String.valueOf(getInt(store, ToolsConstants.PREF_KEEPALIVE_INTERVAL_SECONDS, ToolsConstants.DEFAULT_KEEPALIVE_INTERVAL_SECONDS));
        insightEnabled = getBoolean(store, ToolsConstants.PREF_OBJECT_INSIGHT_ENABLED, ToolsConstants.DEFAULT_OBJECT_INSIGHT_ENABLED);
        removeFromViewEnabled = getBoolean(store, ToolsConstants.PREF_REMOVE_FROM_VIEW_ENABLED, ToolsConstants.DEFAULT_REMOVE_FROM_VIEW_ENABLED);
        objectListSearchEnhancerEnabled = getBoolean(store, ToolsConstants.PREF_OBJECT_LIST_SEARCH_ENHANCER_ENABLED, ToolsConstants.DEFAULT_OBJECT_LIST_SEARCH_ENHANCER_ENABLED);
        workflowFieldMapLayoutEnabled = getBoolean(store, ToolsConstants.PREF_WORKFLOW_FIELD_MAP_LAYOUT_ENABLED, ToolsConstants.DEFAULT_WORKFLOW_FIELD_MAP_LAYOUT_ENABLED);
    }

    private boolean getBoolean(IPreferenceStore store, String key, boolean defaultValue) {
        try {
            if (store != null && store.contains(key)) {
                return store.getBoolean(key);
            }
        } catch (Throwable ignored) {
        }
        return ToolsPreferences.node().getBoolean(key, defaultValue);
    }

    private String getString(IPreferenceStore store, String key, String defaultValue) {
        try {
            if (store != null && store.contains(key)) {
                return store.getString(key);
            }
        } catch (Throwable ignored) {
        }
        return ToolsPreferences.node().get(key, defaultValue);
    }

    private int getInt(IPreferenceStore store, String key, int defaultValue) {
        try {
            if (store != null && store.contains(key)) {
                return store.getInt(key);
            }
        } catch (Throwable ignored) {
        }
        return ToolsPreferences.node().getInt(key, defaultValue);
    }

    private void refreshFeatureTable() {
        if (featureTable == null || featureTable.isDisposed()) {
            return;
        }
        TableItem[] items = featureTable.getItems();
        for (int i = 0; i < items.length; i++) {
            Feature feature = (Feature) items[i].getData();
            if (feature != null) {
                items[i].setChecked(isFeatureEnabled(feature.id));
                items[i].setText(1, enabledText(feature.id));
            }
        }
    }

    private void initFeatures() {
        features.put(FEATURE_SUFFIX, new Feature(FEATURE_SUFFIX, "Custom suffix cleanup",
                "Removes BMC's automatic __c suffix when new objects/fields are created.",
                "Restart Developer Studio after enabling/disabling if the relevant BMC editor classes have already loaded."));
        features.put(FEATURE_TABLE_NAMES, new Feature(FEATURE_TABLE_NAMES, "Default naming",
                "Automatically sets generated database names for table columns.",
                "Restart Developer Studio after enabling/disabling if table-field editor classes have already loaded."));
        features.put(FEATURE_AUTO_IDS, new Feature(FEATURE_AUTO_IDS, "Automatic field IDs",
                "Assigns field IDs from your developer/day range for newly created fields.",
                "Restart Developer Studio after enabling/disabling if field creation classes have already loaded."));
        features.put(FEATURE_PWA_ICONS, new Feature(FEATURE_PWA_ICONS, "PWA icon helper",
                "Adds the icon picker for Icon properties and preloads the CSS icon catalog.",
                "No restart is normally required after changing the CSS URL; reopen the picker if needed."));
        features.put(FEATURE_FAST_OBJECTS, new Feature(FEATURE_FAST_OBJECTS, "Fast object lists",
                "Filters large object lists to selected Customization Type values such as Overlay and Custom.",
                "Requires -javaagent and a Developer Studio restart with -clean to affect the initial server request."));
        features.put(FEATURE_OBJECT_SORTING, new Feature(FEATURE_OBJECT_SORTING, "Default object sorting",
                "Uses one default sort rule for all supported object lists and object pickers.",
                "Restart Developer Studio with -clean after installation. Existing lists may need to be reopened."));
        features.put(FEATURE_MESSAGE_SUPPRESSION, new Feature(FEATURE_MESSAGE_SUPPRESSION, "Message Suppressor",
                "Hides selected warning codes during save, without suppressing errors or affecting validation.",
                "Restart Developer Studio with -clean after installing 0.1.68. Editing code list takes effect immediately."));
        features.put(FEATURE_MIDTIER_CACHE, new Feature(FEATURE_MIDTIER_CACHE, "MidTier Cache Flush",
                "Flush MidTier cache for the current form or workflow, a complete instance, or all pods.",
                "No restart required for settings. Reopen an editor if the toolbar visibility does not update immediately."));
        features.put(FEATURE_KEEPALIVE, new Feature(FEATURE_KEEPALIVE, "Keepalive",
                "Keeps already connected AR Server sessions warm by periodically calling verifyUser().",
                "No restart is required; Apply reconfigures the keepalive service."));
        features.put(FEATURE_INSIGHT, new Feature(FEATURE_INSIGHT, "Object Insight panel",
                "Shows useful details for the selected Developer Studio object in a separate view.",
                "No restart is required; Apply opens or hides the view."));
        features.put(FEATURE_VIEW_ACTIONS, new Feature(FEATURE_VIEW_ACTIONS, "Form view actions",
                "Adds form-editor actions such as Remove from view.",
                "A restart is recommended if the context menu was already created before enabling this feature."));
        features.put(FEATURE_OBJECT_LIST_SEARCH, new Feature(FEATURE_OBJECT_LIST_SEARCH, "Object list search helper",
                "Improves the built-in Forms/Active Links/etc. object-list search field.",
                "Restart Developer Studio after enabling/disabling so the object-list UI classes are woven before they load. Reopen object lists to get the native X icon."));
        features.put(FEATURE_WORKFLOW_FIELD_MAP_LAYOUT, new Feature(FEATURE_WORKFLOW_FIELD_MAP_LAYOUT, "Workflow field-map layout",
                "Uses the empty space in workflow field-map tables for the Value column.",
                "Reopen workflow editors after enabling/disabling. Restart is safest if workflow UI classes were already loaded."));
    }

    private boolean isFeatureEnabled(String id) {
        if (FEATURE_SUFFIX.equals(id)) return suffixEnabled;
        if (FEATURE_TABLE_NAMES.equals(id)) return tableNamesEnabled;
        if (FEATURE_AUTO_IDS.equals(id)) return autoIdsEnabled;
        if (FEATURE_PWA_ICONS.equals(id)) return pwaIconsEnabled;
        if (FEATURE_FAST_OBJECTS.equals(id)) return fastObjectsEnabled;
        if (FEATURE_OBJECT_SORTING.equals(id)) return objectSortingEnabled;
        if (FEATURE_MESSAGE_SUPPRESSION.equals(id)) return messageSuppressionEnabled;
        if (FEATURE_MIDTIER_CACHE.equals(id)) return midTierCacheEnabled;
        if (FEATURE_KEEPALIVE.equals(id)) return keepAliveEnabled;
        if (FEATURE_INSIGHT.equals(id)) return insightEnabled;
        if (FEATURE_VIEW_ACTIONS.equals(id)) return removeFromViewEnabled;
        if (FEATURE_OBJECT_LIST_SEARCH.equals(id)) return objectListSearchEnhancerEnabled;
        if (FEATURE_WORKFLOW_FIELD_MAP_LAYOUT.equals(id)) return workflowFieldMapLayoutEnabled;
        return false;
    }

    private void setFeatureEnabled(String id, boolean enabled) {
        if (FEATURE_SUFFIX.equals(id)) suffixEnabled = enabled;
        else if (FEATURE_TABLE_NAMES.equals(id)) tableNamesEnabled = enabled;
        else if (FEATURE_AUTO_IDS.equals(id)) autoIdsEnabled = enabled;
        else if (FEATURE_PWA_ICONS.equals(id)) pwaIconsEnabled = enabled;
        else if (FEATURE_FAST_OBJECTS.equals(id)) fastObjectsEnabled = enabled;
        else if (FEATURE_OBJECT_SORTING.equals(id)) objectSortingEnabled = enabled;
        else if (FEATURE_MESSAGE_SUPPRESSION.equals(id)) messageSuppressionEnabled = enabled;
        else if (FEATURE_MIDTIER_CACHE.equals(id)) midTierCacheEnabled = enabled;
        else if (FEATURE_KEEPALIVE.equals(id)) keepAliveEnabled = enabled;
        else if (FEATURE_INSIGHT.equals(id)) insightEnabled = enabled;
        else if (FEATURE_VIEW_ACTIONS.equals(id)) removeFromViewEnabled = enabled;
        else if (FEATURE_OBJECT_LIST_SEARCH.equals(id)) objectListSearchEnhancerEnabled = enabled;
        else if (FEATURE_WORKFLOW_FIELD_MAP_LAYOUT.equals(id)) workflowFieldMapLayoutEnabled = enabled;
    }

    private String enabledText(String id) {
        return isFeatureEnabled(id) ? "Yes" : "No";
    }

    private String fastFormsAgentStatusText() {
        boolean active = Boolean.parseBoolean(System.getProperty("se.yrell.developertools.fastFormsAgent.active", "false"));
        if (active) {
            return "Fast object lists agent status: ACTIVE. Initial server-side object list filtering can be applied after restart with this feature enabled.";
        }
        return "Fast object lists agent status: NOT ACTIVE. Add the -javaagent line to DeveloperStudio.ini, then restart Developer Studio with -clean. Otherwise this feature may only filter after BMC has already loaded the full list.";
    }

    private void copyAgentLine() {
        Clipboard clipboard = new Clipboard(getShell().getDisplay());
        try {
            clipboard.setContents(new Object[] { suggestedJavaAgentLine() }, new Transfer[] { TextTransfer.getInstance() });
            MessageDialog.openInformation(getShell(), "Yrell Developer Tools",
                    "Suggested -javaagent line copied to clipboard. Add it to DeveloperStudio.ini, then restart Developer Studio with -clean.");
        } finally {
            clipboard.dispose();
        }
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
                    URI uri = URI.create(path);
                    path = new File(uri).getAbsolutePath();
                }
                return "-javaagent:" + path;
            }
        } catch (Throwable ignored) {
            // Fall through to generic example.
        }
        return "-javaagent:C:\\Temp\\se.yrell.developertools_0.1.56.jar";
    }

    private boolean isValidDeveloperId(String developerIdText) {
        try {
            int developerId = Integer.parseInt(trim(developerIdText));
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
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i].trim();
            if (!("0".equals(p) || "1".equals(p) || "2".equals(p) || "4".equals(p)
                    || "base".equalsIgnoreCase(p) || "overlaid".equalsIgnoreCase(p)
                    || "overlay".equalsIgnoreCase(p) || "custom".equalsIgnoreCase(p))) {
                return false;
            }
        }
        return true;
    }

    private boolean looksLikeUrl(String value) {
        String lower = trim(value).toLowerCase(java.util.Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimOrDefault(String value, String defaultValue) {
        String trimmed = trim(value);
        return trimmed.length() == 0 ? defaultValue : trimmed;
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(trim(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static final class Feature {
        final String id;
        final String name;
        final String description;
        final String restartNote;

        Feature(String id, String name, String description, String restartNote) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.restartNote = restartNote;
        }
    }

    private interface ValueSetter {
        void set(String value);
    }

    private interface BooleanSetter {
        void set(boolean value);
    }
}
