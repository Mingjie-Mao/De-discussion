package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;


import backend.BackendConfig;
import backend.BackendException;
import backend.BackendModerationGateway;
import backend.BackendModerationStatus;
import backend.BackendRuntime;

public class SettingsActivity extends AppCompatActivity {
    private TextView textSettingsLanguageValue;
    private TextView textSettingsThemeValue;
    private TextView textSettingsMode;
    private TextView textSettingsBackendStatus;
    private Button buttonRecords;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiPreferences.applyAppearance(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        View root = findViewById(R.id.settingsRoot);
        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(left, bars.top + top, right, bars.bottom + bottom);
            return insets;
        });

        ImageButton buttonBack = findViewById(R.id.buttonSettingsBack);
        LinearLayout rowLanguage = findViewById(R.id.rowSettingsLanguage);
        LinearLayout rowTheme = findViewById(R.id.rowSettingsTheme);
        LinearLayout layoutAdmin = findViewById(R.id.layoutSettingsAdmin);
        Button buttonQueue = findViewById(R.id.buttonSettingsModerationQueue);
        buttonRecords = findViewById(R.id.buttonSettingsModerationRecords);
        Button buttonBackend = findViewById(R.id.buttonSettingsBackend);
        Button buttonSwitchAccount = findViewById(R.id.buttonSettingsSwitchAccount);
        Button buttonLogout = findViewById(R.id.buttonSettingsLogout);
        textSettingsMode = findViewById(R.id.textSettingsMode);
        textSettingsLanguageValue = findViewById(R.id.textSettingsLanguageValue);
        textSettingsThemeValue = findViewById(R.id.textSettingsThemeValue);
        textSettingsBackendStatus = findViewById(R.id.textSettingsBackendStatus);

        buttonBack.setOnClickListener(v -> finish());
        rowLanguage.setOnClickListener(v -> showLanguageDialog());
        rowTheme.setOnClickListener(v -> showThemeDialog());
        layoutAdmin.setVisibility(UiPreferences.isAdminSession(this) ? View.VISIBLE : View.GONE);
        textSettingsMode.setText(UiPreferences.isAdminSession(this)
                ? getString(R.string.mode_admin) + " · " + BackendRuntime.from(this).admin().session().username()
                : getString(R.string.mode_member));
        buttonQueue.setVisibility(UiPreferences.isAdminSession(this) ? View.VISIBLE : View.GONE);
        buttonQueue.setOnClickListener(v -> startActivity(new Intent(this, AdminReviewActivity.class)));
        buttonRecords.setVisibility(UiPreferences.isAdminSession(this) ? View.VISIBLE : View.GONE);
        buttonRecords.setOnClickListener(v -> startActivity(new Intent(this, AdminReviewActivity.class).putExtra("queue", 1)));
        buttonBackend.setOnClickListener(v -> showBackendDialog());
        buttonSwitchAccount.setOnClickListener(v -> signOutToLogin());
        buttonLogout.setOnClickListener(v -> signOutToLogin());
        refreshLabels();
        if (UiPreferences.isAdminSession(this)) refreshBackendStatus();
    }

    private void refreshLabels() {
        boolean chinese = "zh-CN".equals(UiPreferences.getLanguageTag(this));
        textSettingsLanguageValue.setText(chinese
                ? R.string.settings_language_zh
                : R.string.settings_language_en);
        textSettingsThemeValue.setText(UiPreferences.isDarkTheme(this)
                ? R.string.settings_theme_dark
                : R.string.settings_theme_light);
    }

    private void showLanguageDialog() {
        boolean chinese = "zh-CN".equals(UiPreferences.getLanguageTag(this));

        showChoiceSheet(
                getString(R.string.settings_language),
                R.drawable.ic_language_24,
                new CharSequence[] {
                        getString(R.string.settings_language_en),
                        getString(R.string.settings_language_zh)
                },
                chinese ? 1 : 0,
                which -> {
                    AccountProfileSync.update(this, backend.BackendUserSession.body("languageTag", which == 1 ? "zh-CN" : "en"), () -> {
                        UiPreferences.applyAppearance(this); recreate();
                    });
                });
    }

    private void showThemeDialog() {
        showChoiceSheet(
                getString(R.string.settings_theme),
                R.drawable.ic_palette_24,
                new CharSequence[] {
                        getString(R.string.settings_theme_light),
                        getString(R.string.settings_theme_dark)
                },
                UiPreferences.isDarkTheme(this) ? 1 : 0,
                which -> {
                    AccountProfileSync.update(this, backend.BackendUserSession.body("theme", which == 1 ? "dark" : "light"), () -> {
                        UiPreferences.applyAppearance(this); recreate();
                    });
                });
    }

    /**
     * One choice out of a short list, on the app's own sheet.
     *
     * <p>Picking dismisses immediately and applies. There is no confirm button
     * because there is nothing to confirm: the change is visible the moment it
     * lands, and choosing again undoes it.
     */
    private void showChoiceSheet(
            CharSequence title,
            int iconResId,
            CharSequence[] labels,
            int selectedIndex,
            java.util.function.IntConsumer onPick) {

        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout content = AppSheet.sheet(this, title, null, () -> holder[0].dismiss());

        for (int i = 0; i < labels.length; i++) {
            int index = i;
            content.addView(AppSheet.optionRow(
                    this,
                    iconResId,
                    labels[i],
                    i == selectedIndex,
                    () -> {
                        holder[0].dismiss();
                        if (index != selectedIndex) {
                            onPick.accept(index);
                        }
                    }));
        }

        holder[0] = AppSheet.show(this, content);
    }

    private void showBackendDialog() {
        BackendConfig config = BackendRuntime.from(this).config();

        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout content = AppSheet.sheet(
                this, getString(R.string.backend_settings_title), null, () -> holder[0].dismiss());

        EditText baseUrl = sheetField(
                R.string.backend_url_hint,
                config.baseUrl(),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        content.addView(baseUrl);
        content.addView(AppSheet.note(this, getString(R.string.backend_admin_session_note)));

        LinearLayout actions = AppSheet.actions(this);
        actions.addView(AppSheet.button(
                this, getString(R.string.action_cancel), false, () -> holder[0].dismiss()));
        actions.addView(AppSheet.button(this, getString(R.string.action_save), true, () -> {
            try {
                String oldUrl = config.baseUrl();
                config.setBaseUrl(baseUrl.getText().toString());
                config.setEnabled(true);
                holder[0].dismiss();
                if (!oldUrl.equals(config.baseUrl())) signOutToLogin();
                else refreshBackendStatus();
            } catch (IllegalArgumentException error) {
                Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        }));
        content.addView(actions);

        holder[0] = AppSheet.show(this, content);
    }

    private EditText sheetField(int hintResId, String value, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hintResId);
        field.setText(value);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setTextSize(16);
        field.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        field.setHintTextColor(ContextCompat.getColor(this, R.color.ink_tertiary));
        field.setBackground(AppSheet.roundRect(this, R.color.surface_alt, R.color.surface_border, 18, 1));
        int padding = AppSheet.dp(this, 14);
        field.setPadding(padding, padding, padding, padding);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, AppSheet.dp(this, 10), 0, 0);
        field.setLayoutParams(params);
        return field;
    }

    private void refreshBackendStatus() {
        BackendRuntime runtime = BackendRuntime.from(this);

        if (!runtime.config().isEnabled()) {
            textSettingsBackendStatus.setText(R.string.backend_status_offline);
            return;
        }

        textSettingsBackendStatus.setText(R.string.backend_status_checking);
        runtime.moderation().fetchStatus(new BackendModerationGateway.Callback<>() {
            @Override
            public void onSuccess(BackendModerationStatus status) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                textSettingsBackendStatus.setText(status.llmActive()
                        ? getString(R.string.backend_status_llm, status.activeEngine())
                        : getString(R.string.backend_status_rules, status.activeEngine()));
            }

            @Override
            public void onError(BackendException error) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                textSettingsBackendStatus.setText(
                        getString(R.string.backend_status_unreachable, error.getMessage()));
            }
        });
    }

    private void signOutToLogin() {
        UiPreferences.clearLoginSession(this);
        AppData.setAdminMode(false);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
