package se.yrell.developertools.inspector;

final class InsightRow {
    final String category;
    final String attribute;
    final String value;

    InsightRow(String category, String attribute, String value) {
        this.category = category == null ? "" : category;
        this.attribute = attribute == null ? "" : attribute;
        this.value = value == null ? "" : value;
    }
}
