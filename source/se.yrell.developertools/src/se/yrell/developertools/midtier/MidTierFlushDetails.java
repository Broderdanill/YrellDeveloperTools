package se.yrell.developertools.midtier;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

/** Modeless details window, opened from Eclipse Progress or for interactive SSO. */
final class MidTierFlushDetails {
    private final Shell shell;
    private final Label status;
    private final Composite browserArea;
    private final GridData browserLayout;
    private final Button toggle;
    private MidTierSsoBrowser browser;
    private final MidTierConfiguration.Mapping target;
    private final int mode;
    private final String form;

    MidTierFlushDetails(Shell parent, MidTierConfiguration.Mapping target,
            String form, int mode, BooleanSupplier active) {
        this.target = target;
        this.mode = mode;
        this.form = form;
        Display display = Display.getDefault();
        shell = parent != null && !parent.isDisposed()
                ? new Shell(parent, SWT.SHELL_TRIM | SWT.RESIZE)
                : new Shell(display, SWT.SHELL_TRIM | SWT.RESIZE);
        shell.setText("MidTier flush – Details");
        shell.setLayout(new GridLayout(1, false));
        Label name = new Label(shell, SWT.NONE);
        name.setText(mode == 0 ? "Form: " + form : mode == 1 ? "Full MidTier cache" : "All MidTier pods");
        status = new Label(shell, SWT.WRAP);
        GridData statusData = new GridData(SWT.FILL, SWT.TOP, true, false);
        statusData.widthHint = 440;
        status.setLayoutData(statusData);
        status.setText("Starting…");
        toggle = new Button(shell, SWT.PUSH);
        toggle.setText("Show browser / login");
        toggle.setEnabled(false);
        browserArea = new Composite(shell, SWT.NONE);
        browserArea.setLayout(new FillLayout());
        browserLayout = new GridData(SWT.FILL, SWT.FILL, true, true);
        browserLayout.widthHint = 770;
        browserLayout.heightHint = 420;
        browserLayout.exclude = true;
        browserArea.setLayoutData(browserLayout);
        browserArea.setVisible(false);
        toggle.addListener(SWT.Selection, event -> setBrowserVisible(browserLayout.exclude));
        shell.addListener(SWT.Close, e -> {
            // Hide rather than destroy a live RSSO session; Progress can reopen it.
            if (browser != null && active.getAsBoolean()) {
                e.doit = false;
                shell.setVisible(false);
            }
        });
        shell.setSize(505, 180);
    }

    boolean isDisposed() { return shell.isDisposed(); }

    void stopBrowser() {
        if (browser != null) browser.cancel();
    }

    void open() {
        if (shell.isDisposed()) return;
        shell.open();
        shell.forceActive();
    }

    void update(String text) {
        if (shell.isDisposed()) return;
        status.setText(text == null ? "" : text);
        shell.layout(true, true);
    }

    private void setBrowserVisible(boolean show) {
        if (shell.isDisposed()) return;
        browserLayout.exclude = !show;
        browserArea.setVisible(show);
        toggle.setText(show ? "Hide browser / login" : "Show browser / login");
        if (show) shell.setSize(Math.max(890, shell.getSize().x), Math.max(700, shell.getSize().y));
        else shell.setSize(505, 180);
        shell.layout(true, true);
    }

    void beginBrowser(Supplier<MidTierHttpClient.Credentials> supplier,
            Consumer<String> onStatus, Consumer<MidTierHttpClient.Result> onSuccess,
            Consumer<String> onFailure) {
        if (shell.isDisposed()) throw new IllegalStateException("Details window closed");
        if (browser != null) return;
        browser = new MidTierSsoBrowser(browserArea, target, mode, form, supplier,
                message -> { update(message); onStatus.accept(message); },
                result -> { update("Cache response received"); onSuccess.accept(result); },
                error -> { update(error); onFailure.accept(error); });
        toggle.setEnabled(true);
        browser.start();
    }

}
