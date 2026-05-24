package com.example.oneday;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
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
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class StatsFragment extends Fragment {

    private LinearLayout scrollContainer;
    private SessionManager session;

    private StatsWeeklyResponse weeklyData;
    private StatsStreakResponse streakData;
    private StatsSummaryResponse summaryData;
    private int loadedCount = 0;

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

        loadAll();
    }

    private void loadAll() {
        loadedCount = 0;
        weeklyData  = null;
        streakData  = null;
        summaryData = null;

        String token = "Bearer " + session.getToken();

        ApiClient.get().getStatsWeekly(token).enqueue(new Callback<StatsWeeklyResponse>() {
            @Override public void onResponse(@NonNull Call<StatsWeeklyResponse> call,
                                             @NonNull Response<StatsWeeklyResponse> r) {
                if (!isAdded()) return;
                weeklyData = r.body();
                onDataReady();
            }
            @Override public void onFailure(@NonNull Call<StatsWeeklyResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                onDataReady();
            }
        });

        ApiClient.get().getStatsStreak(token).enqueue(new Callback<StatsStreakResponse>() {
            @Override public void onResponse(@NonNull Call<StatsStreakResponse> call,
                                             @NonNull Response<StatsStreakResponse> r) {
                if (!isAdded()) return;
                streakData = r.body();
                onDataReady();
            }
            @Override public void onFailure(@NonNull Call<StatsStreakResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                onDataReady();
            }
        });

        ApiClient.get().getStatsSummary(token).enqueue(new Callback<StatsSummaryResponse>() {
            @Override public void onResponse(@NonNull Call<StatsSummaryResponse> call,
                                             @NonNull Response<StatsSummaryResponse> r) {
                if (!isAdded()) return;
                summaryData = r.body();
                onDataReady();
            }
            @Override public void onFailure(@NonNull Call<StatsSummaryResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                onDataReady();
            }
        });
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
    }

    private View buildWeeklyCard(float dp) {
        MaterialCardView card = makeCard(dp);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = (int)(12 * dp);
        card.setLayoutParams(clp);

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(18*dp));
        inner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        float[] values = new float[7];
        String[] dayLabels = new String[7];
        float avgProgress = 0;

        if (weeklyData != null && weeklyData.days != null && !weeklyData.days.isEmpty()) {
            List<StatsWeeklyResponse.WeekDay> days = weeklyData.days;
            float sum = 0;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
            SimpleDateFormat dayFmt = new SimpleDateFormat("EEE", Locale.ENGLISH);
            for (int i = 0; i < Math.min(7, days.size()); i++) {
                values[i] = days.get(i).progressPercent;
                sum += values[i];
                try {
                    Date d = sdf.parse(days.get(i).date);
                    dayLabels[i] = dayFmt.format(d).substring(0, 1);
                } catch (Exception e) {
                    dayLabels[i] = String.valueOf(i + 1);
                }
            }
            avgProgress = days.isEmpty() ? 0 : sum / days.size();
        } else {
            String[] defaults = {"M", "T", "W", "T", "F", "S", "S"};
            System.arraycopy(defaults, 0, dayLabels, 0, 7);
        }

        LinearLayout headerRow = new LinearLayout(requireContext());
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hrlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hrlp.bottomMargin = (int)(2 * dp);
        headerRow.setLayoutParams(hrlp);

        TextView tvLabel = sectionLabel("WEEKLY PROGRESS", dp);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvPct = new TextView(requireContext());
        tvPct.setText(String.format(Locale.ENGLISH, "%.0f%%", avgProgress));
        tvPct.setTextColor(Color.parseColor("#5D65D9"));
        tvPct.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
        tvPct.setTypeface(null, Typeface.BOLD);

        headerRow.addView(tvLabel);
        headerRow.addView(tvPct);

        TextView tvSub = new TextView(requireContext());
        tvSub.setText("Last 7 days");
        tvSub.setTextColor(Color.parseColor("#AAAAAA"));
        tvSub.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        LinearLayout.LayoutParams sublp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sublp.bottomMargin = (int)(16 * dp);
        tvSub.setLayoutParams(sublp);

        BarChartView chart = new BarChartView(requireContext(), values, dayLabels);
        chart.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(140 * dp)));

        inner.addView(headerRow);
        inner.addView(tvSub);
        inner.addView(chart);
        card.addView(inner);
        return card;
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

        MaterialCardView donutCard = makeCard(dp);
        LinearLayout.LayoutParams dclp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        dclp.setMarginEnd((int)(6 * dp));
        donutCard.setLayoutParams(dclp);

        LinearLayout donutInner = new LinearLayout(requireContext());
        donutInner.setOrientation(LinearLayout.VERTICAL);
        donutInner.setPadding((int)(14*dp), (int)(14*dp), (int)(14*dp), (int)(16*dp));
        donutInner.setGravity(Gravity.CENTER_HORIZONTAL);
        donutInner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        donutInner.addView(sectionLabel("CURRENT STREAK", dp));

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
        tv.setTextColor(Color.parseColor("#222222"));
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
            bgArcPaint.setColor(Color.parseColor("#ECEEFE"));

            fillArcPaint.setStyle(Paint.Style.STROKE);
            fillArcPaint.setColor(Color.parseColor("#5D65D9"));
            fillArcPaint.setStrokeCap(Paint.Cap.ROUND);

            numPaint.setColor(Color.parseColor("#5D65D9"));
            numPaint.setTypeface(Typeface.DEFAULT_BOLD);
            numPaint.setTextAlign(Paint.Align.CENTER);

            labelPaint.setColor(Color.parseColor("#AAAAAA"));
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
