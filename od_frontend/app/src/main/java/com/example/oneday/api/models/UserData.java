package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class UserData {
    @SerializedName("id")
    public int id;

    @SerializedName("username")
    public String username;

    @SerializedName("email")
    public String email;

    @SerializedName("name")
    public String name;
}
