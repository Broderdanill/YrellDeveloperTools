package se.yrell.developertools.midtier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.ui.IEditorPart;

import com.bmc.arsys.studio.commonui.common.EditorInput;
import com.bmc.arsys.studio.model.ar.IARWorkflow;
import com.bmc.arsys.studio.model.item.WorkflowItem;
import com.bmc.arsys.studio.model.store.IFormObject;
import com.bmc.arsys.studio.model.store.ARServerStore;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.model.store.StoreManager;
import com.bmc.arsys.studio.ui.editors.form.FormEditor;
import com.bmc.arsys.studio.ui.editors.workflow.WorkflowContentProvider;
import com.bmc.arsys.studio.ui.editors.workflow.WorkflowEditorBase;
import com.bmc.arsys.studio.ui.editors.workflow.WorkflowEditorPage;

/** Exact BMC 26.1.1 editor APIs; never guess a form from an editor title. */
public final class MidTierEditorContext {
    private final String server;
    private final List<String> forms;
    private final boolean workflow;

    private MidTierEditorContext(String server, Collection<String> forms, boolean workflow) {
        this.server = server;
        this.forms = List.copyOf(forms);
        this.workflow = workflow;
    }
    public String server() { return server; }
    public List<String> forms() { return forms; }
    public boolean workflow() { return workflow; }

    public static boolean supports(IEditorPart editor) {
        return editor instanceof FormEditor || editor instanceof WorkflowEditorBase;
    }

    public static MidTierEditorContext capture(IEditorPart editor) {
        if (!supports(editor)) return null;
        String server = "";
        EditorInput input = editor.getEditorInput() instanceof EditorInput e ? e : null;
        if (input != null) server = clean(input.getServer());
        Set<String> forms = new LinkedHashSet<>();
        if (editor instanceof FormEditor e) {
            IFormObject form = e.getFormObject();
            if (form != null) {
                add(forms, form.getName());
                if (server.isEmpty()) server = storeName(form.getStore());
            }
            if (forms.isEmpty() && input != null) add(forms, input.getName());
        } else if (editor instanceof WorkflowEditorBase e) {
            WorkflowEditorPage page = e.getWorkflowDocumentPage();
            WorkflowContentProvider provider = page == null ? null : page.getWorkflowContentProvider();
            if (provider != null) {
                IARWorkflow workflow = provider.getWorkflowObject();
                if (workflow != null) {
                    add(forms, workflow.getPrimaryForm());
                    addAll(forms, workflow.getFormList());
                }
                WorkflowItem item = provider.getWorkflowItem();
                if (item != null) {
                    if (forms.isEmpty()) add(forms, item.getPrimaryForm());
                    if (server.isEmpty()) server = storeName(item.getStore());
                }
            }
            if (forms.isEmpty() && input != null) {
                if (input.getModelObject() instanceof IARWorkflow workflow) {
                    add(forms, workflow.getPrimaryForm());
                    addAll(forms, workflow.getFormList());
                } else if (input.getModelItem() instanceof WorkflowItem item) {
                    add(forms, item.getPrimaryForm());
                }
            }
        }
        if (server.isEmpty() || forms.isEmpty()) return null;
        return new MidTierEditorContext(server, forms, editor instanceof WorkflowEditorBase);
    }

    private static void addAll(Set<String> out, Collection<String> values) {
        if (values != null) for (String value : values) add(out, value);
    }
    private static void add(Set<String> out, String value) { if (!clean(value).isEmpty()) out.add(value.trim()); }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String storeName(IStore store) { return store == null ? "" : clean(store.getName()); }

    public static List<String> connectedServers() {
        List<String> servers = new ArrayList<>();
        try {
            for (IStore store : StoreManager.getInstance().getStores()) {
                if (store instanceof ARServerStore && store.isConnected() && !clean(store.getName()).isEmpty()
                        && !containsIgnoreCase(servers, store.getName())) servers.add(store.getName());
            }
        } catch (RuntimeException ignored) { /* A disconnected or initializing workbench is valid. */ }
        servers.sort(String.CASE_INSENSITIVE_ORDER);
        return servers;
    }
    private static boolean containsIgnoreCase(List<String> servers, String candidate) {
        for (String s : servers) if (s.equalsIgnoreCase(candidate)) return true;
        return false;
    }
}
