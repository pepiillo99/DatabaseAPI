package me.pepe.DatabaseAPI.DatabaseManager;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.pepe.DatabaseAPI.DatabaseAPI;
import me.pepe.DatabaseAPI.DatabaseManager.Databases.PlayerDataDatabase;
import me.pepe.DatabaseAPI.DatabaseManager.Identifier.Identifier;
import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;
import me.pepe.DatabaseAPI.DatabaseManager.Types.TableDatabaseMultiKeys;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Player.PlayerDatabaseTable;
import me.pepe.DatabaseAPI.Utils.Callback;
import me.pepe.DatabaseAPI.Utils.DatabaseAlreadyLoadedException;
import me.pepe.DatabaseAPI.Utils.DatabaseConfiguration;

import java.util.Map.Entry;

public class DatabaseManager {
	private DatabaseAPI instance;
	private HashMap<UUID, PlayerData> playerDatabases = new HashMap<UUID, PlayerData>();
	private HashMap<Class<? extends Database>, Database> databases = new HashMap<Class<? extends Database>, Database>();
	private Connection mainConnection;
	private ExecutorService loadQueue = Executors.newSingleThreadExecutor();
	private List<String> noSaveDatabase = new ArrayList<String>();
	public DatabaseManager(DatabaseAPI instance) {
		this.instance = instance;
		new File(instance.getConfiguration().getDataFolder(), "Databases").mkdir();
		if (!instance.getConfiguration().isSQL()) {
			try {
				Class.forName("org.mariadb.jdbc.Driver");
				mainConnection = DriverManager.getConnection("jdbc:mariadb://" + instance.getConfiguration().getIP() + ":" + instance.getConfiguration().getPort() + "/mysql?user=" + instance.getConfiguration().getUser() + "&password=" + instance.getConfiguration().getPassword() + "&autoReconnect=true&amp;useUnicode=true;characterEncoding=UTF-8");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	public boolean saveable(String databaseName) {
		return !noSaveDatabase.contains(databaseName);
	}
	public int countLoadedPlayers() {
		return playerDatabases.size();
	}
	public PlayerData getPlayerDatabase(UUID uuid) {
		if (playerDatabases.containsKey(uuid)) {
			return playerDatabases.get(uuid);
		} else {
			return null;
		}
	}
	public PlayerData getPlayerDatabase(int identifier) {
		for (PlayerData pData : playerDatabases.values()) {
			if (pData.getIdentifier().getID() == identifier) {
				return pData;
			}
		}
		return null;
	}
	public boolean isPlayerLoaded(UUID uuid) {
		return playerDatabases.containsKey(uuid);
	}
	public boolean isPlayerLoaded(int identifier) {
		return getPlayerDatabase(identifier) != null;
	}
	public void getIdentifier(UUID uuid, String name, Callback<Identifier> callback, Callback<String> notFindedCallback, boolean async) { // Este metodo si no esta en la database no dejaría entrar al jugador en el aysncplayerloginevent
		HashMap<String, Object> keys = new HashMap<String, Object>();
		keys.put("uuid", uuid);
		keys.put("name", name);
		getDatabase(PlayerDataDatabase.class).getTableMultiKeysMultiEntrys(Identifier.class, keys, async, new Callback<TableDatabaseMultiKeys>() {
			@Override
			public void done(TableDatabaseMultiKeys result, Exception exception) {
				callback.done((Identifier) result, exception);
			}			
		}, new Callback<HashMap<String, Object>>() {
			@Override
			public void done(HashMap<String, Object> result, Exception exception) {
				notFindedCallback.done(name, exception);
			}			
		});
	}
	public void getIdentifier(UUID uuid, Callback<Identifier> callback, Callback<UUID> notFindedCallback, boolean async) {
		HashMap<String, Object> keys = new HashMap<String, Object>();
		keys.put("uuid", uuid);
		getDatabase(PlayerDataDatabase.class).getTableMultiKeysMultiEntrys(Identifier.class, keys, async, new Callback<TableDatabaseMultiKeys>() {
			@Override
			public void done(TableDatabaseMultiKeys result, Exception exception) {
				callback.done((Identifier) result, exception);
			}			
		}, new Callback<HashMap<String, Object>>() {
			@Override
			public void done(HashMap<String, Object> result, Exception exception) {
				notFindedCallback.done(uuid, exception);
			}			
		});
	}
	public void hasIdentifier(String name, Callback<Boolean> callback, boolean async) {
		HashMap<String, Object> keys = new HashMap<String, Object>();
		keys.put("name", name);
		getDatabase(PlayerDataDatabase.class).hasTableMultiKeysMultiEntrys(Identifier.class, keys, async, new Callback<Boolean>() {
			@Override
			public void done(Boolean result, Exception exception) {
				callback.done(result, exception);
			}			
		});
	}
	public void getIdentifier(String name, Callback<Identifier> callback, Callback<String> notFindedCallback, boolean async) {
		HashMap<String, Object> keys = new HashMap<String, Object>();
		keys.put("name", name);
		getDatabase(PlayerDataDatabase.class).getTableMultiKeysMultiEntrys(Identifier.class, keys, async, new Callback<TableDatabaseMultiKeys>() {
			@Override
			public void done(TableDatabaseMultiKeys result, Exception exception) {
				callback.done((Identifier) result, exception);
			}			
		}, new Callback<HashMap<String, Object>>() {
			@Override
			public void done(HashMap<String, Object> result, Exception exception) {
				notFindedCallback.done(name, exception);
			}			
		});
	}
	public void getIdentifier(int id, Callback<Identifier> callback, Callback<Integer> notFindedCallback, boolean async) {
		HashMap<String, Object> keys = new HashMap<String, Object>();
		keys.put("Identifiers", id);
		getDatabase(PlayerDataDatabase.class).getTableMultiKeysMultiEntrys(Identifier.class, keys, async, new Callback<TableDatabaseMultiKeys>() {
			@Override
			public void done(TableDatabaseMultiKeys result, Exception exception) {
				callback.done((Identifier) result, exception);
			}			
		}, new Callback<HashMap<String, Object>>() {
			@Override
			public void done(HashMap<String, Object> result, Exception exception) {
				notFindedCallback.done(id, exception);
			}			
		});
	}
	public void internalLoadPlayer(PlayerData pData, boolean async, Callback<PlayerData> callback) {
		Identifier identifier = pData.getIdentifier();
		if (!pData.isLoaded() && !playerDatabases.containsKey(identifier.getUUID())) {
			playerDatabases.put(identifier.getUUID(), pData);
			loadPlayer(pData, async, callback);
		} else {
			if (playerDatabases.containsKey(identifier.getUUID())) {
				instance.log("Databases", "&cLa data de " + pData.getIdentifier().getName() + " ya está cargada");
				pData.setError(new DatabaseAlreadyLoadedException());
				if (callback != null) {
					callback.done(pData, null);
				}
			} else {
				if (callback != null) {
					callback.done(pData, null);
				}
			}
		}
	}
	public void internalLoadPlayer(UUID uuid, String name, boolean async, Callback<PlayerData> callback) {
		if (!playerDatabases.containsKey(uuid)) {
			if (async) {
				loadQueue.submit(new Runnable() {
					@Override
					public void run() {
						getIdentifier(uuid, name, new Callback<Identifier>() {
							@Override
							public void done(Identifier result, Exception exception) {
								PlayerData pData = new PlayerData(result);
								playerDatabases.put(result.getUUID(), pData);
								loadPlayer(pData, false, callback);
							}
						}, new Callback<String>() {
							@Override
							public void done(String result, Exception exception) {
								if (DatabaseAPI.getInstance().getConfiguration().canGenerateIdentifiers()) {
									instance.log("Identifier", "Jugador no encontrado, generando...");
									getDatabase(PlayerDataDatabase.class).getTable(Identifier.class, -0, new Callback<DatabaseTable>() {
										@Override
										public void done(DatabaseTable result, Exception ex) {
											Identifier identifier = (Identifier) result;
											identifier.setName(name);
											identifier.setUUID(uuid);
											identifier.setSaved(false);
											identifier.save(false);
											instance.log("Identifier", "Jugador no encontrado, creado exitosamente con la ID: " + identifier.getID());
											PlayerData pData = new PlayerData(identifier);
											playerDatabases.put(uuid, pData);
											loadPlayer(pData, false, callback);
										}
									});
								} else {
									callback.done(null, new NullPointerException());
									/**
									 * No carga al jugador porque en teoria no deberia de entrar por no tener el Identifier creado y tener el generate Identifiers desactivado
									 * loadPlayer(pData, false);
									 */
								}
							}							
						}, false);
					}
				});
			} else {
				loadQueue.submit(new Runnable() {
					@Override
					public void run() {
						getIdentifier(uuid, name, new Callback<Identifier>() {
							@Override
							public void done(Identifier result, Exception exception) {
								PlayerData pData = new PlayerData(result);
								playerDatabases.put(result.getUUID(), pData);
								loadPlayer(pData, false, callback);
							}
						}, new Callback<String>() {
							@Override
							public void done(String result, Exception exception) {
								if (DatabaseAPI.getInstance().getConfiguration().canGenerateIdentifiers()) {
									instance.log("Identifier", "Jugador no encontrado, generando...");
									getDatabase(PlayerDataDatabase.class).getTable(Identifier.class, -0, new Callback<DatabaseTable>() {
										@Override
										public void done(DatabaseTable result, Exception ex) {
											Identifier identifier = (Identifier) result;
											identifier.setName(name);
											identifier.setUUID(uuid);
											identifier.save(false);
											instance.log("Identifier", "Jugador no encontrado, creado exitosamente con la ID: " + identifier.getID());
											PlayerData pData = new PlayerData(identifier);
											playerDatabases.put(uuid, pData);
											loadPlayer(pData, false, callback);
										}
									});
								} else {
									callback.done(null, new NullPointerException());
									//loadPlayer(pData, false);
								}
							}							
						}, false);
					}
				});
			}
		} else {
			instance.log("Databases", "&cEl jugador " + name + " ya estaba cargado");
			PlayerData pData = playerDatabases.get(uuid);
			pData.setError(new DatabaseAlreadyLoadedException());
			callback.done(pData, null);
		}
	}
	public void loadPlayer(PlayerData pData, boolean async) {
		loadPlayer(pData, async, null);
	}
	public void loadPlayer(PlayerData pData, boolean async, Callback<PlayerData> callback) {
		if (!pData.isLoaded()) { // Si no esta vacia no carga todos las base de datos, esto esta hecho para cuando necesitamos cargar una playerdata para coger una sola información (ej: skin del jugador)
			for (Entry<Class<? extends Database>, Database> databaseEntry : databases.entrySet()) {
				databaseEntry.getValue().internalLoadPlayer(pData);
			}
			if (pData.getDatas().isEmpty()) {
				callback.done(pData, null);
			} else {
				for (PlayerDatabaseTable pDb : pData.getDatas()) {
					pDb.getDatabase().load(async, pDb, new Callback<DatabaseTable>() {
						@Override
						public void done(DatabaseTable result, Exception exception) {
							if (exception == null) {
								if (pData.getDatas().size() == pData.countLoadedDatas()) {
									if (callback != null) {
										callback.done(pData, null);
									}
								}
							} else {
								pData.setError(exception);
							}
						}
					});
				}
			}
		} else {
			instance.log("Databases", "&cLa data de " + pData.getIdentifier().getName() + " ya esta cargada");
		}
	}
	public void unloadPlayer(UUID uuid, boolean async, boolean kick) {
		if (playerDatabases.containsKey(uuid)) {
			if (kick && instance.isPlayerOnline(uuid)) {
				instance.kickPlayer(uuid, "&cTu información se ha descargado, por favor informe al staff");
			}
			playerDatabases.get(uuid).saveAll(async);
			playerDatabases.remove(uuid);
		}
	}
	public Database getDatabase(Class<? extends Database> dbClass) {
		if (databases.containsKey(dbClass)) {
			return databases.get(dbClass);
		} else {
			return null;
		}
	}
	public void registerDatabase(Database database) {
		if (!databases.containsKey(database.getClass())) {
			createDatabase(database);
			databases.put(database.getClass(), database);
		} else {
			instance.log("DatabaseManager", "La base de dator que se ha intentado registrar ya estaba registrada '" + database.getClass().getSimpleName() + "'");
		}
	}
	protected void createDatabase(Database database) {
		if (!database.isSQLite()) {
			Statement statement;
			try {
				statement = mainConnection.createStatement();
				statement.executeUpdate("CREATE DATABASE IF NOT EXISTS " + database.getDatabaseName());
				statement.close();
				database.setConnectionReady();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else {
			DatabaseConfiguration config = DatabaseAPI.getInstance().getConfiguration();
			File db = new File(config.getDataFolder(), "Databases/" + database.getDatabaseName() + ".db");
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
		}
	}
	public void unloadDatabases() throws SQLException {
		for (Database dbs : databases.values()) {
			dbs.closeConnection();
		}
	}
}
