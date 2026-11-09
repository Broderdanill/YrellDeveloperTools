package se.yrell.developertools.midtier;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.progress.IProgressConstants;

import se.yrell.developertools.Log;

/** A user-visible Eclipse Progress job. No modal dialog stays open while it runs. */
public final class MidTierFlushJob extends Job {
    private final Shell parent;
    private final MidTierEditorContext context;
    private final MidTierConfiguration.Mapping target;
    private final int mode;
    private final String form;
    private final CompletableFuture<MidTierHttpClient.Result> browserResult = new CompletableFuture<>();
    private volatile String detail = "Waiting to start";
    private volatile boolean active = true;
    private MidTierFlushDetails details; // accessed only on SWT UI thread

    private MidTierFlushJob(Shell parent, MidTierEditorContext context,
            MidTierConfiguration.Mapping target, int mode, String form) {
        super("MidTier flush: " + (mode == 0 ? form : mode == 1 ? "full cache" : "all pods"));
        this.parent = parent;
        this.context = context;
        this.target = target;
        this.mode = mode;
        this.form = form;
        setUser(true); // Eclipse Progress view, not a hidden system job.
        setProperty(IProgressConstants.KEEP_PROPERTY, Boolean.TRUE);
        setProperty(IProgressConstants.KEEPONE_PROPERTY, Boolean.TRUE);
        setProperty(IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY, Boolean.TRUE);
        setProperty(IProgressConstants.ACTION_PROPERTY, new Action("Open MidTier flush details") {
            @Override public void run() { openDetails(); }
        });
    }

    public static void start(Shell parent, MidTierEditorContext context,
            MidTierConfiguration.Mapping target, int mode, String form) {
        if (mode < 0 || mode > 2) throw new IllegalArgumentException("Unsupported flush operation");
        if (mode == 0 && (form == null || form.isBlank())) throw new IllegalArgumentException("Form name required");
        new MidTierFlushJob(parent, context, target, mode, form == null ? "" : form).schedule();
    }

    @Override public boolean belongsTo(Object family) { return family == MidTierFlushJob.class; }

    private void report(String message) {
        detail = message;
        Display display = Display.getDefault();
        if (display != null && !display.isDisposed()) display.asyncExec(() -> {
            if (details != null) details.update(detail);
        });
    }

    private void openDetails() {
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) return;
        display.asyncExec(() -> {
            if (details == null || details.isDisposed())
                details = new MidTierFlushDetails(parent, target, form, mode, () -> active);
            details.open();
            details.update(detail);
        });
    }

    private MidTierHttpClient.Result viaBrowser(IProgressMonitor monitor) throws Exception {
        CompletableFuture<Void> created = new CompletableFuture<>();
        Display display = Display.getDefault();
        if (display == null || display.isDisposed()) throw new IllegalStateException("SWT Display is unavailable");
        display.asyncExec(() -> {
            if (!active || monitor.isCanceled()) {
                created.completeExceptionally(new CancellationException());
                return;
            }
            try {
                if (details == null || details.isDisposed())
                    details = new MidTierFlushDetails(parent, target, form, mode, () -> active);
                // Do NOT open a dialog for quick actions. Browser login runs in the
                // retained native control; Progress > Details opens it on demand.
                details.beginBrowser(
                    target.authenticationMethod() == MidTierConfiguration.AuthenticationMethod.AUTO
                        ? () -> MidTierStudioCredentials.forServer(context.server()) : null,
                    message -> report(message),
                    result -> browserResult.complete(result),
                    error -> browserResult.completeExceptionally(new IllegalStateException(error)));
                created.complete(null);
            } catch (Throwable e) { created.completeExceptionally(e); }
        });
        while (true) {
            if (monitor.isCanceled()) throw new InterruptedException("Cache flush canceled");
            try { created.get(250, TimeUnit.MILLISECONDS); break; }
            catch (TimeoutException ignored) { }
        }
        while (true) {
            if (monitor.isCanceled()) throw new InterruptedException("Cache flush canceled");
            try { return browserResult.get(250, TimeUnit.MILLISECONDS); }
            catch (TimeoutException ignored) { }
            catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception exception) throw exception;
                throw new IllegalStateException(cause);
            }
        }
    }

    @Override protected IStatus run(IProgressMonitor monitor) {
        monitor.beginTask("Flushing MidTier cache", IProgressMonitor.UNKNOWN);
        try {
            report("Connecting to MidTier…");
            MidTierHttpClient.Result result;
            if (target.authenticationMethod() == MidTierConfiguration.AuthenticationMethod.SSO) {
                report("Waiting for MidTier SSO session…");
                result = viaBrowser(monitor);
            } else {
                try {
                    result = MidTierHttpClient.forTarget(target).flush(
                        mode, form, target.allowHttpCredentials(),
                        () -> MidTierStudioCredentials.forServer(context.server()),
                        target.authenticationMethod() == MidTierConfiguration.AuthenticationMethod.AUTO);
                } catch (MidTierHttpClient.BrowserAuthenticationRequired e) {
                    report("MidTier requires browser authentication…");
                    result = viaBrowser(monitor);
                }
            }
            String summary = switch (result.outcome()) {
                case CLEANED -> "Cache flushed successfully";
                case ALREADY_EMPTY -> "Already empty – nothing to flush";
                case ACCEPTED_UNVERIFIED -> "Request sent – result could not be verified";
            };
            report(summary + (result.message().isBlank() ? "" : ": " + result.message()));
            Log.info("MidTier cache: " + summary + " for " + context.server() + "/" + form);
            return result.outcome() == MidTierHttpClient.Outcome.ACCEPTED_UNVERIFIED
                    ? new Status(IStatus.WARNING, "se.yrell.developertools", summary) : Status.OK_STATUS;
        } catch (InterruptedException | CancellationException e) {
            Thread.currentThread().interrupt();
            report("Canceled – server-side result not verified");
            return Status.CANCEL_STATUS;
        } catch (Exception e) {
            Log.error("MidTier cache failed for AR server " + context.server(), e);
            report("MidTier flush failed: " + e.getMessage());
            return new Status(IStatus.ERROR, "se.yrell.developertools", "MidTier flush failed", e);
        } finally {
            active = false;
            // Prevent a canceled job from submitting after it has ended.
            Display display = Display.getDefault();
            if (display != null && !display.isDisposed()) display.asyncExec(() -> {
                if (details != null && !details.isDisposed()) details.stopBrowser();
            });
            monitor.done();
        }
    }
}
