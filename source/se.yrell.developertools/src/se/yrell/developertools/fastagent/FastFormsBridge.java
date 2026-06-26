package se.yrell.developertools.fastagent;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** Helper methods called from transformed BMC classes. This class is made visible to BMC class loaders via bootstrap append. */
public final class FastFormsBridge {
    private static final int OVERLAY_PROP_FIELD_ID = 30086;
    private static final int AR_REL_OP_EQUAL = 1;
    private static final int AR_COND_OP_AND = 1;
    private static final int AR_COND_OP_OR = 2;

    private static volatile Set<String> lastCustomizationCheckboxLabels = null;
    private static volatile long overlayGateRejectLogCount = 0L;
    private static volatile long diagnosticsLogCount = 0L;

    private FastFormsBridge() {}

    public static boolean isEnabledForAgent() { return isEnabled(); }
    public static String valuesForAgent() { return agentProperty("bmc.ds.fastForms.values", "2,4"); }
    public static String logFilePathForAgent() { return logFilePath(); }
    public static void debugForAgent(String msg) { debug(msg); }
    public static void printStackForAgent(Throwable t) { if (isDebug()) writeStack(t); }

    public static boolean shouldDeselectBaseCheckbox(Object checkboxConfig) {
        if (!isEnabled()) return false;
        if (!Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.deselectBaseCheckbox", "true"))) return false;
        if (allowedValues().contains(0)) return false;
        try {
            Field propName = findField(checkboxConfig.getClass(), "propName");
            Field trueVal = findField(checkboxConfig.getClass(), "trueVal");
            if (propName == null || trueVal == null) {
                debug("checkbox config fields not found in " + safeClassName(checkboxConfig));
                return false;
            }
            propName.setAccessible(true);
            trueVal.setAccessible(true);
            Object prop = propName.get(checkboxConfig);
            Object value = trueVal.get(checkboxConfig);
            boolean result = "OVERLAY_PROP".equals(prop) && Integer.valueOf(0).equals(value);
            if (result) debug("Base checkbox default changed to unchecked");
            return result;
        } catch (Throwable t) {
            debug("could not inspect checkbox config: " + t);
            return false;
        }
    }

    public static List sanitizeSelectedCheckboxNames(List input) {
        // This method is called by Developer Studio's checkbox-driven viewer filter.
        // Store the current Customization Type state so the always-installed OverlayObjectListFilter
        // can use the same state for Forms, where BMC does not always wire the checkbox filter the
        // same way as for Filters/Active Links.
        rememberSelectedCheckboxNames(input);
        if (!isEnabled() || input == null || allowedValues().contains(0)) return input;
        if (allowManualBaseCheckbox()) return input;
        try {
            ArrayList out = new ArrayList(input.size());
            boolean removed = false;
            for (Object o : input) {
                if ("Base".equals(o)) { removed = true; continue; }
                out.add(o);
            }
            if (removed) debug("removed Base from selected checkbox names");
            rememberSelectedCheckboxNames(out);
            return out;
        } catch (Throwable t) {
            debug("could not sanitize selected checkbox names: " + t);
            return input;
        }
    }

    public static void syncCheckboxSelectionEvent(Object event) {
        if (!isEnabled() || event == null) return;
        try {
            Object source = event.getClass().getMethod("getSource").invoke(event);
            if (source == null) return;
            Method getText = findMethod(source.getClass(), "getText");
            Method getSelection = findMethod(source.getClass(), "getSelection");
            if (getText == null || getSelection == null) return;
            getText.setAccessible(true);
            getSelection.setAccessible(true);
            Object textObj = getText.invoke(source);
            Object selectedObj = getSelection.invoke(source);
            if (!(textObj instanceof String) || !(selectedObj instanceof Boolean)) return;
            String label = (String) textObj;
            if (!isCustomizationLabel(label)) return;
            Set<String> labels = lastCustomizationCheckboxLabels;
            if (labels == null) labels = defaultCustomizationLabelsFromAllowedValues();
            else labels = new HashSet<String>(labels);
            if (((Boolean) selectedObj).booleanValue()) labels.add(label);
            else labels.remove(label);
            lastCustomizationCheckboxLabels = labels;
            debug("checkbox selection changed: " + label + "=" + selectedObj + ", customizationLabels=" + labels);
        } catch (Throwable t) {
            debug("could not sync checkbox selection event: " + t);
        }
    }

    public static void scheduleTriggerFilters(final Object objectListView) {
        if (!isEnabled() || !triggerUiFilter() || objectListView == null || allowedValues().contains(0)) return;
        try {
            final Method trigger = findMethod(objectListView.getClass(), "triggerFilters");
            if (trigger == null) {
                debug("triggerFilters method not found on " + safeClassName(objectListView));
                return;
            }
            trigger.setAccessible(true);
            final Runnable r = new Runnable() {
                @Override public void run() {
                    try {
                        trigger.invoke(objectListView);
                        debug("triggered UI checkbox filtering for " + safeClassName(objectListView));
                    } catch (Throwable t) {
                        debug("could not trigger UI checkbox filtering: " + t);
                    }
                }
            };
            ClassLoader cl = objectListView.getClass().getClassLoader();
            Class<?> displayClass = Class.forName("org.eclipse.swt.widgets.Display", false, cl);
            Object display = displayClass.getMethod("getDefault").invoke(null);
            if (display != null) {
                displayClass.getMethod("asyncExec", Runnable.class).invoke(display, r);
                // v6: FilteringSection was often constructed before a single-list viewer existed.
                // Also schedule two delayed passes after createTypeListContent/createFormListContent so
                // the custom checkbox filter is installed after the ObjectListComponent has items.
                try { displayClass.getMethod("timerExec", int.class, Runnable.class).invoke(display, 250, r); } catch (Throwable ignored) {}
                try { displayClass.getMethod("timerExec", int.class, Runnable.class).invoke(display, 1000, r); } catch (Throwable ignored) {}
            } else {
                r.run();
            }
        } catch (Throwable t) {
            debug("could not schedule UI checkbox filtering: " + t);
        }
    }

    public static boolean shouldRejectBaseModelItem(Object item) {
        // v6: by default the normal Developer Studio checkbox filter should decide this,
        // because that allows the user to tick Base manually later. This hard reject is
        // kept as an optional fallback for troubleshooting.
        if (!forceObjectListReject()) return false;
        if (!isEnabled() || item == null || allowedValues().contains(0)) return false;
        try {
            if (!isCustomizationFilterCandidate(item)) return false;
            int overlay = overlayValueOfModelItem(item);
            boolean reject = !allowedValues().contains(overlay);
            if (reject) debug("hard ObjectListView filter rejected item " + modelItemName(item) + " overlay=" + overlay);
            return reject;
        } catch (Throwable t) {
            debug("could not evaluate model item for hard rejection: " + t);
            return false;
        }
    }

    public static int propertyFilterDecision(Object propertyFilter, Object item) {
        // Return -1 to let BMC's original PropertyFilter.select run.
        // This hook mainly fixes FormItem/Base semantics if BMC's own value lookup is missing.
        if (!isEnabled() || propertyFilter == null || item == null) return -1;
        try {
            Field prop = findField(propertyFilter.getClass(), "proprtyName"); // BMC typo in field name.
            Field val = findField(propertyFilter.getClass(), "trueValue");
            if (prop == null || val == null) return -1;
            prop.setAccessible(true);
            val.setAccessible(true);
            Object propName = prop.get(propertyFilter);
            Object trueValue = val.get(propertyFilter);
            if (!"OVERLAY_PROP".equals(propName) || !(trueValue instanceof Number)) return -1;
            if (!isCustomizationFilterCandidate(item)) return -1;
            int overlay = overlayValueOfModelItem(item);
            boolean selected = ((Number) trueValue).intValue() == overlay;
            return selected ? 1 : 0;
        } catch (Throwable t) {
            debug("could not evaluate property filter decision: " + t);
            return -1;
        }
    }

    public static Object[] filterModelItemArray(Object[] input) {
        if (!isEnabled() || !hardOverlayListFilter() || input == null || allowedValues().contains(0)) return input;
        try {
            ArrayList<Object> out = new ArrayList<>(input.length);
            int filtered = 0;
            for (Object item : input) {
                if (isAllowedOrNonCustomizableModelItem(item)) out.add(item);
                else filtered++;
            }
            if (filtered > 0) debug("filtered object array for display: " + input.length + " -> " + out.size());
            return filtered == 0 ? input : out.toArray(new Object[0]);
        } catch (Throwable t) {
            debug("could not filter model item array: " + t);
            return input;
        }
    }


    public static Object[] filterModelItemArrayForOverlayGate(Object[] input) {
        if (!isEnabled() || !overlayGateFilter() || input == null) return input;
        try {
            ArrayList<Object> out = new ArrayList<Object>(input.length);
            int filtered = 0;
            int formSeen = 0;
            int formFiltered = 0;
            Set<Integer> allowed = allowedValuesFromCheckboxState();
            for (Object item : input) {
                boolean form = isFormItem(item);
                if (form) formSeen++;
                if (shouldRejectByAllowedValues(item, allowed)) {
                    filtered++;
                    if (form) formFiltered++;
                } else {
                    out.add(item);
                }
            }
            if (filtered > 0) {
                long n = ++overlayGateRejectLogCount;
                if (n <= 25 || isDebug()) {
                    debug("overlay gate filtered viewer array: " + input.length + " -> " + out.size() +
                          " (removed " + filtered + ", formsSeen=" + formSeen + ", formsRemoved=" + formFiltered +
                          ", allowed=" + allowed + ", labels=" + lastCustomizationCheckboxLabels + ")");
                }
                return out.toArray(new Object[0]);
            }
            if (isDebug() && formSeen > 0 && diagnosticsLogCount++ < 20) {
                debug("overlay gate saw form viewer array but removed none: size=" + input.length +
                      ", formsSeen=" + formSeen + ", allowed=" + allowed + ", labels=" + lastCustomizationCheckboxLabels + ")");
            }
            return input;
        } catch (Throwable t) {
            debug("could not apply overlay gate viewer filter: " + t);
            if (isDebug()) writeStack(t);
            return input;
        }
    }

    public static void probeItemListForDiagnostics(Object itemList) {
        if (!isEnabled() || !isDebug() || itemList == null || !(itemList instanceof Iterable)) return;
        long n = diagnosticsLogCount++;
        if (n > 40) return;
        try {
            int total = 0, form = 0, overlay0 = 0, overlay1 = 0, overlay2 = 0, overlay4 = 0, overlayOther = 0;
            Iterator it = ((Iterable)itemList).iterator();
            while (it.hasNext()) {
                Object item = it.next();
                total++;
                if (isFormItem(item)) form++;
                if (isCustomizationFilterCandidate(item)) {
                    int ov = overlayValueOfModelItem(item);
                    if (ov == 0) overlay0++;
                    else if (ov == 1) overlay1++;
                    else if (ov == 2) overlay2++;
                    else if (ov == 4) overlay4++;
                    else overlayOther++;
                }
            }
            if (form > 0 || overlay0 + overlay1 + overlay2 + overlay4 + overlayOther > 0) {
                debug("ObjectListComponent.setItems input diagnostics: class=" + safeClassName(itemList) +
                      ", total=" + total + ", formItems=" + form + ", overlay0/base=" + overlay0 +
                      ", overlay1/overlaid=" + overlay1 + ", overlay2/overlay=" + overlay2 +
                      ", overlay4/custom=" + overlay4 + ", overlayOther=" + overlayOther +
                      ", allowed=" + allowedValuesFromCheckboxState() + ", labels=" + lastCustomizationCheckboxLabels);
            }
        } catch (Throwable t) {
            debug("could not probe ItemList diagnostics: " + t);
        }
    }

    public static Object filterItemListForDisplay(Object itemList) {
        if (!isEnabled() || !hardFilterItemLists() || itemList == null || allowedValues().contains(0)) return itemList;
        if (!(itemList instanceof Iterable)) return itemList;
        try {
            int before = sizeOf(itemList);
            Class<?> listClass = itemList.getClass();
            Object out;
            try {
                Constructor<?> ctor = listClass.getConstructor();
                out = ctor.newInstance();
            } catch (Throwable t) {
                ClassLoader cl = listClass.getClassLoader();
                Class<?> itemListClass = Class.forName("com.bmc.arsys.studio.model.item.ItemList", false, cl);
                out = itemListClass.getConstructor().newInstance();
            }

            copyItemTypes(itemList, out);
            Method addItem = findMethod(out.getClass(), "addItem", Class.forName("com.bmc.arsys.studio.model.item.IModelItem", false, out.getClass().getClassLoader()));
            if (addItem == null) addItem = findMethod(out.getClass(), "addItem", Object.class);
            if (addItem == null) {
                debug("addItem method not found on " + safeClassName(out));
                return itemList;
            }
            addItem.setAccessible(true);

            int kept = 0;
            int filtered = 0;
            Iterator it = ((Iterable)itemList).iterator();
            while (it.hasNext()) {
                Object item = it.next();
                if (isAllowedOrNonCustomizableModelItem(item)) {
                    addItem.invoke(out, item);
                    kept++;
                } else {
                    filtered++;
                }
            }
            if (filtered > 0) {
                debug("filtered ItemList for display " + safeClassName(itemList) + ": " + before + " -> " + kept + " (removed " + filtered + ")");
                return out;
            }
            return itemList;
        } catch (Throwable t) {
            debug("could not filter ItemList for display: " + t);
            if (isDebug()) writeStack(t);
            return itemList;
        }
    }

    public static Object filterProviderResult(Object provider, Object result) {
        if (!isEnabled() || !hardFilterProviderResults() || result == null || allowedValues().contains(0)) return result;
        if (!isCustomizableProvider(provider)) return result;
        Object filtered = filterItemListLikeObject(result);
        if (filtered != result) debug("hard-filtered provider result for " + safeClassName(provider));
        return filtered;
    }

    private static Object filterItemListLikeObject(Object result) {
        // The known result types, ItemList and CompoundItemList, are Iterable-like in DS.
        return filterItemListForDisplay(result);
    }

    public static boolean shouldRejectForHardOverlayListFilter(Object item) {
        if (!isEnabled() || !hardOverlayListFilter() || item == null || allowedValues().contains(0)) return false;
        try {
            if (!isCustomizationFilterCandidate(item)) return false;
            int overlay = overlayValueOfModelItem(item);
            return !allowedValues().contains(overlay);
        } catch (Throwable t) {
            debug("could not evaluate hard overlay list filter: " + t);
            return false;
        }
    }

    public static boolean shouldRejectForOverlayGate(Object item) {
        if (!isEnabled() || !overlayGateFilter() || item == null) return false;
        try {
            return shouldRejectByAllowedValues(item, allowedValuesFromCheckboxState());
        } catch (Throwable t) {
            debug("could not evaluate overlay gate select: " + t);
            return false;
        }
    }

    /** Called from ARDynamicNamedListProvider/FormListProvider when a RegularQuery has just been built. */
    public static void applyOverlayFilterToRegularQuery(Object provider, Object regularQuery) {
        if (!isEnabled() || !serverFilter() || regularQuery == null) return;
        Set<Integer> values = allowedValues();
        if (values.contains(0)) {
            debug("base included; not applying RegularQuery filter");
            return;
        }
        if (!isCustomizableProvider(provider)) {
            debug("provider not customizable; RegularQuery filter skipped for " + safeClassName(provider));
            return;
        }

        try {
            ClassLoader cl = regularQuery.getClass().getClassLoader();
            Class<?> regularQueryClass = regularQuery.getClass();
            Class<?> querySourceFormClass = Class.forName("com.bmc.arsys.api.QuerySourceForm", false, cl);
            Class<?> iQuerySourceClass = Class.forName("com.bmc.arsys.api.IQuerySource", false, cl);
            Class<?> operandClass = Class.forName("com.bmc.arsys.api.ArithmeticOrRelationalOperand", false, cl);
            Class<?> relClass = Class.forName("com.bmc.arsys.api.RelationalOperationInfo", false, cl);
            Class<?> qualifierClass = Class.forName("com.bmc.arsys.api.QualifierInfo", false, cl);
            Class<?> valueClass = Class.forName("com.bmc.arsys.api.Value", false, cl);

            Object formSource = findFormSource(provider, regularQuery, regularQueryClass, querySourceFormClass);
            if (formSource == null) {
                debug("no QuerySourceForm found; RegularQuery filter skipped for " + safeClassName(provider));
                return;
            }

            Object filterQualifier = buildOverlayQualifier(qualifierClass, relClass, operandClass, valueClass, iQuerySourceClass, formSource, values);
            Method getQualifier = regularQueryClass.getMethod("getQualifier");
            Object currentQualifier = getQualifier.invoke(regularQuery);
            Object finalQualifier = andQualifiers(qualifierClass, currentQualifier, filterQualifier);
            Method setQualifier = regularQueryClass.getMethod("setQualifier", qualifierClass);
            setQualifier.invoke(regularQuery, finalQualifier);
            debug("applied RegularQuery overlay/custom filter for " + safeClassName(provider) + " values=" + values);
        } catch (Throwable t) {
            logAlways("could not apply RegularQuery overlay/custom filter for " + safeClassName(provider) + ": " + t);
            if (isDebug()) writeStack(t);
        }
    }

    /** Called from ARBaseNamedListProvider.getEntries(...). */
    public static Object augmentEntryQualifier(Object provider, Object qualifier) {
        if (!isEnabled() || !serverFilter()) return qualifier;
        Set<Integer> values = allowedValues();
        if (values.contains(0)) return qualifier;
        if (!isCustomizableProvider(provider)) return qualifier;

        try {
            ClassLoader cl = provider.getClass().getClassLoader();
            Class<?> operandClass = Class.forName("com.bmc.arsys.api.ArithmeticOrRelationalOperand", false, cl);
            Class<?> relClass = Class.forName("com.bmc.arsys.api.RelationalOperationInfo", false, cl);
            Class<?> qualifierClass = Class.forName("com.bmc.arsys.api.QualifierInfo", false, cl);
            Class<?> valueClass = Class.forName("com.bmc.arsys.api.Value", false, cl);

            Object filterQualifier = buildOverlayQualifierNoSource(qualifierClass, relClass, operandClass, valueClass, values);
            Object finalQualifier = andQualifiers(qualifierClass, qualifier, filterQualifier);
            debug("applied Entry overlay/custom qualifier for " + safeClassName(provider) + " values=" + values);
            return finalQualifier;
        } catch (Throwable t) {
            logAlways("could not augment Entry qualifier for " + safeClassName(provider) + ": " + t);
            if (isDebug()) writeStack(t);
            return qualifier;
        }
    }


    public static String filterOverlaySql(String sql, Object store, String alias, String objectName) {
        if (!isEnabled() || !serverFilter() || sql == null) return sql;
        Set<Integer> values = allowedValues();
        if (values.contains(Integer.valueOf(0))) return sql;
        // Only patch broad list queries. Leave exact-name overlay SQL alone so opening a named base object
        // is less likely to break if the user deliberately works outside the default overlay/custom list.
        if (objectName != null && objectName.trim().length() > 0) return sql;
        try {
            String a = (alias == null || alias.trim().length() == 0) ? "" : java.util.regex.Pattern.quote(alias.trim()) + "\\.";
            String allowed;
            if (values.contains(Integer.valueOf(2)) && values.contains(Integer.valueOf(4)) && values.size() == 2) {
                allowed = "($1overlayProp = 2 OR $1overlayProp = 4)";
            } else {
                StringBuilder sb = new StringBuilder("(");
                boolean first = true;
                for (Integer v : values) {
                    if (!first) sb.append(" OR ");
                    first = false;
                    sb.append("$1overlayProp = ").append(v.intValue());
                }
                sb.append(")");
                allowed = sb.toString();
            }
            // BMC's native custom-mode SQL includes Base too: (alias.overlayProp = 0 OR 2 OR 4).
            // Replace that exact broad clause with the configured values, normally 2+4.
            String pattern = "(?i)\\((" + a + ")overlayProp\\s*=\\s*0\\s+OR\\s+\\1overlayProp\\s*=\\s*2\\s+OR\\s+\\1overlayProp\\s*=\\s*4\\)";
            String patched = sql.replaceAll(pattern, allowed);
            if (!patched.equals(sql)) {
                debug("patched SQL overlay clause alias=" + alias + " values=" + values);
                return patched;
            }
            if (isDebug() && sql.indexOf("overlayProp") >= 0) {
                debug("SQL overlay clause not changed alias=" + alias + ": " + summarizeSql(sql));
            }
            return sql;
        } catch (Throwable t) {
            debug("could not patch SQL overlay clause: " + t);
            return sql;
        }
    }

    private static String summarizeSql(String sql) {
        if (sql == null) return "<null>";
        String one = sql.replace('\n', ' ').replace('\r', ' ').replaceAll("\\s+", " ").trim();
        return one.length() > 500 ? one.substring(0, 500) + "..." : one;
    }


    private static void rememberSelectedCheckboxNames(List input) {
        if (input == null) return;
        try {
            Set<String> labels = new HashSet<String>();
            for (Object o : input) {
                if (o instanceof String && isCustomizationLabel((String)o)) labels.add((String)o);
            }
            if (!labels.isEmpty()) {
                lastCustomizationCheckboxLabels = labels;
                debug("remembered customization checkbox labels=" + labels);
            }
        } catch (Throwable t) {
            debug("could not remember selected checkbox names: " + t);
        }
    }

    private static boolean shouldRejectByAllowedValues(Object item, Set<Integer> allowed) throws Exception {
        if (item == null) return false;
        if (!isCustomizationFilterCandidate(item)) return false;
        int overlay = overlayValueOfModelItem(item);
        boolean reject = !allowed.contains(Integer.valueOf(overlay));
        if (reject && isDebug() && overlayGateRejectLogCount < 50) {
            debug("overlay gate rejects " + modelItemName(item) + " class=" + safeClassName(item) + " overlay=" + overlay + " allowed=" + allowed);
        }
        return reject;
    }

    private static Set<Integer> allowedValuesFromCheckboxState() {
        Set<String> labels = lastCustomizationCheckboxLabels;
        if (labels == null || labels.isEmpty()) return allowedValues();
        Set<Integer> out = new HashSet<Integer>();
        for (String label : labels) addAllowedValueForLabel(out, label);
        if (out.isEmpty()) return allowedValues();
        return out;
    }

    private static Set<String> defaultCustomizationLabelsFromAllowedValues() {
        Set<Integer> values = allowedValues();
        Set<String> labels = new HashSet<String>();
        if (values.contains(Integer.valueOf(0))) labels.add("Base");
        if (values.contains(Integer.valueOf(1))) labels.add("Overlaid");
        if (values.contains(Integer.valueOf(2))) labels.add("Overlay");
        if (values.contains(Integer.valueOf(4))) labels.add("Custom");
        return labels;
    }

    private static void addAllowedValueForLabel(Set<Integer> out, String label) {
        if (label == null) return;
        String l = label.trim().toLowerCase();
        if (l.equals("base") || l.contains("unmodified")) out.add(Integer.valueOf(0));
        else if (l.equals("overlaid")) out.add(Integer.valueOf(1));
        else if (l.equals("overlay")) out.add(Integer.valueOf(2));
        else if (l.equals("custom")) out.add(Integer.valueOf(4));
    }

    private static boolean isCustomizationLabel(String label) {
        if (label == null) return false;
        String l = label.trim().toLowerCase();
        return l.equals("base") || l.contains("unmodified") || l.equals("overlaid") || l.equals("overlay") || l.equals("custom");
    }

    private static int normalizeOverlayValue(int value) {
        // APIItem's named constructor initializes OVERLAY_PROP to -1. BMC still displays that
        // as Unmodified/Base through Helper.getCustomizationTypeString(...), so treat negative
        // values as Base for filtering.
        return value < 0 ? 0 : value;
    }

    private static boolean isFormItem(Object item) {
        if (item == null) return false;
        String cn = item.getClass().getName();
        return cn.endsWith(".FormItem") || cn.contains(".model.item.FormItem");
    }

    private static boolean isAllowedOrNonCustomizableModelItem(Object item) {
        if (item == null) return true;
        try {
            if (!isCustomizationFilterCandidate(item)) return true;
            int overlay = overlayValueOfModelItem(item);
            boolean allowed = allowedValuesFromCheckboxState().contains(Integer.valueOf(overlay));
            if (!allowed) debug("filtered item " + modelItemName(item) + " overlay=" + overlay);
            return allowed;
        } catch (Throwable t) {
            debug("could not evaluate model item: " + t);
            return true;
        }
    }

    private static int overlayValueOfModelItem(Object item) throws Exception {
        // First use APIItem.getOverlayProperty when present. FormItem inherits this, and it is
        // the most reliable way to distinguish null/base from overlay/custom.
        Method getOverlayProperty = findMethod(item.getClass(), "getOverlayProperty");
        if (getOverlayProperty != null) {
            getOverlayProperty.setAccessible(true);
            Object v = getOverlayProperty.invoke(item);
            if (v instanceof Number) return normalizeOverlayValue(((Number) v).intValue());
        }
        Method getValue = findMethod(item.getClass(), "getValue", String.class);
        if (getValue != null) {
            getValue.setAccessible(true);
            Object v = getValue.invoke(item, "OVERLAY_PROP");
            if (v instanceof Number) return normalizeOverlayValue(((Number) v).intValue());
        }
        return 0; // Developer Studio's own filter treats NULL as Base/Unmodified.
    }

    private static boolean isCustomizationFilterCandidate(Object item) {
        try {
            if (item == null) return false;
            // v6: FormItem must be treated as customizable even if a type helper lookup fails.
            String cn = item.getClass().getName();
            if (cn.endsWith(".FormItem") || cn.contains(".model.item.FormItem")) return true;
            if (findMethod(item.getClass(), "getOverlayProperty") != null) {
                Object type = null;
                try { type = invokeNoArg(item, "getType"); } catch (Throwable ignored) {}
                if (type != null && isCustomizableType(type, item.getClass().getClassLoader())) return true;
            }
            Object type = invokeNoArg(item, "getType");
            if (type == null) return false;
            ClassLoader cl = item.getClass().getClassLoader();
            return isCustomizableType(type, cl);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isCustomizableModelItem(Object item) {
        return isCustomizationFilterCandidate(item);
    }

    private static boolean isCustomizableProvider(Object provider) {
        try {
            if (provider == null) return false;
            String providerClassName = provider.getClass().getName();
            if (providerClassName.endsWith(".FormListProvider")) return true;
            Object type = invokeNoArg(provider, "getType");
            if (type == null) return false;
            ClassLoader cl = provider.getClass().getClassLoader();
            return isCustomizableType(type, cl);
        } catch (Throwable t) {
            debug("could not determine customizable provider for " + safeClassName(provider) + ": " + t);
            return false;
        }
    }

    private static boolean isCustomizableType(Object type, ClassLoader cl) {
        try {
            Class<?> helperClass = Class.forName("com.bmc.arsys.studio.model.internal.helper.Helper", false, cl);
            Method m = findStaticMethodByName(helperClass, "isCustomizableType", 1);
            if (m == null) return false;
            Object r = m.invoke(null, type);
            return Boolean.TRUE.equals(r);
        } catch (Throwable t) {
            try {
                Class<?> overlayUtilClass = Class.forName("com.bmc.arsys.studio.commonui.OverlayUtil", false, cl);
                Method m = findStaticMethodByName(overlayUtilClass, "isOverlaySupported", 1);
                if (m != null) return Boolean.TRUE.equals(m.invoke(null, type));
            } catch (Throwable ignored) {}
            return false;
        }
    }

    private static Object findFormSource(Object provider, Object regularQuery, Class<?> regularQueryClass, Class<?> querySourceFormClass) throws Exception {
        String formName = null;
        try {
            Object name = invokeNoArg(provider, "getFormName");
            if (name instanceof String) formName = (String) name;
        } catch (Throwable ignored) {}

        Method getFromSources = regularQueryClass.getMethod("getFromSources");
        Object rawSources = getFromSources.invoke(regularQuery);
        if (!(rawSources instanceof List)) return null;

        Object firstQuerySourceForm = null;
        Method getName = querySourceFormClass.getMethod("getName");
        for (Object source : (List) rawSources) {
            if (!querySourceFormClass.isInstance(source)) continue;
            if (firstQuerySourceForm == null) firstQuerySourceForm = source;
            if (formName != null && formName.equals(getName.invoke(source))) return source;
        }
        return firstQuerySourceForm;
    }

    private static Object buildOverlayQualifier(Class<?> qualifierClass, Class<?> relClass, Class<?> operandClass,
                                                Class<?> valueClass, Class<?> iQuerySourceClass, Object querySource,
                                                Set<Integer> values) throws Exception {
        Object filterQualifier = null;
        for (Integer v : values) {
            Object q = buildEqualsQualifierWithSource(qualifierClass, relClass, operandClass, valueClass, iQuerySourceClass, querySource, OVERLAY_PROP_FIELD_ID, v.intValue());
            filterQualifier = orQualifiers(qualifierClass, filterQualifier, q);
        }
        return filterQualifier;
    }

    private static Object buildOverlayQualifierNoSource(Class<?> qualifierClass, Class<?> relClass, Class<?> operandClass,
                                                        Class<?> valueClass, Set<Integer> values) throws Exception {
        Object filterQualifier = null;
        for (Integer v : values) {
            Object q = buildEqualsQualifierNoSource(qualifierClass, relClass, operandClass, valueClass, OVERLAY_PROP_FIELD_ID, v.intValue());
            filterQualifier = orQualifiers(qualifierClass, filterQualifier, q);
        }
        return filterQualifier;
    }

    private static Object buildEqualsQualifierWithSource(Class<?> qualifierClass, Class<?> relClass, Class<?> operandClass,
                                                         Class<?> valueClass, Class<?> iQuerySourceClass, Object querySource,
                                                         int fieldId, int value) throws Exception {
        Constructor<?> fieldOperandCtor = operandClass.getConstructor(int.class, iQuerySourceClass);
        Object fieldOperand = fieldOperandCtor.newInstance(fieldId, querySource);
        return buildEqualsQualifierFromOperand(qualifierClass, relClass, operandClass, valueClass, fieldOperand, value);
    }

    private static Object buildEqualsQualifierNoSource(Class<?> qualifierClass, Class<?> relClass, Class<?> operandClass,
                                                       Class<?> valueClass, int fieldId, int value) throws Exception {
        Constructor<?> fieldOperandCtor = operandClass.getConstructor(int.class);
        Object fieldOperand = fieldOperandCtor.newInstance(fieldId);
        return buildEqualsQualifierFromOperand(qualifierClass, relClass, operandClass, valueClass, fieldOperand, value);
    }

    private static Object buildEqualsQualifierFromOperand(Class<?> qualifierClass, Class<?> relClass, Class<?> operandClass,
                                                          Class<?> valueClass, Object fieldOperand, int value) throws Exception {
        Constructor<?> valueCtor = valueClass.getConstructor(int.class);
        Object apiValue = valueCtor.newInstance(value);
        Constructor<?> valueOperandCtor = operandClass.getConstructor(valueClass);
        Object valueOperand = valueOperandCtor.newInstance(apiValue);
        Constructor<?> relCtor = relClass.getConstructor(int.class, operandClass, operandClass);
        Object rel = relCtor.newInstance(AR_REL_OP_EQUAL, fieldOperand, valueOperand);
        Constructor<?> qualifierCtor = qualifierClass.getConstructor(relClass);
        return qualifierCtor.newInstance(rel);
    }

    private static Object andQualifiers(Class<?> qualifierClass, Object left, Object right) throws Exception {
        if (left == null) return right;
        if (right == null) return left;
        Constructor<?> ctor = qualifierClass.getConstructor(int.class, qualifierClass, qualifierClass);
        return ctor.newInstance(AR_COND_OP_AND, left, right);
    }

    private static Object orQualifiers(Class<?> qualifierClass, Object left, Object right) throws Exception {
        if (left == null) return right;
        if (right == null) return left;
        Constructor<?> ctor = qualifierClass.getConstructor(int.class, qualifierClass, qualifierClass);
        return ctor.newInstance(AR_COND_OP_OR, left, right);
    }

    private static void copyItemTypes(Object src, Object dst) {
        try {
            Method getItemTypes = findMethod(src.getClass(), "getItemTypes");
            Method addItemType = null;
            Object raw = getItemTypes == null ? null : getItemTypes.invoke(src);
            if (!(raw instanceof Iterable)) return;
            for (Object type : (Iterable) raw) {
                if (type == null) continue;
                if (addItemType == null) addItemType = findMethod(dst.getClass(), "addItemType", type.getClass().getInterfaces().length > 0 ? type.getClass().getInterfaces()[0] : type.getClass());
                if (addItemType == null) {
                    // Directly look for any one-argument addItemType.
                    addItemType = findMethodByName(dst.getClass(), "addItemType", 1);
                }
                if (addItemType != null) {
                    addItemType.setAccessible(true);
                    addItemType.invoke(dst, type);
                }
            }
        } catch (Throwable t) {
            debug("could not copy item types: " + t);
        }
    }

    private static int sizeOf(Object itemList) {
        try {
            Method size = findMethod(itemList.getClass(), "size");
            Object r = size == null ? null : size.invoke(itemList);
            return r instanceof Number ? ((Number) r).intValue() : -1;
        } catch (Throwable t) { return -1; }
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        Method m = findMethod(target.getClass(), name);
        if (m == null) throw new NoSuchMethodException(name);
        m.setAccessible(true);
        return m.invoke(target);
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... params) {
        Class<?> current = c;
        while (current != null) {
            try { return current.getDeclaredMethod(name, params); }
            catch (NoSuchMethodException ignored) { current = current.getSuperclass(); }
        }
        try { return c.getMethod(name, params); } catch (NoSuchMethodException ignored) { return null; }
    }

    private static Method findMethodByName(Class<?> c, String name, int parameterCount) {
        for (Class<?> cur = c; cur != null; cur = cur.getSuperclass()) {
            for (Method m : cur.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == parameterCount) return m;
            }
        }
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == parameterCount) return m;
        }
        return null;
    }

