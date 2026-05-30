package com.example.oneday;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.oneday.api.ApiClient;
import com.example.oneday.api.models.GoalData;
import com.example.oneday.api.models.GoalsResponse;
import com.example.oneday.session.SessionManager;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GoalsFragment extends Fragment {

    private LinearLayout goalsContainer;
    private LinearLayout filterContainer;
    private LinearLayout addGoalBtnContainer;
    private View filterScroll;
    private SessionManager session;
    private List<GoalData> allGoals = new ArrayList<>();
    private String currentFilter = "all";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_goals, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session = new SessionManager(requireContext());
        goalsContainer     = view.findViewById(R.id.goalsContainer);
        filterContainer    = view.findViewById(R.id.filterContainer);
        addGoalBtnContainer = view.findViewById(R.id.addGoalBtnContainer);
        filterScroll       = view.findViewById(R.id.filterScroll);

        TextView tvDate = view.findViewById(R.id.tvDate);
        tvDate.setText(new SimpleDateFormat("EEE, MMM d", Locale.ENGLISH).format(new Date()));

        setupFilters();
        setupAddButton();
        loadGoals();
    }


    private void setupFilters() {
        String[] keys   = {"all", "planned", "in_progress", "completed"};
        String[] labels = {"All", "Planned", "In Progress", "Completed"};
        final TextView[] chips = new TextView[keys.length];
        float dp = requireContext().getResources().getDisplayMetrics().density;

        for (int i = 0; i < keys.length; i++) {
            final int idx = i;
            TextView chip = new TextView(requireContext());
            chip.setText(labels[i]);
            chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setPadding((int)(14 * dp), (int)(7 * dp), (int)(14 * dp), (int)(7 * dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd((int)(8 * dp));
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                currentFilter = keys[idx];
                for (int j = 0; j < chips.length; j++)
                    setFilterChip(chips[j], keys[j].equals(currentFilter), dp);
                applyFilter();
            });
            chips[i] = chip;
            setFilterChip(chip, i == 0, dp);
            filterContainer.addView(chip);
        }
    }

    private void setFilterChip(TextView chip, boolean selected, float dp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(20 * dp);
        if (selected) {
            bg.setColor(Color.parseColor("#5D65D9"));
            chip.setTextColor(Color.WHITE);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);
        } else {
            bg.setColor(Color.parseColor("#F5F5F8"));
            bg.setStroke((int)(1.5f * dp), Color.parseColor("#DDDDE3"));
            chip.setTextColor(Color.parseColor("#777777"));
            chip.setTypeface(null, android.graphics.Typeface.NORMAL);
        }
        chip.setBackground(bg);
    }


    private void setupAddButton() {
        float dp = requireContext().getResources().getDisplayMetrics().density;
        android.widget.Button btn = new android.widget.Button(requireContext());
        btn.setText("Add goal");
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
        btn.setOnClickListener(v -> showGoalForm());
        addGoalBtnContainer.addView(btn);
    }


    private void loadGoals() {
        String token = "Bearer " + session.getToken();
        ApiClient.get().getGoals(token).enqueue(new Callback<GoalsResponse>() {
            @Override
            public void onResponse(@NonNull Call<GoalsResponse> call,
                                   @NonNull Response<GoalsResponse> response) {
                if (!isAdded() || response.body() == null) return;
                allGoals = response.body().goals != null ? response.body().goals : new ArrayList<>();
                applyFilter();
            }

            @Override
            public void onFailure(@NonNull Call<GoalsResponse> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Could not load goals", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilter() {
        List<GoalData> filtered = new ArrayList<>();
        for (GoalData g : allGoals) {
            if ("all".equals(currentFilter) || currentFilter.equals(g.status))
                filtered.add(g);
        }
        buildGoalsList(filtered);
    }


    private void buildGoalsList(List<GoalData> goals) {
        if (goalsContainer == null) return;
        goalsContainer.removeAllViews();
        if (goals.isEmpty()) { addEmptyState(); return; }
        for (GoalData g : goals) goalsContainer.addView(buildGoalCard(g));
    }

    private View buildGoalCard(GoalData goal) {
        float dp = requireContext().getResources().getDisplayMetrics().density;
        int statusColor = getStatusColor(goal.status);

        MaterialCardView card = new MaterialCardView(requireContext());
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = (int)(8 * dp);
        card.setLayoutParams(clp);
        card.setCardBackgroundColor(Color.WHITE);
        card.setRadius(12 * dp);
        card.setCardElevation(0);
        card.setStrokeWidth((int)(2 * dp));
        card.setStrokeColor(Color.parseColor("#DDDDE3"));

        LinearLayout inner = new LinearLayout(requireContext());
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding((int)(14*dp), (int)(14*dp), (int)(14*dp), (int)(12*dp));
        inner.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout topRow = new LinearLayout(requireContext());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams trlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        trlp.bottomMargin = (int)(10 * dp);
        topRow.setLayoutParams(trlp);

        View dot = new View(requireContext());
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams((int)(8*dp), (int)(8*dp));
        dlp.setMarginEnd((int)(10 * dp));
        dot.setLayoutParams(dlp);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(statusColor);
        dot.setBackground(dotBg);

        TextView tvTitle = new TextView(requireContext());
        tvTitle.setText(goal.title);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        tvTitle.setTextColor(Color.parseColor("#222222"));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setMaxLines(1);
        tvTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvTitle.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView badge = new TextView(requireContext());
        badge.setText(getStatusLabel(goal.status));
        badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        badge.setTextColor(statusColor);
        badge.setTypeface(null, android.graphics.Typeface.BOLD);
        badge.setPadding((int)(8*dp), (int)(3*dp), (int)(8*dp), (int)(3*dp));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setShape(GradientDrawable.RECTANGLE);
        badgeBg.setCornerRadius(20 * dp);
        badgeBg.setColor(Color.argb(30, Color.red(statusColor), Color.green(statusColor), Color.blue(statusColor)));
        badge.setBackground(badgeBg);
        badge.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        topRow.addView(dot);
        topRow.addView(tvTitle);
        topRow.addView(badge);

        ProgressBar pb = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress((int) goal.progressPercent);
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(statusColor));
        pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#EEEEF5")));
        LinearLayout.LayoutParams pblp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (int)(6 * dp));
        pblp.bottomMargin = (int)(8 * dp);
        pb.setLayoutParams(pblp);

        LinearLayout bottomRow = new LinearLayout(requireContext());
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bottomRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvPct = new TextView(requireContext());
        tvPct.setText(goal.daysCompleted + " / " + goal.targetDays + " days  ·  " + (int) goal.progressPercent + "%");
        tvPct.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
        tvPct.setTextColor(Color.parseColor("#AAAAAA"));
        tvPct.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        bottomRow.addView(tvPct);

        if (goal.deadline != null && !goal.deadline.isEmpty()) {
            TextView tvDeadline = new TextView(requireContext());
            tvDeadline.setText(formatDate(goal.deadline));
            tvDeadline.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
            tvDeadline.setTextColor(Color.parseColor("#AAAAAA"));
            tvDeadline.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            bottomRow.addView(tvDeadline);
        }

        inner.addView(topRow);
        inner.addView(pb);
        inner.addView(bottomRow);
        card.addView(inner);

        card.setClickable(true);
        card.setFocusable(true);
        card.setOnLongClickListener(v -> {
            showDeletePopup(goal.id, card);
            return true;
        });

        return card;
    }

    private void showDeletePopup(int goalId, View anchor) {
        float dp = requireContext().getResources().getDisplayMetrics().density;

        android.widget.Button deleteBtn = new android.widget.Button(requireContext());
        deleteBtn.setText("Delete goal?");
        deleteBtn.setTextColor(Color.WHITE);
        deleteBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
        deleteBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        deleteBtn.setAllCaps(false);
        GradientDrawable delBg = new GradientDrawable();
        delBg.setShape(GradientDrawable.RECTANGLE);
        delBg.setColor(Color.parseColor("#5D65D9"));
        delBg.setCornerRadius(10 * dp);
        deleteBtn.setBackground(delBg);
        deleteBtn.setPadding((int)(16*dp), (int)(10*dp), (int)(16*dp), (int)(10*dp));
        deleteBtn.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);

        android.widget.PopupWindow popup = new android.widget.PopupWindow(
                deleteBtn,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(8 * dp);
        popup.showAsDropDown(anchor, (int)(16*dp),
                -(anchor.getHeight() / 2 + deleteBtn.getMeasuredHeight() / 2));

        deleteBtn.setOnClickListener(v -> {
            popup.dismiss();
            deleteGoal(goalId);
        });
    }

    private void showGoalForm() {
        goalsContainer.removeAllViews();
        filterScroll.setVisibility(View.GONE);
        addGoalBtnContainer.setVisibility(View.GONE);

        float dp = requireContext().getResources().getDisplayMetrics().density;

        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding((int)(4*dp), (int)(8*dp), (int)(4*dp), (int)(8*dp));
        form.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvFormTitle = new TextView(requireContext());
        tvFormTitle.setText("Create new goal");
        tvFormTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        tvFormTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvFormTitle.setTextColor(Color.parseColor("#5D65D9"));
        LinearLayout.LayoutParams ftlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ftlp.bottomMargin = (int)(18 * dp);
        tvFormTitle.setLayoutParams(ftlp);
        form.addView(tvFormTitle);

        android.widget.EditText etTitle = buildInputField(dp, "Goal title", false);
        LinearLayout.LayoutParams etlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        etlp.bottomMargin = (int)(14 * dp);
        etTitle.setLayoutParams(etlp);
        form.addView(buildFieldLabel(dp, "Title"));
        form.addView(etTitle);

        android.widget.EditText etTargetDays = buildInputField(dp, "30", true);
        etTargetDays.setText("30");
        LinearLayout.LayoutParams tdlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tdlp.bottomMargin = (int)(14 * dp);
        etTargetDays.setLayoutParams(tdlp);
        form.addView(buildFieldLabel(dp, "Target days"));
        form.addView(etTargetDays);

        android.widget.EditText etDesc = buildInputField(dp, "Description (optional)", false);
        etDesc.setMinLines(2);
        etDesc.setMaxLines(4);
        etDesc.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout.LayoutParams dslp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dslp.bottomMargin = (int)(14 * dp);
        etDesc.setLayoutParams(dslp);
        form.addView(buildFieldLabel(dp, "Description"));
        form.addView(etDesc);

        android.widget.EditText etDeadline = buildInputField(dp, "Pick a date (optional)", false);
        etDeadline.setFocusable(false);
        etDeadline.setClickable(true);
        final String[] selectedDate = {null};
        etDeadline.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            new android.app.DatePickerDialog(requireContext(), (datePicker, y, m, d) -> {
                selectedDate[0] = String.format(Locale.getDefault(), "%04d-%02d-%02d", y, m + 1, d);
                etDeadline.setText(formatDate(selectedDate[0]));
            }, cal.get(java.util.Calendar.YEAR),
                    cal.get(java.util.Calendar.MONTH),
                    cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
        });
        LinearLayout.LayoutParams ddlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ddlp.bottomMargin = (int)(20 * dp);
        etDeadline.setLayoutParams(ddlp);
        form.addView(buildFieldLabel(dp, "Deadline"));
        form.addView(etDeadline);

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
        cancelBg.setStroke((int)(2*dp), Color.parseColor("#5D65D9"));
        cancelBg.setCornerRadius(12 * dp);
        btnCancel.setBackground(cancelBg);
        btnCancel.setPadding(0, bpv, 0, bpv);
        LinearLayout.LayoutParams cblp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        cblp.setMarginEnd((int)(8 * dp));
        btnCancel.setLayoutParams(cblp);
        btnCancel.setOnClickListener(v -> restoreList());

        android.widget.Button btnSave = new android.widget.Button(requireContext());
        btnSave.setText("Save goal");
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

        final String[] dateCapture = selectedDate;
        btnSave.setOnClickListener(v -> {
            String title = etTitle.getText().toString().trim();
            if (title.isEmpty()) { etTitle.setError("Required"); return; }
            String desc = etDesc.getText().toString().trim();
            int targetDays = 30;
            try { targetDays = Math.max(1, Integer.parseInt(etTargetDays.getText().toString().trim())); }
            catch (NumberFormatException ignored) {}
            submitGoal(title, targetDays, desc.isEmpty() ? null : desc, dateCapture[0]);
        });

        btnRow.addView(btnCancel);
        btnRow.addView(btnSave);
        form.addView(btnRow);

        goalsContainer.addView(form);
    }

    private void restoreList() {
        filterScroll.setVisibility(View.VISIBLE);
        addGoalBtnContainer.setVisibility(View.VISIBLE);
        loadGoals();
    }

    private void submitGoal(String title, int targetDays, String desc, String deadline) {
        String token = "Bearer " + session.getToken();
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("target_days", targetDays);
        if (desc != null) body.put("description", desc);
        if (deadline != null) body.put("deadline", deadline);

        ApiClient.get().createGoal(token, body).enqueue(new Callback<GoalData>() {
            @Override
            public void onResponse(@NonNull Call<GoalData> call, @NonNull Response<GoalData> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    restoreList();
                } else {
                    Toast.makeText(getContext(), "Failed to save", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(@NonNull Call<GoalData> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteGoal(int id) {
        String token = "Bearer " + session.getToken();
        ApiClient.get().deleteGoal(id, token).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) loadGoals();
                else Toast.makeText(getContext(), "Failed to delete", Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(getContext(), "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addEmptyState() {
        float dp = requireContext().getResources().getDisplayMetrics().density;
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        wlp.topMargin = (int)(40 * dp);
        wrapper.setLayoutParams(wlp);

        android.widget.ImageView icon = new android.widget.ImageView(requireContext());
        int iconSize = (int)(86 * dp);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(iconSize, iconSize);
        ilp.bottomMargin = (int)(12 * dp);
        icon.setLayoutParams(ilp);
        icon.setImageResource(R.drawable.ic_no_goals);
        icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        wrapper.addView(icon);

        TextView text = new TextView(requireContext());
        text.setText("No goals yet");
        text.setTextColor(Color.parseColor("#999999"));
        text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
        text.setGravity(android.view.Gravity.CENTER);
        text.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        wrapper.addView(text);

        goalsContainer.addView(wrapper);
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
        et.setPadding((int)(12*dp), (int)(10*dp), (int)(12*dp), (int)(10*dp));
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setColor(Color.parseColor("#F8F8FC"));
        bg.setStroke((int)(1.5f * dp), Color.parseColor("#DDDDE3"));
        bg.setCornerRadius(10 * dp);
        et.setBackground(bg);
        return et;
    }

    private int getStatusColor(String status) {
        if (status == null) return Color.parseColor("#5D65D9");
        switch (status) {
            case "in_progress": return Color.parseColor("#FF8C42");
            case "completed":   return Color.parseColor("#4CAF50");
            case "partial":     return Color.parseColor("#FFC107");
            case "failed":      return Color.parseColor("#E53935");
            case "archived":    return Color.parseColor("#999999");
            default:            return Color.parseColor("#5D65D9");
        }
    }

    private String getStatusLabel(String status) {
        if (status == null) return "Planned";
        switch (status) {
            case "in_progress": return "In Progress";
            case "completed":   return "Completed";
            case "partial":     return "Partial";
            case "failed":      return "Failed";
            case "archived":    return "Archived";
            default:            return "Planned";
        }
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
}
