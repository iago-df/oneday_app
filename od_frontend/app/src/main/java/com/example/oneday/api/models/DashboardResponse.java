package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class DashboardResponse {
    public ProfileData profile;
    public DayEntryData today;
    @SerializedName("activities_today")
    public List<ActivityData> activitiesToday;
    @SerializedName("quick_stats")
    public QuickStatsData quickStats;
}
