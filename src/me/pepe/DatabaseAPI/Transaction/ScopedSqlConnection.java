package me.pepe.DatabaseAPI.Transaction;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/** Mantiene los recursos y el control de commit dentro de DatabaseAPI. */
public final class ScopedSqlConnection implements AutoCloseable {
	public interface Guard {
		void check() throws SQLException;
		void failed(SQLException failure);
	}
	private final Guard guard;
	private final Thread owner = Thread.currentThread();
	private final Connection facade;
	private final IdentityHashMap<Object, Object> facades = new IdentityHashMap<Object, Object>();
	private final List<AutoCloseable> resources = new ArrayList<AutoCloseable>();
	private final IdentityHashMap<AutoCloseable, Integer> tracked = new IdentityHashMap<AutoCloseable, Integer>();
	private boolean active = true;
	public ScopedSqlConnection(Connection connection, Guard guard) {
		this.guard = guard;
		this.facade = (Connection) wrap(connection, Connection.class);
	}
	public Connection getFacade() {
		return facade;
	}
	private Object wrap(final Object target, Class<?> type) {
		Object existing = facades.get(target);
		if (existing != null) {
			return existing;
		}
		Class<?>[] interfaces = target instanceof ResultSet && target instanceof ResultSetMetaData ? new Class<?>[] { ResultSet.class, ResultSetMetaData.class } : new Class<?>[] { type };
		Object result = Proxy.newProxyInstance(ScopedSqlConnection.class.getClassLoader(), interfaces, new InvocationHandler() {
			@Override
			public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
				String name = method.getName();
				if (method.getDeclaringClass() == Object.class) {
					if (name.equals("equals")) {
						return proxy == arguments[0];
					}
					if (name.equals("hashCode")) {
						return System.identityHashCode(proxy);
					}
					return "DatabaseAPI scoped JDBC";
				}
				// Cerrar debe seguir siendo posible tras un fallo SQL. Relanzar la misma excepción
				// durante try-with-resources produciría autosupresión y ocultaría la causa real.
				if (!(target instanceof Connection) && name.equals("close") && Thread.currentThread() == owner) {
					if (!active) {
						return null;
					}
					try {
						Object value = method.invoke(target, arguments);
						Integer slot = tracked.remove(target);
						if (slot != null) {
							resources.set(slot, null);
						}
						facades.remove(target);
						return value;
					} catch (InvocationTargetException failure) {
						SQLException closed = new SQLException("No se pudo cerrar el recurso SQL", failure.getCause());
						guard.failed(closed);
						throw closed;
					}
				}
				guard.check();
				if (!active) {
					throw reject("El ámbito SQL ha terminado");
				}
				if (name.equals("unwrap")) {
					throw reject("No se permite extraer el recurso JDBC de su ámbito");
				}
				if (name.equals("isWrapperFor")) {
					return false;
				}
				if (target instanceof Connection && !name.equals("prepareStatement") && !name.equals("getAutoCommit") && !name.equals("isClosed") && !name.equals("isReadOnly") && !name.equals("getTransactionIsolation")) {
					throw reject("El control de conexión y transacción corresponde a DatabaseAPI: " + name);
				}
				if (name.equals("prepareStatement")) {
					String sql = (String) arguments[0];
					if (!sql.matches("(?is)^\\s*(SELECT|INSERT|UPDATE|DELETE|WITH)\\b.*")) {
						throw reject("El contexto SQL solo admite consultas y cambios de datos parametrizados");
					}
				}
				if (target instanceof Statement && arguments != null && arguments.length > 0 && arguments[0] instanceof String && (name.startsWith("execute") || name.equals("addBatch"))) {
					throw reject("Ejecuta únicamente la sentencia preparada por el contexto");
				}
				try {
					Object value = method.invoke(target, arguments);
					if (value instanceof Connection) {
						return facade;
					}
					if (value instanceof PreparedStatement) {
						track((PreparedStatement) value);
						return wrap(value, PreparedStatement.class);
					}
					if (value instanceof Statement) {
						return wrap(value, Statement.class);
					}
					if (value instanceof ResultSet) {
						track((ResultSet) value);
						return wrap(value, ResultSet.class);
					}
					if (value instanceof ResultSetMetaData) {
						return wrap(value, ResultSetMetaData.class);
					}
					return value;
				} catch (InvocationTargetException failure) {
					if (failure.getCause() instanceof SQLException) {
						guard.failed((SQLException) failure.getCause());
					}
					throw failure.getCause();
				}
			}
		});
		facades.put(target, result);
		return result;
	}
	private SQLException reject(String message) {
		SQLException failure = new SQLException(message);
		guard.failed(failure);
		return failure;
	}
	private void track(AutoCloseable resource) {
		if (!tracked.containsKey(resource)) {
			tracked.put(resource, resources.size());
			resources.add(resource);
		}
	}
	@Override
	public void close() throws SQLException {
		SQLException failure = null;
		for (int index = resources.size() - 1; index >= 0; index--) {
			if (resources.get(index) == null) {
				continue;
			}
			try {
				resources.get(index).close();
			} catch (Exception closeFailure) {
				if (failure == null) {
					failure = new SQLException("No se pudieron cerrar los recursos SQL", closeFailure);
				} else {
					failure.addSuppressed(closeFailure);
				}
			}
		}
		active = false;
		resources.clear();
		tracked.clear();
		facades.clear();
		if (failure != null) {
			guard.failed(failure);
			throw failure;
		}
	}
}
