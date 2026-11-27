package me.pepe.DatabaseAPI.Schema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;
import me.pepe.DatabaseAPI.DatabaseManager.Types.TableDatabaseMultiKeys;

public final class SchemaMigrationHistoryTable extends TableDatabaseMultiKeys {
    public static final String NAME = "DatabaseSchemaMigrationTable";
    private long id;
    private String component = "";
    private int schemaVersion;
    private String migrationID = "";
    private String checksum = "";
    private String description = "";
    private String tableName = "";
    private long appliedAt;
    private String backupFile = "";
    private String retiredNames = "";
    public SchemaMigrationHistoryTable(long id, Database database) {
        super(NAME, "ID", database);
        this.id = id;
    }
    @Override
    public void buildKey(Long key) {
        id = key;
    }
    @Override
    public Long keySerialize() {
        return id;
    }
    @Override
    protected void buildDatabase(ResultSet row) throws SQLException {
        id = row.getLong("ID");
        deserialize(row);
    }
    @Override
    public void deserialize(ResultSet row) throws SQLException {
        component = row.getString("component");
        schemaVersion = row.getInt("schemaVersion");
        migrationID = row.getString("migrationID");
        checksum = row.getString("checksum");
        description = row.getString("description");
        tableName = row.getString("tableName");
        appliedAt = row.getLong("appliedAt");
        backupFile = row.getString("backupFile");
        retiredNames = row.getString("retiredNames");
    }
    @Override
    public HashMap<String, Object> serialize(HashMap<String, Object> map) {
        map.put("component", component);
        map.put("schemaVersion", schemaVersion);
        map.put("migrationID", migrationID);
        map.put("checksum", checksum);
        map.put("description", description);
        map.put("tableName", tableName);
        map.put("appliedAt", appliedAt);
        map.put("backupFile", backupFile);
        map.put("retiredNames", retiredNames);
        return map;
    }
}
