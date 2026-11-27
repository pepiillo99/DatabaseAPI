package me.pepe.DatabaseAPI.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Construcción común de sentencias parametrizadas, también dentro del ámbito SQL gestionado. */
public final class SqlStatements {
	private SqlStatements() {}
	public static PreparedStatement prepare(Connection connection, String sql, Object... values) throws SQLException {
		PreparedStatement statement = connection.prepareStatement(sql);
		try {
			bind(statement, values);
			return statement;
		} catch (SQLException | RuntimeException failure) {
			try {
				statement.close();
			} catch (SQLException closeFailure) {
				failure.addSuppressed(closeFailure);
			}
			throw failure;
		}
	}
	public static void bind(PreparedStatement statement, Object... values) throws SQLException {
		for (int index = 0; index < values.length; index++) {
			statement.setObject(index + 1, values[index]);
		}
	}
	public static int update(Connection connection, String sql, Object... values) throws SQLException {
		try (PreparedStatement statement = prepare(connection, sql, values)) {
			return statement.executeUpdate();
		}
	}
	public static long insert(Connection connection, String sql, Object... values) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			bind(statement, values);
			if (statement.executeUpdate() != 1) {
				throw new SQLException("No se confirmó la inserción del registro");
			}
			try (ResultSet keys = statement.getGeneratedKeys()) {
				if (!keys.next() || keys.getLong(1) == 0L) {
					throw new SQLException("No se confirmó la clave del registro insertado");
				}
				return keys.getLong(1);
			}
		}
	}
}
