package com.example.myapplication;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.UUID;

import dao.model.Message;
import dao.model.Post;
import dao.model.User;

public class UserProfileActivity extends AppCompatActivity {
    public static final String EXTRA_USER_ID = "user_id";

    private enum Section {
        POSTS,
        COMMENTS,
        SAVED,
        LIKED
    }

    private UUID userId;
    private String publicUsername;
    private TextView textProfileAvatar;
    private TextView textProfileName;
    private TextView textProfileMeta;
    private TextView textProfileFollowingCount;
    private TextView textProfileFollowersCount;
    private TextView textProfileEngagementCount;
    private TextView textProfileContentEmpty;
    private RecyclerView recyclerProfilePosts;
    private LinearLayout layoutProfileComments;
    private Button buttonFollow;
    private Button buttonProfileTabPosts;
    private Button buttonProfileTabComments;
    private Button buttonProfileTabSaved;
    private Button buttonProfileTabLiked;
    private Section selectedSection = Section.POSTS;
    private com.google.android.material.button.MaterialButton profileMore;
    private java.util.Map<String,String> postCursors=new java.util.LinkedHashMap<>();
    private String commentCursor;
    private boolean loadingPosts,loadingComments,postsFailed,commentsFailed;
    private String loadingCollectionKind, failedCollectionKind;
    private final java.util.Set<UUID> authoredCommentIds=new java.util.LinkedHashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiPreferences.applyAppearance(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_user_profile);
        View root = findViewById(R.id.userProfileRoot);
        int paddingLeft=root.getPaddingLeft(),paddingTop=root.getPaddingTop();
        int paddingRight=root.getPaddingRight(),paddingBottom=root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    paddingLeft,
                    paddingTop + systemBars.top,
                    paddingRight,
                    paddingBottom + systemBars.bottom
            );
            return insets;
        });

        String id = getIntent().getStringExtra(EXTRA_USER_ID);
        userId = id == null ? null : UUID.fromString(id);
        if (userId != null) AppData.ensureRemoteUser(userId, null);
        textProfileAvatar = findViewById(R.id.textProfileAvatar);
        textProfileName = findViewById(R.id.textProfileName);
        textProfileMeta = findViewById(R.id.textProfileMeta);
        textProfileContentEmpty = findViewById(R.id.textProfileContentEmpty);
        recyclerProfilePosts = findViewById(R.id.recyclerProfilePosts);
        recyclerProfilePosts.setLayoutManager(new LinearLayoutManager(this));
        recyclerProfilePosts.setNestedScrollingEnabled(false);
        layoutProfileComments = findViewById(R.id.layoutProfileComments);
        buttonFollow = findViewById(R.id.buttonProfileFollow);
        textProfileFollowingCount = findViewById(R.id.textProfileFollowingCount);
        textProfileFollowersCount = findViewById(R.id.textProfileFollowersCount);
        textProfileEngagementCount = findViewById(R.id.textProfileEngagementCount);
        buttonProfileTabPosts = findViewById(R.id.buttonProfileTabPosts);
        buttonProfileTabComments = findViewById(R.id.buttonProfileTabComments);
        buttonProfileTabSaved = findViewById(R.id.buttonProfileTabSaved);
        buttonProfileTabLiked = findViewById(R.id.buttonProfileTabLiked);

        findViewById(R.id.buttonProfileBack).setOnClickListener(v -> finish());
        findViewById(R.id.layoutProfileFollowing).setOnClickListener(v ->
                ServerFeatures.showPeople(this, userId, "following"));
        findViewById(R.id.layoutProfileFollowers).setOnClickListener(v ->
                ServerFeatures.showPeople(this, userId, "followers"));
        findViewById(R.id.layoutProfileEngagement).setOnClickListener(v ->
                showEngagementStatsDialog(userId));
        buttonProfileTabPosts.setOnClickListener(v -> selectSection(Section.POSTS));
        buttonProfileTabComments.setOnClickListener(v -> selectSection(Section.COMMENTS));
        buttonProfileTabSaved.setOnClickListener(v -> selectSection(Section.SAVED));
        buttonProfileTabLiked.setOnClickListener(v -> selectSection(Section.LIKED));
        buttonFollow.setOnClickListener(v -> {
            AppData.toggleFollow(userId);
            refresh();
        });
        profileMore=findViewById(R.id.buttonPublicProfileMore);
        profileMore.setOnClickListener(v->{if(selectedSection==Section.COMMENTS) loadComments(!commentsFailed);
            else if(collectionKind()!=null) loadCollection(!collectionKind().equals(failedCollectionKind));
            else loadPosts(!postsFailed);});
        ServerFeatures.init(this);
        if(userId!=null) ServerFeatures.call("GET","/api/users/"+userId,null,new backend.BackendModerationGateway.Callback<>() {
            public void onSuccess(org.json.JSONObject profile){if(isFinishing()||isDestroyed())return;publicUsername=profile.optString("username",AppData.getUsername(userId));
                AppData.ensureRemoteUser(userId, profile.optString("displayName",publicUsername));
                AppData.setRemoteAvatar(userId, profile.isNull("avatarUrl")?null:profile.optString("avatarUrl",null), profile.optInt("avatarColor"),backend.BackendRuntime.from(UserProfileActivity.this).config().baseUrl()); refresh();}
            public void onError(backend.BackendException error){/* Content remains usable; metadata retries on the next visit. */}
        });
        refresh(); loadPosts(false);
    }

    @Override protected void onStart() { super.onStart(); ServerFeatures.init(this); ServerFeatures.observe(this, this::refresh); }
    @Override protected void onStop() { ServerFeatures.remove(this); super.onStop(); }

    private void refresh() {
        User user = AppData.getUser(userId);
        if (user == null) {
            finish();
            return;
        }
        textProfileName.setText(AppData.getDisplayName(this, userId));
        textProfileAvatar.setText(AppData.getAvatarLetter(this, userId));
        GradientDrawable avatar = (GradientDrawable) ContextCompat.getDrawable(this, R.drawable.bg_avatar_circle).mutate();
        avatar.setColor(AppData.getAvatarColor(this, userId));
        textProfileAvatar.setBackground(avatar);
        AvatarRenderer.display(textProfileAvatar, AppData.getAvatarUrl(this, userId), AppData.getAvatarLetter(this,userId), AppData.getAvatarColor(this,userId));
        ArrayList<Post> posts = AppData.getPostsByUser(userId);
        textProfileMeta.setText(getString(R.string.profile_meta_format, publicUsername==null?AppData.getUsername(userId):publicUsername));
        boolean following = AppData.isFollowing(userId);
        boolean self = userId.equals(AppData.getCurrentUserId());
        int followingTotal=AppData.getFollowingCount(userId);
        textProfileFollowingCount.setText(ServerFeatures.hasState("users",userId)?String.valueOf(followingTotal):"—");
        int followersTotal=AppData.getFollowerCount(userId);
        textProfileFollowersCount.setText(ServerFeatures.hasState("users",userId)?String.valueOf(followersTotal):"—");
        int engagementTotal=AppData.getReceivedEngagementCount(userId);
        textProfileEngagementCount.setText(ServerFeatures.hasState("users",userId)?String.valueOf(engagementTotal):"—");
        buttonProfileTabSaved.setVisibility(self?View.VISIBLE:View.GONE);
        buttonProfileTabLiked.setVisibility(self?View.VISIBLE:View.GONE);
        buttonFollow.setVisibility(self ? View.GONE : View.VISIBLE);
        buttonFollow.setText(following ? R.string.action_following : R.string.action_follow);
        buttonFollow.setAlpha(following ? 0.55f : 1.0f);
        refreshSelectedSection();
    }

    private void selectSection(Section section) {
        selectedSection = section;
        if(section==Section.COMMENTS) loadComments(false);
        if(collectionKind()!=null) loadCollection(false);
        refreshSelectedSection();
    }

    private void refreshSelectedSection() {
        styleTabs();
        if(profileMore!=null) {
            boolean c=selectedSection==Section.COMMENTS,p=selectedSection==Section.POSTS;
            String kind=collectionKind();
            boolean busy=c?loadingComments:loadingPosts,failed=c?commentsFailed:postsFailed;
            profileMore.setVisibility((c&&(busy||failed||commentCursor!=null))||(p&&(busy||failed||!postCursors.isEmpty()))?View.VISIBLE:View.GONE);
            if(kind!=null) {
                busy=loadingCollectionKind!=null;failed=kind.equals(failedCollectionKind);
                profileMore.setVisibility(busy||failed||ServerFeatures.collectionMore(kind)?View.VISIBLE:View.GONE);
            }
            profileMore.setEnabled(!busy);profileMore.setText(busy?R.string.feed_sync_loading:failed?R.string.feed_retry:R.string.feed_load_more);
        }
        if (selectedSection == Section.COMMENTS) {
            refreshComments();
            return;
        }

        layoutProfileComments.setVisibility(View.GONE);
        ArrayList<Post> posts;
        int emptyText;
        if (selectedSection == Section.SAVED) {
            posts = getSavedProfilePosts();
            emptyText = R.string.you_saved_empty;
        } else if (selectedSection == Section.LIKED) {
            posts = getLikedProfilePosts();
            emptyText = R.string.you_liked_empty;
        } else {
            posts = AppData.getPostsByUser(userId);
            emptyText = R.string.you_posts_empty;
        }
        textProfileContentEmpty.setText(emptyText);
        textProfileContentEmpty.setVisibility(posts.isEmpty() && !(collectionKind()!=null?loadingCollectionKind!=null:loadingPosts) ? View.VISIBLE : View.GONE);
        if((selectedSection==Section.POSTS&&postsFailed)||(collectionKind()!=null&&collectionKind().equals(failedCollectionKind)))
            textProfileContentEmpty.setText(R.string.feed_sync_failed);
        recyclerProfilePosts.setVisibility(posts.isEmpty() ? View.GONE : View.VISIBLE);
        if(recyclerProfilePosts.getAdapter() instanceof PostAdapter adapter) adapter.replacePosts(posts);
        else recyclerProfilePosts.setAdapter(buildPostAdapter(posts));
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
        adapter.setOnUserClickListener(clickedUserId -> {
            if (!userId.equals(clickedUserId)) {
                Intent intent = new Intent(this, UserProfileActivity.class);
                intent.putExtra(EXTRA_USER_ID, clickedUserId.toString());
                startActivity(intent);
            }
        });
        return adapter;
    }

    private void loadPosts(boolean more) {
        if(userId==null||loadingPosts) return;
        var runtime=backend.BackendRuntime.from(this);String origin=runtime.config().baseUrl();
        var cursors=new java.util.LinkedHashMap<String,String>();
        if(more)cursors.putAll(postCursors);else cursors.put(backend.BackendForumGateway.PROFILE_CURSOR_KEY,null);
        if(cursors.isEmpty())return;loadingPosts=true;refreshSelectedSection();
        runtime.forum().fetchProfilePage(userId,cursors,new backend.BackendModerationGateway.Callback<>() {
            public void onSuccess(backend.BackendForumGateway.ProfilePage page) {
                if(isFinishing()||isDestroyed())return;loadingPosts=false;
                if(!origin.equals(runtime.config().baseUrl()))return;
                if(!more)AppData.replaceUserPosts(userId,page.items(),origin);else for(var p:page.items())AppData.upsertRemotePost(p,origin);
                postCursors=page.cursors();postsFailed=false;refresh();
            }
            public void onError(backend.BackendException e){if(isFinishing()||isDestroyed()||UiPreferences.handleExpiredSession(UserProfileActivity.this,e))return;loadingPosts=false;postsFailed=true;refreshSelectedSection();}
        });
    }
    private void loadComments(boolean more) {
        if(userId==null||loadingComments)return;
        var runtime=backend.BackendRuntime.from(this);String origin=runtime.config().baseUrl();UUID actor=AppData.getCurrentUserId();
        loadingComments=true;refreshSelectedSection();
        runtime.forum().fetchAuthoredComments(userId,actor,more?commentCursor:null,new backend.BackendModerationGateway.Callback<>() {
            public void onSuccess(backend.BackendForumGateway.AuthoredCommentPage page) {
                if(isFinishing()||isDestroyed())return;loadingComments=false;
                if(!origin.equals(runtime.config().baseUrl())||!actor.equals(AppData.getCurrentUserId()))return;
                if(!more)authoredCommentIds.clear();
                for(var item:page.items()){AppData.upsertRemotePost(item.post(),origin);AppData.upsertRemoteComment(item.post().id(),item.comment(),origin);authoredCommentIds.add(item.comment().id());}
                commentCursor=page.hasMore()?page.nextCursor():null;commentsFailed=false;refreshSelectedSection();
            }
            public void onError(backend.BackendException e){if(isFinishing()||isDestroyed()||UiPreferences.handleExpiredSession(UserProfileActivity.this,e))return;loadingComments=false;commentsFailed=true;refreshSelectedSection();}
        });
    }

    private void refreshComments() {
        ArrayList<Message> comments = AppData.getMessagesByUser(userId);
        comments.removeIf(m->!authoredCommentIds.contains(m.id()));
        layoutProfileComments.removeAllViews();
        recyclerProfilePosts.setVisibility(View.GONE);
        layoutProfileComments.setVisibility(comments.isEmpty() ? View.GONE : View.VISIBLE);
        textProfileContentEmpty.setText(R.string.you_comments_empty);
        textProfileContentEmpty.setVisibility(comments.isEmpty() && !loadingComments ? View.VISIBLE : View.GONE);
        if(commentsFailed) textProfileContentEmpty.setText(R.string.feed_sync_failed);
        for (Message message : comments) {
            layoutProfileComments.addView(makeCommentRow(message));
        }
    }

    private View makeCommentRow(Message message) {
        LinearLayout row = new LinearLayout(this);
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
        TextView meta = new TextView(this);
        meta.setText(post == null
                ? AppData.formatTimestamp(message.timestamp())
                : AppData.getPostCommunityLabel(this, post) + " · "
                + AppData.getPostDisplayTitle(this, post) + " · " + AppData.formatTimestamp(message.timestamp()));
        meta.setTextSize(13);
        meta.setTextColor(ContextCompat.getColor(this, R.color.ink_secondary));
        meta.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(meta);

        TextView body = new TextView(this);
        body.setText(AppData.getMessageDisplayContent(this, message));
        body.setTextSize(16);
        body.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
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

    private ArrayList<Post> getSavedProfilePosts() { return AppData.getBookmarkedPosts(); }
    private ArrayList<Post> getLikedProfilePosts() { return AppData.getLikedPosts(); }

    private String collectionKind() {
        return selectedSection==Section.SAVED ? "BOOKMARKED" : selectedSection==Section.LIKED ? "LIKED" : null;
    }

    private void loadCollection(boolean more) {
        String kind=collectionKind();
        if(kind==null || loadingCollectionKind!=null || !userId.equals(AppData.getCurrentUserId())) return;
        loadingCollectionKind=kind; refreshSelectedSection();
        ServerFeatures.loadCollection(kind,more,new backend.BackendModerationGateway.Callback<>() {
            public void onSuccess(Void ignored) {
                if(isFinishing()||isDestroyed())return;
                loadingCollectionKind=null;failedCollectionKind=null;refreshSelectedSection();
                if(collectionKind()!=null&&!kind.equals(collectionKind()))loadCollection(false);
            }
            public void onError(backend.BackendException error) {
                if(isFinishing()||isDestroyed()||UiPreferences.handleExpiredSession(UserProfileActivity.this,error))return;
                loadingCollectionKind=null;failedCollectionKind=kind;refreshSelectedSection();
                if(collectionKind()!=null&&!kind.equals(collectionKind()))loadCollection(false);
            }
        });
    }

    private void styleTabs() {
        styleTab(buttonProfileTabPosts, selectedSection == Section.POSTS);
        styleTab(buttonProfileTabComments, selectedSection == Section.COMMENTS);
        styleTab(buttonProfileTabSaved, selectedSection == Section.SAVED);
        styleTab(buttonProfileTabLiked, selectedSection == Section.LIKED);
    }

    private void styleTab(Button button, boolean selected) {
        button.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        button.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.ink_primary : R.color.ink_secondary));
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(dp(999));
        background.setColor(ContextCompat.getColor(this,
                selected ? R.color.surface : R.color.tab_bar_fill));
        background.setStroke(dp(1), ContextCompat.getColor(this, R.color.surface_border));
        button.setBackground(background);
    }

    private void showUserList(int titleResId, ArrayList<User> users) {
        ProfileInsightDialog.showUsers(this, titleResId, users, clickedUserId -> {
            Intent intent = new Intent(this, UserProfileActivity.class);
            intent.putExtra(EXTRA_USER_ID, clickedUserId.toString());
            startActivity(intent);
        });
    }

    private void showEngagementStatsDialog(UUID targetUserId) {
        ArrayList<ProfileInsightDialog.StatItem> stats = new ArrayList<>();
        stats.add(new ProfileInsightDialog.StatItem(R.drawable.ic_channel_24,
                R.string.engagement_posts_label, AppData.getPostsByUser(targetUserId).size()));
        stats.add(new ProfileInsightDialog.StatItem(R.drawable.ic_vote_up_filled_24,
                R.string.engagement_likes_label, AppData.getReceivedLikeCount(targetUserId)));
        stats.add(new ProfileInsightDialog.StatItem(R.drawable.ic_bookmark_filled_24,
                R.string.engagement_saves_label, AppData.getReceivedBookmarkCount(targetUserId)));
        ProfileInsightDialog.showStats(this, R.string.you_engagement, stats);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openPost(Post post) {
        Intent intent = new Intent(this, PostViewerActivity.class);
        intent.putExtra(PostViewerActivity.EXTRA_POST_ID, post.id.toString());
        startActivity(intent);
    }
}
