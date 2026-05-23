package com.example.oneday;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.oneday.api.ApiClient;
import com.example.oneday.api.models.ActivityData;
import com.example.oneday.api.models.DashboardResponse;
import com.example.oneday.session.SessionManager;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TodayFragment extends Fragment {

    private TextView tvDate, tvGreeting, tvMainGoal, tvProgressPct;
    private ProgressBar progressBar;
    private LinearLayout timelineContainer, unanchoredContainer;
    private LinearLayout dotsLayout;
    private ViewPager2 viewPager;
    private SectionAdapter sectionAdapter;
    private SessionManager session;
    private int currentDayEntryId = -1;
    private int currentPage = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_today, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvDate = view.findViewById(R.id.tvDate);
        tvGreeting = view.findViewById(R.id.tvGreeting);
        tvMainGoal = view.findViewById(R.id.tvMainGoal);
        tvProgressPct = view.findViewById(R.id.tvProgressPct);
        progressBar = view.findViewById(R.id.progressBar);
        viewPager = view.findViewById(R.id.viewPager);
        dotsLayout = view.findViewById(R.id.dotsLayout);

        session = new SessionManager(requireContext());

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MMM d", Locale.ENGLISH);
        tvDate.setText(sdf.format(new Date()));

        String name = session.getName();
        tvGreeting.setText("Hello, " + (name != null ? name : "there"));

        // ViewPager2 setup
        sectionAdapter = new SectionAdapter();
        viewPager.setOffscreenPageLimit(1); // keep both pages alive
        viewPager.setAdapter(sectionAdapter);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                setupDots(position);
            }
        });

        // Get container references after ViewPager2 lays out its pages, then load data
        viewPager.post(() -> {
            View p0 = sectionAdapter.getPage(0);
            View p1 = sectionAdapter.getPage(1);
            if (p0 != null) timelineContainer = p0.findViewById(R.id.timelineContainer);
            if (p1 != null) unanchoredContainer = p1.findViewById(R.id.unanchoredContainer);
            viewPager.setCurrentItem(currentPage, false);
            setupDots(currentPage);
            loadDashboard();
        });
    }

    private void setupDots(int activePage) {
        dotsLayout.removeAllViews();
        int dotSize = (int)(8 * requireContext().getResources().getDisplayMetrics().density);
        int dotMargin = (int)(5 * requireContext().getResources().getDisplayMetrics().density);
        for (int i = 0; i < 2; i++) {
            View dot = new View(requireContext());
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dotSize, dotSize);
            lp.leftMargin = dotMargin;
            lp.rightMargin = dotMargin;
            dot.setLayoutParams(lp);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(i == activePage ? Color.parseColor("#5D65D9") : Color.parseColor("#C4C8F4"));
            dot.setBackground(d);
            dotsLayout.addView(dot);
        }
    }

    private void loadDashboard() {
        String token = "Bearer " + session.getToken();
        ApiClient.get().getDashboard(token).enqueue(new Callback<DashboardResponse>() {
            @Override
            public void onResponse(@NonNull Call<DashboardResponse> call,
                                   @NonNull Response<DashboardResponse> response) {
                if (!isAdded() || response.body() == null) return;
                populateUI(response.body());
            }

            @Override
            public void onFailure(@NonNull Call<DashboardResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Could not load dashboard", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void populateUI(DashboardResponse data) {
        if (data.today != null) currentDayEntryId = data.today.id;
        if (data.today != null && data.today.mainGoal != null) {
            tvMainGoal.setText(data.today.mainGoal.title);
        } else {
            tvMainGoal.setText("No goal set for today");
        }
        int progress = data.today != null ? (int) data.today.progressPercent : 0;
        progressBar.setProgress(progress);
        tvProgressPct.setText(progress + "%");

        List<ActivityData> activities = data.activitiesToday != null
                ? data.activitiesToday : new ArrayList<>();
        buildTimeline(activities);
    }

    private void buildTimeline(List<ActivityData> activities) {
        if (timelineContainer == null || unanchoredContainer == null) return;
        timelineContainer.removeAllViews();
        unanchoredContainer.removeAllViews();

        List<ActivityData> anchored = new ArrayList<>();
        List<ActivityData> unanchored = new ArrayList<>();

        for (ActivityData a : activities) {
            if (a.startTime != null && !a.startTime.isEmpty()) {
                anchored.add(a);
            } else {
                unanchored.add(a);
            }
        }

        Collections.sort(anchored, (a, b) -> a.startTime.compareTo(b.startTime));

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        if (anchored.isEmpty()) {
            addEmptyState(timelineContainer, "No activities for today yet", "Add activity", true);
        } else {
            for (int i = 0; i < anchored.size(); i++) {
                timelineContainer.addView(buildActivityItem(inflater, anchored.get(i), false));
            }
            timelineContainer.addView(buildAddButton("Add activity", true));
        }

        if (unanchored.isEmpty()) {
            addEmptyState(unanchoredContainer, "No tasks for today yet", "Add task", false);
        } else {
            for (int i = 0; i < unanchored.size(); i++) {
                unanchoredContainer.addView(buildActivityItem(inflater, unanchored.get(i), true));
            }
            unanchoredContainer.addView(buildAddButton("Add task", false));
        }
    }

    private View buildAddButton(String label, boolean isTimeline) {
        float dp = requireContext().getResources().getDisplayMetrics().density;
        LinearLayout targetContainer = isTimeline ? timelineContainer : unanchoredContainer;

        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.HORIZONTAL);
        wrapper.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wlp.topMargin = (int)(20 * dp);
        wlp.bottomMargin = (int)(10 * dp);
        wrapper.setLayoutParams(wlp);

        android.widget.Button btn = new android.widget.Button(requireContext());
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.parseColor("#5D65D9"));
        bg.setCornerRadius(14 * dp);
        btn.setBackground(bg);
        btn.setPadding(0, (int)(14 * dp), 0, (int)(14 * dp));
        btn.setLayoutParams(new LinearLayout.LayoutParams((int)(220 * dp), LinearLayout.LayoutParams.WRAP_CONTENT));
        btn.setOnClickListener(v -> showAddForm(targetContainer, isTimeline));

        wrapper.addView(btn);
        return wrapper;
    }

    private void addEmptyState(LinearLayout container, String message, String buttonLabel, boolean isTimeline) {
        float dp = requireContext().getResources().getDisplayMetrics().density;

        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        wlp.topMargin = (int)(70 * dp);
        wrapper.setLayoutParams(wlp);

        // Icon
        android.widget.ImageView icon = new android.widget.ImageView(requireContext());
        int iconSize = (int)(86 * dp);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ilp.bottomMargin = (int)(12 * dp);
        icon.setLayoutParams(ilp);
        icon.setImageResource(R.drawable.ic_no_goals);
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        wrapper.addView(icon);

        // Text
        TextView text = new TextView(requireContext());
        text.setText(message);
        text.setTextColor(Color.parseColor("#999999"));
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        text.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.bottomMargin = (int)(18 * dp);
        text.setLayoutParams(tlp);
        wrapper.addView(text);

        // Button
        android.widget.Button btn = new android.widget.Button(requireContext());
        btn.setText(buttonLabel);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTypeface(btn.getTypeface(), android.graphics.Typeface.BOLD);
        btn.setAllCaps(false);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setColor(Color.parseColor("#5D65D9"));
        btnBg.setCornerRadius(14 * dp);
        btn.setBackground(btnBg);
        int pv = (int)(14 * dp);
        btn.setPadding(0, pv, 0, pv);
        int btnWidth = (int)(220 * dp);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(btnWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        btn.setLayoutParams(blp);
        btn.setOnClickListener(v -> showAddForm(container, isTimeline));
        wrapper.addView(btn);

        container.addView(wrapper);
    }

    private View buildActivityItem(LayoutInflater inflater, ActivityData activity, boolean isQuick) {
        View item = inflater.inflate(isQuick ? R.layout.item_activity_quick : R.layout.item_activity, null, false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        if (isQuick) {
            int gap = (int)(5 * requireContext().getResources().getDisplayMetrics().density);
            lp.bottomMargin = gap;
            lp.topMargin = gap / 2;
        }
        item.setLayoutParams(lp);

        TextView tvTime = item.findViewById(R.id.tvTime);
        View vDot = item.findViewById(R.id.vDot);
        TextView tvTitle = item.findViewById(R.id.tvTitle);
        ImageView ivCheck = item.findViewById(R.id.ivCheck);
        MaterialCardView card = isQuick
                ? (MaterialCardView) item
                : item.findViewById(R.id.cardView);

        if (tvTime != null) tvTime.setText(formatTime(activity.startTime));
        tvTitle.setText(activity.title);

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(getActivityColor(activity.activityType));
        vDot.setBackground(dot);


        int doneColor = getLightActivityColor(activity.activityType);

        boolean done = "completed".equals(activity.status);
        ivCheck.setImageResource(done ? R.drawable.ic_check_done : R.drawable.ic_check_empty);
        if (done) {
            card.setCardBackgroundColor(doneColor);
            tvTitle.setAlpha(0.45f);
        }
        item.setTag(done); // true = completed, used by scrollToNextItem

        final int activityId = activity.id;
        final boolean[] isDone = {done};
        final String deleteLabel = isQuick ? "Delete task?" : "Delete activity?";

        // Tap anywhere on card to toggle completion
        ivCheck.setClickable(false);
        ivCheck.setFocusable(false);
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(v -> {
            if (!isDone[0]) {
                isDone[0] = true;
                item.setTag(true);
                ivCheck.setImageResource(R.drawable.ic_check_done);
                tvTitle.setAlpha(0.45f);
                card.setCardBackgroundColor(doneColor);
                updateActivityStatus(activityId, "completed");
                scrollToNextItem(item);
            } else {
                isDone[0] = false;
                item.setTag(false);
                ivCheck.setImageResource(R.drawable.ic_check_empty);
                tvTitle.setAlpha(1.0f);
                card.setCardBackgroundColor(Color.WHITE);
                updateActivityStatus(activityId, "pending");
            }
        });

        // Long press → delete popup
        card.setOnLongClickListener(v -> {
            float dp = requireContext().getResources().getDisplayMetrics().density;

            android.widget.Button deleteBtn = new android.widget.Button(requireContext());
            deleteBtn.setText(deleteLabel);
            deleteBtn.setTextColor(Color.WHITE);
            deleteBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
            deleteBtn.setTypeface(null, android.graphics.Typeface.BOLD);
            deleteBtn.setAllCaps(false);
            GradientDrawable delBg = new GradientDrawable();
            delBg.setShape(GradientDrawable.RECTANGLE);
            delBg.setColor(Color.parseColor("#5D65D9"));
            delBg.setCornerRadius(10 * dp);
            deleteBtn.setBackground(delBg);
            deleteBtn.setPadding((int)(16 * dp), (int)(10 * dp), (int)(16 * dp), (int)(10 * dp));
            deleteBtn.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

            android.widget.PopupWindow popup = new android.widget.PopupWindow(
                    deleteBtn,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    true);
            popup.setOutsideTouchable(true);
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            popup.setElevation(8 * dp);
            popup.showAsDropDown(card, (int)(16 * dp),
                    -(card.getHeight() / 2 + deleteBtn.getMeasuredHeight() / 2));

            deleteBtn.setOnClickListener(dv -> {
                popup.dismiss();
                deleteActivityById(activityId, item, isQuick);
            });
            return true;
        });

        return item;
    }

    private void deleteActivityById(int id, View item, boolean wasQuick) {
        String token = "Bearer " + session.getToken();
        ApiClient.get().deleteActivity(id, token).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    ViewGroup parent = (ViewGroup) item.getParent();
                    if (parent != null) {
                        parent.removeView(item);
                        // If only the add button wrapper remains (child count == 1), show empty state
                        if (parent.getChildCount() <= 1) {
                            parent.removeAllViews();
                            boolean isTimeline = !wasQuick;
                            String msg = isTimeline ? "No activities for today yet" : "No tasks for today yet";
                            String lbl = isTimeline ? "Add activity" : "Add task";
                            addEmptyState((LinearLayout) parent, msg, lbl, isTimeline);
                        }
                    }
                } else {
                    Toast.makeText(getContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void scrollToNextItem(View completedItem) {
        ViewGroup container = (ViewGroup) completedItem.getParent();
        if (container == null) return;
        int index = -1;
        for (int i = 0; i < container.getChildCount(); i++) {
            if (container.getChildAt(i) == completedItem) {
                index = i;
                break;
            }
        }
        if (index < 0) return;
        // Find the next item that is NOT completed, skipping already-done ones
        View nextPending = null;
        for (int i = index + 1; i < container.getChildCount(); i++) {
            View candidate = container.getChildAt(i);
            if (!Boolean.TRUE.equals(candidate.getTag())) {
                nextPending = candidate;
                break;
            }
        }
        if (nextPending == null) return;
        final View target = nextPending;
        View scrollView = (View) container.getParent();
        if (scrollView instanceof androidx.core.widget.NestedScrollView) {
            androidx.core.widget.NestedScrollView nsv =
                    (androidx.core.widget.NestedScrollView) scrollView;
            nsv.post(() -> nsv.smoothScrollTo(0, target.getTop()));
        }
    }

    private String formatTime(String time) {
        if (time == null || time.isEmpty()) return "";
        try {
            String[] parts = time.split(":");
            int hour = Integer.parseInt(parts[0]);
            int min = Integer.parseInt(parts[1]);
            return String.format(Locale.getDefault(), "%02d:%02d", hour, min);
        } catch (Exception e) {
            return time;
        }
    }

    private int getActivityColor(String type) {
        if (type == null) return ContextCompat.getColor(requireContext(), R.color.colorPrimary);
        switch (type) {
            case "session":   return Color.parseColor("#FF8C42");
            case "habit":     return Color.parseColor("#4CAF50");
            case "event":     return Color.parseColor("#FFC107");
            case "deep_work": return Color.parseColor("#E53935");
            case "task":
            default:          return ContextCompat.getColor(requireContext(), R.color.colorPrimary);
        }
    }

    private int getLightActivityColor(String type) {
        int base = getActivityColor(type);
        // Blend 15% category color + 85% white for a very light tint
        int r = (int)(Color.red(base)   * 0.15f + 255 * 0.85f);
        int g = (int)(Color.green(base) * 0.15f + 255 * 0.85f);
        int b = (int)(Color.blue(base)  * 0.15f + 255 * 0.85f);
        return Color.rgb(r, g, b);
    }

    private void updateActivityStatus(int id, String status) {
        String token = "Bearer " + session.getToken();
        Map<String, Object> body = new HashMap<>();
        body.put("status", status);
        ApiClient.get().updateActivity(id, token, body).enqueue(new Callback<ActivityData>() {
            @Override
            public void onResponse(@NonNull Call<ActivityData> call,
                                   @NonNull Response<ActivityData> response) {
            }

            @Override
            public void onFailure(@NonNull Call<ActivityData> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Failed to update activity", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── Add form ──────────────────────────────────────────────────────────────

    private void showAddForm(LinearLayout container, boolean isTimeline) {
        container.removeAllViews();
        float dp = requireContext().getResources().getDisplayMetrics().density;

        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(4 * dp);
        form.setPadding(pad, (int)(8 * dp), pad, (int)(8 * dp));
        form.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Title
        TextView tvFormTitle = new TextView(requireContext());
        tvFormTitle.setText(isTimeline ? "Create new activity" : "Create new task");
        tvFormTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        tvFormTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvFormTitle.setTextColor(Color.parseColor("#5D65D9"));
        LinearLayout.LayoutParams ftlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ftlp.bottomMargin = (int)(18 * dp);
        tvFormTitle.setLayoutParams(ftlp);
        form.addView(tvFormTitle);

        // Activity/task name field
        android.widget.EditText etTitle = buildInputField(dp, isTimeline ? "Activity name" : "Task name", false);
        LinearLayout.LayoutParams etlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etlp.bottomMargin = (int)(14 * dp);
        etTitle.setLayoutParams(etlp);
        form.addView(buildFieldLabel(dp, "Title"));
        form.addView(etTitle);

        // Type chips
        form.addView(buildFieldLabel(dp, "Type"));
        String[] typeKeys  = {"task", "session", "habit", "event", "deep_work"};
        String[] typeNames = {"Task", "Session", "Habit", "Event", "Deep Work"};
        final String[] selectedType = {"task"};
        final TextView[] chips = new TextView[typeKeys.length];

        LinearLayout chipsWrap = new LinearLayout(requireContext());
        chipsWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cwlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cwlp.bottomMargin = (int)(14 * dp);
        chipsWrap.setLayoutParams(cwlp);

        LinearLayout row1 = new LinearLayout(requireContext());
        row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams r1lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        r1lp.bottomMargin = (int)(6 * dp);
        row1.setLayoutParams(r1lp);

        LinearLayout row2 = new LinearLayout(requireContext());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        for (int i = 0; i < typeKeys.length; i++) {
            final int idx = i;
            TextView chip = new TextView(requireContext());
            chip.setText(typeNames[i]);
            chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding((int)(8 * dp), (int)(7 * dp), (int)(8 * dp), (int)(7 * dp));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            clp.setMarginEnd((int)(4 * dp));
            chip.setLayoutParams(clp);
            chip.setOnClickListener(v -> {
                selectedType[0] = typeKeys[idx];
                for (int j = 0; j < typeKeys.length; j++) {
                    setChipSelected(chips[j], typeKeys[j], typeKeys[j].equals(selectedType[0]), dp);
                }
            });
            chips[i] = chip;
            setChipSelected(chip, typeKeys[i], i == 0, dp);
            if (i < 3) row1.addView(chip);
            else        row2.addView(chip);
        }
        // balance row2 with an invisible spacer for the 3rd slot
        View spacerChip = new View(requireContext());
        spacerChip.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        row2.addView(spacerChip);

        chipsWrap.addView(row1);
        chipsWrap.addView(row2);
        form.addView(chipsWrap);

        // Start time + duration (timeline only)
        final android.widget.EditText[] etTimeRef     = {null};
        final android.widget.EditText[] etDurationRef = {null};
        if (isTimeline) {
            android.widget.EditText etTime = buildInputField(dp, "HH:MM", false);
            etTime.setFocusable(false);
            etTime.setClickable(true);
            etTime.setOnClickListener(v ->
                    new android.app.TimePickerDialog(requireContext(), (tp, h, m) ->
                            etTime.setText(String.format(Locale.getDefault(), "%02d:%02d", h, m)),
                            8, 0, true).show());
            LinearLayout.LayoutParams ttlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ttlp.bottomMargin = (int)(14 * dp);
            etTime.setLayoutParams(ttlp);
            form.addView(buildFieldLabel(dp, "Start time"));
            form.addView(etTime);
            etTimeRef[0] = etTime;

            android.widget.EditText etDuration = buildInputField(dp, "Minutes", true);
            LinearLayout.LayoutParams dtlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            dtlp.bottomMargin = (int)(20 * dp);
            etDuration.setLayoutParams(dtlp);
            form.addView(buildFieldLabel(dp, "Duration (min)"));
            form.addView(etDuration);
            etDurationRef[0] = etDuration;
        }

        // Buttons row
        LinearLayout btnRow = new LinearLayout(requireContext());
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        int bpv = (int)(12 * dp);

        android.widget.Button btnCancel = new android.widget.Button(requireContext());
        btnCancel.setText("Cancel");
        btnCancel.setTextColor(Color.parseColor("#5D65D9"));
        btnCancel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        btnCancel.setTypeface(null, android.graphics.Typeface.BOLD);
        btnCancel.setAllCaps(false);
        GradientDrawable cancelBg = new GradientDrawable();
        cancelBg.setShape(GradientDrawable.RECTANGLE);
        cancelBg.setColor(Color.WHITE);
        cancelBg.setStroke((int)(2 * dp), Color.parseColor("#5D65D9"));
        cancelBg.setCornerRadius(12 * dp);
        btnCancel.setBackground(cancelBg);
        btnCancel.setPadding(0, bpv, 0, bpv);
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cblp.setMarginEnd((int)(8 * dp));
        btnCancel.setLayoutParams(cblp);
        btnCancel.setOnClickListener(v -> loadDashboard());

        android.widget.Button btnSave = new android.widget.Button(requireContext());
        btnSave.setText(isTimeline ? "Save activity" : "Save task");
        btnSave.setTextColor(Color.WHITE);
        btnSave.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        btnSave.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSave.setAllCaps(false);
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setShape(GradientDrawable.RECTANGLE);
        saveBg.setColor(Color.parseColor("#5D65D9"));
        saveBg.setCornerRadius(12 * dp);
        btnSave.setBackground(saveBg);
        btnSave.setPadding(0, bpv, 0, bpv);
        btnSave.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final android.widget.EditText etTimeCapture     = etTimeRef[0];
        final android.widget.EditText etDurationCapture = etDurationRef[0];
        btnSave.setOnClickListener(v -> {
            String titleText = etTitle.getText().toString().trim();
            if (titleText.isEmpty()) { etTitle.setError("Required"); return; }
            String time = (etTimeCapture != null) ? etTimeCapture.getText().toString().trim() : null;
            Integer duration = null;
            if (etDurationCapture != null) {
                String ds = etDurationCapture.getText().toString().trim();
                if (!ds.isEmpty()) try { duration = Integer.parseInt(ds); } catch (NumberFormatException ignored) {}
            }
            submitNewActivity(titleText, selectedType[0], time, duration);
        });

        btnRow.addView(btnCancel);
        btnRow.addView(btnSave);
        form.addView(btnRow);

        container.addView(form);
    }

    private TextView buildFieldLabel(float dp, String text) {
        TextView label = new TextView(requireContext());
        label.setText(text);
        label.setTextColor(Color.parseColor("#777777"));
        label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int)(4 * dp);
        lp.topMargin   = (int)(2 * dp);
        label.setLayoutParams(lp);
        return label;
    }

    private android.widget.EditText buildInputField(float dp, String hint, boolean numeric) {
        android.widget.EditText et = new android.widget.EditText(requireContext());
        et.setHint(hint);
        et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        et.setTextColor(Color.parseColor("#222222"));
        et.setHintTextColor(Color.parseColor("#BBBBBB"));
        if (numeric) et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        et.setPadding((int)(12 * dp), (int)(10 * dp), (int)(12 * dp), (int)(10 * dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.parseColor("#F8F8FC"));
        bg.setStroke((int)(1.5f * dp), Color.parseColor("#DDDDE3"));
        bg.setCornerRadius(10 * dp);
        et.setBackground(bg);
        return et;
    }

    private void setChipSelected(TextView chip, String type, boolean selected, float dp) {
        int color = getActivityColor(type);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(8 * dp);
        if (selected) {
            bg.setColor(color);
            chip.setTextColor(Color.WHITE);
        } else {
            bg.setColor(Color.parseColor("#F5F5F8"));
            bg.setStroke((int)(1.5f * dp), color);
            chip.setTextColor(color);
        }
        chip.setBackground(bg);
    }

    private void submitNewActivity(String title, String type, String time, Integer duration) {
        String token = "Bearer " + session.getToken();
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("activity_type", type);
        if (time != null && !time.isEmpty()) body.put("start_time", time);
        if (duration != null) body.put("estimated_minutes", duration);
        if (currentDayEntryId > 0) body.put("day_entry_id", currentDayEntryId);

        ApiClient.get().createActivity(token, body).enqueue(new Callback<ActivityData>() {
            @Override
            public void onResponse(@NonNull Call<ActivityData> call,
                                   @NonNull Response<ActivityData> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    loadDashboard();
                } else {
                    Toast.makeText(getContext(), "Failed to save", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ActivityData> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ── ViewPager2 adapter ────────────────────────────────────────────────────

    private class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.PageHolder> {

        private final View[] pages = new View[2];

        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            View page = inf.inflate(
                    viewType == 0 ? R.layout.page_timeline : R.layout.page_quicktasks,
                    parent, false);
            pages[viewType] = page;
            return new PageHolder(page);
        }

        @Override public void onBindViewHolder(@NonNull PageHolder holder, int position) {}
        @Override public int getItemCount() { return 2; }
        @Override public int getItemViewType(int position) { return position; }

        View getPage(int index) { return pages[index]; }

        class PageHolder extends RecyclerView.ViewHolder {
            PageHolder(View v) { super(v); }
        }
    }
}
