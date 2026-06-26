package se.yrell.developertools.inspector;

final class InsightRow {
    final String category;
    final String name;
    final String id;
    final String value;

    InsightRow(String category, String name, String id, String value) {
        this.category = category == null ? "" : category;
        this.name = name == null ? "" : name;
        this.id = id == null ? "" : id;
        this.value = value == null ? "" : value;
    }
}
