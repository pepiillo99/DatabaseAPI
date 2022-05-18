package me.pepe.DatabaseAPI.Utils;

import java.io.File;

public class DatabaseConfiguration {
	private String ip;
	private int port;
	private String user;
	private String password;
	private boolean sql;
	private boolean generateIdentifiers;
	private File dataFolder = new File("");
	public DatabaseConfiguration(String ip, int port, String user, String password, boolean sql, boolean generateIdentifiers, File dataFolder) {
		this.ip = ip;
		this.port = port;
		this.user = user;
		this.password = password;
		this.sql = sql;
		this.generateIdentifiers = generateIdentifiers;
		this.dataFolder = dataFolder;
	}
	public DatabaseConfiguration(boolean generateIdentifiers, File dataFolder) { // sql
		sql = true;
		this.generateIdentifiers = generateIdentifiers;
		this.dataFolder = dataFolder;
	}
	public DatabaseConfiguration(String ip, int port, String user, String password) { // mysql
		sql = false;
		this.ip = ip;
		this.port = port;
		this.user = user;
		this.password = password;
	}
	public String getIP() {
		return ip;
	}
	public int getPort() {
		return port;
	}
	public String getUser() {
		return user;
	}
	public String getPassword() {
		return password;
	}
	public boolean isSQL() {
		return sql;
	}
	public boolean canGenerateIdentifiers() {
		return generateIdentifiers;
	}
	public File getDataFolder() {
		return dataFolder;
	}
}
