package me.pepe.DatabaseAPI.DatabaseManager.Types.Player.Multi;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map.Entry;

import me.pepe.DatabaseAPI.DatabaseAPI;
import me.pepe.DatabaseAPI.DatabaseManager.PlayerData;
import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Player.PlayerDatabaseTable;
import me.pepe.DatabaseAPI.Utils.Callback;

public abstract class MultiPlayerDatabaseTable<V extends MultiPlayerDatabaseTableEntry> extends PlayerDatabaseTable<MultiPlayerDatabaseTable> {
	private final HashMap<String, V> datas = new HashMap<String, V>();
	public MultiPlayerDatabaseTable(String name, PlayerData pData, Database database) {
		super(name, pData, database);
	}
	public HashMap<String, V> getDatas() {
		return datas;
	}
	protected abstract HashMap<String, Object> serializeMulti(HashMap<String, Object> map, V data);
	public abstract V deserializeMulti(String dataName, ResultSet result) throws SQLException;
	public V getData(String dataName) {
		if (datas.containsKey(dataName)) {
			return datas.get(dataName);
		} else {
			V dataClass = null;
			try {
				dataClass = deserializeMulti(dataName, null);
			} catch (SQLException e) {
				e.printStackTrace();
			}
			datas.put(dataName, dataClass);
			return dataClass;
		}
	}
	public boolean hasData(String dataName) {
		return datas.containsKey(dataName);
	}
	public void load(boolean async) {
		if (async) {
			getDatabase().getQueue().submit(new Runnable() {
				@Override
				public void run() {
					load(null);
				}
			});
		} else {
			load(null);
		}
	}
	public void load(boolean async, Callback<DatabaseTable> callback) {
		if (async) {
			getDatabase().getQueue().submit(new Runnable() {
				@Override
				public void run() {
					load(callback);
				}
			});
		} else {
			load(callback);
		}
	}
	private void load(Callback<DatabaseTable> callback) {
		DatabaseTable thissss = this;
		getDatabase().getConnection(new Callback<Connection>() {
			@Override
			public void done(Connection connection, Exception exception) {
				try {
					String select = "SELECT * FROM " + getTableName() + " WHERE identifier = ?";
					PreparedStatement statement = connection.prepareStatement(select);
					getDatabase().saveInStatement(statement, keySerialize(), 1);
					ResultSet resultSet = statement.executeQuery();
					while (resultSet.next()) {
						String dataName = resultSet.getString("data_name");
						V dataClass = deserializeMulti(dataName, resultSet);
						dataClass.setHasData(true);
						datas.put(dataName, dataClass);
					}
					resultSet.close();
					statement.close();
					loaded = true;
					if (callback != null) {
						callback.done(thissss, null);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}			
		});
	}
	@Override
	public void save(boolean async) {
		if (DatabaseAPI.getInstance().getDatabaseManager().saveable(getTableName()) && isLoaded()) {
			if (async) {
				getDatabase().getQueue().submit(new Runnable() {
					@Override
					public void run() {
						saveAll();
					}
				});
			} else {
				saveAll();
			}
		}
	}
	private void saveAll() {
		for (Entry<String, V> entrys : datas.entrySet()) {
			if (entrys.getValue().isNecesarySave()) {
				getDatabase().getConnection(new Callback<Connection>() {
					@Override
					public void done(Connection connection, Exception exception) {
						try {
							PreparedStatement statement;
							if (entrys.getValue().hasData()) {
								statement = update(entrys.getKey(), entrys.getValue(), connection);
								entrys.getValue().setNecesarySave(false);
							} else {
								entrys.getValue().setHasData(true);
								statement = insert(entrys.getKey(), entrys.getValue(), connection);
								entrys.getValue().setNecesarySave(false);
							}
							statement.executeUpdate();
							statement.close();						
						} catch(SQLException ex) {
							ex.printStackTrace();
						}
					}					
				});
			}
		}
	}
	private PreparedStatement insert(String dataName, V dataClass, Connection connection) throws SQLException {
		String insertStatement = "INSERT INTO " + getTableName() + " (identifier, data_name";
		HashMap<String, Object> saveMap = serializeMulti(new HashMap<String, Object>(), dataClass);
		for (Entry<String, Object> save : saveMap.entrySet()) {
			insertStatement = insertStatement + ", " + save.getKey();
		}
		insertStatement = insertStatement + ") VALUES (?, ?";
		for (int i = saveMap.size(); i > 0; i--) {
			insertStatement = insertStatement + ", ?";
		}
		insertStatement = insertStatement + ")";
		PreparedStatement preparedStatement = connection.prepareStatement(insertStatement);
		int statementSize = 1;
		getDatabase().saveInStatement(preparedStatement, keySerialize(), statementSize++);
		getDatabase().saveInStatement(preparedStatement, dataName, statementSize++);
		for (Entry<String, Object> save : saveMap.entrySet()) {
			getDatabase().saveInStatement(preparedStatement, save.getValue(), statementSize++);
		}
		return preparedStatement;
	}
	private PreparedStatement update(String dataName, V dataClass, Connection connection) throws SQLException {
		HashMap<String, Object> saveMap = serializeMulti(new HashMap<String, Object>(), dataClass);
		String saveStatement = "UPDATE " + getTableName() + " SET";
		for (Entry<String, Object> save : saveMap.entrySet()) {
			saveStatement = saveStatement + " " + save.getKey() + " = ?,";
		}
		saveStatement = saveStatement.substring(0, saveStatement.length() - 1) + " WHERE identifier = ? AND data_name = ?";
		PreparedStatement statement = connection.prepareStatement(saveStatement);
		int statementSize = 1;
		for (Entry<String, Object> save : saveMap.entrySet()) {
			getDatabase().saveInStatement(statement, save.getValue(), statementSize++);
		}
		getDatabase().saveInStatement(statement, keySerialize(), statementSize++);
		getDatabase().saveInStatement(statement, dataName, statementSize++);
		return statement;
	}
	@Override
	public void deserialize(ResultSet result) {
		// UNUSED	
	}
	@Override
	public HashMap<String, Object> serialize(HashMap map) {
		try {
			return serializeMulti(map, deserializeMulti("", null));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		// UNUSED
		return map;
	}
}