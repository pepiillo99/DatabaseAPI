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
import java.util.IdentityHashMap;
import java.util.Objects;
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
import me.pepe.DatabaseAPI.Transaction.TransactionAction;
import me.pepe.DatabaseAPI.Transaction.TransactionContext;
import me.pepe.DatabaseAPI.Transaction.TransactionException;
import me.pepe.DatabaseAPI.Transaction.TransactionResult;
import me.pepe.DatabaseAPI.Transaction.TransactionWork;
import me.pepe.DatabaseAPI.Transaction.SqlWork;
import me.pepe.DatabaseAPI.Transaction.ScopedSqlConnection;
import me.pepe.DatabaseAPI.Transaction.TransactionWarningHandler;

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
	private volatile SQLException transactionError;
	private final ThreadLocal<ManagedTransaction> managedTransaction = new ThreadLocal<ManagedTransaction>();
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
		ManagedTransaction active = managedTransaction.get();
		if (active != null) {
			SQLException failure = active.rejectLegacyOperation();
			notifyCallback(callback, null, failure);
			return;
		}
		synchronized (connectionLock) {
			if (transactionError != null) {
				notifyCallback(callback, null, transactionError);
				return;
			}
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
		final boolean migrationsEnabled = instance != null && instance.getConfiguration() != null && instance.getConfiguration().isSchemaMigrationsEnabled();
		final boolean managedSchema = migrationsEnabled && tableClass != SchemaMigrationHistoryTable.class;
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
						if (migrationsEnabled) {
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
						if (migrationsEnabled) {
							schemaError = ex instanceof SQLException ? (SQLException) ex : new SQLException("Fallo durante la preparación del esquema.", ex);
						}
						ex.printStackTrace();
					}
				}			
			});
		}
	}
	public boolean hasError() {
		if (schemaError != null || transactionError != null) {
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
		if (transactionError != null) {
			List<String> failures = new ArrayList<String>();
			failures.add(transactionError.getMessage());
			errors.put("DatabaseTransaction", failures);
		}
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
		ManagedTransaction active = managedTransaction.get();
		if (active != null) {
			notifyCallback(callback, null, active.rejectLegacyOperation());
			return;
		}
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
	/** Devuelve el contexto gestionado del hilo actual, o null fuera de una transacción. */
	public TransactionContext currentTransaction() {
		return managedTransaction.get();
	}
	/** Reutiliza explícitamente el ámbito SQL actual o confirma uno propio antes de devolver el valor. */
	public <T> T withSql(final SqlWork<T> work, TransactionWarningHandler warnings) throws Exception {
		if (warnings == null || work == null) {
			throw new IllegalArgumentException("Trabajo y tratamiento de avisos obligatorios");
		}
		ManagedTransaction current = managedTransaction.get();
		if (current != null) {
			return current.sql(work);
		}
		TransactionResult<T> result = transaction(new TransactionWork<T>() {
			@Override
			public T execute(TransactionContext transaction) throws Exception {
				return transaction.sql(work);
			}
		});
		for (Exception warning : result.getPostCommitFailures()) {
			try {
				warnings.handle(warning);
			} catch (RuntimeException reportingFailure) {
				// La escritura está confirmada aunque falle su diagnóstico; no convertirla en reintentable.
				reportingFailure.printStackTrace();
			}
		}
		return result.getValue();
	}
	public <T> TransactionResult<T> transaction(TransactionWork<T> work) throws TransactionException {
		final SqlResult<TransactionResult<T>> result = new SqlResult<TransactionResult<T>>();
		transaction(false, work, new Callback<TransactionResult<T>>() {
			@Override
			public void done(TransactionResult<T> value, Exception failure) {
				result.value = value;
				result.error = failure;
			}
		});
		if (result.error != null) {
			throw (TransactionException) result.error;
		}
		return result.value;
	}
	public <T> void transaction(boolean async, final TransactionWork<T> work, final Callback<TransactionResult<T>> callback) {
		if (callback == null) {
			throw new IllegalArgumentException("La transacción requiere un callback de confirmación");
		}
		execute(async, new SqlOperation<CommittedWork<T>>() {
			@Override
			public CommittedWork<T> run(Connection connection) throws Exception {
				return runTransaction(connection, work);
			}
		}, new Callback<CommittedWork<T>>() {
			@Override
			public void done(CommittedWork<T> committed, Exception failure) {
				if (failure != null) {
					TransactionException error = failure instanceof TransactionException ? (TransactionException) failure : new TransactionException("No se pudo iniciar la transacción", TransactionException.Outcome.NOT_STARTED, failure);
					notifyCallback(callback, null, error);
					return;
				}
				committed.transaction.publish(committed.warnings);
				notifyCallback(callback, new TransactionResult<T>(committed.value, committed.warnings), null);
			}
		});
	}
	private <T> CommittedWork<T> runTransaction(Connection connection, TransactionWork<T> work) throws Exception {
		if (work == null || !connection.getAutoCommit()) {
			throw new TransactionException("Se requiere trabajo y una conexión sin transacción externa", TransactionException.Outcome.NOT_STARTED, null);
		}
		ManagedTransaction transaction = new ManagedTransaction(connection);
		boolean started = false;
		boolean commitAttempted = false;
		boolean committed = false;
		boolean rolledBack = false;
		TransactionException failed = null;
		List<Exception> warnings = new ArrayList<Exception>();
		try {
			connection.setAutoCommit(false);
			started = true;
			managedTransaction.set(transaction);
			T value = work.execute(transaction);
			transaction.checkReadyToCommit();
			commitAttempted = true;
			connection.commit();
			committed = true;
			return new CommittedWork<T>(value, transaction, warnings);
		} catch (Throwable failure) {
			if (started) {
				try {
					connection.rollback();
					rolledBack = true;
				} catch (SQLException rollbackFailure) {
					failure.addSuppressed(rollbackFailure);
				}
			}
			TransactionException.Outcome outcome = started && rolledBack && !commitAttempted ? TransactionException.Outcome.ROLLED_BACK : TransactionException.Outcome.UNKNOWN;
			if (outcome == TransactionException.Outcome.UNKNOWN) {
				transactionError = new SQLException("Resultado transaccional incierto: bloquear y conciliar antes de reabrir la base", failure);
			}
			if (failure instanceof Error) {
				throw (Error) failure;
			}
			failed = new TransactionException("No se confirmó la transacción: " + outcome, outcome, failure);
			throw failed;
		} finally {
			managedTransaction.remove();
			transaction.active = false;
			transaction.finish(committed, rolledBack && !commitAttempted, warnings);
			// No activar autocommit si el commit o el rollback dejaron un resultado incierto.
			if (committed || rolledBack && !commitAttempted) {
				try {
					connection.setAutoCommit(true);
				} catch (SQLException resetFailure) {
					transactionError = new SQLException("No se pudo restaurar la conexión después de la transacción", resetFailure);
					warnings.add(resetFailure);
				}
			}
			if (failed != null) {
				for (Exception warning : warnings) {
					failed.addSuppressed(warning);
				}
			}
		}
	}
	private final class CommittedWork<T> {
		private final T value;
		private final ManagedTransaction transaction;
		private final List<Exception> warnings;
		private CommittedWork(T value, ManagedTransaction transaction, List<Exception> warnings) {
			this.value = value;
			this.transaction = transaction;
			this.warnings = warnings;
		}
	}
	private boolean isRegisteredModel(DatabaseTable table) {
		if (tableInstances.containsKey(table.getClass())) {
			return true;
		}
		for (Map.Entry<Class<? extends DatabaseTable>, DatabaseTableInstance> entry : tableInstances.entrySet()) {
			if (entry.getKey().isInstance(table)) {
				DatabaseTable model = entry.getValue().newInstance(null);
				if (Objects.equals(model.getTableName(), table.getTableName()) && Objects.equals(model.getKeyName(), table.getKeyName()) && model.getKeyType() == table.getKeyType()) {
					return true;
				}
			}
		}
		return false;
	}
	private final class PendingWrite {
		private final DatabaseTable table;
		private final Object originalKey;
		private HashMap<String, Object> data;
		private Object writtenKey;
		private boolean written;
		private boolean notifySave;
		private PendingWrite(DatabaseTable table) {
			this.table = table;
			this.originalKey = table.keySerialize();
		}
	}
	private final class ManagedTransaction implements TransactionContext {
		private final Connection connection;
		private final Thread owner = Thread.currentThread();
		private final IdentityHashMap<DatabaseTable, PendingWrite> writes = new IdentityHashMap<DatabaseTable, PendingWrite>();
		private final List<PendingWrite> writeOrder = new ArrayList<PendingWrite>();
		private final List<DatabaseTable> loaded = new ArrayList<DatabaseTable>();
		private final List<TransactionAction> actions = new ArrayList<TransactionAction>();
		private boolean active = true;
		private volatile SQLException rollbackOnly;
		private ManagedTransaction(Connection connection) {
			this.connection = connection;
		}
		private SQLException rejectLegacyOperation() {
			SQLException failure = new SQLException("Usa el contexto transaccional; no mezcles operaciones heredadas ni transacciones anidadas");
			rollbackOnly = failure;
			return failure;
		}
		private void checkActive() throws SQLException {
			if (!active || Thread.currentThread() != owner) {
				SQLException failure = new SQLException("El contexto transaccional ha terminado o se usa desde otro hilo");
				rollbackOnly = failure;
				throw failure;
			}
			if (rollbackOnly != null) {
				throw rollbackOnly;
			}
		}
		@Override
		public <T extends DatabaseTable> T create(Class<T> type, Object key) throws SQLException {
			checkActive();
			try {
				Object factoryKey = TableDatabaseMultiKeys.class.isAssignableFrom(type) && key instanceof Number ? Long.valueOf(new BigDecimal(key.toString()).longValueExact()) : key;
				T table = type.cast(newTable(type, factoryKey));
				if (table instanceof TableDatabaseMultiKeys) {
					((TableDatabaseMultiKeys) table).buildKey(((Number) key).longValue());
				}
				table.setLoaded();
				table.setHasData(false);
				return table;
			} catch (Exception failure) {
				rollbackOnly = new SQLException("No se pudo crear la instancia transaccional", failure);
				throw rollbackOnly;
			}
		}
		@Override
		public <T extends DatabaseTable> T find(Class<T> type, Object key) throws SQLException {
			checkActive();
			try {
				T table = create(type, key);
				Object canonicalKey = table.keySerialize();
				for (PendingWrite pending : writeOrder) {
					if (pending.written && type.isInstance(pending.table) && Objects.equals(pending.table.keySerialize(), canonicalKey)) {
						return type.cast(pending.table);
					}
				}
				for (DatabaseTable cached : loaded) {
					if (type.isInstance(cached) && Objects.equals(cached.keySerialize(), canonicalKey)) {
						return type.cast(cached);
					}
				}
				HashMap<String, Object> filters = new HashMap<String, Object>();
				filters.put(table.getKeyName(), canonicalKey);
				try (PreparedStatement statement = prepareStatement("*", table, filters, connection, 1, 0, ""); ResultSet result = statement.executeQuery()) {
					if (!result.next()) {
						return null;
					}
					if (table instanceof TableDatabaseMultiKeys) {
						((TableDatabaseMultiKeys) table).load(result, false);
					} else {
						table.deserialize(result);
						table.setHasData(true);
						table.reloadLastSave(false);
					}
				}
				loaded.add(table);
				return table;
			} catch (Exception failure) {
				rollbackOnly = new SQLException("No se pudo leer el registro transaccional", failure);
				throw rollbackOnly;
			}
		}
		@Override
		public void save(DatabaseTable table) throws SQLException {
			save(table, false);
		}
		@Override
		public void save(DatabaseTable table, boolean ignoreOnSave) throws SQLException {
			write(table, false, false, ignoreOnSave);
		}
		@Override
		public void insert(DatabaseTable table) throws SQLException {
			write(table, true, false, false);
		}
		@Override
		public boolean insertIfAbsent(DatabaseTable table) throws SQLException {
			return write(table, true, true, false);
		}
		private boolean write(DatabaseTable table, boolean insertOnly, boolean ifAbsent, boolean ignoreOnSave) throws SQLException {
			checkActive();
			try {
				if (table == null || table.getDatabase() != Database.this || !table.isLoaded() || !isRegisteredModel(table) || !table.hasPrimaryKey()) {
					throw new SQLException("La tabla cargada debe pertenecer a esta base y tener clave primaria");
				}
				if (!DatabaseAPI.getInstance().getDatabaseManager().saveable(getDatabaseName()) || noSaveTable.contains(table.getTableName())) {
					throw new SQLException("El guardado está deshabilitado para esta tabla");
				}
				for (PendingWrite other : writeOrder) {
					if (other.table != table && other.table.getTableName().equals(table.getTableName()) && Objects.equals(other.table.keySerialize(), table.keySerialize()) && !(table.isAutoIncrement() && Long.valueOf(0).equals(table.keySerialize()))) {
						if (ifAbsent && other.written) {
							return false;
						}
						throw new SQLException("Usa una sola instancia por registro dentro de la transacción");
					}
				}
				PendingWrite pending = writes.get(table);
				if (pending == null) {
					synchronized (table) {
						if (table.isSaving()) {
							throw new SQLException("La tabla ya tiene un guardado en curso");
						}
						pending = new PendingWrite(table);
						writes.put(table, pending);
						writeOrder.add(pending);
						table.setSaving(true);
					}
				} else if (!Objects.equals(pending.written ? pending.writtenKey : pending.originalKey, table.keySerialize())) {
					throw new SQLException("No se puede cambiar la clave de un registro durante la transacción");
				}
				boolean generated = table instanceof TableDatabaseMultiKeys && table.isAutoIncrement() && Long.valueOf(0).equals(table.keySerialize());
				boolean exists = !generated && rowExists(connection, table);
				if (insertOnly && exists) {
					if (ifAbsent) {
						return false;
					}
					throw new SQLException("El registro ya existe: la inserción no sobrescribe datos");
				}
				if (!insertOnly && table.hasData() && !exists) {
					throw new SQLException("No se puede actualizar un registro que ya no existe");
				}
				HashMap<String, Object> data = new HashMap<String, Object>(table.serialize(new HashMap<String, Object>()));
				try (PreparedStatement statement = exists ? update(connection, table, data) : Database.this.insert(connection, table, data, generated)) {
					int affected = statement.executeUpdate();
					if (affected != 1 && !(exists && affected == 0 && rowExists(connection, table))) {
						throw new SQLException("No se confirmó la escritura del registro");
					}
					if (generated) {
						try (ResultSet keys = statement.getGeneratedKeys()) {
							if (!keys.next() || keys.getLong(1) == 0) {
								throw new SQLException("No se pudo obtener la clave del registro");
							}
							((TableDatabaseMultiKeys) table).buildKey(keys.getLong(1));
						}
					}
				}
				pending.data = data;
				pending.writtenKey = table.keySerialize();
				pending.written = true;
				pending.notifySave = pending.notifySave || !ignoreOnSave;
				return true;
			} catch (Exception failure) {
				rollbackOnly = new SQLException("No se pudo guardar el registro transaccional", failure);
				throw rollbackOnly;
			}
		}
		@Override
		public <T extends DatabaseTable> List<T> findAll(Class<T> type, HashMap<String, Object> filters, int limit, int offset, String orderBy) throws SQLException {
			checkActive();
			try {
				DatabaseTable prototype = newTable(type, null);
				List<T> values = new ArrayList<T>();
				try (PreparedStatement query = prepareStatement("*", prototype, copyKeys(filters), connection, limit, offset, orderBy); ResultSet rows = query.executeQuery()) {
					while (rows.next()) {
						Object key = prototype instanceof TableDatabaseMultiKeys ? Long.valueOf(rows.getLong(prototype.getKeyName())) : rows.getObject(prototype.getKeyName());
						T table = create(type, key);
						if (table instanceof TableDatabaseMultiKeys) {
							((TableDatabaseMultiKeys) table).load(rows, false);
						} else {
							table.deserialize(rows);
							table.setHasData(true);
							table.reloadLastSave(false);
						}
						T existing = null;
						for (DatabaseTable cached : loaded) {
							if (type.isInstance(cached) && Objects.equals(cached.keySerialize(), key)) {
								existing = type.cast(cached);
								break;
							}
						}
						for (PendingWrite pending : writeOrder) {
							if (pending.written && type.isInstance(pending.table) && Objects.equals(pending.table.keySerialize(), key)) {
								existing = type.cast(pending.table);
								break;
							}
						}
						if (existing == null) {
							loaded.add(table);
						}
						values.add(existing == null ? table : existing);
					}
				}
				return values;
			} catch (Exception failure) {
				rollbackOnly = new SQLException("No se pudo consultar la selección transaccional", failure);
				throw rollbackOnly;
			}
		}
		@Override
		public <T> T sql(SqlWork<T> work) throws Exception {
			checkActive();
			try (ScopedSqlConnection access = new ScopedSqlConnection(connection, new ScopedSqlConnection.Guard() {
				@Override
				public void check() throws SQLException {
					checkActive();
				}
				@Override
				public void failed(SQLException failure) {
					rollbackOnly = failure;
				}
			})) {
				return work.execute(access.getFacade());
			} catch (Exception failure) {
				rollbackOnly = new SQLException("Falló el trabajo SQL de la transacción", failure);
				throw failure;
			}
		}
		@Override
		public void afterCommit(TransactionAction action) throws SQLException {
			checkActive();
			if (action == null) {
				rollbackOnly = new SQLException("La acción posterior al commit es obligatoria");
				throw rollbackOnly;
			}
			actions.add(action);
		}
		private void checkReadyToCommit() throws SQLException {
			checkActive();
			for (PendingWrite pending : writeOrder) {
				if (pending.written && (!Objects.equals(pending.writtenKey, pending.table.keySerialize()) || !pending.data.equals(pending.table.serialize(new HashMap<String, Object>())))) {
					throw new SQLException("Hay cambios posteriores al último guardado de la transacción");
				}
			}
		}
		private void finish(boolean committed, boolean rolledBack, List<Exception> warnings) {
			for (PendingWrite pending : writeOrder) {
				try {
					if (committed && pending.written) {
						pending.table.setHasData(true);
						pending.table.reloadLastSave(pending.data, false);
					} else if (rolledBack && pending.table instanceof TableDatabaseMultiKeys) {
						((TableDatabaseMultiKeys) pending.table).buildKey(((Number) pending.originalKey).longValue());
					} else if (!committed && !rolledBack) {
						pending.table.invalidatePersistence();
					}
				} catch (RuntimeException failure) {
					pending.table.invalidatePersistence();
					transactionError = new SQLException("No se pudo reconciliar el estado del objeto transaccional", failure);
					warnings.add(failure);
				} finally {
					pending.table.setSaving(false);
				}
			}
		}
		private void publish(List<Exception> warnings) {
			for (PendingWrite pending : writeOrder) {
				if (pending.written && pending.notifySave && pending.table.isLoaded()) {
					try {
						pending.table.onSave();
					} catch (Exception failure) {
						warnings.add(failure);
					}
				}
			}
			for (TransactionAction action : actions) {
				try {
					action.execute();
				} catch (Exception failure) {
					warnings.add(failure);
				}
			}
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
