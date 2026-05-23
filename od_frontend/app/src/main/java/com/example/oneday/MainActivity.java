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
            int shift = (int)(3 * getResources().getDisplayMetrics().density);
            int height = (int)(66 * getResources().getDisplayMetrics().density);
            v.getLayoutParams().height = height + bottom / 2 + shift;
            v.requestLayout();
            int extra = (int)(18     * getResources().getDisplayMetrics().density);
            v.setPadding(0, 0, 0, bottom / 2 + shift + extra);
            return insets;
        });

        final Fragment[] fragments = {
                new TodayFragment(),
                new CalendarFragment(),
                new GoalsFragment(),
                new StatsFragment()
        };
        final int[] navIds = {
                R.id.nav_today,
                R.id.nav_calendar,
                R.id.nav_goals,
                R.id.nav_stats
        };

        if (savedInstanceState == null) {
            androidx.fragment.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            for (int i = 0; i < fragments.length; i++) {
                ft.add(R.id.fragmentContainer, fragments[i]);
                if (i != 0) ft.hide(fragments[i]);
            }
            ft.commit();
            binding.bottomNav.setSelectedItemId(R.id.nav_today);
        }

        final Fragment[] active = {fragments[0]};

        binding.bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment next = null;
            for (int i = 0; i < navIds.length; i++) {
                if (navIds[i] == id) { next = fragments[i]; break; }
            }
            if (next == null || next == active[0]) return true;
            getSupportFragmentManager().beginTransaction()
                    .hide(active[0])
                    .show(next)
                    .commit();
            active[0] = next;
            return true;
        });
    }
}
