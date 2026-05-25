package com.example.oneday;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.oneday.api.ApiClient;
import com.example.oneday.api.models.CalendarResponse;
import com.example.oneday.session.SessionManager;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CalendarFragment extends Fragment {

    private LinearLayout calendarContainer;
    private LinearLayout detailContainer;
    private LinearLayout legendContainer;
    private MaterialCardView detailCard;
    private TextView tvMonthYear;
    private SessionManager session;

    private int currentYear;
    private int currentMonth;
    private final Map<String, CalendarResponse.CalendarDay> dayMap = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_calendar, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session = new SessionManager(requireContext());

        Calendar now = Calendar.getInstance();
        currentYear  = now.get(Calendar.YEAR);
        currentMonth = now.get(Calendar.MONTH) + 1;

        TextView tvDate = view.findViewById(R.id.tvDate);
        tvDate.setText(new SimpleDateFormat("EEE, MMM d", Locale.ENGLISH).format(new Date()));

        calendarContainer = view.findViewById(R.id.calendarContainer);
        detailContainer   = view.findViewById(R.id.detailContainer);
        detailCard        = view.findViewById(R.id.detailCard);
        legendContainer   = view.findViewById(R.id.legendContainer);
        tvMonthYear       = view.findViewById(R.id.tvMonthYear);

        view.findViewById(R.id.btnPrevMonth).setOnClickListener(v -> {
            if (currentMonth == 1) { currentMonth = 12; currentYear--; }
            else currentMonth--;
            loadCalendar();
        });

        view.findViewById(R.id.btnNextMonth).setOnClickListener(v -> {
            if (currentMonth == 12) { currentMonth = 1; currentYear++; }
            else currentMonth++;
            loadCalendar();
        });

        buildLegend();
        loadCalendar();
    }


    private void loadCalendar() {
        String token = "Bearer " + session.getToken();
        ApiClient.get().getCalendar(token, currentYear, currentMonth)
                .enqueue(new Callback<CalendarResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<CalendarResponse> call,
                                           @NonNull Response<CalendarResponse> r) {
                        if (!isAdded() || r.body() == null) return;
                        buildCalendar(r.body());
                    }
                    @Override
                    public void onFailure(@NonNull Call<CalendarResponse> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        Toast.makeText(getContext(), "Could not load calendar", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    private void buildCalendar(CalendarResponse data) {
        float dp = requireContext().getResources().getDisplayMetrics().density;

        dayMap.clear();
        if (data.days != null) {
            for (CalendarResponse.CalendarDay d : data.days) dayMap.put(d.date, d);
        }

        String[] monthNames = {"January","February","March","April","May","June",
                "July","August","September","October","November","December"};
        tvMonthYear.setText(monthNames[currentMonth - 1] + " " + currentYear);

        calendarContainer.removeAllViews();

        LinearLayout headerRow = new LinearLayout(requireContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams hrlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hrlp.bottomMargin = (int)(4 * dp);
        headerRow.setLayoutParams(hrlp);
        for (String name : new String[]{"Mo","Tu","We","Th","Fr","Sa","Su"}) {
            TextView tv = new TextView(requireContext());
            tv.setText(name);
            tv.setGravity(Gravity.CENTER);
            tv.setTextColor(Color.parseColor("#AAAAAA"));
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(26 * dp), 1f));
            headerRow.addView(tv);
        }
        calendarContainer.addView(headerRow);

        Calendar cal = Calendar.getInstance();
        cal.set(currentYear, currentMonth - 1, 1);
        int startOffset  = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        int daysInMonth  = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int totalCells   = startOffset + daysInMonth;
        int rows         = (int) Math.ceil(totalCells / 7.0);

        int dayNum = 1;
        for (int row = 0; row < rows; row++) {
            LinearLayout weekRow = new LinearLayout(requireContext());
            weekRow.setOrientation(LinearLayout.HORIZONTAL);
            weekRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            for (int col = 0; col < 7; col++) {
                int cellIndex = row * 7 + col;
                if (cellIndex < startOffset || dayNum > daysInMonth) {
                    View empty = new View(requireContext());
                    empty.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(44*dp), 1f));
                    weekRow.addView(empty);
                } else {
                    String dateStr = String.format(Locale.ENGLISH, "%04d-%02d-%02d",
                            currentYear, currentMonth, dayNum);
                    CalendarResponse.CalendarDay dayData = dayMap.get(dateStr);
                    if (dayData == null) {
                        dayData = new CalendarResponse.CalendarDay();
                        dayData.date = dateStr;
                        dayData.hasEntry = false;
                    }
                    weekRow.addView(buildDayCell(dayData, dayNum, dp));
                    dayNum++;
                }
            }
            calendarContainer.addView(weekRow);
        }

        showDetailHint(dp);
    }

    private View buildDayCell(CalendarResponse.CalendarDay day, int num, float dp) {
        int cellH    = (int)(46 * dp);
        int circleS  = (int)(36 * dp);
        boolean isToday = isToday(day.date);
        boolean hasFill = day.hasEntry && day.status != null && !"empty".equals(day.status);

        FrameLayout cell = new FrameLayout(requireContext());
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, cellH, 1f));

        View circle = new View(requireContext());
        circle.setLayoutParams(new FrameLayout.LayoutParams(circleS, circleS, Gravity.CENTER));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        if (hasFill) {
            bg.setColor(getStatusColor(day.status));
        } else if (isToday) {
            bg.setColor(Color.parseColor("#ECEEFE"));
            bg.setStroke((int)(2 * dp), Color.parseColor("#5D65D9"));
        } else {
            bg.setColor(Color.parseColor("#F5F5F8"));
        }
        circle.setBackground(bg);

        TextView tv = new TextView(requireContext());
        tv.setText(String.valueOf(num));
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        if (hasFill) {
            tv.setTextColor(Color.WHITE);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
        } else if (isToday) {
            tv.setTextColor(Color.parseColor("#5D65D9"));
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            tv.setTextColor(Color.parseColor("#555555"));
        }
        tv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, cellH, Gravity.CENTER));

        cell.addView(circle);
        cell.addView(tv);

        if (day.hasEntry) {
            cell.setClickable(true);
            cell.setFocusable(true);
            final CalendarResponse.CalendarDay snap = day;
            cell.setOnClickListener(v -> showDayDetail(snap));
        }
        return cell;
    }


    private void showDayDetail(CalendarResponse.CalendarDay day) {
        if (detailContainer == null) return;
        float dp = requireContext().getResources().getDisplayMetrics().density;
        detailContainer.removeAllViews();

        String dateDisplay = day.date;
        try {
            SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            SimpleDateFormat out = new SimpleDateFormat("EEEE, MMMM d", Locale.ENGLISH);
            dateDisplay = out.format(in.parse(day.date));
        } catch (Exception ignored) {}

        TextView tvDateTitle = new TextView(requireContext());
        tvDateTitle.setText(dateDisplay);
        tvDateTitle.setTextColor(Color.parseColor("#222222"));
        tvDateTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        tvDateTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams dtlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dtlp.bottomMargin = (int)(12 * dp);
        tvDateTitle.setLayoutParams(dtlp);
        detailContainer.addView(tvDateTitle);

        if (!day.hasEntry || "empty".equals(day.status)) {
            TextView noData = new TextView(requireContext());
            noData.setText("No data for this day");
            noData.setTextColor(Color.parseColor("#BBBBBB"));
            noData.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            detailContainer.addView(noData);
            return;
        }

        int statusColor = getStatusColor(day.status);

        LinearLayout statusRow = new LinearLayout(requireContext());
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        srlp.bottomMargin = (int)(14 * dp);
        statusRow.setLayoutParams(srlp);

        TextView badge = new TextView(requireContext());
        badge.setText(getStatusLabel(day.status));
        badge.setTextColor(statusColor);
        badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setPadding((int)(10*dp), (int)(4*dp), (int)(10*dp), (int)(4*dp));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(20 * dp);
        badgeBg.setColor(Color.argb(25, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor)));
        badge.setBackground(badgeBg);
        statusRow.addView(badge);

        if (day.isClosed) {
            TextView closed = new TextView(requireContext());
            closed.setText("  ·  Closed");
            closed.setTextColor(Color.parseColor("#AAAAAA"));
            closed.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            statusRow.addView(closed);
        }
        detailContainer.addView(statusRow);

        detailContainer.addView(buildDetailLabel("Progress", dp));

        LinearLayout progressRow = new LinearLayout(requireContext());
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams prlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        prlp.bottomMargin = (int)(14 * dp);
        progressRow.setLayoutParams(prlp);

        ProgressBar pb = new ProgressBar(requireContext(), null,
                android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress((int) day.progressPercent);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(statusColor));
        pb.setProgressBackgroundTintList(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#EEEEF5")));
        pb.setLayoutParams(new LinearLayout.LayoutParams(0, (int)(7*dp), 1f));

        TextView tvPct = new TextView(requireContext());
        tvPct.setText((int) day.progressPercent + "%");
        tvPct.setTextColor(statusColor);
        tvPct.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        tvPct.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams pctlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        pctlp.setMarginStart((int)(10 * dp));
        tvPct.setLayoutParams(pctlp);

        progressRow.addView(pb);
        progressRow.addView(tvPct);
        detailContainer.addView(progressRow);

        if (day.mainGoalTitle != null && !day.mainGoalTitle.isEmpty()) {
            detailContainer.addView(buildDetailLabel("Main goal", dp));
            TextView tvGoal = new TextView(requireContext());
            tvGoal.setText(day.mainGoalTitle);
            tvGoal.setTextColor(Color.parseColor("#222222"));
            tvGoal.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            detailContainer.addView(tvGoal);
        }
    }

    private void showDetailHint(float dp) {
        if (detailContainer == null) return;
        detailContainer.removeAllViews();
        TextView hint = new TextView(requireContext());
        hint.setText("Tap a day to see details");
        hint.setTextColor(Color.parseColor("#BBBBBB"));
        hint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        hint.setGravity(Gravity.CENTER);
        hint.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        detailContainer.addView(hint);
    }


    private void buildLegend() {
        if (legendContainer == null) return;
        float dp = requireContext().getResources().getDisplayMetrics().density;

        String[][] items = {
                {"Completed", "#4CAF50"},
                {"Partial",   "#FFC107"},
                {"Failed",    "#E53935"},
                {"Today",     "#5D65D9"},
        };

        for (String[] item : items) {
            LinearLayout entry = new LinearLayout(requireContext());
            entry.setOrientation(LinearLayout.HORIZONTAL);
            entry.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            elp.setMarginEnd((int)(14 * dp));
            entry.setLayoutParams(elp);

            View dot = new View(requireContext());
            int dotSize = (int)(10 * dp);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dotSize, dotSize);
            dlp.setMarginEnd((int)(5 * dp));
            dot.setLayoutParams(dlp);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(Color.parseColor(item[1]));
            dot.setBackground(dotBg);

            TextView label = new TextView(requireContext());
            label.setText(item[0]);
            label.setTextColor(Color.parseColor("#777777"));
            label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);

            entry.addView(dot);
            entry.addView(label);
            legendContainer.addView(entry);
        }
    }


    private TextView buildDetailLabel(String text, float dp) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#777777"));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int)(5 * dp);
        tv.setLayoutParams(lp);
        return tv;
    }

    private boolean isToday(String dateStr) {
        return dateStr.equals(new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(new Date()));
    }

    private int getStatusColor(String status) {
        if (status == null) return Color.parseColor("#AAAAAA");
        switch (status) {
            case "completed":   return Color.parseColor("#4CAF50");
            case "partial":     return Color.parseColor("#FFC107");
            case "failed":      return Color.parseColor("#E53935");
            case "in_progress": return Color.parseColor("#FF8C42");
            case "planned":     return Color.parseColor("#5D65D9");
            default:            return Color.parseColor("#AAAAAA");
        }
    }

    private String getStatusLabel(String status) {
        if (status == null) return "Unknown";
        switch (status) {
            case "completed":   return "Completed";
            case "partial":     return "Partial";
            case "failed":      return "Failed";
            case "in_progress": return "In Progress";
            case "planned":     return "Planned";
            default:            return status;
        }
    }
}
