package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class DayEntryData {
    public int id;
    public String date;
    public String status;
    @SerializedName("progress_percent")
    public float progressPercent;
    @SerializedName("is_closed")
    public boolean isClosed;
    @SerializedName("main_goal")
    public MainGoalData mainGoal;
}
