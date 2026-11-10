package se.yrell.developertools;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressConstants;

import com.bmc.arsys.studio.model.item.FormItem;
import com.bmc.arsys.studio.model.store.IFormObject;
import com.bmc.arsys.studio.model.store.IStore;
import com.bmc.arsys.studio.ui.editors.form.FormEditor;

/** Runs an explicit selected-form name change in Eclipse Progress. */
public final class ManualSuffixCleanupJob extends Job {
    private final Shell shell;
    private final IStore store;
    private final String formName;
    private final String server;
    private final ManualSuffixCleanup.Kind kind;

    private ManualSuffixCleanupJob(Shell shell, IStore store, String formName,
                                   ManualSuffixCleanup.Kind kind) {
        super("Remove __c from " + (kind == ManualSuffixCleanup.Kind.FIELD_NAMES ? "fields" : "views")
                + ": " + formName);
        this.shell = shell;
        this.store = store;
        this.formName = formName;
        this.server = store.getName();
        this.kind = kind;
        setUser(true);
        setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
        setProperty(IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY, Boolean.TRUE);
    }

    public static void start(Shell shell, FormItem selected, ManualSuffixCleanup.Kind kind) {
        if (selected == null || selected.isPlaceHolder() || selected.getStore() == null
                || selected.getName() == null || selected.getName().isBlank() || kind == null) return;
        IStore store = selected.getStore();
        if (!store.isConnected()) {
            MessageDialog.openWarning(shell, "Remove __c suffix", "AR server is not connected.");
            return;
        }
        if (isFormOpen(store, selected.getName())) {
            MessageDialog.openWarning(shell, "Remove __c suffix",
                    "Close this form in Developer Studio before renaming saved field or view names. "
                    + "This prevents overwriting edits in an open form editor.");
            return;
        }
        new ManualSuffixCleanupJob(shell, store, selected.getName(), kind).schedule();
    }

    /** Called on UI thread. Never open or modify a form editor to perform a server rename. */
    private static boolean isFormOpen(IStore store, String formName) {
        try {
            for (IWorkbenchWindow window : PlatformUI.getWorkbench().getWorkbenchWindows()) {
                for (IWorkbenchPage page : window.getPages()) {
                    for (IEditorReference ref : page.getEditorReferences()) {
                        if (ref.getEditor(false) instanceof FormEditor editor) {
                            IFormObject form = editor.getFormObject();
                            if (form != null && form.getStore() == store
                                    && formName.equalsIgnoreCase(form.getName())) return true;
                        }
                    }
                }
            }
        } catch (RuntimeException e) {
            Log.warn("Could not inspect open form editors before suffix cleanup: " + e.getMessage());
            return true; // fail closed
        }
        return false;
    }

    @Override protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Inspecting saved names on " + server + "/" + formName, IProgressMonitor.UNKNOWN);
        try {
            if (monitor.isCanceled()) return Status.CANCEL_STATUS;
            if (!store.isConnected()) throw new IllegalStateException("Server disconnected: " + server);
            // Exact selected AR server and form; never fall back to active editor.
            Object loaded = store.getObject(com.bmc.arsys.studio.model.type.IARSystemTypes.FORM, formName);
            if (!(loaded instanceof IFormObject form))
                throw new IllegalStateException("Could not read the selected form from AR server: " + formName);
            ManualSuffixCleanup.NamePlan plan = ManualSuffixCleanup.inspect(form, kind);
            if (plan.renames().isEmpty()) {
                inform("Nothing to change", "No matching names can be renamed on '" + formName
                    + "'." + conflictSummary(plan));
                return Status.OK_STATUS;
            }
            if (!confirm(plan)) return Status.CANCEL_STATUS;
            if (monitor.isCanceled()) return Status.CANCEL_STATUS;
            monitor.subTask("Renaming saved " + (kind == ManualSuffixCleanup.Kind.FIELD_NAMES ? "fields" : "views"));
            ManualSuffixCleanup.Result result = ManualSuffixCleanup.apply(form, kind, plan);
            String summary = "Renamed " + result.applied() + " "
                    + (kind == ManualSuffixCleanup.Kind.FIELD_NAMES ? "field" : "view")
                    + " name(s) in '" + formName + "' on " + server + "." + conflictSummary(plan)
                    + " Refresh Object List or reopen the form to see updated names.";
            Log.info(summary);
            inform("Remove __c suffix complete", summary);
            return Status.OK_STATUS;
        } catch (Exception e) {
            Log.error("Manual __c suffix cleanup failed for " + server + "/" + formName, e);
            inform("Remove __c suffix failed", e.getMessage() == null ? e.toString() : e.getMessage());
            return new Status(IStatus.ERROR, ToolsConstants.PLUGIN_ID, "Remove __c suffix failed", e);
        } finally {
            monitor.done();
        }
    }

    private boolean confirm(ManualSuffixCleanup.NamePlan plan) {
        AtomicBoolean confirmed = new AtomicBoolean();
        String title = kind == ManualSuffixCleanup.Kind.FIELD_NAMES
                ? "Remove __c Suffix from Field Names" : "Remove __c Suffix from View Names";
        StringBuilder message = new StringBuilder("AR server: ").append(server)
                .append("\nForm: ").append(formName)
                .append("\n\nRename ").append(plan.renames().size())
                .append(" saved ").append(kind == ManualSuffixCleanup.Kind.FIELD_NAMES ? "field" : "view")
                .append(" name(s):\n");
        List<ManualSuffixCleanup.Rename> renames = plan.renames();
        for (int i = 0; i < Math.min(12, renames.size()); i++) {
            ManualSuffixCleanup.Rename rename = renames.get(i);
            message.append("  ").append(rename.original()).append(" → ").append(rename.replacement()).append("\n");
        }
        if (renames.size() > 12) message.append("  … and ").append(renames.size() - 12).append(" more\n");
        message.append(conflictSummary(plan));
        for (int i = 0; i < Math.min(5, plan.conflicts().size()); i++)
            message.append("\n  ").append(plan.conflicts().get(i));
        if (plan.conflicts().size() > 5)
            message.append("\n  … and ").append(plan.conflicts().size() - 5).append(" more skipped");
        message.append("\n\nThis changes saved names on the AR server. "
                + "Close any related editors first. The operation has no automatic undo. Continue?");
        withUi(() -> confirmed.set(MessageDialog.openConfirm(validShell(), title, message.toString())));
        return confirmed.get();
    }

    private void inform(String title, String message) {
        withUi(() -> MessageDialog.openInformation(validShell(), title, message));
    }

    private String conflictSummary(ManualSuffixCleanup.NamePlan plan) {
        return plan.conflicts().isEmpty() ? "" : "\nSkipped " + plan.conflicts().size()
                + " rename(s) because the target name already exists or is ambiguous.";
    }

    private Shell validShell() {
        return shell != null && !shell.isDisposed() ? shell : Display.getDefault().getActiveShell();
    }

    private static void withUi(Runnable task) {
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) throw new IllegalStateException("SWT UI is unavailable");
        display.syncExec(task);
    }
}
