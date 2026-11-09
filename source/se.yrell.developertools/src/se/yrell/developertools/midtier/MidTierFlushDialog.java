package se.yrell.developertools.midtier;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import se.yrell.developertools.Log;

/** Native dialog; does not create a separate SWT Browser session.
 * Form flush needs ONE explicit button press and no extra confirmation.
 */
public final class MidTierFlushDialog extends Dialog {
    private final MidTierEditorContext context;
    private final MidTierConfiguration.Mapping target;
    private Combo formChoice;
    private Combo operationChoice;
    private Label status;
    private volatile boolean running;

    public MidTierFlushDialog(Shell shell, MidTierEditorContext context, MidTierConfiguration.Mapping target) {
        super(shell);
        this.context = context;
        this.target = target;
        setShellStyle(getShellStyle() | SWT.RESIZE);
    }
    @Override protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("MidTier Cache Flush");
    }
    @Override protected Control createDialogArea(Composite parent) {
        Composite root = (Composite) super.createDialogArea(parent);
        root.setLayout(new GridLayout(2, false));
        new Label(root, SWT.NONE).setText("AR Server / MidTier:");
        Label targetLabel = new Label(root, SWT.WRAP);
        targetLabel.setText(context.server() + "  →  " + target.baseUrl());
        targetLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(root, SWT.NONE).setText("Form:");
        formChoice = new Combo(root, SWT.READ_ONLY);
        for (String form : context.forms()) formChoice.add(form);
        if (formChoice.getItemCount() > 0) formChoice.select(0);
        formChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(root, SWT.NONE).setText("Operation:");
        operationChoice = new Combo(root, SWT.READ_ONLY);
        operationChoice.setItems(new String[] {
            "Flush selected form", "Flush complete MidTier cache", "Flush cache on all pods"
        });
        operationChoice.select(0);
        operationChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        operationChoice.addListener(SWT.Selection, event -> formChoice.setEnabled(operationChoice.getSelectionIndex() == 0));
        Label note = new Label(root, SWT.WRAP);
        note.setText("Automatic login uses the username and password of the connected Developer Studio AR Server. No password, session cookie or cfgStok is saved in plugin preferences. In RSSO sessions without a reusable password, automatic login cannot be performed. Full flushes can affect all users.");
        GridData noteLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
        noteLayout.horizontalSpan = 2;
        noteLayout.widthHint = 520;
        note.setLayoutData(noteLayout);
        status = new Label(root, SWT.WRAP);
        status.setText("Ready. Select an operation and click Flush now.");
        GridData statusLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
        statusLayout.horizontalSpan = 2;
        status.setLayoutData(statusLayout);
        return root;
    }
    @Override protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Flush now", false);
        createButton(parent, IDialogConstants.CANCEL_ID, "Close", true);
    }
    @Override protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.OK_ID) { executeFlush(); return; }
        super.buttonPressed(buttonId);
    }
    private void executeFlush() {
        if (running) return;
        int mode = operationChoice.getSelectionIndex();
        String form = formChoice.getText();
        if (mode == 0 && form.isBlank()) return;
        if (mode != 0) {
            String scope = mode == 1 ? "the entire MidTier cache for all AR servers and tenants"
                    : "the entire MidTier cache on ALL PODS";
            if (!MessageDialog.openConfirm(getShell(), "Confirm full cache flush",
                    "Flush " + scope + "?\nTarget: " + target.baseUrl()
                    + "\nThis may affect performance for all MidTier users.")) return;
        }
        running = true;
        getButton(IDialogConstants.OK_ID).setEnabled(false);
        status.setText("Authenticating with the connected Developer Studio account and flushing...");
        getShell().layout(true, true);
        Job job = Job.create("Yrell MidTier cache flush", monitor -> {
            try {
                MidTierHttpClient.Result result = MidTierHttpClient.forTarget(target).flush(
                        mode, form, target.allowHttpCredentials(), () -> MidTierStudioCredentials.forServer(context.server()));
                String message = switch (result.outcome()) {
                    case CLEANED -> "Cache flushed: " + form + " (" + context.server() + ")";
                    case ALREADY_EMPTY -> "Already empty: " + form + " (" + context.server() + ")";
                    case ACCEPTED_UNVERIFIED -> "Request completed (HTTP " + result.httpStatus()
                            + "), but MidTier did not confirm a successful flush: " + result.message();
                };
                Log.info("MidTier cache operation: " + message);
                updateStatus(message, false);
                return Status.OK_STATUS;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateStatus("MidTier operation interrupted; result not verified.", true);
                return Status.CANCEL_STATUS;
            } catch (Exception e) {
                // Never log credentials, cookies or the POST body.
                Log.warn("MidTier cache operation failed for server " + context.server()
                        + ", target " + target.baseUrl() + ": " + e.getClass().getSimpleName());
                updateStatus("MidTier flush failed: " + e.getMessage(), true);
                return new Status(IStatus.ERROR, "se.yrell.developertools", "MidTier flush failed");
            }
        });
        job.setUser(false);
        job.schedule();
    }
    private void updateStatus(String text, boolean error) {
        Shell shell = getShell();
        if (shell == null || shell.isDisposed()) return;
        shell.getDisplay().asyncExec(() -> {
            if (shell.isDisposed() || status == null || status.isDisposed()) return;
            running = false;
            status.setText(text);
            getButton(IDialogConstants.OK_ID).setEnabled(true);
            shell.layout(true, true);
        });
    }
}
