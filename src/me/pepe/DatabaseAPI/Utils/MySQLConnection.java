package me.pepe.DatabaseAPI.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import me.pepe.DatabaseAPI.DatabaseAPI;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;

public class MySQLConnection {
	private final DatabaseAPI instance;
	private final Database database;
	private int maxUses = 2000;
	private int uses;
	private long lastUse;
	private Connection connection;
	private long caduc = 5 * 60 * 1000;
	private boolean closed;

	public MySQLConnection(DatabaseAPI instance, Database database, long caduc, int maxUses, Callback<Connection> callback) {
		this.instance = instance;
		this.database = database;
		this.caduc = caduc;
		this.maxUses = maxUses;
		getConnection(callback);
	}
	public MySQLConnection(DatabaseAPI instance, Database database, Callback<Connection> callback) {
		this.instance = instance;
		this.database = database;
		getConnection(callback);
	}
	public synchronized boolean isCaduqued() {
		try {
			if (connection == null || connection.isClosed()) {
				return true;
			}
			if (!connection.getAutoCommit()) {
				return false;
			}
			return System.currentTimeMillis() - lastUse >= caduc || uses >= maxUses;
		} catch (SQLException exception) {
			return true;
		}
	}
	public synchronized void getConnection(Callback<Connection> callback) {
		Exception failure = null;
		try {
			if (closed) {
				failure = new SQLException("La conexión MariaDB está cerrada");
			} else if (isCaduqued()) {
				if (connection != null) {
					connection.close();
					connection = null;
				}
				Class.forName("org.mariadb.jdbc.Driver");
				Properties properties = new Properties();
				properties.setProperty("user", instance.getConfiguration().getUser());
				properties.setProperty("password", instance.getConfiguration().getPassword());
				properties.setProperty("characterEncoding", "UTF-8");
				String url = "jdbc:mariadb://" + instance.getConfiguration().getIP() + ":"
						+ instance.getConfiguration().getPort() + "/" + database.getDatabaseName();
				connection = DriverManager.getConnection(url, properties);
				uses = 0;
			}
			if (failure == null) {
				uses++;
				lastUse = System.currentTimeMillis();
			}
		} catch (Exception exception) {
			failure = exception;
		}
		// La conexión se mantiene reservada durante la ejecución síncrona del callback.
		if (callback != null) {
			callback.done(failure == null ? connection : null, failure);
		}
	}
	public synchronized void close() throws SQLException {
		closed = true;
		if (connection != null) {
			connection.close();
			connection = null;
		}
	}
}
