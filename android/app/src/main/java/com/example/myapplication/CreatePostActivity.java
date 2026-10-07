package com.example.myapplication;

import android.graphics.drawable.GradientDrawable;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.util.Locale;

import backend.BackendConfig;
import backend.BackendException;
import backend.BackendForumGateway;
import backend.BackendMedia;
import backend.BackendModerationGateway;
import backend.BackendRuntime;
import dao.model.Post;
import dao.model.User;

public class CreatePostActivity extends AppCompatActivity {
    public static final String EXTRA_EDIT_POST_ID = "edit_post_id";

    private EditText inputPostTitle;
    private EditText inputPostBody;
    private EditText inputPostCustomCategory;
    private RadioGroup radioPostCategory;
    private ImageView imagePostPreview;
    private MaterialButton buttonPublishPost;
    private Uri selectedPostImageUri;
    private Post editingPost;

    private final ActivityResultLauncher<String> pickPostImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }
                try {
                    selectedPostImageUri = ImageStorage.copyToLocalImage(this, uri);
                    imagePostPreview.setImageURI(selectedPostImageUri);
                    imagePostPreview.setVisibility(android.view.View.VISIBLE);
                } catch (IOException exception) {
                    Toast.makeText(this, getString(R.string.toast_action_failed), Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiPreferences.applyAppearance(this);
        super.onCreate(savedInstanceState);
        if (!UiPreferences.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_post);
        android.view.View createRoot=findViewById(R.id.createPostRoot);
        int paddingLeft=createRoot.getPaddingLeft(),paddingTop=createRoot.getPaddingTop();
        int paddingRight=createRoot.getPaddingRight(),paddingBottom=createRoot.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(createRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    paddingLeft,
                    systemBars.top + paddingTop,
                    paddingRight,
                    systemBars.bottom + paddingBottom
            );
            return insets;
        });

        TextView buttonCancelPost = findViewById(R.id.buttonCancelPost);
        TextView textCreatePostAvatar = findViewById(R.id.textCreatePostAvatar);
        inputPostTitle = findViewById(R.id.inputPostTitle);
        inputPostBody = findViewById(R.id.inputPostBody);
        inputPostCustomCategory = findViewById(R.id.inputPostCustomCategory);
        radioPostCategory = findViewById(R.id.radioPostCategory);
        imagePostPreview = findViewById(R.id.imagePostPreview);
        ImageButton buttonMentionFriend = findViewById(R.id.buttonMentionFriend);
        ImageButton buttonAddPostImage = findViewById(R.id.buttonAddPostImage);
        ImageButton buttonAddEmoji = findViewById(R.id.buttonAddEmoji);
        buttonPublishPost = findViewById(R.id.buttonPublishPost);
        editingPost = AppData.getPostById(getIntent().getStringExtra(EXTRA_EDIT_POST_ID));

        String nickname = UiPreferences.getProfileNickname(this);
        textCreatePostAvatar.setText(getAvatarLetter(nickname));
        textCreatePostAvatar.setBackground(makeAvatarBackground());
        AvatarRenderer.display(textCreatePostAvatar,UiPreferences.getAvatarImageUri(this),
                getAvatarLetter(nickname),UiPreferences.getAvatarColor(this));

        styleCategoryChips();

        buttonCancelPost.setOnClickListener(v -> finish());
        radioPostCategory.setOnCheckedChangeListener((group, checkedId) -> {
            styleCategoryChips();
            inputPostCustomCategory.setVisibility(checkedId == R.id.radioCategoryCustom
                    ? android.view.View.VISIBLE
                    : android.view.View.GONE);
        });
        buttonMentionFriend.setOnClickListener(v -> showMentionDialog());
        buttonAddPostImage.setOnClickListener(v -> pickPostImageLauncher.launch("image/*"));
        buttonAddEmoji.setOnClickListener(v -> showEmojiPicker());
        buttonPublishPost.setOnClickListener(v -> publishPost());
        inputPostTitle.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshPublishState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        if (editingPost != null) {
            populateEditPost();
        } else {
            inputPostTitle.requestFocus();
        }
        refreshPublishState();
    }

    private void populateEditPost() {
        inputPostTitle.setText(AppData.getPostTitle(editingPost));
        inputPostBody.setText(AppData.getPostBody(editingPost));
        buttonPublishPost.setText(R.string.action_save);

        String imageUri = AppData.getPostImageUri(editingPost);
        if (imageUri != null && !imageUri.isEmpty()) {
            selectedPostImageUri = Uri.parse(imageUri);
            RemoteImageLoader.display(imagePostPreview, selectedPostImageUri);
            imagePostPreview.setVisibility(android.view.View.VISIBLE);
        }

        selectCategory(AppData.getPostCategory(this, editingPost));
    }

    private void selectCategory(String category) {
        String normalized = category == null ? "" : category.trim();
        if (normalized.equals(getString(R.string.category_social))) {
            radioPostCategory.check(R.id.radioCategorySocial);
        } else if (normalized.equals(getString(R.string.category_finals))) {
            radioPostCategory.check(R.id.radioCategoryFinals);
        } else if (normalized.equals(getString(R.string.category_ai))) {
            radioPostCategory.check(R.id.radioCategoryAi);
        } else if (normalized.equals(getString(R.string.category_study)) || normalized.isEmpty()) {
            radioPostCategory.check(R.id.radioCategoryStudy);
        } else {
            radioPostCategory.check(R.id.radioCategoryCustom);
            inputPostCustomCategory.setText(normalized);
            inputPostCustomCategory.setVisibility(android.view.View.VISIBLE);
        }
        styleCategoryChips();
    }

    private void styleCategoryChips() {
        int checkedId = radioPostCategory.getCheckedRadioButtonId();
        for (int i = 0; i < radioPostCategory.getChildCount(); i++) {
            RadioButton btn = (RadioButton) radioPostCategory.getChildAt(i);
            boolean selected = btn.getId() == checkedId;
            btn.setButtonDrawable(null);
            btn.setGravity(Gravity.CENTER);
            btn.setMinWidth(dp(60));
            btn.setPadding(dp(14), 0, dp(14), 0);
            btn.setTextSize(14);
            btn.setTypeface(selected ? android.graphics.Typeface.DEFAULT_BOLD : android.graphics.Typeface.DEFAULT);
            btn.setTextColor(ContextCompat.getColor(this, selected ? R.color.ink_primary : R.color.ink_secondary));
            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(999));
            bg.setColor(ContextCompat.getColor(this, R.color.tab_bar_fill));
            bg.setStroke(dp(1), ContextCompat.getColor(this,
                    selected ? R.color.surface_border : R.color.tab_bar_stroke));
            btn.setBackground(bg);
        }
    }

    private void showEmojiPicker() {
        String[] emojis = {
            "😀", "😂", "😍", "🥰", "😎", "🤔", "😅", "🤣",
            "😭", "😊", "👍", "❤️", "🔥", "✨", "🎉", "🎊",
            "💯", "🙏", "👀", "💪", "🎵", "🌟", "🌈", "🍕",
            "🎮", "📚", "💻", "🏆", "🎯", "🚀", "😴", "🥳",
            "😤", "🤯", "👻", "🫡", "💬", "🌸", "🐱", "🐶"
        };

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), dp(8), dp(12), dp(8));

        androidx.appcompat.app.AlertDialog[] dialogHolder = new androidx.appcompat.app.AlertDialog[1];

        int perRow = 8;
        for (int i = 0; i < emojis.length; i += perRow) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int j = i; j < Math.min(i + perRow, emojis.length); j++) {
                TextView ev = new TextView(this);
                ev.setText(emojis[j]);
                ev.setTextSize(22);
                ev.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(40), dp(44));
                ev.setLayoutParams(p);
                final String emoji = emojis[j];
                ev.setOnClickListener(v -> {
                    insertEmoji(emoji);
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                });
                row.addView(ev);
            }
            grid.addView(row);
        }

        ScrollView sv = new ScrollView(this);
        sv.addView(grid);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this,
                R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setView(sv)
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    private void insertEmoji(String emoji) {
        EditText target = inputPostBody.hasFocus() ? inputPostBody : inputPostTitle;
        int start = Math.max(target.getSelectionStart(), 0);
        target.getText().insert(start, emoji);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void refreshPublishState() {
        buttonPublishPost.setEnabled(!inputPostTitle.getText().toString().trim().isEmpty());
        buttonPublishPost.setAlpha(buttonPublishPost.isEnabled() ? 1.0f : 0.45f);
    }

    private void publishPost() {
        String title = inputPostTitle.getText().toString().trim();
        if (title.isEmpty()) {
            inputPostTitle.setError(getString(R.string.dialog_post_title_hint));
            return;
        }

        String body = inputPostBody.getText().toString().trim();
        String category = AppData.canonicalPostCategory(getSelectedCategory());
        String imageUri = selectedPostImageUri == null ? null : selectedPostImageUri.toString();
        BackendRuntime backend = BackendRuntime.from(this);
        publishOnline(backend, title, body, category, imageUri);
    }

    private void publishOnline(
            BackendRuntime backend, String title, String body, String category, String imageUri) {
        if (AppData.getCurrentUserId() == null) {
            Toast.makeText(this, R.string.toast_action_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        BackendForumGateway.MediaUpload upload;
        try {
            boolean retained = editingPost != null && imageUri != null
                    && imageUri.equals(AppData.getPostImageUri(editingPost));
            upload = retained ? null : BackendMedia.read(this, selectedPostImageUri);
        } catch (IOException error) {
            Toast.makeText(this, R.string.toast_action_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        buttonPublishPost.setEnabled(false);
        BackendModerationGateway.Callback<BackendForumGateway.PostSnapshot> callback =
                new BackendModerationGateway.Callback<>() {
                    @Override public void onSuccess(BackendForumGateway.PostSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;
                        Post saved = AppData.upsertRemotePost(snapshot, backend.config().baseUrl());
                        AppData.setPostCategory(saved, category);
                        Toast.makeText(CreatePostActivity.this,
                                editingPost == null ? R.string.toast_post_created : R.string.toast_post_updated,
                                Toast.LENGTH_SHORT).show();
                        finish();
                    }
                    @Override public void onError(BackendException error) {
                        if (isFinishing() || isDestroyed()) return;
                        if (UiPreferences.handleExpiredSession(CreatePostActivity.this, error)) return;
                        buttonPublishPost.setEnabled(true);
                        Toast.makeText(CreatePostActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                };
        if (editingPost == null) {
            backend.forum().createPost(
                    AppData.getCurrentUserId(), AppData.getSelectedForumKey(), title, body, upload, category, callback);
        } else {
            backend.forum().updatePost(
                    AppData.getCurrentUserId(), editingPost.id, title, body, upload,
                    AppData.getPostImageUri(editingPost), category, callback);
        }
    }

    private String getSelectedCategory() {
        if (radioPostCategory.getCheckedRadioButtonId() == R.id.radioCategoryCustom) {
            String custom = inputPostCustomCategory.getText().toString().trim();
            return custom.isEmpty() ? getString(R.string.category_study) : custom;
        }
        RadioButton checked = findViewById(radioPostCategory.getCheckedRadioButtonId());
        return checked == null ? getString(R.string.category_study) : checked.getText().toString();
    }

    private void showMentionDialog() { ServerFeatures.pickFollowing(this, mention -> insertMention(mention)); }

    private void insertMention(String mention) {
        int start = Math.max(inputPostBody.getSelectionStart(), 0);
        String prefix = start > 0 ? " " : "";
        inputPostBody.getText().insert(start, prefix + mention + " ");
        inputPostBody.requestFocus();
    }

    private GradientDrawable makeAvatarBackground() {
        GradientDrawable drawable = (GradientDrawable) ContextCompat.getDrawable(this, R.drawable.bg_avatar_circle).mutate();
        drawable.setColor(UiPreferences.getAvatarColor(this));
        return drawable;
    }

    private String getAvatarLetter(String nickname) {
        String trimmed = nickname == null ? "" : nickname.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
    }
}
