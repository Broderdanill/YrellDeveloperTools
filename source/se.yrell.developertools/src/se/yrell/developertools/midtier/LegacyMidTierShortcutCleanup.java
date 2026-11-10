package se.yrell.developertools.midtier;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jface.bindings.Binding;
import org.eclipse.jface.bindings.Scheme;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.keys.IBindingService;
import org.osgi.service.prefs.Preferences;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/**
 * One-time best-effort cleanup of a keyboard binding persisted by 0.1.59/0.1.60.
 * This class deliberately never installs, validates, or dispatches a shortcut.
 * All non-Yrell USER bindings and all SYSTEM bindings are preserved.
 */
public final class LegacyMidTierShortcutCleanup {
    private static final String PREF_KEY = "midTierCache.quickShortcut";
    private static final String LEGACY_COMMAND = "se.yrell.developertools.commands.midTierFlushObject";

    private LegacyMidTierShortcutCleanup() { }

    public static void schedule() {
        // Clear the no-longer-supported preference even when the workbench
        // binding service is unavailable (e.g., the reported BMC NPE).
        try {
            Preferences node = ToolsPreferences.node();
            if (node.get(PREF_KEY, null) != null) {
                node.remove(PREF_KEY);
                node.flush();
            }
        } catch (Exception e) {
            Log.warn("Could not clear old MidTier shortcut preference: " + e.getClass().getSimpleName());
        }
        try {
            if (!PlatformUI.isWorkbenchRunning()) return;
            Display display = PlatformUI.getWorkbench().getDisplay();
            if (display == null || display.isDisposed()) return;
            display.asyncExec(LegacyMidTierShortcutCleanup::removeOldUserBinding);
        } catch (Exception e) {
            Log.warn("Could not schedule legacy shortcut cleanup: " + e.getClass().getSimpleName());
        }
    }

    private static void removeOldUserBinding() {
        try {
            IBindingService service = PlatformUI.getWorkbench().getService(IBindingService.class);
            if (service == null) return;
            Binding[] current = service.getBindings();
            if (current == null) return;
            List<Binding> keep = new ArrayList<>(current.length);
            int removed = 0;
            for (Binding binding : current) {
                if (isOurOldUserBinding(binding)) removed++;
                else if (binding != null) keep.add(binding);
            }
            if (removed == 0) return;
            Scheme active = service.getActiveScheme();
            if (active == null) {
                Log.warn("Legacy MidTier key binding found but Eclipse has no active key scheme; cannot remove it automatically.");
                return;
            }
            service.savePreferences(active, keep.toArray(new Binding[0]));
            Log.info("Removed " + removed + " obsolete Yrell MidTier key binding(s) from Eclipse.");
        } catch (Exception e) {
            // Never interfere with Developer Studio startup or Preferences.
            Log.warn("Could not remove legacy MidTier key binding: " + e.getClass().getSimpleName());
        }
    }

    static boolean isOurOldUserBinding(Binding binding) {
        return binding != null
                && binding.getType() == Binding.USER
                && binding.getParameterizedCommand() != null
                && LEGACY_COMMAND.equals(binding.getParameterizedCommand().getId());
    }
}
