package com.example.myapplication;

import android.content.Context;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import backend.BackendReportTarget;
import backend.BackendForumGateway;
import dao.PostDAO;
import dao.UserDAO;
import dao.model.Message;
import dao.model.Post;
import dao.model.Report;
import dao.model.TimestampFormatter;
import dao.model.TimestampFormatterTimeSinceEnglish;
import dao.model.User;
import moderation.ModerationTools;

public final class AppData {
    public static final String STRATEGY_OLDEST = "OLDEST";
    public static final String STRATEGY_MOST = "MOST";

    public static final String FORUM_ANU = "anu";
    public static final String FORUM_UNSW = "unsw";
    public static final String FORUM_USYD = "usyd";
    public static final String FORUM_UM = "um";

    private static final List<String> FORUM_ORDER = new ArrayList<>(Arrays.asList(
            FORUM_ANU,
            FORUM_UNSW,
            FORUM_USYD,
            FORUM_UM
    ));
    private static final Map<String, String> CUSTOM_FORUM_LABELS = new HashMap<>();

    public static void addCustomForum(String label) {
        String key = "custom_" + label.toLowerCase(Locale.getDefault()).replaceAll("[^a-z0-9]", "_");
        if (!FORUM_ORDER.contains(key)) {
            FORUM_ORDER.add(key);
            CUSTOM_FORUM_LABELS.put(key, label);
        }
    }

    public static boolean isCustomForum(String forumKey) {
        return CUSTOM_FORUM_LABELS.containsKey(forumKey);
    }

    private static final TimestampFormatter TIMESTAMP_FORMATTER = new TimestampFormatterTimeSinceEnglish();

    private static final Map<UUID, PostMeta> POST_META = new HashMap<>();
    private static final Map<UUID, String> POST_TITLE_OVERRIDES = new HashMap<>();
    private static final Map<UUID, String> POST_BODY_OVERRIDES = new HashMap<>();
    private static final Map<UUID, String> POST_IMAGE_URIS = new HashMap<>();
    private static final Map<UUID, MessageMeta> MESSAGE_META = new HashMap<>();
    private static final Map<UUID, String> MESSAGE_IMAGE_URIS = new HashMap<>();
    private static final Map<UUID, String> MESSAGE_BODY_OVERRIDES = new HashMap<>();
    private static final Map<UUID, Integer> POST_TOP_RANKS = new HashMap<>();
    private static final Map<UUID, String> POST_CATEGORIES = new HashMap<>();
    private static final Map<UUID, Integer> BASE_BOOKMARKS = new HashMap<>();
    private static final Map<VoteTarget, Integer> BASE_VOTES = new HashMap<>();
    private static final Map<UserVoteKey, Integer> USER_VOTES = new HashMap<>();
    private static final Set<UUID> FOLLOWED_USERS = new HashSet<>();
    private static final Set<UUID> BOOKMARKED_POSTS = new HashSet<>();
    private static final Set<UUID> DELETED_POSTS = new HashSet<>();
    private static final Set<UUID> REMOTE_POSTS = new HashSet<>();
    private static final Set<UUID> REMOTE_COMMENTS = new HashSet<>();
    private static final Set<UUID> REMOVED_REMOTE_COMMENTS = new HashSet<>();
    private static final Map<UUID, String> REMOTE_AVATAR_URLS = new HashMap<>();
    private static final Map<UUID, Integer> REMOTE_AVATAR_COLORS = new HashMap<>();
    private static final Map<UUID, String> REMOTE_USER_NAMES = new HashMap<>();
    private static final Set<String> REMOTE_SYNCED_FORUMS = new HashSet<>();
    private static final ArrayList<AppNotification> SEEDED_NOTIFICATIONS = new ArrayList<>();

    private static boolean populated;
    private static boolean adminMode;
    private static boolean onlineMode;
    private static String backendOrigin;
    private static String populatedLanguage;
    private static String selectedForumKey = FORUM_ANU;

    private static User memberViewer;
    private static User adminViewer;
    private static User studyBuddy;
    private static User lateCoder;
    private static User uxPilot;
    private static User treeSage;
    private static User compSurvivor;
    private static User anuSleepDeprived;
    private static User labPartner;
    private static User quacEnjoyer;
    private static User deadlineVictim;

    private static Message hiddenExampleMessage;
    private static Message queueExampleMessage;

    private AppData() {
    }

    public static void ensurePopulated() {
        String currentLanguage = currentLanguageTag();
        if (populated && currentLanguage.equals(populatedLanguage)) {
            return;
        }

        UserDAO.getInstance().clear();
        PostDAO.getInstance().clear();
        ModerationTools.clearAll();
        seedSequence = 0;
        POST_META.clear();
        POST_TITLE_OVERRIDES.clear();
        POST_BODY_OVERRIDES.clear();
        POST_IMAGE_URIS.clear();
        MESSAGE_META.clear();
        MESSAGE_IMAGE_URIS.clear();
        MESSAGE_BODY_OVERRIDES.clear();
        POST_TOP_RANKS.clear();
        POST_CATEGORIES.clear();
        BASE_BOOKMARKS.clear();
        BASE_VOTES.clear();
        USER_VOTES.clear();
        FOLLOWED_USERS.clear();
        BOOKMARKED_POSTS.clear();
        DELETED_POSTS.clear();
        REMOTE_POSTS.clear();
        REMOTE_COMMENTS.clear();
        ThreadRefreshCache.clear();
        REMOVED_REMOTE_COMMENTS.clear();
        REMOTE_USER_NAMES.clear(); REMOTE_AVATAR_URLS.clear(); REMOTE_AVATAR_COLORS.clear();
        REMOTE_SYNCED_FORUMS.clear();
        SEEDED_NOTIFICATIONS.clear();

        seedUsers();
        seedForumThreads();
        seedSocialState();
        seedModerationState();

        adminMode = false;
        selectedForumKey = FORUM_ANU;
        populated = true;
        populatedLanguage = currentLanguage;
    }

    public static boolean isAdminMode() {
        return adminMode;
    }

    public static void setAdminMode(boolean isAdminMode) {
        ensurePopulated();
        adminMode = isAdminMode;
    }

    public static void setServerAdministrator(UUID id, String username) {
        ensurePopulated();
        adminViewer = new User(id, User.Role.Admin, username, "");
        if (UserDAO.getInstance().getByUUID(id) == null) UserDAO.getInstance().add(adminViewer);
        adminMode = true;
    }

    public static void setServerMember(UUID id, String username) {
        ensurePopulated();
        memberViewer = new User(id, User.Role.Member, username, "");
        if (UserDAO.getInstance().getByUUID(id) == null) UserDAO.getInstance().add(memberViewer);
        adminMode = false;
    }

    public static void setOnlineMode(boolean enabled) { onlineMode = enabled; }

    /** Cached server content belongs to one API origin. */
    public static void bindBackendOrigin(String origin) {
        if (backendOrigin != null && !backendOrigin.equals(origin)) {
            DELETED_POSTS.addAll(REMOTE_POSTS);
            REMOVED_REMOTE_COMMENTS.addAll(REMOTE_COMMENTS);
            REMOTE_POSTS.clear();
            REMOTE_SYNCED_FORUMS.clear();
            REMOTE_USER_NAMES.clear(); REMOTE_AVATAR_URLS.clear(); REMOTE_AVATAR_COLORS.clear();
        }
        backendOrigin = origin;
    }

    public static void setPostCategory(Post post, String category) {
        if (post != null && category != null) POST_CATEGORIES.put(post.id, category);
    }

    public static List<String> getForumKeys() {
        return FORUM_ORDER;
    }

    public static void setSelectedForum(String forumKey) {
        ensurePopulated();
        if (FORUM_ORDER.contains(forumKey) || CUSTOM_FORUM_LABELS.containsKey(forumKey)) {
            selectedForumKey = forumKey;
        }
    }

    public static String getSelectedForumKey() {
        ensurePopulated();
        return selectedForumKey;
    }

    public static boolean isSelectedForum(String forumKey) {
        ensurePopulated();
        return selectedForumKey.equals(forumKey);
    }

    public static String getSelectedForumLabel(Context context) {
        return getForumLabel(context, selectedForumKey);
    }

    public static int getSelectedForumAvatarResId() {
        return getForumAvatarResId(selectedForumKey);
    }

    public static int getSelectedForumHeaderColorResId() {
        return getForumHeaderColorResId(selectedForumKey);
    }

    public static String getForumLabel(Context context, String forumKey) {
        if (CUSTOM_FORUM_LABELS.containsKey(forumKey)) {
            return CUSTOM_FORUM_LABELS.get(forumKey);
        }
        if (FORUM_UNSW.equals(forumKey)) {
            return context.getString(R.string.forum_unsw_label);
        }
        if (FORUM_USYD.equals(forumKey)) {
            return context.getString(R.string.forum_usyd_label);
        }
        if (FORUM_UM.equals(forumKey)) {
            return context.getString(R.string.forum_um_label);
        }
        return context.getString(R.string.forum_anu_label);
    }

    public static String getCurrentModeLabel(Context context) {
        return context.getString(adminMode ? R.string.mode_admin : R.string.mode_member);
    }

    public static String getCurrentUserLabel(Context context) {
        User user = getCurrentUser();
        if (user == null) {
            return context.getString(R.string.viewer_unknown);
        }
        return context.getString(
                adminMode ? R.string.viewer_user_admin : R.string.viewer_user_member,
                user.username()
        );
    }

    public static String getMainSubtitle(Context context) {
        ensurePopulated();
        if (!FORUM_ANU.equals(selectedForumKey)) {
            return context.getString(R.string.forum_empty_subtitle, getSelectedForumLabel(context));
        }
        return context.getString(adminMode
                ? R.string.forum_anu_subtitle_admin
                : R.string.forum_anu_subtitle_member);
    }

    public static String getFeedEmptyTitle(Context context) {
        if (FORUM_ANU.equals(selectedForumKey)) {
            return context.getString(R.string.feed_empty_title_generic);
        }
        return context.getString(R.string.feed_empty_title_forum, getSelectedForumLabel(context));
    }

    public static String getFeedEmptyBody(Context context) {
        if (FORUM_ANU.equals(selectedForumKey)) {
            return context.getString(R.string.feed_empty_body_generic);
        }
        return context.getString(R.string.feed_empty_body_forum, getSelectedForumLabel(context));
    }

    public static ArrayList<Post> getPosts() {
        ensurePopulated();

        ArrayList<Post> posts = new ArrayList<>();
        Iterator<Post> iterator = PostDAO.getInstance().getAll();
        while (iterator.hasNext()) {
            Post post = iterator.next();
            if (isDeletedPost(post)) {
                continue;
            }
            if (!selectedForumKey.equals(getForumKey(post))) {
                continue;
            }
            if ((onlineMode || REMOTE_SYNCED_FORUMS.contains(selectedForumKey)) && !REMOTE_POSTS.contains(post.id)) {
                continue;
            }
            if (!adminMode && isRootHidden(post)) {
                continue;
            }
            posts.add(post);
        }

        posts.sort(Comparator
                .comparingInt(AppData::getTopRank)
                .thenComparing(Comparator.comparingLong(AppData::getLatestActivityTimestamp).reversed()));
        return posts;
    }

    public static ArrayList<Post> searchPosts(Context context, String query) {
        ArrayList<Post> posts = getPosts();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase();
        if (normalizedQuery.isEmpty()) {
            return posts;
        }

        ArrayList<Post> filtered = new ArrayList<>();
        for (Post post : posts) {
            if (matchesPostSearch(context, post, normalizedQuery)) {
                filtered.add(post);
            }
        }
        return filtered;
    }

    public static ArrayList<String> getPostCategories(Context context) {
        ensurePopulated();
        ArrayList<String> categories = new ArrayList<>();
        categories.add(context.getString(R.string.category_all));
        for (String fallback : defaultCategories(context)) {
            if (!categories.contains(fallback)) {
                categories.add(fallback);
            }
        }
        for (Post post : getPosts()) {
            String category = getPostCategory(context, post);
            if (!category.trim().isEmpty() && !categories.contains(category)) {
                categories.add(category);
            }
        }
        return categories;
    }

    public static ArrayList<Post> filterPostsByCategory(Context context, String category) {
        ArrayList<Post> posts = getPosts();
        if (category == null || category.equals(context.getString(R.string.category_all))) {
            return posts;
        }
        ArrayList<Post> filtered = new ArrayList<>();
        for (Post post : posts) {
            if (category.equals(getPostCategory(context, post))) {
                filtered.add(post);
            }
        }
        return filtered;
    }

    public static Post getPostById(String postId) {
        ensurePopulated();
        if (postId == null) {
            return null;
        }
        Post post = PostDAO.getInstance().get(new Post(UUID.fromString(postId)));
        return isDeletedPost(post) ? null : post;
    }

    public static Post getPostForMessage(Message message) {
        if (message == null) {
            return null;
        }
        Post post = PostDAO.getInstance().get(new Post(message.thread()));
        return isDeletedPost(post) ? null : post;
    }

    public static Message getRootMessage(Post post) {
        if (post == null) {
            return null;
        }
        UUID rootId = getRootMessageId(post);
        if (rootId == null) {
            return null;
        }
        return findMessage(post, rootId);
    }

    public static ArrayList<Message> getMessages(Post post) {
        return getMessages(post, Collections.emptySet());
    }

    public static ArrayList<Message> getMessages(Post post, Set<UUID> expandedTopLevelCommentIds) {
        ensurePopulated();
        ArrayList<Message> messages = new ArrayList<>();
        if (post == null) {
            return messages;
        }

        ArrayList<Message> visibleMessages = collectMessages(adminMode
                ? post.messages.getAll()
                : post.getVisibleMessages(false).getAll());
        Set<UUID> visibleIds = new HashSet<>();
        for (Message message : visibleMessages) {
            visibleIds.add(message.id());
        }

        ArrayList<Message> topLevel = new ArrayList<>();
        Map<UUID, ArrayList<Message>> groupedReplies = new HashMap<>();
        UUID rootId = getRootMessageId(post);

        for (Message message : visibleMessages) {
            if (isRootMessage(message)) {
                continue;
            }

            UUID parentId = getParentId(message);
            if (parentId == null || parentId.equals(rootId) || !visibleIds.contains(parentId)) {
                topLevel.add(message);
                continue;
            }
            groupedReplies.computeIfAbsent(parentId, unused -> new ArrayList<>()).add(message);
        }

        sortMessagesByTimestamp(topLevel);
        for (ArrayList<Message> children : groupedReplies.values()) {
            sortMessagesByTimestamp(children);
        }

        for (Message message : topLevel) {
            appendXhsThread(message, groupedReplies, messages, expandedTopLevelCommentIds == null
                    ? Collections.emptySet()
                    : expandedTopLevelCommentIds);
        }
        return messages;
    }

    public static ArrayList<Message> getReportedMessages(String strategy) {
        ensurePopulated();
        ArrayList<Message> messages = new ArrayList<>();
        int totalMessages = Math.max(1, getAllMessages().size());
        Iterator<Message> iterator = ModerationTools.getReportedMessages(strategy, totalMessages);
        while (iterator.hasNext()) {
            messages.add(iterator.next());
        }
        return messages;
    }

    public static String getPostSummary(Context context, Post post) {
        int visibleReplies = getVisibleMessageCount(post);
        int totalReplies = getTotalMessageCount(post);
        int hiddenReplies = totalReplies - visibleReplies;

        if (adminMode && hiddenReplies > 0) {
            return context.getString(R.string.post_summary_admin_hidden, totalReplies, hiddenReplies);
        }
        if (adminMode) {
            return context.getString(R.string.post_summary_admin_visible, totalReplies);
        }
        if (hiddenReplies > 0) {
            return context.getString(R.string.post_summary_member_hidden, visibleReplies, hiddenReplies);
        }
        return context.getString(R.string.post_summary_member_visible, visibleReplies);
    }

    public static String getPostMeta(Context context, Post post) {
        return context.getString(R.string.post_meta_started_by, getDisplayName(context, post.poster));
    }

    public static String getPostFeedMeta(Context context, Post post) {
        return context.getString(
                R.string.post_feed_meta_format,
                getPostCommunityLabel(context, post),
                getDisplayName(context, post.poster),
                getPostTimestampLabel(post)
        );
    }

