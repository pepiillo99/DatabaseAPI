package me.pepe.DatabaseAPI.Transaction;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;

/** Contexto de una sola base, válido únicamente durante execute y en su hilo.
 * Las lecturas no invocan onLoad. Los cambios se escriben explícitamente y onSave espera al commit.
 * Un rollback conserva las ediciones pendientes, sin marcarlas como guardadas, y restaura claves generadas.
 */
public interface TransactionContext {
	<T extends DatabaseTable> T find(Class<T> type, Object key) throws SQLException;
	<T extends DatabaseTable> T create(Class<T> type, Object key) throws SQLException;
	<T extends DatabaseTable> List<T> findAll(Class<T> type, HashMap<String, Object> filters, int limit, int offset, String orderBy) throws SQLException;
	void save(DatabaseTable table) throws SQLException;
	void save(DatabaseTable table, boolean ignoreOnSave) throws SQLException;
	void insert(DatabaseTable table) throws SQLException;
	boolean insertIfAbsent(DatabaseTable table) throws SQLException;
	void afterCommit(TransactionAction action) throws SQLException;
	<T> T sql(SqlWork<T> work) throws Exception;
}
