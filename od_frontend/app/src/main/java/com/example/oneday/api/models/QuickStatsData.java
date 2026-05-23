package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class QuickStatsData {
    public int streak;
    @SerializedName("month_days_completed")
    public int monthDaysCompleted;
    @SerializedName("month_days_total")
    public int monthDaysTotal;
    @SerializedName("activities_completed_today")
    public int activitiesCompletedToday;
    @SerializedName("activities_total_today")
    public int activitiesTotalToday;
}
