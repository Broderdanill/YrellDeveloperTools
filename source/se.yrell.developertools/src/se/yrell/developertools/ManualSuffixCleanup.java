package se.yrell.developertools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.bmc.arsys.studio.model.ar.ARView;
import com.bmc.arsys.studio.model.store.IFieldObject;
import com.bmc.arsys.studio.model.store.IFormObject;
import com.bmc.arsys.studio.model.store.IModelObject;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.type.IARSystemTypes;

/**
 * Explicit, confirmed cleanup of previously persisted __c names on ONE selected form.
 * This is independent from automatic suffix suppression for NEW objects.
 * No form rename, no view display-label change, no unsafe collision resolution.
 */
public final class ManualSuffixCleanup {
    public enum Kind { FIELD_NAMES, VIEW_NAMES }
    public record Rename(int index, String original, String replacement) { }
    public record NamePlan(List<Rename> renames, List<String> conflicts) { }
    public record Result(int applied, int requested) { }

    private ManualSuffixCleanup() { }

    /** Pure, deterministic planning; name comparisons follow AR's case-insensitive rules. */
    public static NamePlan planNames(List<String> names) {
        if (names == null) return new NamePlan(List.of(), List.of());
        Set<String> existing = new HashSet<>();
        Map<String, Integer> destinationCounts = new HashMap<>();
        for (String name : names) {
            if (name != null) existing.add(name.toLowerCase(Locale.ROOT));
            String candidate = strip(name);
            if (candidate != null && !candidate.equals(name)) {
                String key = candidate.toLowerCase(Locale.ROOT);
                destinationCounts.put(key, destinationCounts.getOrDefault(key, 0) + 1);
            }
        }
        List<Rename> changes = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            String candidate = strip(name);
            if (candidate == null || candidate.equals(name)) continue;
            String key = candidate.toLowerCase(Locale.ROOT);
            if (existing.contains(key) || destinationCounts.getOrDefault(key, 0) != 1) {
                conflicts.add(name + " → " + candidate + " (name conflict)");
            } else {
                changes.add(new Rename(i, name, candidate));
            }
        }
        return new NamePlan(List.copyOf(changes), List.copyOf(conflicts));
    }

    private static String strip(String name) {
        return name != null && name.length() > 3 && name.endsWith("__c")
                ? name.substring(0, name.length() - 3) : name;
    }

    public static NamePlan inspect(IFormObject form, Kind kind) {
        if (form == null || kind == null) throw new IllegalArgumentException("Form and scope required");
        List<String> names = new ArrayList<>();
        if (kind == Kind.FIELD_NAMES) {
            Collection<IFieldObject> fields = form.getFields();
            if (fields != null) for (IFieldObject field : fields) names.add(field.getName());
        } else {
            Collection<ARView> views = form.getViews();
            if (views != null) for (ARView view : views) names.add(view.getName());
        }
        return planNames(names);
    }

    /**
     * Apply the validated plan. The form is reloaded in the calling Job before
     * planning, never taken from an unsaved editor. On any server failure stop;
     * do not report partial changes as success. Caller must refresh the form.
     */
    public static Result apply(IFormObject form, Kind kind, NamePlan plan) throws Exception {
        if (form == null || form.getStore() == null || kind == null || plan == null)
            throw new IllegalArgumentException("Form, store, scope and plan required");
        IStore store = form.getStore();
        List<? extends IModelObject> objects = kind == Kind.FIELD_NAMES
                ? new ArrayList<IFieldObject>(form.getFields()) : new ArrayList<ARView>(form.getViews());
        int succeeded = 0;
        for (Rename change : plan.renames()) {
            if (change.index() < 0 || change.index() >= objects.size())
                throw new IllegalStateException("Form contents changed during suffix cleanup");
            IModelObject object = objects.get(change.index());
            if (!change.original().equals(object.getName()))
                throw new IllegalStateException("Object name changed during suffix cleanup: " + change.original());
            try {
                object.setNewName(change.replacement());
                object.setName(change.replacement());
                if (object instanceof IFieldObject field) field.setDirty(true);
                if (object instanceof ARView view) view.setDirty(true);
                store.storeObject(kind == Kind.FIELD_NAMES ? IARSystemTypes.FIELD : IARSystemTypes.VIEW,
                        object, true, true);
                succeeded++;
            } catch (Exception e) {
                throw new IllegalStateException("Renamed " + succeeded + " of " + plan.renames().size()
                        + " objects before failure on '" + change.original()
                        + "'. Check the server state before retrying.", e);
            }
        }
        return new Result(succeeded, plan.renames().size());
    }
}
