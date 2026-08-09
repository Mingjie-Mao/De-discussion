package com.example.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
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
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import backend.BackendConfig;
import backend.BackendException;
import backend.BackendModerationGateway;
import backend.BackendReportTarget;
import backend.BackendRuntime;
import dao.model.Message;
import dao.model.Post;
import dao.model.User;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PostViewerActivity extends AppCompatActivity {
    public static final String EXTRA_POST_ID = "post_id";

    private TextView textPostViewerMode;
    private TextView textPostViewerAuthorAvatar;
    private TextView textPostViewerForum;
    private TextView textPostViewerMeta;
    private TextView textPostViewerTitle;
    private TextView textPostViewerBody;
    private TextView textPostViewerState;
    private TextView textPostViewerScore;
    private TextView textPostViewerCommentsCount;
    private TextView textPostViewerBookmarkCount;
    private TextView textCommentsEmpty;
    private ImageView imagePostViewerAttachment;
    private ImageButton buttonPostUpvote;
    private ImageButton buttonPostDownvote;
    private ImageView imagePostBookmarkIcon;
    private LinearLayout buttonPostBookmark;
    private LinearLayout buttonPostComments;
    private LinearLayout layoutPostViewerHeaderCard;
    private Button buttonBack;
    private ImageButton buttonPostMenu;
    private ImageButton buttonPostEdit;
    private ImageButton buttonPostDelete;
    private NestedScrollView postViewerScroll;
    private RecyclerView recyclerMessages;
    private Post post;
    private Message rootMessage;
    private UUID pendingScrollMessageId;
    private Uri selectedReplyImageUri;
    private ImageView activeReplyImagePreview;
    private View activeReplyImagePreviewContainer;
    private TextView activeReplySendButton;
    private final Set<UUID> expandedTopLevelComments = new HashSet<>();
    private final Set<UUID> pendingReports = new HashSet<>();

    private final ActivityResultLauncher<String> pickReplyImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri == null) {
                    return;
                }
                try {
                    selectedReplyImageUri = ImageStorage.copyToLocalImage(this, uri);
                } catch (IOException exception) {
                    Toast.makeText(this, getString(R.string.toast_action_failed), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (activeReplyImagePreview != null) {
                    activeReplyImagePreview.setImageURI(selectedReplyImageUri);
                }
                if (activeReplyImagePreviewContainer != null) {
                    activeReplyImagePreviewContainer.setVisibility(View.VISIBLE);
                }
                if (activeReplySendButton != null) {
                    updateReplySendState(activeReplySendButton, null);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiPreferences.applyAppearance(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_post_viewer);
        View postViewerRoot = findViewById(R.id.postViewerRoot);
        int initialPaddingLeft = postViewerRoot.getPaddingLeft();
        int initialPaddingTop = postViewerRoot.getPaddingTop();
        int initialPaddingRight = postViewerRoot.getPaddingRight();
        int initialPaddingBottom = postViewerRoot.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(postViewerRoot, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    initialPaddingLeft,
                    systemBars.top + initialPaddingTop,
                    initialPaddingRight,
                    initialPaddingBottom
            );
            postViewerScroll.setPaddingRelative(
                    postViewerScroll.getPaddingStart(),
                    postViewerScroll.getPaddingTop(),
                    postViewerScroll.getPaddingEnd(),
                    systemBars.bottom + dp(12)
            );
            return insets;
        });

        textPostViewerMode = findViewById(R.id.textPostViewerMode);
        textPostViewerAuthorAvatar = findViewById(R.id.textPostViewerAuthorAvatar);
        textPostViewerForum = findViewById(R.id.textPostViewerForum);
        textPostViewerMeta = findViewById(R.id.textPostViewerMeta);
        textPostViewerTitle = findViewById(R.id.textPostViewerTitle);
        textPostViewerBody = findViewById(R.id.textPostViewerBody);
        textPostViewerState = findViewById(R.id.textPostViewerState);
        textPostViewerScore = findViewById(R.id.textPostViewerScore);
        textPostViewerCommentsCount = findViewById(R.id.textPostViewerCommentsCount);
        textPostViewerBookmarkCount = findViewById(R.id.textPostViewerBookmarkCount);
        textCommentsEmpty = findViewById(R.id.textCommentsEmpty);
        imagePostViewerAttachment = findViewById(R.id.imagePostViewerAttachment);
        buttonPostComments = findViewById(R.id.textPostViewerComments);
        layoutPostViewerHeaderCard = findViewById(R.id.layoutPostViewerHeaderCard);
        buttonPostUpvote = findViewById(R.id.buttonPostUpvote);
        buttonPostDownvote = findViewById(R.id.buttonPostDownvote);
        buttonPostBookmark = findViewById(R.id.buttonPostBookmark);
        imagePostBookmarkIcon = findViewById(R.id.imagePostBookmarkIcon);
        buttonBack = findViewById(R.id.buttonBack);
        buttonPostMenu = findViewById(R.id.buttonPostMenu);
        buttonPostEdit = findViewById(R.id.buttonPostEdit);
        buttonPostDelete = findViewById(R.id.buttonPostDelete);
        postViewerScroll = findViewById(R.id.postViewerScroll);
        recyclerMessages = findViewById(R.id.recyclerMessages);

        recyclerMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerMessages.setNestedScrollingEnabled(false);
        post = AppData.getPostById(getIntent().getStringExtra(EXTRA_POST_ID));

        buttonBack.setOnClickListener(v -> finish());
        buttonPostUpvote.setOnClickListener(v -> {
            if (AppData.togglePostVote(post, 1)) {
                refreshUi();
                animateVote(buttonPostUpvote);
            }
        });
        buttonPostDownvote.setOnClickListener(v -> {
            if (AppData.togglePostVote(post, -1)) {
                refreshUi();
                animateVote(buttonPostDownvote);
            }
        });
        buttonPostMenu.setOnClickListener(v -> {
            if (rootMessage != null) {
                handleMessageAction(rootMessage);
            }
        });
        buttonPostEdit.setOnClickListener(v -> openPostEditor());
        buttonPostDelete.setOnClickListener(v -> confirmDeletePost());
        buttonPostComments.setOnClickListener(v -> showReplyDialog(rootMessage));
        textPostViewerMeta.setOnClickListener(v -> openUserProfile(post == null ? null : post.poster));
        textPostViewerAuthorAvatar.setOnClickListener(v -> openUserProfile(post == null ? null : post.poster));
        textPostViewerAuthorAvatar.setClickable(true);
        textPostViewerAuthorAvatar.setFocusable(true);
        buttonPostBookmark.setOnClickListener(v -> {
            if (AppData.togglePostBookmark(post)) {
                refreshUi();
                animateVote(imagePostBookmarkIcon);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void refreshUi() {
        if (post == null) {
            textPostViewerMode.setText(R.string.thread_unavailable);
            textPostViewerForum.setText(R.string.post_not_found);
            textPostViewerMeta.setText(R.string.post_not_found_body);
            textPostViewerTitle.setText("");
            textPostViewerBody.setText(R.string.post_not_found_summary);
            textPostViewerState.setVisibility(View.GONE);
            buttonPostEdit.setVisibility(View.GONE);
            buttonPostDelete.setVisibility(View.GONE);
            textCommentsEmpty.setVisibility(View.VISIBLE);
            recyclerMessages.setAdapter(new MessageAdapter(new ArrayList<>()));
            return;
        }

        rootMessage = AppData.getRootMessage(post);
        boolean ownsPost = AppData.getCurrentUserId() != null
                && AppData.getCurrentUserId().equals(post.poster);
        buttonPostEdit.setVisibility(ownsPost ? View.VISIBLE : View.GONE);
        buttonPostDelete.setVisibility(ownsPost ? View.VISIBLE : View.GONE);
        textPostViewerMode.setText(AppData.getCurrentModeLabel(this));
        textPostViewerForum.setText(AppData.getPostCommunityLabel(this, post));
        GradientDrawable authorAvatarBg = new GradientDrawable();
        authorAvatarBg.setShape(GradientDrawable.OVAL);
        authorAvatarBg.setColor(AppData.getAvatarColor(this, post.poster));
        textPostViewerAuthorAvatar.setBackground(authorAvatarBg);
        textPostViewerAuthorAvatar.setText(AppData.getAvatarLetter(post.poster));
        textPostViewerMeta.setText(getString(
                R.string.message_author_line,
                AppData.getDisplayName(this, post.poster),
                AppData.getPostTimestampLabel(post)
        ));
        textPostViewerTitle.setText(AppData.getPostTitle(post));
        textPostViewerBody.setText(AppData.getPostBody(post));
        applyHeaderTheme();
        String postImageUri = AppData.getPostImageUri(post);
        if (postImageUri == null || postImageUri.isEmpty()) {
            imagePostViewerAttachment.setImageDrawable(null);
            imagePostViewerAttachment.setOnClickListener(null);
            imagePostViewerAttachment.setVisibility(View.GONE);
        } else {
            Uri attachmentUri = Uri.parse(postImageUri);
            imagePostViewerAttachment.setImageURI(attachmentUri);
            imagePostViewerAttachment.setOnClickListener(v ->
                    ImageAttachmentViewer.show(this, attachmentUri, R.string.post_image_attachment));
            imagePostViewerAttachment.setVisibility(View.VISIBLE);
        }
        textPostViewerCommentsCount.setText(AppData.getPostReplyCountLabel(this, post));
        textPostViewerBookmarkCount.setText(AppData.getPostBookmarkCountLabel(this, post));
        textPostViewerScore.setText(String.valueOf(AppData.getPostVoteScore(post)));
        updatePostVoteColors();

        String rootState = rootMessage == null ? "" : AppData.getMessageStatus(this, rootMessage);
        textPostViewerState.setText(rootState);
        textPostViewerState.setVisibility(rootState.isEmpty() ? View.GONE : View.VISIBLE);

        ArrayList<Message> messages = AppData.getMessages(post, expandedTopLevelComments);
        textCommentsEmpty.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);

        MessageAdapter adapter = new MessageAdapter(messages, expandedTopLevelComments);
        adapter.setOnMessageActionListener(this::handleMessageAction);
        adapter.setOnMessageVoteListener((message, direction) -> {
            AppData.toggleMessageVote(message, direction);
            refreshUi();
        });
        adapter.setOnMessageReplyListener(this::showReplyDialog);
        adapter.setOnReplyThreadToggleListener(topLevelCommentId -> {
            if (expandedTopLevelComments.contains(topLevelCommentId)) {
                expandedTopLevelComments.remove(topLevelCommentId);
            } else {
                expandedTopLevelComments.add(topLevelCommentId);
            }
            refreshUi();
        });
        adapter.setOnUserClickListener(this::openUserProfile);
        recyclerMessages.setAdapter(adapter);
        scrollToPendingReply(messages);
    }

    private void openUserProfile(UUID userId) {
        if (userId == null) {
            return;
        }
        Intent intent = new Intent(this, UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, userId.toString());
        startActivity(intent);
    }

    private void openPostEditor() {
        if (post == null) {
            return;
        }
        Intent intent = new Intent(this, CreatePostActivity.class);
        intent.putExtra(CreatePostActivity.EXTRA_EDIT_POST_ID, post.id.toString());
        startActivity(intent);
    }

    private void confirmDeletePost() {
        if (post == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setTitle(R.string.action_delete_post)
                .setMessage(R.string.dialog_delete_post_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete_post, (dialog, which) -> {
                    if (AppData.deletePost(post)) {
                        Toast.makeText(this, R.string.toast_post_deleted, Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, R.string.toast_action_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void updatePostVoteColors() {
        int upvoteColor = ContextCompat.getColor(this, R.color.vote_up);
        int neutralColor = ContextCompat.getColor(this, R.color.forum_header_on_secondary);
        int downvoteColor = ContextCompat.getColor(this, R.color.vote_down);
        int primaryColor = ContextCompat.getColor(this, R.color.ink_primary);
        int reportColor = ContextCompat.getColor(this, R.color.danger_ink);

        int voteDirection = AppData.getCurrentUserPostVote(post);
        buttonPostUpvote.setImageResource(voteDirection > 0
                ? R.drawable.ic_vote_up_filled_24
                : R.drawable.ic_vote_up_outline_24);
        buttonPostDownvote.setImageResource(voteDirection < 0
                ? R.drawable.ic_vote_down_filled_24
                : R.drawable.ic_vote_down_outline_24);
        boolean bookmarked = AppData.hasBookmarkedPost(post);
        imagePostBookmarkIcon.setImageResource(bookmarked
                ? R.drawable.ic_bookmark_filled_24
                : R.drawable.ic_bookmark_24);
        imagePostBookmarkIcon.setColorFilter(bookmarked
                ? ContextCompat.getColor(this, R.color.rank_gold)
                : ContextCompat.getColor(this, R.color.ink_primary));
        boolean activeAction = AppData.isAdminMode() ? AppData.isHidden(rootMessage) : AppData.hasCurrentUserReported(rootMessage);
        if (AppData.isAdminMode()) {
            buttonPostMenu.setImageResource(R.drawable.ic_hidden_24);
        } else {
            buttonPostMenu.setImageResource(R.drawable.ic_flag_outline_24);
        }
        buttonPostMenu.setColorFilter(activeAction ? reportColor : neutralColor);
        textPostViewerScore.setTextColor(voteDirection > 0
                ? upvoteColor
                : voteDirection < 0 ? downvoteColor : primaryColor);
    }

    private void applyHeaderTheme() {
        if (post == null || layoutPostViewerHeaderCard == null) {
            return;
        }

        int headerColor = ContextCompat.getColor(this, AppData.getPostHeaderColorResId(post));
        int onColor = ContextCompat.getColor(this, R.color.forum_header_on);
        int onSecondary = ContextCompat.getColor(this, R.color.forum_header_on_secondary);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(28));
        background.setColor(headerColor);
        layoutPostViewerHeaderCard.setBackground(background);

        textPostViewerForum.setTextColor(onSecondary);
        textPostViewerMeta.setTextColor(onSecondary);
        textPostViewerTitle.setTextColor(onColor);
        textPostViewerBody.setTextColor(onSecondary);
        textPostViewerState.setTextColor(onSecondary);
    }

    private void animateVote(View view) {
        view.animate().cancel();
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.animate()
                .scaleX(1.16f)
                .scaleY(1.16f)
                .setDuration(110L)
                .withEndAction(() -> view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(160L)
                        .start())
                .start();
    }

    private void handleMessageAction(Message message) {
        BackendRuntime backend = BackendRuntime.from(this);
        BackendConfig backendConfig = backend.config();

        if (backendConfig.isEnabled() && AppData.isAdminMode()) {
            Toast.makeText(this, R.string.toast_review_in_queue, Toast.LENGTH_SHORT).show();
            return;
        }

        if (backendConfig.isEnabled()) {
            submitOnlineReport(backend, message);
            return;
        }

        boolean success = AppData.isAdminMode()
                ? AppData.toggleHidden(message)
                : AppData.toggleReport(message);
        if (!success) {
            Toast.makeText(this, getString(R.string.toast_action_failed), Toast.LENGTH_SHORT).show();
            return;
        }

        String feedback = AppData.isAdminMode()
                ? (AppData.isHidden(message)
                ? getString(R.string.toast_reply_hidden)
                : getString(R.string.toast_reply_restored))
                : (AppData.hasCurrentUserReported(message)
                ? getString(R.string.toast_reply_reported)
                : getString(R.string.toast_report_removed));
        Toast.makeText(this, feedback, Toast.LENGTH_SHORT).show();
        refreshUi();
    }

    private void submitOnlineReport(BackendRuntime backend, Message message) {
        if (message == null || AppData.getCurrentUserId() == null) {
            Toast.makeText(this, R.string.toast_action_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (AppData.hasCurrentUserReported(message)) {
            Toast.makeText(this, R.string.toast_online_report_final, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!pendingReports.add(message.id())) {
            return;
        }

        BackendReportTarget target = AppData.backendReportTarget(message);
        if (target == null) {
            pendingReports.remove(message.id());
            Toast.makeText(this, R.string.toast_action_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, R.string.toast_report_submitting, Toast.LENGTH_SHORT).show();
        backend.moderation().submitReport(target, AppData.getCurrentUserId(),
                new BackendModerationGateway.Callback<>() {
                    @Override
                    public void onSuccess(UUID reportId) {
                        pendingReports.remove(message.id());
                        AppData.addReport(message);
                        Toast.makeText(
                                PostViewerActivity.this,
                                R.string.toast_report_sent_for_review,
                                Toast.LENGTH_SHORT).show();
                        refreshUi();
                    }

                    @Override
                    public void onError(BackendException error) {
                        pendingReports.remove(message.id());
                        Toast.makeText(
                                PostViewerActivity.this,
                                getString(R.string.toast_report_failed, error.getMessage()),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showReplyDialog(Message parent) {
        if (post == null || parent == null) {
            Toast.makeText(this, getString(R.string.toast_action_failed), Toast.LENGTH_SHORT).show();
            return;
        }

        selectedReplyImageUri = null;
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setHint(getString(R.string.dialog_reply_to_hint, AppData.getUsername(parent.poster())));
        input.setBackgroundResource(R.drawable.bg_reply_input);
        input.setPadding(dp(18), dp(14), dp(18), dp(14));

        LinearLayout dialogContent = new LinearLayout(this);
        dialogContent.setOrientation(LinearLayout.VERTICAL);
        dialogContent.setBackgroundResource(R.drawable.bg_reply_dialog);
        dialogContent.setPadding(dp(14), dp(14), dp(14), dp(12));
        dialogContent.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(92)
        ));

        FrameLayout previewContainer = new FrameLayout(this);
        previewContainer.setVisibility(View.GONE);
        ImageView imagePreview = new ImageView(this);
        imagePreview.setAdjustViewBounds(true);
        imagePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imagePreview.setBackgroundResource(R.drawable.bg_card);
        previewContainer.addView(imagePreview, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        ImageButton buttonRemoveImage = new ImageButton(this);
        buttonRemoveImage.setBackgroundResource(R.drawable.bg_preview_remove);
        buttonRemoveImage.setImageResource(R.drawable.ic_close_24);
        buttonRemoveImage.setColorFilter(ContextCompat.getColor(this, R.color.ink_primary));
        buttonRemoveImage.setPadding(dp(6), dp(6), dp(6), dp(6));
        buttonRemoveImage.setOnClickListener(v -> {
            selectedReplyImageUri = null;
            imagePreview.setImageDrawable(null);
            previewContainer.setVisibility(View.GONE);
            if (activeReplySendButton != null) {
                updateReplySendState(activeReplySendButton, input);
            }
        });
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(32), dp(32));
        removeParams.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        removeParams.setMargins(0, dp(8), dp(8), 0);
        previewContainer.addView(buttonRemoveImage, removeParams);

        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(150)
        );
        imageParams.topMargin = dp(10);
        dialogContent.addView(previewContainer, imageParams);
        activeReplyImagePreview = imagePreview;
        activeReplyImagePreviewContainer = previewContainer;

        LinearLayout toolsRow = new LinearLayout(this);
        toolsRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toolsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        toolsParams.topMargin = dp(8);
        dialogContent.addView(toolsRow, toolsParams);

        ImageButton buttonAddEmoji = createReplyToolButton(R.drawable.ic_emoji_24, R.string.action_add_emoji);
        buttonAddEmoji.setOnClickListener(v -> showReplyEmojiPicker(input));
        toolsRow.addView(buttonAddEmoji);

        ImageButton buttonAttachImage = createReplyToolButton(R.drawable.ic_image_24, R.string.action_add_image);
        buttonAttachImage.setOnClickListener(v -> pickReplyImageLauncher.launch("image/*"));
        toolsRow.addView(buttonAttachImage);

        ImageButton buttonMentionFriend = createReplyToolButton(R.drawable.ic_at_24, R.string.action_mention_friend);
        buttonMentionFriend.setOnClickListener(v -> showMentionDialog(input));
        toolsRow.addView(buttonMentionFriend);

        Space toolsSpacer = new Space(this);
        toolsRow.addView(toolsSpacer, new LinearLayout.LayoutParams(0, 1, 1));

        TextView buttonSend = new TextView(this);
        buttonSend.setGravity(android.view.Gravity.CENTER);
        buttonSend.setMinWidth(dp(64));
        buttonSend.setPadding(dp(16), 0, dp(16), 0);
        buttonSend.setText(R.string.action_send);
        buttonSend.setTextSize(14);
        buttonSend.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        buttonSend.setBackgroundResource(R.drawable.bg_reply_send);
        buttonSend.setOnClickListener(v -> {
            if (buttonSend.isEnabled()) {
                publishReply(input, parent, (androidx.appcompat.app.AlertDialog) buttonSend.getTag());
            }
        });
        toolsRow.addView(buttonSend, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(40)
        ));
        activeReplySendButton = buttonSend;
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateReplySendState(buttonSend, input);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        updateReplySendState(buttonSend, input);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this,
                R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setView(dialogContent)
                .create();

        dialog.setOnShowListener(unused -> buttonSend.setTag(dialog));
        dialog.setOnDismissListener(unused -> {
            activeReplyImagePreview = null;
            activeReplyImagePreviewContainer = null;
            activeReplySendButton = null;
            selectedReplyImageUri = null;
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        input.requestFocus();
    }

    private ImageButton createReplyToolButton(int iconResId, int contentDescriptionResId) {
        ImageButton button = new ImageButton(this);
        button.setBackgroundResource(R.drawable.bg_reply_tool);
        button.setContentDescription(getString(contentDescriptionResId));
        button.setImageResource(iconResId);
        button.setColorFilter(ContextCompat.getColor(this, R.color.ink_primary));
        button.setPadding(dp(5), dp(5), dp(5), dp(5));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
        params.setMarginEnd(dp(8));
        button.setLayoutParams(params);
        return button;
    }

    private void showMentionDialog(EditText input) {
        ArrayList<User> followed = AppData.getFollowedPeople();
        if (followed.isEmpty()) {
            Toast.makeText(this, R.string.toast_follow_someone_first, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[followed.size()];
        for (int i = 0; i < followed.size(); i++) {
            labels[i] = "@" + followed.get(i).username();
        }
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setTitle(R.string.action_mention_friend)
                .setItems(labels, (dialog, which) -> insertMention(input, labels[which]))
                .show();
    }

    private void insertMention(EditText input, String mention) {
        int start = Math.max(input.getSelectionStart(), 0);
        String prefix = start > 0 ? " " : "";
        input.getText().insert(start, prefix + mention + " ");
        input.requestFocus();
    }

    private void showReplyEmojiPicker(EditText target) {
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
                ev.setGravity(android.view.Gravity.CENTER);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(40), dp(44));
                ev.setLayoutParams(p);
                final String emoji = emojis[j];
                ev.setOnClickListener(v -> {
                    int start = Math.max(target.getSelectionStart(), 0);
                    target.getText().insert(start, emoji);
                    if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                });
                row.addView(ev);
            }
            grid.addView(row);
        }
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(grid);
        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this,
                R.style.ThemeOverlay_App_MaterialAlertDialog)
                .setView(sv)
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    private void updateReplySendState(TextView buttonSend, EditText input) {
        boolean hasText = input != null && !input.getText().toString().trim().isEmpty();
        boolean enabled = hasText || selectedReplyImageUri != null;
        buttonSend.setEnabled(enabled);
        buttonSend.setAlpha(enabled ? 1.0f : 0.5f);
        buttonSend.setTextColor(ContextCompat.getColor(
                this,
                enabled ? R.color.accent_strong : R.color.ink_tertiary
        ));
    }

    private void publishReply(EditText input, Message parent, androidx.appcompat.app.AlertDialog dialog) {
        String content = input.getText().toString().trim();
        String imageUri = selectedReplyImageUri == null ? null : selectedReplyImageUri.toString();
        if (content.isEmpty() && imageUri == null) {
            input.setError(getString(R.string.dialog_reply_body_hint));
            return;
        }

        Message reply = AppData.createReply(parent, content, imageUri);
        if (reply == null) {
            Toast.makeText(this, getString(R.string.toast_action_failed), Toast.LENGTH_SHORT).show();
            return;
        }

        pendingScrollMessageId = reply.id();
        Toast.makeText(this, getString(R.string.toast_reply_created), Toast.LENGTH_SHORT).show();
        dialog.dismiss();
        refreshUi();
    }

    private void scrollToPendingReply(ArrayList<Message> messages) {
        if (pendingScrollMessageId == null) {
            return;
        }

        int targetPosition = -1;
        for (int i = 0; i < messages.size(); i++) {
            if (pendingScrollMessageId.equals(messages.get(i).id())) {
                targetPosition = i;
                break;
            }
        }
        pendingScrollMessageId = null;

        if (targetPosition >= 0) {
            int position = targetPosition;
            recyclerMessages.post(() -> {
                RecyclerView.ViewHolder holder = recyclerMessages.findViewHolderForAdapterPosition(position);
                if (holder == null) {
                    postViewerScroll.smoothScrollTo(0, recyclerMessages.getBottom());
                    return;
                }

                int targetY = recyclerMessages.getTop() + holder.itemView.getTop();
                postViewerScroll.smoothScrollTo(0, Math.max(0, targetY - dp(12)));
            });
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
