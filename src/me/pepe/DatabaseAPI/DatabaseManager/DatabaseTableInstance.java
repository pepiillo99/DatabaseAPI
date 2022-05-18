package me.pepe.DatabaseAPI.DatabaseManager;

import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;

public abstract class DatabaseTableInstance<K> {
	public abstract DatabaseTable newInstance(K key);
}
