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
	/**
	 * Returns the data type that has the key of the table.
	 * @return DatabaseKeyType - data type of table key
	 */
	public DatabaseKeyType getKeyType() {
		return keyType;
	}
	/**
	 * @return if table is loaded
	 */
	public boolean isLoaded() {
		return loaded;
	}
	/**
	 * @return if table is saved
	 */
	public boolean isSaved() {
		return saved;
	}
	public void setLoaded() {
		loaded = true;
	}
	public Database getDatabase() {
		return database;
	}
	/**
	 * Method used to define if the database data has been saved, then save it using save(..).
	 * @param saved - boolean which to set if the data is saved
	 */
	public void setSaved(boolean saved) {
		if (saved) {
			this.saved = true;
			hasData = true;
		} else {
			this.saved = false;
		}
	}
	/**
	 * @return if has data
	 */
	public boolean hasData() {
		return hasData;
	}
	public void setHasData(boolean hasData) {
		this.hasData = hasData;
	}
	/**
	 * @return if table key is autoincrement (if has key)
	 */
	public boolean isAutoIncrement() {
		return autoIncrement;
	}
	/**
	 * You should only put an auto-incrementing table in the case of using the DatabaseKeyType as INT
	 * @param autoIncrement - boolean to autoincrement or not
	 */
	protected void setAutoIncrement(boolean autoIncrement) {
		this.autoIncrement = autoIncrement;
	}
	/**
	 * @return if has primary key
	 */
	public boolean hasPrimaryKey() {
		return hasPrimaryKey;
	}
	/**
	 * Define if the table has primary key.
	 * @param hasPrimaryKey - boolean that indicates if the table has a primary key
	 */
	protected void setHasPrimaryKey(boolean hasPrimaryKey) {
		this.hasPrimaryKey = hasPrimaryKey;
	}
	/**
	 * It is necessary to return the key using this method. The data type of the key must be the same as the DatabaseKeyType defined above.
	 * @return Key of table
	 */
	public abstract Object keySerialize();
	/**
	 * It is necessary to assemble the information to save it correctly in the table. You should save it with <String, Object>.
	 * @param map - obtained map to save the information <String, Object>
	 */
	public abstract HashMap<String, Object> serialize(HashMap<String, Object> map);
	/**
	 * It is necessary to disassemble the information received from the table and use the data obtained.
	 * @param result - ResultSet of query
	 * @throws SQLException
	 */
	public abstract void deserialize(ResultSet result) throws SQLException;
	/**
	 * Save the data from the table.
	 * @param async - boolean that defines whether to use the method asynchromatically (being able to continue the process without interruption)
	 */
	public void save(boolean async) {
		getDatabase().save(async, this);
	}
	/**
	 * You can add this method to your class to override it and do some action, this will be executed when loading the table.
	 * @param hasData - boolean that tells if the table has data
	 */
	public void onLoad(boolean hasData) {}
}
