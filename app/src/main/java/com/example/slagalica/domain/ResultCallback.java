package com.example.slagalica.domain;

public interface ResultCallback {
    void onSuccess();
    void onError(String message);
}
