package me.pepe.DatabaseAPI.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** La presencia de este resultado confirma el commit; sus avisos no autorizan repetirlo. */
public final class TransactionResult<T> {
	private final T value;
	private final List<Exception> postCommitFailures;
	public TransactionResult(T value, List<Exception> postCommitFailures) {
		this.value = value;
		this.postCommitFailures = Collections.unmodifiableList(new ArrayList<Exception>(postCommitFailures));
	}
	public T getValue() {
		return value;
	}
	public List<Exception> getPostCommitFailures() {
		return postCommitFailures;
	}
}
