package me.pepe.DatabaseAPI.DatabaseManager.Types;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.pepe.DatabaseAPI.DatabaseAPI;
import me.pepe.DatabaseAPI.DatabaseManager.DatabaseKeyType;
import me.pepe.DatabaseAPI.DatabaseManager.DatabaseTableInstance;
import me.pepe.DatabaseAPI.DatabaseManager.PlayerData;
import me.pepe.DatabaseAPI.DatabaseManager.Identifier.Identifier;
import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Player.PlayerDatabaseTable;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Player.Multi.MultiPlayerDatabaseTable;
import me.pepe.DatabaseAPI.Utils.Callback;
import me.pepe.DatabaseAPI.Utils.CallbackRequest;
import me.pepe.DatabaseAPI.Utils.DatabaseConfiguration;
import me.pepe.DatabaseAPI.Utils.MySQLConnection;
import me.pepe.DatabaseAPI.Utils.SimpleCallbackRequest;
import me.pepe.DatabaseAPI.Schema.SchemaMigrationHistoryTable;
import me.pepe.DatabaseAPI.Schema.SchemaMigrationManager;

public abstract class Database {
	private String name;
	private boolean temporaly = false;
	private final ConcurrentHashMap<Class<? extends DatabaseTable>, DatabaseTableInstance> tableInstances = new ConcurrentHashMap<Class<? extends DatabaseTable>, DatabaseTableInstance>();
	private boolean obligatorySQLite = false;
	private final Object connectionLock = new Object();
	private final Object lifecycleLock = new Object();
	private volatile Thread queueThread;
	private volatile boolean closing;
	private volatile boolean closed;
	private volatile SQLException closeError;
	private Future<?> closingTask;
	private int activeOperations;
	private final ThreadLocal<Integer> operationDepth = new ThreadLocal<Integer>();
	private final ExecutorService queue = Executors.newSingleThreadExecutor(new ThreadFactory() {
		@Override
		public Thread newThread(Runnable task) {
			Thread thread = new Thread(task, "DatabaseAPI-" + getDatabaseName());
			queueThread = thread;
			return thread;
		}
	});
	private List<String> noSaveTable = new ArrayList<String>();
	private Connection sqlConnection;
	private File sqliteFile;
	private MySQLConnection mysqlConnection;
	private SchemaMigrationManager schemaMigrationManager;
	private volatile SQLException schemaError;
	public Database(String name) {
		this(name, false, false);
	}
	public Database(String name, boolean obligatorySQLite) {
		this(name, obligatorySQLite, false);
	}
	public Database(String name, boolean obligatorySQLite, boolean temporaly) {
		this.name = name;
		this.obligatorySQLite = temporaly ? true : obligatorySQLite;
		this.temporaly = temporaly;
		DatabaseConfiguration config = DatabaseAPI.getInstance().getConfiguration();
		if (config.isSQL() || isObligatorySQLite()) {
			File db = new File(config.getDataFolder(), "Databases/" + (temporaly ? "Temp/" : "") + name + ".db");
			sqliteFile = db;
			if (!db.exists()) {
				try {
					db.createNewFile();
					System.out.println(db.getAbsolutePath() + " created!");
				} catch (IOException e) {
					e.printStackTrace();
				}
			} else {
				System.out.println(db.getAbsolutePath() + " loaded!");
			}
			/**
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:sqlite:" + db);
            hikariConfig.setDriverClassName("org.sqlite.JDBC");
            hikariConfig.setMaximumPoolSize(1);
            hikariConfig.setAutoCommit(true);
            hikariConfig.setConnectionTestQuery("SELECT 1");
            hikariConfig.setMaxLifetime(60000); // 60 Sec
            hikariConfig.setIdleTimeout(45000); // 45 Sec
            hikariConfig.setMaximumPoolSize(50); // 50 Connections (including idle connections)
            source = new HikariDataSource(hikariConfig);
			 */
			try {
				Class.forName("org.sqlite.JDBC");
				sqlConnection = DriverManager.getConnection("jdbc:sqlite:" + db);
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			// SE HA CAMBIADO EN setConnectionReady() porque hacia la conexion antes de crear la base de datos...
			/**
			 * 
			try {
				Class.forName("org.mariadb.jdbc.Driver");
				createDatabase(database.getDatabaseName());
				connection = DriverManager.getConnection("jdbc:mariadb://" + instance.getConfiguration().getIP() + ":" + instance.getConfiguration().getPort() + "/" + database.getDatabaseName() + "?user=" + instance.getConfiguration().getUser() + "&password=" + instance.getConfiguration().getPassword() + "&autoReconnect=true&amp;useUnicode=true;characterEncoding=UTF-8&maxIdleTime=999999999&sessionVariables=wait_timeout=999999999");
			} catch (Exception e) {
				e.printStackTrace();
			}
			 */
		}
	}
	public void setConnectionReady() {
		DatabaseConfiguration config = DatabaseAPI.getInstance().getConfiguration();
		if (mysqlConnection == null && sqlConnection == null && !isSQLite()) {
			mysqlConnection = new MySQLConnection(DatabaseAPI.getInstance(), this, new Callback<Connection>() {
				@Override
				public void done(Connection connection, Exception ex) {
					if (ex != null) {
						ex.printStackTrace();
					}
				}
			});
			/** 
			HikariConfig hikariConfig = new HikariConfig();
			hikariConfig.setJdbcUrl("jdbc:mariadb://" + config.getIP() + ":" + config.getPort() + "/" + getDatabaseName() + "?user=" + config.getUser() + "&password=" + config.getPassword() + "&autoReconnect=true&amp;useUnicode=true;characterEncoding=UTF-8&maxIdleTime=999999999&sessionVariables=wait_timeout=999999999");
			hikariConfig.setDriverClassName("org.mariadb.jdbc.Driver");
			hikariConfig.setMaximumPoolSize(3);
			hikariConfig.setAutoCommit(true);
			hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
			hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
			hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
			mysqlConnection = new HikariDataSource(hikariConfig);
			 */
		}
	}
	public String getDatabaseName() {
		return name;
	}
	public File getSQLiteFile() {
		if (sqliteFile == null) {
			throw new IllegalStateException("La base no utiliza SQLite.");
		}
		return sqliteFile;
	}
	public long getSchemaMigrationVersion() {
		return schemaMigrationManager == null ? 0 : schemaMigrationManager.getMigrationVersion();
	}
	public ExecutorService getQueue() {
		return queue;
	}
	public boolean isObligatorySQLite() {
		return obligatorySQLite;
	}
	public boolean isSQLite() {
		return obligatorySQLite || DatabaseAPI.getInstance().getConfiguration().isSQL();
	}
	public boolean isTemporaly() {
		return temporaly;
	}
	public void getConnection(Callback<Connection> callback) {
		synchronized (connectionLock) {
			if (schemaError != null) {
				notifyCallback(callback, null, schemaError);
				return;
			}
			if (closed) {
				notifyCallback(callback, null, new SQLException("La base de datos está cerrada"));
				return;
			}
			if (isSQLite()) {
				try {
					if (sqlConnection == null || sqlConnection.isClosed()) {
						notifyCallback(callback, null, new SQLException("La conexión SQLite no está disponible"));
					} else {
						notifyCallback(callback, sqlConnection, null);
					}
				} catch (SQLException exception) {
					notifyCallback(callback, null, exception);
				}
			} else if (mysqlConnection == null) {
				notifyCallback(callback, null, new SQLException("La conexión MariaDB no está preparada"));
			} else {
				mysqlConnection.getConnection(callback);
			}
		}
	}
	public boolean hasTable(Class<? extends DatabaseTable> clase) {
		return tableInstances.containsKey(clase);
	}
	public void internalLoadPlayer(PlayerData pData) {
		for (Entry<Class<? extends DatabaseTable>, DatabaseTableInstance> entryTable : tableInstances.entrySet()) {
			if (pData.getOnlyLoad() != null) {
				if (pData.getOnlyLoad().contains(entryTable.getKey()) && !entryTable.getKey().equals(Identifier.class)) {
					try {
						DatabaseTable newTable = entryTable.getValue().newInstance(null);
						if (newTable instanceof PlayerDatabaseTable) {
							DatabaseTable table = entryTable.getValue().newInstance(pData);
							pData.addData((PlayerDatabaseTable) table);
						}
					} catch (ClassCastException ex) {
						// la key no es un PlayerData, por lo que no es una tabla de datos de jugador.
					}
				}
			} else {
				if (!entryTable.getKey().equals(Identifier.class)) {
					try {
						DatabaseTable newTable = entryTable.getValue().newInstance(null);
						if (newTable instanceof PlayerDatabaseTable) {
							DatabaseTable table = entryTable.getValue().newInstance(pData);
							pData.addData((PlayerDatabaseTable) table);
						}
					} catch (ClassCastException ex) {
						// la key no es un PlayerData, por lo que no es una tabla de datos de jugador.
					}
				}
			}
		}
	}
	public void getAllTables(Class<? extends DatabaseTable> type, final Callback<DatabaseTable> callback,
			final Callback<CallbackRequest<Boolean>> onFinish) {
		queryTables(type, null, false, new Callback<TableDatabaseMultiKeys>() {
			@Override
			public void done(TableDatabaseMultiKeys table, Exception exception) {
				notifyCallback(callback, table, exception);
			}
		}, null, onFinish, 0, 0, "", callback);
	}
	public void newTableDatabaseMultiKeysEntry(Class<? extends TableDatabaseMultiKeys> type, Callback<DatabaseTable> callback) {
		newTableDatabaseMultiKeysEntry(type, callback, false);
	}
	public void newTableDatabaseMultiKeysEntry(Class<? extends TableDatabaseMultiKeys> type, Callback<DatabaseTable> callback, boolean async) {
		getTable(type, 0L, async, callback);
	}
	public void getTable(Class<? extends DatabaseTable> type, Object key, Callback<DatabaseTable> callback) {
		getTable(type, key, true, callback);
	}
	public void getTable(Class<? extends DatabaseTable> type, Object key, boolean async, Callback<DatabaseTable> callback) {
		DatabaseTable table;
		try {
			table = newTable(type, key);
			if (table instanceof TableDatabaseMultiKeys) {
				((TableDatabaseMultiKeys) table).buildKey(((Number) key).longValue());
			}
		} catch (Exception exception) {
			notifyCallback(callback, null, exception);
			return;
		}
		load(async, table, callback);
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> type, HashMap<String, Object> keys,
			Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFound) {
		getTable(type, keys, true, callback, notFound, null);
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> type, HashMap<String, Object> keys, boolean async,
			Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFound) {
		getTable(type, keys, async, callback, notFound, null);
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> type, HashMap<String, Object> keys,
			Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFound,
			Callback<CallbackRequest<Boolean>> onFinish) {
		getTable(type, keys, true, callback, notFound, onFinish);
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> type, HashMap<String, Object> keys, boolean async,
			Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFound,
			Callback<CallbackRequest<Boolean>> onFinish) {
		getTable(type, keys, async, callback, notFound, onFinish, 0, 0, "");
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> type, HashMap<String, Object> keys, boolean async,
			Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFound,
			Callback<CallbackRequest<Boolean>> onFinish, int limit) {
		getTable(type, keys, async, callback, notFound, onFinish, limit, 0, "");
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> type, HashMap<String, Object> keys, boolean async,
			Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFound,
			Callback<CallbackRequest<Boolean>> onFinish, int limit, int offset) {
		getTable(type, keys, async, callback, notFound, onFinish, limit, offset, "");
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> type, HashMap<String, Object> keys, boolean async,
			Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFound,
			Callback<CallbackRequest<Boolean>> onFinish, int limit, int offset, String orderBy) {
		queryTables(type, keys, async, callback, notFound, onFinish, limit, offset, orderBy, null);
	}
	public void getTableMultiKeysMultiEntrys(Class<? extends TableDatabaseMultiKeys> type, HashMap<String, Object> keys,
			boolean async, Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFound) {
		getTable(type, keys, async, callback, notFound, null);
	}
	private void queryTables(final Class<? extends DatabaseTable> type, HashMap<String, Object> keys, boolean async,
			final Callback<TableDatabaseMultiKeys> callback, final Callback<HashMap<String, Object>> notFound,
			final Callback<CallbackRequest<Boolean>> onFinish, final int limit, final int offset, final String orderBy,
			final Callback<DatabaseTable> allTablesCallback) {
		final HashMap<String, Object> filters = copyKeys(keys);
		execute(async, new SqlOperation<List<DatabaseTable>>() {
			@Override
			public List<DatabaseTable> run(Connection connection) throws Exception {
				DatabaseTable prototype = newTable(type, null);
				List<DatabaseTable> tables = new ArrayList<DatabaseTable>();
				try (PreparedStatement statement = prepareStatement("*", prototype, filters, connection, limit, offset, orderBy);
						ResultSet result = statement.executeQuery()) {
					while (result.next()) {
						Object key = prototype instanceof TableDatabaseMultiKeys
								? Long.valueOf(result.getLong(prototype.getKeyName())) : result.getObject(prototype.getKeyName());
						DatabaseTable table = newTable(type, key);
						if (table instanceof TableDatabaseMultiKeys) {
							((TableDatabaseMultiKeys) table).load(result, false);
						} else {
							table.setLoaded();
							table.setHasData(true);
							table.deserialize(result);
							table.reloadLastSave(false);
						}
						tables.add(table);
					}
				}
				return tables;
			}
		}, new Callback<List<DatabaseTable>>() {
			@Override
			public void done(List<DatabaseTable> tables, Exception exception) {
				if (exception != null) {
					if (onFinish != null) {
						notifyCallback(onFinish, new CallbackRequest<Boolean>(false, exception), exception);
					} else if (allTablesCallback != null) {
						notifyCallback(allTablesCallback, null, exception);
					} else {
						notifyCallback(callback, null, exception);
					}
					return;
				}
				for (DatabaseTable table : tables) {
					try {
						table.onLoad(true);
					} catch (Exception failure) {
						if (onFinish != null) {
							notifyCallback(onFinish, new CallbackRequest<Boolean>(false, failure), failure);
						} else {
							notifyCallback(callback, null, failure);
						}
						return;
					}
					if (allTablesCallback != null) {
						notifyCallback(allTablesCallback, table, null);
					} else if (table instanceof TableDatabaseMultiKeys) {
						notifyCallback(callback, (TableDatabaseMultiKeys) table, null);
					}
				}
				if (tables.isEmpty() && notFound != null) {
					notifyCallback(notFound, filters, null);
				}
				notifyCallback(onFinish, new CallbackRequest<Boolean>(!tables.isEmpty()), null);
			}
		});
	}
	public void hasTableMultiKeysMultiEntrys(final Class<? extends TableDatabaseMultiKeys> type, HashMap<String, Object> keys,
			boolean async, Callback<Boolean> callback) {
		final HashMap<String, Object> filters = copyKeys(keys);
		execute(async, new SqlOperation<Boolean>() {
			@Override
			public Boolean run(Connection connection) throws Exception {
				DatabaseTable table = newTable(type, null);
				try (PreparedStatement statement = prepareStatement("1", table, filters, connection, 1, 0, "");
						ResultSet result = statement.executeQuery()) {
					return result.next();
				}
			}
		}, callback);
	}
	public void load(boolean async, DatabaseTable table) {
		load(async, table, null);
	}
	public void load(boolean async, final DatabaseTable table, final Callback<DatabaseTable> callback) {
		if (table instanceof MultiPlayerDatabaseTable) {
			((MultiPlayerDatabaseTable) table).load(async, callback);
			return;
		}
		execute(async, new SqlOperation<Boolean>() {
			@Override
			public Boolean run(Connection connection) throws Exception {
				if (table.isLoaded()) {
					return false;
				}
				HashMap<String, Object> keys = new HashMap<String, Object>();
				keys.put(table.getKeyName(), table.keySerialize());
				try (PreparedStatement statement = prepareStatement("*", table, keys, connection, 1, 0, "");
						ResultSet result = statement.executeQuery()) {
					table.setHasData(result.next());
					if (table.hasData()) {
						table.deserialize(result);
					}
					table.setLoaded();
				}
				return true;
			}
		}, new Callback<Boolean>() {
			@Override
			public void done(Boolean newlyLoaded, Exception exception) {
				if (exception == null && Boolean.TRUE.equals(newlyLoaded)) {
					try {
					table.onLoad(table.hasData());
					table.reloadLastSave(false);
				} catch (Exception failure) {
					notifyCallback(callback, table, failure);
					return;
				}
				}
				notifyCallback(callback, table, exception);
			}
		});
	}
	public void save(boolean async, boolean ignoreColumnsUpdate, DatabaseTable table) {
		save(async, ignoreColumnsUpdate, false, table, null);
	}
	public void save(boolean async, boolean ignoreColumnsUpdate, DatabaseTable table, Callback<SimpleCallbackRequest> callback) {
		save(async, ignoreColumnsUpdate, false, table, callback);
	}
	public void save(boolean async, final boolean ignoreColumnsUpdate, final boolean ignoreOnSave,
			final DatabaseTable table, final Callback<SimpleCallbackRequest> callback) {
		execute(async, new SqlOperation<Boolean>() {
			@Override
			public Boolean run(Connection connection) throws Exception {
				return saveRecord(connection, table, ignoreColumnsUpdate);
			}
		}, new Callback<Boolean>() {
			@Override
			public void done(Boolean written, Exception exception) {
				if (exception == null && Boolean.TRUE.equals(written) && !ignoreOnSave) {
					try {
						table.onSave();
					} catch (Exception failure) {
						exception = failure;
					}
				}
				notifyCallback(callback, exception == null ? new SimpleCallbackRequest() : failureResult(exception), exception);
			}
		});
	}
	public void getMaxOfTable(Class<? extends DatabaseTable> type, String column, boolean async, Callback<Object> callback) {
		getMaxOfTable(type, column, null, async, callback);
	}
	public void getMaxOfTable(Class<? extends DatabaseTable> type, String column, HashMap<String, Object> keys,
			boolean async, Callback<Object> callback) {
		aggregate(type, column, keys, async, "MAX", Object.class, callback);
	}
	public void getMinOfTable(Class<? extends DatabaseTable> type, String column, boolean async, Callback<Object> callback) {
		getMinOfTable(type, column, null, async, callback);
	}
	public void getMinOfTable(Class<? extends DatabaseTable> type, String column, HashMap<String, Object> keys,
			boolean async, Callback<Object> callback) {
		aggregate(type, column, keys, async, "MIN", Object.class, callback);
	}
	public void getMedieOfTable(Class<? extends DatabaseTable> type, String column, boolean async, Callback<Double> callback) {
		getMedieOfTable(type, column, null, async, callback);
	}
	public void getMedieOfTable(Class<? extends DatabaseTable> type, String column, HashMap<String, Object> keys,
			boolean async, Callback<Double> callback) {
		aggregate(type, column, keys, async, "AVG", Double.class, callback);
	}
	public void getSumOfTable(Class<? extends DatabaseTable> type, String column, boolean async, Callback<Integer> callback) {
		getSumOfTable(type, column, null, async, callback);
	}
	public void getSumOfTable(Class<? extends DatabaseTable> type, String column, HashMap<String, Object> keys,
			boolean async, Callback<Integer> callback) {
		aggregate(type, column, keys, async, "SUM", Integer.class, callback);
	}
	public void getCountOfTable(Class<? extends DatabaseTable> type, String column, boolean async, Callback<Integer> callback) {
		getCountOfTable(type, column, null, async, callback);
	}
	public void getCountOfTable(Class<? extends DatabaseTable> type, String column, HashMap<String, Object> keys,
			boolean async, Callback<Integer> callback) {
		aggregate(type, column, keys, async, "COUNT", Integer.class, callback);
	}
	public void getDecimalSumOfTable(Class<? extends DatabaseTable> type, String column, boolean async, Callback<BigDecimal> callback) {
		getDecimalSumOfTable(type, column, null, async, callback);
	}
	public void getDecimalSumOfTable(Class<? extends DatabaseTable> type, String column, HashMap<String, Object> keys,
			boolean async, Callback<BigDecimal> callback) {
		aggregate(type, column, keys, async, "SUM", BigDecimal.class, callback);
	}
	/**
	 * This method is used to register the tables in the API so that they can be used later.
	 * @param instance - DatabaseAPI instance
	 * @param clase - Table class object
	 * @param tableInstance - Database table instance creator
	 */
	public void registerTable(DatabaseAPI instance, Class<? extends DatabaseTable> tableClass, DatabaseTableInstance tableInstance) {
		registerTable(instance, tableClass, tableInstance, true);
	}
	/**
	 * This method is used to register the tables in the API so that they can be used later.
	 * @param instance - DatabaseAPI instance
	 * @param clase - Table class object
	 * @param tableInstance - Database table instance creator
	 * @param saveDatabase - if you like save this table (update/insert)
	 */
	public void registerTable(DatabaseAPI instance, Class<? extends DatabaseTable> tableClass, DatabaseTableInstance tableInstance, boolean saveDatabase) {
		DatabaseTable table = tableInstance.newInstance(null);
		final boolean managedSchema = instance.getConfiguration().isSchemaMigrationsEnabled() && tableClass != SchemaMigrationHistoryTable.class;
		if (tableInstances.containsKey(tableClass)) {
			System.err.println("[Database]: &cLa tabla " + table.getTableName() + " ya estaba registrada...");
		} else {
			if (managedSchema) {
				try {
					if (schemaMigrationManager == null) {
						schemaMigrationManager = new SchemaMigrationManager(this);
					}
					schemaMigrationManager.initializeHistory(instance);
				} catch (SQLException failure) {
					schemaError = failure;
					tableInstance.registerError("No se pudo preparar el historial de esquema: " + failure.getMessage());
					tableInstances.put(tableClass, tableInstance);
					return;
				}
			}
			if (!saveDatabase) {
				noSaveTable.add(table.getTableName());
			}
			getConnection(new Callback<Connection>() {
				@Override
				public void done(Connection connection, Exception exception) {
					if (exception != null) {
						tableInstance.registerError("Error de conexión al registrar la tabla");
						tableInstances.put(tableClass, tableInstance);
						if (instance.getConfiguration().isSchemaMigrationsEnabled()) {
							schemaError = new SQLException("No se ha confirmado la preparación del esquema.", exception);
						}
						return;
					}
					try {
						if (managedSchema) {
							schemaMigrationManager.beforeTable(connection, table);
						}
						DatabaseMetaData meta = connection.getMetaData();
						boolean tableExists;
						try (ResultSet rs = meta.getTables(null, null, table.getTableName(), new String[] {"TABLE"})) {
							tableExists = rs.next();
						}
						if (tableExists) {
							System.out.println("[Database]: &aLa tabla de " + table.getTableName() + " se ha cargado correctamente en la base de datos " + getDatabaseName() + (isSQLite() ? "sqlite" : "mysql"));
							Map<String, String> actualColumns = new HashMap<>();
							try (ResultSet columns = meta.getColumns(null, null, table.getTableName(), null)) {
							while (columns.next()) {
							    String columnName = columns.getString("COLUMN_NAME");
							    String dataType = columns.getString("TYPE_NAME");
							    actualColumns.put(columnName, dataType);
							}
							}							
							Map<String, Object> expectedMap = table.serialize(new HashMap<String, Object>());
							Map<String, String> expectedColumns = new HashMap<String, String>();
							for (Entry<String, Object> entry : expectedMap.entrySet()) {
							    String columnName = entry.getKey();
							    String type = getStatementName(entry.getValue());
							    expectedColumns.put(columnName, type);
							}				
							expectedColumns.put(table.getKeyName(), table.getKeyType().getStatementName(!isSQLite()));			
							for (Entry<String, String> expected : expectedColumns.entrySet()) {
							    String column = expected.getKey();
							    String expectedType = expected.getValue();

							    if (!actualColumns.containsKey(column)) {
							    	tableInstance.registerError("Missing column " + column);
							    } else {
							        String actualType = actualColumns.get(column);
							        if (!actualType.equalsIgnoreCase(expectedType)) {
							        	tableInstance.registerError("Different type in column: " + column + " (expected: " + expectedType + ", current: " + actualType + ")");
							        }
							    }
							}							
							for (String dbColumn : actualColumns.keySet()) {
							    if (!expectedColumns.containsKey(dbColumn)) {
							    	if (managedSchema) {
							    		System.out.println("[DatabaseAPI][schema] Columna conservada sin uso actual: " + table.getTableName() + "." + dbColumn);
							    	} else {
							    		tableInstance.registerError("Extra column in table, not used in DatabaseTable: " + dbColumn);
							    	}
							    }
							}
						} else {
							String createTableStatement = "CREATE TABLE IF NOT EXISTS " + table.getTableName() + " (" + table.getKeyName() + " " + table.getKeyType().getStatementName(!isSQLite()) + (table instanceof MultiPlayerDatabaseTable ? ", data_name VARCHAR(50) NOT NULL" : (table.isAutoIncrement() ? " PRIMARY KEY " + (isSQLite() ? "AUTOINCREMENT" : "AUTO_INCREMENT") : (table.hasPrimaryKey() ? " PRIMARY KEY" : " NOT NULL")));
							HashMap<String, Object> saveMap = table.serialize(new HashMap<String, Object>());
							for (Entry<String, Object> save : saveMap.entrySet()) {
								createTableStatement = createTableStatement + ", " + save.getKey() + " " + getStatementName(save.getValue()) + " NOT NULL";
							}
							createTableStatement = createTableStatement + ");";
							try (PreparedStatement statement = connection.prepareStatement(createTableStatement)) {
							System.out.println("[Database]: &aLa tabla de " + table.getTableName() + " se ha generado correctamente en la base de datos " + getDatabaseName() + (isSQLite() ? "sqlite" : "mysql"));
							statement.executeUpdate();
							}
						}
						if (managedSchema) {
							if (tableInstance.hasError()) {
								throw new SQLException("El esquema de " + table.getTableName() + " no supera la validación.");
							}
							schemaMigrationManager.afterTable(connection, table);
						}
						tableInstances.put(tableClass, tableInstance);
						System.out.println("[Database]: &aLa tabla de " + table.getTableName() + " se ha registrado exitosamente en la base de datos " + getDatabaseName());
					} catch(SQLException | RuntimeException ex) {
						tableInstance.registerError("No se ha confirmado el registro/actualización: " + ex.getMessage());
						tableInstances.put(tableClass, tableInstance);
						if (instance.getConfiguration().isSchemaMigrationsEnabled()) {
							schemaError = ex instanceof SQLException ? (SQLException) ex : new SQLException("Fallo durante la preparación del esquema.", ex);
						}
						ex.printStackTrace();
					}
				}			
			});
		}
	}
	public boolean hasError() {
		if (schemaError != null) {
			return true;
		}
		for (DatabaseTableInstance tableInstance : tableInstances.values()) {
			if (tableInstance.hasError()) {
				return true;
			}
		}
		return false;
	}
	public HashMap<String, List<String>> getErrors() {
		HashMap<String, List<String>> errors = new HashMap<String, List<String>>();
		if (schemaError != null) {
			List<String> failures = new ArrayList<String>();
			failures.add(schemaError.getMessage());
			errors.put("DatabaseSchemaMigrationTable", failures);
		}
		for (DatabaseTableInstance<?> tableInstance : tableInstances.values()) {
			if (tableInstance.hasError()) {
				List<String> tableErrors = new ArrayList<String>();
				for (String error : tableInstance.getErrors()) {
					tableErrors.add(error);
				}
				errors.put(tableInstance.newInstance(null).getTableName(), tableErrors);
			}
		}
		return errors;
	}
	public void editTableInstance(DatabaseAPI instance, Class<? extends DatabaseTable> tableClass, DatabaseTableInstance tableInstance) {
		DatabaseTable table = tableInstance.newInstance(null);
		if (tableInstances.containsKey(tableClass)) {
			tableInstances.put(tableClass, tableInstance);
			System.out.println("[Database]: &aLa instancia de la tabla " + table.getTableName() + " ha sido modificada correctamente");
		} else {
			System.out.println("[Database]: &cLa tabla " + table.getTableName() + " no se puede editar porque no estaba registrada...");
		}
	}
	public void remove(boolean async, final DatabaseTable table, Callback<Integer> callback) {
		execute(async, new SqlOperation<Integer>() {
			@Override
			public Integer run(Connection connection) throws Exception {
				String sql = "DELETE FROM " + identifier(table.getTableName()) + " WHERE " + identifier(table.getKeyName()) + " = ?";
				int affected;
				try (PreparedStatement statement = connection.prepareStatement(sql)) {
					saveInStatement(statement, table.keySerialize(), 1);
					affected = statement.executeUpdate();
				}
				table.setHasData(false);
				return affected;
			}
		}, callback);
	}
	public void remove(boolean async, DatabaseTable table) {
		remove(async, table, null);
	}
	public void saveInStatement(PreparedStatement statement, Object value, int index) throws SQLException {
		if (value == null) {
			statement.setNull(index, Types.NULL);
		} else if (value instanceof Integer) {
			statement.setInt(index, (Integer) value);
		} else if (value instanceof Double) {
			statement.setDouble(index, (Double) value);
		} else if (value instanceof Long) {
			statement.setLong(index, (Long) value);
		} else if (value instanceof Boolean) {
			statement.setBoolean(index, (Boolean) value);
		} else if (value instanceof BigDecimal) {
			statement.setBigDecimal(index, (BigDecimal) value);
		} else {
			statement.setString(index, value.toString());
		}
	}
	public String getStatementName(Object value) {
		if (value instanceof Integer) {
			return "INTEGER";
		}
		if (value instanceof Double) {
			return "DOUBLE";
		}
		if (value instanceof Long) {
			return "LONG";
		}
		if (value instanceof Boolean) {
			return "BOOLEAN";
		}
				return "TEXT";
	}
	private PreparedStatement prepareStatement(String select, DatabaseTable table, HashMap<String, Object> keys,
			Connection connection, int limit, int offset, String orderBy) throws SQLException {
		if (limit < 0 || offset < 0) {
			throw new SQLException("El límite y el desplazamiento deben ser positivos");
		}
		StringBuilder sql = new StringBuilder("SELECT ").append(select).append(" FROM ").append(identifier(table.getTableName()));
		List<Object> values = new ArrayList<Object>();
		boolean first = true;
		for (Entry<String, Object> entry : keys.entrySet()) {
			String raw = entry.getKey();
			String column = raw;
			String operator = "=";
			if (!hasColumn(table, column)) {
				for (String suffix : new String[] {"!=", "<=", ">=", "LIKE", "!", "<", ">"}) {
					if (raw.endsWith(suffix)) {
						column = raw.substring(0, raw.length() - suffix.length());
						operator = suffix.equals("!") ? "!=" : suffix;
						break;
					}
				}
			}
			requireColumn(table, column);
			sql.append(first ? " WHERE " : " AND ").append(identifier(column));
			first = false;
			if (entry.getValue() == null && (operator.equals("=") || operator.equals("!="))) {
				sql.append(operator.equals("=") ? " IS NULL" : " IS NOT NULL");
			} else {
				sql.append(" ").append(operator).append(" ?");
				values.add(entry.getValue());
			}
		}
		if (orderBy != null && !orderBy.trim().isEmpty()) {
			boolean firstOrder = true;
			for (String term : orderBy.split(",", -1)) {
				String[] parts = term.trim().split("\\s+");
				if (parts.length > 2
						|| (parts.length == 2 && !parts[1].equalsIgnoreCase("ASC") && !parts[1].equalsIgnoreCase("DESC"))) {
					throw new SQLException("Orden de consulta inválido");
				}
				requireColumn(table, parts[0]);
				sql.append(firstOrder ? " ORDER BY " : ", ").append(identifier(parts[0]));
				if (parts.length == 2) {
					sql.append(" ").append(parts[1].toUpperCase(Locale.ROOT));
				}
				firstOrder = false;
			}
		}
		if (limit > 0) {
			sql.append(" LIMIT ").append(limit);
		} else if (offset > 0) {
			sql.append(isSQLite() ? " LIMIT -1" : " LIMIT 18446744073709551615");
		}
		if (offset > 0) {
			sql.append(" OFFSET ").append(offset);
		}
		PreparedStatement statement = connection.prepareStatement(sql.toString());
		try {
			int index = 1;
			for (Object value : values) {
				saveInStatement(statement, value, index++);
			}
			return statement;
		} catch (SQLException | RuntimeException exception) {
			statement.close();
			throw exception;
		}
	}
	private String identifier(String name) throws SQLException {
		if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
			throw new SQLException("Identificador de base de datos inválido");
		}
		return "`" + name + "`";
	}
	private boolean hasColumn(DatabaseTable table, String name) {
		return table.getKeyName().equals(name) || table.serialize(new HashMap<String, Object>()).containsKey(name);
	}
	private void requireColumn(DatabaseTable table, String column) throws SQLException {
		if (!hasColumn(table, column)) {
			throw new SQLException("Columna no registrada en " + table.getTableName() + ": " + column);
		}
		identifier(column);
	}
	private DatabaseTable newTable(Class<? extends DatabaseTable> type, Object key) throws SQLException {
		DatabaseTableInstance factory = tableInstances.get(type);
		if (factory == null) {
			throw new SQLException("La tabla no está registrada en " + getDatabaseName());
		}
		return factory.newInstance(key);
	}
	private HashMap<String, Object> copyKeys(HashMap<String, Object> keys) {
		return keys == null ? new HashMap<String, Object>() : new HashMap<String, Object>(keys);
	}
	private <T> void aggregate(final Class<? extends DatabaseTable> type, final String column, HashMap<String, Object> keys,
			boolean async, final String function, final Class<T> resultType, Callback<T> callback) {
		final HashMap<String, Object> filters = copyKeys(keys);
		execute(async, new SqlOperation<T>() {
			@Override
			public T run(Connection connection) throws Exception {
				DatabaseTable table = newTable(type, null);
				if (!function.equals("COUNT") || !column.equals("*")) {
					requireColumn(table, column);
				}
				String select = function + "(" + (column.equals("*") ? "*" : identifier(column)) + ") AS result";
				try (PreparedStatement statement = prepareStatement(select, table, filters, connection, 0, 0, "");
						ResultSet result = statement.executeQuery()) {
					result.next();
					if (resultType == Object.class) {
						return resultType.cast(result.getObject("result"));
					}
					if (resultType == Double.class) {
						return resultType.cast(Double.valueOf(result.getDouble("result")));
					}
					BigDecimal value = result.getBigDecimal("result");
					if (value == null) {
						value = BigDecimal.ZERO;
					}
					if (resultType == BigDecimal.class) {
						return resultType.cast(value);
					}
					return resultType.cast(Integer.valueOf(value.intValueExact()));
				}
			}
		}, callback);
	}
	private boolean saveRecord(Connection connection, DatabaseTable table, boolean ignoreColumnsUpdate) throws SQLException {
		if (!table.isLoaded()) {
			throw new SQLException("La tabla debe cargarse antes de guardarse");
		}
		if (!DatabaseAPI.getInstance().getDatabaseManager().saveable(getDatabaseName()) || noSaveTable.contains(table.getTableName())) {
			throw new SQLException("El guardado está deshabilitado para esta tabla");
		}
		synchronized (table) {
			if (table.isSaving()) {
				throw new SQLException("La tabla ya tiene un guardado en curso");
			}
			if (table.isSaved(ignoreColumnsUpdate)) {
				return false;
			}
			table.setSaving(true);
		}
		boolean ownTransaction = false;
		try {
			HashMap<String, Object> data = new HashMap<String, Object>(table.serialize(new HashMap<String, Object>()));
			boolean inserting = !table.hasData();
			boolean generatedKey = inserting && table instanceof TableDatabaseMultiKeys && table.isAutoIncrement()
					&& table.keySerialize() instanceof Number && ((Number) table.keySerialize()).longValue() == 0L;
			ownTransaction = connection.getAutoCommit();
			if (ownTransaction) {
				connection.setAutoCommit(false);
			}
			if (inserting && !generatedKey && rowExists(connection, table)) {
				inserting = false;
			}
			long key = 0;
			try (PreparedStatement statement = inserting ? insert(connection, table, data, generatedKey) : update(connection, table, data)) {
				int affected = statement.executeUpdate();
				if (affected == 0 && (inserting || !rowExists(connection, table))) {
					throw new SQLException("No se ha confirmado la persistencia del registro");
				}
				if (generatedKey) {
					try (ResultSet result = statement.getGeneratedKeys()) {
						if (!result.next() || (key = result.getLong(1)) == 0L) {
							throw new SQLException("No se pudo obtener la clave del nuevo registro");
						}
					}
				}
			}
			if (ownTransaction) {
				connection.commit();
			}
			if (generatedKey) {
				((TableDatabaseMultiKeys) table).buildKey(key);
			}
			table.setHasData(true);
			table.reloadLastSave(data, false);
			return true;
		} catch (SQLException | RuntimeException exception) {
			if (ownTransaction) {
				try {
					connection.rollback();
				} catch (SQLException rollbackFailure) {
					exception.addSuppressed(rollbackFailure);
				}
			}
			throw exception;
		} finally {
			table.setSaving(false);
			if (ownTransaction) {
				connection.setAutoCommit(true);
			}
		}
	}
	private boolean rowExists(Connection connection, DatabaseTable table) throws SQLException {
		String sql = "SELECT 1 FROM " + identifier(table.getTableName()) + " WHERE " + identifier(table.getKeyName()) + " = ? LIMIT 1";
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			saveInStatement(statement, table.keySerialize(), 1);
			try (ResultSet result = statement.executeQuery()) {
				return result.next();
			}
		}
	}
	private PreparedStatement insert(Connection connection, DatabaseTable table, HashMap<String, Object> data,
			boolean generatedKey) throws SQLException {
		List<String> columns = new ArrayList<String>(data.keySet());
		if (!generatedKey) {
			columns.add(0, table.getKeyName());
		}
		StringBuilder sql = new StringBuilder("INSERT INTO ").append(identifier(table.getTableName()));
		if (columns.isEmpty()) {
			sql.append(isSQLite() ? " DEFAULT VALUES" : " () VALUES ()");
		} else {
			sql.append(" (");
			for (int index = 0; index < columns.size(); index++) {
				sql.append(index == 0 ? "" : ", ").append(identifier(columns.get(index)));
			}
			sql.append(") VALUES (");
			for (int index = 0; index < columns.size(); index++) {
				sql.append(index == 0 ? "?" : ", ?");
			}
			sql.append(")");
		}
		PreparedStatement statement = connection.prepareStatement(sql.toString(), Statement.RETURN_GENERATED_KEYS);
		try {
			int index = 1;
			for (String column : columns) {
				saveInStatement(statement, column.equals(table.getKeyName()) ? table.keySerialize() : data.get(column), index++);
			}
			return statement;
		} catch (SQLException | RuntimeException exception) {
			statement.close();
			throw exception;
		}
	}
	private PreparedStatement update(Connection connection, DatabaseTable table, HashMap<String, Object> data) throws SQLException {
		StringBuilder sql = new StringBuilder("UPDATE ").append(identifier(table.getTableName())).append(" SET ");
		List<String> columns = new ArrayList<String>(data.keySet());
		for (int index = 0; index < columns.size(); index++) {
			sql.append(index == 0 ? "" : ", ").append(identifier(columns.get(index))).append(" = ?");
		}
		if (columns.isEmpty()) {
			sql.append(identifier(table.getKeyName())).append(" = ").append(identifier(table.getKeyName()));
		}
		sql.append(" WHERE ").append(identifier(table.getKeyName())).append(" = ?");
		PreparedStatement statement = connection.prepareStatement(sql.toString());
		try {
			int index = 1;
			for (String column : columns) {
				saveInStatement(statement, data.get(column), index++);
			}
			saveInStatement(statement, table.keySerialize(), index);
			return statement;
		} catch (SQLException | RuntimeException exception) {
			statement.close();
			throw exception;
		}
	}
	private SimpleCallbackRequest failureResult(Exception exception) {
		String message = exception.getMessage();
		return new SimpleCallbackRequest(message == null || message.isEmpty() ? exception.getClass().getSimpleName() : message);
	}
	private <T> void notifyCallback(Callback<T> callback, T result, Exception exception) {
		if (callback == null) {
			if (exception != null) {
				exception.printStackTrace();
			}
			return;
		}
		try {
			callback.done(result, exception);
		} catch (RuntimeException callbackFailure) {
			callbackFailure.printStackTrace();
		}
	}
	private interface SqlOperation<T> {
		T run(Connection connection) throws Exception;
	}
	private static class SqlResult<T> {
		T value;
		Exception error;
	}
	private <T> void execute(boolean async, final SqlOperation<T> operation, final Callback<T> callback) {
		Runnable task = new Runnable() {
			@Override
			public void run() {
				Integer previousDepth = operationDepth.get();
				operationDepth.set(previousDepth == null ? 1 : previousDepth + 1);
				try {
					final SqlResult<T> result = new SqlResult<T>();
					getConnection(new Callback<Connection>() {
						@Override
						public void done(Connection connection, Exception exception) {
							result.error = exception;
							if (exception == null) {
								try {
									result.value = operation.run(connection);
								} catch (Exception failure) {
									result.error = failure;
								}
							}
						}
					});
					notifyCallback(callback, result.value, result.error);
				} finally {
					if (previousDepth == null) {
						operationDepth.remove();
					} else {
						operationDepth.set(previousDepth);
					}
					synchronized (lifecycleLock) {
						activeOperations--;
						lifecycleLock.notifyAll();
					}
				}
			}
		};
		Exception rejection = null;
		synchronized (lifecycleLock) {
			if (closing) {
				rejection = new SQLException("La base de datos se está cerrando");
			} else {
				activeOperations++;
				if (async && Thread.currentThread() != queueThread) {
					try {
						queue.execute(task);
						return;
					} catch (RejectedExecutionException exception) {
						activeOperations--;
						rejection = exception;
					}
				}
			}
		}
		if (rejection == null) {
			task.run();
		} else {
			notifyCallback(callback, null, rejection);
		}
	}
	public void closeConnection() throws SQLException {
		Future<?> completion;
		synchronized (lifecycleLock) {
			if (closingTask == null) {
				closing = true;
				closingTask = queue.submit(new Runnable() {
					@Override
					public void run() {
						boolean interrupted = false;
						synchronized (lifecycleLock) {
							while (activeOperations > 0) {
								try {
									lifecycleLock.wait();
								} catch (InterruptedException exception) {
									interrupted = true;
								}
							}
						}
						if (interrupted) {
							Thread.currentThread().interrupt();
						}
						synchronized (connectionLock) {
							try {
								if (sqlConnection != null) {
									sqlConnection.close();
								} else if (mysqlConnection != null) {
									mysqlConnection.close();
								}
							} catch (SQLException exception) {
								closeError = exception;
							} finally {
								closed = true;
							}
						}
					}
				});
				queue.shutdown();
			}
			completion = closingTask;
		}
		if (Thread.currentThread() != queueThread && operationDepth.get() == null && !Thread.holdsLock(connectionLock)) {
			try {
				completion.get();
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new SQLException("Se interrumpió el cierre de la base de datos", exception);
			} catch (ExecutionException exception) {
				throw new SQLException("No se pudo cerrar la base de datos", exception.getCause());
			}
			if (closeError != null) {
				throw closeError;
			}
		}
	}
}
