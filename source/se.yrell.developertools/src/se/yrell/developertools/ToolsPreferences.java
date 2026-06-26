package se.yrell.developertools;

import org.eclipse.core.runtime.preferences.InstanceScope;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;
import org.eclipse.jface.preference.IPreferenceStore;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

public final class ToolsPreferences {
    private ToolsPreferences() {
    }

    public static Preferences node() {
        return InstanceScope.INSTANCE.getNode(ToolsConstants.PLUGIN_ID);
    }

    public static boolean isRemoveCustomSuffixEnabled() {
        return node().getBoolean(ToolsConstants.PREF_REMOVE_CUSTOM_SUFFIX_ENABLED,
                ToolsConstants.DEFAULT_REMOVE_CUSTOM_SUFFIX_ENABLED);
    }

    public static boolean isRemoveCustomSuffixUiFilterEnabled() {
        // The separate UI filter setting was removed in 0.1.17. Proposed names are cleaned
        // whenever the main suffix cleanup feature is enabled.
        return isRemoveCustomSuffixEnabled();
    }

    public static boolean isRemoveCustomSuffixPostSaveCleanupEnabled() {
        // Post-save cleanup was removed in 0.1.17 to avoid changing persisted objects unexpectedly.
        return false;
    }

    public static boolean isTableColumnDbNameEnabled() {
        return node().getBoolean(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED,
                ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_ENABLED);
    }

    public static String getTableColumnDbNamePattern() {
        String value = node().get(ToolsConstants.PREF_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN,
                ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN).trim();
        return value.length() == 0 ? ToolsConstants.DEFAULT_DEFAULT_NAMES_TABLE_COLUMN_DB_NAME_PATTERN : value;
    }

    public static boolean isAutoFieldIdEnabled() {
        return node().getBoolean(ToolsConstants.PREF_AUTO_FIELD_ID_ENABLED,
                ToolsConstants.DEFAULT_AUTO_FIELD_ID_ENABLED);
    }

    public static String getAutoFieldDeveloperId() {
        return node().get(ToolsConstants.PREF_AUTO_FIELD_ID_DEVELOPER_ID, "").trim();
    }

    public static boolean isAutoFieldIdSkipPanelsEnabled() {
        return node().getBoolean(ToolsConstants.PREF_AUTO_FIELD_ID_SKIP_PANELS,
                ToolsConstants.DEFAULT_AUTO_FIELD_ID_SKIP_PANELS);
    }

    public static String getAutoFieldNextId() {
        return node().get(ToolsConstants.PREF_AUTO_FIELD_ID_NEXT_ID, "").trim();
    }

    public static void setAutoFieldNextId(String nextId) {
        String value = nextId == null ? "" : nextId.trim();
        Preferences node = node();
        node.put(ToolsConstants.PREF_AUTO_FIELD_ID_NEXT_ID, value);
        try {
            node.flush();
        } catch (BackingStoreException e) {
            Log.warn("Could not persist next Auto Field ID preference: " + e.getMessage());
        }

        ToolsActivator activator = ToolsActivator.getDefault();
        if (activator != null) {
            try {
                IPreferenceStore store = activator.getPreferenceStore();
                if (store != null) {
                    store.setValue(ToolsConstants.PREF_AUTO_FIELD_ID_NEXT_ID, value);
                }
            } catch (Throwable ignored) {
                // InstanceScope above is the source of truth; the UI store update is best-effort only.
            }
        }
    }

    public static boolean isPwaIconHelperEnabled() {
        return node().getBoolean(ToolsConstants.PREF_PWA_ICON_HELPER_ENABLED,
                ToolsConstants.DEFAULT_PWA_ICON_HELPER_ENABLED);
    }

    public static String getPwaIconCatalogDir() {
        return node().get(ToolsConstants.PREF_PWA_ICON_CATALOG_DIR, "").trim();
    }

    public static String getPwaIconCatalogUrl() {
        return node().get(ToolsConstants.PREF_PWA_ICON_CATALOG_URL, "").trim();
    }

    public static boolean hasPwaIconCatalogUrl() {
        return getPwaIconCatalogUrl().length() > 0;
    }

    public static boolean isFastFormsEnabled() {
        return node().getBoolean(ToolsConstants.PREF_FAST_FORMS_ENABLED,
                ToolsConstants.DEFAULT_FAST_FORMS_ENABLED);
    }

