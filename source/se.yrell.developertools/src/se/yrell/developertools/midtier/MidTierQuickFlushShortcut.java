package se.yrell.developertools.midtier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.jface.bindings.Binding;
import org.eclipse.jface.bindings.Scheme;
import org.eclipse.jface.bindings.keys.KeyBinding;
import org.eclipse.jface.bindings.keys.KeySequence;
import org.eclipse.jface.bindings.keys.ParseException;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.keys.IBindingService;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/**
 * Persists a USER key binding through Eclipse's public IBindingService API.
 *
 * 0.1.58 installed an SWT Display filter.  That approach was not integrated
 * with the workbench's key dispatcher, could fail when its early-startup chain
 * stopped, and required an exact match on the active BMC view/shell.
 *
 * No SYSTEM binding is declared in plugin.xml: the user must explicitly opt in
 * by setting a shortcut in Yrell Developer Tools preferences.
 */
public final class MidTierQuickFlushShortcut {
    public static final String COMMAND_ID = "se.yrell.developertools.commands.midTierFlushObject";
    private static final String WINDOW_CONTEXT = "org.eclipse.ui.contexts.window";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
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
            KeySequence sequence = KeySequence.getInstance(value);
            if (sequence.getKeyStrokes().length != 1)
                return "Only a single key combination is supported.";
            if (!PlatformUI.isWorkbenchRunning()) return null;
            IBindingService bindings = PlatformUI.getWorkbench().getService(IBindingService.class);
            if (bindings == null) return "Eclipse keyboard binding service is unavailable.";
            return conflict(bindings, sequence);
        } catch (ParseException e) {
            return "Unrecognized shortcut: " + value;
        } catch (RuntimeException e) {
            return "Could not verify Eclipse shortcut: " + e.getClass().getSimpleName();
        }
    }

    private static String conflict(IBindingService service, KeySequence proposed) {
        // getConflictsFor evaluates active scheme/context collisions, unlike a raw
        // scan of every registered binding (which also includes inactive schemes).
        for (Object candidate : service.getConflictsFor(proposed)) {
            if (!(candidate instanceof Binding binding)) continue;
            if (isOurCommand(binding)) continue;
            if (binding.getParameterizedCommand() == null) continue;
            String name;
            try { name = binding.getParameterizedCommand().getCommand().getName(); }
            catch (Exception ignored) { name = binding.getParameterizedCommand().getId(); }
            return "This shortcut conflicts with Eclipse command: " + name;
        }
        // For builds whose getConflictsFor excludes perfect matches, double check.
        Binding perfect = service.getPerfectMatch(proposed);
        if (perfect != null && !isOurCommand(perfect) && perfect.getParameterizedCommand() != null)
            return "This shortcut is already in use by Eclipse: "
                + perfect.getParameterizedCommand().getId();
        return null;
    }

    private static boolean isOurCommand(Binding binding) {
        if (binding == null || binding.getParameterizedCommand() == null) return false;
        return COMMAND_ID.equals(binding.getParameterizedCommand().getId());
    }

    /** Install independently of all other startup features. */
    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        if (!PlatformUI.isWorkbenchRunning()) {
            INSTALLED.set(false);
            Log.warn("MidTier shortcut: Eclipse workbench is not running during startup.");
            return;
        }
        refresh();
    }

    /** Apply a changed preference to the *active* Eclipse binding manager. */
    public static void refresh() {
        try {
            Display display = PlatformUI.getWorkbench().getDisplay();
            if (display == null || display.isDisposed()) return;
            Runnable sync = () -> {
                try { synchronize(); }
                catch (Throwable t) { Log.error("MidTier shortcut registration failed", t); }
            };
            if (Display.getCurrent() == display) sync.run();
            else display.asyncExec(sync);
        } catch (Throwable t) {
            Log.error("Could not schedule MidTier shortcut registration", t);
        }
    }

    private static void synchronize() throws IOException, ParseException {
        IBindingService service = PlatformUI.getWorkbench().getService(IBindingService.class);
        ICommandService commands = PlatformUI.getWorkbench().getService(ICommandService.class);
        if (service == null || commands == null) {
            Log.warn("MidTier shortcut: Eclipse command/binding service is unavailable.");
            return;
        }
        String saved = normalize(ToolsPreferences.getMidTierQuickShortcut());
        if (!ToolsPreferences.isMidTierCacheEnabled()) saved = "";

        // Preserve all BMC/Eclipse bindings, including other USER bindings.
        // Only USER entries belonging to our command are replaced or removed.
        List<Binding> next = new ArrayList<>();
        boolean foundOwn = false;
        for (Binding binding : service.getBindings()) {
            if (binding.getType() == Binding.USER && isOurCommand(binding)) {
                foundOwn = true;
                continue;
            }
            next.add(binding);
        }
        if (saved.isEmpty() && !foundOwn) return;
        Scheme activeScheme = service.getActiveScheme();
        if (activeScheme == null) {
            Log.warn("MidTier shortcut: no active Eclipse key scheme.");
            return;
        }
        if (!saved.isEmpty()) {
            KeySequence sequence = KeySequence.getInstance(saved);
            String collision = conflict(service, sequence);
            if (collision != null) {
                // Keep pre-existing bindings unchanged and make the failure visible.
                Log.warn("MidTier shortcut " + saved + " not activated: " + collision);
                return;
            }
            Command command = commands.getCommand(COMMAND_ID);
            if (command == null || !command.isDefined()) {
                Log.warn("MidTier shortcut: command is not registered in Eclipse: " + COMMAND_ID);
                return;
            }
            KeyBinding key = new KeyBinding(sequence,
                    new ParameterizedCommand(command, null),
                    activeScheme.getId(), WINDOW_CONTEXT, null, null, null, Binding.USER);
            next.add(key);
        }
        service.savePreferences(activeScheme, next.toArray(new Binding[0]));
        Log.info(saved.isEmpty() ? "MidTier shortcut removed from Eclipse key bindings"
                : "MidTier shortcut registered with Eclipse: " + saved + " (scheme=" + activeScheme.getId() + ")");
    }

    /** Retained for pure compatibility tests of the accepted key combinations. */
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
