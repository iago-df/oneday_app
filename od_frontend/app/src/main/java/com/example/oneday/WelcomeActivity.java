package com.example.oneday;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.oneday.databinding.ActivityWelcomeBinding;
public class WelcomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setNavigationBarColor(getColor(R.color.colorPrimary));

        WindowInsetsControllerCompat insetsCtrl = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        insetsCtrl.setAppearanceLightNavigationBars(false);  // white nav icons
        insetsCtrl.setAppearanceLightStatusBars(true);      // dark status bar icons (white bg at top)

        ActivityWelcomeBinding binding = ActivityWelcomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());


        final int basePaddingPx = (int) (16 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentLayout, (v, insets) -> {
            int navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    basePaddingPx + navBottom);
            return insets;
        });

        binding.btnCreateAccount.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        binding.btnLogIn.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
    }

}
