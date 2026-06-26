package se.yrell.developertools;

public final class ToolsConstants {
    public static final String PLUGIN_ID = "se.yrell.developertools";

    public static final String PREF_REMOVE_CUSTOM_SUFFIX_ENABLED = "removeCustomSuffix.enabled";
    /** Deprecated since 0.1.17. UI clean follows PREF_REMOVE_CUSTOM_SUFFIX_ENABLED. */
    public static final String PREF_REMOVE_CUSTOM_SUFFIX_UI_FILTER = "removeCustomSuffix.uiFilter";
    /** Deprecated since 0.1.17. Post-save cleanup is removed. */
    public static final String PREF_REMOVE_CUSTOM_SUFFIX_POST_SAVE_CLEANUP = "removeCustomSuffix.postSaveCleanup";

    public static final String PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED = "defaultNames.tableColumnDbName.enabled";
    public static final String PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN = "defaultNames.tableColumnDbName.pattern";

    public static final String PREF_AUTO_FIELD_ID_ENABLED = "autoFieldId.enabled";
    public static final String PREF_AUTO_FIELD_ID_DEVELOPER_ID = "autoFieldId.developerId";
    public static final String PREF_AUTO_FIELD_ID_SKIP_PANELS = "autoFieldId.skipPanels";
    /** Read-only/status value since 0.1.17. Kept for logging/status only. */
    public static final String PREF_AUTO_FIELD_ID_NEXT_ID = "autoFieldId.nextId";
    /** Deprecated since 0.1.5. Kept only so old workspaces do not fail when the key exists. */
    public static final String PREF_AUTO_FIELD_ID_SERVER_WIDE_UNIQUENESS = "autoFieldId.serverWideUniqueness";

    public static final String PREF_PWA_ICON_HELPER_ENABLED = "pwaIconHelper.enabled";
    /** Deprecated since 0.1.16. Icon catalog is URL-only. */
    public static final String PREF_PWA_ICON_CATALOG_DIR = "pwaIconHelper.catalogDir";
    public static final String PREF_PWA_ICON_CATALOG_URL = "pwaIconHelper.catalogUrl";

    public static final String PREF_FAST_FORMS_ENABLED = "fastForms.enabled";
    public static final String PREF_FAST_FORMS_VALUES = "fastForms.values";
    public static final String PREF_FAST_FORMS_DEBUG = "fastForms.debug";

    public static final String PREF_KEEPALIVE_ENABLED = "keepAlive.enabled";
    public static final String PREF_KEEPALIVE_INTERVAL_SECONDS = "keepAlive.intervalSeconds";

    public static final String PREF_OBJECT_INSIGHT_ENABLED = "objectInsight.enabled";
    public static final String PREF_REMOVE_FROM_VIEW_ENABLED = "removeFromView.enabled";
    public static final String PREF_OBJECT_LIST_SEARCH_ENHANCER_ENABLED = "objectListSearchEnhancer.enabled";
    public static final String PREF_WORKFLOW_FIELD_MAP_LAYOUT_ENABLED = "workflowFieldMapLayout.enabled";


    public static final boolean DEFAULT_REMOVE_CUSTOM_SUFFIX_ENABLED = false;
    public static final boolean DEFAULT_REMOVE_CUSTOM_SUFFIX_UI_FILTER = false;
    public static final boolean DEFAULT_REMOVE_CUSTOM_SUFFIX_POST_SAVE_CLEANUP = false;

    public static final boolean DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED = false;
    public static final String DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN = "col_{remote_form}_{remote_field_name}";

    public static final boolean DEFAULT_AUTO_FIELD_ID_ENABLED = false;
    public static final boolean DEFAULT_AUTO_FIELD_ID_SKIP_PANELS = false;
    public static final boolean DEFAULT_AUTO_FIELD_ID_SERVER_WIDE_UNIQUENESS = false;

    public static final boolean DEFAULT_PWA_ICON_HELPER_ENABLED = false;

    public static final boolean DEFAULT_FAST_FORMS_ENABLED = false;
    /** Default values: 2=Overlay, 4=Custom. */
    public static final String DEFAULT_FAST_FORMS_VALUES = "2,4";
    public static final boolean DEFAULT_FAST_FORMS_DEBUG = false;

    public static final boolean DEFAULT_KEEPALIVE_ENABLED = false;
    public static final int DEFAULT_KEEPALIVE_INTERVAL_SECONDS = 120;
    public static final int MIN_KEEPALIVE_INTERVAL_SECONDS = 30;
    public static final int MAX_KEEPALIVE_INTERVAL_SECONDS = 3600;

    public static final boolean DEFAULT_OBJECT_INSIGHT_ENABLED = false;
    public static final boolean DEFAULT_REMOVE_FROM_VIEW_ENABLED = false;
    public static final boolean DEFAULT_OBJECT_LIST_SEARCH_ENHANCER_ENABLED = false;
    public static final boolean DEFAULT_WORKFLOW_FIELD_MAP_LAYOUT_ENABLED = false;

    private ToolsConstants() {
    }
}
