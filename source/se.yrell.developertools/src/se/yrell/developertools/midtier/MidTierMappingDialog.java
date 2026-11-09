package se.yrell.developertools.midtier;

import java.util.List;

import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/** Edit one server/URL mapping; intentionally stores no login details. */
public final class MidTierMappingDialog extends TitleAreaDialog {
    private Combo serverField;
    private Text urlField;
    private Text ssoUrlField;
    private Button allowHttp;
    private MidTierConfiguration.Mapping result;
    private final MidTierConfiguration.Mapping initial;
    public MidTierMappingDialog(Shell parent, MidTierConfiguration.Mapping initial) {
        super(parent);
        this.initial = initial;
    }
    @Override protected Control createDialogArea(Composite parent) {
        Composite root = (Composite) super.createDialogArea(parent);
        setTitle("MidTier cache target");
        setMessage("Map a connected AR Server to MidTier. Uses Studio login credentials on demand; never stores a password.");
        Composite fields = new Composite(root, SWT.NONE);
        fields.setLayout(new GridLayout(2, false));
        fields.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        new Label(fields, SWT.NONE).setText("AR Server");
        serverField = new Combo(fields, SWT.DROP_DOWN);
        serverField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        List<String> connected = MidTierEditorContext.connectedServers();
        for (String name : connected) serverField.add(name);
        if (initial != null) serverField.setText(initial.server());
        else if (connected.size() == 1) serverField.setText(connected.get(0));
        new Label(fields, SWT.NONE).setText("MidTier URL");
        urlField = new Text(fields, SWT.BORDER);
        urlField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        if (initial != null) urlField.setText(initial.baseUrl());
        else urlField.setText("https://midtier.example.com/arsys");
        new Label(fields, SWT.NONE).setText("SSO redirect URL (optional)");
        ssoUrlField = new Text(fields, SWT.BORDER);
        ssoUrlField.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        ssoUrlField.setText(initial == null || initial.ssoBaseUrl() == null ? "" : initial.ssoBaseUrl());
        new Label(fields, SWT.NONE).setText("");
        allowHttp = new Button(fields, SWT.CHECK);
        allowHttp.setText("Allow sending Studio credentials over unencrypted HTTP (test only)");
        allowHttp.setSelection(initial != null && initial.allowHttpCredentials());
        new Label(fields, SWT.NONE).setText("");
        Label hint = new Label(fields, SWT.WRAP);
        hint.setText("MidTier: https://midtier.example.com/arsys. If login redirects to a SEPARATE SSO host, optionally allow its exact HTTPS context here, e.g. https://sso.example.com/rsso. This never authorizes sending AR credentials to SSO. A Java HTTP client cannot run browser-based interactive SSO flows.");
        GridData hintData = new GridData(SWT.FILL, SWT.TOP, true, false);
        hintData.widthHint = 480;
        hint.setLayoutData(hintData);
        return root;
    }
    @Override protected void okPressed() {
        try {
            result = new MidTierConfiguration.Mapping(
                    MidTierConfiguration.normalizeServer(serverField.getText()),
                    MidTierConfiguration.normalizeBaseUrl(urlField.getText()), allowHttp.getSelection(),
                    MidTierConfiguration.normalizeSsoUrl(ssoUrlField.getText()));
            super.okPressed();
        } catch (IllegalArgumentException e) {
            setErrorMessage(e.getMessage());
        }
    }
    public MidTierConfiguration.Mapping getResult() { return result; }
}
