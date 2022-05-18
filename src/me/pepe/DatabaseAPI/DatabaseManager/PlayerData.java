package me.pepe.DatabaseAPI.DatabaseManager;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import me.pepe.DatabaseAPI.DatabaseAPI;
import me.pepe.DatabaseAPI.DatabaseManager.Identifier.Identifier;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Player.PlayerDatabaseTable;

public class PlayerData {
	private Identifier identifier;
	private HashMap<Class<? extends PlayerDatabaseTable>, PlayerDatabaseTable> playerDatabases = new HashMap<Class<? extends PlayerDatabaseTable>, PlayerDatabaseTable>();
	private Exception error;
	private List<Class<? extends PlayerDatabaseTable>> onlyLoad;
	public PlayerData(Identifier identifier) {
		this.identifier = identifier;
	}
	public Identifier getIdentifier() {
		return identifier;
	}
	public boolean isLoaded() {
		boolean loaded = true;
		if (playerDatabases.values().isEmpty()) {
			loaded = false;
		} else {
			for (PlayerDatabaseTable db : playerDatabases.values()) {
				if (!db.isLoaded()) {
					loaded = false;
					break;
				}
			}
		}
		return loaded;
	}
	public List<Class<? extends PlayerDatabaseTable>> getOnlyLoad() {
		return onlyLoad;
	}
	public void setOnlyLoad(List<Class<? extends PlayerDatabaseTable>> onlyLoad) {
		this.onlyLoad = onlyLoad;
	}
	public Collection<PlayerDatabaseTable> getDatas() {
		return Collections.unmodifiableCollection(playerDatabases.values());
	}
	public int countLoadedDatas() {
		int loaded = 0;
		for (PlayerDatabaseTable pDb : getDatas()) {
			if (pDb.isLoaded()) {
				loaded++;
			}
		}
		return loaded;
	}
	public <T extends PlayerDatabaseTable> T getData(Class<? extends PlayerDatabaseTable> clase) {
		if (playerDatabases.containsKey(clase)) {
			return (T) playerDatabases.get(clase);
		} else {
			DatabaseAPI.getInstance().log("PlayerDatabase", "La database " + clase.getSimpleName() + " no está cargada en esta PlayerData, ¿posiblemente no fue añadido posteriormente?");
			return null;
		}
	}
	public void addData(PlayerDatabaseTable db) {
		playerDatabases.put((Class<PlayerDatabaseTable>) db.getClass(), db);
	}
	public void saveAll(boolean async) {
		for (Entry<Class<? extends PlayerDatabaseTable>, PlayerDatabaseTable> db : playerDatabases.entrySet()) {
			db.getValue().save(async);
		}
	}
	public boolean hasIdentifier() {
		return identifier != null;
	}
	public boolean hasError() {
		return error != null;
	}
	public void setError(Exception error) {
		this.error = error;
	}
	public Exception getError() {
		return error;
	}
}