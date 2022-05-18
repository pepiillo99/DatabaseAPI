package me.pepe.DatabaseAPI.DatabaseManager.Tables;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import me.pepe.DatabaseAPI.DatabaseManager.DatabaseKeyType;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;

public abstract class DatabaseTable<D extends Database> {
	private String name;
	private String keyName;
	private DatabaseKeyType keyType;
	protected boolean loaded = false;
	protected boolean hasData = false;
	private boolean saved = true;
	private boolean autoIncrement = false;
	private boolean hasPrimaryKey = true;
	private Database database;
	public DatabaseTable(String name, String keyName, DatabaseKeyType keyType, Database database) {
		this.name = name;
		this.keyName = keyName;
		this.keyType = keyType;
		this.database = database;
	}
	public String getTableName() {
		return name;
	}
	public String getKeyName() {
		return keyName;
	}
	public DatabaseKeyType getKeyType() {
		return keyType;
	}
	public boolean isLoaded() {
		return loaded;
	}
	public boolean isSaved() {
		return saved;
	}
	public void setLoaded() {
		loaded = true;
	}
	public Database getDatabase() {
		return database;
	}
	public void setSaved(boolean saved) {
		if (saved) {
			this.saved = true;
			hasData = true;
		} else {
			this.saved = false;
		}
	}
	public boolean hasData() {
		return hasData;
	}
	public void setHasData(boolean hasData) {
		this.hasData = hasData;
	}
	public boolean isAutoIncrement() {
		return autoIncrement;
	}
	protected void setAutoIncrement(boolean autoIncrement) {
		this.autoIncrement = autoIncrement;
	}
	public boolean hasPrimaryKey() {
		return hasPrimaryKey;
	}
	protected void setHasPrimaryKey(boolean hasPrimaryKey) {
		this.hasPrimaryKey = hasPrimaryKey;
	}
	public abstract Object keySerialize();
	public HashMap<String, Object> keysSerialize() {
		return null;
	}
	public abstract HashMap<String, Object> serialize(HashMap<String, Object> map);
	public abstract void deserialize(ResultSet result) throws SQLException;
	public void save(boolean async) {
		getDatabase().save(async, this);
	}
	public void onLoad(boolean hasData) {}
}
