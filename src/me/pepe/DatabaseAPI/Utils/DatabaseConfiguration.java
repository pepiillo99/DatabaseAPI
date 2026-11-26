package me.pepe.DatabaseAPI.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.pepe.DatabaseAPI.Schema.SchemaMigration;

public class DatabaseConfiguration {
	private String ip;
	private int port;
	private String user;
	private String password;
	private boolean sql;
	private boolean generateIdentifiers;
	private File dataFolder = new File("");
	private boolean schemaMigrationsEnabled;
	private String schemaComponent = "";
	private int schemaVersion;
	private final List<SchemaMigration> schemaMigrations = new ArrayList<SchemaMigration>();
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
	public DatabaseConfiguration enableSchemaMigrations(String component, int version) {
		if (component == null || !component.matches("[A-Za-z0-9_.-]{1,80}") || version < 1) {
			throw new IllegalArgumentException("Componente y versión de esquema válidos obligatorios.");
		}
		schemaMigrationsEnabled = true;
		schemaComponent = component;
		schemaVersion = version;
		return this;
	}
	public DatabaseConfiguration addSchemaMigration(SchemaMigration migration) {
		if (migration == null) {
			throw new IllegalArgumentException("Migración obligatoria.");
		}
		for (SchemaMigration existing : schemaMigrations) {
			if (existing.getId().equals(migration.getId())) {
				throw new IllegalArgumentException("Identificador de migración duplicado: " + migration.getId());
			}
		}
		schemaMigrations.add(migration);
		return this;
	}
	public boolean isSchemaMigrationsEnabled() {
		return schemaMigrationsEnabled;
	}
	public String getSchemaComponent() {
		return schemaComponent;
	}
	public int getSchemaVersion() {
		return schemaVersion;
	}
	public List<SchemaMigration> getSchemaMigrations() {
		return Collections.unmodifiableList(schemaMigrations);
	}
}
