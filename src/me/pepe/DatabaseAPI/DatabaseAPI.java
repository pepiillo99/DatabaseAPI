package me.pepe.DatabaseAPI;

import java.util.UUID;

import me.pepe.DatabaseAPI.DatabaseManager.DatabaseManager;
import me.pepe.DatabaseAPI.DatabaseManager.DatabaseTableInstance;
import me.pepe.DatabaseAPI.DatabaseManager.Databases.PlayerDataDatabase;
import me.pepe.DatabaseAPI.DatabaseManager.Databases.ServerDataDatabase;
import me.pepe.DatabaseAPI.DatabaseManager.Identifier.Identifier;
import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;
import me.pepe.DatabaseAPI.Utils.DatabaseConfiguration;

public abstract class DatabaseAPI {
	private static DatabaseAPI instance;
	private DatabaseConfiguration configuration;
	private DatabaseManager dbManager;
	public DatabaseAPI(DatabaseConfiguration configuration) {
		instance = this;
		this.configuration = configuration;
		configuration.getDataFolder().mkdirs();
		this.dbManager = new DatabaseManager(this);
		dbManager.registerDatabase(new PlayerDataDatabase());
		dbManager.getDatabase(PlayerDataDatabase.class).registerTable(this, Identifier.class, new DatabaseTableInstance<Integer>() {
			@Override
			public DatabaseTable newInstance(Integer key) {
				if (key != null) {
					return new Identifier(dbManager.getDatabase(PlayerDataDatabase.class));
				} else {
					return new Identifier(dbManager.getDatabase(PlayerDataDatabase.class));
				}
			}			
		});
		dbManager.registerDatabase(new ServerDataDatabase());
	}
	public abstract boolean isPlayerOnline(UUID uuid);
	public abstract void kickPlayer(UUID uuid, String message);
	public DatabaseConfiguration getConfiguration() {
		return configuration;
	}
	public static DatabaseAPI getInstance() {
		return instance;
	}
	public DatabaseManager getDatabaseManager() {
		return dbManager;
	}
}
