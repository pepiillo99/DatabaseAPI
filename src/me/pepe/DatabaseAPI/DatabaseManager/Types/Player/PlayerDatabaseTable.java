package me.pepe.DatabaseAPI.DatabaseManager.Types.Player;

import me.pepe.DatabaseAPI.DatabaseManager.DatabaseKeyType;
import me.pepe.DatabaseAPI.DatabaseManager.PlayerData;
import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Player.Multi.MultiPlayerDatabaseTable;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Player.Simple.SimplePlayerDatabaseTable;
import me.pepe.DatabaseAPI.Utils.Callback;

public abstract class PlayerDatabaseTable<V extends DatabaseTable> extends DatabaseTable {
	private PlayerData pData;
	private long lastUpdate = 0;
	public PlayerDatabaseTable(String name, PlayerData pData, Database database) {
		super(name, "identifier", DatabaseKeyType.LONG, database);
		this.pData = pData;
	}
	@Override
	public Long keySerialize() {
		return pData.getIdentifier().getID();
	}
	public PlayerData getPlayerData() {
		return pData;
	}
	public long getLastUpdate() {
		return lastUpdate;
	}
	public void update(boolean priority, String dataName) {
		update(priority, dataName, null);
	}
	public void update(boolean priority, String dataName, Callback<Boolean> callback) {
		if (this instanceof MultiPlayerDatabaseTable) {
			MultiPlayerDatabaseTable multidb = (MultiPlayerDatabaseTable) this;
			if (multidb.hasData(dataName)) {
				multidb.getData(dataName).setNecesarySave(true);
				if (priority) {
					setSaved(false);
					multidb.save(true);
				} else {
					if ((lastUpdate + 10000) - System.currentTimeMillis() <= 0) {
						setSaved(false);
						multidb.save(true);
					}
				}
				lastUpdate = System.currentTimeMillis();
			}
		}
	}
	protected void update(boolean priority) {
		this.update(priority, new Callback<Boolean>() { // no puedo ponerlo nullo porque entiende que es el metodo del dataname ;(

			@Override
			public void done(Boolean result, Exception exception) {}
		});
	}
	protected void update(boolean priority, Callback<Boolean> callback) {
		if (this instanceof SimplePlayerDatabaseTable) {
			setSaved(false);
			if (priority) {
				getDatabase().save(true, this, callback);
			} else {
				if ((lastUpdate + 10000) - System.currentTimeMillis() <= 0) {
					getDatabase().save(true, this, callback);
				}
			}
			lastUpdate = System.currentTimeMillis();
		}
	}
}