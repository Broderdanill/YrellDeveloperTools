package se.yrell.developertools.inspector;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import se.yrell.developertools.Log;
import se.yrell.developertools.ToolsPreferences;

/** Opens/hides the optional Object Insight view. */
public final class ObjectInsightViewSupport {
    public static final String VIEW_ID = "se.yrell.developertools.views.objectInsight";

    private ObjectInsightViewSupport() {
    }

    public static void openIfEnabledAsync() {
        if (!ToolsPreferences.isObjectInsightEnabled()) {
            return;
        }
        openAsync();
    }

    public static void openAsync() {
        try {
            Display display = PlatformUI.getWorkbench().getDisplay();
            if (display == null || display.isDisposed()) {
                return;
            }
            display.asyncExec(new Runnable() {
                @Override
                public void run() {
                    try {
                        IWorkbenchPage page = activePage();
                        if (page != null) {
                            page.showView(VIEW_ID);
                        }
                    } catch (Throwable t) {
                        Log.warn("Could not open Object Insight view: " + t.getMessage());
                    }
                }
            });
        } catch (Throwable t) {
            Log.warn("Could not schedule Object Insight view open: " + t.getMessage());
        }
    }

    public static void hideAsync() {
        try {
            Display display = PlatformUI.getWorkbench().getDisplay();
            if (display == null || display.isDisposed()) {
                return;
            }
            display.asyncExec(new Runnable() {
                @Override
                public void run() {
                    try {
                        IWorkbenchPage page = activePage();
                        if (page != null) {
                            IViewPart view = page.findView(VIEW_ID);
                            if (view != null) {
                                page.hideView(view);
                            }
                        }
                    } catch (Throwable t) {
                        Log.warn("Could not hide Object Insight view: " + t.getMessage());
                    }
                }
            });
        } catch (Throwable t) {
            Log.warn("Could not schedule Object Insight view hide: " + t.getMessage());
        }
    }

    private static IWorkbenchPage activePage() {
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if (window == null) {
            IWorkbenchWindow[] windows = PlatformUI.getWorkbench().getWorkbenchWindows();
            if (windows != null && windows.length > 0) {
                window = windows[0];
            }
        }
        return window == null ? null : window.getActivePage();
    }
}
