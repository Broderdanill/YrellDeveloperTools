package se.yrell.developertools.inspector;

import java.util.List;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
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

    @Override
    public void createPartControl(Composite parent) {
        Composite root = new Composite(parent, SWT.NONE);
        root.setLayout(new GridLayout(1, false));

        heading = new Label(root, SWT.WRAP);
        heading.setText("Select a Developer Studio object to show permissions or table qualification.");
        heading.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

        table = new Table(root, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

        createColumn("Category", 140);
        createColumn("Attribute", 190);
        createColumn("Value", 520);

        try {
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
        List<InsightRow> rows = collector.collect(selected);
        table.setRedraw(false);
        try {
            table.removeAll();
            for (int i = 0; i < rows.size(); i++) {
                InsightRow row = rows.get(i);
                TableItem item = new TableItem(table, SWT.NONE);
                item.setText(new String[] { row.category, row.attribute, row.value });
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
}