    public static String getPostFeedByline(Context context, Post post) {
        return context.getString(
                R.string.post_feed_meta_byline_format,
                getDisplayName(context, post.poster),
                getPostTimestampLabel(post)
        );
    }

    public static String getPostCommunityLabel(Context context, Post post) {
        return getForumLabel(context, getForumKey(post));
    }

    public static int getPostCommunityAvatarResId(Post post) {
        return getForumAvatarResId(getForumKey(post));
    }

    public static int getForumAvatarResId(String forumKey) {
        if (FORUM_UNSW.equals(forumKey)) {
            return R.drawable.avatar_unsw;
        }
        if (FORUM_USYD.equals(forumKey)) {
            return R.drawable.avatar_usyd;
        }
        if (FORUM_UM.equals(forumKey)) {
            return R.drawable.avatar_um;
        }
        return R.drawable.avatar_anu;
    }

    public static int getForumHeaderColorResId(String forumKey) {
        if (FORUM_UNSW.equals(forumKey)) {
            return R.color.forum_unsw_header;
        }
        if (FORUM_USYD.equals(forumKey)) {
            return R.color.forum_usyd_header;
        }
        if (FORUM_UM.equals(forumKey)) {
            return R.color.forum_um_header;
        }
        return R.color.forum_anu_header;
    }

    public static int getPostHeaderColorResId(Post post) {
        return getForumHeaderColorResId(getForumKey(post));
    }

    public static String getPostBody(Post post) {
        if (post != null && POST_BODY_OVERRIDES.containsKey(post.id)) {
            return POST_BODY_OVERRIDES.get(post.id);
        }
        Message rootMessage = getRootMessage(post);
        return rootMessage == null ? "" : rootMessage.message();
    }

    public static String getPostTitle(Post post) {
        if (post == null) {
            return "";
        }
        String override = POST_TITLE_OVERRIDES.get(post.id);
        return override == null ? post.topic : override;
    }

    public static String getPostBodyPreview(Post post) {
        return ellipsize(getPostBody(post), 220);
    }

    /** What a reader sees: machine-translated in a Chinese interface, else as written. */
    public static String getPostDisplayTitle(Context context, Post post) {
        if (post == null || !REMOTE_POSTS.contains(post.id)) return getPostTitle(post);
        return ContentTranslations.postTitle(context, post.id, getPostTitle(post), getPostBody(post));
    }

    public static String getPostDisplayBody(Context context, Post post) {
        if (post == null || !REMOTE_POSTS.contains(post.id)) return getPostBody(post);
        return ContentTranslations.postBody(context, post.id, getPostTitle(post), getPostBody(post));
    }

    public static String getPostDisplayBodyPreview(Context context, Post post) {
        return ellipsize(getPostDisplayBody(context, post), 220);
    }

    public static boolean isPostTranslated(Context context, Post post) {
        return post != null && REMOTE_POSTS.contains(post.id)
                && ContentTranslations.isTranslated(context, post.id, getPostTitle(post), getPostBody(post));
    }

    public static String canonicalPostCategory(String category) {
        if(category==null||category.isBlank())return "Study";
        return switch(category.trim()) {
            case "学习" -> "Study"; case "社交" -> "Social"; case "期末" -> "Finals";
            case "生活" -> "Life"; case "就业" -> "Career"; default -> category.trim();
        };
    }

    public static String getPostCategory(Context context, Post post) {
        ensurePopulated();
        String category=canonicalPostCategory(post==null?null:POST_CATEGORIES.get(post.id));
        boolean zh=context==null?contextLanguageIsChinese():context.getResources().getConfiguration().getLocales().get(0).getLanguage().startsWith("zh");
        if(!zh)return category;
        return switch(category) {
            case "Study" -> "学习"; case "Social" -> "社交"; case "Finals" -> "期末";
            case "Life" -> "生活"; case "Career" -> "就业"; default -> category;
        };
    }

    public static String getPostTimestampLabel(Post post) {
        Message rootMessage = getRootMessage(post);
        return rootMessage == null ? "" : formatTimestamp(rootMessage.timestamp());
    }

    public static int getTotalMessageCount(Post post) {
        if (post == null) {
            return 0;
        }
        int count = 0;
        Iterator<Message> iterator = post.messages.getAll();
        while (iterator.hasNext()) {
            Message message = iterator.next();
            if (!isRootMessage(message)) {
                count++;
            }
        }
        return count;
    }

    public static int getVisibleMessageCount(Post post) {
        if (post == null) {
            return 0;
        }
        int count = 0;
        Iterator<Message> iterator = (adminMode ? post.messages : post.getVisibleMessages(false)).getAll();
        while (iterator.hasNext()) {
            Message message = iterator.next();
            if (!isRootMessage(message)) {
                count++;
            }
        }
        return count;
    }

    public static int getCommentDepth(Message message) {
        if (message == null || isRootMessage(message)) {
            return 0;
        }
        int depth = 0;
        UUID rootId = getRootMessageId(getPostForMessage(message));
        UUID parentId = getParentId(message);
        while (parentId != null && !parentId.equals(rootId)) {
            depth++;
            MessageMeta meta = MESSAGE_META.get(parentId);
            parentId = meta == null ? null : meta.parentId;
        }
        return depth;
    }

    public static int getDisplayCommentDepth(Message message) {
        return isTopLevelComment(message) ? 0 : 1;
    }

    public static String getMessageDisplayContent(Context context, Message message) {
        if (message == null) {
            return "";
        }
        String body = MESSAGE_BODY_OVERRIDES.getOrDefault(message.id(), message.message());
        return REMOTE_COMMENTS.contains(message.id())
                ? ContentTranslations.comment(context, message.thread(), message.id(), body)
                : body;
    }

    public static String getMessageAuthorDisplayName(Context context, Message message) {
        if (message == null) {
            return "";
        }
        String author = getDisplayName(context, message.poster());
        if (getCommentDepth(message) <= 1) {
            return author;
        }
        Message parent = getParentMessage(message);
        if (parent == null) {
            return author;
        }
        return author + "  ▸  " + getDisplayName(context, parent.poster());
    }

    public static String getMessageAuthorDisplayName(Message message) {
        return getMessageAuthorDisplayName(null, message);
    }

    public static boolean isTopLevelComment(Message message) {
        if (message == null || isRootMessage(message)) {
            return false;
        }
        UUID rootId = getRootMessageId(getPostForMessage(message));
        UUID parentId = getParentId(message);
        return parentId == null || parentId.equals(rootId);
    }

    public static UUID getTopLevelCommentId(Message message) {
        Message topLevel = getTopLevelComment(message);
        return topLevel == null ? null : topLevel.id();
    }

    public static int getTopLevelReplyCount(Message message) {
        Message topLevel = getTopLevelComment(message);
        if (topLevel == null) {
            return 0;
        }
        Post post = getPostForMessage(topLevel);
        if (post == null) {
            return 0;
        }
        Map<UUID, ArrayList<Message>> groupedReplies = buildVisibleReplyGroups(post);
        ArrayList<Message> replies = new ArrayList<>();
        appendFlatReplies(topLevel, groupedReplies, replies);
        return replies.size();
    }

    public static int getMessageReplyCount(Message parent) {
        ensurePopulated();
        if (parent == null) {
            return 0;
        }

        Post post = getPostForMessage(parent);
        if (post == null) {
            return 0;
        }

        int count = 0;
        ArrayList<Message> visibleMessages = collectMessages(adminMode
                ? post.messages.getAll()
                : post.getVisibleMessages(false).getAll());
        for (Message message : visibleMessages) {
            UUID parentId = getParentId(message);
            if (parent.id().equals(parentId)) {
                count++;
            }
        }
        return count;
    }

    public static ArrayList<AppNotification> getNotifications(Context context) {
        return getNotifications(context, NotificationType.ALL);
    }

    public static ArrayList<AppNotification> getNotifications(Context context, NotificationType filterType) {
        ensurePopulated();
        ArrayList<AppNotification> notifications = new ArrayList<>();
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return notifications;
        }

        for (AppNotification notification : SEEDED_NOTIFICATIONS) {
            if (currentUser.id().equals(notification.recipientId())
                    && (filterType == NotificationType.ALL || filterType == notification.type())) {
                notifications.add(notification);
            }
        }

        Iterator<Post> postIterator = PostDAO.getInstance().getAll();
        while (postIterator.hasNext()) {
            Post post = postIterator.next();
            Message root = getRootMessage(post);
            if (root == null) {
                continue;
            }

            ArrayList<Message> messages = collectMessages(adminMode
                    ? post.messages.getAll()
                    : post.getVisibleMessages(false).getAll());
            for (Message message : messages) {
                if (isRootMessage(message)) {
                    continue;
                }

                Message parent = getParentMessage(message);
                if (parent != null && parent.poster().equals(currentUser.id())) {
                    AppNotification notification = new AppNotification(
                            NotificationType.COMMENT,
                            currentUser.id(),
                            message.poster(),
                            post.id,
                            message.id(),
                            context.getString(R.string.notification_comment_title, getUsername(message.poster())),
                            message.message(),
                            message.timestamp()
                    );
                    if (filterType == NotificationType.ALL || filterType == notification.type()) {
                        notifications.add(notification);
                    }
                }

                if (mentionsUser(message.message(), currentUser)) {
                    AppNotification notification = new AppNotification(
                            NotificationType.MENTION,
                            currentUser.id(),
                            message.poster(),
                            post.id,
                            message.id(),
                            context.getString(R.string.notification_mention_title, getUsername(message.poster())),
                            message.message(),
                            message.timestamp()
                    );
                    if (filterType == NotificationType.ALL || filterType == notification.type()) {
                        notifications.add(notification);
                    }
                }

            }
        }

