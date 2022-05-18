package me.pepe.DatabaseAPI.DatabaseManager;

public enum DatabaseKeyType {
	STRING(String.class, "VARCHAR(32)"),
	BOOLEAN(Boolean.class, "BOOLEAN"),
	LONG(Long.class, "BIGINT(19)"),
	INT(Integer.class, "INTEGER"),
	DOUBLE(Double.class, "DOUBLE");
	
	private Class<?> clase;
	private String statementName;
	
	DatabaseKeyType(Class<?> clase, String statementName) {
		this.clase = clase;
		this.statementName = statementName;
	}
	public Class<?> getClase() {
		return clase;
	}
	public String getStatementName() {
		return statementName;
	}
}
