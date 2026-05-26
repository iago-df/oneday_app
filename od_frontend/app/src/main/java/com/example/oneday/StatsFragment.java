package com.example.oneday;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.oneday.api.ApiClient;
import com.example.oneday.api.models.StatsStreakResponse;
import com.example.oneday.api.models.StatsSummaryResponse;
import com.example.oneday.api.models.StatsWeeklyResponse;
import com.example.oneday.session.SessionManager;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class StatsFragment extends Fragment {

    private LinearLayout scrollContainer;
    private LinearLayout weeklyCardInner;
    private TextView tvWeekNext;
    private SessionManager session;

    private StatsWeeklyResponse weeklyData;
    private StatsStreakResponse streakData;
    private StatsSummaryResponse summaryData;
    private int loadedCount = 0;
    private Calendar currentWeekStart;
    private boolean lastNavGoingBack = false;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_stats, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session = new SessionManager(requireContext());
        scrollContainer = view.findViewById(R.id.statsScrollContainer);

        TextView tvDate = view.findViewById(R.id.tvDate);
        tvDate.setText(new SimpleDateFormat("EEE, MMM d", Locale.ENGLISH).format(new Date()));

        currentWeekStart = getThisMonday();
        loadAll();
    }

    private Calendar getThisMonday() {
        Calendar cal = Calendar.getInstance();
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        cal.add(Calendar.DAY_OF_MONTH, -((dow + 5) % 7));
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal;
    }

    private String calToString(Calendar cal) {
        return String.format(Locale.ENGLISH, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH));
    }

    private String formatWeekRange(Calendar monday) {
        Calendar sunday = (Calendar) monday.clone();
        sunday.add(Calendar.DAY_OF_MONTH, 6);
        SimpleDateFormat dayFmt      = new SimpleDateFormat("d", Locale.ENGLISH);
        SimpleDateFormat monthDayFmt = new SimpleDateFormat("MMM d", Locale.ENGLISH);
        if (monday.get(Calendar.MONTH) == sunday.get(Calendar.MONTH)) {
            return new SimpleDateFormat("MMM", Locale.ENGLISH).format(monday.getTime())
                    + " " + dayFmt.format(monday.getTime())
                    + " – " + dayFmt.format(sunday.getTime());
        } else {
            return monthDayFmt.format(monday.getTime()) + " – " + monthDayFmt.format(sunday.getTime());
        }
    }

    private boolean isCurrentWeek() {
        return calToString(currentWeekStart).equals(calToString(getThisMonday()));
    }


    private void loadAll() {
        loadedCount = 0;
        String token = "Bearer " + session.getToken();
        String[] range = weekRange();

        ApiClient.get().getStatsWeekly(token, range[0], range[1])
                .enqueue(new Callback<StatsWeeklyResponse>() {
                    @Override public void onResponse(@NonNull Call<StatsWeeklyResponse> c,
                                                     @NonNull Response<StatsWeeklyResponse> r) {
                        if (!isAdded()) return;
                        weeklyData = r.body();
                        onDataReady();
                    }
                    @Override public void onFailure(@NonNull Call<StatsWeeklyResponse> c, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        onDataReady();
                    }
                });

        ApiClient.get().getStatsStreak(token).enqueue(new Callback<StatsStreakResponse>() {
            @Override public void onResponse(@NonNull Call<StatsStreakResponse> c,
                                             @NonNull Response<StatsStreakResponse> r) {
                if (!isAdded()) return;
                streakData = r.body();
                onDataReady();
            }
            @Override public void onFailure(@NonNull Call<StatsStreakResponse> c, @NonNull Throwable t) {
                if (!isAdded()) return;
                onDataReady();
            }
        });

        ApiClient.get().getStatsSummary(token).enqueue(new Callback<StatsSummaryResponse>() {
            @Override public void onResponse(@NonNull Call<StatsSummaryResponse> c,
                                             @NonNull Response<StatsSummaryResponse> r) {
                if (!isAdded()) return;
                summaryData = r.body();
                onDataReady();
            }
            @Override public void onFailure(@NonNull Call<StatsSummaryResponse> c, @NonNull Throwable t) {
                if (!isAdded()) return;
                onDataReady();
            }
        });
    }

    private void loadWeeklyOnly() {
        String token = "Bearer " + session.getToken();
        String[] range = weekRange();
        ApiClient.get().getStatsWeekly(token, range[0], range[1])
                .enqueue(new Callback<StatsWeeklyResponse>() {
                    @Override public void onResponse(@NonNull Call<StatsWeeklyResponse> c,
                                                     @NonNull Response<StatsWeeklyResponse> r) {
                        if (!isAdded()) return;
                        weeklyData = r.body();
                        requireActivity().runOnUiThread(() -> rebuildWeeklyContent());
                    }
                    @Override public void onFailure(@NonNull Call<StatsWeeklyResponse> c, @NonNull Throwable t) {}
                });
    }

    private String[] weekRange() {
        Calendar end = (Calendar) currentWeekStart.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);
        return new String[]{ calToString(currentWeekStart), calToString(end) };
    }


    private void prevWeek() {
        lastNavGoingBack = true;
        currentWeekStart.add(Calendar.DAY_OF_MONTH, -7);
        loadWeeklyOnly();
    }

    private void nextWeek() {
        if (isCurrentWeek()) return;
        lastNavGoingBack = false;
        currentWeekStart.add(Calendar.DAY_OF_MONTH, 7);
        loadWeeklyOnly();
    }

    private synchronized void onDataReady() {
        loadedCount++;
        if (loadedCount < 3) return;
        requireActivity().runOnUiThread(this::buildUI);
    }


    private void buildUI() {
        if (scrollContainer == null || !isAdded()) return;
        scrollContainer.removeAllViews();
        float dp = requireContext().getResources().getDisplayMetrics().density;

        scrollContainer.addView(buildWeeklyCard(dp));
        scrollContainer.addView(buildStreakRow(dp));
        scrollContainer.addView(buildSummaryRow(dp));
        scrollContainer.addView(buildProfileCard(dp));
    }


    private View buildWeeklyCard(float dp) {
        MaterialCardView card = makeCard(dp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = (int)(12 * dp);
        card.setLayoutParams(clp);
        card.setClickable(true);

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(18*dp));
        inner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        weeklyCardInner = inner;

        buildWeeklyContent(dp);
        card.addView(inner);

        GestureDetector gd = new GestureDetector(requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent e) { return true; }
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float vX, float vY) {
                        if (e1 == null) return false;
                        float dx = e2.getX() - e1.getX();
                        if (Math.abs(dx) > 80 && Math.abs(vX) > 100) {
                            if (dx < 0) nextWeek(); else prevWeek();
                            return true;
                        }
                        return false;
                    }
                });
        card.setOnTouchListener((v, event) -> gd.onTouchEvent(event));
        return card;
    }

    private void rebuildWeeklyContent() {
        if (weeklyCardInner == null || !isAdded()) return;
        float dp = requireContext().getResources().getDisplayMetrics().density;
        int width = weeklyCardInner.getWidth();

        if (width == 0) {
            weeklyCardInner.removeAllViews();
            buildWeeklyContent(dp);
            return;
        }

        if (weeklyCardInner.getParent() instanceof ViewGroup) {
            ((ViewGroup) weeklyCardInner.getParent()).setClipChildren(false);
        }

        float slideOutX = lastNavGoingBack ?  width : -width;
        float slideInX  = lastNavGoingBack ? -width :  width;

        weeklyCardInner.animate()
                .translationX(slideOutX)
                .setDuration(200)
                .withEndAction(() -> {
                    if (!isAdded()) return;
                    weeklyCardInner.removeAllViews();
                    buildWeeklyContent(dp);
                    weeklyCardInner.setTranslationX(slideInX);
                    weeklyCardInner.animate()
                            .translationX(0f)
                            .setDuration(200)
                            .start();
                })
                .start();
    }

    private void buildWeeklyContent(float dp) {
        float[] values = new float[7];
        float avgProgress = 0;

        if (weeklyData != null && weeklyData.days != null && !weeklyData.days.isEmpty()) {
            List<StatsWeeklyResponse.WeekDay> days = weeklyData.days;
            float sum = 0;
            for (int i = 0; i < Math.min(7, days.size()); i++) {
                values[i] = days.get(i).progressPercent;
                sum += values[i];
            }
            avgProgress = days.isEmpty() ? 0 : sum / days.size();
        }

        LinearLayout topRow = new LinearLayout(requireContext());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        trlp.bottomMargin = (int)(4 * dp);
        topRow.setLayoutParams(trlp);

        TextView tvLabel = sectionLabel("WEEKLY PROGRESS", dp);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvPct = new TextView(requireContext());
        tvPct.setText(String.format(Locale.ENGLISH, "%.0f%%", avgProgress));
        tvPct.setTextColor(Color.parseColor("#5D65D9"));
        tvPct.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
        tvPct.setTypeface(null, Typeface.BOLD);

        topRow.addView(tvLabel);
        topRow.addView(tvPct);
        weeklyCardInner.addView(topRow);

        LinearLayout navRow = new LinearLayout(requireContext());
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams nrlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nrlp.bottomMargin = (int)(16 * dp);
        navRow.setLayoutParams(nrlp);

        TextView tvPrev = new TextView(requireContext());
        tvPrev.setText("‹");
        tvPrev.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        tvPrev.setTextColor(Color.parseColor("#5D65D9"));
        tvPrev.setTypeface(null, Typeface.BOLD);
        tvPrev.setLayoutParams(new LinearLayout.LayoutParams((int)(28*dp), (int)(28*dp)));
        tvPrev.setGravity(Gravity.CENTER);
        tvPrev.setOnClickListener(v -> prevWeek());

        TextView tvWeekLabel = new TextView(requireContext());
        tvWeekLabel.setText(formatWeekRange(currentWeekStart));
        tvWeekLabel.setTextColor(Color.parseColor("#AAAAAA"));
        tvWeekLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        tvWeekLabel.setGravity(Gravity.CENTER);
        tvWeekLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        tvWeekNext = new TextView(requireContext());
        tvWeekNext.setText("›");
        tvWeekNext.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        tvWeekNext.setTypeface(null, Typeface.BOLD);
        tvWeekNext.setLayoutParams(new LinearLayout.LayoutParams((int)(28*dp), (int)(28*dp)));
        tvWeekNext.setGravity(Gravity.CENTER);
        tvWeekNext.setOnClickListener(v -> nextWeek());
        tvWeekNext.setTextColor(Color.parseColor(isCurrentWeek() ? "#CCCCCC" : "#5D65D9"));

        navRow.addView(tvPrev);
        navRow.addView(tvWeekLabel);
        navRow.addView(tvWeekNext);
        weeklyCardInner.addView(navRow);

        String[] dayLabels = {"Mo","Tu","We","Th","Fr","Sa","Su"};
        BarChartView chart = new BarChartView(requireContext(), values, dayLabels);
        chart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(140 * dp)));
        weeklyCardInner.addView(chart);
    }

    private View buildStreakRow(float dp) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = (int)(12 * dp);
        row.setLayoutParams(rlp);

        int currentStreak = streakData != null ? streakData.currentStreak : 0;
        int bestStreak    = streakData != null ? streakData.bestStreak    : 0;

        MaterialCardView donutCard = new MaterialCardView(requireContext());
        donutCard.setCardBackgroundColor(Color.parseColor("#5D65D9"));
        donutCard.setRadius(14 * dp);
        donutCard.setCardElevation(0);
        donutCard.setStrokeWidth(0);
        LinearLayout.LayoutParams dclp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        dclp.setMarginEnd((int)(6 * dp));
        donutCard.setLayoutParams(dclp);

        LinearLayout donutInner = new LinearLayout(requireContext());
        donutInner.setOrientation(LinearLayout.VERTICAL);
        donutInner.setPadding((int)(14*dp), (int)(14*dp), (int)(14*dp), (int)(16*dp));
        donutInner.setGravity(Gravity.CENTER_HORIZONTAL);
        donutInner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        donutInner.addView(whiteLabel("CURRENT STREAK", dp));

        DonutChartView donut = new DonutChartView(requireContext(), currentStreak,
                Math.max(bestStreak, Math.max(currentStreak, 1)));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams((int)(110*dp), (int)(110*dp));
        dlp.topMargin = (int)(10 * dp);
        dlp.gravity = Gravity.CENTER_HORIZONTAL;
        donut.setLayoutParams(dlp);
        donutInner.addView(donut);
        donutCard.addView(donutInner);

        MaterialCardView rightCard = makeCard(dp);
        rightCard.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout rightInner = new LinearLayout(requireContext());
        rightInner.setOrientation(LinearLayout.VERTICAL);
        rightInner.setPadding((int)(14*dp), (int)(14*dp), (int)(14*dp), (int)(16*dp));
        rightInner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        rightInner.addView(sectionLabel("BEST STREAK", dp));
        rightInner.addView(bigNumber(String.valueOf(bestStreak), dp));
        rightInner.addView(smallLabel("days", dp));

        View divider = new View(requireContext());
        LinearLayout.LayoutParams divlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * dp));
        divlp.topMargin    = (int)(14 * dp);
        divlp.bottomMargin = (int)(14 * dp);
        divider.setLayoutParams(divlp);
        divider.setBackgroundColor(Color.parseColor("#ECEEFE"));
        rightInner.addView(divider);

        rightInner.addView(sectionLabel("LAST ACTIVE", dp));
        String lastDate = (streakData != null && streakData.lastClosedDate != null)
                ? formatDate(streakData.lastClosedDate) : "—";
        rightInner.addView(bigNumber(lastDate, dp));

        rightCard.addView(rightInner);

        row.addView(donutCard);
        row.addView(rightCard);
        return row;
    }


    private View buildProfileCard(float dp) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(Color.parseColor("#5D65D9"));
        card.setRadius(14 * dp);
        card.setCardElevation(0);
        card.setStrokeWidth(0);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = (int)(12 * dp);
        card.setLayoutParams(clp);

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        String name     = session.getName();
        String username = session.getUsername();
        String email    = session.getEmail();
        String displayName = (name != null && !name.isEmpty()) ? name
                : (username != null ? username : "User");
        String initial = displayName.substring(0, 1).toUpperCase(Locale.ENGLISH);

        android.widget.FrameLayout avatarFrame = new android.widget.FrameLayout(requireContext());
        int avatarSize = (int)(44 * dp);
        LinearLayout.LayoutParams aflp = new LinearLayout.LayoutParams(avatarSize, avatarSize);
        aflp.setMarginEnd((int)(12 * dp));
        avatarFrame.setLayoutParams(aflp);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor("#7A80E0"));
        avatarFrame.setBackground(circle);
        TextView tvInitial = new TextView(requireContext());
        tvInitial.setText(initial);
        tvInitial.setTextColor(Color.WHITE);
        tvInitial.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        tvInitial.setTypeface(null, Typeface.BOLD);
        tvInitial.setGravity(Gravity.CENTER);
        tvInitial.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT));
        avatarFrame.addView(tvInitial);
        row.addView(avatarFrame);

        LinearLayout textCol = new LinearLayout(requireContext());
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(requireContext());
        tvName.setText(displayName);
        tvName.setTextColor(Color.WHITE);
        tvName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        textCol.addView(tvName);

        if (email != null && !email.isEmpty()) {
            TextView tvEmail = new TextView(requireContext());
            tvEmail.setText(email);
            tvEmail.setTextColor(Color.parseColor("#BEC3F0"));
            tvEmail.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            tvEmail.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            textCol.addView(tvEmail);
        }
        row.addView(textCol);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setColor(Color.WHITE);
        btnBg.setCornerRadius(18 * dp);

        TextView btnLogout = new TextView(requireContext());
        btnLogout.setText("Log out");
        btnLogout.setTextColor(Color.parseColor("#5D65D9"));
        btnLogout.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
        btnLogout.setTypeface(null, Typeface.BOLD);
        btnLogout.setGravity(Gravity.CENTER);
        btnLogout.setBackground(btnBg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams((int)(82 * dp), (int)(34 * dp));
        blp.setMarginStart((int)(10 * dp));
        btnLogout.setLayoutParams(blp);
        btnLogout.setOnClickListener(v -> {
            session.clearSession();
            android.content.Intent intent = new android.content.Intent(
                    requireContext(), LoginActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
        row.addView(btnLogout);

        card.addView(row);
        return card;
    }


    private View buildSummaryRow(float dp) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        int completed = 0, hours = 0;
        float rate = 0;
        if (summaryData != null && summaryData.activities != null) {
            completed = summaryData.activities.completed;
            hours     = summaryData.activities.totalMinutes / 60;
            rate      = summaryData.activities.completionRate;
        }

        View c1 = buildMiniStatCard("ACTIVITIES", String.valueOf(completed), "done", dp);
        View c2 = buildMiniStatCard("HOURS", String.valueOf(hours), "logged", dp);
        View c3 = buildMiniStatCard("RATE", String.format(Locale.ENGLISH, "%.0f%%", rate), "completion", dp);

        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp1.setMarginEnd((int)(6 * dp));
        c1.setLayoutParams(lp1);

        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp2.setMarginEnd((int)(6 * dp));
        c2.setLayoutParams(lp2);

        c3.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(c1);
        row.addView(c2);
        row.addView(c3);
        return row;
    }

    private View buildMiniStatCard(String title, String value, String unit, float dp) {
        MaterialCardView card = makeCard(dp);
        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding((int)(10*dp), (int)(14*dp), (int)(10*dp), (int)(14*dp));
        inner.setGravity(Gravity.CENTER_HORIZONTAL);
        inner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        inner.addView(sectionLabel(title, dp));
        inner.addView(bigNumber(value, dp));
        inner.addView(smallLabel(unit, dp));

        card.addView(inner);
        return card;
    }


    private MaterialCardView makeCard(float dp) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(14 * dp);
        card.setCardElevation(0);
        card.setStrokeWidth((int)(3 * dp));
        card.setStrokeColor(Color.parseColor("#ECEEFE"));
        return card;
    }

    private TextView sectionLabel(String text, float dp) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#5D65D9"));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int)(4 * dp);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView bigNumber(String text, float dp) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#5D65D9"));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private TextView smallLabel(String text, float dp) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#AAAAAA"));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private TextView whiteLabel(String text, float dp) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#BEC3F0"));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int)(4 * dp);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView whiteNumber(String text, float dp) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    private TextView whiteSubLabel(String text, float dp) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#BEC3F0"));
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return tv;
    }


    private String formatDate(String iso) {
        try {
            SimpleDateFormat in  = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            SimpleDateFormat out = new SimpleDateFormat("MMM d", Locale.ENGLISH);
            return out.format(in.parse(iso));
        } catch (Exception e) {
            return iso;
        }
    }


    static class BarChartView extends View {
        private final float[] values;
        private final String[] labels;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        BarChartView(Context ctx, float[] values, String[] labels) {
            super(ctx);
            this.values = values;
            this.labels = labels;
            fillPaint.setColor(Color.parseColor("#5D65D9"));
            bgPaint.setColor(Color.parseColor("#ECEEFE"));
            textPaint.setColor(Color.parseColor("#AAAAAA"));
            textPaint.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float dp = getResources().getDisplayMetrics().density;
            int W = getWidth();
            int H = getHeight();
            float labelH = 22 * dp;
            float barAreaH = H - labelH;
            int n = values.length;
            float gap = 7 * dp;
            float barW = (W - gap * (n + 1)) / n;
            float cornerR = barW / 2f;

            textPaint.setTextSize(11 * dp);

            for (int i = 0; i < n; i++) {
                float left  = gap + i * (barW + gap);
                float right = left + barW;
                float barBottom = barAreaH - 4 * dp;
                float barTop    = 0;

                canvas.drawRoundRect(new RectF(left, barTop, right, barBottom), cornerR, cornerR, bgPaint);

                float pct = Math.max(0, Math.min(100, values[i]));
                float fillH = (barBottom - barTop) * (pct / 100f);
                if (fillH >= cornerR * 2) {
                    float fillTop = barBottom - fillH;
                    canvas.drawRoundRect(new RectF(left, fillTop, right, barBottom), cornerR, cornerR, fillPaint);
                } else if (fillH > 0) {
                    canvas.drawRoundRect(new RectF(left, barBottom - cornerR * 2, right, barBottom),
                            cornerR, cornerR, fillPaint);
                }

                canvas.drawText(labels[i], left + barW / 2f, H - 4 * dp, textPaint);
            }
        }
    }


    static class DonutChartView extends View {
        private final int value;
        private final int maxVal;
        private final Paint bgArcPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fillArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint numPaint     = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint labelPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);


        DonutChartView(Context ctx, int value, int maxVal) {
            super(ctx);
            this.value  = value;
            this.maxVal = maxVal > 0 ? maxVal : 1;
            bgArcPaint.setStyle(Paint.Style.STROKE);
            bgArcPaint.setColor(Color.parseColor("#7A80E0"));
            fillArcPaint.setStyle(Paint.Style.STROKE);
            fillArcPaint.setColor(Color.WHITE);
            fillArcPaint.setStrokeCap(Paint.Cap.ROUND);
            numPaint.setColor(Color.WHITE);
            numPaint.setTypeface(Typeface.DEFAULT_BOLD);
            numPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setColor(Color.parseColor("#BEC3F0"));
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTypeface(Typeface.DEFAULT_BOLD);
            labelPaint.setLetterSpacing(0.1f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float dp = getResources().getDisplayMetrics().density;
            float strokeW = 14 * dp;
            bgArcPaint.setStrokeWidth(strokeW);
            fillArcPaint.setStrokeWidth(strokeW);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy) - strokeW / 2f - 2 * dp;

            RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

            canvas.drawArc(oval, -90, 360, false, bgArcPaint);

            if (value > 0) {
                float sweep = (value / (float) maxVal) * 360f;
                canvas.drawArc(oval, -90, Math.min(sweep, 360), false, fillArcPaint);
            }

            numPaint.setTextSize(26 * dp);
            Paint.FontMetrics fm = numPaint.getFontMetrics();
            float textCenterY = cy - (fm.ascent + fm.descent) / 2f - 7 * dp;
            canvas.drawText(String.valueOf(value), cx, textCenterY, numPaint);

            labelPaint.setTextSize(9 * dp);
            canvas.drawText("DAYS", cx, textCenterY + 16 * dp, labelPaint);
        }
    }
}
