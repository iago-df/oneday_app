package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class ActivityData {
    public int id;
    public String title;
    public String description;
    @SerializedName("activity_type")
    public String activityType;
    public String status;
    @SerializedName("estimated_minutes")
    public Integer estimatedMinutes;
    @SerializedName("actual_minutes")
    public Integer actualMinutes;
    @SerializedName("start_time")
    public String startTime;
    @SerializedName("end_time")
    public String endTime;
    public int order;
    @SerializedName("day_entry_id")
    public Integer dayEntryId;
    @SerializedName("goal_id")
    public Integer goalId;
    @SerializedName("category_id")
    public Integer categoryId;
}
