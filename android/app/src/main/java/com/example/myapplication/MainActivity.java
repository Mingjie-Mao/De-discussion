package com.example.myapplication;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.graphics.drawable.Animatable;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import backend.BackendRuntime;
import backend.BackendException;
import backend.BackendModerationGateway;

public class MainActivity extends AppCompatActivity {
    private static final String EXTRA_START_PAGE = "start_page";
    private static final String STATE_CURRENT_PAGE = "current_page";
    private static final long TAB_ANIMATION_MS = 320L;
    private static final long PAGE_FADE_OUT_MS = 85L;
    private static final long PAGE_FADE_IN_MS = 150L;
    private static final float PAGE_TRANSITION_MIN_ALPHA = 0.18f;

    private DrawerLayout drawerRoot;
    private View mainContent;
    private View drawerPanel;
    private ViewPager2 viewPager;
    private View navShell;
    private View navIndicator;
    private View tabChannels;
    private View tabNotifications;
    private View tabMarket;
    private View tabLeaderboard;
    private View tabYou;
    private ImageView iconChannels;
    private TextView channelDots;
    private ImageView iconNotifications;
    private ImageView iconMarket;
    private ImageView iconLeaderboard;
    private ImageView iconYou;
    private TextView labelChannels;
    private TextView labelNotifications;
    private TextView labelMarket;
    private TextView labelLeaderboard;
    private TextView labelYou;
    private LinearLayout buttonDrawerForumAnu;
    private LinearLayout buttonDrawerForumUnsw;
    private LinearLayout buttonDrawerForumUsyd;
    private LinearLayout buttonDrawerForumUm;
    private LinearLayout layoutDrawerForumList;
    private LinearLayout buttonDrawerAddForum;
    private ImageView imageDrawerForumAnu;
    private ImageView imageDrawerForumUnsw;
    private ImageView imageDrawerForumUsyd;
    private ImageView imageDrawerForumUm;
    private TextView textDrawerForumAnu;
    private TextView textDrawerForumUnsw;
    private TextView textDrawerForumUsyd;
    private TextView textDrawerForumUm;
    private boolean pageTransitionRunning;

