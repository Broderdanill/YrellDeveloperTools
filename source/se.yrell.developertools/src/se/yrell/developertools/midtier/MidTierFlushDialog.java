package se.yrell.developertools.midtier;

import java.util.List;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

import se.yrell.developertools.Log;

/**
 * Uses BMC's own Configuration Tool and its authenticated browser session.
 * Submission reuses the LIVE form and cfgStok already supplied by MidTier;
 * a Studio ARServerUser ticket is not a MidTier config login token.
 */
public final class MidTierFlushDialog extends Dialog {
    private final MidTierEditorContext context;
    private final MidTierConfiguration.Mapping target;
    private Browser browser;
    private Combo formChoice;
    private Combo operationChoice;
    private Label status;

    public MidTierFlushDialog(Shell shell, MidTierEditorContext context, MidTierConfiguration.Mapping target) {
        super(shell);
        this.context = context;
        this.target = target;
        setShellStyle(getShellStyle() | SWT.RESIZE | SWT.MAX);
    }
    @Override protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText("MidTier Cache Flush");
    }
    @Override protected Control createDialogArea(Composite parent) {
        Composite root = (Composite) super.createDialogArea(parent);
        root.setLayout(new GridLayout(2, false));
        new Label(root, SWT.NONE).setText("AR Server / MidTier:");
        Label server = new Label(root, SWT.WRAP);
        server.setText(context.server() + "  →  " + target.baseUrl());
        server.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

        new Label(root, SWT.NONE).setText("Form:");
        formChoice = new Combo(root, SWT.READ_ONLY);
        for (String form : context.forms()) formChoice.add(form);
        if (formChoice.getItemCount() > 0) formChoice.select(0);
        formChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        if (context.workflow() && context.forms().size() > 1) {
            Label hint = new Label(root, SWT.WRAP);
            hint.setText("Workflow references several forms. Select the form whose cache should be cleared.");
            GridData data = new GridData(SWT.FILL, SWT.TOP, true, false);
            data.horizontalSpan = 2;
            hint.setLayoutData(data);
        }

        new Label(root, SWT.NONE).setText("Operation:");
        operationChoice = new Combo(root, SWT.READ_ONLY);
        operationChoice.setItems(new String[] { "Flush selected form", "Flush complete MidTier cache", "Flush complete cache on all pods" });
        operationChoice.select(0);
        operationChoice.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        operationChoice.addListener(SWT.Selection, e -> openPage());

        Label note = new Label(root, SWT.WRAP);
        note.setText("Sign in to BMC MidTier Configuration Tool below if required. Use an administrator authorized for the Configuration Tool. Login and security token stay in this browser session. No password is stored by Yrell Developer Tools.");
        GridData noteData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        noteData.horizontalSpan = 2;
        note.setLayoutData(noteData);

        status = new Label(root, SWT.WRAP);
        status.setText("Opening Configuration Tool...");
        GridData statusData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        statusData.horizontalSpan = 2;
        status.setLayoutData(statusData);
        browser = new Browser(root, SWT.BORDER);
        GridData browserData = new GridData(SWT.FILL, SWT.FILL, true, true);
        browserData.horizontalSpan = 2;
        browserData.widthHint = 930;
        browserData.heightHint = 480;
        browser.setLayoutData(browserData);
        browser.addProgressListener(new ProgressAdapter() {
            @Override public void completed(ProgressEvent event) {
                if (browser == null || browser.isDisposed() || status.isDisposed()) return;
                if (!MidTierConfiguration.isExpectedPage(browser.getUrl(), target, isForm())) {
                    status.setText("Login or redirection page. After signing in, use 'Reload cache page' below.");
                } else {
                    status.setText("Configuration Tool loaded. Confirm the operation with 'Execute flush'.");
                }
                status.getParent().layout(true, true);
            }
        });
        root.getDisplay().asyncExec(() -> {
            if (browser != null && !browser.isDisposed()) openPage();
        });
        return root;
    }

    @Override protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, 1001, "Reload cache page", false);
        createButton(parent, IDialogConstants.OK_ID, "Execute flush", false);
        createButton(parent, IDialogConstants.CANCEL_ID, "Close", true);
    }
    @Override protected void buttonPressed(int buttonId) {
        if (buttonId == 1001) { openPage(); return; }
        if (buttonId == IDialogConstants.OK_ID) { executeFlush(); return; }
        super.buttonPressed(buttonId);
    }
    private boolean isForm() { return operationChoice != null && operationChoice.getSelectionIndex() == 0; }
    private void openPage() {
        if (browser == null || browser.isDisposed()) return;
        status.setText("Loading cache page...");
        browser.setUrl(MidTierConfiguration.pageUrl(target, isForm()));
    }

    private void executeFlush() {
        if (browser == null || browser.isDisposed()) return;
        if (!MidTierConfiguration.isExpectedPage(browser.getUrl(), target, isForm())) {
            MessageDialog.openInformation(getShell(), "MidTier Cache Flush",
                    "The required cache page is not open. Log in to the Configuration Tool, select 'Reload cache page', then try again.");
            return;
        }
        int mode = operationChoice.getSelectionIndex();
        String form = formChoice.getText();
        if (mode == 0 && form.isBlank()) return;
        String scope = mode == 0 ? "the form '" + form + "' on AR server '" + context.server() + "'"
                : mode == 1 ? "the ENTIRE MidTier cache (all AR servers and tenants)"
                : "the ENTIRE MidTier cache on ALL PODS (all AR servers and tenants)";
        if (!MessageDialog.openConfirm(getShell(), "Confirm MidTier cache flush",
                "Flush " + scope + "?\n\nTarget: " + target.baseUrl()
                        + "\n\nComplete cache flushes can have a significant performance impact. This action cannot be undone.")) return;
        try {
            String result = (String) browser.evaluate(makeSubmissionScript(mode, context.server(), form,
                    new java.net.URI(target.baseUrl()).getPath()));
            if ("SUBMITTED".equals(result)) {
                status.setText("Request submitted. Check the MidTier response below; success is not assumed.");
                Log.info("MidTier flush submitted: mode=" + mode + ", AR server=" + context.server()
                        + ", form=" + (mode == 0 ? form : "<entire-cache>") + ", URL=" + target.baseUrl());
            } else {
                String detail = switch (result == null ? "" : result) {
                    case "NO_TOKEN" -> "No live cfgStok token found. The Configuration Tool may need a login.";
                    case "NO_FORM" -> "Expected cache form was not found in the loaded page.";
                    case "NO_SERVER" -> "The AR server is not offered by this MidTier. Check the server mapping and server list.";
                    case "BAD_ACTION" -> "The submitted HTML form action does not match the configured MidTier. No request was sent.";
                    default -> "The MidTier page could not be used. Response: " + String.valueOf(result);
                };
                MessageDialog.openError(getShell(), "MidTier Cache Flush", detail);
            }
        } catch (Exception e) {
            Log.error("Could not submit MidTier flush", e);
            MessageDialog.openError(getShell(), "MidTier Cache Flush",
                    "Could not submit the request. No successful flush has been confirmed.\n" + e.getMessage());
        }
    }

    /** ES5-compatible because SWT Browser may use the Windows IE engine. */
    static String makeSubmissionScript(int mode, String server, String form, String contextPath) {
        String expected = contextPath + "/servlet/GoatConfigServlet";
        return "(function(){"
                + "var f=document.forms[" + quote(mode == 0 ? "cache_clenaup_form" : "flush_cache_form") + "];"
                + "if(!f)return 'NO_FORM';"
                + "var tok=f.elements['cfgStok'];if(!tok||!tok.value)return 'NO_TOKEN';"
                + "var a=document.createElement('a');a.href=f.action;"
                + "if(a.protocol!==location.protocol||a.hostname.toLowerCase()!==location.hostname.toLowerCase()"
                + "||a.port!==location.port||a.pathname!==" + quote(expected)
                + "||a.search.indexOf('action=Cache')<0"
                + (mode == 0 ? "||a.search.indexOf('op_type=form_cleanup')<0" : "")
                + ")return 'BAD_ACTION';"
                + (mode == 0
                    ? "var select=f.elements['server'];if(!select)return 'NO_SERVER';"
                        + "var found=false;for(var i=0;i<select.options.length;i++){"
                        + "if(select.options[i].value.toLowerCase()===" + quote(server.toLowerCase(java.util.Locale.ROOT))
                        + "){select.selectedIndex=i;found=true;break;}}"
                        + "if(!found)return 'NO_SERVER';"
                        + "var name=f.elements['form_name'];var flush=f.elements['form_flush'];"
                        + "if(!name||!flush)return 'NO_FORM';name.value=" + quote(form) + ";flush.value='Yes';"
                    : "var key=f.elements[" + quote(mode == 2 ? "cache_flush_allpods" : "cache_flush") + "];"
                        + "if(!key)return 'NO_FORM';key.value='Yes';"
                        + "var other=f.elements[" + quote(mode == 2 ? "cache_flush" : "cache_flush_allpods") + "];"
                        + "if(other)other.value='No';")
                + "f.submit();return 'SUBMITTED';})()";
    }

    private static String quote(String value) {
        StringBuilder b = new StringBuilder("'");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') b.append("\\\\");
            else if (c == '\'') b.append("\\'");
            else if (c == '\n') b.append("\\n");
            else if (c == '\r') b.append("\\r");
            else if (c == '<') b.append("\\x3c");
            else if (c == '>') b.append("\\x3e");
            else if (c == '\u2028' || c == '\u2029') b.append(String.format("\\u%04x", (int)c));
            else b.append(c);
        }
        return b.append("'").toString();
    }
}
