package se.yrell.developertools.inspector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.part.ViewPart;

import se.yrell.developertools.Log;

/**
 * Shows high-value details for the selected Developer Studio object without having
 * to open every ellipsis dialog in the Properties view.
 */
public class ObjectInsightView extends ViewPart implements ISelectionListener {
    public static final String ID = ObjectInsightViewSupport.VIEW_ID;

    private Label heading;
    private Table table;
    private final ObjectInsightCollector collector = new ObjectInsightCollector();
    private final LastExternalSelectionProvider selectionProvider = new LastExternalSelectionProvider();
    private final AtomicLong selectionVersion = new AtomicLong();
    private volatile Job currentJob;
    private volatile boolean closed;

    @Override
    public void createPartControl(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(1, false));

        heading = new Label(root, SWT.WRAP);
        heading.setText("Select a Developer Studio object to show permissions, table qualification or table sort.");
        heading.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        table = new Table(root, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createColumn("Category", 120);
        createColumn("Name", 260);
        createColumn("ID", 90);
        createColumn("Value", 560);

        try {
            getSite().setSelectionProvider(selectionProvider);
            getSite().getWorkbenchWindow().getSelectionService().addPostSelectionListener(this);
            ISelection selection = getSite().getWorkbenchWindow().getSelectionService().getSelection();
            render(selection);
        } catch (Throwable t) {
            Log.warn("Could not attach Object Insight selection listener: " + t.getMessage());
        }
    }

    private void createColumn(String text, int width) {
        TableColumn column = new TableColumn(table, SWT.LEFT);
        column.setText(text);
        column.setWidth(width);
    }

    @Override
    public void dispose() {
        closed = true;
        selectionVersion.incrementAndGet();
        Job previous = currentJob;
        if (previous != null) previous.cancel();
        try {
            getSite().getWorkbenchWindow().getSelectionService().removePostSelectionListener(this);
        } catch (Throwable ignored) {
        }
        super.dispose();
    }

    @Override
    public void setFocus() {
        if (table != null && !table.isDisposed()) {
            table.setFocus();
        }
    }

    @Override
    public void selectionChanged(IWorkbenchPart part, ISelection selection) {
        if (part == this) {
            return;
        }
        render(selection);
    }

    private void render(ISelection selection) {
        if (table == null || table.isDisposed()) {
            return;
        }
        Object selected = selectedObject(selection);
        if (selection != null && selected != null) {
            selectionProvider.setSelection(selection);
        }
        final long version = selectionVersion.incrementAndGet();
        Job previous = currentJob;
        if (previous != null) previous.cancel();
        if (selected == null) {
            showRows(null, java.util.Collections.singletonList(
                    new InsightRow("Info", "Status", "", "No selected Developer Studio object.")));
            return;
        }
        heading.setText("Loading Object Insight...");
        final org.eclipse.swt.widgets.Display uiDisplay = table.getDisplay();
        Job job = new Job("Yrell Object Insight") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                if (monitor.isCanceled() || closed || selectionVersion.get() != version) return Status.CANCEL_STATUS;
                final List<InsightRow> rows;
                try {
                    rows = collector.collect(selected);
                } catch (Throwable t) {
                    Log.warn("Object Insight collection failed: " + t.getMessage());
                    if (monitor.isCanceled()) return Status.CANCEL_STATUS;
                    uiDisplay.asyncExec(() -> {
                        if (!closed && selectionVersion.get() == version) {
                            showRows(selected, java.util.Collections.singletonList(
                                    new InsightRow("Info", "Error", "", "Could not collect object metadata.")));
                        }
                    });
                    return Status.OK_STATUS;
                }
                if (monitor.isCanceled() || closed || selectionVersion.get() != version) return Status.CANCEL_STATUS;
                if (!uiDisplay.isDisposed()) {
                    uiDisplay.asyncExec(() -> {
                        if (!closed && selectionVersion.get() == version) showRows(selected, rows);
                    });
                }
                return Status.OK_STATUS;
            }
        };
        job.setSystem(true);
        currentJob = job;
        job.schedule(120L);
    }

    private void showRows(Object selected, List<InsightRow> rows) {
        if (table == null || table.isDisposed()) return;
        table.setRedraw(false);
        try {
            table.removeAll();
            for (int i = 0; i < rows.size(); i++) {
                InsightRow row = rows.get(i);
                TableItem item = new TableItem(table, SWT.NONE);
                item.setText(new String[] { row.category, row.name, row.id, row.value });
            }
            if (heading != null && !heading.isDisposed()) {
                heading.setText(selected == null
                        ? "No object selected."
                        : "Object Insight");
            }
        } finally {
            table.setRedraw(true);
        }
    }

    private Object selectedObject(ISelection selection) {
        if (selection instanceof IStructuredSelection) {
            IStructuredSelection structured = (IStructuredSelection) selection;
            if (!structured.isEmpty()) {
                return structured.getFirstElement();
            }
        }
        return null;
    }

    /**
     * Keeps the Properties view on the real Developer Studio selection even when
     * focus moves into Object Insight. Without this, Eclipse can treat the Object
     * Insight table row as the active selection and clear the normal Properties view.
     */
    private static final class LastExternalSelectionProvider implements ISelectionProvider {
        private final List<ISelectionChangedListener> listeners = new ArrayList<ISelectionChangedListener>();
        private ISelection selection = StructuredSelection.EMPTY;

        @Override
        public void addSelectionChangedListener(ISelectionChangedListener listener) {
            if (listener != null && !listeners.contains(listener)) {
                listeners.add(listener);
            }
        }

        @Override
        public ISelection getSelection() {
            return selection;
        }

        @Override
        public void removeSelectionChangedListener(ISelectionChangedListener listener) {
            listeners.remove(listener);
        }

        @Override
        public void setSelection(ISelection selection) {
            if (selection == null) {
                return;
            }
            this.selection = selection;
            SelectionChangedEvent event = new SelectionChangedEvent(this, selection);
            for (ISelectionChangedListener listener : new ArrayList<ISelectionChangedListener>(listeners)) {
                try {
                    listener.selectionChanged(event);
                } catch (Throwable ignored) {
                    // Never let the helper view break Developer Studio selection handling.
                }
            }
        }
    }
}
