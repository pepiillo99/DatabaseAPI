package me.pepe.DatabaseAPI.Utils;

public class CallbackRequest {
	private String error;
	public CallbackRequest() {}
	public CallbackRequest(String error) {
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