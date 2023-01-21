package me.pepe.DatabaseAPI.DatabaseManager.examples;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import me.pepe.DatabaseAPI.DatabaseManager.DatabaseKeyType;
import me.pepe.DatabaseAPI.DatabaseManager.Databases.ServerDataDatabase;
import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;

public class ContactsTable extends DatabaseTable<ServerDataDatabase> {
	private int number;
	private String name;
	public ContactsTable(Database database) {
		// (tableName, keyName, DatabaseType, database)
		super("ContactsTable", "number", DatabaseKeyType.INT, database);
	}
	@Override
	public Object keySerialize() { // you can change Object to Integer...
		return number;
	}
	@Override
	public HashMap<String, Object> serialize(HashMap<String, Object> map) {
		// As the telephone number is the key to the table, it will not be necessary to save it. (supposedly this will never change)
		map.put("name", name);
		return map;
	}
	@Override
	public void deserialize(ResultSet result) throws SQLException {
		this.number = result.getInt("number"); // Differentiating from serialize, here it is necessary to store the number
		this.name = result.getString("name"); // we collect the name of the result and define it in our variable
	}
	@Override
	public void onLoad(boolean hasData) {
		System.out.println("Table " + getTableName() + " loaded hasData: " + hasData);
	}
	public int getNumber() {
		return number;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
		setSaved(false); // we define as the table is not saved
		save(true); // we save the table asynchromatically, in the case of not doing so (false) for the process to continue, you must wait for the database to be saved.
	}
}