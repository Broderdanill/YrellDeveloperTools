package se.yrell.developertools.midtier;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
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
    private Composite browserArea;
    private GridData browserLayout;
    private MidTierSsoBrowser ssoBrowser;
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
        note.setText("Authentication: " + target.authenticationMethod().label() + ". Automatic mode switches to an embedded SSO browser when needed. SSO sessions are handled by the browser engine. Configuration Tool may also require a separate administrator login. No Studio password is sent to the SSO server; no password or cfgStok is stored in plugin preferences. Full flushes affect all users.");
        GridData noteLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
        noteLayout.horizontalSpan = 2;
        noteLayout.widthHint = 520;
        note.setLayoutData(noteLayout);
        status = new Label(root, SWT.WRAP);
        status.setText("Ready. Select an operation and click Flush now.");
        GridData statusLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
        statusLayout.horizontalSpan = 2;
        status.setLayoutData(statusLayout);
        browserArea = new Composite(root, SWT.NONE);
        browserArea.setLayout(new FillLayout());
        browserLayout = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        browserLayout.heightHint = 410;
        browserLayout.widthHint = 780;
        browserLayout.exclude = true;
        browserArea.setVisible(false);
        browserArea.setLayoutData(browserLayout);
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
        status.setText("Connecting to MidTier...");
        getShell().layout(true, true);
        if (target.authenticationMethod() == MidTierConfiguration.AuthenticationMethod.SSO) {
            startSsoBrowser(mode, form);
            return;
        }
        Job job = Job.create("Yrell MidTier cache flush", monitor -> {
            try {
                MidTierHttpClient.Result result = MidTierHttpClient.forTarget(target).flush(
                        mode, form, target.allowHttpCredentials(),
                        () -> MidTierStudioCredentials.forServer(context.server()),
                        target.authenticationMethod() == MidTierConfiguration.AuthenticationMethod.AUTO);
                completeSuccess(result, form);
                return Status.OK_STATUS;
            } catch (MidTierHttpClient.BrowserAuthenticationRequired e) {
                Shell shell = getShell();
                if (shell != null && !shell.isDisposed()) shell.getDisplay().asyncExec(() -> {
                    if (!shell.isDisposed()) startSsoBrowser(mode, form);
                });
                return Status.OK_STATUS;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                updateStatus("MidTier operation interrupted; result not verified.", true);
                return Status.CANCEL_STATUS;
            } catch (Exception e) {
                // Never log credentials, cookies or the POST body.
                // Full stack trace in Eclipse Error Log. Never include POST bodies or session secrets.
                Log.error("MidTier cache operation failed for server " + context.server()
                        + ", target " + target.baseUrl(), e);
                updateStatus("MidTier flush failed: " + e.getMessage(), true);
                return new Status(IStatus.ERROR, "se.yrell.developertools", "MidTier flush failed", e);
            }
        });
        job.setUser(false);
        job.schedule();
    }
    private void startSsoBrowser(int mode, String form) {
        Shell shell = getShell();
        if (shell == null || shell.isDisposed()) return;
        try {
            if (ssoBrowser != null && !ssoBrowser.control().isDisposed()) ssoBrowser.control().dispose();
            browserLayout.exclude = false;
            browserArea.setVisible(true);
            ssoBrowser = new MidTierSsoBrowser(browserArea, target, mode, form,
                    target.authenticationMethod() == MidTierConfiguration.AuthenticationMethod.AUTO
                        ? () -> MidTierStudioCredentials.forServer(context.server()) : null,
                    message -> browserStatus(message),
                    result -> completeSuccess(result, form),
                    message -> {
                        Log.warn("MidTier browser SSO: " + message);
                        updateStatus(message, true);
                    });
            shell.layout(true, true);
            shell.setSize(Math.max(shell.getSize().x, 900), Math.max(shell.getSize().y, 720));
            ssoBrowser.start();
        } catch (RuntimeException e) {
            Log.error("MidTier browser-based SSO could not start", e);
            updateStatus("SSO browser unavailable: " + e.getMessage(), true);
        }
    }
    private void browserStatus(String message) {
        if (status != null && !status.isDisposed()) {
            status.setText(message);
            getShell().layout(true, true);
        }
    }
    private void completeSuccess(MidTierHttpClient.Result result, String form) {
        String message = switch (result.outcome()) {
            case CLEANED -> "Cache flushed: " + form + " (" + context.server() + ")";
            case ALREADY_EMPTY -> "Already empty: " + form + " (" + context.server() + ")";
            case ACCEPTED_UNVERIFIED -> "MidTier request completed, but result not verified: " + result.message();
        };
        Log.info("MidTier cache operation: " + message);
        updateStatus(message, false);
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
