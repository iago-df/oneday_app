package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class StatsSummaryResponse {
    public DaysStats days;
    public ActivitiesStats activities;
    public int streak;

    public static class DaysStats {
        public int total;
        public int completed;
        public int partial;
        public int failed;
        @SerializedName("avg_progress_percent") public float avgProgressPercent;
    }

    public static class ActivitiesStats {
        public int total;
        public int completed;
        @SerializedName("completion_rate")  public float completionRate;
        @SerializedName("total_minutes")    public int totalMinutes;
    }
}
