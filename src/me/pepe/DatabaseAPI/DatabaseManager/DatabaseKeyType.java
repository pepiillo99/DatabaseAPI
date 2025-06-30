package me.pepe.DatabaseAPI.DatabaseManager;

public enum DatabaseKeyType {
	STRING(String.class, "VARCHAR(32)", ""),
	BOOLEAN(Boolean.class, "BOOLEAN", false),
	LONG(Long.class, "BIGINT(19)", "INTEGER", 0L),
	INT(Integer.class, "INTEGER", 0),
	DOUBLE(Double.class, "DOUBLE", 0D);
	
	private Class<?> clase;
	private String mysqlStatementName;
	private String sqliteStatementName;
	private Object newInstance;
	
	DatabaseKeyType(Class<?> clase, String mysqlStatementName, Object newInstance) {
		this(clase, mysqlStatementName, mysqlStatementName, newInstance);
	}
	DatabaseKeyType(Class<?> clase, String mysqlStatementName, String sqliteStatementName, Object newInstance) {
		this.clase = clase;
		this.mysqlStatementName = mysqlStatementName;
		this.sqliteStatementName = sqliteStatementName;
		this.newInstance = newInstance;
	}
	public Class<?> getClase() {
		return clase;
	}
	public String getStatementName(boolean mysql) {
		return mysql ? mysqlStatementName : sqliteStatementName;
	}
	public Object getNewInstance() {
		return newInstance;
	}
}
