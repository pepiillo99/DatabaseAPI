package me.pepe.DatabaseAPI.DatabaseManager;

import java.util.ArrayList;
import java.util.List;

import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;

public abstract class DatabaseTableInstance<K> {
	private List<String> errors = new ArrayList<String>();
	public abstract DatabaseTable<?> newInstance(K key);
	/**
	 * You can check if table has errors
	 * @return if table has error
	 */
	public boolean hasError() {
		return !errors.isEmpty();
	}
	/**
	 * Getter of errors
	 * @return list of errors
	 */
	public List<String> getErrors() {
		return errors;
	}
	/**
	 * Can register error on table, and on next time u can check this
	 * @param error - string describing the error
	 */
	public void registerError(String error) {
		errors.add(error);
	}
}
