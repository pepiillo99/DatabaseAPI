package me.pepe.DatabaseAPI.Schema;

public final class SchemaChange {
    public enum Type { ADD_COLUMN, RENAME_TABLE, DROP_TABLE, COPY_COLUMN }
    private final Type type;
    private final String table;
    private final String source;
    private final String target;
    private final String sqlType;
    private final Object defaultValue;
    private SchemaChange(Type type, String table, String source, String target, String sqlType, Object defaultValue) {
        this.type = type;
        this.table = table;
        this.source = source;
        this.target = target;
        this.sqlType = sqlType;
        this.defaultValue = defaultValue;
    }
    public static SchemaChange addColumn(String table, String column, String sqlType, Object defaultValue) {
        return new SchemaChange(Type.ADD_COLUMN, table, null, column, sqlType, defaultValue);
    }
    public static SchemaChange renameTable(String source, String target) {
        return new SchemaChange(Type.RENAME_TABLE, source, null, target, null, null);
    }
    public static SchemaChange dropTable(String table) {
        return new SchemaChange(Type.DROP_TABLE, table, null, null, null, null);
    }
    public static SchemaChange copyColumn(String table, String source, String target) {
        return new SchemaChange(Type.COPY_COLUMN, table, source, target, null, null);
    }
    public Type getType() {
        return type;
    }
    public String getTable() {
        return table;
    }
    public String getSource() {
        return source;
    }
    public String getTarget() {
        return target;
    }
    public String getSqlType() {
        return sqlType;
    }
    public Object getDefaultValue() {
        return defaultValue;
    }
}
