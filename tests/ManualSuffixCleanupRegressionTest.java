import java.util.ArrayList;
import java.util.List;
import se.yrell.developertools.ManualSuffixCleanup;

/** Explicit selected-form cleanup should never rename unrelated or colliding names. */
public class ManualSuffixCleanupRegressionTest {
    private static int checks;
    private static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
        checks++;
    }
    private static void testActualFieldAndViewPersistence() throws Exception {
        var saved = new ArrayList<String>();
        var store = (com.bmc.arsys.studio.model.store.IStore) java.lang.reflect.Proxy.newProxyInstance(
            ManualSuffixCleanupRegressionTest.class.getClassLoader(),
            new Class<?>[]{com.bmc.arsys.studio.model.store.IStore.class}, (proxy, method, args) -> {
                if (method.getName().equals("storeObject")) {
                    saved.add(args[0].toString() + ":" + ((com.bmc.arsys.studio.model.store.IModelObject)args[1]).getName());
                    return List.of();
                }
                if (method.getName().equals("getName")) return "test-arserver";
                if (method.getName().equals("isConnected")) return true;
                return null;
            });
        var fields = new ArrayList<com.bmc.arsys.studio.model.store.IFieldObject>();
        for (String name : List.of("Existing", "Existing__c", "RenameMe__c", "Another__c")) {
            var holder = new String[]{name};
            fields.add((com.bmc.arsys.studio.model.store.IFieldObject) java.lang.reflect.Proxy.newProxyInstance(
                ManualSuffixCleanupRegressionTest.class.getClassLoader(),
                new Class<?>[]{com.bmc.arsys.studio.model.store.IFieldObject.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return holder[0];
                    if (method.getName().equals("setName")) {holder[0] = (String)args[0]; return null;}
                    return null;
                }));
        }
        var views = new ArrayList<com.bmc.arsys.studio.model.ar.ARView>();
        var view = new com.bmc.arsys.studio.model.ar.ARView();
        view.setName("Main__c");
        views.add(view);
        var form = (com.bmc.arsys.studio.model.store.IFormObject) java.lang.reflect.Proxy.newProxyInstance(
            ManualSuffixCleanupRegressionTest.class.getClassLoader(),
            new Class<?>[]{com.bmc.arsys.studio.model.store.IFormObject.class}, (proxy, method, args) -> {
                if (method.getName().equals("getFields")) return fields;
                if (method.getName().equals("getViews")) return views;
                if (method.getName().equals("getStore")) return store;
                if (method.getName().equals("getName")) return "TestForm";
                return null;
            });
        var fieldPlan = ManualSuffixCleanup.inspect(form, ManualSuffixCleanup.Kind.FIELD_NAMES);
        check(fieldPlan.renames().size() == 2, "two field renames in selected form");
        check(fieldPlan.conflicts().size() == 1, "existing name blocks a field rename");
        var result = ManualSuffixCleanup.apply(form, ManualSuffixCleanup.Kind.FIELD_NAMES, fieldPlan);
        check(result.applied() == 2, "persisted two fields via BMC storeObject");
        check(fields.get(1).getName().equals("Existing__c"), "left colliding field unchanged");
        check(fields.get(2).getName().equals("RenameMe"), "renamed persisted field");
        check(view.getName().equals("Main__c"), "field operation leaves view alone");
        check(saved.size() == 2, "two server save operations");
        var viewPlan = ManualSuffixCleanup.inspect(form, ManualSuffixCleanup.Kind.VIEW_NAMES);
        check(viewPlan.renames().size() == 1, "view separately selected");
        ManualSuffixCleanup.apply(form, ManualSuffixCleanup.Kind.VIEW_NAMES, viewPlan);
        check(view.getName().equals("Main"), "view name updated");
        check(saved.size() == 3, "view separately persisted");
        check(ManualSuffixCleanup.inspect(form, ManualSuffixCleanup.Kind.FIELD_NAMES).renames().isEmpty(),
            "no duplicate field renames after completion");
    }

    public static void main(String[] args) throws Exception {
        var empty = ManualSuffixCleanup.planNames(List.of("Field", "View", "__c"));
        check(empty.renames().isEmpty(), "no __c suffixes to remove");
        check(empty.conflicts().isEmpty(), "no fake conflicts");

        var simple = ManualSuffixCleanup.planNames(List.of("ABC__c", "XYZ", "Test__c"));
        check(simple.renames().size() == 2, "two matching saved names");
        check(simple.renames().get(0).index() == 0, "stable source index");
        check(simple.renames().get(0).original().equals("ABC__c"), "original preserved");
        check(simple.renames().get(0).replacement().equals("ABC"), "only trailing __c removed");
        check(simple.renames().get(1).replacement().equals("Test"), "second name cleaned");
        check(simple.conflicts().isEmpty(), "unambiguous rename");

        var conflicts = ManualSuffixCleanup.planNames(List.of("A__c", "a", "B__c", "b__c", "C__c", "D__c"));
        check(conflicts.renames().size() == 2, "skip existing/ambiguous destinations");
        check(conflicts.conflicts().size() == 3, "detect case-insensitive and duplicate candidate conflicts");
        check(conflicts.renames().get(0).replacement().equals("C"), "keep C");
        check(conflicts.renames().get(1).replacement().equals("D"), "keep D");

        var caseSensitiveSuffix = ManualSuffixCleanup.planNames(List.of("Field__C", "Field__c__more"));
        check(caseSensitiveSuffix.renames().isEmpty(), "only explicit __c ending");

        var immutable = ManualSuffixCleanup.planNames(new ArrayList<>(List.of("Field__c")));
        boolean blocked = false;
        try { immutable.renames().clear(); } catch (UnsupportedOperationException expected) { blocked = true; }
        check(blocked, "plan is immutable");

        var source = java.nio.file.Files.readString(java.nio.file.Path.of(
            "source/se.yrell.developertools/src/se/yrell/developertools/midtier/MidTierObjectListActionProvider.java"));
        check(source.contains("Remove __c Suffix from Field Names..."), "field context menu label");
        check(source.contains("Remove __c Suffix from View Names..."), "view context menu label");
        check(source.contains("selected instanceof FormItem form && !form.isPlaceHolder()"), "form-only and non-placeholder guard");
        check(!source.contains("ManualSuffixCleanupJob.start(site == null ? null : site.getShell(), (FormItem)"), "captured right-click item");
        var worker = java.nio.file.Files.readString(java.nio.file.Path.of(
            "source/se.yrell.developertools/src/se/yrell/developertools/ManualSuffixCleanupJob.java"));
        check(worker.contains("if (!confirm(plan)) return Status.CANCEL_STATUS"), "explicit confirmation prior to server writes");
        check(worker.contains("isFormOpen(store, selected.getName())"), "open-editor overwrite prevention");
        check(worker.contains("setUser(true)"), "background Progress task");
        check(worker.contains("store.getObject(com.bmc.arsys.studio.model.type.IARSystemTypes.FORM, formName)"), "reload selected form");
        var runner = java.nio.file.Files.readString(java.nio.file.Path.of(
            "source/se.yrell.developertools/src/se/yrell/developertools/ManualSuffixCleanup.java"));
        check(!runner.contains("IARSystemTypes.FORM,"), "never rename form name");
        check(runner.contains("IARSystemTypes.FIELD : IARSystemTypes.VIEW"), "mode-specific server writes");
        check(!runner.contains("isRemoveCustomSuffixEnabled"), "explicit action independent of automatic feature toggle");
        testActualFieldAndViewPersistence();
        System.out.println("ManualSuffixCleanupRegressionTest: " + checks + " PASS");
    }
}
