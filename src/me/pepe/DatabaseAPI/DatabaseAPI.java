package me.pepe.DatabaseAPI;

import java.util.UUID;
import java.sql.SQLException;

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
		if (configuration.isSchemaMigrationsEnabled() && !configuration.isSQL()) {
			throw new IllegalArgumentException("Las migraciones automáticas requieren SQLite con backup verificado; no se abre MariaDB con esta estrategia.");
		}
		instance = this;
		this.configuration = configuration;
		configuration.getDataFolder().mkdirs();
		this.dbManager = new DatabaseManager(this);
		dbManager.registerDatabase(new PlayerDataDatabase());
		dbManager.getDatabase(PlayerDataDatabase.class).registerTable(this, Identifier.class, new DatabaseTableInstance<Long>() {
			@Override
			public DatabaseTable newInstance(Long key) {
				if (key != null) {
					return new Identifier(dbManager.getDatabase(PlayerDataDatabase.class));
				} else {
					return new Identifier(dbManager.getDatabase(PlayerDataDatabase.class));
				}
			}			
		});
		if (configuration.isSchemaMigrationsEnabled() && dbManager.getDatabase(PlayerDataDatabase.class).hasError()) {
			IllegalStateException failure = new IllegalStateException("No se inicia DatabaseAPI: el esquema global no se ha confirmado.");
			try {
				dbManager.unloadDatabases();
			} catch (SQLException closeFailure) {
				failure.addSuppressed(closeFailure);
			}
			throw failure;
		}
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
