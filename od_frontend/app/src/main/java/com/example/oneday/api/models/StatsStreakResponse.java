package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class StatsStreakResponse {
    @SerializedName("current_streak")   public int currentStreak;
    @SerializedName("best_streak")      public int bestStreak;
    @SerializedName("last_closed_date") public String lastClosedDate;
}
