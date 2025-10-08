package me.pepe.DatabaseAPI.Utils;

public class SimpleCallbackRequest {
	private String error;
	public SimpleCallbackRequest() {}
	public SimpleCallbackRequest(String error) {
		this.error = error;
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