package me.pepe.DatabaseAPI.Utils;

public class CallbackRequest<T> {
	private T result;
	private String error;
	public CallbackRequest() {}
	public CallbackRequest(T result) {
		this.result = result;
	}
	public CallbackRequest(String error) {
		this.error = error;
	}
	public CallbackRequest(T result, String error) {
		this.result = result;
		this.error = error;
	}
	public boolean hasResult() {
		return result != null;
	}
	public T getResult() {
		return result;
	}
	public boolean isCorrect() {
		return !hasError();
	}
	public boolean hasError() {
		return error != null && !error.isEmpty();
	}
	public String getError() {
		return error;
	}
}