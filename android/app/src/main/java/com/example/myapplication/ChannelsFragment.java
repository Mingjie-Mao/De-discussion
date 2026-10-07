package com.example.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.UUID;

import backend.BackendException;
import backend.BackendForumGateway;
import backend.BackendModerationGateway;
import backend.BackendRuntime;
import dao.model.Message;
import dao.model.Post;

public class ChannelsFragment extends Fragment implements RefreshablePage {
    private LinearLayout layoutChannelsHeader;
    private TextView textChannelsMode;
    private TextView textChannelsTitle;
    private TextView textChannelsSubtitle;
    private TextView textFeedEmptyTitle;
    private TextView textFeedEmptyBody;
    private ImageView imageChannelsForumAvatar;
    private ImageView imageChannelSearchIcon;
    private LinearLayout layoutChannelSearch;
    private LinearLayout layoutSearchAssistant;
    private TextView textSearchAssistantTitle;
    private TextView textSearchAssistantBody;
    private EditText inputChannelSearch;
    private ImageButton buttonClearSearch;
    private ImageButton buttonChannelsDrawer;
    private RecyclerView recyclerPosts;
    private RadioGroup radioChannelCategories;
    private String currentForumKey;
    private String searchQuery = "";
    private String selectedCategory = "";
    private boolean synchronizing;
    private View layoutFeedSync;
    private TextView textFeedSync;
    private View buttonFeedRetry;
    private long syncRevision;
    private com.google.android.material.button.MaterialButton buttonFeedMore;
    private String nextFeedCursor;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_channels, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        layoutChannelsHeader = view.findViewById(R.id.layoutChannelsHeader);
        textChannelsMode = view.findViewById(R.id.textChannelsMode);
        textChannelsTitle = view.findViewById(R.id.textChannelsTitle);
        textChannelsSubtitle = view.findViewById(R.id.textChannelsSubtitle);
        textFeedEmptyTitle = view.findViewById(R.id.textFeedEmptyTitle);
        textFeedEmptyBody = view.findViewById(R.id.textFeedEmptyBody);
        imageChannelsForumAvatar = view.findViewById(R.id.imageChannelsForumAvatar);
        imageChannelSearchIcon = view.findViewById(R.id.imageChannelSearchIcon);
        layoutChannelSearch = view.findViewById(R.id.layoutChannelSearch);
        layoutSearchAssistant = view.findViewById(R.id.layoutSearchAssistant);
        layoutSearchAssistant.setVisibility(View.GONE);
        textSearchAssistantTitle = view.findViewById(R.id.textSearchAssistantTitle);
        textSearchAssistantBody = view.findViewById(R.id.textSearchAssistantBody);
        inputChannelSearch = view.findViewById(R.id.inputChannelSearch);
        buttonClearSearch = view.findViewById(R.id.buttonClearSearch);
        recyclerPosts = view.findViewById(R.id.recyclerPosts);
        layoutFeedSync = view.findViewById(R.id.layoutFeedSync);
        textFeedSync = view.findViewById(R.id.textFeedSync);
        buttonFeedRetry = view.findViewById(R.id.buttonFeedRetry);
        buttonFeedRetry.setOnClickListener(v -> synchronizeSelectedForum());
        buttonFeedMore = view.findViewById(R.id.buttonFeedMore);
        buttonFeedMore.setOnClickListener(v -> synchronizeSelectedForum(true));
        radioChannelCategories = view.findViewById(R.id.radioChannelCategories);
        buttonChannelsDrawer = view.findViewById(R.id.buttonChannelsDrawer);
        ImageButton buttonCreatePost = view.findViewById(R.id.buttonCreatePost);

        recyclerPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        layoutChannelSearch.setOnClickListener(v -> showKeyboard());
        inputChannelSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchQuery = inputChannelSearch.getText().toString();
                refreshContent();
                inputChannelSearch.clearFocus();
                hideKeyboard();
                return true;
            }
            return false;
        });
        inputChannelSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s == null ? "" : s.toString();
                refreshContent();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        buttonChannelsDrawer.setFocusable(false);
        buttonChannelsDrawer.setFocusableInTouchMode(false);
        buttonChannelsDrawer.setOnClickListener(v -> host().openDrawer());
        buttonCreatePost.setOnClickListener(v -> startActivity(new Intent(requireContext(), CreatePostActivity.class)));
        buttonClearSearch.setOnClickListener(v -> inputChannelSearch.setText(""));
        refreshContent();
    }

    private void showKeyboard() {
        inputChannelSearch.requestFocus();
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(inputChannelSearch, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(inputChannelSearch.getWindowToken(), 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        synchronizeSelectedForum();
    }

    @Override
    public void refreshContent() {
        if (!isAdded() || getView() == null || textChannelsMode == null || recyclerPosts == null) {
            return;
        }

        layoutSearchAssistant.setVisibility(View.GONE);
        textChannelsMode.setText(AppData.getCurrentModeLabel(requireContext()));
        String selectedForumKey = AppData.getSelectedForumKey();
        if (currentForumKey == null || !currentForumKey.equals(selectedForumKey)) {
            currentForumKey = selectedForumKey;
            synchronizeSelectedForum();
            if (!searchQuery.isEmpty()) {
                inputChannelSearch.setText("");
                return;
            }
        }
        textChannelsTitle.setText(AppData.getSelectedForumLabel(requireContext()));
        imageChannelsForumAvatar.setImageResource(AppData.getSelectedForumAvatarResId());
        textChannelsSubtitle.setVisibility(View.GONE);
        applyHeaderTheme(selectedForumKey);
        inputChannelSearch.setHint(getString(R.string.search_channel_hint, AppData.getSelectedForumLabel(requireContext())));
        buttonClearSearch.setVisibility(searchQuery.trim().isEmpty() ? View.GONE : View.VISIBLE);
        refreshCategoryFilters();

        ArrayList<Post> posts = AppData.searchPosts(requireContext(), searchQuery);
        if (!selectedCategory.isEmpty() && !selectedCategory.equals(getString(R.string.category_all))) {
            posts.removeIf(post -> !selectedCategory.equals(AppData.getPostCategory(requireContext(), post)));
        }
        if(recyclerPosts.getAdapter() instanceof PostAdapter adapter) adapter.replacePosts(posts);
        else {
            PostAdapter adapter = new PostAdapter(posts);
            adapter.setOnClickListener(this::openPost);
            adapter.setOnVoteClickListener((post, direction) -> AppData.togglePostVote(post, direction));
            adapter.setOnBookmarkClickListener(AppData::togglePostBookmark);
            adapter.setOnUserClickListener(this::openUserProfile);
            recyclerPosts.setAdapter(adapter);
        }

        boolean empty = posts.isEmpty();
        recyclerPosts.setVisibility(empty ? View.GONE : View.VISIBLE);
        textFeedEmptyTitle.setVisibility(empty ? View.VISIBLE : View.GONE);
        textFeedEmptyBody.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty && !searchQuery.trim().isEmpty()) {
            textFeedEmptyTitle.setText(R.string.search_empty_title);
            textFeedEmptyBody.setText(R.string.search_empty_body);
        } else {
            textFeedEmptyTitle.setText(AppData.getFeedEmptyTitle(requireContext()));
            textFeedEmptyBody.setText(AppData.getFeedEmptyBody(requireContext()));
        }

        if (!searchQuery.trim().isEmpty() && !empty && textSearchAssistantBody != null) {
            textSearchAssistantBody.setText(buildSearchSummary(posts));
            layoutSearchAssistant.setVisibility(View.VISIBLE);
        }
    }

    private void synchronizeSelectedForum() {
        synchronizeSelectedForum(false);
    }

    private void synchronizeSelectedForum(boolean more) {
        if (!isAdded() || getView() == null) return;
        BackendRuntime backend = BackendRuntime.from(requireContext());
        if (!backend.config().isEnabled()) {
            layoutFeedSync.setVisibility(View.GONE);
            refreshContent();
            return;
        }
        if (synchronizing) return;
        if (more && nextFeedCursor == null) return;
        synchronizing = true;
        long request = ++syncRevision;
        String forumKey = AppData.getSelectedForumKey();
        String origin = backend.config().baseUrl();
        layoutFeedSync.setVisibility(View.VISIBLE);
        textFeedSync.setText(R.string.feed_sync_loading);
        buttonFeedRetry.setEnabled(false);
        buttonFeedMore.setEnabled(false);
        backend.forum().fetchFeedPage(forumKey, more ? nextFeedCursor : null,
                new BackendModerationGateway.Callback<backend.BackendForumGateway.FeedPage>() {
                    @Override public void onSuccess(backend.BackendForumGateway.FeedPage value) {
                        if (!isAdded() || getView() == null || request != syncRevision) return;
                        synchronizing = false;
                        if (!origin.equals(backend.config().baseUrl())) { synchronizeSelectedForum(); return; }
                        if (more) {
                            for (backend.BackendForumGateway.PostSnapshot post : value.items()) AppData.upsertRemotePost(post, origin);
                        } else AppData.synchronizeForum(forumKey, value.items(), origin);
                        nextFeedCursor = value.hasMore() ? value.nextCursor() : null;
                        buttonFeedMore.setEnabled(true);
                        buttonFeedMore.setVisibility(nextFeedCursor == null ? View.GONE : View.VISIBLE);
                        layoutFeedSync.setVisibility(View.GONE);
                        buttonFeedRetry.setEnabled(true);
                        buttonFeedMore.setEnabled(true);
                        refreshContent();
                        if (!forumKey.equals(AppData.getSelectedForumKey())) synchronizeSelectedForum();
                    }
                    @Override public void onError(BackendException error) {
                        if (!isAdded() || getView() == null || request != syncRevision) return;
                        synchronizing = false;
                        layoutFeedSync.setVisibility(View.VISIBLE);
                        textFeedSync.setText(getString(R.string.feed_sync_failed) + "\n" + error.getMessage());
                        buttonFeedRetry.setEnabled(true);
                        buttonFeedMore.setEnabled(true);
                        refreshContent();
                        if (!forumKey.equals(AppData.getSelectedForumKey())) synchronizeSelectedForum();
                    }
                });
    }

    @Override public void onDestroyView() {
        syncRevision++;
        synchronizing = false;
        super.onDestroyView();
    }

    private void openPost(Post post) {
        if (!isAdded()) {
            return;
        }

        Intent intent = new Intent(requireContext(), PostViewerActivity.class);
        intent.putExtra(PostViewerActivity.EXTRA_POST_ID, post.id.toString());
        startActivity(intent);
    }

    private void openUserProfile(java.util.UUID userId) {
        if (!isAdded() || userId == null) {
            return;
        }
        Intent intent = new Intent(requireContext(), UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, userId.toString());
        startActivity(intent);
    }

    private void refreshCategoryFilters() {
        if (radioChannelCategories == null) {
            return;
        }
        ArrayList<String> categories = AppData.getPostCategories(requireContext());
        if (selectedCategory.isEmpty() || !categories.contains(selectedCategory)) {
            selectedCategory = getString(R.string.category_all);
        }
        radioChannelCategories.setOnCheckedChangeListener(null);
        radioChannelCategories.removeAllViews();
        boolean firstCategory = true;
        for (String category : categories) {
            RadioButton button = new RadioButton(requireContext());
            button.setId(View.generateViewId());
            button.setText(category);
            button.setButtonDrawable(null);
            button.setGravity(Gravity.CENTER);
            button.setMinWidth(dp(76));
            button.setPadding(dp(16), 0, dp(16), 0);
            button.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_primary));
            button.setTextSize(15);
            button.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            RadioGroup.LayoutParams params = new RadioGroup.LayoutParams(
                    RadioGroup.LayoutParams.WRAP_CONTENT,
                    RadioGroup.LayoutParams.MATCH_PARENT
            );
            if (!firstCategory) {
                params.setMarginStart(dp(8));
            }
            radioChannelCategories.addView(button, params);
            firstCategory = false;
            if (category.equals(selectedCategory)) {
                button.setChecked(true);
            }
            styleCategoryButton(button, category.equals(selectedCategory));
        }
        radioChannelCategories.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton checked = group.findViewById(checkedId);
            if (checked != null) {
                selectedCategory = checked.getText().toString();
                refreshContent();
            }
        });
    }

    private void styleCategoryButton(RadioButton button, boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(999));
        background.setColor(selected
                ? Color.argb(170, 232, 236, 243)
                : Color.argb(74, 255, 255, 255));
        background.setStroke(dp(1), selected
                ? ContextCompat.getColor(requireContext(), R.color.tab_bar_stroke)
                : Color.argb(128, 226, 229, 234));
        button.setBackground(background);
        button.setTextColor(ContextCompat.getColor(
                requireContext(),
                selected ? R.color.ink_primary : R.color.ink_secondary
        ));
    }

    private MainActivity host() {
        return (MainActivity) requireActivity();
    }

    private void applyHeaderTheme(String forumKey) {
        if (!isAdded() || layoutChannelsHeader == null) {
            return;
        }

        int headerColor = ContextCompat.getColor(requireContext(), AppData.getForumHeaderColorResId(forumKey));
        int onColor = ContextCompat.getColor(requireContext(), R.color.forum_header_on);
        int onSecondary = ContextCompat.getColor(requireContext(), R.color.forum_header_on_secondary);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(28));
        background.setColor(headerColor);
        layoutChannelsHeader.setBackground(background);

        textChannelsTitle.setTextColor(onColor);
        textChannelsSubtitle.setTextColor(onSecondary);
        buttonChannelsDrawer.setColorFilter(onColor);
        textChannelsMode.setTextColor(onColor);

        GradientDrawable modePill = new GradientDrawable();
        modePill.setShape(GradientDrawable.RECTANGLE);
        modePill.setCornerRadius(dp(999));
        modePill.setColor(Color.argb(38, 255, 255, 255));
        textChannelsMode.setBackground(modePill);

        GradientDrawable searchBackground = new GradientDrawable();
        searchBackground.setShape(GradientDrawable.RECTANGLE);
        searchBackground.setCornerRadius(dp(999));
        searchBackground.setColor(Color.argb(42, 255, 255, 255));
        layoutChannelSearch.setBackground(searchBackground);
        inputChannelSearch.setTextColor(onColor);
        inputChannelSearch.setHintTextColor(onSecondary);
        imageChannelSearchIcon.setColorFilter(onColor);
        buttonClearSearch.setColorFilter(onSecondary);

        imageChannelsForumAvatar.setImageTintList(null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String buildSearchSummary(ArrayList<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return "";
        }

        Post hotPost = posts.get(0);
        int maxReplies = 0;
        int maxVote = Integer.MIN_VALUE;
        Post topVotePost = posts.get(0);
        Message bestReply = null;
        int bestReplyScore = Integer.MIN_VALUE;

        for (Post post : posts) {
            ArrayList<Message> msgs = AppData.getMessages(post);
            Message root = AppData.getRootMessage(post);
            UUID rootId = root != null ? root.id() : null;
            int replyCount = AppData.getPostCommentCount(post);
            for (Message m : msgs) {
                if (rootId != null && m.id().equals(rootId)) continue;
                int score = AppData.getMessageVoteScore(m);
                if (score > bestReplyScore) {
                    bestReplyScore = score;
                    bestReply = m;
                }
            }
            if (replyCount > maxReplies) {
                maxReplies = replyCount;
                hotPost = post;
            }
            int pVote = AppData.getPostVoteScore(post);
            if (pVote > maxVote) {
                maxVote = pVote;
                topVotePost = post;
            }
        }

        String hotSummary = summarizePost(hotPost);
        String topSummary = bestReply == null
                ? summarizePost(topVotePost)
                : summarizeReply(bestReply);
        return getString(R.string.search_popular_summary,limitSummary(hotSummary)) + "\n"
                + getString(R.string.search_top_summary,limitSummary(topSummary));
    }

    private String summarizePost(Post post) {
        return compactText(AppData.getPostDisplayTitle(requireContext(),post)+" · "+AppData.getPostDisplayBody(requireContext(),post));
    }

    private String summarizeReply(Message reply) {
        return compactText(AppData.getMessageDisplayContent(requireContext(),reply));
    }

    private String compactText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("[\"“”‘’]", "")
                .trim();
    }

    private String limitSummary(String text) {
        String compact = compactText(text);
        int maxLength = 18;
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength - 1) + "…";
    }
}
