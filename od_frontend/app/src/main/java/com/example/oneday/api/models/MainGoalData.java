package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

public class MainGoalData {
    public int id;
    public String title;
    public String status;
    @SerializedName("goal_type")
    public String goalType;
    @SerializedName("progress_percent")
    public float progressPercent;
    public String deadline;
}
