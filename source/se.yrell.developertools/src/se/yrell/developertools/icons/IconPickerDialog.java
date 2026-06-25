package se.yrell.developertools.icons;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import se.yrell.developertools.Log;

public class IconPickerDialog extends TitleAreaDialog {
    private final List<IconEntry> allIcons;
    private Text searchText;
    private Table table;
    private Browser browser;
    private BrowserFunction selectFunction;
    private Label selectedLabel;
    private IconEntry selectedIcon;
    private boolean clearSelected;
    private final String currentValue;
    private final boolean browserPreviewMode;
    private File lastBrowserHtmlFile;
    private Button previousPageButton;
    private Button nextPageButton;
    private Label pageLabel;
    private int pageIndex = 0;
    private String lastFilter = null;
    private static final int PAGE_SIZE = 200;

    public IconPickerDialog(Shell parentShell, String currentValue) {
        super(parentShell);
        this.currentValue = currentValue == null ? "" : currentValue.trim();
        this.allIcons = IconCatalog.getIcons();
        this.browserPreviewMode = IconCatalog.isCssCatalogActive();
    }

    @Override
    protected Control createDialogArea(Composite parent) {
        setTitle("Choose PWA Icon");
        setMessage(IconCatalog.getActiveStatus());

        Composite area = (Composite) super.createDialogArea(parent);
        Composite container = new Composite(area, SWT.NONE);
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
        container.setLayout(new GridLayout(2, false));

        Label searchLabel = new Label(container, SWT.NONE);
        searchLabel.setText("Search:");

        searchText = new Text(container, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.CANCEL);
        searchText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        searchText.addModifyListener(e -> { pageIndex = 0; refreshContent(); });
        searchText.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (!browserPreviewMode && e.keyCode == SWT.ARROW_DOWN && table != null && table.getItemCount() > 0) {
                    table.setFocus();
                    table.setSelection(0);
                    updateSelectionFromTable();
                }
            }
        });

        if (browserPreviewMode) {
            createBrowserContent(container);
        } else {
            createTableContent(container);
        }

        createPagingControls(container);

        selectedLabel = new Label(container, SWT.NONE);
        selectedLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        updateSelectedLabel();

        refreshContent();
        if (currentValue.length() > 0) {
            searchText.setText(currentValue);
            refreshContent();
        }
        return area;
    }

    private void createBrowserContent(Composite container) {
        try {
            browser = createBestBrowser(container);
            GridData browserData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
            browserData.widthHint = 780;
            browserData.heightHint = 520;
            browser.setLayoutData(browserData);
            selectFunction = new BrowserFunction(browser, "yrellSelectIcon") {
                @Override
                public Object function(Object[] arguments) {
                    if (arguments == null || arguments.length == 0 || arguments[0] == null) {
                        return null;
                    }
                    String name = String.valueOf(arguments[0]);
                    boolean commit = arguments.length > 1 && Boolean.TRUE.equals(arguments[1]);
                    clearSelected = false;
                    selectedIcon = findByName(name);
                    updateSelectedLabel();
                    if (commit && selectedIcon != null && getShell() != null && !getShell().isDisposed()) {
                        getShell().getDisplay().asyncExec(new Runnable() {
                            @Override
                            public void run() {
                                if (getShell() != null && !getShell().isDisposed()) {
                                    okPressed();
                                }
                            }
                        });
                    }
                    return null;
                }
            };
        } catch (Throwable t) {
            Log.warn("SWT Browser could not be created for CSS icon preview. Falling back to table mode: " + t.getMessage());
            browser = null;
            browserPreviewFallback(container);
        }
    }

    private Browser createBestBrowser(Composite container) {
        try {
            Browser edge = new Browser(container, SWT.BORDER | SWT.EDGE);
            Log.info("PWA Icon Picker created SWT Browser with SWT.EDGE.");
            return edge;
        } catch (Throwable edgeFailure) {
            Log.warn("Could not create SWT Browser with SWT.EDGE; falling back to default browser engine: " + edgeFailure.getMessage());
            return new Browser(container, SWT.BORDER);
        }
    }

    private void browserPreviewFallback(Composite container) {
        table = new Table(container, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        tableData.widthHint = 720;
        tableData.heightHint = 500;
        table.setLayoutData(tableData);

        TableColumn glyphColumn = new TableColumn(table, SWT.LEFT);
        glyphColumn.setText("Glyph");
        glyphColumn.setWidth(80);

        TableColumn nameColumn = new TableColumn(table, SWT.LEFT);
        nameColumn.setText("Class");
        nameColumn.setWidth(420);

        table.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                updateSelectionFromTable();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent e) {
                updateSelectionFromTable();
                if (selectedIcon != null) {
                    okPressed();
                }
            }
        });
    }

    private void createTableContent(Composite container) {
        table = new Table(container, SWT.BORDER | SWT.FULL_SELECTION | SWT.SINGLE | SWT.V_SCROLL | SWT.H_SCROLL);
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        GridData tableData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
        tableData.widthHint = 720;
        tableData.heightHint = 500;
        table.setLayoutData(tableData);

        TableColumn glyphColumn = new TableColumn(table, SWT.LEFT);
        glyphColumn.setText("Glyph");
        glyphColumn.setWidth(90);

        TableColumn nameColumn = new TableColumn(table, SWT.LEFT);
        nameColumn.setText("Class");
        nameColumn.setWidth(460);

        table.addSelectionListener(new org.eclipse.swt.events.SelectionAdapter() {
            @Override
            public void widgetSelected(org.eclipse.swt.events.SelectionEvent e) {
                updateSelectionFromTable();
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent e) {
                updateSelectionFromTable();
                if (selectedIcon != null) {
                    okPressed();
                }
            }
        });
    }

    private void createPagingControls(Composite container) {
        Composite paging = new Composite(container, SWT.NONE);
        paging.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
        paging.setLayout(new GridLayout(3, false));

        previousPageButton = new Button(paging, SWT.PUSH);
        previousPageButton.setText("Previous");
        previousPageButton.addListener(SWT.Selection, e -> {
            if (pageIndex > 0) {
                pageIndex--;
                refreshContent();
            }
        });

        pageLabel = new Label(paging, SWT.NONE);
        pageLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        pageLabel.setText("Page 1");

        nextPageButton = new Button(paging, SWT.PUSH);
        nextPageButton.setText("Next");
        nextPageButton.addListener(SWT.Selection, e -> {
            pageIndex++;
            refreshContent();
        });
    }

    @Override
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Yrell Developer Tools - PWA Icon Picker");
    }

    @Override
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.NO_ID, "Clear", false);
        super.createButtonsForButtonBar(parent);
        updateOkButton();
    }

    @Override
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.NO_ID) {
            clearSelected = true;
            selectedIcon = null;
            okPressed();
            return;
        }
        super.buttonPressed(buttonId);
    }

    private void refreshContent() {
        if (browser != null && !browser.isDisposed()) {
            refreshBrowser();
        } else {
            refreshTable();
        }
        updateSelectedLabel();
        updateOkButton();
    }

    private void refreshBrowser() {
        String filter = currentFilter();
        List<IconEntry> allMatches = filtered(filter);
        normalizePageIndex(allMatches.size());
        List<IconEntry> page = page(allMatches);
        if (!clearSelected && selectedIcon == null && currentValue.length() > 0) {
            selectedIcon = findByName(currentValue);
        }
        if (!clearSelected && selectedIcon == null && !page.isEmpty()) {
            selectedIcon = page.get(0);
        }
        updatePageControls(allMatches.size());
        String html = buildHtml(page, allMatches.size());
        try {
            File file = File.createTempFile("yrell-pwa-icons-", ".html");
            Files.write(file.toPath(), html.getBytes(StandardCharsets.UTF_8));
            file.deleteOnExit();
            lastBrowserHtmlFile = file;
            browser.setUrl(file.toURI().toString());
        } catch (Throwable t) {
            Log.warn("Could not write PWA icon preview to a temporary HTML file. Falling back to Browser.setText: " + t.getMessage());
            browser.setText(html);
        }
    }

    private void refreshTable() {
        if (table == null || table.isDisposed()) {
            return;
        }
        String filter = currentFilter();
        List<IconEntry> allMatches = filtered(filter);
        normalizePageIndex(allMatches.size());
        List<IconEntry> page = page(allMatches);
        updatePageControls(allMatches.size());

        table.setRedraw(false);
        try {
            table.removeAll();
            for (IconEntry icon : page) {
                TableItem item = new TableItem(table, SWT.NONE);
                item.setText(0, icon.hasCodePoint() ? "\\" + icon.getCodePoint() : "");
                item.setText(1, icon.getName());
                item.setData(icon);
                if (!clearSelected && icon.getName().equals(currentValue)) {
                    table.setSelection(item);
                    selectedIcon = icon;
                }
            }
            if (!clearSelected && table.getSelectionCount() == 0 && table.getItemCount() > 0) {
                table.setSelection(0);
                updateSelectionFromTable();
            }
        } finally {
            table.setRedraw(true);
        }
    }

    private String currentFilter() {
        String filter = searchText == null ? "" : searchText.getText().trim().toLowerCase(Locale.ROOT);
        if (lastFilter == null || !lastFilter.equals(filter)) {
            pageIndex = 0;
            lastFilter = filter;
        }
        return filter;
    }

    private void normalizePageIndex(int matchCount) {
        int pageCount = Math.max(1, (matchCount + PAGE_SIZE - 1) / PAGE_SIZE);
        if (pageIndex < 0) {
            pageIndex = 0;
        }
        if (pageIndex >= pageCount) {
            pageIndex = pageCount - 1;
        }
    }

    private List<IconEntry> page(List<IconEntry> matches) {
        int from = Math.max(0, Math.min(pageIndex * PAGE_SIZE, matches.size()));
        int to = Math.max(from, Math.min(from + PAGE_SIZE, matches.size()));
        return new ArrayList<IconEntry>(matches.subList(from, to));
    }

    private void updatePageControls(int matchCount) {
        int pageCount = Math.max(1, (matchCount + PAGE_SIZE - 1) / PAGE_SIZE);
        if (previousPageButton != null && !previousPageButton.isDisposed()) {
            previousPageButton.setEnabled(pageIndex > 0);
        }
        if (nextPageButton != null && !nextPageButton.isDisposed()) {
            nextPageButton.setEnabled(pageIndex + 1 < pageCount);
        }
        if (pageLabel != null && !pageLabel.isDisposed()) {
            int from = matchCount == 0 ? 0 : pageIndex * PAGE_SIZE + 1;
            int to = Math.min((pageIndex + 1) * PAGE_SIZE, matchCount);
            pageLabel.setText("Page " + (pageIndex + 1) + " of " + pageCount + "  (" + from + "-" + to + " of " + matchCount + ")");
        }
    }

    private List<IconEntry> filtered(String filter) {
        List<IconEntry> filtered = new ArrayList<IconEntry>();
        for (IconEntry icon : allIcons) {
            if (matches(icon, filter)) {
                filtered.add(icon);
            }
        }
        return filtered;
    }

    private String buildHtml(List<IconEntry> icons, int matchCount) {
        String cssBase = IconCatalog.getActiveCssBaseUrl();
        String cssText = IconCatalog.getActiveBrowserCss();
        StringBuilder html = new StringBuilder(32768 + cssText.length());
        html.append("<!doctype html><html><head><meta charset=\"UTF-8\">\n");
        if (cssBase.length() > 0) {
            html.append("<base href=\"").append(escapeHtml(cssBase)).append("\">\n");
        }
        if (cssText.length() > 0) {
            html.append("<style>\n").append(safeCss(cssText)).append("\n</style>\n");
        }
        html.append("<style>\n")
            .append("body{font-family:Segoe UI,Arial,sans-serif;margin:0;padding:10px;background:#fff;color:#222;}\n")
            .append(".hint{font-size:12px;color:#666;margin:0 0 8px 0;}\n")
            .append(".grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:6px;}\n")
            .append(".card{display:flex;align-items:center;gap:8px;border:1px solid #d0d0d0;border-radius:4px;padding:7px;cursor:pointer;background:#fafafa;white-space:nowrap;overflow:hidden;}\n")
            .append(".card:hover{background:#eef5ff;border-color:#7eb4ea;}\n")
            .append(".selected{background:#dbeeff;border-color:#3d88d7;}\n")
            .append(".ico{font-size:24px;width:30px;text-align:center;color:#222;line-height:1;}\n")
            .append(".name{font-size:12px;overflow:hidden;text-overflow:ellipsis;}\n")
            .append(".empty{font-size:13px;color:#666;margin:20px;}\n")
            .append("</style></head><body>\n");
        html.append("<div class=\"hint\">").append(escapeHtml(IconCatalog.getActiveStatus())).append("<br>Click an icon to choose it. Showing ").append(icons.size()).append(" on this page, ").append(matchCount).append(" match(es), ").append(allIcons.size()).append(" total icons.</div>\n");
        if (icons.isEmpty()) {
            html.append("<div class=\"empty\">No icons match the current search.</div>");
        } else {
            html.append("<div class=\"grid\">\n");
            String selectedName = selectedIcon == null ? currentValue : selectedIcon.getName();
            for (IconEntry icon : icons) {
                String name = icon.getName();
                String selected = name.equals(selectedName) ? " selected" : "";
                html.append("<div class=\"card").append(selected).append("\" title=\"").append(escapeHtml(name)).append("\" ")
                    .append("onclick=\"window.yrellSelectIcon && window.yrellSelectIcon('").append(escapeJs(name)).append("', true);\">\n")
                    .append("<span class=\"ico ").append(escapeHtml(name)).append("\"></span>")
                    .append("<span class=\"name\">").append(escapeHtml(name)).append("</span>")
                    .append("</div>\n");
            }
            html.append("</div>\n");
        }
        html.append("</body></html>");
        return html.toString();
    }

    private boolean matches(IconEntry icon, String filter) {
        if (filter == null || filter.length() == 0) {
            return true;
        }
        String name = icon.getName().toLowerCase(Locale.ROOT);
        String compact = filter.replace(" ", "_").replace('-', '_');
        return name.contains(filter) || name.replace('-', '_').contains(compact);
    }

    private void updateSelectionFromTable() {
        clearSelected = false;
        selectedIcon = null;
        if (table == null || table.isDisposed() || table.getSelectionCount() == 0) {
            updateSelectedLabel();
            updateOkButton();
            return;
        }
        Object data = table.getSelection()[0].getData();
        if (data instanceof IconEntry) {
            selectedIcon = (IconEntry) data;
        }
        updateSelectedLabel();
        updateOkButton();
    }

    private IconEntry findByName(String name) {
        if (name == null) {
            return null;
        }
        for (IconEntry icon : allIcons) {
            if (name.equals(icon.getName())) {
                return icon;
            }
        }
        return null;
    }

    private void updateSelectedLabel() {
        if (selectedLabel == null || selectedLabel.isDisposed()) {
            return;
        }
        selectedLabel.setText(clearSelected ? "Selected: <clear Icon>" : (selectedIcon == null ? "Selected: " : "Selected: " + selectedIcon.getName()));
    }

    private void updateOkButton() {
        if (getButton(IDialogConstants.OK_ID) != null && !getButton(IDialogConstants.OK_ID).isDisposed()) {
            getButton(IDialogConstants.OK_ID).setEnabled(clearSelected || selectedIcon != null);
        }
    }

    public String getSelectedIconName() {
        return clearSelected ? "" : (selectedIcon == null ? null : selectedIcon.getName());
    }

    @Override
    public boolean close() {
        if (selectFunction != null) {
            try {
                selectFunction.dispose();
            } catch (Throwable ignored) {
                // Ignore.
            }
            selectFunction = null;
        }
        if (lastBrowserHtmlFile != null) {
            try {
                lastBrowserHtmlFile.delete();
            } catch (Throwable ignored) {
                // Ignore.
            }
            lastBrowserHtmlFile = null;
        }
        return super.close();
    }


    private static String safeCss(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("</style", "<\\/style");
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String escapeJs(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "").replace("\n", "");
    }
}
