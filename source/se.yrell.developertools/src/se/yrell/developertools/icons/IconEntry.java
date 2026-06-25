package se.yrell.developertools.icons;

public final class IconEntry {
    private final String name;
    private final String resourcePath;
    private final int page;
    private final int row;
    private final int column;
    private final String codePoint;

    public IconEntry(String name, String resourcePath, int page, int row, int column) {
        this(name, resourcePath, page, row, column, null);
    }

    public IconEntry(String name, String resourcePath, int page, int row, int column, String codePoint) {
        this.name = name;
        this.resourcePath = resourcePath == null ? "" : resourcePath;
        this.page = page;
        this.row = row;
        this.column = column;
        this.codePoint = codePoint == null ? "" : codePoint;
    }

    public String getName() {
        return name;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    public int getPage() {
        return page;
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public String getCodePoint() {
        return codePoint;
    }

    public boolean hasCodePoint() {
        return codePoint != null && codePoint.length() > 0;
    }
}
