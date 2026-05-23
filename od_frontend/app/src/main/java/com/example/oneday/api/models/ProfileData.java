package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class ProfileData {
    public String name;
    @SerializedName("avatar_url")
    public String avatarUrl;
}
