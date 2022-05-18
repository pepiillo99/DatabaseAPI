package me.pepe.DatabaseAPI.Utils;

public interface Callback<T> {

    void done(T result, Exception exception);
}
