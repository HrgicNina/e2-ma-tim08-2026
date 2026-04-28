package com.example.slagalica.domain;

public interface AuthResultCallback {
    void onSuccess();
    void onEmailNotVerified();
    void onError(String message);
}
