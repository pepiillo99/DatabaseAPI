package me.pepe.DatabaseAPI.DatabaseManager.Types;

import java.sql.ResultSet;
import java.sql.SQLException;

import me.pepe.DatabaseAPI.DatabaseManager.DatabaseKeyType;
import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;

public abstract class TableDatabaseMultiKeys extends DatabaseTable {
	public TableDatabaseMultiKeys(String name, String keyName, Database database) {
		super(name, keyName, DatabaseKeyType.LONG, database);
		hasData = true;
		setAutoIncrement(true);
	}
	public abstract void buildKey(Long key);
	protected abstract void buildDatabase(ResultSet result) throws SQLException;
	public void load(ResultSet result) throws SQLException {
		loaded = true;
		hasData = true;
		buildDatabase(result);
		reloadLastSave();
	}
}