    public static String getFastFormsValues() {
        String value = node().get(ToolsConstants.PREF_FAST_FORMS_VALUES,
                ToolsConstants.DEFAULT_FAST_FORMS_VALUES).trim();
        return value.length() == 0 ? ToolsConstants.DEFAULT_FAST_FORMS_VALUES : value;
    }

    public static boolean isFastFormsDebugEnabled() {
        return node().getBoolean(ToolsConstants.PREF_FAST_FORMS_DEBUG,
                ToolsConstants.DEFAULT_FAST_FORMS_DEBUG);
    }



    public static File fastFormsAgentPropertiesFile() {
        String home = System.getProperty("user.home");
        if (home == null || home.trim().length() == 0) {
            return null;
        }
        return new File(new File(home, ".yrell-developertools"), "fastforms-agent.properties");
    }

    public static void writeFastFormsAgentProperties(boolean enabled, String values, boolean debug) {
        File file = fastFormsAgentPropertiesFile();
        if (file == null) {
            Log.warn("Could not write Fast object lists agent settings because user.home is not set.");
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            Properties properties = new Properties();
            properties.setProperty("bmc.ds.fastForms.enabled", Boolean.toString(enabled));
            properties.setProperty("bmc.ds.fastForms.values", values == null || values.trim().length() == 0
                    ? ToolsConstants.DEFAULT_FAST_FORMS_VALUES : values.trim());
            properties.setProperty("bmc.ds.fastForms.debug", Boolean.toString(debug));
            properties.setProperty("bmc.ds.fastForms.serverFilter", "true");
            properties.setProperty("bmc.ds.fastForms.triggerUiFilter", "false");
            properties.setProperty("bmc.ds.fastForms.overlayGateFilter", "true");
            properties.setProperty("bmc.ds.fastForms.deselectBaseCheckbox", "true");
            FileOutputStream out = new FileOutputStream(file);
            try {
                properties.store(out, "Yrell Developer Tools Fast object lists agent settings");
            } finally {
                out.close();
            }
            Log.info("Wrote Fast object lists agent settings to " + file.getAbsolutePath());
        } catch (Throwable t) {
            Log.error("Could not write Fast object lists agent settings to " + file.getAbsolutePath(), t);
        }
    }

    public static boolean isKeepAliveEnabled() {
        return node().getBoolean(ToolsConstants.PREF_KEEPALIVE_ENABLED,
                ToolsConstants.DEFAULT_KEEPALIVE_ENABLED);
    }

    public static int getKeepAliveIntervalSeconds() {
        int value = node().getInt(ToolsConstants.PREF_KEEPALIVE_INTERVAL_SECONDS,
                ToolsConstants.DEFAULT_KEEPALIVE_INTERVAL_SECONDS);
        if (value < ToolsConstants.MIN_KEEPALIVE_INTERVAL_SECONDS) {
            return ToolsConstants.MIN_KEEPALIVE_INTERVAL_SECONDS;
        }
        if (value > ToolsConstants.MAX_KEEPALIVE_INTERVAL_SECONDS) {
            return ToolsConstants.MAX_KEEPALIVE_INTERVAL_SECONDS;
        }
        return value;
    }

    public static boolean isObjectInsightEnabled() {
        return node().getBoolean(ToolsConstants.PREF_OBJECT_INSIGHT_ENABLED,
                ToolsConstants.DEFAULT_OBJECT_INSIGHT_ENABLED);
    }

    public static boolean isRemoveFromViewEnabled() {
        return node().getBoolean(ToolsConstants.PREF_REMOVE_FROM_VIEW_ENABLED,
                ToolsConstants.DEFAULT_REMOVE_FROM_VIEW_ENABLED);
    }

    public static boolean isObjectListSearchEnhancerEnabled() {
        return node().getBoolean(ToolsConstants.PREF_OBJECT_LIST_SEARCH_ENHANCER_ENABLED,
                ToolsConstants.DEFAULT_OBJECT_LIST_SEARCH_ENHANCER_ENABLED);
    }

    public static boolean isWorkflowFieldMapLayoutEnabled() {
        return node().getBoolean(ToolsConstants.PREF_WORKFLOW_FIELD_MAP_LAYOUT_ENABLED,
                ToolsConstants.DEFAULT_WORKFLOW_FIELD_MAP_LAYOUT_ENABLED);
    }


    /**
     * Deprecated since 0.1.5. Auto Field ID is intentionally local again and no longer scans all forms.
     */
    public static boolean isAutoFieldIdServerWideUniquenessEnabled() {
        return false;
    }
}
