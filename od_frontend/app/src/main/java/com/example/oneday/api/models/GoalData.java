package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class GoalData {
    public int id;
    public String title;
    public String description;
    public String status;
    @SerializedName("target_days")       public int targetDays;
    @SerializedName("days_completed")    public int daysCompleted;
    @SerializedName("progress_percent") public float progressPercent;
    public String deadline;
    @SerializedName("is_active")      public boolean isActive;
    @SerializedName("created_at")     public String createdAt;
    @SerializedName("updated_at")        public String updatedAt;
}
