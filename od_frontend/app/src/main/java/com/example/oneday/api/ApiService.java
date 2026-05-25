package com.example.oneday.api;

import com.example.oneday.api.models.ActivityData;
import com.example.oneday.api.models.AuthResponse;
import com.example.oneday.api.models.CalendarResponse;
import com.example.oneday.api.models.DashboardResponse;
import com.example.oneday.api.models.GoalData;
import com.example.oneday.api.models.GoalsResponse;
import com.example.oneday.api.models.LoginRequest;
import com.example.oneday.api.models.RegisterRequest;
import com.example.oneday.api.models.StatsStreakResponse;
import com.example.oneday.api.models.StatsSummaryResponse;
import com.example.oneday.api.models.StatsWeeklyResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

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

    @GET("goals/")
    Call<GoalsResponse> getGoals(@Header("Authorization") String token);

    @POST("goals/")
    Call<GoalData> createGoal(
            @Header("Authorization") String token,
            @Body Map<String, Object> body
    );

    @DELETE("goals/{id}/")
    Call<Void> deleteGoal(
            @Path("id") int id,
            @Header("Authorization") String token
    );

    @GET("stats/weekly/")
    Call<StatsWeeklyResponse> getStatsWeekly(
            @Header("Authorization") String token,
            @Query("from") String from,
            @Query("to") String to
    );

    @GET("stats/streak/")
    Call<StatsStreakResponse> getStatsStreak(@Header("Authorization") String token);

    @GET("stats/summary/")
    Call<StatsSummaryResponse> getStatsSummary(@Header("Authorization") String token);

    @GET("calendar/")
    Call<CalendarResponse> getCalendar(
            @Header("Authorization") String token,
            @Query("year") int year,
            @Query("month") int month
    );
}
