package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class GoalData {
    public int id;
    public String title;
    public String description;
    @SerializedName("goal_type")      public String goalType;
    public String status;
    @SerializedName("progress_percent") public float progressPercent;
    @SerializedName("start_date")     public String startDate;
    @SerializedName("end_date")       public String endDate;
    public String deadline;
    @SerializedName("is_active")      public boolean isActive;
    @SerializedName("created_at")     public String createdAt;
}
