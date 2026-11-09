package se.yrell.developertools.midtier;

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

/** Small action picker. It closes immediately after starting an Eclipse Progress job. */
public final class MidTierFlushDialog extends Dialog {
    private final MidTierEditorContext context;
    private final MidTierConfiguration.Mapping target;
    private Combo formChoice;
    private Combo operationChoice;

    public MidTierFlushDialog(Shell shell, MidTierEditorContext context, MidTierConfiguration.Mapping target) {
        super(shell);
        this.context = context;
        this.target = target;
    }

    @Override protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("MidTier cache");
    }

    @Override protected Control createDialogArea(Composite parent) {
        Composite root = (Composite) super.createDialogArea(parent);
        root.setLayout(new GridLayout(2, false));
        new Label(root, SWT.NONE).setText("Form");
        formChoice = new Combo(root, SWT.READ_ONLY);
        for (String form : context.forms()) formChoice.add(form);
        boolean hasForm = formChoice.getItemCount() > 0;
        if (hasForm) formChoice.select(0);
        else formChoice.setEnabled(false);
        formChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(root, SWT.NONE).setText("Action");
        operationChoice = new Combo(root, SWT.READ_ONLY);
        operationChoice.setItems(new String[] {"Flush form", "Flush full cache", "Flush all pods"});
        operationChoice.select(hasForm ? 0 : 1);
        operationChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        if (!hasForm) {
            operationChoice.remove(0); // No form context, so full/pods only.
            operationChoice.select(0);
        }
        operationChoice.addListener(SWT.Selection,
                e -> formChoice.setEnabled(hasForm && operationChoice.getSelectionIndex() == 0));
        return root;
    }

    @Override protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.OK_ID, "Start flush", true);
        createButton(parent, IDialogConstants.CANCEL_ID, "Cancel", false);
    }

    @Override protected void okPressed() {
        int mode = operationChoice.getSelectionIndex() + (context.forms().isEmpty() ? 1 : 0);
        String form = formChoice.getText();
        if (mode != 0) {
            String scope = mode == 1 ? "all AR servers and tenants" : "all MidTier pods";
            if (!MessageDialog.openConfirm(getShell(), "Confirm full cache flush",
                    "Flush the complete cache for " + scope + "?\nThis may affect other users.")) return;
        }
        MidTierFlushJob.start(getParentShell(), context, target, mode, form);
        super.okPressed();
    }
}
