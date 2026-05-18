package com.example.oneday;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.oneday.databinding.ActivitySplashBinding;
import com.example.oneday.session.SessionManager;

public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 1500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivitySplashBinding binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        SessionManager session = new SessionManager(this);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent next = session.isLoggedIn()
                    ? new Intent(this, MainActivity.class)
                    : new Intent(this, WelcomeActivity.class);
            startActivity(next);
            finish();
        }, SPLASH_DELAY_MS);
    }
}