        notifications.sort(Comparator.comparingLong(AppNotification::timestamp).reversed());
        return notifications;
    }

    public static int getReportCount(Message message) {
        int count = 0;
        Iterator<Report> iterator = ModerationTools.getAllReports();
        while (iterator.hasNext()) {
            Report report = iterator.next();
            if (report.message.equals(message.id())) {
                count++;
            }
        }
        return count;
    }

    public static boolean isHidden(Message message) {
        return message != null && ModerationTools.isHidden(message.id());
    }

    public static boolean hasCurrentUserReported(Message message) {
        User currentUser = getCurrentUser();
        return currentUser != null
                && message != null
                && ModerationTools.hasReported(message.id(), currentUser.id());
    }

    public static boolean toggleReport(Message message) {
        ensurePopulated();
        if (adminMode || message == null || memberViewer == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (ModerationTools.hasReported(message.id(), memberViewer.id())) {
            return ModerationTools.removeReport(message.id(), memberViewer.id(), now);
        }
        return ModerationTools.addReport(message.id(), memberViewer.id(), now);
    }

    /** Add a local marker only after an online report was accepted. */
    public static boolean addReport(Message message) {
        ensurePopulated();
        return !adminMode
                && message != null
                && memberViewer != null
                && ModerationTools.addReport(
                        message.id(), memberViewer.id(), System.currentTimeMillis());
    }

    /**
     * Snapshot the post and the exact ancestor chain needed by the moderation
     * backend. Server IDs remain the source of truth; this
     * immutable value is safe to consume on the gateway's worker thread.
     */
    public static BackendReportTarget backendReportTarget(Message message) {
        ensurePopulated();
        Post post = getPostForMessage(message);
        if (post == null || message == null) {
            return null;
        }

        BackendReportTarget.PostSnapshot postSnapshot = new BackendReportTarget.PostSnapshot(
                post.id,
                post.poster,
                getForumKey(post),
                getPostTitle(post),
                getPostBody(post));

        if (isRootMessage(message)) {
            return new BackendReportTarget(
                    postSnapshot,
                    List.of(),
                    BackendReportTarget.TargetType.POST,
                    post.id);
        }

        ArrayList<BackendReportTarget.CommentSnapshot> chain = new ArrayList<>();
        Message cursor = message;
        while (cursor != null && !isRootMessage(cursor)) {
            chain.add(new BackendReportTarget.CommentSnapshot(
                    cursor.id(), cursor.poster(), cursor.message()));
            cursor = findMessage(post, getParentId(cursor));
        }
        Collections.reverse(chain);

        return new BackendReportTarget(
                postSnapshot,
                chain,
                BackendReportTarget.TargetType.COMMENT,
                message.id());
    }

    public static Post createPost(String topic, String body) {
        return createPost(topic, body, null);
    }

    /** Replaces one forum's visible cache with the backend page. */
    public static void synchronizeForum(
            String forumKey, List<BackendForumGateway.PostSnapshot> snapshots, String baseUrl) {
        ensurePopulated();
        Set<UUID> received = new HashSet<>();
        for (BackendForumGateway.PostSnapshot snapshot : snapshots) {
            received.add(snapshot.id());
            upsertRemotePost(snapshot, baseUrl);
        }
        for (UUID remoteId : new HashSet<>(REMOTE_POSTS)) {
            Post existing = PostDAO.getInstance().get(new Post(remoteId));
            if (existing != null && forumKey.equals(getForumKey(existing)) && !received.contains(remoteId)) {
                DELETED_POSTS.add(remoteId);
            }
        }
        REMOTE_SYNCED_FORUMS.add(forumKey);
    }

    public static Post upsertRemotePost(BackendForumGateway.PostSnapshot snapshot, String baseUrl) {
        ensurePopulated();
        User author = ensureRemoteUser(snapshot.authorId(), snapshot.authorName());
        setRemoteAvatar(snapshot.authorId(), snapshot.avatarUrl(), snapshot.avatarColor(), baseUrl);
        Post post = PostDAO.getInstance().get(new Post(snapshot.id()));
        if (post == null) {
            post = new Post(snapshot.id(), author.id(), snapshot.title());
            UUID rootId = stableId("remote-root", snapshot.id().toString());
            post.messages.insert(new Message(
                    rootId, author.id(), snapshot.id(), snapshot.createdAt(), snapshot.body()));
            PostDAO.getInstance().add(post);
            POST_META.put(post.id, new PostMeta(snapshot.forumKey(), rootId));
            MESSAGE_META.put(rootId, new MessageMeta(null, true));
        }
        POST_TITLE_OVERRIDES.put(post.id, snapshot.title());
        POST_BODY_OVERRIDES.put(post.id, snapshot.body());
        String image = absoluteMediaUrl(baseUrl, snapshot.mediaUrl());
        if (image == null) POST_IMAGE_URIS.remove(post.id); else POST_IMAGE_URIS.put(post.id, image);
        POST_CATEGORIES.put(post.id,snapshot.category()==null?"Study":snapshot.category());
        if(snapshot.pinRank()==null) POST_TOP_RANKS.remove(post.id); else POST_TOP_RANKS.put(post.id,snapshot.pinRank());
        DELETED_POSTS.remove(post.id);
        REMOTE_POSTS.add(post.id);
        REMOTE_SYNCED_FORUMS.add(snapshot.forumKey());
        return post;
    }

    public static void replaceUserPosts(UUID authorId, List<BackendForumGateway.PostSnapshot> items, String origin) {
        for (Post post : getPostsByUser(authorId)) if (REMOTE_POSTS.contains(post.id)) DELETED_POSTS.add(post.id);
        for (BackendForumGateway.PostSnapshot item : items) upsertRemotePost(item, origin);
    }

    public static void synchronizeComments(
            UUID postId, List<BackendForumGateway.CommentSnapshot> snapshots, String baseUrl) {
        ensurePopulated();
        Post post = PostDAO.getInstance().get(new Post(postId));
        if (post == null) return;
        UUID rootId = getRootMessageId(post);
        for (BackendForumGateway.CommentSnapshot snapshot : snapshots) {
            REMOTE_COMMENTS.add(snapshot.id());
            REMOVED_REMOTE_COMMENTS.remove(snapshot.id());
            ensureRemoteUser(snapshot.authorId(), snapshot.authorName());
            setRemoteAvatar(snapshot.authorId(), snapshot.avatarUrl(), snapshot.avatarColor(), baseUrl);
            Message existing = findMessage(post, snapshot.id());
            if (existing == null) {
                existing = new Message(snapshot.id(), snapshot.authorId(), post.id, snapshot.createdAt(), snapshot.body());
                post.messages.insert(existing);
                MESSAGE_META.put(existing.id(), new MessageMeta(
                        snapshot.parentId() == null ? rootId : snapshot.parentId(), false));
            }
            MESSAGE_BODY_OVERRIDES.put(existing.id(), snapshot.body());
            String image = absoluteMediaUrl(baseUrl, snapshot.mediaUrl());
            if (image == null) MESSAGE_IMAGE_URIS.remove(existing.id()); else MESSAGE_IMAGE_URIS.put(existing.id(), image);
        }
    }

    /** Refresh replaces the visible page; subsequent cursor pages append to it. */
    public static void replaceRemoteComments(
            UUID postId, List<BackendForumGateway.CommentSnapshot> snapshots, String baseUrl) {
        Post post = PostDAO.getInstance().get(new Post(postId));
        if (post != null) {
            Iterator<Message> messages = post.messages.getAll();
            while (messages.hasNext()) {
                UUID id = messages.next().id();
                if (REMOTE_COMMENTS.contains(id)) REMOVED_REMOTE_COMMENTS.add(id);
            }
        }
        synchronizeComments(postId, snapshots, baseUrl);
    }

    public static Message upsertRemoteComment(
            UUID postId, BackendForumGateway.CommentSnapshot snapshot, String baseUrl) {
        synchronizeComments(postId, List.of(snapshot), baseUrl);
        Post post = PostDAO.getInstance().get(new Post(postId));
        return post == null ? null : findMessage(post, snapshot.id());
    }

    static User ensureRemoteUser(UUID userId, String displayName) {
        if (displayName != null && !displayName.isBlank()) REMOTE_USER_NAMES.put(userId, displayName);
        User existing = UserDAO.getInstance().getByUUID(userId);
        if (existing != null) return existing;
        String base = displayName == null || displayName.isBlank() ? "member" : displayName.trim();
        User user = new User(userId, User.Role.Member, base + "_" + userId.toString().substring(0, 6), "");
        UserDAO.getInstance().add(user);
        return user;
    }

    private static String absoluteMediaUrl(String baseUrl, String mediaUrl) {
        if (mediaUrl == null || mediaUrl.isBlank()) return null;
        if (mediaUrl.startsWith("http://") || mediaUrl.startsWith("https://")) return mediaUrl;
        return baseUrl + (mediaUrl.startsWith("/") ? mediaUrl : "/" + mediaUrl);
    }

    public static Post createPost(String topic, String body, String imageUri) {
        return createPost(topic, body, imageUri, null);
    }

    public static Post createPost(String topic, String body, String imageUri, String category) {
        ensurePopulated();
        User poster = getCurrentUser();
        if (poster == null || topic == null || topic.trim().isEmpty()) {
            return null;
        }
        long now = System.currentTimeMillis();
        Post post = createForumPost(
                selectedForumKey,
                poster,
                topic.trim(),
                body == null ? "" : body.trim(),
                now,
                1
        );
        String trimmedImageUri = imageUri == null ? "" : imageUri.trim();
        if (trimmedImageUri.isEmpty()) POST_IMAGE_URIS.remove(post.id);
        else POST_IMAGE_URIS.put(post.id, trimmedImageUri);
        String trimmedCategory = category == null ? "" : category.trim();
        POST_CATEGORIES.put(post.id, trimmedCategory.isEmpty() ? defaultCategories(null).get(0) : trimmedCategory);
        return post;
    }

    public static boolean updatePost(Post post, String title, String body, String category) {
        return updatePost(post, title, body, category, null);
    }

    public static boolean updatePost(Post post, String title, String body, String category, String imageUri) {
        ensurePopulated();
        UUID currentUserId = getCurrentUserId();
        if (post == null || isDeletedPost(post) || currentUserId == null || !currentUserId.equals(post.poster)) {
            return false;
        }
        String trimmedTitle = title == null ? "" : title.trim();
        if (trimmedTitle.isEmpty()) {
            return false;
        }
        POST_TITLE_OVERRIDES.put(post.id, trimmedTitle);
        POST_BODY_OVERRIDES.put(post.id, body == null ? "" : body.trim());
        String trimmedCategory = category == null ? "" : category.trim();
        if (!trimmedCategory.isEmpty()) {
            POST_CATEGORIES.put(post.id, trimmedCategory);
        }
        String trimmedImageUri = imageUri == null ? "" : imageUri.trim();
        if (trimmedImageUri.isEmpty()) POST_IMAGE_URIS.remove(post.id);
        else POST_IMAGE_URIS.put(post.id, trimmedImageUri);
        return true;
    }

    public static boolean deletePost(Post post) {
        ensurePopulated();
        UUID currentUserId = getCurrentUserId();
        if (post == null || currentUserId == null || !currentUserId.equals(post.poster)) {
            return false;
        }
        DELETED_POSTS.add(post.id);
        POST_TITLE_OVERRIDES.remove(post.id);
        POST_BODY_OVERRIDES.remove(post.id);
        POST_IMAGE_URIS.remove(post.id);
        POST_CATEGORIES.remove(post.id);
        POST_TOP_RANKS.remove(post.id);
        BASE_BOOKMARKS.remove(post.id);
        BASE_VOTES.keySet().removeIf(key -> key.targetId().equals(post.id));
        BOOKMARKED_POSTS.remove(post.id);
        USER_VOTES.keySet().removeIf(key -> key.targetId().equals(post.id));
        return true;
    }

    public static boolean isDeletedPost(Post post) {
        return post != null && DELETED_POSTS.contains(post.id);
    }

    public static Message createReply(Message parent, String content) {
        return createReply(parent, content, null);
    }

    public static Message createReply(Message parent, String content, String imageUri) {
        ensurePopulated();
        User poster = getCurrentUser();
        Post post = getPostForMessage(parent);
        String trimmedContent = content == null ? "" : content.trim();
        String trimmedImageUri = imageUri == null ? "" : imageUri.trim();
        if (poster == null || post == null || parent == null || (trimmedContent.isEmpty() && trimmedImageUri.isEmpty())) {
            return null;
        }

        Message reply = addComment(post, poster, parent, System.currentTimeMillis(), trimmedContent, 0);
        if (!trimmedImageUri.isEmpty()) {
            MESSAGE_IMAGE_URIS.put(reply.id(), trimmedImageUri);
        }
        return reply;
    }

    public static String getMessageImageUri(Message message) {
        if (message == null) {
            return null;
        }
        return MESSAGE_IMAGE_URIS.get(message.id());
    }

    public static void setMessageImageUri(Message message, String imageUri) {
        if (message == null) return;
        if (imageUri == null || imageUri.isBlank()) MESSAGE_IMAGE_URIS.remove(message.id());
        else MESSAGE_IMAGE_URIS.put(message.id(), imageUri);
    }

    public static String getPostImageUri(Post post) {
        if (post == null) {
            return null;
        }
        return POST_IMAGE_URIS.get(post.id);
    }

    public static boolean togglePostVote(Post post, int direction) {
        return toggleVote(TargetType.POST, post == null ? null : post.id, direction);
    }

    public static boolean togglePostBookmark(Post post) {
        if (onlineMode) return post != null && ServerFeatures.bookmark(post.id, !hasBookmarkedPost(post));
        ensurePopulated();
        if (post == null) {
            return false;
        }
        if (BOOKMARKED_POSTS.contains(post.id)) {
            BOOKMARKED_POSTS.remove(post.id);
        } else {
            BOOKMARKED_POSTS.add(post.id);
        }
        return true;
    }

    public static boolean hasBookmarkedPost(Post post) {
        ensurePopulated();
        if (onlineMode) return post != null && ServerFeatures.state("posts", post.id).optBoolean("bookmarked");
        return post != null && BOOKMARKED_POSTS.contains(post.id);
    }

    public static ArrayList<Post> getBookmarkedPosts() {
        if (onlineMode) { ArrayList<Post> result = new ArrayList<>(); for (UUID id : ServerFeatures.collection("BOOKMARKED")) { Post post = getPostById(id.toString()); if (post != null && !isDeletedPost(post)) result.add(post); } return result; }
        ensurePopulated();
        ArrayList<Post> posts = new ArrayList<>();
        Iterator<Post> iterator = PostDAO.getInstance().getAll();
        while (iterator.hasNext()) {
            Post post = iterator.next();
            if (post != null && !isDeletedPost(post) && BOOKMARKED_POSTS.contains(post.id)) {
                posts.add(post);
            }
        }
        posts.sort(Comparator.comparingLong(AppData::getLatestActivityTimestamp).reversed());
        return posts;
    }

    public static ArrayList<Post> getLikedPosts() {
        if (onlineMode) { ArrayList<Post> result = new ArrayList<>(); for (UUID id : ServerFeatures.collection("LIKED")) { Post post = getPostById(id.toString()); if (post != null && !isDeletedPost(post)) result.add(post); } return result; }
        ensurePopulated();
        ArrayList<Post> posts = new ArrayList<>();
        Iterator<Post> iterator = PostDAO.getInstance().getAll();
        while (iterator.hasNext()) {
            Post post = iterator.next();
            if (post != null && !isDeletedPost(post) && getCurrentUserPostVote(post) > 0) {
                posts.add(post);
            }
        }
        posts.sort(Comparator.comparingLong(AppData::getLatestActivityTimestamp).reversed());
        return posts;
    }

    public static int getPostBookmarkCount(Post post) {
        if (onlineMode) return post == null ? 0 : ServerFeatures.state("posts", post.id).optInt("bookmarks");
        ensurePopulated();
        if (post == null) {
            return 0;
        }
        int count = BASE_BOOKMARKS.getOrDefault(post.id, 0);
        return BOOKMARKED_POSTS.contains(post.id) ? count + 1 : count;
    }

    public static String getPostBookmarkCountLabel(Context context, Post post) {
        return String.valueOf(getPostBookmarkCount(post));
    }

    public static boolean toggleMessageVote(Message message, int direction) {
        return toggleVote(TargetType.MESSAGE, message == null ? null : message.id(), direction);
    }

    public static boolean togglePostLike(Post post) {
        return togglePostVote(post, 1);
    }

    public static boolean toggleMessageLike(Message message) {
        return toggleMessageVote(message, 1);
    }

    public static boolean toggleHidden(Message message) {
        ensurePopulated();
        if (!adminMode || message == null || adminViewer == null) {
            return false;
        }

        return ModerationTools.setHidden(message.id(), adminViewer.id(), !ModerationTools.isHidden(message.id()));
    }

    public static boolean setHidden(Message message, boolean hidden) {
        ensurePopulated();
        return adminMode
                && message != null
                && adminViewer != null
                && ModerationTools.setHidden(message.id(), adminViewer.id(), hidden);
    }

    /** Backend post ids map to the local post id; comment ids map directly. */
    public static Message findModerationMessage(UUID localTargetId) {
        ensurePopulated();
        if (localTargetId == null) {
            return null;
        }
        Iterator<Post> posts = PostDAO.getInstance().getAll();
        while (posts.hasNext()) {
            Post post = posts.next();
            if (localTargetId.equals(post.id)) {
                return getRootMessage(post);
            }
            Message found = findMessage(post, localTargetId);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    public static int getPostVoteScore(Post post) {
        return getVoteScore(TargetType.POST, post == null ? null : post.id);
    }

    public static int getMessageVoteScore(Message message) {
        return getVoteScore(TargetType.MESSAGE, message == null ? null : message.id());
    }

    public static String getPostLikeCountLabel(Context context, Post post) {
        return String.valueOf(getPostVoteScore(post));
    }

    public static int getPostCommentCount(Post post) {
        int loaded=getVisibleMessageCount(post);
        return post!=null && REMOTE_POSTS.contains(post.id)
                ? ServerFeatures.state("posts",post.id).optInt("comments",loaded) : loaded;
    }

    public static String getPostReplyCountLabel(Context context, Post post) {
        return String.valueOf(getPostCommentCount(post));
    }

    public static String getPostCommentChipLabel(Context context, Post post) {
        return context.getString(R.string.post_comments_chip, getPostCommentCount(post));
    }

    public static String getMessageLikeCountLabel(Context context, Message message) {
        return String.valueOf(getMessageVoteScore(message));
    }

    public static boolean hasCurrentUserLikedPost(Post post) {
        return getCurrentUserVote(TargetType.POST, post == null ? null : post.id) > 0;
    }

    public static boolean hasCurrentUserLikedMessage(Message message) {
        return getCurrentUserVote(TargetType.MESSAGE, message == null ? null : message.id()) > 0;
    }

    public static int getCurrentUserPostVote(Post post) {
        return getCurrentUserVote(TargetType.POST, post == null ? null : post.id);
    }

    public static int getCurrentUserMessageVote(Message message) {
        return getCurrentUserVote(TargetType.MESSAGE, message == null ? null : message.id());
    }

    public static String getMessageStatus(Context context, Message message) {
        if (message == null) {
            return "";
        }
        ArrayList<String> parts = new ArrayList<>();
        int reportCount = getReportCount(message);

        if (isHidden(message)) {
            parts.add(context.getString(adminMode ? R.string.message_hidden_admin : R.string.message_hidden_member));
        }
        if (reportCount > 0) {
            parts.add(reportCount == 1
                    ? context.getString(R.string.message_report_single)
                    : context.getString(R.string.message_report_plural, reportCount));
        }
        if (!adminMode && hasCurrentUserReported(message)) {
            parts.add(context.getString(R.string.message_reported_by_you));
        }
        if (parts.isEmpty()) {
            return "";
        }
        return joinWithBullets(parts);
    }

    public static String getMessageActionLabel(Context context, Message message) {
        if (adminMode) {
            return context.getString(isHidden(message) ? R.string.action_restore : R.string.action_hide);
        }
        return context.getString(hasCurrentUserReported(message) ? R.string.action_undo_report : R.string.action_report);
    }

    public static String getQueueSubtitle(Context context, String strategy, int size) {
        String prefix = context.getString(
                STRATEGY_MOST.equals(strategy) ? R.string.queue_sort_most : R.string.queue_sort_oldest
        );
        if (size == 0) {
            return context.getString(R.string.queue_subtitle_empty, prefix);
        }
        return context.getString(R.string.queue_subtitle_count, prefix, size);
    }

    public static String getReportedCardMeta(Context context, Message message) {
        Post post = getPostForMessage(message);
        String forum = post == null ? context.getString(R.string.thread_unknown) : getPostCommunityLabel(context, post);
        return context.getString(R.string.reported_meta_format, forum, getMessageStatus(context, message));
    }

    public static String getUsername(UUID userId) {
        ensurePopulated();
        if (REMOTE_USER_NAMES.containsKey(userId)) return REMOTE_USER_NAMES.get(userId);
        User user = UserDAO.getInstance().getByUUID(userId);
        if (user == null || user.username() == null) {
            return contextLanguageIsChinese() ? "unknown" : "unknown";
        }
        return user.username();
    }

    public static UUID getCurrentUserId() {
        User user = getCurrentUser();
        return user == null ? null : user.id();
    }

    public static User getUser(UUID userId) {
        ensurePopulated();
        return userId == null ? null : UserDAO.getInstance().getByUUID(userId);
    }

    public static ArrayList<User> getPeople() {
        ensurePopulated();
        ArrayList<User> users = new ArrayList<>();
        Iterator<User> iterator = UserDAO.getInstance().getAll();
        while (iterator.hasNext()) {
            User user = iterator.next();
            if (user != null && !user.id().equals(getCurrentUserId()) && (!onlineMode || REMOTE_USER_NAMES.containsKey(user.id()))) {
                users.add(user);
            }
        }
        users.sort(Comparator.comparing(User::username));
        return users;
    }

    public static ArrayList<User> getFollowedPeople() {
        if (onlineMode) { ArrayList<User> result=new ArrayList<>(); for (User user:getPeople()) if (isFollowing(user.id())) result.add(user); return result; }
        ensurePopulated();
        ArrayList<User> users = new ArrayList<>();
        for (UUID userId : FOLLOWED_USERS) {
            User user = getUser(userId);
            if (user != null) {
                users.add(user);
            }
        }
        users.sort(Comparator.comparing(User::username));
        return users;
    }

    public static boolean isFollowing(UUID userId) {
        if (onlineMode) return userId != null && ServerFeatures.state("users", userId).optBoolean("followed");
        ensurePopulated();
        return userId != null && FOLLOWED_USERS.contains(userId);
    }

    public static boolean toggleFollow(UUID userId) {
        if (onlineMode) return userId != null && !userId.equals(getCurrentUserId()) && ServerFeatures.follow(userId, !isFollowing(userId));
        ensurePopulated();
        UUID currentUserId = getCurrentUserId();
        if (userId == null || userId.equals(currentUserId)) {
            return false;
        }
        if (FOLLOWED_USERS.contains(userId)) {
            FOLLOWED_USERS.remove(userId);
        } else {
            FOLLOWED_USERS.add(userId);
        }
        return true;
    }

    public static int getFollowerCount(UUID userId) {
        if (onlineMode) return userId == null ? 0 : ServerFeatures.state("users", userId).optInt("followers");
        ensurePopulated();
        User user = getUser(userId);
        if (user == null) {
            return 0;
        }
        int base = 8 + Math.abs(user.username().hashCode() % 37);
        UUID currentUserId = getCurrentUserId();
        if (currentUserId != null && !currentUserId.equals(userId) && FOLLOWED_USERS.contains(userId)) {
            base += 1;
        }
        return base;
    }

    public static int getFollowingCount(UUID userId) {
        if (onlineMode) return userId == null ? 0 : ServerFeatures.state("users", userId).optInt("following");
        ensurePopulated();
        UUID currentUserId = getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(userId)) {
            return getFollowedPeople().size();
        }
        User user = getUser(userId);
        if (user == null) {
            return 0;
        }
        return 2 + Math.abs(user.username().hashCode() % 11);
    }

    public static ArrayList<User> getFollowingPeople(UUID userId) {
        if (onlineMode) return new ArrayList<>(); // Online lists are loaded with bounded server pages.

        ensurePopulated();
        UUID currentUserId = getCurrentUserId();
        if (currentUserId != null && currentUserId.equals(userId)) {
            return getFollowedPeople();
        }
        return samplePeopleForUser(userId, getFollowingCount(userId), 0);
    }

    public static ArrayList<User> getFollowerPeople(UUID userId) {
        if (onlineMode) return new ArrayList<>(); // Online lists are loaded with bounded server pages.

        ensurePopulated();
        return samplePeopleForUser(userId, Math.min(getFollowerCount(userId), 12), 7);
    }

    public static int getReceivedEngagementCount(UUID userId) {
        ensurePopulated();
        return getReceivedLikeCount(userId) + getReceivedBookmarkCount(userId);
    }

    public static int getReceivedLikeCount(UUID userId) {
        if (onlineMode) return userId == null ? 0 : ServerFeatures.state("users", userId).optInt("likes");
        ensurePopulated();
        int total = 0;
        for (Post post : getPostsByUser(userId)) {
            total += Math.max(0, getPostVoteScore(post));
        }
        return total;
    }

    public static int getReceivedBookmarkCount(UUID userId) {
        if (onlineMode) return userId == null ? 0 : ServerFeatures.state("users", userId).optInt("bookmarks");
        ensurePopulated();
        int total = 0;
        for (Post post : getPostsByUser(userId)) {
            total += Math.max(0, getPostBookmarkCount(post));
        }
        return total;
    }

    private static ArrayList<User> samplePeopleForUser(UUID userId, int count, int offset) {
        ArrayList<User> pool = getPeople();
        pool.removeIf(user -> user.id().equals(userId));
        if (pool.isEmpty()) {
            return pool;
        }
        int limit = Math.max(0, Math.min(count, pool.size()));
        int start = Math.abs((userId == null ? 0 : userId.hashCode()) + offset) % pool.size();
        ArrayList<User> sampled = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            sampled.add(pool.get((start + i) % pool.size()));
        }
        return sampled;
    }

    public static ArrayList<Post> getPostsByUser(UUID userId) {
        ensurePopulated();
        ArrayList<Post> posts = new ArrayList<>();
        Iterator<Post> iterator = PostDAO.getInstance().getAll();
        while (iterator.hasNext()) {
            Post post = iterator.next();
            if (isDeletedPost(post) || (onlineMode && !REMOTE_POSTS.contains(post.id))) {
                continue;
            }
            if (post.poster != null && post.poster.equals(userId)) {
                posts.add(post);
            }
        }
        posts.sort(Comparator.comparingLong(AppData::getLatestActivityTimestamp).reversed());
        return posts;
    }

    public static ArrayList<Message> getMessagesByUser(UUID userId) {
        ensurePopulated();
        ArrayList<Message> messages = new ArrayList<>();
        if (userId == null) {
            return messages;
        }
        Iterator<Post> postIterator = PostDAO.getInstance().getAll();
        while (postIterator.hasNext()) {
            Post post = postIterator.next();
            if (isDeletedPost(post) || (onlineMode && !REMOTE_POSTS.contains(post.id))) {
                continue;
            }
            Iterator<Message> messageIterator = (adminMode
                    ? post.messages
                    : post.getVisibleMessages(false)).getAll();
            while (messageIterator.hasNext()) {
                Message message = messageIterator.next();
                if (!isRootMessage(message) && !REMOVED_REMOTE_COMMENTS.contains(message.id()) && userId.equals(message.poster())) {
                    messages.add(message);
                }
            }
        }
        messages.sort(Comparator.comparingLong(Message::timestamp).reversed());
        return messages;
    }

    public static String getDisplayName(Context context, UUID userId) {
        User currentUser = getCurrentUser();
        if (context != null && currentUser != null && currentUser.id().equals(userId)) {
            String nickname = UiPreferences.getProfileNickname(context);
            if (nickname != null && !nickname.trim().isEmpty()) {
                return nickname.trim();
            }
        }
        return getUsername(userId);
    }

    public static String formatTimestamp(long timestamp) {
        return TIMESTAMP_FORMATTER.format(timestamp);
    }

    public static String getThreadUnknownLabel(Context context) {
        return context.getString(R.string.thread_unknown);
    }

    public static String formatMessageAuthorLine(Context context, Message message) {
        return context.getString(
                R.string.message_author_line,
                getDisplayName(context, message.poster()),
                formatTimestamp(message.timestamp())
        );
    }

    public static String getAvatarLetter(Context context, UUID userId) {
        String label = getDisplayName(context, userId);
        if (label == null) {
            return "?";
        }
        String trimmed = label.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        return trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    public static String getAvatarLetter(UUID userId) {
        return getAvatarLetter(null, userId);
    }

    public static void setRemoteAvatar(UUID user, String url, int color, String origin) {
        if (user == null) return;
        if (url == null) REMOTE_AVATAR_URLS.remove(user); else REMOTE_AVATAR_URLS.put(user, absoluteMediaUrl(origin, url));
        REMOTE_AVATAR_COLORS.put(user, color);
    }
    public static String getAvatarUrl(Context context, UUID user) {
        if (user != null && user.equals(getCurrentUserId())) return UiPreferences.getAvatarImageUri(context);
        return REMOTE_AVATAR_URLS.get(user);
    }
    public static int getAvatarColor(UUID userId) {
        if (REMOTE_AVATAR_COLORS.containsKey(userId)) return UiPreferences.getGoogleColor(REMOTE_AVATAR_COLORS.get(userId));
        String username = getUsername(userId);
        int index = Math.abs(username.hashCode()) % UiPreferences.getGoogleColorCount();
        return UiPreferences.getGoogleColor(index);
    }

    public static int getAvatarColor(Context context, UUID userId) {
        User currentUser = getCurrentUser();
        if (context != null && currentUser != null && currentUser.id().equals(userId)) {
            return UiPreferences.getAvatarColor(context);
        }
        return getAvatarColor(userId);
    }

    private static User getCurrentUser() {
        return adminMode ? adminViewer : memberViewer;
    }

    private static void seedUsers() {
        memberViewer = addUser("campusviewer", User.Role.Member);
        adminViewer = addUser("modmentor", User.Role.Admin);
        studyBuddy = addUser("studybuddy", User.Role.Member);
        lateCoder = addUser("latecoder", User.Role.Member);
        uxPilot = addUser("uxpilot", User.Role.Member);
        treeSage = addUser("treesage", User.Role.Member);
        compSurvivor = addUser("comp_survivor", User.Role.Member);
        anuSleepDeprived = addUser("anu_sleep_deprived", User.Role.Member);
        labPartner = addUser("lab_partner", User.Role.Member);
        quacEnjoyer = addUser("quac_enjoyer", User.Role.Member);
        deadlineVictim = addUser("deadline_victim", User.Role.Member);
    }

    private static void seedSocialState() {
        FOLLOWED_USERS.add(studyBuddy.id());
        FOLLOWED_USERS.add(treeSage.id());
        FOLLOWED_USERS.add(uxPilot.id());

        long now = System.currentTimeMillis();
        Iterator<Post> iterator = PostDAO.getInstance().getAll();
        while (iterator.hasNext()) {
            Post post = iterator.next();
            if (post.poster == null || !post.poster.equals(memberViewer.id())) {
                continue;
            }
            BOOKMARKED_POSTS.add(post.id);
            SEEDED_NOTIFICATIONS.add(new AppNotification(
                    NotificationType.LIKE,
                    memberViewer.id(),
                    studyBuddy.id(),
                    post.id,
                    getRootMessageId(post),
                    contextLanguageIsChinese() ? "studybuddy 点赞了你的帖子" : "studybuddy liked your post",
                    getPostTitle(post),
                    now - minutes(7)
            ));
            SEEDED_NOTIFICATIONS.add(new AppNotification(
                    NotificationType.BOOKMARK,
                    memberViewer.id(),
                    treeSage.id(),
                    post.id,
                    getRootMessageId(post),
                    contextLanguageIsChinese() ? "treesage 收藏了你的帖子" : "treesage saved your post",
                    getPostTitle(post),
                    now - minutes(6)
            ));
            SEEDED_NOTIFICATIONS.add(new AppNotification(
                    NotificationType.MENTION,
                    memberViewer.id(),
                    uxPilot.id(),
                    post.id,
                    getRootMessageId(post),
                    contextLanguageIsChinese() ? "uxpilot @了你" : "uxpilot mentioned you",
                    "@" + memberViewer.username() + " " + (contextLanguageIsChinese() ? "这个方向可以继续做。" : "this direction is worth keeping."),
                    now - minutes(5)
            ));
            break;
        }
    }

    private static void seedForumThreads() {
        long now = System.currentTimeMillis();
        boolean zh = contextLanguageIsChinese();

        Post comp2300Post = createForumPost(
                FORUM_ANU,
                compSurvivor,
                zh ? "COMP2300 真的快把我摧毁了" : "COMP2300 is actually destroying me",
                zh
                        ? "我知道计算机组成会很难，但 COMP2300 感觉像一份全职工作。每次 lab 开始我都以为自己理解电路，最后都会开始怀疑自己到底懂不懂二进制。QuAC CPU 确实很酷，但凌晨两点调 Digital 是另一种痛苦。"
                        : "I knew computer organisation would be hard, but COMP2300 feels like a full-time job. Every lab starts with me thinking I understand the circuit, and ends with me questioning whether I understand binary at all. The QuAC CPU is cool, but debugging Digital at 2am is a different kind of pain.",
                now - minutes(18),
                37
        );
        POST_CATEGORIES.put(comp2300Post.id, zh ? "学习" : "Study");
        Message comp2300Comment = addComment(
                comp2300Post,
                anuSleepDeprived,
                null,
                now - minutes(15),
                zh
                        ? "同感。Digital 里一个小小接线错误都像是在针对我本人。"
                        : "Same. Digital makes one tiny wiring mistake feel like a personal attack.",
                12
        );
        Message comp2300SecondReply = addComment(
                comp2300Post,
                labPartner,
                comp2300Comment,
                now - minutes(13),
                zh
                        ? "PC 不更新的那一刻，我的灵魂直接离开身体。"
                        : "The moment the PC stops updating, my soul leaves my body.",
                6
        );
        addComment(
                comp2300Post,
                lateCoder,
                comp2300SecondReply,
                now - minutes(12),
                zh
                        ? "我发誓一半 lab 时间都在找我到底忘了连哪根线。"
                        : "I swear half the lab is just learning which wire I forgot to connect.",
                4
        );
        addComment(
                comp2300Post,
                quacEnjoyer,
                null,
                now - minutes(11),
                zh
                        ? "但说实话，datapath 一旦想通，确实还挺爽的。"
                        : "Lowkey though, once the datapath clicks, it is actually kind of satisfying.",
                9
        );
        addComment(
                comp2300Post,
                deadlineVictim,
                null,
                now - minutes(9),
                zh
                        ? "最难的是假装自己没有花三个小时调一个 mux。"
                        : "The hardest part is pretending I did not spend three hours debugging a single mux.",
                15
        );
        addComment(
                comp2300Post,
                quacEnjoyer,
                null,
                now - minutes(8),
                zh
                        ? "你昨天说的那个 datapath 图救了我一命。"
                        : "Your datapath sketch from yesterday saved me.",
                5
        );

        Post layoutPost = createForumPost(
                FORUM_ANU,
                memberViewer,
                zh ? "我们的 ANU 主页要不要直接做成 Reddit 风格？" : "Should the ANU home feed lean fully into a Reddit-style layout?",
                zh
                        ? "我更想把主页做成信息流，减少大块卡片，把帖子重点放在论坛名、标题、正文和评论入口上。这样讲 demo 的时候会更像真实社区产品。"
                        : "I want the home feed to feel more like a discussion stream: less chunky cards, more focus on the community name, title, body, and comment entry. It should read like a real social product during the demo.",
                now - minutes(96),
                42
        );
        POST_CATEGORIES.put(layoutPost.id, "AI");
        pinPostToTop(layoutPost, 0);
        POST_IMAGE_URIS.put(layoutPost.id, drawableImageUri(R.drawable.avatar_anu));
        Message layoutTopReply = addComment(
                layoutPost,
                studyBuddy,
                null,
                now - minutes(88),
                zh
                        ? "赞成，尤其是如果我们把侧边栏改成只负责切论坛，首页就会清爽很多。"
                        : "I agree, especially if the drawer only handles community switching. That would make the home screen much cleaner.",
                14
        );
        MESSAGE_IMAGE_URIS.put(layoutTopReply.id(), drawableImageUri(R.drawable.avatar_unsw));
        addComment(
                layoutPost,
                uxPilot,
                layoutTopReply,
                now - minutes(82),
                zh
                        ? "帖子之间只用分隔线会更像网页 Reddit，视觉节奏也会更快。"
                        : "Using separators instead of cards would feel much closer to Reddit and speed up the visual rhythm.",
                9
        );
        Message layoutThirdLevel = addComment(
                layoutPost,
                treeSage,
                layoutTopReply,
                now - minutes(81),
                zh
                        ? "如果要演示楼中楼，我建议这里再接一个二楼回复，让缩进一眼就能看出来。"
                        : "If we want to show nesting clearly, we should add a second-floor reply here so the indentation reads immediately.",
                6
        );
        addComment(
                layoutPost,
                memberViewer,
                layoutThirdLevel,
                now - minutes(80),
                zh
                        ? "三楼也可以保留得很简洁，只要头像、昵称和操作按钮还在就够了。"
                        : "A third-floor reply can stay very simple as long as the avatar, username, and actions still hold together.",
                4
        );
        hiddenExampleMessage = addComment(
                layoutPost,
                lateCoder,
                layoutTopReply,
                now - minutes(79),
                zh
                        ? "别想太多，直接照着抄就好了。"
                        : "Stop overthinking it and just copy the layout.",
                -3
        );
        addComment(
                layoutPost,
                adminViewer,
                null,
                now - minutes(74),
                zh
                        ? "参考可以，但语气也要保持建设性。我们重点展示的是产品表达和审核能力。"
                        : "Borrow the structure if it helps, but keep the tone constructive. The real story is product clarity plus moderation.",
                18
        );

        Post forumPost = createForumPost(
                FORUM_ANU,
                uxPilot,
                zh ? "先只填充 ANU，另外三个论坛留空可以吗？" : "Can we fully seed ANU first and leave the other three forums empty?",
                zh
                        ? "我打算先把 ANU 论坛的帖子和评论做完整，UNSW、USYD、UM 先显示为空状态，这样论坛切换的逻辑也能先验收。"
                        : "My plan is to fully populate the ANU forum first, while UNSW, USYD, and UM show an empty state for now. That still lets us prove the forum switching flow.",
                now - minutes(67),
                28
        );
        POST_CATEGORIES.put(forumPost.id, zh ? "社交" : "Social");
        queueExampleMessage = addComment(
                forumPost,
                studyBuddy,
                null,
                now - minutes(60),
                zh
                        ? "可以。老师能一眼看出我们已经有多论坛结构，只是暂时没往另外三个论坛灌内容。"
                        : "Yes. Markers can immediately see the multi-forum structure even if only one forum is populated right now.",
                11
        );
        Message forumSecondFloor = addComment(
                forumPost,
                treeSage,
                queueExampleMessage,
                now - minutes(56),
                zh
                        ? "而且这样更方便测试空页面和创建新帖之后的更新。"
                        : "It also makes empty-state handling and new-post refreshes much easier to test.",
                7
        );
        addComment(
                forumPost,
                adminViewer,
                forumSecondFloor,
                now - minutes(54),
                zh
                        ? "再往下接一个三楼，就能同时展示论坛切换和多层评论。"
                        : "One more third-floor reply here lets us show forum switching and deeper threading in the same post.",
                5
        );

        Post nestedPost = createForumPost(
                FORUM_ANU,
                treeSage,
                zh ? "楼中楼缩进要做到多深才够演示？" : "How deep should comment nesting go for the demo?",
                zh
                        ? "我觉得至少要有两层回复。只要楼层缩进清楚、头像和昵称层级明显，老师就能看出来评论树已经建好了。"
                        : "I think we need at least two visible reply levels. As long as the indentation, avatars, and bold usernames read clearly, the thread structure will be obvious in the demo.",
                now - minutes(49),
                19
        );
        POST_CATEGORIES.put(nestedPost.id, zh ? "期末" : "Finals");
        pinPostToTop(nestedPost, 1);
        Message nestedRootReply = addComment(
                nestedPost,
                memberViewer,
                null,
                now - minutes(42),
                zh
                        ? "两层够了，再深会挤压手机宽度。"
                        : "Two levels should be enough. Going deeper will start to squeeze the mobile layout.",
                6
        );
        Message nestedSecondLevel = addComment(
                nestedPost,
                uxPilot,
                nestedRootReply,
                now - minutes(38),
                zh
                        ? "那就让第二层仍然保留投票、reply 和举报入口，证明交互不会因为缩进消失。"
                        : "Then the second level should still keep vote, reply, and report entry points so the interaction does not disappear with indentation.",
                8
        );
        MESSAGE_IMAGE_URIS.put(nestedSecondLevel.id(), drawableImageUri(R.drawable.avatar_usyd));
        addComment(
                nestedPost,
                adminViewer,
                nestedSecondLevel,
                now - minutes(33),
                zh
                        ? "对，重点是结构清楚，不一定要把 reply 真做完。"
                        : "Exactly. The important part is that the structure is clear; the reply composer does not need to be fully wired yet.",
                5
        );
        addComment(
                nestedPost,
                studyBuddy,
                nestedRootReply,
                now - minutes(31),
                zh
                        ? "我再补一个二楼分支，这样就不会看起来像只有单条链路。"
                        : "I will add another second-floor branch so the thread does not look like only a single chain.",
                3
        );

        Post unswPost = createForumPost(
                FORUM_UNSW,
                studyBuddy,
                zh ? "UNSW 论坛先放什么内容比较自然？" : "What should the UNSW forum open with?",
                zh
                        ? "如果只是为了演示多论坛切换，我觉得可以先放一些和课程协作、社团活动相关的轻量帖子，不需要每个论坛都做得一样满。"
                        : "If the goal is to prove multi-forum switching, I think a few lightweight posts about coursework and student clubs are enough. Every forum does not need the same content density.",
                now - minutes(58),
                17
        );
        POST_CATEGORIES.put(unswPost.id, zh ? "社交" : "Social");
        POST_IMAGE_URIS.put(unswPost.id, drawableImageUri(R.drawable.avatar_unsw));
        addComment(
                unswPost,
                uxPilot,
                null,
                now - minutes(51),
                zh
                        ? "对，论坛氛围稍微区分开一点，切换时会更像真实产品。"
                        : "Agreed. Giving each forum a slightly different tone makes the switch feel more like a real product.",
                6
        );
        Message unswNested = addComment(
                unswPost,
                memberViewer,
                null,
                now - minutes(49),
                zh
                        ? "那我先占一楼，顺便拿这个帖子做楼中楼样例。"
                        : "I will take the first-floor slot here and use this thread as another nesting example.",
                4
        );
        MESSAGE_IMAGE_URIS.put(unswNested.id(), drawableImageUri(R.drawable.avatar_anu));
        Message unswSecondFloor = addComment(
                unswPost,
                adminViewer,
                unswNested,
                now - minutes(47),
                zh
                        ? "二楼回复放在这里，老师切到 UNSW 也能看到缩进。"
                        : "The second-floor reply can go here so the indentation is visible even after switching to UNSW.",
                3
        );
        addComment(
                unswPost,
                treeSage,
                unswSecondFloor,
                now - minutes(46),
                zh
                        ? "三楼再跟一句，演示时就不会只剩 ANU 有深层评论。"
                        : "And a third-floor reply keeps ANU from being the only forum with deeper comments.",
                2
        );

        createForumPost(
                FORUM_UNSW,
                lateCoder,
                zh ? "下周展示前要不要先统一界面术语？" : "Should we unify the UI wording before next week's demo?",
                zh
                        ? "现在不同页面里有帖子、评论、回复几种叫法，最好在正式演示前统一一下，不然视觉上已经成熟了，文案却会显得散。"
                        : "We currently mix labels like post, comment, and reply across screens. It would be good to unify them before the live demo so the wording feels as polished as the UI.",
                now - minutes(45),
                9
        );

        Post unswExam = createForumPost(
                FORUM_UNSW,
                compSurvivor,
                zh ? "COMP1511 期末考试有哪些必考知识点？" : "What are the must-know topics for COMP1511 finals?",
                zh
                        ? "期末快到了，感觉链表和递归两块还是没吃透。有没有学长学姐整理过考纲重点？往年真题在哪里可以找到？"
                        : "Finals are coming and I still can't get my head around linked lists and recursion. Has anyone compiled a cheat sheet? Where can I find past papers?",
                now - minutes(88),
                24
        );
        POST_CATEGORIES.put(unswExam.id, zh ? "期末" : "Finals");
        Message unswExamReply1 = addComment(unswExam, anuSleepDeprived, null, now - minutes(80),
                zh ? "链表的关键就是画图，每次指针变动前先在草稿纸上画清楚，考试不会出错。" : "Draw the diagram before every pointer step. If it's clear on paper you won't mess up in the exam.",
                14);
        addComment(unswExam, treeSage, unswExamReply1, now - minutes(75),
                zh ? "同意，递归的话先写好 base case，剩下的自然就出来了。" : "Agreed. Nail the base case first and the rest follows naturally.",
                8);
        addComment(unswExam, deadlineVictim, null, now - minutes(70),
                zh ? "CSE 的 past papers 在 UNSW Teams 上有，搜一下 COMP1511 那个频道。" : "Past papers are on UNSW Teams in the COMP1511 channel.",
                11);

        Post unswCafe = createForumPost(
                FORUM_UNSW,
                quacEnjoyer,
                zh ? "UNSW 周边哪家咖啡好喝又不贵？" : "Best affordable coffee near UNSW?",
                zh
                        ? "图书馆旁边的那家最近涨价了，$7 一杯有点肉痛。有没有 Kensington 附近性价比高一点的选择？"
                        : "The café next to the library just hiked prices to $7 a cup. Anyone know a better deal around Kensington?",
                now - minutes(62),
                18
        );
        POST_CATEGORIES.put(unswCafe.id, zh ? "社交" : "Social");
        addComment(unswCafe, labPartner, null, now - minutes(55),
                zh ? "Anzac Parade 上有一家叫 Brewtown 的，flat white 才 $4.5，味道还不错。" : "Brewtown on Anzac Parade does a flat white for $4.50 and it's actually good.",
                9);
        addComment(unswCafe, memberViewer, null, now - minutes(50),
                zh ? "Main Walkway 上的 vending machine 咖啡 $2.5，紧急情况救命的。" : "The vending machine on Main Walkway does $2.50 coffee for emergencies.",
                7);

        Post unswIntern = createForumPost(
                FORUM_UNSW,
                uxPilot,
                zh ? "大二学生能找到实习吗，还是要等大三？" : "Can second-year students land internships or is that a third-year thing?",
                zh
                        ? "看到很多 JD 写 penultimate，想知道大二学生有没有机会？还是先做一些 casual 的项目积累一下比较好？"
                        : "Most JDs say penultimate student. Wondering if there's any point applying in second year or should I just build personal projects for now?",
                now - minutes(40),
                21
        );
        POST_CATEGORIES.put(unswIntern.id, zh ? "就业" : "Career");
        addComment(unswIntern, studyBuddy, null, now - minutes(35),
                zh ? "大二完全可以投。很多公司的 internship program 对年级要求没那么严，关键是 GitHub 要有东西。" : "Second year is fine. Plenty of companies don't care as long as your GitHub isn't empty.",
                13);
        addComment(unswIntern, adminViewer, null, now - minutes(30),
                zh ? "小公司会更灵活，可以先从 startup 开始积累，再往大厂走。" : "Startups are more flexible about year level. Build experience there and use it to pitch the big names.",
                10);

        Post usydPost = createForumPost(
                FORUM_USYD,
                treeSage,
                zh ? "USYD 论坛适合拿来放更学术一点的话题" : "The USYD forum could lean a bit more academic",
                zh
                        ? "比如数据结构讲解、课程项目复盘、或者如何把 UML 图讲得更清楚。这样不同论坛会有一点自己的气质。"
                        : "We could use it for topics like data-structure explanations, project retrospectives, or clearer UML storytelling. That gives each forum a distinct personality.",
                now - minutes(53),
                13
        );
        POST_CATEGORIES.put(usydPost.id, zh ? "学习" : "Study");
        POST_IMAGE_URIS.put(usydPost.id, drawableImageUri(R.drawable.avatar_usyd));
        addComment(
                usydPost,
                memberViewer,
                null,
                now - minutes(47),
                zh
                        ? "这样挺好，切论坛时用户能马上感受到内容方向不同。"
                        : "That works well. Users can immediately feel the difference in content direction when they switch forums.",
                4
        );
        Message usydNested = addComment(
                usydPost,
                uxPilot,
                null,
                now - minutes(45),
                zh
                        ? "我也想在这个论坛放一个结构更清楚的评论树。"
                        : "I also want one cleaner nested thread in this forum.",
                3
        );
        MESSAGE_IMAGE_URIS.put(usydNested.id(), drawableImageUri(R.drawable.avatar_um));
        Message usydSecondFloor = addComment(
                usydPost,
                studyBuddy,
                usydNested,
                now - minutes(43),
                zh
                        ? "那这条就当二楼，顺便验证学术向帖子也能复用同一套布局。"
                        : "Then this can be the second floor, which also proves the same layout works for more academic threads.",
                2
        );
        addComment(
                usydPost,
                adminViewer,
                usydSecondFloor,
                now - minutes(42),
                zh
                        ? "三楼继续跟进一下，这样 USYD 也有完整的楼层示例。"
                        : "A third-floor follow-up here gives USYD a full nesting example too.",
                2
        );

        Post usydGroup = createForumPost(
                FORUM_USYD,
                lateCoder,
                zh ? "Data Science 项目找搭档，要求有 Python 基础" : "Looking for a Data Science project partner – Python required",
                zh
                        ? "期末大作业要做一个情感分析项目，想找一两个有 Python 基础的同学一起做，最好对 NLP 有一点了解。可以私信或者直接在这里留言。"
                        : "Working on a sentiment analysis project for the final and looking for 1-2 people with Python experience — ideally some NLP background. DM or reply here.",
                now - minutes(72),
                16
        );
        POST_CATEGORIES.put(usydGroup.id, zh ? "学习" : "Study");
        Message usydGroupReply = addComment(usydGroup, quacEnjoyer, null, now - minutes(65),
                zh ? "我有兴趣，本学期在学 NLTK，可以对接。" : "Interested — I've been working through NLTK this semester, happy to connect.",
                7);
        addComment(usydGroup, treeSage, usydGroupReply, now - minutes(60),
                zh ? "我也想加，之前做过一个 Twitter 情感分析的小项目，经验可以分享。" : "Count me in too — I did a small Twitter sentiment classifier last semester, happy to share the approach.",
                5);

        Post usydFood = createForumPost(
                FORUM_USYD,
                anuSleepDeprived,
                zh ? "Wentworth 食堂性价比下降了还是我变穷了" : "Is Wentworth getting worse or am I just broke now",
                zh
                        ? "感觉一年前 $12 能吃得很好，现在 $16 还只有一半的量。有没有其他地方推荐？Carslaw 对面那个怎么样？"
                        : "I swear $12 used to feel like a feast. Now $16 gets me half the plate. Anyone know a better spot near Carslaw?",
                now - minutes(55),
                22
        );
        POST_CATEGORIES.put(usydFood.id, zh ? "生活" : "Life");
        addComment(usydFood, memberViewer, null, now - minutes(48),
                zh ? "Carslaw 对面的那家寿司卷非常划算，$10 可以吃饱。" : "The sushi rolls across from Carslaw are great value — $10 and actually filling.",
                12);
        addComment(usydFood, deadlineVictim, null, now - minutes(42),
                zh ? "我现在就靠 Fisher Library 楼下的自动售货机撑过去……" : "I'm surviving exclusively on Fisher Library vending machines at this point…",
                16);

        Post usydExamAnxiety = createForumPost(
                FORUM_USYD,
                compSurvivor,
                zh ? "期末前焦虑睡不着，有什么好办法吗？" : "Can't sleep before finals — anyone else or just me?",
                zh
                        ? "每次到期末前两周就开始失眠，脑子根本停不下来。试过冥想但效果不稳定，有没有真的有用的方法？"
                        : "For the last two weeks before every finals period I can't switch off. Tried meditation but it's hit or miss. What actually works for you?",
                now - minutes(38),
                31
        );
        POST_CATEGORIES.put(usydExamAnxiety.id, zh ? "生活" : "Life");
        addComment(usydExamAnxiety, uxPilot, null, now - minutes(32),
                zh ? "把明天要做的事情写出来，放下手机，通常 20 分钟内就睡着了。" : "Write tomorrow's tasks down, put the phone away. Usually asleep within 20 minutes.",
                18);
        addComment(usydExamAnxiety, studyBuddy, null, now - minutes(28),
                zh ? "我会把复习进度做成清单，每划掉一项都有成就感，焦虑感也少很多。" : "I make a checklist of what I've covered and tick things off. The sense of progress kills the anxiety.",
                14);

        createForumPost(
                FORUM_USYD,
                adminViewer,
                zh ? "管理员视角下最重要的是操作路径短" : "Short moderator flows matter more than flashy UI",
                zh
                        ? "我更在意的是，从看到被举报评论，到进入帖子，再到执行隐藏动作，整个路径是否足够短。样式可以继续打磨，但流程要先顺。"
                        : "I care most about how quickly a moderator can move from seeing a reported comment to opening the thread and taking action. The visuals can keep improving, but the flow has to stay tight.",
                now - minutes(39),
                21
        );

        Post umPost = createForumPost(
                FORUM_UM,
                uxPilot,
                zh ? "UM 论坛可以更偏生活化一点" : "The UM forum can feel a bit more casual",
                zh
                        ? "比如校园生活、咖啡店推荐、社团活动分享，这样四个论坛切换时不会全是同一种项目讨论。"
                        : "It could cover things like campus life, cafe recommendations, and student activity chatter so the four forums do not all feel like clones of the same project board.",
                now - minutes(50),
                15
        );
        POST_CATEGORIES.put(umPost.id, zh ? "社交" : "Social");
        Message umReply = addComment(
                umPost,
                studyBuddy,
                null,
                now - minutes(44),
                zh
                        ? "这个思路不错，能让界面看起来更像真正长期在用的社区。"
                        : "I like that direction. It makes the app feel more like a community people would actually keep using.",
                5
        );
        MESSAGE_IMAGE_URIS.put(umReply.id(), drawableImageUri(R.drawable.avatar_um));
        Message umSecondFloor = addComment(
                umPost,
                memberViewer,
                umReply,
                now - minutes(41),
                zh
                        ? "而且这样也适合展示楼中楼，不一定都要围绕审核功能说。"
                        : "It also gives us another good place to show nested replies without making every thread explicitly about moderation.",
                3
        );
        addComment(
                umPost,
                adminViewer,
                umSecondFloor,
                now - minutes(39),
                zh
                        ? "再接一个三楼，生活类论坛也能自然出现深层讨论。"
                        : "One more third-floor reply makes deep discussion feel natural even in the more casual forum.",
                2
        );

        Post umEvents = createForumPost(
                FORUM_UM,
                quacEnjoyer,
                zh ? "本周有什么好玩的校园活动吗？" : "What's on campus this week worth going to?",
                zh
                        ? "最近几周一直宅在宿舍，想出去活动一下。有没有人知道本周有什么有意思的活动？社团的、学校的都可以。"
                        : "Been stuck in my room for weeks. Anyone know what's on this week? Club events, talks, anything beats staring at the ceiling.",
                now - minutes(78),
                19
        );
        POST_CATEGORIES.put(umEvents.id, zh ? "社交" : "Social");
        addComment(umEvents, memberViewer, null, now - minutes(70),
                zh ? "周四晚上有电影社的放映，在 Student Hub，免费的。" : "Film club screening Thursday evening at Student Hub — it's free.",
                10);
        addComment(umEvents, labPartner, null, now - minutes(63),
                zh ? "还有周五下午的语言交换活动，在 Main Library 三楼，超轻松的。" : "There's a language exchange Friday afternoon on the third floor of the Main Library. Very chill.",
                8);

        Post umStudyTips = createForumPost(
                FORUM_UM,
                treeSage,
                zh ? "在宿舍怎么保持学习效率？我总是一坐下就刷手机" : "How do you stay productive in the dorms? I always end up on my phone",
                zh
                        ? "宿舍真的太舒适了根本坐不住，图书馆关门又早。有没有在宿舍也能高效学习的方法分享一下？"
                        : "Dorm room is too comfy to study in, library closes early. How does anyone get anything done? Looking for real tactics not just 'put your phone away'.",
                now - minutes(58),
                28
        );
        POST_CATEGORIES.put(umStudyTips.id, zh ? "学习" : "Study");
        addComment(umStudyTips, anuSleepDeprived, null, now - minutes(50),
                zh ? "我把手机放到房间另一边充电，物理距离真的有用。" : "I charge my phone across the room. Physical distance actually works.",
                15);
        addComment(umStudyTips, compSurvivor, null, now - minutes(44),
                zh ? "Pomodoro 番茄工作法，25 分钟专注 + 5 分钟休息，不骗你效率起飞了。" : "Pomodoro method — 25 minutes focus, 5 minute break. Sounds dumb but my output tripled.",
                12);
        addComment(umStudyTips, deadlineVictim, null, now - minutes(38),
                zh ? "我现在学会了：把作业拆成小块，每完成一块就奖励自己看一集剧。" : "Break tasks into tiny chunks and allow one episode per chunk completed. Weirdly motivating.",
                9);

        Post umInternship = createForumPost(
                FORUM_UM,
                studyBuddy,
                zh ? "商科的实习好找吗？我投了十几家都没回音" : "Is it this hard to get a commerce internship for everyone or just me?",
                zh
                        ? "已经海投了十几家公司的实习，只有两家给面试邀请。不知道是简历问题还是现在市场真的太难了，求有经验的同学支招。"
                        : "Applied to over 15 internships and only heard back from two. Don't know if it's my resume or the market. Anyone had success recently?",
                now - minutes(36),
                25
        );
        POST_CATEGORIES.put(umInternship.id, zh ? "就业" : "Career");
        addComment(umInternship, uxPilot, null, now - minutes(28),
                zh ? "找朋友帮你改一下简历再投，我当时改了一版点击率直接翻倍。" : "Get someone to review your resume before the next batch. One revision doubled my callback rate.",
                16);
        addComment(umInternship, adminViewer, null, now - minutes(22),
                zh ? "LinkedIn 内推比直接投效果好很多，试试找 UM 校友。" : "LinkedIn referrals beat cold applications. Try searching for UM alumni at the companies you want.",
                13);

        createForumPost(
                FORUM_UM,
                lateCoder,
                zh ? "如果之后真做后端扩展，论坛字段最好进模型" : "If we extend the backend later, forum ownership should move into the model",
                zh
                        ? "现在演示层里已经能切论坛了，但如果后面要持久化或者做真实多论坛查询，帖子所属论坛最好正式进 Post 数据结构。"
                        : "The demo layer already supports forum switching, but if we later add persistence or real multi-forum queries, the forum field should live directly in the Post model.",
                now - minutes(34),
                11
        );

        Post anuLibrary = createForumPost(
                FORUM_ANU,
                labPartner,
                zh ? "Chifley 二楼哪里适合小组讨论？" : "Where is good for group discussion on Chifley level 2?",
                zh
                        ? "明天要和三个人一起过 presentation，不想打扰安静区。有没有相对不吵、又能说话的位置？"
                        : "I need to run through a presentation with three people tomorrow and don't want to annoy the quiet zone. Any good spots where talking is okay?",
                now - minutes(26),
                23
        );
        POST_CATEGORIES.put(anuLibrary.id, zh ? "学习" : "Study");
        addComment(anuLibrary, studyBuddy, null, now - minutes(22),
                zh ? "靠窗那排圆桌还不错，下午三点前通常有位置。" : "The round tables near the windows are good, usually free before 3pm.",
                10);
        Message anuLibraryReply = addComment(anuLibrary, memberViewer, null, now - minutes(20),
                zh ? "如果要录音，建议直接订 Marie Reay 的小房间。" : "If you need to record, book a small room in Marie Reay instead.",
                12);
        addComment(anuLibrary, uxPilot, anuLibraryReply, now - minutes(18),
                zh ? "对，Marie Reay 隔音明显好很多。" : "Yep, Marie Reay has much better sound isolation.",
                6);

        Post anuAiNotes = createForumPost(
                FORUM_ANU,
                treeSage,
                zh ? "用 AI 整理 lecture notes 会不会太依赖？" : "Is using AI to clean lecture notes making me too dependent?",
                zh
                        ? "我会把 lecture transcript 丢给 AI 生成重点，但担心自己只是看总结，没有真正理解。你们怎么平衡效率和学习质量？"
                        : "I paste lecture transcripts into AI to extract key points, but I'm worried I'm just reading summaries instead of actually learning. How do you balance speed and understanding?",
                now - minutes(24),
                29
        );
        POST_CATEGORIES.put(anuAiNotes.id, "AI");
        addComment(anuAiNotes, compSurvivor, null, now - minutes(19),
                zh ? "我会先自己写一版，再让 AI 找遗漏，这样不会完全被带着走。" : "I write my own notes first, then ask AI to find gaps. That keeps me from being carried by it.",
                17);
        addComment(anuAiNotes, quacEnjoyer, null, now - minutes(16),
                zh ? "让 AI 出 quiz 比让它总结更有用，答不上来就说明还没懂。" : "AI-generated quizzes are more useful than summaries. If you can't answer them, you don't understand it yet.",
                15);

        Post unswSociety = createForumPost(
                FORUM_UNSW,
                deadlineVictim,
                zh ? "社团招新摊位太多了，怎么选才不踩雷？" : "Too many society stalls — how do you pick without wasting time?",
                zh
                        ? "O-Week 逛了一圈感觉每个社团都说自己很 active。有没有判断一个社团是不是真的有活动的方法？"
                        : "Walked through O-Week and every society claims to be super active. Any way to tell which ones actually run events?",
                now - minutes(32),
                20
        );
        POST_CATEGORIES.put(unswSociety.id, zh ? "社交" : "Social");
        addComment(unswSociety, uxPilot, null, now - minutes(27),
                zh ? "看他们最近三个月 Instagram 有没有真实活动照片，比看宣传语靠谱。" : "Check whether their Instagram has real event photos from the last three months.",
                11);
        addComment(unswSociety, studyBuddy, null, now - minutes(25),
                zh ? "先加 Discord，不活跃的群一眼就看出来。" : "Join the Discord first. Dead societies are obvious immediately.",
                9);

        Post unswAiAssignment = createForumPost(
                FORUM_UNSW,
                lateCoder,
                zh ? "作业允许用 AI，但 citation 到底怎么写？" : "Assignment allows AI, but how exactly should we cite it?",
                zh
                        ? "课程说明说可以用 AI 辅助 debug 和 brainstorming，但必须 disclose。有没有人知道标准写法？"
                        : "The course says AI is allowed for debugging and brainstorming, but must be disclosed. Does anyone know the clean format?",
                now - minutes(29),
                26
        );
        POST_CATEGORIES.put(unswAiAssignment.id, "AI");
        Message unswAiReply = addComment(unswAiAssignment, adminViewer, null, now - minutes(23),
                zh ? "写清楚工具、用途、你自己修改了什么，别把 AI 输出当最终答案。" : "State the tool, what you used it for, and what you changed yourself. Don't submit raw AI output.",
                18);
        addComment(unswAiAssignment, memberViewer, unswAiReply, now - minutes(21),
                zh ? "这样写比只贴一句 ChatGPT assisted 清楚很多。" : "That's much clearer than just writing ChatGPT assisted.",
                7);

        Post usydCommute = createForumPost(
                FORUM_USYD,
                labPartner,
                zh ? "Redfern 到校这段路晚上安全吗？" : "Is the walk from Redfern to campus safe at night?",
                zh
                        ? "最近晚上课结束比较晚，想问问大家从 Redfern 走回校园感觉怎么样？有没有更推荐的路线？"
                        : "My classes finish late this semester. How does the Redfern walk feel at night, and is there a better route?",
                now - minutes(30),
                27
        );
        POST_CATEGORIES.put(usydCommute.id, zh ? "生活" : "Life");
        addComment(usydCommute, anuSleepDeprived, null, now - minutes(24),
                zh ? "主路人很多还可以，别走太偏的小巷。" : "The main road is usually fine because there are people around. Avoid the side streets.",
                13);
        addComment(usydCommute, treeSage, null, now - minutes(21),
                zh ? "晚于十点我会坐 bus 到 closer stop，省心一点。" : "After 10pm I take the bus to a closer stop. Less stress.",
                10);

        Post usydTutorial = createForumPost(
                FORUM_USYD,
                studyBuddy,
                zh ? "tutorial 不说话会影响 participation 分吗？" : "Will staying quiet in tutorials hurt participation marks?",
                zh
                        ? "我每周都有去 tutorial，也做了题，但真的不太会主动发言。老师会看出席和小组讨论，还是必须举手回答？"
                        : "I attend every tutorial and do the prep, but I don't really speak up. Do tutors count attendance and group work, or do I need to answer out loud?",
                now - minutes(27),
                24
        );
        POST_CATEGORIES.put(usydTutorial.id, zh ? "学习" : "Study");
        Message usydTutReply = addComment(usydTutorial, uxPilot, null, now - minutes(20),
                zh ? "可以课后问 tutor 一个问题，也算很好的参与。" : "Ask the tutor one question after class. That usually counts as solid participation.",
                14);
        addComment(usydTutorial, deadlineVictim, usydTutReply, now - minutes(18),
                zh ? "我也是这样，比当众发言压力小很多。" : "Same. Much less stressful than speaking in front of everyone.",
                6);

        Post umHousing = createForumPost(
                FORUM_UM,
                compSurvivor,
                zh ? "校外租房通勤多久算可以接受？" : "How long is too long for an off-campus commute?",
                zh
                        ? "看中一个房子价格还行，但到学校单程要 45 分钟。大家觉得这个距离会不会太累？"
                        : "Found a place with decent rent, but it's 45 minutes each way to campus. Is that too much long-term?",
                now - minutes(31),
                22
        );
        POST_CATEGORIES.put(umHousing.id, zh ? "生活" : "Life");
        addComment(umHousing, labPartner, null, now - minutes(26),
                zh ? "45 分钟还能接受，但最好是一趟车直达，换乘会很消耗。" : "45 minutes is okay if it's one direct trip. Transfers make it exhausting.",
                12);
        addComment(umHousing, quacEnjoyer, null, now - minutes(24),
                zh ? "算上早八和下雨天，你会更想住近一点。" : "Factor in 8am classes and rainy days. You'll want to be closer.",
                11);

        Post umPresentation = createForumPost(
                FORUM_UM,
                memberViewer,
                zh ? "presentation 开场怎么说比较自然？" : "How do you start a presentation without sounding awkward?",
                zh
                        ? "每次开头都只会说 hi everyone today we will talk about... 感觉很僵。有没有更自然的开场方式？"
                        : "Every presentation I start with hi everyone today we will talk about... and it feels stiff. Any smoother openings?",
                now - minutes(23),
                18
        );
        POST_CATEGORIES.put(umPresentation.id, zh ? "学习" : "Study");
        addComment(umPresentation, treeSage, null, now - minutes(17),
                zh ? "先抛一个问题，再说你们会回答这个问题，听众会更容易进入。" : "Open with a question, then say your talk will answer it. Easier for the audience to follow.",
                9);
        addComment(umPresentation, adminViewer, null, now - minutes(14),
                zh ? "别背太满，第一句话背熟，后面看关键词就好。" : "Don't memorize everything. Lock in the first sentence, then speak from keywords.",
                8);

        Post anuCourseSwap = createForumPost(
                FORUM_ANU,
                deadlineVictim,
                zh ? "第二周换课还来得及吗？" : "Is week 2 too late to swap courses?",
                zh
                        ? "现在这门课 workload 比想象中重很多，想换到另一门选修。但已经第二周了，担心 tutorial 和小测跟不上。有人试过这个时间点换课吗？"
                        : "This course workload is much heavier than I expected and I want to swap into another elective. It's already week 2 though, so I'm worried about tutorials and quizzes. Has anyone done this?",
                now - minutes(13),
                21
        );
        POST_CATEGORIES.put(anuCourseSwap.id, zh ? "学习" : "Study");
        Message anuSwapReply = addComment(anuCourseSwap, studyBuddy, null, now - minutes(11),
                zh ? "第二周还可以，先邮件问 course convenor 能不能补 tutorial 记录。" : "Week 2 is still okay. Email the course convenor and ask whether tutorial records can be caught up.",
                12);
        addComment(anuCourseSwap, adminViewer, anuSwapReply, now - minutes(9),
                zh ? "也记得看 census date，别拖到退课还要付费。" : "Also check the census date so you don't get charged for a course you drop.",
                8);

        Post anuLostCard = createForumPost(
                FORUM_ANU,
                anuSleepDeprived,
                zh ? "有人在 Kambri 捡到学生卡吗？" : "Has anyone found a student card around Kambri?",
                zh
                        ? "今天中午在 Kambri 吃饭之后发现学生卡不见了，可能掉在座位或者草坪附近。名字首字母 M，如果有人看到麻烦告诉我。"
                        : "Lost my student card after lunch at Kambri today, probably near the seating area or grass. Initial M. Please let me know if you spotted it.",
                now - minutes(12),
                16
        );
        POST_CATEGORIES.put(anuLostCard.id, zh ? "生活" : "Life");
        addComment(anuLostCard, labPartner, null, now - minutes(10),
                zh ? "可以问一下 Kambri information desk，学生卡经常会被送过去。" : "Check the Kambri information desk. Student cards often get handed in there.",
                9);
        addComment(anuLostCard, memberViewer, null, now - minutes(8),
                zh ? "我刚经过草坪没看到，不过帮你留意。" : "I just walked past the lawn and didn't see it, but I'll keep an eye out.",
                5);

        Post anuHackathonTeam = createForumPost(
                FORUM_ANU,
                uxPilot,
                zh ? "周末 hackathon 缺一个会做 UI 的队友" : "Weekend hackathon team needs one UI person",
                zh
                        ? "我们现在有两个后端和一个做数据的，缺一个能把 demo 页面快速做顺的人。不要求很强，能一起熬过周末就行。"
                        : "We have two backend people and one data person, but need someone who can make the demo UI feel coherent. No need to be elite, just willing to build through the weekend.",
                now - minutes(10),
                25
        );
        POST_CATEGORIES.put(anuHackathonTeam.id, zh ? "社交" : "Social");
        addComment(anuHackathonTeam, treeSage, null, now - minutes(7),
                zh ? "我可以帮一点设计系统和文案，但周日晚上前要留时间复习。" : "I can help with design system and copy, but need Sunday night free for revision.",
                11);
        addComment(anuHackathonTeam, quacEnjoyer, null, now - minutes(6),
                zh ? "如果需要 pitch deck 我也能补几页。" : "I can also help with a few pitch deck slides if needed.",
                7);

        Post unswLibrarySeats = createForumPost(
                FORUM_UNSW,
                labPartner,
                zh ? "Main Library 早上几点去才有座？" : "What time do you need to reach Main Library to get a seat?",
                zh
                        ? "这周每次十点到都已经很满了。想问 exam season 期间大家一般几点去占座？有没有不那么卷的楼层？"
                        : "Every time I arrive at 10 this week it's already packed. During exam season, when do people usually get seats? Any less intense floors?",
                now - minutes(12),
                23
        );
        POST_CATEGORIES.put(unswLibrarySeats.id, zh ? "期末" : "Finals");
        addComment(unswLibrarySeats, compSurvivor, null, now - minutes(9),
                zh ? "八点半前基本稳，十点之后只能靠运气。" : "Before 8:30 is pretty safe. After 10 it's pure luck.",
                13);
        addComment(unswLibrarySeats, deadlineVictim, null, now - minutes(7),
                zh ? "Law Library 人少一点，但插座位置不多。" : "Law Library is quieter, but there aren't many power outlets.",
                8);

        Post unswGroupProject = createForumPost(
                FORUM_UNSW,
                quacEnjoyer,
                zh ? "小组作业有人完全不回消息怎么办？" : "What do you do when a group member never replies?",
                zh
                        ? "我们小组有一个人已经三天没回 Discord 了，deadline 又快到了。现在直接和 tutor 说会不会显得太早？"
                        : "One person in our group hasn't replied on Discord for three days and the deadline is close. Is it too early to tell the tutor?",
                now - minutes(11),
                27
        );
        POST_CATEGORIES.put(unswGroupProject.id, zh ? "学习" : "Study");
        Message unswGroupReply = addComment(unswGroupProject, adminViewer, null, now - minutes(8),
                zh ? "先在群里明确分工和截止时间，截图保存，再邮件 tutor。" : "Post clear tasks and deadlines in the group chat, keep screenshots, then email the tutor.",
                16);
        addComment(unswGroupProject, uxPilot, unswGroupReply, now - minutes(6),
                zh ? "对，证据越清楚 tutor 越容易处理。" : "Exactly. Clear evidence makes it much easier for the tutor to handle.",
                6);

        Post unswCheapLunch = createForumPost(
                FORUM_UNSW,
                memberViewer,
                zh ? "校园附近 $10 以下午饭还有吗？" : "Does sub-$10 lunch still exist near campus?",
                zh
                        ? "最近感觉吃饭越来越贵，想找一些不用走太远、十刀以内能吃饱的选择。欢迎推荐隐藏菜单。"
                        : "Food keeps getting more expensive. Looking for places within walking distance where you can still get full under $10. Hidden menu tips welcome.",
                now - minutes(9),
                19
        );
        POST_CATEGORIES.put(unswCheapLunch.id, zh ? "生活" : "Life");
        addComment(unswCheapLunch, studyBuddy, null, now - minutes(6),
                zh ? "Mathews 附近有家饭团，$8.5 一个很顶。" : "There's an onigiri place near Mathews. $8.50 and surprisingly filling.",
                10);
        addComment(unswCheapLunch, anuSleepDeprived, null, now - minutes(5),
                zh ? "Aldi 面包加 tuna 是我的贫穷套餐。" : "Aldi bread plus tuna is my budget survival combo.",
                9);

        Post usydClubFair = createForumPost(
                FORUM_USYD,
                lateCoder,
                zh ? "社团 fair 哪些摊位值得排队？" : "Which club fair stalls are actually worth lining up for?",
                zh
                        ? "排了二十分钟只拿到一张 flyer 有点心碎。有没有活动多、认识人也容易的社团推荐？"
                        : "Queued twenty minutes and only got a flyer, mildly tragic. Any societies with real events and easy ways to meet people?",
                now - minutes(12),
                22
        );
        POST_CATEGORIES.put(usydClubFair.id, zh ? "社交" : "Social");
        addComment(usydClubFair, treeSage, null, now - minutes(9),
                zh ? "摄影社不错，活动频率高，新人也容易加入。" : "Photography society is good: frequent events and beginner-friendly.",
                12);
        addComment(usydClubFair, memberViewer, null, now - minutes(7),
                zh ? "语言交换社很轻松，第一次去也不会尴尬。" : "Language exchange society is very chill, not awkward even on the first visit.",
                8);

        Post usydAiPolicy = createForumPost(
                FORUM_USYD,
                adminViewer,
                zh ? "USYD 的 AI disclosure 现在到底多严格？" : "How strict is USYD's AI disclosure now?",
                zh
                        ? "有的课说可以用，有的课又写得很模糊。大家有没有见过比较清楚的 disclosure 模板？"
                        : "Some courses allow it, some write it vaguely. Has anyone seen a clear disclosure template that tutors actually accept?",
                now - minutes(10),
                28
        );
        POST_CATEGORIES.put(usydAiPolicy.id, "AI");
        addComment(usydAiPolicy, studyBuddy, null, now - minutes(8),
                zh ? "我一般写工具名、日期、用途，再说明最终内容由自己改写。" : "I list the tool, date, purpose, and state that the final content was rewritten by me.",
                15);
        Message usydAiPolicyReply = addComment(usydAiPolicy, compSurvivor, null, now - minutes(6),
                zh ? "最好别把 prompt 全贴上去，太占篇幅了。" : "I wouldn't paste the full prompts unless they ask. It takes too much space.",
                9);
        addComment(usydAiPolicy, uxPilot, usydAiPolicyReply, now - minutes(5),
                zh ? "摘要式 disclosure 看起来更专业。" : "A concise disclosure looks more professional.",
                5);

        Post usydMorningClass = createForumPost(
                FORUM_USYD,
                deadlineVictim,
                zh ? "早八课怎么保持清醒？" : "How do you stay awake in 8am lectures?",
                zh
                        ? "我已经连续两周在 lecture 中间断片了。咖啡也救不了，求一些现实可行的方法。"
                        : "I've zoned out halfway through lecture two weeks in a row. Coffee isn't saving me. Any realistic tips?",
                now - minutes(8),
                20
        );
        POST_CATEGORIES.put(usydMorningClass.id, zh ? "生活" : "Life");
        addComment(usydMorningClass, labPartner, null, now - minutes(6),
                zh ? "前一天晚上把早餐准备好，早上少做一个决定会轻松很多。" : "Prep breakfast the night before. One less decision in the morning helps a lot.",
                11);
        addComment(usydMorningClass, quacEnjoyer, null, now - minutes(4),
                zh ? "坐前排，困了也不好意思睡。" : "Sit in the front row. Shame is surprisingly effective.",
                13);

        Post umClubSports = createForumPost(
                FORUM_UM,
                labPartner,
                zh ? "想参加运动社团但完全没基础可以吗？" : "Can total beginners join sports clubs?",
                zh
                        ? "想试试羽毛球或者攀岩，但之前几乎没玩过。社团会不会默认大家都已经很会？"
                        : "Thinking of trying badminton or climbing, but I've barely done either. Do clubs expect you to already be good?",
                now - minutes(12),
                21
        );
        POST_CATEGORIES.put(umClubSports.id, zh ? "社交" : "Social");
        addComment(umClubSports, memberViewer, null, now - minutes(9),
                zh ? "羽毛球社有 beginner night，很友好。" : "Badminton club has beginner nights and they're very friendly.",
                12);
        addComment(umClubSports, uxPilot, null, now - minutes(7),
                zh ? "攀岩第一次去会教安全动作，不用担心。" : "Climbing teaches safety basics on your first visit. Don't worry.",
                9);

        Post umExamPlan = createForumPost(
                FORUM_UM,
                anuSleepDeprived,
                zh ? "三门期末挤在四天内怎么排复习？" : "Three finals in four days — how would you plan revision?",
                zh
                        ? "时间表刚出，三门课几乎挤在一起。现在有点不知道先复习哪门，怕每门都只看一半。"
                        : "Timetable just dropped and three exams are basically stacked together. Not sure what to revise first without half-studying everything.",
                now - minutes(10),
                26
        );
        POST_CATEGORIES.put(umExamPlan.id, zh ? "期末" : "Finals");
        Message umExamReply = addComment(umExamPlan, treeSage, null, now - minutes(8),
                zh ? "先按分数权重和薄弱程度排，不要平均分时间。" : "Rank by weighting and weakness. Don't split time evenly.",
                15);
        addComment(umExamPlan, adminViewer, umExamReply, now - minutes(6),
                zh ? "每天最后留半小时复盘错题，避免重复犯错。" : "Reserve 30 minutes daily to review mistakes so you don't repeat them.",
                8);

        Post umCampusApp = createForumPost(
                FORUM_UM,
                lateCoder,
                zh ? "学校 app 的课表提醒总是晚五分钟" : "Campus app timetable alerts are always five minutes late",
                zh
                        ? "已经两次因为提醒延迟差点迟到。有没有办法同步到 Google Calendar，或者大家都用什么替代方案？"
                        : "Twice now the alert came late enough that I nearly missed class. Is there a way to sync it to Google Calendar, or what do people use instead?",
                now - minutes(8),
                17
        );
        POST_CATEGORIES.put(umCampusApp.id, "AI");
        addComment(umCampusApp, compSurvivor, null, now - minutes(5),
                zh ? "我手动导入 Google Calendar，再提前十分钟提醒。" : "I manually add classes to Google Calendar with a 10-minute alert.",
                10);
        addComment(umCampusApp, studyBuddy, null, now - minutes(3),
                zh ? "如果能做自动同步，这其实是个很好的小项目。" : "Automatic sync would actually be a great small project.",
                7);

        Post anuGym = createForumPost(
                FORUM_ANU,
                studyBuddy,
                zh ? "ANU gym 晚上几点之后人少一点？" : "When does the ANU gym get quieter at night?",
                zh
                        ? "下午六点去完全排不到器械。想找一个不那么挤、又不会太晚回家的时间段。"
                        : "Went at 6pm and every machine had a queue. Looking for a quieter window that still doesn't mean getting home too late.",
                now - minutes(7),
                18
        );
        POST_CATEGORIES.put(anuGym.id, zh ? "生活" : "Life");
        addComment(anuGym, labPartner, null, now - minutes(5),
                zh ? "八点半之后明显好很多，力量区会空下来。" : "After 8:30 is much better. The weights area opens up.",
                9);
        addComment(anuGym, deadlineVictim, null, now - minutes(4),
                zh ? "早上七点也不错，但前提是你起得来。" : "7am is also good, assuming you can actually wake up.",
                8);

        Post anuScholarship = createForumPost(
                FORUM_ANU,
                quacEnjoyer,
                zh ? "奖学金申请里的 personal statement 怎么写？" : "How do you write a scholarship personal statement?",
                zh
                        ? "要求 500 字以内，但我不知道该写成绩、社团还是家庭背景。有没有比较清晰的结构？"
                        : "The limit is 500 words and I don't know whether to focus on grades, societies, or background. Any structure that works?",
                now - minutes(6),
                22
        );
        POST_CATEGORIES.put(anuScholarship.id, zh ? "学习" : "Study");
        addComment(anuScholarship, adminViewer, null, now - minutes(4),
                zh ? "先写目标，再写证据，最后写这笔钱会具体改变什么。" : "Start with your goal, then evidence, then what the money would concretely change.",
                14);
        addComment(anuScholarship, uxPilot, null, now - minutes(3),
                zh ? "别堆经历，选两件最能证明你的事讲清楚。" : "Don't list everything. Pick two moments that prove the point.",
                11);

        Post unswTransport = createForumPost(
                FORUM_UNSW,
                compSurvivor,
                zh ? "light rail 迟到会影响早课吗？" : "Is light rail delay a real risk for morning classes?",
                zh
                        ? "准备搬到 Randwick 附近，但听说早上 light rail 有时候很慢。大家会提前多久出门？"
                        : "Thinking of moving near Randwick, but heard the light rail can crawl in the morning. How early do people leave?",
                now - minutes(7),
                17
        );
        POST_CATEGORIES.put(unswTransport.id, zh ? "生活" : "Life");
        addComment(unswTransport, treeSage, null, now - minutes(5),
                zh ? "如果是九点课，至少多留十五分钟缓冲。" : "For a 9am class, leave at least a 15-minute buffer.",
                8);
        addComment(unswTransport, memberViewer, null, now - minutes(4),
                zh ? "雨天一定更早，排队会很夸张。" : "On rainy days go earlier. The queues get dramatic.",
                7);

        Post unswResume = createForumPost(
                FORUM_UNSW,
                uxPilot,
                zh ? "career fair 简历要不要带纸质版？" : "Should I bring printed resumes to career fair?",
                zh
                        ? "感觉大家都扫码投递了，但又怕现场聊天时手里什么都没有显得不专业。"
                        : "Everyone seems to apply through QR codes now, but I worry showing up empty-handed looks unprepared.",
                now - minutes(6),
                24
        );
        POST_CATEGORIES.put(unswResume.id, zh ? "社交" : "Social");
        addComment(unswResume, studyBuddy, null, now - minutes(4),
                zh ? "带五份就够，更多时候是为了自己安心。" : "Bring five copies. Mostly it's for your own confidence.",
                12);
        addComment(unswResume, adminViewer, null, now - minutes(3),
                zh ? "重点是准备两个具体问题，比纸质简历更有用。" : "Two specific questions matter more than the paper resume.",
                15);

        Post usydLibraryNoise = createForumPost(
                FORUM_USYD,
                quacEnjoyer,
                zh ? "Fisher Library 哪层最安静？" : "Which Fisher Library level is the quietest?",
                zh
                        ? "想找一个能连续写 essay 的地方，不想一直被小组讨论打断。"
                        : "Need somewhere to write an essay for a few hours without group chatter cutting through every ten minutes.",
                now - minutes(7),
                19
        );
        POST_CATEGORIES.put(usydLibraryNoise.id, zh ? "学习" : "Study");
        addComment(usydLibraryNoise, lateCoder, null, now - minutes(5),
                zh ? "高层安静很多，但下午位置会很快满。" : "Upper levels are much quieter, but seats go fast after lunch.",
                10);
        addComment(usydLibraryNoise, labPartner, null, now - minutes(4),
                zh ? "戴降噪耳机去 Law Library 也很稳。" : "Noise-cancelling headphones plus Law Library is a reliable combo.",
                9);

        Post usydRoommate = createForumPost(
                FORUM_USYD,
                anuSleepDeprived,
                zh ? "室友总是凌晨做饭怎么沟通？" : "How do you talk to a roommate who cooks at 1am?",
                zh
                        ? "不是想吵架，但每天锅铲声真的很崩溃。怎么开口比较不尴尬？"
                        : "I don't want a fight, but the pan noise every night is wrecking my sleep. How do I raise it without making things weird?",
                now - minutes(6),
                21
        );
        POST_CATEGORIES.put(usydRoommate.id, zh ? "生活" : "Life");
        addComment(usydRoommate, treeSage, null, now - minutes(4),
                zh ? "先讲你的作息受影响，再一起定 quiet hours。" : "Frame it around your sleep, then agree on quiet hours together.",
                12);
        addComment(usydRoommate, deadlineVictim, null, now - minutes(3),
                zh ? "可以先发消息约个白天聊，别在凌晨当场爆炸。" : "Text to talk during the day. Don't explode at 1am.",
                13);

        Post umPrinting = createForumPost(
                FORUM_UM,
                deadlineVictim,
                zh ? "哪里打印 lecture slides 最便宜？" : "Where is the cheapest place to print lecture slides?",
                zh
                        ? "有几门课还是看纸质更舒服，但学校打印好像有点贵。大家有什么省钱办法？"
                        : "Some classes are easier on paper, but campus printing feels expensive. Any cheap workflow?",
                now - minutes(7),
                16
        );
        POST_CATEGORIES.put(umPrinting.id, zh ? "学习" : "Study");
        addComment(umPrinting, compSurvivor, null, now - minutes(5),
                zh ? "双面四页一张能省很多，字还看得清。" : "Double-sided, four slides per page saves a lot and stays readable.",
                8);
        addComment(umPrinting, memberViewer, null, now - minutes(4),
                zh ? "只打印 tutorial 题，lecture notes 用平板标注。" : "Print tutorial questions only, annotate lecture notes digitally.",
                7);

        Post umAiClub = createForumPost(
                FORUM_UM,
                uxPilot,
                zh ? "有没有想一起做 AI 小项目的同学？" : "Anyone want to build a small AI side project?",
                zh
                        ? "想做一个能整理课程讨论和待办的小工具，不一定要很正式，主要练手和做 portfolio。"
                        : "I want to build a small tool that organizes course discussions and todos. Nothing too formal, mostly practice and portfolio.",
                now - minutes(6),
                23
        );
        POST_CATEGORIES.put(umAiClub.id, "AI");
        addComment(umAiClub, studyBuddy, null, now - minutes(4),
                zh ? "我可以负责用户流程和测试，代码也能帮一点。" : "I can help with user flows and testing, plus a bit of code.",
                11);
        addComment(umAiClub, lateCoder, null, now - minutes(3),
                zh ? "如果用 Firebase 原型会很快。" : "Firebase would make the prototype pretty quick.",
                10);
    }

    private static String drawableImageUri(int drawableResId) {
        return "android.resource://com.example.myapplication/" + drawableResId;
    }

    private static void seedModerationState() {
        long now = System.currentTimeMillis();

        ModerationTools.addReport(hiddenExampleMessage.id(), memberViewer.id(), now - minutes(72));
        ModerationTools.addReport(hiddenExampleMessage.id(), studyBuddy.id(), now - minutes(71));
        ModerationTools.setHidden(hiddenExampleMessage.id(), adminViewer.id(), true);

        ModerationTools.addReport(queueExampleMessage.id(), uxPilot.id(), now - minutes(55));
        ModerationTools.addReport(queueExampleMessage.id(), treeSage.id(), now - minutes(53));
    }

    /**
     * Counts seeded content so each item gets a stable name.
     *
     * <p>Both languages run the identical sequence of seed calls — only the
     * strings passed in differ — so the n-th post is the same post in either
     * language and can safely share an id.
     */
    private static int seedSequence;

    /**
     * A repeatable id for seeded content.
     *
     * <p>These used to be {@code UUID.randomUUID()}, which meant the demo feed
     * was a different set of ids after every restart and after every language
     * switch. Anything holding an id across that boundary — the moderation
     * backend's record of which local comment it mirrored — was pointing at
     * content that no longer existed, so a reported comment could not be matched
     * back to the feed and the review queue fell back to showing the copy stored
     * on the server, in whatever language it was mirrored in.
     */
    private static UUID stableId(String kind, String key) {
        return UUID.nameUUIDFromBytes(("de:" + kind + ":" + key).getBytes(StandardCharsets.UTF_8));
    }

    private static User addUser(String username, User.Role role) {
        // Usernames are identifiers rather than display names, so they are the
        // same in both languages and make a good key.
        User user = new User(stableId("user", username), role, username, "demo1234");
        UserDAO.getInstance().add(user);
        return user;
    }

    private static Post createForumPost(
            String forumKey,
            User poster,
            String topic,
            String body,
            long timestamp,
            int voteScore
    ) {
        // Keyed on position rather than on the title, which is translated.
        Post post = new Post(stableId("post", forumKey + ":" + (++seedSequence)), poster.id(), topic);
        Message rootMessage = new Message(
                stableId("message", post.id + ":root"), poster.id(), post.id, timestamp, body);
        post.messages.insert(rootMessage);
        PostDAO.getInstance().add(post);

        POST_META.put(post.id, new PostMeta(forumKey, rootMessage.id()));
        MESSAGE_META.put(rootMessage.id(), new MessageMeta(null, true));
        seedVote(TargetType.POST, post.id, voteScore);
        BASE_BOOKMARKS.put(post.id, Math.max(0, Math.round(voteScore / 8.0f)));
        return post;
    }

    private static Message addComment(
            Post post,
            User poster,
            Message parent,
            long timestamp,
            String content,
            int voteScore
    ) {
        Message message = new Message(
                stableId("message", post.id + ":" + (++seedSequence)),
                poster.id(), post.id, timestamp, content);
        post.messages.insert(message);
        UUID rootId = getRootMessageId(post);
        UUID parentId = parent == null ? rootId : parent.id();
        MESSAGE_META.put(message.id(), new MessageMeta(parentId, false));
        seedVote(TargetType.MESSAGE, message.id(), voteScore);
        return message;
    }

    private static void seedVote(TargetType targetType, UUID targetId, int score) {
        if (targetId != null) {
            BASE_VOTES.put(new VoteTarget(targetType, targetId), score);
        }
    }

    private static void pinPostToTop(Post post, int rank) {
        if (post != null) {
            POST_TOP_RANKS.put(post.id, rank);
        }
    }

    private static ArrayList<Message> getAllMessages() {
        ArrayList<Message> messages = new ArrayList<>();
        Iterator<Message> iterator = PostDAO.getInstance().getAllMessages();
        while (iterator.hasNext()) {
            messages.add(iterator.next());
        }
        return messages;
    }

    private static ArrayList<Message> collectMessages(Iterator<Message> iterator) {
        ArrayList<Message> messages = new ArrayList<>();
        while (iterator.hasNext()) {
            Message message = iterator.next();
            if (!REMOVED_REMOTE_COMMENTS.contains(message.id())) messages.add(message);
        }
        return messages;
    }

    private static void appendThread(
            Message message,
            Map<UUID, ArrayList<Message>> groupedReplies,
            ArrayList<Message> orderedMessages
    ) {
        orderedMessages.add(message);
        ArrayList<Message> children = groupedReplies.get(message.id());
        if (children == null) {
            return;
        }
        for (Message child : children) {
            appendThread(child, groupedReplies, orderedMessages);
        }
    }

    private static void appendXhsThread(
            Message topLevelMessage,
            Map<UUID, ArrayList<Message>> groupedReplies,
            ArrayList<Message> orderedMessages,
            Set<UUID> expandedTopLevelCommentIds
    ) {
        orderedMessages.add(topLevelMessage);
        ArrayList<Message> replies = new ArrayList<>();
        appendFlatReplies(topLevelMessage, groupedReplies, replies);
        int visibleReplyCount = expandedTopLevelCommentIds.contains(topLevelMessage.id())
                ? replies.size()
                : Math.min(1, replies.size());
        for (int i = 0; i < visibleReplyCount; i++) {
            orderedMessages.add(replies.get(i));
        }
    }

    private static void appendFlatReplies(
            Message parent,
            Map<UUID, ArrayList<Message>> groupedReplies,
            ArrayList<Message> replies
    ) {
        ArrayList<Message> children = groupedReplies.get(parent.id());
        if (children == null) {
            return;
        }
        for (Message child : children) {
            replies.add(child);
            appendFlatReplies(child, groupedReplies, replies);
        }
    }

    private static Map<UUID, ArrayList<Message>> buildVisibleReplyGroups(Post post) {
        Map<UUID, ArrayList<Message>> groupedReplies = new HashMap<>();
        if (post == null) {
            return groupedReplies;
        }

        ArrayList<Message> visibleMessages = collectMessages(adminMode
                ? post.messages.getAll()
                : post.getVisibleMessages(false).getAll());
        Set<UUID> visibleIds = new HashSet<>();
        for (Message message : visibleMessages) {
            visibleIds.add(message.id());
        }
        UUID rootId = getRootMessageId(post);
        for (Message message : visibleMessages) {
            if (isRootMessage(message)) {
                continue;
            }
            UUID parentId = getParentId(message);
            if (parentId == null || parentId.equals(rootId) || !visibleIds.contains(parentId)) {
                continue;
            }
            groupedReplies.computeIfAbsent(parentId, unused -> new ArrayList<>()).add(message);
        }
        for (ArrayList<Message> children : groupedReplies.values()) {
            sortMessagesByTimestamp(children);
        }
        return groupedReplies;
    }

    private static Message getTopLevelComment(Message message) {
        if (message == null || isRootMessage(message)) {
            return null;
        }
        Post post = getPostForMessage(message);
        UUID rootId = getRootMessageId(post);
        Message current = message;
        UUID parentId = getParentId(current);
        while (parentId != null && !parentId.equals(rootId)) {
            Message parent = findMessage(post, parentId);
            if (parent == null) {
                break;
            }
            current = parent;
            parentId = getParentId(current);
        }
        return current;
    }

    private static Message getParentMessage(Message message) {
        if (message == null) {
            return null;
        }
        Post post = getPostForMessage(message);
        return findMessage(post, getParentId(message));
    }

    private static String getStringForNotification(Context context, int resId, Object... args) {
        return context.getString(resId, args);
    }

    private static boolean matchesPostSearch(Context context, Post post, String normalizedQuery) {
        if (post == null) {
            return false;
        }
        if (containsIgnoreCase(getPostTitle(post), normalizedQuery)
                || containsIgnoreCase(getPostBody(post), normalizedQuery)
                || containsIgnoreCase(getUsername(post.poster), normalizedQuery)
                || containsIgnoreCase(getPostCategory(context, post), normalizedQuery)
                || containsIgnoreCase(getPostCommunityLabel(context, post), normalizedQuery)) {
            return true;
        }

        ArrayList<Message> visibleMessages = collectMessages(adminMode
                ? post.messages.getAll()
                : post.getVisibleMessages(false).getAll());
        for (Message message : visibleMessages) {
            if (!isRootMessage(message) && containsIgnoreCase(message.message(), normalizedQuery)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String value, String normalizedQuery) {
        return value != null && value.toLowerCase().contains(normalizedQuery);
    }

    private static void sortMessagesByTimestamp(ArrayList<Message> messages) {
        messages.sort(Comparator.comparingLong(Message::timestamp));
    }

    private static long getLatestActivityTimestamp(Post post) {
        ArrayList<Message> messages = collectMessages(post.messages.getAll());
        if (messages.isEmpty()) {
            return Long.MIN_VALUE;
        }
        return Collections.max(messages, Comparator.comparingLong(Message::timestamp)).timestamp();
    }

    private static int getTopRank(Post post) {
        Integer rank = post == null ? null : POST_TOP_RANKS.get(post.id);
        return rank == null ? Integer.MAX_VALUE : rank;
    }

    private static long minutes(long amount) {
        return amount * 60_000L;
    }

    private static boolean toggleVote(TargetType targetType, UUID targetId, int direction) {
        if (onlineMode) { if (targetId == null || (direction != 1 && direction != -1)) return false; return ServerFeatures.vote(targetType == TargetType.POST ? "posts" : "comments", targetId, getCurrentUserVote(targetType, targetId) == direction ? 0 : direction); }
        ensurePopulated();
        User user = getCurrentUser();
        if (user == null || targetId == null || (direction != 1 && direction != -1)) {
            return false;
        }

        UserVoteKey key = new UserVoteKey(targetType, targetId, user.id());
        int currentDirection = USER_VOTES.getOrDefault(key, 0);
        if (currentDirection == direction) {
            USER_VOTES.remove(key);
            return true;
        }
        USER_VOTES.put(key, direction);
        return true;
    }

    private static boolean mentionsUser(String content, User user) {
        return content != null
                && user != null
                && content.toLowerCase(Locale.ROOT).contains("@" + user.username().toLowerCase(Locale.ROOT));
    }

    private static List<String> defaultCategories(Context context) {
        boolean zh = context == null ? contextLanguageIsChinese()
                : context.getResources().getConfiguration().getLocales().get(0).getLanguage().startsWith("zh");
        return Arrays.asList(
                zh ? "学习" : "Study",
                zh ? "社交" : "Social",
                zh ? "期末" : "Finals",
                "AI"
        );
    }

    private static int getVoteScore(TargetType targetType, UUID targetId) {
        if (onlineMode) return targetId == null ? 0 : ServerFeatures.state(targetType == TargetType.POST ? "posts" : "comments", targetId).optInt("score");
        if (targetId == null) {
            return 0;
        }
        int total = BASE_VOTES.getOrDefault(new VoteTarget(targetType, targetId), 0);
        for (Map.Entry<UserVoteKey, Integer> entry : USER_VOTES.entrySet()) {
            if (entry.getKey().targetType == targetType && targetId.equals(entry.getKey().targetId)) {
                total += entry.getValue();
            }
        }
        return total;
    }

    private static int getCurrentUserVote(TargetType targetType, UUID targetId) {
        if (onlineMode) return targetId == null ? 0 : ServerFeatures.state(targetType == TargetType.POST ? "posts" : "comments", targetId).optInt("vote");
        User user = getCurrentUser();
        if (user == null || targetId == null) {
            return 0;
        }
        return USER_VOTES.getOrDefault(new UserVoteKey(targetType, targetId, user.id()), 0);
    }

    private static String joinWithBullets(ArrayList<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(" • ");
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    private static String getForumKey(Post post) {
        PostMeta meta = post == null ? null : POST_META.get(post.id);
        return meta == null ? FORUM_ANU : meta.forumKey;
    }

    private static UUID getRootMessageId(Post post) {
        PostMeta meta = post == null ? null : POST_META.get(post.id);
        return meta == null ? null : meta.rootMessageId;
    }

    private static UUID getParentId(Message message) {
        MessageMeta meta = message == null ? null : MESSAGE_META.get(message.id());
        return meta == null ? null : meta.parentId;
    }

    private static boolean isRootMessage(Message message) {
        MessageMeta meta = message == null ? null : MESSAGE_META.get(message.id());
        return meta != null && meta.rootBody;
    }

    private static boolean isRootHidden(Post post) {
        Message rootMessage = getRootMessage(post);
        return rootMessage != null && ModerationTools.isHidden(rootMessage.id());
    }

    private static Message findMessage(Post post, UUID messageId) {
        if (post == null || messageId == null) {
            return null;
        }
        Iterator<Message> iterator = post.messages.getAll();
        while (iterator.hasNext()) {
            Message message = iterator.next();
            if (message.id().equals(messageId)) {
                return message;
            }
        }
        return null;
    }

    private static String ellipsize(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength - 3).trim() + "...";
    }

    private static boolean contextLanguageIsChinese() {
        return currentLanguageTag().startsWith("zh");
    }

    private static String currentLanguageTag() {
        return java.util.Locale.getDefault().toLanguageTag();
    }

    private enum TargetType {
        POST,
        MESSAGE
    }

    private record PostMeta(String forumKey, UUID rootMessageId) {
    }

    private record MessageMeta(UUID parentId, boolean rootBody) {
    }

    private record VoteTarget(TargetType targetType, UUID targetId) {
    }

    private record UserVoteKey(TargetType targetType, UUID targetId, UUID userId) {
    }

    public enum NotificationType {
        ALL,
        LIKE,
        BOOKMARK,
        COMMENT,
        MENTION,
        FOLLOW,
        MODERATION,
        MODERATION_APPEALABLE,
        APPEAL
    }

    public record AppNotification(
            NotificationType type,
            UUID recipientId,
            UUID actorId,
            UUID postId,
            UUID messageId,
            String title,
            String body,
            long timestamp,
            UUID remoteNotificationId,
            String referenceType,
            UUID referenceId,
            boolean read
    ) {
        public AppNotification(
                NotificationType type,
                UUID recipientId,
                UUID actorId,
                UUID postId,
                UUID messageId,
                String title,
                String body,
                long timestamp) {
            this(type, recipientId, actorId, postId, messageId, title, body, timestamp,
                    null, null, null, true);
        }
    }
}
