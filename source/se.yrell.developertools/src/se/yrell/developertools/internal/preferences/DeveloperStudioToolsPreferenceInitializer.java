package se.yrell.developertools.internal.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import se.yrell.developertools.ToolsActivator;
import se.yrell.developertools.ToolsConstants;

public class DeveloperStudioToolsPreferenceInitializer extends AbstractPreferenceInitializer {
    @Override
    public void initializeDefaultPreferences() {
        ToolsActivator activator = ToolsActivator.getDefault();
        if (activator == null) {
            return;
        }
        IPreferenceStore store = activator.getPreferenceStore();
        store.setDefault(ToolsConstants.PREF_REMOVE_CUSTOM_SUFFIX_ENABLED, ToolsConstants.DEFAULT_REMOVE_CUSTOM_SUFFIX_ENABLED);
        store.setDefault(ToolsConstants.PREF_REMOVE_CUSTOM_SUFFIX_UI_FILTER, ToolsConstants.DEFAULT_REMOVE_CUSTOM_SUFFIX_UI_FILTER);
        store.setDefault(ToolsConstants.PREF_REMOVE_CUSTOM_SUFFIX_POST_SAVE_CLEANUP, ToolsConstants.DEFAULT_REMOVE_CUSTOM_SUFFIX_POST_SAVE_CLEANUP);
        store.setDefault(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED, ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED);
        store.setDefault(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN, ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN);

        store.setDefault(ToolsConstants.PREF_AUTO_FIELD_ID_ENABLED, ToolsConstants.DEFAULT_AUTO_FIELD_ID_ENABLED);
        store.setDefault(ToolsConstants.PREF_AUTO_FIELD_ID_DEVELOPER_ID, "");
        store.setDefault(ToolsConstants.PREF_AUTO_FIELD_ID_SKIP_PANELS, ToolsConstants.DEFAULT_AUTO_FIELD_ID_SKIP_PANELS);
        store.setDefault(ToolsConstants.PREF_AUTO_FIELD_ID_NEXT_ID, "");
        store.setDefault(ToolsConstants.PREF_AUTO_FIELD_ID_SERVER_WIDE_UNIQUENESS, ToolsConstants.DEFAULT_AUTO_FIELD_ID_SERVER_WIDE_UNIQUENESS);
        store.setDefault(ToolsConstants.PREF_PWA_ICON_HELPER_ENABLED, ToolsConstants.DEFAULT_PWA_ICON_HELPER_ENABLED);
        store.setDefault(ToolsConstants.PREF_PWA_ICON_CATALOG_DIR, "");
        store.setDefault(ToolsConstants.PREF_PWA_ICON_CATALOG_URL, "");

        store.setDefault(ToolsConstants.PREF_FAST_FORMS_ENABLED, ToolsConstants.DEFAULT_FAST_FORMS_ENABLED);
        store.setDefault(ToolsConstants.PREF_FAST_FORMS_VALUES, ToolsConstants.DEFAULT_FAST_FORMS_VALUES);
        store.setDefault(ToolsConstants.PREF_FAST_FORMS_DEBUG, ToolsConstants.DEFAULT_FAST_FORMS_DEBUG);

        store.setDefault(ToolsConstants.PREF_KEEPALIVE_ENABLED, ToolsConstants.DEFAULT_KEEPALIVE_ENABLED);
        store.setDefault(ToolsConstants.PREF_KEEPALIVE_INTERVAL_SECONDS, ToolsConstants.DEFAULT_KEEPALIVE_INTERVAL_SECONDS);

        store.setDefault(ToolsConstants.PREF_OBJECT_INSIGHT_ENABLED, ToolsConstants.DEFAULT_OBJECT_INSIGHT_ENABLED);
    }
}
