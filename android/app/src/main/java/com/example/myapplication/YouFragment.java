package com.example.myapplication;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

import dao.model.Message;
import dao.model.Post;
import dao.model.User;

public class YouFragment extends Fragment implements RefreshablePage {
    private enum Section {
        POSTS,
        COMMENTS,
        SAVED,
        LIKED
    }

    private TextView textYouAvatar;
    private TextView textYouNickname;
    private TextView textYouUid;
    private TextView textYouPostsEmpty;
    private RecyclerView recyclerYouPosts;
    private LinearLayout layoutYouComments;
    private Button buttonYouTabPosts;
    private Button buttonYouTabComments;
    private Button buttonYouTabSaved;
    private Button buttonYouTabLiked;
    private TextView textYouFollowingCount;
    private TextView textYouFollowersCount;
    private TextView textYouEngagementCount;
    private Section selectedSection = Section.POSTS;
    private com.google.android.material.button.MaterialButton buttonProfileMore;
    private java.util.Map<String, String> profileCursors = new java.util.LinkedHashMap<>();
    private boolean loadingProfile, profileFailed;
    private long profileRevision;
    private boolean commentsLoading,commentsFailed;
    private long commentsRevision;
    private String commentCursor;
    private final java.util.Set<UUID> authoredCommentIds=new java.util.LinkedHashSet<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_you, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        textYouAvatar = view.findViewById(R.id.textYouAvatar);
        textYouNickname = view.findViewById(R.id.textYouNickname);
        textYouUid = view.findViewById(R.id.textYouUid);
        textYouPostsEmpty = view.findViewById(R.id.textYouPostsEmpty);
        recyclerYouPosts = view.findViewById(R.id.recyclerYouPosts);
        buttonProfileMore = view.findViewById(R.id.buttonProfileMore);
        buttonProfileMore.setOnClickListener(v -> { if (selectedSection == Section.SAVED || selectedSection == Section.LIKED) loadCollection(!collectionKind().equals(failedCollectionKind)); else if(selectedSection==Section.COMMENTS) loadServerComments(!commentsFailed); else loadServerPosts(!profileFailed); });
        layoutYouComments = view.findViewById(R.id.layoutYouComments);
        recyclerYouPosts.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerYouPosts.setNestedScrollingEnabled(false);

        ImageButton buttonYouSettings = view.findViewById(R.id.buttonYouSettings);
        LinearLayout layoutYouFollowing = view.findViewById(R.id.layoutYouFollowing);
        LinearLayout layoutYouFollowers = view.findViewById(R.id.layoutYouFollowers);
        LinearLayout layoutYouEngagement = view.findViewById(R.id.layoutYouEngagement);
        textYouFollowingCount = view.findViewById(R.id.textYouFollowingCount);
        textYouFollowersCount = view.findViewById(R.id.textYouFollowersCount);
        textYouEngagementCount = view.findViewById(R.id.textYouEngagementCount);
        buttonYouTabPosts = view.findViewById(R.id.buttonYouTabPosts);
        buttonYouTabComments = view.findViewById(R.id.buttonYouTabComments);
        buttonYouTabSaved = view.findViewById(R.id.buttonYouTabSaved);
        buttonYouTabLiked = view.findViewById(R.id.buttonYouTabLiked);

