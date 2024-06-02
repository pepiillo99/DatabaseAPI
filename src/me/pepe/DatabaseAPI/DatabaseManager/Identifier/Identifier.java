package me.pepe.DatabaseAPI.DatabaseManager.Identifier;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;

import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;
import me.pepe.DatabaseAPI.DatabaseManager.Types.TableDatabaseMultiKeys;

public class Identifier extends TableDatabaseMultiKeys {
	private long identifier = 0;
	private String name = "";
	private UUID uuid = UUID.randomUUID();
	public Identifier(Database database) {
		super("Identifiers", "Identifiers", database);
		setAutoIncrement(true);
	}
	@Override
	public Object keySerialize() {
		return identifier;
	}
	@Override
	public HashMap<String, Object> serialize(HashMap mapp) {
		HashMap<String, Object> map = mapp;
		map.put("name", name);
		map.put("uuid", uuid);
		return map;
	}
	@Override
	public void deserialize(ResultSet result) throws SQLException {
		identifier = result.getInt("Identifiers");
		name = result.getString("name");
		uuid = UUID.fromString(result.getString("uuid"));
	}
	@Override
	public void buildDatabase(ResultSet result) throws SQLException {
		identifier = result.getInt("Identifiers");
		name = result.getString("name");
		uuid = UUID.fromString(result.getString("uuid"));
	}
	@Override
	public void buildKey(Long key) {
		this.identifier = key;
	}
	public long getID() {
		return identifier;
	}
	public String getName() {
		return name;
	}
	public <DatabaseManager> void setName(String name) {
		this.name = name;
		//setUnsaved();
	}
	public UUID getUUID() {
		return uuid;
	}
	public <DatabaseManager> void setUUID(UUID uuid) {
		this.uuid = uuid;
		//setUnsaved();
	}
}