package com.example.oneday;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;

import com.example.oneday.databinding.ActivityMainBinding;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat insetsCtrl = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        insetsCtrl.setAppearanceLightNavigationBars(true);
        insetsCtrl.setAppearanceLightStatusBars(true);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.bottomNav.setItemActiveIndicatorColor(ColorStateList.valueOf(Color.TRANSPARENT));

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav, (v, insets) -> {
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            v.setPadding(0, 0, 0, bottom);
            return insets;
        });

        if (savedInstanceState == null) {
            loadFragment(new TodayFragment());
            binding.bottomNav.setSelectedItemId(R.id.nav_today);
        }

        binding.bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();
            if (id == R.id.nav_today) {
                fragment = new TodayFragment();
            } else if (id == R.id.nav_calendar) {
                fragment = new CalendarFragment();
            } else if (id == R.id.nav_goals) {
                fragment = new GoalsFragment();
            } else if (id == R.id.nav_stats) {
                fragment = new StatsFragment();
            } else {
                return false;
            }
            loadFragment(fragment);
            return true;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}
