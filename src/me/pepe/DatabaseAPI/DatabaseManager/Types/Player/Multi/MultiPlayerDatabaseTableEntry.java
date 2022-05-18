package me.pepe.DatabaseAPI.DatabaseManager.Types.Player.Multi;

public class MultiPlayerDatabaseTableEntry<V extends MultiPlayerDatabaseTable> {
	private String dataName;
	private boolean hasData = false;
	private V database;
	private boolean necesarySave = false;
	public MultiPlayerDatabaseTableEntry(String dataName, boolean hasData, V database) {
		this.dataName = dataName;
		this.hasData = hasData;
		this.database = database;
	}
	public String getDataName() {
		return dataName;
	}
	public boolean hasData() {
		return hasData;
	}
	protected void setHasData(boolean hasData) {
		this.hasData = hasData;
	}
	public V getDatabase() {
		return database;
	}
	public boolean isNecesarySave() {
		return necesarySave;
	}
	public void setNecesarySave(boolean necesarySave) {
		if (necesarySave) {
			database.setSaved(false);
		}
		this.necesarySave = necesarySave;
	}
}