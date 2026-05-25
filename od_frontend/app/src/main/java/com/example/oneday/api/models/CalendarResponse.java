package com.example.oneday.api.models;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class CalendarResponse {
    public int year;
    public int month;
    public List<CalendarDay> days;

    public static class CalendarDay {
        public String date;
        @SerializedName("has_entry")        public boolean hasEntry;
        public String status;
        @SerializedName("progress_percent") public float progressPercent;
        @SerializedName("is_closed")        public boolean isClosed;
        @SerializedName("main_goal_title")  public String mainGoalTitle;
    }
}
