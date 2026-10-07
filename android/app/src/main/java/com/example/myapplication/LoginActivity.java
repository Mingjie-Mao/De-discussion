package com.example.myapplication;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import backend.BackendRuntime;
import backend.BackendException;
import backend.BackendModerationGateway;
import backend.BackendStartupWaiter;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class LoginActivity extends AppCompatActivity {
    private EditText inputUsername;
    private EditText inputPassword;
    private MaterialButton buttonLoginMember;
    private MaterialButton buttonLoginAdmin;
    private boolean isAdminSelected = false;
    private EditText inputServer;
    private TextView loginHint;
    private MaterialButton buttonLogin;
    private MaterialButton buttonRegister;
    private boolean registering;
    private boolean loggingIn;
    private boolean loginSucceeded;
    private volatile long loginAttempt;
    private final Handler loginHandler = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService startupExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
    private java.util.concurrent.Future<?> startupTask;
    private Runnable loginDeadline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        UiPreferences.applyAppearance(this);
        super.onCreate(savedInstanceState);

        if (UiPreferences.isLoggedIn(this)) {
            openMain();
            return;
        }

        EdgeToEdge.enable(this);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.page_background)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }

        setContentView(R.layout.activity_login);

        View root = findViewById(R.id.loginRoot);
        inputUsername = findViewById(R.id.inputLoginUsername);
        inputPassword = findViewById(R.id.inputLoginPassword);
        buttonLoginMember = findViewById(R.id.buttonLoginMember);
        buttonLoginAdmin = findViewById(R.id.buttonLoginAdmin);
        buttonLogin = findViewById(R.id.buttonLogin);
        buttonRegister = findViewById(R.id.buttonRegister);
        inputServer = findViewById(R.id.inputLoginServer);
        inputServer.setText(BackendRuntime.from(this).config().baseUrl());
        loginHint = findViewById(R.id.textLoginHint);
        isAdminSelected = savedInstanceState == null
                ? getIntent().getBooleanExtra("admin", UiPreferences.lastLoginWasAdmin(this))
                : savedInstanceState.getBoolean("admin", false);
        registering = savedInstanceState != null && savedInstanceState.getBoolean("register", false) && !isAdminSelected;

        applyInsets(root);
        updateRoleButtons();
        buttonLoginMember.setOnClickListener(v -> { isAdminSelected = false; updateRoleButtons(); });
        buttonLoginAdmin.setOnClickListener(v -> { isAdminSelected = true; registering = false; updateRoleButtons(); });
        buttonRegister.setOnClickListener(v -> {
            if (loggingIn) cancelLogin();
            else { registering = !registering; updateRoleButtons(); }
        });
        buttonLogin.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        if (loggingIn) return;
        String username = inputUsername.getText().toString().trim();
        String password = inputPassword.getText().toString();
        if (username.isEmpty() || password.isEmpty()) {
            loginHint.setText(R.string.member_enter_credentials);
            return;
        }
        if (registering && (!username.matches("[a-zA-Z0-9_]{3,50}") || password.length() < 8 || password.length() > 128)) {
            loginHint.setText(R.string.member_register_hint);
            return;
        }
        BackendRuntime runtime = BackendRuntime.from(this);
        try {
            runtime.admin().clear();
            runtime.user().clear();
            runtime.config().setBaseUrl(inputServer.getText().toString());
            runtime.config().setEnabled(true);
        } catch (IllegalArgumentException error) { loginHint.setText(error.getMessage()); return; }
        loggingIn = true;
        final long attempt = ++loginAttempt;
        setLoginEnabled(false);
        buttonLogin.setText(R.string.admin_signing_in);
        loginHint.setText(R.string.admin_login_wait);
        buttonRegister.setVisibility(View.VISIBLE);
        buttonRegister.setEnabled(true);
        buttonRegister.setText(R.string.action_cancel);
        loginDeadline = () -> {
            if (!currentAttempt(attempt)) return;
            cancelLogin();
            loginHint.setText(R.string.login_server_timeout);
        };
        loginHandler.postDelayed(loginDeadline, BackendStartupWaiter.WAIT_MS);
        BackendModerationGateway.Callback<Void> callback = new BackendModerationGateway.Callback<>() {
            public void onSuccess(Void ignored) {
                if (!currentAttempt(attempt)) return;
                loginHandler.removeCallbacks(loginDeadline);
                loginSucceeded = true;
                inputPassword.setText("");
                completeLogin(isAdminSelected);
            }
            public void onError(BackendException error) {
                if (!currentAttempt(attempt)) return;
                loginHandler.removeCallbacks(loginDeadline);
                loggingIn = false;
                setLoginEnabled(true);
                updateRoleButtons();
                loginHint.setText(error.status() == 401 ? getString(R.string.member_wrong_credentials)
                        : error.status() == 0 ? getString(R.string.login_server_timeout) : error.getMessage());
            }
        };
        String origin = runtime.config().baseUrl();
        startupTask = startupExecutor.submit(() -> {
            try {
                new BackendStartupWaiter(origin).await(() -> loginAttempt != attempt, seconds ->
                        loginHandler.post(() -> {
                            if (currentAttempt(attempt)) loginHint.setText(getString(R.string.login_server_wait, seconds));
                        }));
                loginHandler.post(() -> {
                    if (!currentAttempt(attempt)) return;
                    if (!origin.equals(runtime.config().baseUrl())) {
                        cancelLogin();
                        return;
                    }
                    loginHint.setText(R.string.login_server_ready);
                    if (isAdminSelected) runtime.admin().login(username, password, callback);
                    else runtime.user().login(username, password, registering, callback);
                });
            } catch (java.util.concurrent.CancellationException ignored) {
                // Cancelled attempts cannot submit credentials or navigate later.
            } catch (BackendException error) {
                loginHandler.post(() -> callback.onError(error));
            }
        });
    }
    private boolean currentAttempt(long attempt) {
        return attempt == loginAttempt && loggingIn && !isFinishing() && !isDestroyed();
    }
    private void cancelLogin() {
        loginAttempt++;
        loggingIn = false;
        if (startupTask != null) startupTask.cancel(true);
        if (loginDeadline != null) loginHandler.removeCallbacks(loginDeadline);
        BackendRuntime.from(this).admin().clear();
        BackendRuntime.from(this).user().clear();
        setLoginEnabled(true);
        updateRoleButtons();
    }
    private void setLoginEnabled(boolean enabled) {
        inputUsername.setEnabled(enabled); inputPassword.setEnabled(enabled);
        inputServer.setEnabled(enabled); buttonLogin.setEnabled(enabled);
        buttonLoginMember.setEnabled(enabled); buttonLoginAdmin.setEnabled(enabled);
        buttonRegister.setEnabled(enabled);
    }
    private void completeLogin(boolean administrator) {
        if (!administrator) BackendRuntime.from(this).admin().clear();
        UiPreferences.setLoginSession(this, administrator);
        AppData.ensurePopulated();
        AppData.setAdminMode(administrator);
        if (administrator) {
            BackendRuntime runtime = BackendRuntime.from(this);
            runtime.bindAdministratorAccount();
            AppData.setServerAdministrator(java.util.UUID.fromString(runtime.admin().session().userId()), runtime.admin().session().username());
        } else {
            BackendRuntime runtime = BackendRuntime.from(this);
            runtime.bindMemberAccount();
            AppData.setServerMember(java.util.UUID.fromString(runtime.user().session().userId()), runtime.user().session().username());
            UiPreferences.setProfileNickname(this, runtime.user().session().displayName());
            UiPreferences.setProfileUid(this, runtime.user().session().userId());
        }
        AccountProfileSync.applyCached(this);
        UiPreferences.applyAppearance(this);
        openMain();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("admin", isAdminSelected); state.putBoolean("register", registering); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        loginAttempt++;
        if (loginDeadline != null) loginHandler.removeCallbacks(loginDeadline);
        if (startupTask != null) startupTask.cancel(true);
        startupExecutor.shutdownNow();
        if (loggingIn && !loginSucceeded) {
            BackendRuntime.from(this).admin().clear();
            BackendRuntime.from(this).user().clear();
        }
        super.onDestroy();
    }

    private void updateRoleButtons() {
        inputServer.setVisibility(isAdminSelected ? View.VISIBLE : View.GONE);
        buttonRegister.setVisibility(isAdminSelected ? View.GONE : View.VISIBLE);
        buttonRegister.setText(registering ? R.string.member_have_account : R.string.member_create_account);
        buttonLogin.setText(registering ? R.string.member_register : R.string.action_login);
        loginHint.setText(isAdminSelected ? R.string.admin_login_hint : registering ? R.string.member_register_hint : R.string.member_login_hint);
        int selectedBg = ContextCompat.getColor(this, R.color.tab_bar_fill);
        int unselectedBg = ContextCompat.getColor(this, R.color.surface);
        int selectedText = ContextCompat.getColor(this, R.color.ink_primary);
        int unselectedText = ContextCompat.getColor(this, R.color.ink_secondary);

        buttonLoginMember.setBackgroundTintList(ColorStateList.valueOf(isAdminSelected ? unselectedBg : selectedBg));
        buttonLoginMember.setTextColor(isAdminSelected ? unselectedText : selectedText);

        buttonLoginAdmin.setBackgroundTintList(ColorStateList.valueOf(isAdminSelected ? selectedBg : unselectedBg));
        buttonLoginAdmin.setTextColor(isAdminSelected ? selectedText : unselectedText);
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void applyInsets(View target) {
        int start = target.getPaddingStart();
        int top = target.getPaddingTop();
        int end = target.getPaddingEnd();
        int bottom = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(target, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPaddingRelative(
                    start,
                    top + systemBars.top,
                    end,
                    bottom + systemBars.bottom
            );
            return insets;
        });
    }
}
