package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class StatsWeeklyResponse {
    public List<WeekDay> days;

    public static class WeekDay {
        public String date;
        public String status;
        @SerializedName("progress_percent")      public float progressPercent;
        @SerializedName("is_closed")             public boolean isClosed;
        @SerializedName("activities_total")      public int activitiesTotal;
        @SerializedName("activities_completed")  public int activitiesCompleted;
        public int minutes;
    }
}
