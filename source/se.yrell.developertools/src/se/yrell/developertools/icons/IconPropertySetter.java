package se.yrell.developertools.icons;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.views.properties.IPropertyDescriptor;
import org.eclipse.ui.views.properties.IPropertySource;

import se.yrell.developertools.Log;

public final class IconPropertySetter {
    private IconPropertySetter() {
    }

    public static boolean setIconValue(String value, Item item) {
        return setIconValue(value, item, null);
    }

    public static boolean setIconValue(String value, Item item, Control valueControl) {
        boolean updatedModel = setViaCurrentSelection(value);
        updateVisualValue(value, item);
        updateVisualValue(value, valueControl);
        copyToClipboard(value);
        return updatedModel;
    }

    private static void updateVisualValue(String value, Item item) {
        if (item == null || item.isDisposed()) {
            return;
        }
        try {
            if (item instanceof TreeItem) {
                TreeItem treeItem = (TreeItem) item;
                int columnCount = treeItem.getParent().getColumnCount();
                if (columnCount > 1) {
                    treeItem.setText(1, value);
                } else {
                    treeItem.setText(value);
                }
            } else if (item instanceof TableItem) {
                TableItem tableItem = (TableItem) item;
                int columnCount = tableItem.getParent().getColumnCount();
                if (columnCount > 1) {
                    tableItem.setText(1, value);
                } else {
                    tableItem.setText(value);
                }
            }
        } catch (Throwable ignored) {
            // Visual update is best effort.
        }
    }


    private static void updateVisualValue(String value, Control control) {
        if (control == null || control.isDisposed()) {
            return;
        }
        try {
            if (control instanceof Text) {
                ((Text) control).setText(value);
            } else if (control instanceof Combo) {
                ((Combo) control).setText(value);
            } else if (control instanceof CCombo) {
                ((CCombo) control).setText(value);
            } else if (control instanceof StyledText) {
                ((StyledText) control).setText(value);
            } else {
                try {
                    java.lang.reflect.Method method = control.getClass().getMethod("setText", String.class);
                    method.invoke(control, value);
                } catch (Throwable ignored) {
                    // Best effort only.
                }
            }
        } catch (Throwable ignored) {
            // Visual update is best effort.
        }
    }

    private static boolean setViaCurrentSelection(String value) {
        try {
            IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
            if (window == null) {
                return false;
            }
            ISelection selection = window.getSelectionService().getSelection();
            if (!(selection instanceof IStructuredSelection)) {
                IWorkbenchPage page = window.getActivePage();
                if (page != null) {
                    selection = page.getSelection();
                }
            }
            if (!(selection instanceof IStructuredSelection)) {
                return false;
            }
            IStructuredSelection structured = (IStructuredSelection) selection;
            boolean changed = false;
            for (Object element : structured.toArray()) {
                IPropertySource source = propertySourceFor(element);
                if (source == null) {
                    continue;
                }
                Object id = findIconPropertyId(source);
                if (id != null) {
                    source.setPropertyValue(id, value);
                    changed = true;
                }
            }
            return changed;
        } catch (Throwable t) {
            Log.warn("Could not set PWA Icon property through Eclipse property source: " + t.getMessage());
            return false;
        }
    }

    private static IPropertySource propertySourceFor(Object element) {
        if (element == null) {
            return null;
        }
        if (element instanceof IPropertySource) {
            return (IPropertySource) element;
        }
        if (element instanceof IAdaptable) {
            Object adapted = ((IAdaptable) element).getAdapter(IPropertySource.class);
            if (adapted instanceof IPropertySource) {
                return (IPropertySource) adapted;
            }
        }
        Object adapted = Platform.getAdapterManager().getAdapter(element, IPropertySource.class);
        if (adapted instanceof IPropertySource) {
            return (IPropertySource) adapted;
        }
        return null;
    }

    private static Object findIconPropertyId(IPropertySource source) {
        try {
            IPropertyDescriptor[] descriptors = source.getPropertyDescriptors();
            if (descriptors == null) {
                return null;
            }
            for (IPropertyDescriptor descriptor : descriptors) {
                if (descriptor == null) {
                    continue;
                }
                String displayName = descriptor.getDisplayName();
                if ("Icon".equals(displayName)) {
                    return descriptor.getId();
                }
            }
            for (IPropertyDescriptor descriptor : descriptors) {
                if (descriptor == null) {
                    continue;
                }
                Object id = descriptor.getId();
                if (id != null && "icon".equalsIgnoreCase(String.valueOf(id))) {
                    return id;
                }
            }
        } catch (Throwable t) {
            Log.warn("Could not inspect property descriptors for PWA Icon: " + t.getMessage());
        }
        return null;
    }

    private static void copyToClipboard(String value) {
        Display display = Display.getCurrent();
        if (display == null || display.isDisposed()) {
            return;
        }
        Clipboard clipboard = null;
        try {
            clipboard = new Clipboard(display);
            clipboard.setContents(new Object[] { value }, new Transfer[] { TextTransfer.getInstance() });
        } catch (Throwable ignored) {
            // Ignore clipboard failures; setting the property is the primary action.
        } finally {
            if (clipboard != null) {
                clipboard.dispose();
            }
        }
    }
}
