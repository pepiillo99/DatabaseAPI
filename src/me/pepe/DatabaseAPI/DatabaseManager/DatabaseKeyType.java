package me.pepe.DatabaseAPI.DatabaseManager;

public enum DatabaseKeyType {
	STRING(String.class, "VARCHAR(32)", ""),
	BOOLEAN(Boolean.class, "BOOLEAN", false),
	LONG(Long.class, "BIGINT(19)", 0L),
	INT(Integer.class, "INTEGER", 0),
	DOUBLE(Double.class, "DOUBLE", 0D);
	
	private Class<?> clase;
	private String statementName;
	private Object newInstance;
	
	DatabaseKeyType(Class<?> clase, String statementName, Object newInstance) {
		this.clase = clase;
		this.statementName = statementName;
		this.newInstance = newInstance;
	}
	public Class<?> getClase() {
		return clase;
	}
	public String getStatementName() {
		return statementName;
	}
	public Object getNewInstance() {
		return newInstance;
	}
}
