package me.pepe.DatabaseAPI.DatabaseManager.Types.Player.Simple;

import me.pepe.DatabaseAPI.DatabaseManager.PlayerData;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Player.PlayerDatabaseTable;

public abstract class SimplePlayerDatabaseTable extends PlayerDatabaseTable<SimplePlayerDatabaseTable> {
	public SimplePlayerDatabaseTable(String name, PlayerData pData, Database database) {
		super(name, pData, database);
	}
}