        textYouAvatar.setOnClickListener(v -> host().showAvatarPicker());
        textYouNickname.setOnClickListener(v -> host().showNicknameDialog());
        buttonYouSettings.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));
        layoutYouFollowing.setOnClickListener(v ->
                ServerFeatures.showPeople(requireContext(), AppData.getCurrentUserId(), "following"));
        layoutYouFollowers.setOnClickListener(v ->
                ServerFeatures.showPeople(requireContext(), AppData.getCurrentUserId(), "followers"));
        layoutYouEngagement.setOnClickListener(v ->
                showEngagementStatsDialog(AppData.getCurrentUserId()));
        buttonYouTabPosts.setOnClickListener(v -> selectSection(Section.POSTS));
        buttonYouTabComments.setOnClickListener(v -> selectSection(Section.COMMENTS));
        buttonYouTabSaved.setOnClickListener(v -> selectSection(Section.SAVED));
        buttonYouTabLiked.setOnClickListener(v -> selectSection(Section.LIKED));

        refreshContent();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshContent();
        loadServerPosts(false);
    }

    private void loadServerPosts(boolean more) {
        if (!isAdded() || getView() == null || loadingProfile) return;
        backend.BackendRuntime runtime = backend.BackendRuntime.from(requireContext());
        UUID userId = AppData.getCurrentUserId();
        if (userId == null || !UiPreferences.isLoggedIn(requireContext())) return;
        java.util.Map<String, String> cursors = new java.util.LinkedHashMap<>();
        if (more) cursors.putAll(profileCursors);
        else for (String forum : AppData.getForumKeys()) cursors.put(forum, null);
        if (cursors.isEmpty()) return;
        loadingProfile = true;
        long request = ++profileRevision;
        String origin = runtime.config().baseUrl();
        buttonProfileMore.setEnabled(false);
        buttonProfileMore.setVisibility(View.VISIBLE);
        buttonProfileMore.setText(R.string.feed_sync_loading);
        runtime.forum().fetchProfilePage(userId, cursors, new backend.BackendModerationGateway.Callback<>() {
            @Override public void onSuccess(backend.BackendForumGateway.ProfilePage page) {
                if (!isAdded() || getView() == null || request != profileRevision) return;
                loadingProfile = false;
                if (!origin.equals(runtime.config().baseUrl()) || !userId.equals(AppData.getCurrentUserId())) return;
                if (more) for (var post : page.items()) AppData.upsertRemotePost(post, origin);
                else AppData.replaceUserPosts(userId, page.items(), origin);
                profileCursors = page.cursors();
                profileFailed = false;
                refreshContent();
            }
            @Override public void onError(backend.BackendException error) {
                if (!isAdded() || getView() == null || request != profileRevision) return;
                if (UiPreferences.handleExpiredSession(requireActivity(), error)) return;
                loadingProfile = false;
                profileFailed = true;
                buttonProfileMore.setEnabled(true);
                buttonProfileMore.setText(R.string.feed_retry);
                buttonProfileMore.setVisibility(View.VISIBLE);
                android.widget.Toast.makeText(requireContext(), error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override public void onDestroyView() {
        profileRevision++; collectionRevision++; commentsRevision++; commentsLoading=false; loadingCollection = false;
        loadingProfile = false;
        super.onDestroyView();
    }

    @Override
    public void refreshContent() {
        if (!isResumed() || !isAdded() || getView() == null || textYouAvatar == null) {
            return;
        }

        String nickname = UiPreferences.getProfileNickname(requireContext());
        String username = AccountProfileSync.session(requireContext()).username();


        textYouNickname.setText(nickname);
        textYouUid.setText(getString(R.string.you_account_format, username));
        refreshProfileStats();
        AvatarRenderer.display(textYouAvatar, UiPreferences.getAvatarImageUri(requireContext()),
                nickname.isBlank() ? "?" : nickname.substring(0, 1).toUpperCase(), UiPreferences.getAvatarColor(requireContext()));
        refreshSelectedSection();
    }

    private void refreshProfileStats() {
        UUID userId = AppData.getCurrentUserId();
        int followingTotal=AppData.getFollowingCount(userId);
        textYouFollowingCount.setText(ServerFeatures.hasState("users",userId)?String.valueOf(followingTotal):"—");
        int followersTotal=AppData.getFollowerCount(userId);
        textYouFollowersCount.setText(ServerFeatures.hasState("users",userId)?String.valueOf(followersTotal):"—");
        int engagementTotal=AppData.getReceivedEngagementCount(userId);
        textYouEngagementCount.setText(ServerFeatures.hasState("users",userId)?String.valueOf(engagementTotal):"—");
    }

    private void selectSection(Section section) {
        selectedSection = section;
        if (section == Section.SAVED || section == Section.LIKED) loadCollection(false);
        if(section==Section.COMMENTS) loadServerComments(false);
        refreshSelectedSection();
    }

    private boolean loadingCollection;
    private String failedCollectionKind;
    private long collectionRevision;
    private void loadCollection(boolean more) {
        if (loadingCollection || !isAdded()) return;
        loadingCollection = true; long revision = ++collectionRevision;
        String kind = collectionKind();
        buttonProfileMore.setText(R.string.feed_sync_loading); buttonProfileMore.setEnabled(false);
        ServerFeatures.loadCollection(kind, more, new backend.BackendModerationGateway.Callback<>() {
            public void onSuccess(Void ignored) { if (!isAdded() || getView() == null || revision != collectionRevision) return; loadingCollection = false;failedCollectionKind=null; refreshSelectedSection(); if(collectionKind()!=null&&!kind.equals(collectionKind()))loadCollection(false); }
            public void onError(backend.BackendException error) { if (!isAdded() || getView() == null || revision != collectionRevision) return; if (UiPreferences.handleExpiredSession(requireActivity(), error)) return; loadingCollection = false;failedCollectionKind=kind; buttonProfileMore.setText(R.string.feed_retry); buttonProfileMore.setEnabled(true); buttonProfileMore.setVisibility(View.VISIBLE); android.widget.Toast.makeText(requireContext(), error.getMessage(), android.widget.Toast.LENGTH_LONG).show(); if(collectionKind()!=null&&!kind.equals(collectionKind()))loadCollection(false); }
        });
    }

    private String collectionKind() {
        return selectedSection==Section.SAVED?"BOOKMARKED":selectedSection==Section.LIKED?"LIKED":null;
    }

    private void refreshSelectedSection() {
        styleTabs();
        if (!loadingProfile) {
            buttonProfileMore.setText(profileFailed ? R.string.feed_retry : R.string.feed_load_more);
            buttonProfileMore.setEnabled(true);
            buttonProfileMore.setVisibility((selectedSection == Section.POSTS && (profileFailed || !profileCursors.isEmpty())) || ((selectedSection == Section.SAVED || selectedSection == Section.LIKED) && ServerFeatures.collectionMore(selectedSection == Section.SAVED ? "BOOKMARKED" : "LIKED")) ? View.VISIBLE : View.GONE);
            if(collectionKind()!=null&&collectionKind().equals(failedCollectionKind)) {
                buttonProfileMore.setVisibility(View.VISIBLE);buttonProfileMore.setText(R.string.feed_retry);
            }
            if (loadingCollection) { buttonProfileMore.setVisibility(View.VISIBLE);buttonProfileMore.setEnabled(false); buttonProfileMore.setText(R.string.feed_sync_loading); }
        }
        if (selectedSection == Section.COMMENTS) {
            buttonProfileMore.setVisibility(commentsLoading||commentsFailed||commentCursor!=null?View.VISIBLE:View.GONE);
            buttonProfileMore.setEnabled(!commentsLoading);
            buttonProfileMore.setText(commentsLoading?R.string.feed_sync_loading:commentsFailed?R.string.feed_retry:R.string.feed_load_more);
            refreshMyComments();
            return;
        }

        layoutYouComments.setVisibility(View.GONE);
        recyclerYouPosts.setVisibility(View.VISIBLE);
        ArrayList<Post> posts;
        int emptyText;
        if (selectedSection == Section.SAVED) {
            posts = AppData.getBookmarkedPosts();
            emptyText = R.string.you_saved_empty;
        } else if (selectedSection == Section.LIKED) {
            posts = AppData.getLikedPosts();
            emptyText = R.string.you_liked_empty;
        } else {
            posts = AppData.getPostsByUser(AppData.getCurrentUserId());
            emptyText = R.string.you_posts_empty;
        }
        textYouPostsEmpty.setText(emptyText);
        textYouPostsEmpty.setVisibility(posts.isEmpty() && !(collectionKind()!=null?loadingCollection:loadingProfile) ? View.VISIBLE : View.GONE);
        if((selectedSection==Section.POSTS&&profileFailed)||(collectionKind()!=null&&collectionKind().equals(failedCollectionKind)))
            textYouPostsEmpty.setText(R.string.feed_sync_failed);
        recyclerYouPosts.setVisibility(posts.isEmpty() ? View.GONE : View.VISIBLE);
        if(recyclerYouPosts.getAdapter() instanceof PostAdapter adapter) adapter.replacePosts(posts);
        else recyclerYouPosts.setAdapter(buildPostAdapter(posts));
    }

    private PostAdapter buildPostAdapter(ArrayList<Post> posts) {
        PostAdapter adapter = new PostAdapter(posts);
        adapter.setOnClickListener(this::openPost);
        adapter.setOnVoteClickListener((post, direction) -> {
            AppData.togglePostVote(post, direction);
            refreshSelectedSection();
        });
        adapter.setOnBookmarkClickListener(post -> {
            AppData.togglePostBookmark(post);
            refreshSelectedSection();
        });
        adapter.setOnUserClickListener(this::openUserProfile);
        return adapter;
    }

    private void loadServerComments(boolean more) {
        if(!isAdded()||commentsLoading) return;
        var runtime=backend.BackendRuntime.from(requireContext()); UUID userId=AppData.getCurrentUserId();
        String origin=runtime.config().baseUrl(); long revision=++commentsRevision; commentsLoading=true;
        runtime.forum().fetchAuthoredComments(userId,more?commentCursor:null,new backend.BackendModerationGateway.Callback<>() {
            public void onSuccess(backend.BackendForumGateway.AuthoredCommentPage page) {
                if(!isAdded()||getView()==null||revision!=commentsRevision) return;
                commentsLoading=false;
                if(!origin.equals(runtime.config().baseUrl())||!userId.equals(AppData.getCurrentUserId())) return;
                if(!more) authoredCommentIds.clear();
                for(var item:page.items()) { AppData.upsertRemotePost(item.post(),origin); AppData.upsertRemoteComment(item.post().id(),item.comment(),origin); authoredCommentIds.add(item.comment().id()); }
                commentCursor=page.hasMore()?page.nextCursor():null; commentsFailed=false; refreshSelectedSection();
            }
            public void onError(backend.BackendException error) {
                if(!isAdded()||getView()==null||revision!=commentsRevision) return;
                if (UiPreferences.handleExpiredSession(requireActivity(), error)) return;
                commentsLoading=false; commentsFailed=true; refreshSelectedSection();
                android.widget.Toast.makeText(requireContext(),error.getMessage(),android.widget.Toast.LENGTH_LONG).show();
            }
        });
    }

    private void refreshMyComments() {
        ArrayList<Message> comments = AppData.getMessagesByUser(AppData.getCurrentUserId());
        comments.removeIf(m->!authoredCommentIds.contains(m.id()));
        layoutYouComments.removeAllViews();
        recyclerYouPosts.setVisibility(View.GONE);
        layoutYouComments.setVisibility(comments.isEmpty() ? View.GONE : View.VISIBLE);
        textYouPostsEmpty.setText(R.string.you_comments_empty);
        textYouPostsEmpty.setVisibility(comments.isEmpty() && !commentsLoading ? View.VISIBLE : View.GONE);
        if(commentsFailed) textYouPostsEmpty.setText(R.string.feed_sync_failed);
        for (Message message : comments) {
            layoutYouComments.addView(makeCommentRow(message));
        }
    }

    private View makeCommentRow(Message message) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);

        Post post = AppData.getPostForMessage(message);
        TextView meta = new TextView(requireContext());
        meta.setText(post == null
                ? AppData.formatTimestamp(message.timestamp())
                : AppData.getPostCommunityLabel(requireContext(), post) + " · "
                + AppData.getPostDisplayTitle(requireContext(), post) + " · " + AppData.formatTimestamp(message.timestamp()));
        meta.setTextSize(13);
        meta.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_secondary));
        meta.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(meta);

        TextView body = new TextView(requireContext());
        body.setText(AppData.getMessageDisplayContent(requireContext(), message));
        body.setTextSize(16);
        body.setTextColor(ContextCompat.getColor(requireContext(), R.color.ink_primary));
        body.setLineSpacing(dp(3), 1.0f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        bodyParams.setMargins(0, dp(8), 0, 0);
        row.addView(body, bodyParams);

        row.setOnClickListener(v -> {
            if (post != null) {
                openPost(post);
            }
        });
        return row;
    }

    private void styleTabs() {
        styleTab(buttonYouTabPosts, selectedSection == Section.POSTS);
        styleTab(buttonYouTabComments, selectedSection == Section.COMMENTS);
        styleTab(buttonYouTabSaved, selectedSection == Section.SAVED);
        styleTab(buttonYouTabLiked, selectedSection == Section.LIKED);
    }

    private void styleTab(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        button.setTextColor(ContextCompat.getColor(requireContext(),
                selected ? R.color.ink_primary : R.color.ink_secondary));
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(999));
        background.setColor(ContextCompat.getColor(requireContext(),
                selected ? R.color.surface : R.color.tab_bar_fill));
        background.setStroke(dp(1), ContextCompat.getColor(requireContext(), R.color.surface_border));
        button.setBackground(background);
    }

    private void openPost(Post post) {
        Intent intent = new Intent(requireContext(), PostViewerActivity.class);
        intent.putExtra(PostViewerActivity.EXTRA_POST_ID, post.id.toString());
        startActivity(intent);
    }

    private void openUserProfile(UUID userId) {
        if (userId == null) {
            return;
        }
        Intent intent = new Intent(requireContext(), UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID, userId.toString());
        startActivity(intent);
    }

    private void showUserList(int titleResId, ArrayList<User> users) {
        ProfileInsightDialog.showUsers(requireContext(), titleResId, users, this::openUserProfile);
    }

    private void showEngagementStatsDialog(UUID userId) {
        ArrayList<ProfileInsightDialog.StatItem> stats = new ArrayList<>();
        stats.add(new ProfileInsightDialog.StatItem(R.drawable.ic_channel_24,
                R.string.engagement_posts_label, AppData.getPostsByUser(userId).size()));
        stats.add(new ProfileInsightDialog.StatItem(R.drawable.ic_vote_up_filled_24,
                R.string.engagement_likes_label, AppData.getReceivedLikeCount(userId)));
        stats.add(new ProfileInsightDialog.StatItem(R.drawable.ic_bookmark_filled_24,
                R.string.engagement_saves_label, AppData.getReceivedBookmarkCount(userId)));
        ProfileInsightDialog.showStats(requireContext(), R.string.you_engagement, stats);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private MainActivity host() {
        return (MainActivity) requireActivity();
    }

    private void showDefaultAvatar(String nickname) {
        textYouAvatar.setText(getAvatarLetter(nickname));
        textYouAvatar.setBackground(makeAvatarBackground(UiPreferences.getAvatarIndex(requireContext())));
    }

    private GradientDrawable makeAvatarBackground(int avatarIndex) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(UiPreferences.getGoogleColor(avatarIndex));
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
