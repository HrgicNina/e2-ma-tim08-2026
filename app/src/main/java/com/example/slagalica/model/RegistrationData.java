package com.example.slagalica.model;

public class RegistrationData {
    public final String email;
    public final String username;
    public final String region;
    public final String password;
    public final String confirmPassword;

    public RegistrationData(String email, String username, String region, String password, String confirmPassword) {
        this.email = email;
        this.username = username;
        this.region = region;
        this.password = password;
        this.confirmPassword = confirmPassword;
    }
}