    private final ActivityResultLauncher<String> pickAvatarImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) return;
                AccountProfileSync.avatar(this, uri, () -> {
                    notifyPagesChanged();
                    Toast.makeText(this, R.string.toast_profile_saved, Toast.LENGTH_SHORT).show();
                });
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        AccountProfileSync.applyCached(this);
        UiPreferences.applyAppearance(this);
        super.onCreate(savedInstanceState);
        if (!UiPreferences.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.page_background)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        setContentView(R.layout.activity_main);

        AppData.ensurePopulated();
        AppData.setOnlineMode(true);
        AppData.bindBackendOrigin(BackendRuntime.from(this).config().baseUrl());
        if (!bindSelectedAccount()) return;

        drawerRoot = findViewById(R.id.drawerRoot);
        mainContent = findViewById(R.id.mainContent);
        drawerPanel = findViewById(R.id.drawerPanel);
        viewPager = findViewById(R.id.viewPager);
        navShell = findViewById(R.id.navShell);
        navIndicator = findViewById(R.id.navIndicator);
        tabChannels = findViewById(R.id.tabChannels);
        tabNotifications = findViewById(R.id.tabNotifications);
        tabMarket = findViewById(R.id.tabMarket);
        tabLeaderboard = findViewById(R.id.tabLeaderboard);
        tabYou = findViewById(R.id.tabYou);
        iconChannels = findViewById(R.id.iconChannels);
        channelDots = findViewById(R.id.channelDots);
        iconNotifications = findViewById(R.id.iconNotifications);
        iconMarket = findViewById(R.id.iconMarket);
        iconLeaderboard = findViewById(R.id.iconLeaderboard);
        iconYou = findViewById(R.id.iconYou);
        labelChannels = findViewById(R.id.labelChannels);
        labelNotifications = findViewById(R.id.labelNotifications);
        labelMarket = findViewById(R.id.labelMarket);
        labelLeaderboard = findViewById(R.id.labelLeaderboard);
        labelYou = findViewById(R.id.labelYou);

        buttonDrawerForumAnu = findViewById(R.id.buttonDrawerForumAnu);
        buttonDrawerForumUnsw = findViewById(R.id.buttonDrawerForumUnsw);
        buttonDrawerForumUsyd = findViewById(R.id.buttonDrawerForumUsyd);
        buttonDrawerForumUm = findViewById(R.id.buttonDrawerForumUm);
        layoutDrawerForumList = findViewById(R.id.layoutDrawerForumList);
        buttonDrawerAddForum = findViewById(R.id.buttonDrawerAddForum);
        imageDrawerForumAnu = findViewById(R.id.imageDrawerForumAnu);
        imageDrawerForumUnsw = findViewById(R.id.imageDrawerForumUnsw);
        imageDrawerForumUsyd = findViewById(R.id.imageDrawerForumUsyd);
        imageDrawerForumUm = findViewById(R.id.imageDrawerForumUm);
        textDrawerForumAnu = findViewById(R.id.textDrawerForumAnu);
        textDrawerForumUnsw = findViewById(R.id.textDrawerForumUnsw);
        textDrawerForumUsyd = findViewById(R.id.textDrawerForumUsyd);
        textDrawerForumUm = findViewById(R.id.textDrawerForumUm);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerRoot.isDrawerOpen(GravityCompat.START)) {
                    drawerRoot.closeDrawer(GravityCompat.START);
                    return;
                }
                if (viewPager.getCurrentItem() != MainPagerAdapter.PAGE_CHANNELS) {
                    setPage(MainPagerAdapter.PAGE_CHANNELS, true);
                    return;
                }

                // Temporarily step out of this callback so the dispatcher can
                // perform the platform's default finish/predictive-back action.
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });

        applyInsets(mainContent, true);
        applyInsets(drawerPanel, false);

        buttonDrawerForumAnu.setOnClickListener(v -> switchForum(AppData.FORUM_ANU));
        buttonDrawerForumUnsw.setOnClickListener(v -> switchForum(AppData.FORUM_UNSW));
        buttonDrawerForumUsyd.setOnClickListener(v -> switchForum(AppData.FORUM_USYD));
        buttonDrawerForumUm.setOnClickListener(v -> switchForum(AppData.FORUM_UM));
        buttonDrawerAddForum.setOnClickListener(v -> showAddForumDialog());

        configurePager();
        configureTabBar();

        int startPage = resolveStartPage(savedInstanceState);
        setPage(startPage, false);
        ServerFeatures.init(this);
        ServerFeatures.observe(this, this::refreshAllPages);
        refreshDrawerUi();
        notifyPagesChanged();
    }

    /** A refresh can revoke the session between the login guard and activity startup. */
    private boolean bindSelectedAccount() {
        BackendRuntime runtime = BackendRuntime.from(this);
        boolean administrator = UiPreferences.isAdminSession(this);
        backend.BackendUserSession session = administrator
                ? runtime.admin().session() : runtime.user().session();
        try {
            // Binding and reading identity must use the same session state.
            synchronized (session) {
                if (administrator) runtime.bindAdministratorAccount();
                else runtime.bindMemberAccount();
                java.util.UUID id = java.util.UUID.fromString(session.userId());
                AppData.setAdminMode(administrator);
                if (administrator) AppData.setServerAdministrator(id, session.username());
                else AppData.setServerMember(id, session.username());
            }
            return true;
        } catch (backend.BackendException error) {
            if (!error.isUnauthorised()) throw error;
            UiPreferences.clearLoginSession(this);
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            finish();
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!UiPreferences.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        if (!bindSelectedAccount()) return;
        AccountProfileSync.fetch(this, () -> {
            UiPreferences.applyAppearance(this);
            if (isFinishing() || isDestroyed()) return;
            refreshDrawerUi(); notifyPagesChanged();
        });
        ServerFeatures.init(this); ServerFeatures.observe(this, this::refreshAllPages);
        refreshDrawerUi();
        notifyPagesChanged();
    }

    @Override protected void onPause() { ServerFeatures.remove(this); super.onPause(); }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_CURRENT_PAGE, viewPager.getCurrentItem());
    }

    public void openDrawer() {
        drawerRoot.openDrawer(GravityCompat.START);
    }

    public void refreshAllPages() {
        notifyPagesChanged();
    }

    public void toggleViewerMode() {
        signOutToLogin();
    }

    public void signOutToLogin() {
        UiPreferences.clearLoginSession(this);
        AppData.setAdminMode(false);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    public void switchForum(String forumKey) {
        AppData.setSelectedForum(forumKey);
        refreshDrawerUi();
        drawerRoot.closeDrawer(GravityCompat.START);
        setPage(MainPagerAdapter.PAGE_CHANNELS, false);
        notifyPagesChanged();
    }

    public void openModerationQueue() {
        if (!AppData.isAdminMode()) {
            Toast.makeText(this, getString(R.string.toast_switch_admin_required), Toast.LENGTH_SHORT).show();
            return;
        }

        drawerRoot.closeDrawer(GravityCompat.START);
        startActivity(new Intent(this, AdminReviewActivity.class));
    }

    public void showAvatarPicker() {
        int selected = UiPreferences.getAvatarIndex(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_avatar_picker_dialog);
        content.setPadding(dp(20), dp(18), dp(20), dp(14));

        TextView title = new TextView(this);
        title.setText(R.string.dialog_avatar_title);
        title.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        titleParams.setMargins(0, 0, 0, dp(10));
        content.addView(title, titleParams);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setView(content)
                .create();

        LinearLayout uploadRow = new LinearLayout(this);
        uploadRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        uploadRow.setOrientation(LinearLayout.HORIZONTAL);
        uploadRow.setPadding(dp(8), dp(10), dp(8), dp(10));
        uploadRow.setClickable(true);
        uploadRow.setFocusable(true);
        ImageView cameraIcon = new ImageView(this);
        GradientDrawable cameraCircle = new GradientDrawable();
        cameraCircle.setShape(GradientDrawable.OVAL);
        cameraCircle.setColor(ContextCompat.getColor(this, R.color.tab_bar_fill));
        cameraIcon.setBackground(cameraCircle);
        cameraIcon.setImageResource(R.drawable.ic_image_24);
        cameraIcon.setColorFilter(ContextCompat.getColor(this, R.color.ink_primary));
        cameraIcon.setPadding(dp(7), dp(7), dp(7), dp(7));
        cameraIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        uploadRow.addView(cameraIcon, new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView uploadLabel = new TextView(this);
        uploadLabel.setText(R.string.action_upload_photo);
        uploadLabel.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        uploadLabel.setTextSize(16);
        LinearLayout.LayoutParams uploadLabelParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        uploadLabelParams.setMarginStart(dp(12));
        uploadRow.addView(uploadLabel, uploadLabelParams);
        uploadRow.setOnClickListener(v -> {
            dialog.dismiss();
            pickAvatarImageLauncher.launch("image/*");
        });
        content.addView(uploadRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(ContextCompat.getColor(this, R.color.surface_border));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        divParams.setMargins(dp(8), dp(4), dp(8), dp(4));
        content.addView(divider, divParams);

        int colorsPerRow = 6;
        for (int i = 0; i < UiPreferences.getGoogleColorCount(); i += colorsPerRow) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(android.view.Gravity.CENTER);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(4), 0, dp(4));

            for (int j = i; j < Math.min(i + colorsPerRow, UiPreferences.getGoogleColorCount()); j++) {
                int index = j;
                TextView swatch = new TextView(this);
                swatch.setGravity(android.view.Gravity.CENTER);
                swatch.setText(index == selected ? "✓" : "");
                swatch.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
                swatch.setTypeface(Typeface.DEFAULT_BOLD);
                swatch.setTextSize(16);
                swatch.setClickable(true);
                swatch.setFocusable(true);
                GradientDrawable swatchBg = new GradientDrawable();
                swatchBg.setShape(GradientDrawable.OVAL);
                swatchBg.setColor(UiPreferences.getGoogleColor(index));
                swatchBg.setStroke(dp(index == selected ? 3 : 1), ContextCompat.getColor(this,
                        index == selected ? R.color.accent_strong : R.color.surface_border));
                swatch.setBackground(swatchBg);
                swatch.setOnClickListener(v -> {
                    AccountProfileSync.update(this, backend.BackendUserSession.body("avatarColor", index, "removeAvatar", true), () -> {
                        dialog.dismiss(); notifyPagesChanged();
                        Toast.makeText(this, R.string.toast_profile_saved, Toast.LENGTH_SHORT).show();
                    });
                });

                LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(38), dp(38));
                swatchParams.setMargins(dp(5), dp(4), dp(5), dp(4));
                row.addView(swatch, swatchParams);
            }
            content.addView(row, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public void showNicknameDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_avatar_picker_dialog);
        content.setPadding(dp(20), dp(18), dp(20), dp(14));

        TextView title = new TextView(this);
        title.setText(R.string.dialog_edit_nickname_title);
        title.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, dp(14));
        content.addView(title, titleParams);

        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(R.string.dialog_nickname_hint);
        input.setText(UiPreferences.getProfileNickname(this));
        input.setSelection(input.getText().length());
        input.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        content.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setView(content)
                .create();

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(16), 0, 0);

        TextView cancelBtn = new TextView(this);
        cancelBtn.setText(R.string.action_cancel);
        cancelBtn.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        cancelBtn.setTextSize(14);
        cancelBtn.setTypeface(Typeface.DEFAULT_BOLD);
        cancelBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        TextView saveBtn = new TextView(this);
        saveBtn.setText(R.string.action_save);
        saveBtn.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        saveBtn.setTextSize(14);
        saveBtn.setTypeface(Typeface.DEFAULT_BOLD);
        saveBtn.setPadding(dp(12), dp(8), dp(4), dp(8));
        saveBtn.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                input.setError(getString(R.string.dialog_nickname_hint));
                return;
            }
            AccountProfileSync.update(this, backend.BackendUserSession.body("displayName", value), () -> {
                dialog.dismiss(); notifyPagesChanged();
                Toast.makeText(this, R.string.toast_profile_saved, Toast.LENGTH_SHORT).show();
            });
        });

        buttonRow.addView(cancelBtn);
        buttonRow.addView(saveBtn);
        content.addView(buttonRow, rowParams);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void saveNicknameLocally(String value) {
        UiPreferences.setProfileNickname(this, value);
        notifyPagesChanged();
        Toast.makeText(this, getString(R.string.toast_profile_saved), Toast.LENGTH_SHORT).show();
    }

    public void showSettingsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_settings, drawerRoot, false);
        RadioButton radioLanguageEnglish = dialogView.findViewById(R.id.radioLanguageEnglish);
        RadioButton radioLanguageChinese = dialogView.findViewById(R.id.radioLanguageChinese);
        RadioButton radioThemeLight = dialogView.findViewById(R.id.radioThemeLight);
        RadioButton radioThemeDark = dialogView.findViewById(R.id.radioThemeDark);
        styleDialogRadioButton(radioLanguageEnglish);
        styleDialogRadioButton(radioLanguageChinese);
        styleDialogRadioButton(radioThemeLight);
        styleDialogRadioButton(radioThemeDark);

        if ("zh-CN".equals(UiPreferences.getLanguageTag(this))) {
            radioLanguageChinese.setChecked(true);
        } else {
            radioLanguageEnglish.setChecked(true);
        }

        if (UiPreferences.isDarkTheme(this)) {
            radioThemeDark.setChecked(true);
        } else {
            radioThemeLight.setChecked(true);
        }

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setTitle(R.string.settings_title)
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, null)
                .create();

        dialog.setOnShowListener(unused -> dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String newLanguage = radioLanguageChinese.isChecked() ? "zh-CN" : "en";
                    boolean newDarkTheme = radioThemeDark.isChecked();

                    boolean changed = !newLanguage.equals(UiPreferences.getLanguageTag(this))
                            || newDarkTheme != UiPreferences.isDarkTheme(this);

                    AccountProfileSync.update(this, backend.BackendUserSession.body("languageTag", newLanguage, "theme", newDarkTheme ? "dark" : "light"), () -> {
                        dialog.dismiss(); drawerRoot.closeDrawer(GravityCompat.START);
                        if (changed) restartForAppearanceChange();
                    });
                }));

        dialog.show();
    }

    public void showLanguageDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_avatar_picker_dialog);
        content.setPadding(dp(20), dp(18), dp(20), dp(14));

        TextView title = new TextView(this);
        title.setText(R.string.settings_language);
        title.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, dp(6));
        content.addView(title, titleParams);

        String[] labels = {getString(R.string.settings_language_en), getString(R.string.settings_language_zh)};
        String[] langTags = {"en", "zh-CN"};
        int checkedIndex = "zh-CN".equals(UiPreferences.getLanguageTag(this)) ? 1 : 0;

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setView(content)
                .create();

        RadioGroup radioGroup = new RadioGroup(this);
        int[] rbIds = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(android.view.View.generateViewId());
            rbIds[i] = rb.getId();
            rb.setText(labels[i]);
            rb.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
            styleDialogRadioButton(rb);
            rb.setTextSize(16);
            rb.setPadding(dp(4), dp(10), dp(4), dp(10));
            radioGroup.addView(rb);
        }
        radioGroup.check(rbIds[checkedIndex]);
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < rbIds.length; i++) {
                if (rbIds[i] == checkedId) {
                    String langTag = langTags[i];
                    boolean changed = !langTag.equals(UiPreferences.getLanguageTag(this));
                    AccountProfileSync.update(this, backend.BackendUserSession.body("languageTag", langTag), () -> {
                        dialog.dismiss(); if (changed) restartForAppearanceChange();
                    });
                    return;
                }
            }
        });
        content.addView(radioGroup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(8), 0, 0);
        TextView cancelBtn = new TextView(this);
        cancelBtn.setText(R.string.action_cancel);
        cancelBtn.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        cancelBtn.setTextSize(14);
        cancelBtn.setTypeface(Typeface.DEFAULT_BOLD);
        cancelBtn.setPadding(dp(12), dp(8), dp(4), dp(8));
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        buttonRow.addView(cancelBtn);
        content.addView(buttonRow, rowParams);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public void showThemeDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_avatar_picker_dialog);
        content.setPadding(dp(20), dp(18), dp(20), dp(14));

        TextView title = new TextView(this);
        title.setText(R.string.settings_theme);
        title.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, dp(6));
        content.addView(title, titleParams);

        String[] labels = {getString(R.string.settings_theme_light), getString(R.string.settings_theme_dark)};
        boolean[] darkFlags = {false, true};
        int checkedIndex = UiPreferences.isDarkTheme(this) ? 1 : 0;

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setView(content)
                .create();

        RadioGroup radioGroup = new RadioGroup(this);
        int[] rbIds = new int[labels.length];
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(android.view.View.generateViewId());
            rbIds[i] = rb.getId();
            rb.setText(labels[i]);
            rb.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
            styleDialogRadioButton(rb);
            rb.setTextSize(16);
            rb.setPadding(dp(4), dp(10), dp(4), dp(10));
            radioGroup.addView(rb);
        }
        radioGroup.check(rbIds[checkedIndex]);
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            for (int i = 0; i < rbIds.length; i++) {
                if (rbIds[i] == checkedId) {
                    boolean isDark = darkFlags[i];
                    boolean changed = isDark != UiPreferences.isDarkTheme(this);
                    AccountProfileSync.update(this, backend.BackendUserSession.body("theme", isDark ? "dark" : "light"), () -> {
                        dialog.dismiss(); if (changed) restartForAppearanceChange();
                    });
                    return;
                }
            }
        });
        content.addView(radioGroup, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(8), 0, 0);
        TextView cancelBtn = new TextView(this);
        cancelBtn.setText(R.string.action_cancel);
        cancelBtn.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        cancelBtn.setTextSize(14);
        cancelBtn.setTypeface(Typeface.DEFAULT_BOLD);
        cancelBtn.setPadding(dp(12), dp(8), dp(4), dp(8));
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        buttonRow.addView(cancelBtn);
        content.addView(buttonRow, rowParams);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    public void showAddForumDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundResource(R.drawable.bg_avatar_picker_dialog);
        content.setPadding(dp(20), dp(18), dp(20), dp(14));

        TextView title = new TextView(this);
        title.setText(getString(R.string.action_add_forum));
        title.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, dp(14));
        content.addView(title, titleParams);

        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint(getString(R.string.forum_name_hint));
        input.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        input.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ink_primary)));
        content.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setView(content)
                .create();

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(16), 0, 0);

        TextView cancelBtn = new TextView(this);
        cancelBtn.setText(R.string.action_cancel);
        cancelBtn.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        cancelBtn.setTextSize(14);
        cancelBtn.setTypeface(Typeface.DEFAULT_BOLD);
        cancelBtn.setPadding(dp(12), dp(8), dp(12), dp(8));
        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        TextView addBtn = new TextView(this);
        addBtn.setText(R.string.action_add_forum);
        addBtn.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        addBtn.setTextSize(14);
        addBtn.setTypeface(Typeface.DEFAULT_BOLD);
        addBtn.setPadding(dp(12), dp(8), dp(4), dp(8));
        addBtn.setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError(getString(R.string.forum_name_hint));
                return;
            }
            AppData.addCustomForum(name);
            addCustomForumToDrawer(name);
            dialog.dismiss();
        });

        buttonRow.addView(cancelBtn);
        buttonRow.addView(addBtn);
        content.addView(buttonRow, rowParams);

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }

    private void addCustomForumToDrawer(String label) {
        String key = "custom_" + label.toLowerCase(java.util.Locale.getDefault()).replaceAll("[^a-z0-9]", "_");

        LinearLayout button = new LinearLayout(this);
        button.setOrientation(LinearLayout.HORIZONTAL);
        button.setGravity(android.view.Gravity.CENTER_VERTICAL);
        button.setClickable(true);
        button.setFocusable(true);
        android.content.res.TypedArray foreground =
                obtainStyledAttributes(new int[]{android.R.attr.selectableItemBackground});
        button.setForeground(foreground.getDrawable(0));
        foreground.recycle();
        android.content.res.ColorStateList bgTint = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.surface));
        button.setBackgroundTintList(bgTint);
        button.setBackgroundResource(R.drawable.bg_mode_pill);
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        params.setMargins(0, dp(10), 0, 0);
        button.setLayoutParams(params);

        ImageView avatar = new ImageView(this);
        android.widget.LinearLayout.LayoutParams avatarParams = new android.widget.LinearLayout.LayoutParams(dp(28), dp(28));
        avatar.setLayoutParams(avatarParams);
        avatar.setBackgroundResource(R.drawable.bg_community_avatar);
        avatar.setImageResource(R.drawable.avatar_anu);
        avatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        avatar.setPadding(dp(3), dp(3), dp(3), dp(3));
        button.addView(avatar);

        TextView labelView = new TextView(this);
        android.widget.LinearLayout.LayoutParams labelParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.setMarginStart(dp(12));
        labelView.setLayoutParams(labelParams);
        labelView.setText(label);
        labelView.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        labelView.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        button.addView(labelView);

        button.setOnClickListener(v -> switchForum(key));

        int addButtonIndex = layoutDrawerForumList.getChildCount() - 1;
        layoutDrawerForumList.addView(button, addButtonIndex);
    }

    @SuppressLint("WrongConstant")
    private void configurePager() {
        viewPager.setAdapter(new MainPagerAdapter(this));
        viewPager.setOffscreenPageLimit(MainPagerAdapter.PAGE_COUNT);
        viewPager.setUserInputEnabled(false);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                super.onPageScrolled(position, positionOffset, positionOffsetPixels);
                syncIndicatorTo(position + positionOffset, false);
            }

            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                updateTabSelection(position);
                syncIndicatorTo(position, true);
            }
        });
    }

    private void playChannelDotsHint() {
        if (channelDots == null) {
            return;
        }

        channelDots.animate().cancel();
        iconChannels.animate().cancel();

        channelDots.setAlpha(0f);
        channelDots.setScaleX(0.85f);
        channelDots.setScaleY(0.85f);
        channelDots.setTranslationY(0f);

        iconChannels.setScaleX(1.0f);
        iconChannels.setScaleY(1.0f);

        ObjectAnimator dotsAlpha = ObjectAnimator.ofFloat(
                channelDots,
                View.ALPHA,
                0f,
                1f,
                1f,
                0f
        );

        ObjectAnimator dotsScaleX = ObjectAnimator.ofFloat(
                channelDots,
                View.SCALE_X,
                0.85f,
                1.35f,
                1.35f,
                0.85f
        );

        ObjectAnimator dotsScaleY = ObjectAnimator.ofFloat(
                channelDots,
                View.SCALE_Y,
                0.85f,
                1.35f,
                1.35f,
                0.85f
        );

        ObjectAnimator iconScaleX = ObjectAnimator.ofFloat(
                iconChannels,
                View.SCALE_X,
                1.0f,
                1.28f,
                1.28f,
                1.0f
        );

        ObjectAnimator iconScaleY = ObjectAnimator.ofFloat(
                iconChannels,
                View.SCALE_Y,
                1.0f,
                1.28f,
                1.28f,
                1.0f
        );

        AnimatorSet set = new AnimatorSet();
        set.playTogether(dotsAlpha, dotsScaleX, dotsScaleY, iconScaleX, iconScaleY);
        set.setDuration(1000L);
        set.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
        set.start();
    }

    private void playNotificationShakeHint() {
        iconNotifications.animate().cancel();
        iconNotifications.setTranslationX(0f);

        ObjectAnimator shake = ObjectAnimator.ofFloat(
                iconNotifications,
                View.TRANSLATION_X,
                0f,
                -dp(6),
                dp(6),
                -dp(6),
                dp(6),
                0f,
                -dp(6),
                dp(6),
                -dp(6),
                dp(6),
                0f
        );

        shake.setDuration(720L);
        shake.setInterpolator(new PathInterpolator(0.36f, 0f, 0.66f, -0.56f));
        shake.start();
    }

    private void playYouSlideHint() {
        iconYou.animate().cancel();

        iconYou.setAlpha(0f);
        iconYou.setTranslationX(-dp(22));

        ObjectAnimator slide = ObjectAnimator.ofFloat(
                iconYou,
                View.TRANSLATION_X,
                -dp(22),
                0f
        );

        ObjectAnimator fade = ObjectAnimator.ofFloat(
                iconYou,
                View.ALPHA,
                0f,
                1f
        );

        AnimatorSet set = new AnimatorSet();
        set.playTogether(slide, fade);
        set.setDuration(1000L);
        set.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
        set.start();
    }

    private void playMarketPulseHint() {
        android.graphics.drawable.Drawable d = iconMarket.getDrawable();
        if (d instanceof Animatable) {
            ((Animatable) d).stop();
            ((Animatable) d).start();
        }
    }

    private void playLeaderboardLiftHint() {
        android.graphics.drawable.Drawable d = iconLeaderboard.getDrawable();
        if (d instanceof Animatable) {
            ((Animatable) d).stop();
            ((Animatable) d).start();
        }
    }

    private void playTabHint(int page) {
        if (page == MainPagerAdapter.PAGE_CHANNELS) {
            playChannelDotsHint();
        } else if (page == MainPagerAdapter.PAGE_NOTIFICATIONS) {
            playNotificationShakeHint();
        } else if (page == MainPagerAdapter.PAGE_MARKET) {
            playMarketPulseHint();
        } else if (page == MainPagerAdapter.PAGE_LEADERBOARD) {
            playLeaderboardLiftHint();
        } else if (page == MainPagerAdapter.PAGE_YOU) {
            playYouSlideHint();
        }
    }

    private void configureTabBar() {
        tabChannels.setOnClickListener(v -> {
            setPage(MainPagerAdapter.PAGE_CHANNELS, true);
            playTabHint(MainPagerAdapter.PAGE_CHANNELS);
        });
        tabNotifications.setOnClickListener(v -> {
            setPage(MainPagerAdapter.PAGE_NOTIFICATIONS, true);
            playTabHint(MainPagerAdapter.PAGE_NOTIFICATIONS);
        });
        tabMarket.setOnClickListener(v -> {
            setPage(MainPagerAdapter.PAGE_MARKET, true);
            playTabHint(MainPagerAdapter.PAGE_MARKET);
        });
        tabLeaderboard.setOnClickListener(v -> {
            setPage(MainPagerAdapter.PAGE_LEADERBOARD, true);
            playTabHint(MainPagerAdapter.PAGE_LEADERBOARD);
        });
        tabYou.setOnClickListener(v -> {
            setPage(MainPagerAdapter.PAGE_YOU, true);
            playTabHint(MainPagerAdapter.PAGE_YOU);
        });

        navShell.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (left != oldLeft || right != oldRight) {
                syncIndicatorTo(viewPager.getCurrentItem(), false);
            }
        });
    }

    private void setPage(int page, boolean smoothScroll) {
        if (viewPager.getCurrentItem() != page) {
            animatePageJump(page);
            return;
        }

        updateTabSelection(page);
        syncIndicatorTo(page, true);
    }

    private void animatePageJump(int page) {
        if (pageTransitionRunning) {
            return;
        }

        pageTransitionRunning = true;
        viewPager.animate().cancel();
        viewPager.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        viewPager.animate()
                .alpha(PAGE_TRANSITION_MIN_ALPHA)
                .setDuration(PAGE_FADE_OUT_MS)
                .setInterpolator(new PathInterpolator(0.32f, 0f, 0.67f, 0f))
                .withEndAction(() -> {
                    viewPager.setCurrentItem(page, false);
                    viewPager.animate()
                            .alpha(1f)
                            .setDuration(PAGE_FADE_IN_MS)
                            .setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f))
                            .withEndAction(() -> {
                                pageTransitionRunning = false;
                                viewPager.setLayerType(View.LAYER_TYPE_NONE, null);
                            })
                            .start();
                })
                .start();
    }

    private void refreshDrawerUi() {
        styleForumButton(buttonDrawerForumAnu, imageDrawerForumAnu, textDrawerForumAnu, AppData.FORUM_ANU);
        styleForumButton(buttonDrawerForumUnsw, imageDrawerForumUnsw, textDrawerForumUnsw, AppData.FORUM_UNSW);
        styleForumButton(buttonDrawerForumUsyd, imageDrawerForumUsyd, textDrawerForumUsyd, AppData.FORUM_USYD);
        styleForumButton(buttonDrawerForumUm, imageDrawerForumUm, textDrawerForumUm, AppData.FORUM_UM);
    }

    private void notifyPagesChanged() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof RefreshablePage && fragment.getView() != null) {
                ((RefreshablePage) fragment).refreshContent();
            }
        }
    }

    private void restartForAppearanceChange() {
        overridePendingTransition(0, 0);
        UiPreferences.applyAppearance(this);
        getWindow().setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(this, R.color.page_background)));
    }

    private void styleDialogRadioButton(RadioButton radioButton) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        int[] colors = new int[]{
                ContextCompat.getColor(this, R.color.ink_primary),
                ContextCompat.getColor(this, R.color.ink_secondary)
        };
        radioButton.setButtonTintList(new ColorStateList(states, colors));
    }

    private int resolveStartPage(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            return savedInstanceState.getInt(STATE_CURRENT_PAGE, MainPagerAdapter.PAGE_CHANNELS);
        }
        return getIntent().getIntExtra(EXTRA_START_PAGE, MainPagerAdapter.PAGE_CHANNELS);
    }

    private void applyInsets(View target, boolean includeBottom) {
        int start = target.getPaddingStart();
        int top = target.getPaddingTop();
        int end = target.getPaddingEnd();
        int bottom = target.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(target, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            boolean imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime());
            view.setPaddingRelative(
                    start,
                    top + systemBars.top,
                    end,
                    imeVisible ? view.getPaddingBottom()
                               : bottom + (includeBottom ? systemBars.bottom : 0)
            );
            return insets;
        });
    }

    private void updateTabSelection(int page) {
        updateTab(tabChannels, iconChannels, labelChannels, page == MainPagerAdapter.PAGE_CHANNELS);
        updateTab(tabNotifications, iconNotifications, labelNotifications, page == MainPagerAdapter.PAGE_NOTIFICATIONS);
        updateTab(tabMarket, iconMarket, labelMarket, page == MainPagerAdapter.PAGE_MARKET);
        updateTab(tabLeaderboard, iconLeaderboard, labelLeaderboard, page == MainPagerAdapter.PAGE_LEADERBOARD);
        updateTab(tabYou, iconYou, labelYou, page == MainPagerAdapter.PAGE_YOU);
    }

    private void updateTab(View tab, ImageView icon, TextView label, boolean selected) {
        int activeColor = ContextCompat.getColor(this, R.color.nav_active);
        int inactiveColor = ContextCompat.getColor(this, R.color.nav_inactive);

        icon.setColorFilter(selected ? activeColor : inactiveColor);
        label.setTextColor(selected ? activeColor : inactiveColor);
        label.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        tab.setAlpha(selected ? 1.0f : 0.82f);
        if (selected) {
            icon.animate()
                    .scaleX(1.18f)
                    .scaleY(1.18f)
                    .setDuration(120L)
                    .withEndAction(() -> icon.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(180L)
                            .setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f))
                            .start())
                    .start();
        } else {
            icon.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120L).start();
        }
        tab.animate()
                .scaleX(selected ? 1.0f : 0.98f)
                .scaleY(selected ? 1.0f : 0.98f)
                .setDuration(TAB_ANIMATION_MS)
                .setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f))
                .start();
    }

    private void styleForumButton(LinearLayout button, ImageView avatar, TextView label, String forumKey) {
        boolean selected = AppData.isSelectedForum(forumKey);
        int backgroundColor = ContextCompat.getColor(this, selected ? R.color.tab_bar_fill : R.color.surface);
        label.setText(AppData.getForumLabel(this, forumKey));
        label.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        avatar.setImageResource(AppData.getForumAvatarResId(forumKey));
        if (AppData.FORUM_UM.equals(forumKey)) {
            avatar.setBackgroundResource(R.drawable.bg_community_avatar_um);
        } else {
            avatar.setBackgroundResource(R.drawable.bg_community_avatar);
        }
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        button.setAlpha(selected ? 1.0f : 0.78f);
    }

    private void syncIndicatorTo(float pageOffset, boolean animate) {
        if (navShell.getWidth() == 0) {
            navShell.post(() -> syncIndicatorTo(pageOffset, animate));
            return;
        }

        int innerWidth = navShell.getWidth() - navShell.getPaddingLeft() - navShell.getPaddingRight();
        int slotWidth = innerWidth / MainPagerAdapter.PAGE_COUNT;
        int indicatorInset = dp(1);
        int indicatorWidth = slotWidth - (indicatorInset * 2);
        int centeredOffset = indicatorInset;

        ViewGroup.LayoutParams layoutParams = navIndicator.getLayoutParams();
        if (layoutParams.width != indicatorWidth) {
            layoutParams.width = indicatorWidth;
            navIndicator.setLayoutParams(layoutParams);
        }

        float targetX = centeredOffset + (slotWidth * pageOffset);
        if (animate) {
            navIndicator.animate()
                    .translationX(targetX)
                    .setDuration(TAB_ANIMATION_MS)
                    .setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f))
                    .start();
            return;
        }

        navIndicator.setTranslationX(targetX);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
