package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class AuthResponse {
    @SerializedName("token")
    public String token;

    @SerializedName("user")
    public UserData user;

    @SerializedName("error")
    public String error;
}
