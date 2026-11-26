package me.pepe.DatabaseAPI.Schema;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import me.pepe.DatabaseAPI.DatabaseAPI;
import me.pepe.DatabaseAPI.DatabaseManager.DatabaseTableInstance;
import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;
import me.pepe.DatabaseAPI.Utils.DatabaseConfiguration;

public class SchemaMigrationManager {
    private final Database database;
    private final DatabaseConfiguration configuration;
    private boolean historyReady;
    private boolean catalogReady;
    private volatile long migrationVersion;
    public SchemaMigrationManager(Database database) {
        this.database = database;
        configuration = DatabaseAPI.getInstance().getConfiguration();
    }
    public synchronized void initializeHistory(DatabaseAPI api) throws SQLException {
        if (historyReady) {
            return;
        }
        if (!database.isSQLite()) {
            throw new SQLException("La migración automática exige backup transaccional SQLite; MariaDB no se modifica sin una estrategia compatible.");
        }
        final DatabaseTableInstance<Long> factory = new DatabaseTableInstance<Long>() {
            @Override
            public DatabaseTable newInstance(Long key) {
                return new SchemaMigrationHistoryTable(key == null ? 0 : key, database);
            }
        };
        // La creación del historial también pertenece a DatabaseAPI.
        database.registerTable(api, SchemaMigrationHistoryTable.class, factory);
        if (factory.hasError()) {
            throw new SQLException("No se ha confirmado el registro del historial de versiones.");
        }
        historyReady = true;
    }
    public synchronized void beforeTable(Connection connection, DatabaseTable table) throws SQLException {
        requireVersion(connection);
        for (SchemaMigration migration : configuration.getSchemaMigrations()) {
            for (SchemaChange change : migration.getChanges()) {
                if (change.getType() == SchemaChange.Type.DROP_TABLE && change.getTable().equalsIgnoreCase(table.getTableName())) {
                    throw new SQLException("La tabla retirada no puede seguir registrada: " + table.getTableName());
                }
            }
        }
        if (!catalogReady) {
            for (SchemaMigration migration : configuration.getSchemaMigrations()) {
                apply(connection, "explicit." + migration.getId(), migration.getDescription(), "", changesChecksum(migration), migration.getChanges());
            }
            catalogReady = true;
        }
        if (!exists(connection, table.getTableName())) {
            return;
        }
        Map<String, String> actual = columns(connection, table.getTableName());
        Map<String, Object> values = table.serialize(new HashMap<String, Object>());
        String keyType = table.getKeyType().getStatementName(false);
        if (!actual.containsKey(table.getKeyName()) || !sameType(actual.get(table.getKeyName()), keyType)) {
            throw new SQLException("La clave de " + table.getTableName() + " requiere una migración explícita; no se inventan identificadores.");
        }
        List<SchemaChange> changes = new ArrayList<SchemaChange>();
        for (Map.Entry<String, Object> entry : new TreeMap<String, Object>(values).entrySet()) {
            String type = database.getStatementName(entry.getValue());
            if (!actual.containsKey(entry.getKey())) {
                changes.add(SchemaChange.addColumn(table.getTableName(), entry.getKey(), type, entry.getValue()));
            } else if (!sameType(actual.get(entry.getKey()), type)) {
                throw new SQLException("El cambio de tipo de " + table.getTableName() + "." + entry.getKey() + " necesita una conversión explícita que conserve el original.");
            }
        }
        String id = tableMigrationID(table);
        String checksum = tableChecksum(table);
        String previous = appliedChecksum(connection, id);
        if (previous != null && !changes.isEmpty()) {
            throw new SQLException("El historial indica una actualización aplicada, pero faltan columnas en " + table.getTableName() + ". Se requiere recuperación, no sobrescritura.");
        }
        apply(connection, id, "Esquema registrado de " + table.getTableName(), table.getTableName(), checksum, changes);
    }
    public synchronized void afterTable(Connection connection, DatabaseTable table) throws SQLException {
        if (!exists(connection, table.getTableName())) {
            throw new SQLException("DatabaseAPI no ha confirmado la creación de " + table.getTableName());
        }
        apply(connection, tableMigrationID(table), "Esquema registrado de " + table.getTableName(), table.getTableName(), tableChecksum(table), Collections.<SchemaChange>emptyList());
    }
    public long getMigrationVersion() {
        return migrationVersion;
    }
    private String tableMigrationID(DatabaseTable table) {
        return "schema." + table.getTableName() + ".v" + configuration.getSchemaVersion();
    }
    private String tableChecksum(DatabaseTable table) throws SQLException {
        StringBuilder text = new StringBuilder(table.getKeyName()).append(':').append(table.getKeyType().getStatementName(false)).append(':').append(table.hasPrimaryKey()).append(':').append(table.isAutoIncrement());
        for (Map.Entry<String, Object> entry : new TreeMap<String, Object>(table.serialize(new HashMap<String, Object>())).entrySet()) {
            text.append('|').append(entry.getKey()).append(':').append(database.getStatementName(entry.getValue()));
        }
        return digest(text.toString());
    }
    private String changesChecksum(SchemaMigration migration) throws SQLException {
        StringBuilder text = new StringBuilder(migration.getId()).append('|').append(migration.getDescription());
        for (SchemaChange change : migration.getChanges()) {
            if (change == null) {
                throw new SQLException("Una migración contiene un paso vacío.");
            }
            text.append('|').append(change.getType()).append(':').append(change.getTable()).append(':').append(change.getSource()).append(':').append(change.getTarget()).append(':').append(change.getSqlType());
            if (change.getType() == SchemaChange.Type.ADD_COLUMN) {
                text.append(':').append(literal(change.getDefaultValue()));
            }
        }
        return digest(text.toString());
    }
    private void requireVersion(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT MAX(schemaVersion),MAX(ID) FROM " + quote(SchemaMigrationHistoryTable.NAME) + " WHERE component=?")) {
            statement.setString(1, configuration.getSchemaComponent());
            try (ResultSet row = statement.executeQuery()) {
                if (row.next()) {
                    if (row.getInt(1) > configuration.getSchemaVersion()) {
                        throw new SQLException("La base usa una versión de esquema posterior. No se permite arrancar una aplicación anterior.");
                    }
                    migrationVersion = row.getLong(2);
                }
            }
        }
    }
    private String appliedChecksum(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT checksum FROM " + quote(SchemaMigrationHistoryTable.NAME) + " WHERE component=? AND migrationID=?")) {
            statement.setString(1, configuration.getSchemaComponent());
            statement.setString(2, id);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        }
    }
    private void apply(Connection connection, String id, String description, String tableName, String checksum, List<SchemaChange> changes) throws SQLException {
        String previous = appliedChecksum(connection, id);
        if (previous != null) {
            if (!previous.equals(checksum)) {
                throw new SQLException("La migración aplicada " + id + " ha cambiado. Incrementa la versión y declara otra migración; no se modifica su historial.");
            }
            return;
        }
        if (!connection.getAutoCommit()) {
            throw new SQLException("No se migra una base dentro de una operación económica en curso.");
        }
        Path backup = null;
        boolean transaction = false;
        boolean attached = false;
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("BEGIN");
            transaction = true;
            requireVersion(connection);
            // La primera lectura fija la instantánea antes de crear la copia.
            previous = appliedChecksum(connection, id);
            if (previous != null) {
                if (!previous.equals(checksum)) {
                    throw new SQLException("Conflicto concurrente de checksum en " + id);
                }
                statement.execute("ROLLBACK");
                transaction = false;
                return;
            }
            Map<String, Snapshot> snapshots = snapshots(connection);
            long lastID = scalar(connection, "SELECT COALESCE(MAX(ID),0) FROM " + quote(SchemaMigrationHistoryTable.NAME));
            long violations = foreignKeyViolations(connection);
            if (needsMutation(connection, changes)) {
                integrity(connection);
                backup = createBackup(connection, id);
                try (PreparedStatement attach = connection.prepareStatement("ATTACH DATABASE ? AS migration_backup")) {
                    attach.setString(1, backup.toUri().toString() + "?mode=ro");
                    attach.execute();
                }
                attached = true;
            }
            Set<String> added = new HashSet<String>();
            List<String> retired = new ArrayList<String>();
            for (SchemaChange change : changes) {
                executeChange(connection, change, snapshots, added, retired, backup);
            }
            long next = Math.addExact(lastID, 1);
            record(connection, next, id, checksum, description, tableName, backup);
            if (attached) {
                for (Snapshot snapshot : snapshots.values()) {
                    verifySnapshot(connection, snapshot, lastID);
                }
                verifyReferences(connection, retired);
                integrity(connection);
                if (violations != foreignKeyViolations(connection)) {
                    throw new SQLException("Las relaciones han cambiado; se revierte la migración.");
                }
            }
            if (!checksum.equals(appliedChecksum(connection, id))) {
                throw new SQLException("No se ha confirmado el historial de la migración.");
            }
            statement.execute("COMMIT");
            transaction = false;
            migrationVersion = next;
            System.out.println("[DatabaseAPI][schema] " + database.getDatabaseName() + " versión " + configuration.getSchemaVersion() + ", paso " + next + ": " + id + (backup == null ? " (sin cambios de datos/esquema)" : "; backup=" + backup));
        } catch (Exception failure) {
            if (transaction) {
                try (Statement rollback = connection.createStatement()) {
                    rollback.execute("ROLLBACK");
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            System.err.println("[DatabaseAPI][schema] ACTUALIZACIÓN NO CONFIRMADA: " + database.getDatabaseName() + "/" + id + (backup == null ? "" : "; copia conservada=" + backup));
            throw failure instanceof SQLException ? (SQLException) failure : new SQLException("No se completó la migración " + id, failure);
        } finally {
            if (attached) {
                try (Statement detach = connection.createStatement()) {
                    detach.execute("DETACH DATABASE migration_backup");
                }
            }
        }
    }
    private boolean needsMutation(Connection connection, List<SchemaChange> changes) throws SQLException {
        for (SchemaChange change : changes) {
            validateChange(change);
            if (exists(connection, change.getTable()) && (change.getType() != SchemaChange.Type.ADD_COLUMN || !columns(connection, change.getTable()).containsKey(change.getTarget()))) {
                return true;
            }
        }
        return false;
    }
    private void validateChange(SchemaChange change) throws SQLException {
        quote(change.getTable());
        if (SchemaMigrationHistoryTable.NAME.equalsIgnoreCase(change.getTable()) || change.getTable().toLowerCase(Locale.ROOT).startsWith("sqlite_")) {
            throw new SQLException("No se permite alterar el historial ni las tablas internas.");
        }
        if (change.getTarget() != null) {
            quote(change.getTarget());
            if (SchemaMigrationHistoryTable.NAME.equalsIgnoreCase(change.getTarget()) || change.getTarget().toLowerCase(Locale.ROOT).startsWith("sqlite_")) {
                throw new SQLException("Destino de migración reservado.");
            }
        }
        if (change.getType() == SchemaChange.Type.ADD_COLUMN) {
            sqlType(change.getSqlType());
            literal(change.getDefaultValue());
        }
    }
    private void executeChange(Connection connection, SchemaChange change, Map<String, Snapshot> snapshots, Set<String> added, List<String> retired, Path backup) throws SQLException {
        validateChange(change);
        if (!exists(connection, change.getTable())) {
            return;
        }
        String table = quote(change.getTable());
        try (Statement statement = connection.createStatement()) {
            if (change.getType() == SchemaChange.Type.ADD_COLUMN) {
                if (!columns(connection, change.getTable()).containsKey(change.getTarget())) {
                    requireBackup(backup);
                    statement.execute("ALTER TABLE " + table + " ADD COLUMN " + quote(change.getTarget()) + " " + sqlType(change.getSqlType()) + " NOT NULL DEFAULT " + literal(change.getDefaultValue()));
                    added.add(change.getTable() + "." + change.getTarget());
                } else if (!sameType(columns(connection, change.getTable()).get(change.getTarget()), sqlType(change.getSqlType()))) {
                    throw new SQLException("La columna existente no coincide con el tipo declarado en la migración.");
                }
            } else if (change.getType() == SchemaChange.Type.RENAME_TABLE) {
                requireBackup(backup);
                if (exists(connection, change.getTarget())) {
                    throw new SQLException("Existen origen y destino de " + change.getTable() + "; no se fusionan ni eliminan datos.");
                }
                statement.execute("ALTER TABLE " + table + " RENAME TO " + quote(change.getTarget()));
                for (Snapshot snapshot : snapshots.values()) {
                    if (snapshot.current.equalsIgnoreCase(change.getTable())) {
                        snapshot.current = change.getTarget();
                    }
                }
                retired.add(change.getTable());
            } else if (change.getType() == SchemaChange.Type.DROP_TABLE) {
                requireBackup(backup);
                System.out.println("[DatabaseAPI][schema] ELIMINACIÓN DE TABLA " + change.getTable() + "; datos conservados en " + backup);
                for (Snapshot snapshot : snapshots.values()) {
                    if (snapshot.current.equalsIgnoreCase(change.getTable())) {
                        verifySnapshot(connection, snapshot, Long.MAX_VALUE);
                        snapshot.dropped = true;
                    }
                }
                statement.execute("DROP TABLE " + table);
                retired.add(change.getTable());
            } else if (change.getType() == SchemaChange.Type.COPY_COLUMN) {
                requireBackup(backup);
                quote(change.getSource());
                if (!added.contains(change.getTable() + "." + change.getTarget()) || !columns(connection, change.getTable()).containsKey(change.getSource())) {
                    throw new SQLException("Solo se copian valores a columnas nuevas de esta migración; nunca se sobrescribe información existente.");
                }
                Map<String, String> types = columns(connection, change.getTable());
                if (!sameType(types.get(change.getSource()), types.get(change.getTarget()))) {
                    throw new SQLException("La copia entre tipos distintos necesita una conversión explícita; no se truncan ni reinterpretan datos.");
                }
                statement.executeUpdate("UPDATE " + table + " SET " + quote(change.getTarget()) + "=" + quote(change.getSource()));
            }
        }
    }
    protected Path createBackup(Connection connection, String migrationID) throws Exception {
        Path source = database.getSQLiteFile().toPath().toRealPath();
        Path directory = source.getParent().resolve("SchemaBackups");
        Files.createDirectories(directory);
        protect(directory, true);
        Path partial = directory.resolve(source.getFileName() + ".v" + configuration.getSchemaVersion() + "." + System.currentTimeMillis() + "." + UUID.randomUUID() + ".partial");
        Files.createFile(partial);
        protect(partial, false);
        try (Statement statement = connection.createStatement()) {
            String path = partial.toString();
            if (path.contains("\"") && path.contains("'")) {
                throw new SQLException("La ruta de copia contiene comillas incompatibles con el driver SQLite.");
            }
            statement.execute("backup to " + (path.contains("\"") ? "'" + path + "'" : "\"" + path + "\""));
        }
        try (Connection snapshot = DriverManager.getConnection("jdbc:sqlite:" + partial.toUri() + "?mode=ro")) {
            integrity(snapshot);
            if (!definitions(connection).equals(definitions(snapshot))) {
                throw new SQLException("La copia no contiene el mismo esquema; no se inicia la migración.");
            }
            for (Snapshot table : snapshots(connection).values()) {
                if (scalar(connection, "SELECT COUNT(*) FROM " + quote(table.original)) != scalar(snapshot, "SELECT COUNT(*) FROM " + quote(table.original))) {
                    throw new SQLException("La copia no confirma todas las filas de " + table.original);
                }
            }
        }
        Path backup = partial.resolveSibling(partial.getFileName().toString().replace(".partial", ".bak"));
        Files.move(partial, backup, StandardCopyOption.ATOMIC_MOVE);
        System.out.println("[DatabaseAPI][schema] BACKUP CONFIRMADO: " + backup);
        return backup;
    }
    private void protect(Path path, boolean directory) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
            Set<PosixFilePermission> permissions = new HashSet<PosixFilePermission>(Arrays.asList(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            if (directory) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
            }
            Files.setPosixFilePermissions(path, permissions);
        }
    }
    private void requireBackup(Path backup) throws SQLException {
        if (backup == null || !Files.isRegularFile(backup)) {
            throw new SQLException("No se permite cambiar ni eliminar datos sin una copia verificada.");
        }
    }
    private void record(Connection connection, long id, String migration, String checksum, String description, String tableName, Path backup) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO " + quote(SchemaMigrationHistoryTable.NAME) + " (ID,component,schemaVersion,migrationID,checksum,description,tableName,appliedAt,backupFile) VALUES (?,?,?,?,?,?,?,?,?)")) {
            statement.setLong(1, id);
            statement.setString(2, configuration.getSchemaComponent());
            statement.setInt(3, configuration.getSchemaVersion());
            statement.setString(4, migration);
            statement.setString(5, checksum);
            statement.setString(6, description);
            statement.setString(7, tableName);
            statement.setLong(8, System.currentTimeMillis());
            statement.setString(9, backup == null ? "" : backup.toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("No se ha confirmado la versión de la migración.");
            }
        }
    }
    private Map<String, Snapshot> snapshots(Connection connection) throws SQLException {
        Map<String, Snapshot> result = new LinkedHashMap<String, Snapshot>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT name FROM main.sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rows.next()) {
                String name = rows.getString(1);
                result.put(name, new Snapshot(name, new ArrayList<String>(columns(connection, name).keySet())));
            }
        }
        return result;
    }
    private Map<String, String> definitions(Connection connection) throws SQLException {
        Map<String, String> result = new TreeMap<String, String>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT type,name,sql FROM main.sqlite_master ORDER BY type,name")) {
            while (rows.next()) {
                result.put(rows.getString(1) + ":" + rows.getString(2), rows.getString(3));
            }
        }
        return result;
    }
    private void verifySnapshot(Connection connection, Snapshot snapshot, long lastID) throws SQLException {
        if (snapshot.dropped) {
            return;
        }
        StringBuilder projection = new StringBuilder();
        for (String column : snapshot.columns) {
            if (projection.length() > 0) {
                projection.append(',');
            }
            projection.append(quote(column));
        }
        String original = "migration_backup." + quote(snapshot.original);
        String current = "main." + quote(snapshot.current);
        String filter = snapshot.original.equals(SchemaMigrationHistoryTable.NAME) ? " WHERE ID<=" + lastID : "";
        String left = "SELECT " + projection + ",COUNT(*) FROM " + current + filter + " GROUP BY " + projection;
        String right = "SELECT " + projection + ",COUNT(*) FROM " + original + " GROUP BY " + projection;
        if (scalar(connection, "SELECT COUNT(*) FROM (" + left + " EXCEPT " + right + ")") != 0 || scalar(connection, "SELECT COUNT(*) FROM (" + right + " EXCEPT " + left + ")") != 0) {
            throw new SQLException("La conservación de filas y valores no está confirmada en " + snapshot.original);
        }
    }
    private void verifyReferences(Connection connection, List<String> retired) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT sql FROM main.sqlite_master WHERE sql IS NOT NULL")) {
            while (rows.next()) {
                for (String name : retired) {
                    if (Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])").matcher(rows.getString(1)).find()) {
                        throw new SQLException("Quedan referencias al nombre retirado " + name + "; se revierte para proteger vistas, triggers y relaciones.");
                    }
                }
            }
        }
    }
    private void integrity(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery("PRAGMA main.integrity_check")) {
            if (!row.next() || !"ok".equals(row.getString(1)) || row.next()) {
                throw new SQLException("La integridad SQLite no está confirmada.");
            }
        }
    }
    private long foreignKeyViolations(Connection connection) throws SQLException {
        long count = 0;
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("PRAGMA main.foreign_key_check")) {
            while (rows.next()) {
                count++;
            }
        }
        return count;
    }
    private Map<String, String> columns(Connection connection, String table) throws SQLException {
        Map<String, String> result = new LinkedHashMap<String, String>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("PRAGMA main.table_info(" + quote(table) + ")")) {
            while (rows.next()) {
                result.put(rows.getString("name"), rows.getString("type"));
            }
        }
        return result;
    }
    private boolean exists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM main.sqlite_master WHERE type='table' AND name=? COLLATE NOCASE")) {
            statement.setString(1, table);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        }
    }
    private long scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(sql)) {
            return row.next() ? row.getLong(1) : 0;
        }
    }
    private boolean sameType(String left, String right) {
        return left.replace(" ", "").equalsIgnoreCase(right.replace(" ", ""));
    }
    private String quote(String identifier) throws SQLException {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new SQLException("Identificador de esquema no válido.");
        }
        return "\"" + identifier + "\"";
    }
    private String sqlType(String type) throws SQLException {
        if (type == null || !type.toUpperCase(Locale.ROOT).matches("TEXT|INTEGER|LONG|BOOLEAN|DOUBLE|REAL|NUMERIC|BIGINT|VARCHAR\\([1-9][0-9]*\\)|DECIMAL\\([1-9][0-9]*,[0-9]+\\)")) {
            throw new SQLException("Tipo de columna no admitido para una ampliación segura.");
        }
        return type.toUpperCase(Locale.ROOT);
    }
    private String literal(Object value) throws SQLException {
        if (value instanceof Boolean) {
            return Boolean.TRUE.equals(value) ? "1" : "0";
        }
        if (value instanceof Integer || value instanceof Long || value instanceof BigDecimal) {
            return value.toString();
        }
        if (value instanceof Double && Double.isFinite((Double) value)) {
            return value.toString();
        }
        if (value instanceof String && ((String) value).indexOf('\0') < 0) {
            return "'" + ((String) value).replace("'", "''") + "'";
        }
        throw new SQLException("La columna nueva necesita un valor inicial explícito, finito y compatible; no se inventan valores nulos.");
    }
    private String digest(String text) throws SQLException {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte value : bytes) {
                result.append(String.format(Locale.ROOT, "%02x", value & 255));
            }
            return result.toString();
        } catch (Exception failure) {
            throw new SQLException("No se pudo comprobar la identidad de la migración.", failure);
        }
    }
    private static final class Snapshot {
        private final String original;
        private final List<String> columns;
        private String current;
        private boolean dropped;
        private Snapshot(String original, List<String> columns) {
            this.original = original;
            this.columns = columns;
            current = original;
        }
    }
}
