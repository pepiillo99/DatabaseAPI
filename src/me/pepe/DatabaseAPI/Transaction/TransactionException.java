package me.pepe.DatabaseAPI.Transaction;

import java.sql.SQLException;

public final class TransactionException extends SQLException {
	private static final long serialVersionUID = 1L;
	public enum Outcome {
		NOT_STARTED, ROLLED_BACK, UNKNOWN
	}
	private final Outcome outcome;
	public TransactionException(String message, Outcome outcome, Throwable cause) {
		super(message, cause);
		this.outcome = outcome;
	}
	public Outcome getOutcome() {
		return outcome;
	}
}
