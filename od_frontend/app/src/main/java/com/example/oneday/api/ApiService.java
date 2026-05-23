package com.example.oneday.api;

import com.example.oneday.api.models.ActivityData;
import com.example.oneday.api.models.AuthResponse;
import com.example.oneday.api.models.DashboardResponse;
import com.example.oneday.api.models.LoginRequest;
import com.example.oneday.api.models.RegisterRequest;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface ApiService {
    @POST("auth/login/")
    Call<AuthResponse> login(@Body LoginRequest body);

    @POST("auth/register/")
    Call<AuthResponse> register(@Body RegisterRequest body);


    @GET("dashboard/today/")
    Call<DashboardResponse> getDashboard(@Header("Authorization") String token);

    @POST("activities/")
    Call<ActivityData> createActivity(
            @Header("Authorization") String token,
            @Body Map<String, Object> body
    );

    @DELETE("activities/{id}/")
    Call<Void> deleteActivity(
            @Path("id") int id,
            @Header("Authorization") String token
    );

    @PUT("activities/{id}/")
    Call<ActivityData> updateActivity(
            @Path("id") int id,
            @Header("Authorization") String token,
            @Body Map<String, Object> body
    );
}
