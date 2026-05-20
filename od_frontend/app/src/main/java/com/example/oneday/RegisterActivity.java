package com.example.oneday;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.example.oneday.api.ApiClient;
import com.example.oneday.api.models.AuthResponse;
import com.example.oneday.api.models.RegisterRequest;
import com.example.oneday.databinding.ActivityRegisterBinding;
import com.example.oneday.session.SessionManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setNavigationBarColor(getColor(R.color.colorAuthBackground));
        WindowInsetsControllerCompat insetsCtrl = WindowCompat.getInsetsController(
                getWindow(), getWindow().getDecorView());
        insetsCtrl.setAppearanceLightNavigationBars(true);
        insetsCtrl.setAppearanceLightStatusBars(true);

        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, top, 0, bottom);
            return insets;
        });

        session = new SessionManager(this);

        binding.btnBack.setOnClickListener(v -> finish());

        final boolean[] passwordShown = {false};
        binding.tilPassword.setEndIconOnClickListener(v -> {
            passwordShown[0] = !passwordShown[0];
            binding.etPassword.setInputType(passwordShown[0]
                    ? android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
            binding.tilPassword.setEndIconDrawable(passwordShown[0]
                    ? R.drawable.ic_eye_open
                    : R.drawable.ic_eye_closed);
            binding.etPassword.setSelection(binding.etPassword.getText().length());
        });

        binding.btnSignUp.setOnClickListener(v -> attemptRegister());
        binding.tvLogIn.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    private void attemptRegister() {
        String username = binding.etUsername.getText().toString().trim();
        String email    = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString();

        if (username.isEmpty()) {
            binding.etUsername.setError("Enter a username");
            return;
        }
        if (email.isEmpty()) {
            binding.etEmail.setError("Enter your email");
            return;
        }
        if (password.isEmpty()) {
            binding.etPassword.setError("Enter a password");
            return;
        }

        hideError();
        setLoading(true);

        ApiClient.get().register(new RegisterRequest(username, email, password))
                .enqueue(new Callback<AuthResponse>() {
                    @Override
                    public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                        setLoading(false);
                        if ((response.code() == 200 || response.code() == 201)
                                && response.body() != null) {
                            AuthResponse body = response.body();
                            session.saveSession(
                                    body.token,
                                    body.user.id,
                                    body.user.username,
                                    body.user.email,
                                    body.user.name
                            );
                            navigateToMain();
                        } else {
                            String msg;
                            switch (response.code()) {
                                case 409: msg = "Username or email already in use."; break;
                                case 400: msg = "Please fill in all required fields."; break;
                                default:  msg = "Registration failed. Please try again."; break;
                            }
                            showError(msg);
                        }
                    }

                    @Override
                    public void onFailure(Call<AuthResponse> call, Throwable t) {
                        setLoading(false);
                        showError("Connection error. Check your network and try again.");
                    }
                });
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        binding.btnSignUp.setEnabled(!loading);
        binding.btnSignUp.setText(loading ? "Creating account…" : "Sign up");
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String message) {
        binding.tvError.setText(message);
        binding.tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        binding.tvError.setVisibility(View.GONE);
    }
}
