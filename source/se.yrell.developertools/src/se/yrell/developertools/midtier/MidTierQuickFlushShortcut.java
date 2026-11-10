package se.yrell.developertools.midtier;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jface.bindings.Binding;
import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.jface.bindings.keys.ParseException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.keys.IBindingService;

import com.bmc.arsys.studio.ui.views.objectlist.ObjectListView;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/** An opt-in shortcut because BMC Developer Studio does not expose Eclipse Keys UI. */
public final class MidTierQuickFlushShortcut {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private MidTierQuickFlushShortcut() { }

    public static String normalize(String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    }

    /** Returns a user-facing error, or null if disabled/valid/non-conflicting. */
    public static String validate(String raw) {
        String value = normalize(raw);
        if (value.isEmpty()) return null;
        if (!value.matches("CTRL\\+ALT\\+(SHIFT\\+)?(F([1-9]|1[0-2])|[A-Z])"))
            return "Use CTRL+ALT+F8 or CTRL+ALT+SHIFT+F8 (F1-F12 or A-Z). Leave empty to disable.";
        try {
            KeySequence seq = KeySequence.getInstance(value);
            if (seq.getKeyStrokes().length != 1) return "Only a single key combination is supported.";
            if (PlatformUI.isWorkbenchRunning()) {
                IBindingService bs = PlatformUI.getWorkbench().getService(IBindingService.class);
                if (bs != null) for (Binding b : bs.getBindings()) {
                    if (b != null && b.getParameterizedCommand() != null && seq.equals(b.getTriggerSequence()))
                        return "This shortcut is already registered by: " + b.getParameterizedCommand().getCommand().getName();
                }
            }
        } catch (ParseException ex) {
            return "Unrecognized shortcut: " + value;
        } catch (Exception ex) {
            return "Could not verify shortcut conflicts: " + ex.getClass().getSimpleName();
        }
        return null;
    }

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Display display = PlatformUI.getWorkbench().getDisplay();
            display.asyncExec(() -> {
                if (display.isDisposed()) return;
                display.addFilter(SWT.KeyDown, new Listener() {
                    @Override public void handleEvent(Event e) { onKey(e); }
                });
            });
        } catch (RuntimeException ex) {
            INSTALLED.set(false);
            Log.warn("MidTier quick shortcut could not be installed: " + ex.getMessage());
        }
    }

    private static void onKey(Event e) {
        if (!ToolsPreferences.isMidTierCacheEnabled()) return;
        String saved = normalize(ToolsPreferences.getMidTierQuickShortcut());
        if (saved.isEmpty() || !matches(saved, e.stateMask, e.keyCode)) return;
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null || window.getShell() == null || window.getShell().isDisposed()) return;
        // Never consume shortcuts typed into modal dialogs / settings / authentication windows.
        if (window.getShell() != e.display.getActiveShell()) return;
        IWorkbenchPage page = window.getActivePage();
        if (page == null) return;
        IWorkbenchPart part = page.getActivePart();
        MidTierEditorContext context;
        if (part instanceof ObjectListView) {
            ISelection selection = part.getSite().getSelectionProvider().getSelection();
            context = MidTierEditorContext.captureSelected(selection);
        } else if (part instanceof org.eclipse.ui.IEditorPart editor) {
            context = MidTierEditorContext.capture(editor);
        } else return;
        // Invalid or ambiguous context must NOT steal the key from Developer Studio.
        if (context == null || context.forms().isEmpty()) return;
        if (MidTierFlushObjectHandler.startDirect(window.getShell(), context)) {
            e.doit = false;
        }
    }

    public static boolean matches(String shortcut, int stateMask, int keyCode) {
        String s = normalize(shortcut);
        if (s.isEmpty()) return false;
        int expectedMods = SWT.CTRL | SWT.ALT;
        if (s.contains("SHIFT+")) expectedMods |= SWT.SHIFT;
        int actualMods = stateMask & (SWT.CTRL | SWT.ALT | SWT.SHIFT | SWT.COMMAND);
        if (actualMods != expectedMods) return false;
        String key = s.substring(s.lastIndexOf('+') + 1);
        if (key.length() == 1) return Character.toUpperCase((char) keyCode) == key.charAt(0);
        if (!key.startsWith("F")) return false;
        try {
            int n = Integer.parseInt(key.substring(1));
            int[] codes = {SWT.F1,SWT.F2,SWT.F3,SWT.F4,SWT.F5,SWT.F6,SWT.F7,SWT.F8,SWT.F9,SWT.F10,SWT.F11,SWT.F12};
            return n >= 1 && n <= 12 && keyCode == codes[n - 1];
        } catch (NumberFormatException ex) { return false; }
    }
}