    private static Method findStaticMethodByName(Class<?> c, String name, int parameterCount) {
        for (Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == parameterCount) return m;
        }
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == parameterCount) { m.setAccessible(true); return m; }
        }
        return null;
    }

    private static Field findField(Class<?> c, String name) {
        for (Class<?> cur = c; cur != null; cur = cur.getSuperclass()) {
            try { return cur.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    private static String modelItemName(Object item) {
        try {
            Object name = invokeNoArg(item, "getName");
            return String.valueOf(name);
        } catch (Throwable t) { return safeClassName(item); }
    }

    private static String safeClassName(Object o) { return o == null ? "<null>" : o.getClass().getName(); }

    private static boolean isEnabled() { return Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.enabled", "false")); }
    private static boolean isDebug() { return Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.debug", "false")); }
    private static boolean triggerUiFilter() { return Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.triggerUiFilter", "true")); }
    private static boolean serverFilter() { return Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.serverFilter", "true")); }
    private static boolean hardFilterItemLists() { return Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.hardFilterItemLists", "false")); }
    private static boolean hardFilterProviderResults() { return Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.hardFilterProviderResults", "false")); }
    private static boolean hardOverlayListFilter() { return Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.hardOverlayListFilter", "false")); }
    private static boolean overlayGateFilter() { return Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.overlayGateFilter", "true")); }
    private static boolean forceObjectListReject() { return Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.forceObjectListReject", "false")); }
    private static boolean allowManualBaseCheckbox() { return Boolean.parseBoolean(agentProperty("bmc.ds.fastForms.allowManualBase", "true")); }

    private static Set<Integer> allowedValues() {
        String raw = agentProperty("bmc.ds.fastForms.values", "2,4");
        Set<Integer> values = new HashSet<Integer>();
        for (String part : raw.split(",")) {
            String p = part.trim().toLowerCase();
            if (p.length() == 0) continue;
            if ("overlay".equals(p)) values.add(2);
            else if ("custom".equals(p)) values.add(4);
            else if ("base".equals(p) || "unmodified".equals(p)) values.add(0);
            else if ("overlaid".equals(p)) values.add(1);
            else {
                try { values.add(Integer.parseInt(p)); } catch (NumberFormatException ignored) {}
            }
        }
        if (values.isEmpty()) { values.add(2); values.add(4); }
        return values;
    }


    private static volatile java.util.Properties agentProperties;

    private static String agentProperty(String key, String def) {
        String direct = System.getProperty(key);
        if (direct != null) return direct;
        java.util.Properties p = loadAgentProperties();
        String v = p.getProperty(key);
        return v == null ? def : v;
    }

    private static java.util.Properties loadAgentProperties() {
        java.util.Properties p = agentProperties;
        if (p != null) return p;
        p = new java.util.Properties();
        java.io.File file = agentPropertiesFile();
        if (file != null && file.isFile()) {
            java.io.FileInputStream in = null;
            try {
                in = new java.io.FileInputStream(file);
                p.load(in);
            } catch (Throwable t) {
                System.err.println("[Yrell Developer Tools FastForms Agent] could not read " + file + ": " + t);
            } finally {
                try { if (in != null) in.close(); } catch (Throwable ignored) {}
            }
        }
        agentProperties = p;
        return p;
    }

    private static java.io.File agentPropertiesFile() {
        String configured = System.getProperty("se.yrell.developertools.fastforms.config");
        if (configured != null && configured.trim().length() > 0) return new java.io.File(configured.trim());
        String home = System.getProperty("user.home");
        if (home == null || home.trim().length() == 0) return null;
        return new java.io.File(new java.io.File(home, ".yrell-developertools"), "fastforms-agent.properties");
    }

    public static void logAlways(String msg) {
        String line = "[Yrell Developer Tools FastForms Agent] " + msg;
        System.err.println(line);
        try {
            File f = new File(logFilePath());
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            FileWriter fw = new FileWriter(f, true);
            try {
                String ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
                fw.write(ts + " " + line + System.lineSeparator());
            } finally {
                fw.close();
            }
        } catch (Throwable ignored) {}
    }

    private static void debug(String msg) { if (isDebug()) logAlways(msg); }

    private static void writeStack(Throwable t) {
        try {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            logAlways(sw.toString());
        } catch (Throwable ignored) {}
    }

    private static String logFilePath() {
        String configured = agentProperty("bmc.ds.fastForms.logFile", null);
        if (configured != null && configured.trim().length() > 0) return configured.trim();
        String tmp = System.getProperty("java.io.tmpdir", ".");
        return new File(tmp, "devstudio-fastforms.log").getAbsolutePath();
    }
}
