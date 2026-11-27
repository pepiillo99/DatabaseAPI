package me.pepe.DatabaseAPI.Transaction;

public interface TransactionWork<T> {
	T execute(TransactionContext transaction) throws Exception;
}
