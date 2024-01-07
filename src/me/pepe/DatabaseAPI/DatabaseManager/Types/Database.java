package me.pepe.DatabaseAPI.DatabaseManager.Types;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import me.pepe.DatabaseAPI.DatabaseManager.Types.Player.Simple.SimplePlayerDatabaseTable;
import me.pepe.DatabaseAPI.Utils.Callback;
import me.pepe.DatabaseAPI.Utils.DatabaseConfiguration;
import me.pepe.DatabaseAPI.Utils.MySQLConnection;

public abstract class Database {
	private String name;
	private boolean temporaly = false;
	private HashMap<Class<? extends DatabaseTable>, DatabaseTableInstance> tableInstances = new HashMap<Class<? extends DatabaseTable>, DatabaseTableInstance>();
	private boolean obligatorySQLite = false;
	private ExecutorService queue = Executors.newSingleThreadExecutor();
	private List<String> noSaveTable = new ArrayList<String>();
	private Connection sqlConnection;
	private MySQLConnection mysqlConnection;
	public Database(String name) {
		this(name, false, false);
	}
	public Database(String name, boolean obligatorySQLite, boolean temporaly) {
		this.name = name;
		this.obligatorySQLite = temporaly ? true : obligatorySQLite;
		this.temporaly = temporaly;
		DatabaseConfiguration config = DatabaseAPI.getInstance().getConfiguration();
		if (config.isSQL() || isObligatorySQLite()) {
			File db = new File(config.getDataFolder(), "Databases/" + (temporaly ? "Temp/" : "") + name + ".db");
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
	public ExecutorService getQueue() {
		return queue;
	}
	public boolean isObligatorySQLite() {
		return obligatorySQLite;
	}
	public boolean isSQLite() {
		return obligatorySQLite || DatabaseAPI.getInstance().getConfiguration().isSQL();
	}
	public void getConnection(Callback<Connection> callback) {
		if (DatabaseAPI.getInstance().getConfiguration().isSQL() || isObligatorySQLite()) {
			callback.done(sqlConnection, null);
		} else {
			mysqlConnection.getConnection(callback);
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
	public void getAllTables(Class<? extends DatabaseTable> clase, Callback<DatabaseTable> callback, Callback<Boolean> onFinish) {
		if (tableInstances.containsKey(clase)) {
			DatabaseTable table = tableInstances.get(clase).newInstance(null);
			if (table instanceof TableDatabaseMultiKeys) {
				TableDatabaseMultiKeys db = (TableDatabaseMultiKeys) tableInstances.get(clase).newInstance(null);
				getConnection(new Callback<Connection>() {
					@Override
					public void done(Connection connection, Exception ex) {
						if (ex == null) {
							try {
								String select = "SELECT * FROM " + db.getTableName();
								PreparedStatement statement = connection.prepareStatement(select);
								ResultSet result = statement.executeQuery();
								if (DatabaseAPI.getInstance().getConfiguration().isSQL() || isObligatorySQLite()) { // si es sql suma el size y al finalizar si es 0 se entiende que no hay entries
									int resultSize = 0;
									while (result.next()) {
										resultSize++;
										TableDatabaseMultiKeys returnDB = (TableDatabaseMultiKeys) tableInstances.get(clase).newInstance(result.getObject(db.getKeyName()));
										returnDB.load(result);
										callback.done(returnDB, null);
										if (resultSize == 0) {
											if (onFinish != null) {
												onFinish.done(resultSize != 0, null);
											}
											break;
										}
									}
									if (onFinish != null) {
										onFinish.done(resultSize != 0, null);
									}
								} else { // aqui coge el ultimo size y lo va restando
									result.last();
									int resultSize = result.getRow();
									result.beforeFirst();
									if (resultSize == 0) {
										if (onFinish != null) {
											onFinish.done(resultSize != 0, null);
										}
									}
									while (result.next()) {
										resultSize--;
										TableDatabaseMultiKeys returnDB = (TableDatabaseMultiKeys) tableInstances.get(clase).newInstance(result.getObject(db.getKeyName()));
										returnDB.load(result);
										callback.done(returnDB, null);
										if (resultSize == 0) {
											if (onFinish != null) {
												onFinish.done(resultSize != 0, null);
											}
											break;
										}
									}
								}
								result.close();
								statement.close();
							} catch (SQLException ex1) {
								ex1.printStackTrace();
							}
						} else {
							ex.printStackTrace();
						}
					}
				});
			} else {
				getConnection(new Callback<Connection>() {
					@Override
					public void done(Connection connection, Exception exception) {
						try {
							String select = "SELECT * FROM " + table.getTableName();
							PreparedStatement statement = connection.prepareStatement(select);
							ResultSet resultSet = statement.executeQuery();
							int resultSize = 0;
							while (resultSet.next()) {
								resultSize++;
								DatabaseTable returnDB = (DatabaseTable) tableInstances.get(clase).newInstance(resultSet.getObject(table.getKeyName()));
								returnDB.setHasData(true);
								returnDB.setLoaded();
								returnDB.deserialize(resultSet);
								callback.done(returnDB, null);
								if (resultSize == 0) {
									if (onFinish != null) {
										onFinish.done(resultSize != 0, null);
									}
									break;
								}
							}
							if (onFinish != null) {
								onFinish.done(resultSize != 0, null);
							}
							resultSet.close();
							statement.close();
							if (callback != null) {
								callback.done(table, null);
							}
						} catch (Exception e) {
							System.err.println("[Database]: Ha ocurrido un error al cargar la database " + name + " el error ha sido enviado por la callback");
							String errorMessage;
							errorMessage = e + "\n";
							for (StackTraceElement stack : e.getStackTrace()) {
								errorMessage = errorMessage + "        at " + stack.getClassName() + "." + stack.getMethodName() + " (" + stack.getFileName() + ":" + stack.getLineNumber() + ")\n";
							}
							System.err.println("Error: " + errorMessage);
							callback.done(table, e);
						}
					}				
				});
			}
		} else {
			try {
				throw new NullPointerException("This database " + clase.getName() + " is not registred in database " + this.getClass().getSimpleName());
			} catch (NullPointerException ex) {
				ex.printStackTrace();
			}
		}
	}
	public void getTable(Class<? extends DatabaseTable> clase, Object key, Callback<DatabaseTable> callback) {
		getTable(clase, key, true, callback);
	}
	public void getTable(Class<? extends DatabaseTable> clase, Object key, boolean async, Callback<DatabaseTable> callback) {
		if (tableInstances.containsKey(clase)) {
			DatabaseTable table = tableInstances.get(clase).newInstance(key);
			if (table instanceof TableDatabaseMultiKeys) {
				TableDatabaseMultiKeys mtdb = (TableDatabaseMultiKeys) table;
				mtdb.buildKey((Integer) key);
				load(async, mtdb, callback);
			} else {
				load(async, table, callback);
			}
		} else {
			try {
				throw new NullPointerException("This database " + clase.getName() + " is not registred in database " + this.getClass().getSimpleName());
			} catch (NullPointerException ex) {
				ex.printStackTrace();
			}
		}
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> clase, HashMap<String, Object> keys, Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFinded) {
		getTable(clase, keys, true, callback, notFinded, null);
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> clase, HashMap<String, Object> keys, boolean async, Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFinded) {
		getTable(clase, keys, async, callback, notFinded, null);
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> clase, HashMap<String, Object> keys, Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFinded, Callback<Boolean> onFinish) {
		getTable(clase, keys, true, callback, notFinded, onFinish);
	}
	public void getTable(Class<? extends TableDatabaseMultiKeys> clase, HashMap<String, Object> keys, boolean async, Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFinded, Callback<Boolean> onFinish) {
		if (tableInstances.containsKey(clase)) {
			DatabaseTable newTable = tableInstances.get(clase).newInstance(null);
			if (newTable instanceof TableDatabaseMultiKeys) {
				TableDatabaseMultiKeys db = (TableDatabaseMultiKeys) tableInstances.get(clase).newInstance(null);
				HashMap<String, Object> map = db.serialize(new HashMap<String, Object>());
				List<String> noKeys = new ArrayList<String>();
				for (Entry<String, Object> key : keys.entrySet()) {
					if (!map.containsKey(key.getKey().replace("!", ""))) {
						if (!key.getKey().replace("!", "").equals(db.getKeyName())) {
							noKeys.add(key.getKey());
						}
					}
				}
				if (noKeys.isEmpty()) {
					getConnection(new Callback<Connection>() {
						@Override
						public void done(Connection connection, Exception ex) {
							if (ex == null) {
								try {
									String select = "SELECT * FROM " + db.getTableName() + " WHERE ";
									for (Entry<String, Object> key : keys.entrySet()) {
										if (select.equals("SELECT * FROM " + db.getTableName() + " WHERE ")) {
											if (key.getKey().contains("!")) {
												select = select + key.getKey().replace("!", "") + " !=?";
											} else {
												select = select + key.getKey() + " =?";
											}
										} else {
											if (key.getKey().contains("!")) {
												select = select + " AND " + key.getKey().replace("!", "") + " !=?";
											} else {
												select = select + " AND " + key.getKey() + " =?";
											}
										}
									}
									PreparedStatement statement = connection.prepareStatement(select);
									int statementSize = 1;
									for (Entry<String, Object> key : keys.entrySet()) {
										saveInStatement(statement, key.getValue(), statementSize++);
									}
									ResultSet result = statement.executeQuery();
									if (DatabaseAPI.getInstance().getConfiguration().isSQL() || isObligatorySQLite()) { // si es sql suma el size y al finalizar si es 0 se entiende que no hay entries
										int resultSize = 0;
										while (result.next()) {
											resultSize++;
											TableDatabaseMultiKeys returnDB = (TableDatabaseMultiKeys) tableInstances.get(clase).newInstance(result.getObject(db.getKeyName()));
											returnDB.load(result);
											callback.done(returnDB, null);
											if (resultSize == 0) {
												if (onFinish != null) {
													onFinish.done(resultSize != 0, null);
												}
												break;
											}
										}
										if (resultSize == 0) {
											notFinded.done(keys, null);
										}
										if (onFinish != null) {
											onFinish.done(resultSize != 0, null);
										}
									} else { // aqui coge el ultimo size y lo va restando
										result.last();
										int resultSize = result.getRow();
										result.beforeFirst();
										if (resultSize == 0) {
											notFinded.done(keys, null);
											if (onFinish != null) {
												onFinish.done(resultSize != 0, null);
											}
										}
										while (result.next()) {
											resultSize--;
											TableDatabaseMultiKeys returnDB = (TableDatabaseMultiKeys) tableInstances.get(clase).newInstance(result.getObject(db.getKeyName()));
											returnDB.load(result);
											callback.done(returnDB, null);
											if (resultSize == 0) {
												if (onFinish != null) {
													onFinish.done(resultSize != 0, null);
												}
												break;
											}
										}
									}
									result.close();
									statement.close();
								} catch (SQLException ex1) {
									ex1.printStackTrace();
								}
							} else {
								ex.printStackTrace();
							}
						}
					});
				} else {
					System.err.println("[TableDatabaseMultiKey]: No se ha encontrado las siguientes keys en la TableDatabaseMultiKey de " + newTable.getTableName() + ": " + noKeys);
				}
			} else {
				System.err.println("[TableDatabaseMultiKey]: La tabla " + newTable.getTableName() + " no es una TableDatabaseMultiKey...");
			}
		} else {
			System.err.println("This database " + clase.getName() + " is not registred");
		}
	}
	public void getTableMultiKeysMultiEntrys(Class<? extends TableDatabaseMultiKeys> clase, HashMap<String, Object> keys, boolean async, Callback<TableDatabaseMultiKeys> callback, Callback<HashMap<String, Object>> notFinded) {
		if (tableInstances.containsKey(clase)) {
			DatabaseTable newTable = tableInstances.get(clase).newInstance(null);
			if (newTable instanceof TableDatabaseMultiKeys) {
				TableDatabaseMultiKeys db = (TableDatabaseMultiKeys) tableInstances.get(clase).newInstance(null);
				HashMap<String, Object> map = db.serialize(new HashMap<String, Object>());
				List<String> noKeys = new ArrayList<String>();
				for (Entry<String, Object> key : keys.entrySet()) {
					if (!map.containsKey(key.getKey().replace("!", ""))) {
						if (!key.getKey().replace("!", "").equals(db.getKeyName())) {
							noKeys.add(key.getKey());
						}
					}
				}
				if (noKeys.isEmpty()) {
					getConnection(new Callback<Connection>() {
						@Override
						public void done(Connection connection, Exception exception) {
							if (exception == null) {
								try {
									String select = "SELECT * FROM " + db.getTableName() + " WHERE ";
									for (Entry<String, Object> key : keys.entrySet()) {
										if (select.equals("SELECT * FROM " + db.getTableName() + " WHERE ")) {
											if (key.getKey().contains("!")) {
												select = select + key.getKey().replace("!", "") + " !=?";
											} else {
												select = select + key.getKey() + " =?";
											}
										} else {
											if (key.getKey().contains("!")) {
												select = select + " AND " + key.getKey().replace("!", "") + " !=?";
											} else {
												select = select + " AND " + key.getKey() + " =?";
											}
										}
									}
									PreparedStatement statement = connection.prepareStatement(select);
									int statementSize = 1;
									for (Entry<String, Object> key : keys.entrySet()) {
										saveInStatement(statement, key.getValue(), statementSize++);
									}
									ResultSet result = statement.executeQuery();
									int size = 0;
									while (result.next()) { // aqui no es necesario comprobar eso por que no tiene callback de on finish
										size++;
										TableDatabaseMultiKeys returnDB = (TableDatabaseMultiKeys) tableInstances.get(clase).newInstance(result.getObject(db.getKeyName()));
										returnDB.load(result);
										callback.done(returnDB, null);
									}
									if (size == 0) {
										notFinded.done(keys, null);
									}
									result.close();
									statement.close();
								} catch(SQLException ex) {
									ex.printStackTrace();
								}
							} else {
								exception.printStackTrace();
							}
						}							
					});
				} else {
					System.err.println("[TableDatabaseMultiKey]: No se ha encontrado las siguientes keys en la TableDatabaseMultiKey de " + newTable.getTableName() + ": " + noKeys);
				}
			} else {
				System.err.println("[TableDatabaseMultiKey]: La base de datos " + newTable.getTableName() + " no es una TableDatabaseMultiKey...");
			}
		} else {
			System.err.println("This database " + clase.getName() + " is not registred");
		}
	}
	public void hasTableMultiKeysMultiEntrys(Class<? extends TableDatabaseMultiKeys> clase, HashMap<String, Object> keys, boolean async, Callback<Boolean> callback) {
		if (tableInstances.containsKey(clase)) {
			DatabaseTable newTable = tableInstances.get(clase).newInstance(null);
			if (newTable instanceof TableDatabaseMultiKeys) {
				TableDatabaseMultiKeys db = (TableDatabaseMultiKeys) tableInstances.get(clase).newInstance(null);
				HashMap<String, Object> map = db.serialize(new HashMap<String, Object>());
				List<String> noKeys = new ArrayList<String>();
				for (Entry<String, Object> key : keys.entrySet()) {
					if (!map.containsKey(key.getKey().replace("!", ""))) {
						if (!key.getKey().replace("!", "").equals(db.getKeyName())) {
							noKeys.add(key.getKey());
						}
					}
				}
				if (noKeys.isEmpty()) {
					getConnection(new Callback<Connection>() {
						@Override
						public void done(Connection connection, Exception exception) {
							if (exception == null) {
								try {
									String select = "SELECT * FROM " + db.getTableName() + " WHERE ";
									for (Entry<String, Object> key : keys.entrySet()) {
										if (select.equals("SELECT * FROM " + db.getTableName() + " WHERE ")) {
											if (key.getKey().contains("!")) {
												select = select + key.getKey().replace("!", "") + " !=?";
											} else {
												select = select + key.getKey() + " =?";
											}
										} else {
											if (key.getKey().contains("!")) {
												select = select + " AND " + key.getKey().replace("!", "") + " !=?";
											} else {
												select = select + " AND " + key.getKey() + " =?";
											}
										}
									}
									select = select + " LIMIT 1";
									PreparedStatement statement = connection.prepareStatement(select);
									int statementSize = 1;
									for (Entry<String, Object> key : keys.entrySet()) {
										saveInStatement(statement, key.getValue(), statementSize++);
									}
									ResultSet result = statement.executeQuery();
									callback.done(result.next(), exception);
									result.close();
									statement.close();
								} catch(SQLException ex) {
									ex.printStackTrace();
								}
							} else {
								exception.printStackTrace();
							}
						}							
					});
				} else {
					System.err.println("[TableDatabaseMultiKey]: No se ha encontrado las siguientes keys en la TableDatabaseMultiKey de " + newTable.getTableName() + ": " + noKeys);
				}
			} else {
				System.err.println("[TableDatabaseMultiKey]: La base de datos " + newTable.getTableName() + " no es una TableDatabaseMultiKey...");
			}
		} else {
			System.err.println("This database " + clase.getName() + " is not registred");
		}
	}
	public void load(boolean async, DatabaseTable table) {
		if (!table.isLoaded()) {
			if (async) {
				queue.submit(new Runnable() {
					@Override
					public void run() {
						load(null, table);
					}
				});
			} else {
				load(null, table);
			}
		}
	}
	public void load(boolean async, DatabaseTable table, Callback<DatabaseTable> callback) {
		if (!table.isLoaded()) {
			if (async) {
				queue.submit(new Runnable() {
					@Override
					public void run() {
						load(callback, table);
					}
				});
			} else {
				load(callback, table);
			}
		}
	}
	private void load(Callback<DatabaseTable> callback, DatabaseTable table) {
		if (table instanceof MultiPlayerDatabaseTable) {
			MultiPlayerDatabaseTable multiTable = (MultiPlayerDatabaseTable) table;
			multiTable.load(false, callback);
		} else {
			getConnection(new Callback<Connection>() {
				@Override
				public void done(Connection connection, Exception exception) {
					try {
						String select = "SELECT * FROM " + table.getTableName() + " WHERE " + table.getKeyName() + " = ?";
						PreparedStatement statement = connection.prepareStatement(select);
						saveInStatement(statement, table.keySerialize(), 1);
						ResultSet resultSet = statement.executeQuery();
						table.setHasData(resultSet.next());
						if (table.hasData()) {
							table.deserialize(resultSet);
						}
						table.setLoaded();
						if (!table.hasData()) {
							if (!(table instanceof SimplePlayerDatabaseTable)) {
								table.setSaved(false);
							}
						}
						table.onLoad(table.hasData());
						resultSet.close();
						statement.close();
						if (callback != null) {
							callback.done(table, null);
						}
					} catch (Exception e) {
						System.err.println("[Database]: Ha ocurrido un error al cargar la database " + name + " el error ha sido enviado por la callback");
						String errorMessage;
						errorMessage = e + "\n";
						for (StackTraceElement stack : e.getStackTrace()) {
							errorMessage = errorMessage + "        at " + stack.getClassName() + "." + stack.getMethodName() + " (" + stack.getFileName() + ":" + stack.getLineNumber() + ")\n";
						}
						System.err.println("Error: " + errorMessage);
						callback.done(table, e);
					}
				}				
			});
		}
	}
	public void save(boolean async, DatabaseTable table) {
		if (DatabaseAPI.getInstance().getDatabaseManager().saveable(getDatabaseName()) && table.isLoaded() && !table.isSaved()) {
			if (async) {
				queue.submit(new Runnable() {
					@Override
					public void run() {
						save(table);
					}
				});
			} else {
				save(table);
			}
		}
	}
	private void save(DatabaseTable table) {
		getConnection(new Callback<Connection>() {
			@Override
			public void done(Connection connection, Exception exception) {
				try {
					PreparedStatement statement;
					if (table.hasData()) {
						statement = update(connection, table);
					} else {
						if (table.isAutoIncrement() && table.getKeyType().equals(DatabaseKeyType.INT) && table.keySerialize() instanceof Integer && ((int)table.keySerialize()) == -0 && table.getClass().getSuperclass().equals(TableDatabaseMultiKeys.class)) {
							String find = "SELECT * FROM " + table.getTableName() + " ORDER BY " + table.getKeyName() + " DESC LIMIT 1";
							PreparedStatement findKeyStatement = connection.prepareStatement(find);
							ResultSet result = findKeyStatement.executeQuery();
							boolean has = result.next();
							if (has) {
								TableDatabaseMultiKeys mKey = (TableDatabaseMultiKeys) table; 
								mKey.buildKey(result.getInt(table.getKeyName()) + 1);
								findKeyStatement.close();
							} else {
								TableDatabaseMultiKeys mKey = (TableDatabaseMultiKeys) table; 
								mKey.buildKey(1);
								findKeyStatement.close();
							}
						} else {
							String find = "SELECT * FROM " + table.getTableName() + " WHERE " + table.getKeyName() + " = ? LIMIT 1;";
							PreparedStatement findKeyStatement = connection.prepareStatement(find);
							saveInStatement(findKeyStatement, table.keySerialize(), 1);
							ResultSet result = findKeyStatement.executeQuery();
							boolean has = result.next();
							table.setHasData(has);
						}
						if (table.hasData()) {
							statement = update(connection, table);
						} else {
							statement = insert(connection, table);
						}
					}
					statement.executeUpdate();
					statement.close();
					table.setSaved(true);
				} catch(SQLException ex) {
					ex.printStackTrace();
				}
			}
		});
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
		if (tableInstances.containsKey(tableClass)) {
			System.err.println("[Database]: &cLa tabla " + table.getTableName() + " ya estaba registrada...");
		} else {
			if (!saveDatabase) {
				noSaveTable.add(table.getTableName());
			}
			getConnection(new Callback<Connection>() {
				@Override
				public void done(Connection connection, Exception exception) {
					try {
						String createTableStatement = "CREATE TABLE IF NOT EXISTS " + table.getTableName() + " (" + table.getKeyName() + " " + table.getKeyType().getStatementName() + (table instanceof MultiPlayerDatabaseTable ? ", data_name VARCHAR(50) NOT NULL" : (!isSQLite() && table.isAutoIncrement() ? " AUTO_INCREMENT PRIMARY KEY" : (table.hasPrimaryKey() ? " PRIMARY KEY" : " NOT NULL")));
						HashMap<String, Object> saveMap = table.serialize(new HashMap<String, Object>());
						for (Entry<String, Object> save : saveMap.entrySet()) {
							createTableStatement = createTableStatement + ", " + save.getKey() + " " + getStatementName(save.getValue()) + " NOT NULL";
						}
						createTableStatement = createTableStatement + ");";
						PreparedStatement statement = connection.prepareStatement(createTableStatement);
						System.out.println("[Database]: &aLa tabla de " + table.getTableName() + " se ha cargado correctamente en la base de datos " + getDatabaseName() + (isSQLite() ? "sqlite" : "mysql"));
						statement.executeUpdate();
						tableInstances.put(tableClass, tableInstance);
						System.out.println("[Database]: &aLa tabla de " + table.getTableName() + " se ha registrado exitosamente en la base de datos " + getDatabaseName());
					} catch(SQLException ex) {
						ex.printStackTrace();
					}
				}			
			});
		}
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
	private PreparedStatement insert(Connection connection, DatabaseTable table) throws SQLException {
		String insertStatement = "INSERT INTO " + table.getTableName() + " (" + table.getKeyName();
		HashMap<String, Object> saveMap = table.serialize(new HashMap<String, Object>());
		for (Entry<String, Object> save : saveMap.entrySet()) {
			insertStatement = insertStatement + ", " + save.getKey();
		}
		insertStatement = insertStatement + ") VALUES (?";
		for (int i = saveMap.size(); i > 0; i--) {
			insertStatement = insertStatement + ", ?";
		}
		insertStatement = insertStatement + ")";
		PreparedStatement preparedStatement = connection.prepareStatement(insertStatement);
		int statementSize = 1;
		saveInStatement(preparedStatement, table.keySerialize(), statementSize++);
		for (Entry<String, Object> save : saveMap.entrySet()) {
			saveInStatement(preparedStatement, save.getValue(), statementSize++);
		}
		//System.out.println(insertStatement);
		return preparedStatement;
	}
	private PreparedStatement update(Connection connection, DatabaseTable table) throws SQLException {
		HashMap<String, Object> saveMap = table.serialize(new HashMap<String, Object>());
		String saveStatement = "UPDATE " + table.getTableName() + " SET";
		for (Entry<String, Object> save : saveMap.entrySet()) {
			saveStatement = saveStatement + " " + save.getKey() + " = ?,";
		}
		saveStatement = saveStatement.substring(0, saveStatement.length() - 1) + " WHERE " + table.getKeyName() + " = ?";
		PreparedStatement statement = connection.prepareStatement(saveStatement);
		int statementSize = 1;
		for (Entry<String, Object> save : saveMap.entrySet()) {
			saveInStatement(statement, save.getValue(), statementSize++);
		}
		String className = table.keySerialize().getClass().getSimpleName();
		if (className.equals("Integer")) {
			saveInStatement(statement, Integer.valueOf(table.keySerialize().toString()), statementSize++);
		} else {
			saveInStatement(statement, table.keySerialize().toString(), statementSize++);
		}
		//System.out.println(saveStatement);
		return statement;
	}
	public void remove(boolean async, DatabaseTable table) {
		if (async) {
			queue.submit(new Runnable() {
				@Override
				public void run() {
					remove(table);
				}
			});
		} else {
			remove(table);
		}
	}
	private void remove(DatabaseTable table) {
		getConnection(new Callback<Connection>() {
			@Override
			public void done(Connection connection, Exception exception) {
				try {
					PreparedStatement statement = delete(connection, table);
					statement.executeUpdate();
					statement.close();
					table.setSaved(false);
					table.setHasData(false);
				} catch(SQLException ex) {
					ex.printStackTrace();
				}
			}			
		});
	}
	private PreparedStatement delete(Connection connection, DatabaseTable table) throws SQLException {
		String removeStatement = "DELETE FROM " + table.getTableName() + " WHERE " + table.getKeyName() + " = " + table.keySerialize() + ";";
		PreparedStatement preparedStatement = connection.prepareStatement(removeStatement);
		return preparedStatement;
	}
	public void saveInStatement(PreparedStatement statement, Object o, int statementSize) throws SQLException {
		String className = o.getClass().getSimpleName();
		if (className.equals("Integer")) {
			statement.setInt(statementSize, (Integer) o);
		} else if (className.equals("Double")) {
			statement.setDouble(statementSize, (Double) o);
		} else if (className.equals("Long")) {
			statement.setLong(statementSize, (Long) o);
		} else if (className.equals("Boolean")) {
			statement.setBoolean(statementSize, (Boolean) o);
		} else if (className.equals("String")) {
			statement.setString(statementSize, (String) o);
		} else {
			statement.setString(statementSize, o.toString());
		}
	}
	public String getStatementName(Object o) { 
		String className = o.getClass().getSimpleName();
		if (className.equals("Integer")) {
			return "INTEGER";
		} else if (className.equals("Double")) {
			return "DOUBLE";
		} else if (className.equals("Long")) {
			return "LONG";
		} else if (className.equals("Boolean")) {
			return "BOOLEAN";
		} else {
			return "TEXT";
		}
	}
	public void closeConnection() throws SQLException {
		if (sqlConnection != null) {
			sqlConnection.close();
		} else {
			mysqlConnection.close();
		}
	}
}