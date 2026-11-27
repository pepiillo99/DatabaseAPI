package me.pepe.DatabaseAPI.Transaction;

import java.sql.SQLException;
import me.pepe.DatabaseAPI.DatabaseManager.Tables.DatabaseTable;

/** Contexto de una sola base, válido únicamente durante execute y en su hilo.
 * Las lecturas no invocan onLoad. Los cambios se escriben explícitamente y onSave espera al commit.
 * Un rollback conserva las ediciones pendientes, sin marcarlas como guardadas, y restaura claves generadas.
 */
public interface TransactionContext {
	<T extends DatabaseTable> T find(Class<T> type, Object key) throws SQLException;
	<T extends DatabaseTable> T create(Class<T> type, Object key) throws SQLException;
	void save(DatabaseTable table) throws SQLException;
	void insert(DatabaseTable table) throws SQLException;
	boolean insertIfAbsent(DatabaseTable table) throws SQLException;
	void afterCommit(TransactionAction action) throws SQLException;
}
