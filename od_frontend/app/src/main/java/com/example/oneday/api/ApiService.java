package com.example.oneday.api;

import com.example.oneday.api.models.AuthResponse;
import com.example.oneday.api.models.LoginRequest;
import com.example.oneday.api.models.RegisterRequest;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {
    @POST("auth/login/")
    Call<AuthResponse> login(@Body LoginRequest body);

    @POST("auth/register/")
    Call<AuthResponse> register(@Body RegisterRequest body);
}
