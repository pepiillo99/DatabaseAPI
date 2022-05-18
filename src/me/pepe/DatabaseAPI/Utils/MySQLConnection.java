package me.pepe.DatabaseAPI.Utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import me.pepe.DatabaseAPI.DatabaseAPI;
import me.pepe.DatabaseAPI.DatabaseManager.Types.Database;

public class MySQLConnection {
	private DatabaseAPI instance;
	private Database database;
	private int maxUses = 2000;
	private int uses = 0;
	private long lastUse = 0;
	private Connection connection;
	private long caduc = 5 * 60 * 1000;
	public MySQLConnection(DatabaseAPI instance, Database database, long caduc, int maxUses, Callback<Connection> callback) {
		this.instance = instance;
		this.database = database;
		this.caduc = caduc;
		this.maxUses = maxUses;
		stablishConnection(callback);
	}
	public MySQLConnection(DatabaseAPI instance, Database database, Callback<Connection> callback) {
		this.instance = instance;
		this.database = database;
		stablishConnection(callback);
	}
	private void stablishConnection(Callback<Connection> callback) {
		try {
			if (connection != null && !connection.isClosed()) {
				new Thread() {
					@Override
					public void run() {
						Connection con = connection;
						try {
							sleep(1000); // duerme un segundo para asegurar que se dejó de usar la conexion...
						} catch (InterruptedException e1) {
							// en teoria esto no pasará xd
						}
						try {
							System.out.println("Connection of database " + database.getDatabaseName() + " is closed!");
							con.close();
						} catch (SQLException e) {
							callback.done(null, e);
						}
					}
				}.start();
			}
		} catch (SQLException e1) {
			callback.done(null, e1);
		}
		try {
			Class.forName("org.mariadb.jdbc.Driver");
			connection = DriverManager.getConnection("jdbc:mariadb://" + instance.getConfiguration().getIP() + ":" + instance.getConfiguration().getPort() + "/" + database.getDatabaseName() + "?user=" + instance.getConfiguration().getUser() + "&password=" + instance.getConfiguration().getPassword() + "&autoReconnect=true&amp;useUnicode=true;characterEncoding=UTF-8&maxIdleTime=999999999&sessionVariables=wait_timeout=999999999");
			lastUse = System.currentTimeMillis();
			uses = 0;
			System.out.println("uses of connection restarteds");
			callback.done(connection, null);		
		} catch (Exception e) {
			callback.done(null, e);
		}
	}
	public boolean isCaduqued() {
		try {
			if ((lastUse + caduc) - System.currentTimeMillis() <= 0 || maxUses <= uses || (connection != null && connection.isClosed())) {
				return true;
			} else {
				return false;
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	public void getConnection(Callback<Connection> callback) {
		if (isCaduqued()) {
			if ((lastUse + caduc) - System.currentTimeMillis() <= 0) {
				System.out.println("to close by connection caduqued...");
			} else if (maxUses <= uses) {
				System.out.println("to close connection by maxuses... " + maxUses + " <= " + uses);
			} else {
				try {
					if (connection != null && connection.isClosed()) {
						System.out.println("to close connection by is closed or nulled connection...");
					}
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			stablishConnection(callback);
		} else {
			uses++;
			lastUse = System.currentTimeMillis();
			callback.done(connection, null);
		}
	}
	public void close() throws SQLException {
		if (connection != null && !connection.isClosed()) {
			connection.close();
		}
	}
}
