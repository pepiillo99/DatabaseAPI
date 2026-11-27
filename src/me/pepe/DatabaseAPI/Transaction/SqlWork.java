package me.pepe.DatabaseAPI.Transaction;

import java.sql.Connection;

/** SQL especializado con una fachada JDBC limitada al ámbito de la transacción. */
public interface SqlWork<T> {
	T execute(Connection connection) throws Exception;
}
