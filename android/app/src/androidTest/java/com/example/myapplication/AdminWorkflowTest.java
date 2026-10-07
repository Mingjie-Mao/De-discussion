package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.espresso.NoMatchingViewException;
import org.junit.*;
import org.junit.runner.RunWith;
import org.json.*;
import backend.BackendRuntime;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.UUID;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/** Device-level integration using isolated HTTP fixtures, never production content. */
@RunWith(AndroidJUnit4.class)
public class AdminWorkflowTest {
    private static final String ADMIN = "11111111-1111-1111-1111-111111111111";
    private static final String MEMBER = "55555555-5555-5555-5555-555555555555";
    private static final String POST = "66666666-6666-6666-6666-666666666666";
    private static final String COMMENT = "77777777-7777-7777-7777-777777777777";
    private static final String CASE = "22222222-2222-2222-2222-222222222222";
    private static final String APPEAL = "33333333-3333-3333-3333-333333333333";
    private Context context;
    private ServerSocket server;
    private ExecutorService pool;
    private volatile boolean running;
    private volatile String user = "admin";
    private volatile String caseStatus = "AWAITING_REVIEW", finalAction = "", assigned = "", appealStatus = "PENDING";
    private volatile String lastNote = "", lastResponse = "";
    private String oldUrl;
    private boolean oldEnabled;
    private volatile boolean failFeed, failNotifications;
    private final java.util.concurrent.atomic.AtomicInteger evidenceTranslationReads=new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger imageReads=new java.util.concurrent.atomic.AtomicInteger();
    private volatile int socialVote, marketCash=1000, marketUnits, marketWrites;
    private volatile boolean socialBookmark, socialFollow, dropFirstReceipt, expiredSession;
    private volatile int firstReceiptStatus;
    private volatile int fixturePrice=100;
    private final java.util.Map<String,JSONObject> tradeReceipts=new java.util.concurrent.ConcurrentHashMap<>();
    private volatile JSONObject savedPost, savedComment;
    private volatile boolean registered, deleted;
    private volatile String profileLanguage="en", profileTheme="light";
    private volatile int profileColor;
    private volatile String contentToken = "", reportedTarget = "";
    private volatile int postWrites;
    private volatile CountDownLatch voteStarted, releaseVote;
    private volatile CountDownLatch stateStarted, releaseState;
    private volatile boolean failVote;
    private final java.util.List<String> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile CountDownLatch feedStarted, releaseFeed;
    private volatile CountDownLatch readinessStarted, releaseReadiness;
    private final java.util.concurrent.atomic.AtomicInteger readinessFailures = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.List<String> feedForums = new java.util.concurrent.CopyOnWriteArrayList<>();
    @Before public void start() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        BackendRuntime runtime = BackendRuntime.from(context);
        oldUrl = runtime.config().baseUrl(); oldEnabled = runtime.config().isEnabled();
        UiPreferences.clearLoginSession(context);
        server = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
        pool = Executors.newCachedThreadPool(); running = true;
        runtime.config().setBaseUrl("http://127.0.0.1:" + server.getLocalPort());
        runtime.config().setEnabled(true);
        pool.execute(() -> {
            while (running) try { Socket socket = server.accept(); pool.execute(() -> serve(socket)); }
            catch (IOException | RejectedExecutionException ignored) { return; }
        });
    }
    @After public void stop() throws Exception {
        running = false; server.close(); pool.shutdownNow();
        UiPreferences.clearLoginSession(context);
        BackendRuntime.from(context).config().setBaseUrl(oldUrl);
        BackendRuntime.from(context).config().setEnabled(oldEnabled);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (androidx.test.runner.lifecycle.Stage stage : new androidx.test.runner.lifecycle.Stage[]{androidx.test.runner.lifecycle.Stage.RESUMED, androidx.test.runner.lifecycle.Stage.STARTED})
                for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage)) activity.finish();
        });
    }
    @Test public void chineseReviewQueueTranslatesSnapshotsAndCanShowOriginal() throws Exception {
        profileLanguage="zh-CN";
        var runtime=BackendRuntime.from(context);
        runtime.admin().session().login("admin","fixture");
        UiPreferences.applyServerProfile(context,runtime.admin().session().profile());
        UiPreferences.setLoginSession(context,true);
        try(var queue=ActivityScenario.launch(AdminReviewActivity.class)) {
            await(()->onView(withText("举报时标题")).check(matches(isDisplayed())));
            onView(withText("原始举报内容")).check(matches(isDisplayed()));
            assertEquals(2,evidenceTranslationReads.get());
            onView(allOf(isAssignableFrom(android.widget.Button.class),withText(containsString("查看原文")))).perform(click());
            onView(withText("Reported snapshot")).check(matches(isDisplayed()));
            onView(withText("Original reported text")).check(matches(isDisplayed()));
            onView(withText("显示译文")).perform(click());
            onView(withText("举报时标题")).check(matches(isDisplayed()));
            UiPreferences.setLanguageTag(context,"en");queue.recreate();
            await(()->onView(withText("Reported snapshot")).check(matches(isDisplayed())));
            assertEquals(2,evidenceTranslationReads.get());
        } finally { UiPreferences.setLanguageTag(context,"en");UiPreferences.applyAppearance(context); }
    }

    @Test public void sleepingServerBecomesReadyBeforeCredentialsAreSubmitted() throws Exception {
        readinessFailures.set(1);
        readinessStarted = new CountDownLatch(1); releaseReadiness = new CountDownLatch(1);
        try (ActivityScenario<LoginActivity> login = ActivityScenario.launch(LoginActivity.class)) {
            signIn("admin");
            assertTrue(readinessStarted.await(3, TimeUnit.SECONDS));
            onView(withId(R.id.textLoginHint)).check(matches(withText(context.getString(R.string.login_server_wait, 0L))));
            assertFalse(requests.contains("POST /api/auth/login"));
            releaseReadiness.countDown();
            await(() -> assertTrue(UiPreferences.isAdminSession(context)));
            assertEquals(2, java.util.Collections.frequency(requests, "GET /actuator/health/readiness"));
            assertEquals(1, java.util.Collections.frequency(requests, "POST /api/auth/login"));
        } finally { releaseReadiness.countDown(); }
    }
    @Test public void cancellingStartupCannotLoginWhenTheOldProbeReturns() throws Exception {
        readinessStarted = new CountDownLatch(1); releaseReadiness = new CountDownLatch(1);
        try (ActivityScenario<LoginActivity> login = ActivityScenario.launch(LoginActivity.class)) {
            signIn("admin");
            assertTrue(readinessStarted.await(3, TimeUnit.SECONDS));
            onView(withId(R.id.buttonRegister)).perform(scrollTo(), click());
            releaseReadiness.countDown();
            await(() -> onView(withId(R.id.buttonLogin)).check(matches(isEnabled())));
            login.onActivity(activity -> assertFalse(activity.isFinishing()));
            assertFalse(UiPreferences.isLoggedIn(context));
            assertFalse(requests.contains("POST /api/auth/login"));
        } finally { releaseReadiness.countDown(); }
    }
    @Test public void realAdminWorkflowFromLoginThroughAppealAndAudit() throws Exception {
        try (ActivityScenario<LoginActivity> login = ActivityScenario.launch(LoginActivity.class)) {
            signIn("admin");
            await(() -> assertTrue(UiPreferences.isAdminSession(context)));
            await(() -> onView(withId(R.id.drawerRoot)).check(matches(isDisplayed())));
            assertEquals(ADMIN, AppData.getCurrentUserId().toString());
            context.startActivity(new Intent(context, AdminReviewActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> onView(withText("Reported snapshot")).check(matches(isDisplayed())));
            onView(withText(containsString(CASE))).check(doesNotExist());
            onView(withText(containsString("Gemini fixture"))).check(doesNotExist());
            onView(withText(containsString("Original reported text"))).check(matches(isDisplayed()));
            onView(withText("Reported snapshot")).perform(scrollTo(), click());
            await(() -> onView(withText(containsString("Reported snapshot"))).check(matches(isDisplayed())));
            onView(withText(containsString("Edited current text"))).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText(R.string.admin_claim)).perform(scrollTo(), click());
            await(() -> assertEquals(ADMIN, assigned));
            await(() -> onView(withText(R.string.admin_release)).check(matches(isEnabled())));
            onView(withHint(R.string.admin_decision_note)).perform(scrollTo(), replaceText("Device test decision"), closeSoftKeyboard());
            onView(withText(R.string.admin_hide)).perform(scrollTo(), click());
            onView(withText(R.string.action_confirm)).inRoot(isDialog()).perform(click());
            await(() -> assertEquals("HIDE", finalAction));
            await(() -> onView(withText(containsString("CASE_RESOLVED"))).perform(scrollTo()).check(matches(isDisplayed())));
            assertEquals("Device test decision", lastNote);
            context.startActivity(new Intent(context, AdminReviewActivity.class).putExtra("queue", 4).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> onView(withText("Evidence has context")).check(matches(isDisplayed())));
            onView(withText(containsString(APPEAL))).check(doesNotExist());
            onView(withText("Evidence has context")).perform(scrollTo(), click());
            onView(withHint(R.string.admin_appeal_response)).perform(scrollTo(), replaceText("Context supports restoration"), closeSoftKeyboard());
            onView(withText(R.string.admin_overturn)).perform(scrollTo(), click());
            onView(withText(R.string.action_confirm)).inRoot(isDialog()).perform(click());
            await(() -> assertEquals("OVERTURNED", appealStatus));
            await(() -> onView(withText(containsString("Context supports restoration"))).check(matches(isDisplayed())));
            assertEquals("NONE", finalAction); assertEquals("Context supports restoration", lastResponse);
            onView(withText(R.string.admin_open_case)).perform(scrollTo(), click());
            await(() -> onView(withText(containsString("APPEAL_DECIDED"))).perform(scrollTo()).check(matches(isDisplayed())));
        }
    }
    @Test public void accountSettingsWriteToServerAndPersistInSessionCache() throws Exception {
        BackendRuntime.from(context).user().session().login("member","fixture-password");
        UiPreferences.setLoginSession(context,false);
        try(ActivityScenario<MainActivity> screen=ActivityScenario.launch(MainActivity.class)) {
            java.util.concurrent.CountDownLatch saved=new java.util.concurrent.CountDownLatch(1);
            screen.onActivity(activity->AccountProfileSync.update(activity,backend.BackendUserSession.body("languageTag","zh-CN","theme","dark","avatarColor",3),saved::countDown));
            assertTrue(saved.await(10,TimeUnit.SECONDS));
            assertEquals("zh-CN",profileLanguage);assertEquals("dark",profileTheme);assertEquals(3,profileColor);
            assertEquals("zh-CN",UiPreferences.getLanguageTag(context));assertTrue(UiPreferences.isDarkTheme(context));
            assertEquals("dark",BackendRuntime.from(context).user().session().profile().optString("theme"));
            assertTrue(requests.contains("PATCH /api/users/me"));
            // Restore this fixture's appearance before finishing, keeping later tests independent.
            java.util.concurrent.CountDownLatch reset=new java.util.concurrent.CountDownLatch(1);
            screen.onActivity(activity->AccountProfileSync.update(activity,backend.BackendUserSession.body("languageTag","en","theme","light","avatarColor",0),reset::countDown));
            assertTrue(reset.await(10,TimeUnit.SECONDS));
        }
    }
    @Test public void myCommentsLoadFromServerWithoutOpeningTheThread() throws Exception {
        AppData.setSelectedForum(AppData.FORUM_ANU);
        try (ActivityScenario<LoginActivity> login = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.buttonLoginMember)).perform(scrollTo(), click());
            onView(withId(R.id.inputLoginUsername)).perform(scrollTo(), replaceText("member"));
            onView(withId(R.id.inputLoginPassword)).perform(scrollTo(), replaceText("fixture-password"), closeSoftKeyboard());
            onView(withId(R.id.buttonLogin)).perform(scrollTo(), click());
            await(() -> onView(withId(R.id.drawerRoot)).check(matches(isDisplayed())));
            onView(withId(R.id.tabYou)).perform(click());
            onView(withId(R.id.buttonYouTabComments)).perform(scrollTo(), click());
            await(() -> onView(withText("A comment from an unvisited thread")).check(matches(isDisplayed())));
            assertTrue(requests.contains("GET /api/community/users/"+MEMBER+"/comments?size=30"));
            assertTrue(requests.stream().noneMatch(r -> r.startsWith("GET /api/posts/88888888-8888-8888-8888-888888888888/comments")));
        }
    }
    @Test public void memberRoleCannotEnterAdminWorkspace() throws Exception {
        try (ActivityScenario<LoginActivity> login = ActivityScenario.launch(LoginActivity.class)) {
            signIn("member", true);
            await(() -> onView(withText(R.string.admin_role_required)).check(matches(isDisplayed())));
            assertFalse(UiPreferences.isAdminSession(context));
        }
    }
    @Test public void feedRetrySynchronizesTheForumSelectedDuringARequest() throws Exception {
        failFeed = true;
        AppData.setSelectedForum(AppData.FORUM_ANU);
        BackendRuntime.from(context).user().session().login("member", "fixture-password");
        UiPreferences.setLoginSession(context, false);
        try (ActivityScenario<MainActivity> main = ActivityScenario.launch(MainActivity.class)) {
            await(() -> onView(withId(R.id.buttonFeedRetry)).check(matches(isEnabled())));
            await(() -> onView(withId(R.id.textFeedSync)).check(matches(withText(containsString(context.getString(R.string.feed_sync_failed))))));
            failFeed = false;
            feedStarted = new CountDownLatch(1);
            releaseFeed = new CountDownLatch(1);
            onView(withId(R.id.buttonFeedRetry)).perform(click());
            assertTrue(feedStarted.await(10, TimeUnit.SECONDS));
            main.onActivity(activity -> activity.switchForum(AppData.FORUM_UNSW));
            releaseFeed.countDown();
            await(() -> assertTrue(feedForums.contains(AppData.FORUM_UNSW)));
            await(() -> onView(withId(R.id.layoutFeedSync)).check(matches(withEffectiveVisibility(Visibility.GONE))));
            assertEquals(AppData.FORUM_UNSW, AppData.getSelectedForumKey());
        } finally {
            if (releaseFeed != null) releaseFeed.countDown();
        }
    }
    @Test public void memberRegistersPublishesEditsAndDeletesUsingOwnServerIdentity() throws Exception {
        AppData.setSelectedForum(AppData.FORUM_ANU);
        try (ActivityScenario<LoginActivity> login = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.buttonLoginMember)).perform(scrollTo(), click());
            onView(withId(R.id.buttonRegister)).perform(scrollTo(), click());
            onView(withId(R.id.inputLoginUsername)).perform(scrollTo(), replaceText("real_member"));
            onView(withId(R.id.inputLoginPassword)).perform(scrollTo(), replaceText("fixture-password"), closeSoftKeyboard());
            onView(withId(R.id.buttonLogin)).perform(scrollTo(), click());
            await(() -> onView(withId(R.id.drawerRoot)).check(matches(isDisplayed())));
            assertTrue(registered); assertEquals(MEMBER, AppData.getCurrentUserId().toString());
            assertFalse(UiPreferences.isAdminSession(context));
            context.startActivity(new Intent(context, CreatePostActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> onView(withId(R.id.inputPostTitle)).check(matches(isDisplayed())));
            onView(withId(R.id.inputPostTitle)).perform(replaceText("A real member post"));
            onView(withId(R.id.inputPostBody)).perform(replaceText("Stored through the API"), closeSoftKeyboard());
            onView(withId(R.id.buttonPublishPost)).perform(click());
            await(() -> assertNotNull(savedPost));
            await(() -> onView(withId(R.id.drawerRoot)).check(matches(isDisplayed())));
            assertEquals("member-access", contentToken);
            context.startActivity(new Intent(context, CreatePostActivity.class).putExtra(CreatePostActivity.EXTRA_EDIT_POST_ID, POST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> onView(withId(R.id.inputPostTitle)).check(matches(withText("A real member post"))));
            onView(withId(R.id.inputPostTitle)).perform(replaceText("Edited member post"), closeSoftKeyboard());
            onView(withId(R.id.buttonPublishPost)).perform(click());
            await(() -> assertEquals("Edited member post", savedPost.optString("title")));
            await(() -> onView(withId(R.id.drawerRoot)).check(matches(isDisplayed())));
            context.startActivity(new Intent(context, PostViewerActivity.class).putExtra(PostViewerActivity.EXTRA_POST_ID, POST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> onView(withId(R.id.textPostViewerComments)).check(matches(isDisplayed())));
            onView(withId(R.id.textPostViewerComments)).perform(click());
            onView(withHint(context.getString(R.string.dialog_reply_to_hint, "real_member"))).perform(replaceText("A server comment"), closeSoftKeyboard());
            onView(withText(R.string.action_send)).inRoot(isDialog()).perform(click());
            await(() -> assertNotNull(savedComment));
            assertEquals("A server comment", savedComment.optString("body"));
            await(() -> onView(withText("A server comment")).check(matches(isDisplayed())));
            assertNotNull("Active post cache", AppData.getPostById(POST));
            assertNotNull("Report target cache", AppData.backendReportTarget(AppData.getRootMessage(AppData.getPostById(POST))));
            onView(withId(R.id.buttonPostMenu)).perform(scrollTo(), click());
            try { await(() -> assertEquals(POST, reportedTarget)); }
            catch (AssertionError error) { throw new AssertionError("Report requests=" + requests + "; member=" + AppData.getCurrentUserId() + "; admin=" + AppData.isAdminMode(), error); }
            backend.BackendReportTarget target = AppData.backendReportTarget(AppData.getRootMessage(AppData.getPostById(POST)));
            onView(withId(R.id.buttonPostDelete)).perform(click());
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click());
            await(() -> assertTrue(deleted));
            CountDownLatch staleReport = new CountDownLatch(1);
            java.util.concurrent.atomic.AtomicInteger rejected = new java.util.concurrent.atomic.AtomicInteger();
            BackendRuntime.from(context).moderation().submitReport(target, java.util.UUID.fromString(MEMBER), new backend.BackendModerationGateway.Callback<>() {
                @Override public void onSuccess(java.util.UUID id) { staleReport.countDown(); }
                @Override public void onError(backend.BackendException error) { rejected.set(error.status()); staleReport.countDown(); }
            });
            assertTrue(staleReport.await(10, TimeUnit.SECONDS));
            assertEquals(404, rejected.get()); assertEquals(1, postWrites);
        }
    }
    @Test public void communityAndMarketUseServerStateAndMemberToken() throws Exception {
        savedPost = new JSONObject().put("id", POST).put("forumKey", "anu").put("title", "Real fixture post").put("body", "Server body")
                .put("author", new JSONObject().put("id", MEMBER).put("username", "member").put("displayName", "member")).put("createdAt", "2026-10-04T00:00:00Z");
        try (ActivityScenario<LoginActivity> login=ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.inputLoginUsername)).perform(scrollTo(), replaceText("member"));
            onView(withId(R.id.inputLoginPassword)).perform(scrollTo(), replaceText("fixture-password"), closeSoftKeyboard());
            onView(withId(R.id.buttonLogin)).perform(scrollTo(), click());
            await(() -> assertNotNull(AppData.getPostById(POST)));
            assertTrue(requests.stream().noneMatch(r -> r.equals("GET /api/market") || r.startsWith("GET /api/market/leaderboard") || r.equals("GET /api/notifications")));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> AppData.togglePostVote(AppData.getPostById(POST),1));
            await(() -> assertEquals(1, socialVote));
            await(() -> assertEquals(1, AppData.getPostVoteScore(AppData.getPostById(POST))));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> AppData.togglePostBookmark(AppData.getPostById(POST)));
            await(() -> assertTrue(socialBookmark));
            await(() -> assertTrue(AppData.hasBookmarkedPost(AppData.getPostById(POST))));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> AppData.toggleFollow(java.util.UUID.fromString(ADMIN)));
            await(() -> assertTrue(socialFollow));
            await(() -> assertTrue(AppData.isFollowing(java.util.UUID.fromString(ADMIN))));
            onView(withId(R.id.tabMarket)).perform(click());
            await(() -> assertTrue(ServerFeatures.marketReady()));
            onView(withId(R.id.inputTradeAmount)).perform(scrollTo(),replaceText("2"),closeSoftKeyboard());
            onView(withId(R.id.buttonTradeBuy)).perform(scrollTo(),click());
            await(() -> assertEquals(1, marketWrites));
            await(() -> assertEquals(800, ServerFeatures.portfolio().optInt("cash")));
            assertEquals(2,marketUnits); assertEquals(1000,ServerFeatures.portfolio().optInt("totalAssets"));
            onView(withId(R.id.inputTradeAmount)).perform(scrollTo(),replaceText("2"),closeSoftKeyboard());
            onView(withId(R.id.buttonTradeSell)).perform(scrollTo(),click());
            await(() -> assertEquals(2,marketWrites));
            await(() -> assertEquals(1000,ServerFeatures.portfolio().optInt("cash")));
            onView(withId(R.id.tabLeaderboard)).perform(click());
            await(() -> onView(withText("ServerRank")).check(matches(isDisplayed())));
            assertTrue(requests.stream().anyMatch(r -> r.startsWith("GET /api/market/leaderboard")));
            assertTrue(requests.contains("PUT /api/community/posts/"+POST+"/vote"));
        }
    }
    @Test public void optimisticVoteIsImmediateRejectsDuplicateAndRollsBackFailure() throws Exception {
        savedPost = new JSONObject().put("id", POST).put("forumKey", "anu").put("title", "Vote latency fixture").put("body", "Body")
                .put("author", new JSONObject().put("id", MEMBER).put("username", "member").put("displayName", "member")).put("createdAt", "2026-10-04T00:00:00Z");
        try (ActivityScenario<LoginActivity> login=ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.buttonLoginMember)).perform(scrollTo(),click());
            onView(withId(R.id.inputLoginUsername)).perform(scrollTo(),replaceText("member"));
            onView(withId(R.id.inputLoginPassword)).perform(scrollTo(),replaceText("fixture-password"),closeSoftKeyboard());
            onView(withId(R.id.buttonLogin)).perform(scrollTo(),click());
            await(() -> {
                assertTrue(UiPreferences.isLoggedIn(context));
                assertFalse(UiPreferences.isAdminSession(context));
                assertNotNull(AppData.getPostById(POST));
                assertEquals("Vote latency fixture", AppData.getPostTitle(AppData.getPostById(POST)));
            });
            voteStarted=new CountDownLatch(1);releaseVote=new CountDownLatch(1);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                assertTrue(AppData.togglePostVote(AppData.getPostById(POST),1));
                assertEquals(1,AppData.getPostVoteScore(AppData.getPostById(POST)));
                assertEquals(1,AppData.getCurrentUserPostVote(AppData.getPostById(POST)));
                assertFalse(AppData.togglePostVote(AppData.getPostById(POST),1));
            });
            assertTrue(voteStarted.await(5,TimeUnit.SECONDS));assertEquals(0,socialVote);
            releaseVote.countDown();
            await(()->assertFalse(ServerFeatures.votePending("posts",java.util.UUID.fromString(POST))));
            assertEquals(1,socialVote);
            voteStarted=new CountDownLatch(1);releaseVote=new CountDownLatch(1);failVote=true;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                assertTrue(AppData.togglePostVote(AppData.getPostById(POST),1));
                assertEquals(0,AppData.getPostVoteScore(AppData.getPostById(POST)));
            });
            assertTrue(voteStarted.await(5,TimeUnit.SECONDS));releaseVote.countDown();
            await(()->assertFalse(ServerFeatures.votePending("posts",java.util.UUID.fromString(POST))));
            assertEquals(1,AppData.getPostVoteScore(AppData.getPostById(POST)));
            assertEquals(1,AppData.getCurrentUserPostVote(AppData.getPostById(POST)));
        } finally { if(releaseVote!=null)releaseVote.countDown(); }
    }

    @Test public void slowStateReadDoesNotDelayVoteOrReplaceItsNewerResult() throws Exception {
        savedPost = new JSONObject().put("id", POST).put("forumKey", "anu").put("title", "Read and vote fixture").put("body", "Body")
                .put("author", new JSONObject().put("id", MEMBER).put("username", "member").put("displayName", "member"))
                .put("createdAt", "2026-10-04T00:00:00Z");
        stateStarted = new CountDownLatch(1); releaseState = new CountDownLatch(1);
        try (ActivityScenario<LoginActivity> login = ActivityScenario.launch(LoginActivity.class)) {
            signIn("member");
            await(() -> {
                assertTrue(UiPreferences.isLoggedIn(context));
                assertNotNull(AppData.getPostById(POST));
                assertEquals("Read and vote fixture", AppData.getPostTitle(AppData.getPostById(POST)));
            });
            assertTrue(stateStarted.await(5, TimeUnit.SECONDS));
            var voted = new CountDownLatch(1);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                ServerFeatures.observe(voted, () -> { if (!ServerFeatures.votePending("posts", UUID.fromString(POST))
                        && ServerFeatures.state("posts", UUID.fromString(POST)).optInt("vote") == 1) voted.countDown(); });
                assertTrue(ServerFeatures.vote("posts", UUID.fromString(POST), 1));
            });
            assertTrue("A vote must finish while the old read is still blocked", voted.await(5, TimeUnit.SECONDS));
            assertEquals(1, socialVote);
            releaseState.countDown();
            await(() -> assertTrue(requests.stream().filter(r -> r.equals("POST /api/community/state")).count() >= 2));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                assertEquals(1, ServerFeatures.state("posts", UUID.fromString(POST)).optInt("vote"));
                ServerFeatures.remove(voted);
            });
        } finally { releaseState.countDown(); }
    }

    @Test public void authorPostsUseOneRequestInsteadOfScanningForums() throws Exception {
        var runtime = BackendRuntime.from(context);
        var done = new CountDownLatch(1);
        var failed = new java.util.concurrent.atomic.AtomicReference<Throwable>();
        runtime.forum().fetchProfilePage(UUID.fromString(MEMBER), java.util.Collections.singletonMap("author", null),
                new backend.BackendModerationGateway.Callback<>() {
                    public void onSuccess(backend.BackendForumGateway.ProfilePage page) { done.countDown(); }
                    public void onError(backend.BackendException e) { failed.set(e); done.countDown(); }
                });
        assertTrue(done.await(5, TimeUnit.SECONDS)); assertNull(failed.get());
        assertEquals(1, requests.stream().filter(r -> r.startsWith("GET /api/posts/authors/")).count());
        assertTrue(feedForums.isEmpty());
    }

    @Test public void reopeningFreshCommentsAndExpandingRepliesMakesNoNewRequest() throws Exception {
        savedPost=new JSONObject().put("id",POST).put("forumKey","anu").put("title","Cached thread")
                .put("body","Body").put("author",new JSONObject().put("id",MEMBER).put("displayName","member"));
        savedComment=new JSONObject().put("id",COMMENT).put("body","Cached comment")
                .put("author",new JSONObject().put("id",MEMBER).put("displayName","member")).put("createdAt","2026-10-04T00:00:00Z");
        JSONArray replies=new JSONArray();
        for(int i=0;i<3;i++) replies.put(new JSONObject().put("id",java.util.UUID.randomUUID().toString())
                .put("parentCommentId",COMMENT).put("body","Cached reply "+i)
                .put("author",new JSONObject().put("id",MEMBER).put("displayName","member"))
                .put("createdAt","2026-10-04T00:00:0"+i+"Z"));
        savedComment.put("replies",replies);
        try(ActivityScenario<LoginActivity> login=ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.buttonLoginMember)).perform(scrollTo(),click());
            onView(withId(R.id.inputLoginUsername)).perform(scrollTo(),replaceText("member"));
            onView(withId(R.id.inputLoginPassword)).perform(scrollTo(),replaceText("fixture-password"),closeSoftKeyboard());
            onView(withId(R.id.buttonLogin)).perform(scrollTo(),click());
            await(()->assertNotNull(AppData.getPostById(POST)));
            Intent intent=new Intent(context,PostViewerActivity.class).putExtra(PostViewerActivity.EXTRA_POST_ID,POST);
            try(ActivityScenario<PostViewerActivity> screen=ActivityScenario.launch(intent)) {
                await(()->onView(withText("Cached comment")).check(matches(isDisplayed())));
                onView(withText(context.getString(R.string.action_expand_replies,2))).perform(scrollTo(),click());
                onView(withText("Cached reply 2")).perform(scrollTo()).check(matches(isDisplayed()));
            }
            long before=requests.stream().filter(r->r.contains("/comments?")).count();assertEquals(1,before);
            try(ActivityScenario<PostViewerActivity> screen=ActivityScenario.launch(intent)) {
                onView(withText("Cached comment")).check(matches(isDisplayed()));
                assertEquals(before,requests.stream().filter(r->r.contains("/comments?")).count());
            }
        }
    }

    @Test public void interruptedTradeRetriesOriginalRequestEvenAfterQuoteChanges() throws Exception {
        dropFirstReceipt=true;
        assertTradeRetryDoesNotDuplicate();
    }
    @Test public void gatewayFailureRetriesOriginalRequestEvenAfterQuoteChanges() throws Exception {
        firstReceiptStatus=503;
        assertTradeRetryDoesNotDuplicate();
    }
    private void assertTradeRetryDoesNotDuplicate() throws Exception {
        try (ActivityScenario<LoginActivity> login=ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.inputLoginUsername)).perform(scrollTo(),replaceText("member"));
            onView(withId(R.id.inputLoginPassword)).perform(scrollTo(),replaceText("fixture-password"),closeSoftKeyboard());
            onView(withId(R.id.buttonLogin)).perform(scrollTo(),click());
            await(() -> assertTrue(UiPreferences.isLoggedIn(context)));
            onView(withId(R.id.tabMarket)).perform(click());await(() -> assertTrue(ServerFeatures.marketReady()));
            onView(withId(R.id.inputTradeAmount)).perform(scrollTo(),replaceText("2"),closeSoftKeyboard());
            onView(withId(R.id.buttonTradeBuy)).perform(scrollTo(),click());
            await(() -> assertEquals(1,marketWrites));
            await(() -> assertEquals(101,CampusMarketRepository.getMarket("anu").getCurrentPrice()));
            onView(withId(R.id.buttonTradeBuy)).perform(scrollTo(),click());
            await(() -> assertTrue(requests.stream().filter(r->r.equals("POST /api/market/trades")).count()>=2));
            await(() -> assertEquals("",onInputValue()));
            assertEquals(1,marketWrites);assertEquals(800,marketCash);assertEquals(1,tradeReceipts.size());
        }
    }
    @Test public void invalidRefreshFromMarketReturnsMemberToLogin() throws Exception {
        try (ActivityScenario<LoginActivity> login=ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.inputLoginUsername)).perform(scrollTo(),replaceText("member"));
            onView(withId(R.id.inputLoginPassword)).perform(scrollTo(),replaceText("fixture-password"),closeSoftKeyboard());
            onView(withId(R.id.buttonLogin)).perform(scrollTo(),click());
            await(() -> assertTrue(UiPreferences.isLoggedIn(context)));
            expiredSession=true;
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                ServerFeatures.call("GET","/api/market",null,new backend.BackendModerationGateway.Callback<>() {
                    public void onSuccess(JSONObject result) { throw new AssertionError("Expired session must fail"); }
                    public void onError(backend.BackendException error) { assertTrue(error.isUnauthorised()); }
                }));
            await(() -> assertFalse(UiPreferences.isLoggedIn(context)));
            await(() -> onView(withId(R.id.buttonLogin)).check(matches(isDisplayed())));
            assertTrue(requests.contains("POST /api/auth/refresh"));
        }
    }
    @Test public void selfProfileCollectionsReadTheActingAccountsActualSavedAndLikedPosts() throws Exception {
        savedPost=new JSONObject().put("id",POST).put("forumKey","anu").put("title","Someone else's saved post").put("body","Body")
            .put("author",new JSONObject().put("id",ADMIN).put("username","admin").put("displayName","admin"))
            .put("createdAt","2026-10-04T00:00:00Z");
        socialBookmark=true;socialVote=1;
        try(ActivityScenario<LoginActivity> login=ActivityScenario.launch(LoginActivity.class)) {
            signIn("member"); await(() -> onView(withId(R.id.drawerRoot)).check(matches(isDisplayed())));
            context.startActivity(new Intent(context,UserProfileActivity.class)
                .putExtra(UserProfileActivity.EXTRA_USER_ID,MEMBER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(() -> onView(withId(R.id.buttonProfileTabSaved)).check(matches(isDisplayed())));
            onView(withId(R.id.buttonProfileTabSaved)).perform(scrollTo(),click());
            await(() -> onView(withText("Someone else's saved post")).check(matches(isDisplayed())));
            assertTrue(requests.contains("GET /api/community/posts?kind=BOOKMARKED&size=30"));
            onView(withId(R.id.buttonProfileTabLiked)).perform(scrollTo(),click());
            await(() -> assertTrue(requests.contains("GET /api/community/posts?kind=LIKED&size=30")));
            await(() -> onView(withText("Someone else's saved post")).check(matches(isDisplayed())));
        }
    }

    @Test public void followerListOpensAnAccountWhosePostsWereNeverLoaded() throws Exception {
        BackendRuntime.from(context).user().session().login("member","fixture-password");
        UiPreferences.setLoginSession(context,false);
        try(ActivityScenario<MainActivity> main=ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.tabYou)).perform(click());
            onView(withId(R.id.layoutYouFollowing)).perform(scrollTo(),click());
            await(() -> onView(withText("Unvisited account (@not-yet-seen)")).check(matches(isDisplayed())));
            onView(withText("Unvisited account (@not-yet-seen)")).perform(click());
            await(() -> onView(withId(R.id.textProfileName)).check(matches(withText("Unvisited account"))));
            assertNotNull(AppData.getUser(UUID.fromString(ADMIN)));
        }
    }

    @Test public void notificationFiltersReuseResultsAndFailedReloadKeepsTheLastList() throws Exception {
        try(ActivityScenario<LoginActivity> login=ActivityScenario.launch(LoginActivity.class)) {
            signIn("member");await(() -> onView(withId(R.id.drawerRoot)).check(matches(isDisplayed())));
            onView(withId(R.id.tabNotifications)).perform(click());
            await(() -> onView(withText("Persisted like notification")).check(matches(isDisplayed())));
            int before=(int)requests.stream().filter(r -> r.startsWith("GET /api/notifications")).count();
            onView(withId(R.id.filterNotificationsLikes)).perform(click());
            onView(withId(R.id.filterNotificationsAll)).perform(click());
            assertEquals(before,requests.stream().filter(r -> r.startsWith("GET /api/notifications")).count());
            failNotifications=true;
            onView(withId(R.id.tabYou)).perform(click());
            onView(withId(R.id.tabNotifications)).perform(click());
            await(() -> onView(withId(R.id.buttonNotificationsRetry)).check(matches(isDisplayed())));
            onView(withText("Persisted like notification")).check(matches(isDisplayed()));
            failNotifications=false;
            onView(withId(R.id.buttonNotificationsRetry)).perform(click());
            await(() -> onView(withId(R.id.buttonNotificationsRetry)).check(matches(withEffectiveVisibility(Visibility.GONE))));
        }
    }

    @Test public void repeatingSystemInsetsDoesNotGrowProfileOrComposerPadding() throws Exception {
        BackendRuntime.from(context).user().session().login("member","fixture-password");
        UiPreferences.setLoginSession(context,false);
        try(ActivityScenario<MainActivity> main=ActivityScenario.launch(MainActivity.class)) {
            Intent profile=new Intent(context,UserProfileActivity.class).putExtra(UserProfileActivity.EXTRA_USER_ID,MEMBER);
            try(ActivityScenario<UserProfileActivity> page=ActivityScenario.launch(profile)) {
                page.onActivity(activity -> assertStableInsets(activity.findViewById(R.id.userProfileRoot)));
            }
            try(ActivityScenario<CreatePostActivity> page=ActivityScenario.launch(CreatePostActivity.class)) {
                page.onActivity(activity -> assertStableInsets(activity.findViewById(R.id.createPostRoot)));
            }
        }
    }

    private void assertStableInsets(android.view.View view) {
        var insets=new androidx.core.view.WindowInsetsCompat.Builder()
            .setInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars(),androidx.core.graphics.Insets.of(0,24,0,16)).build();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(view,insets);
        int top=view.getPaddingTop(),bottom=view.getPaddingBottom();
        androidx.core.view.ViewCompat.dispatchApplyWindowInsets(view,insets);
        assertEquals(top,view.getPaddingTop());assertEquals(bottom,view.getPaddingBottom());
    }

    @Test public void feedVoteKeepsItsAdapterAndLoadedImageWithoutAnotherDownload() throws Exception {
        AppData.setSelectedForum(AppData.FORUM_ANU);
        savedPost=new JSONObject().put("id",POST).put("forumKey","anu").put("title","An image post").put("body","Body")
            .put("mediaUrl","http://127.0.0.1:"+server.getLocalPort()+"/fixture-image.png")
            .put("author",new JSONObject().put("id",MEMBER).put("username","member").put("displayName","member"))
            .put("createdAt","2026-10-04T00:00:00Z");
        BackendRuntime.from(context).user().session().login("member","fixture-password");UiPreferences.setLoginSession(context,false);
        try(var main=ActivityScenario.launch(MainActivity.class)) {
            await(() -> onView(withId(R.id.imagePostAttachment)).check((view,error) -> {
                if(error!=null)throw error;assertTrue(((android.widget.ImageView)view).getDrawable() instanceof android.graphics.drawable.BitmapDrawable);
            }));
            final Object[] before=new Object[3];
            main.onActivity(activity -> {
                before[0]=((androidx.recyclerview.widget.RecyclerView)activity.findViewById(R.id.recyclerPosts)).getAdapter();
                before[1]=activity.findViewById(R.id.imagePostAttachment);
                before[2]=((android.widget.ImageView)before[1]).getDrawable();
                AppData.togglePostVote(AppData.getPostById(POST),1);
            });
            await(() -> assertEquals(1,socialVote));
            await(() -> assertEquals(1,AppData.getPostVoteScore(AppData.getPostById(POST))));
            main.onActivity(activity -> {
                assertSame(before[0],((androidx.recyclerview.widget.RecyclerView)activity.findViewById(R.id.recyclerPosts)).getAdapter());
                assertSame(before[1],activity.findViewById(R.id.imagePostAttachment));
                assertSame(before[2],((android.widget.ImageView)before[1]).getDrawable());
            });
            assertEquals("A vote must not refetch the visible image",1,imageReads.get());
        }
    }

    private String onInputValue() {
        java.util.concurrent.atomic.AtomicReference<String> value=new java.util.concurrent.atomic.AtomicReference<>();
        onView(withId(R.id.inputTradeAmount)).check((view,error)->value.set(((android.widget.EditText)view).getText().toString()));return value.get();
    }
    private JSONObject socialState() throws JSONException {
        return new JSONObject().put("posts",new JSONArray().put(new JSONObject().put("id",POST).put("score",socialVote).put("vote",socialVote).put("bookmarks",socialBookmark?1:0).put("bookmarked",socialBookmark)))
          .put("comments",new JSONArray()).put("users",new JSONArray().put(new JSONObject().put("id",ADMIN).put("followed",socialFollow).put("followers",socialFollow?1:0).put("following",0)).put(new JSONObject().put("id",MEMBER).put("followers",0).put("following",socialFollow?1:0).put("likes",socialVote).put("bookmarks",socialBookmark?1:0)));
    }
    private JSONObject marketState() throws JSONException {
        JSONArray quotes=new JSONArray();for(String forum:java.util.List.of("anu","unsw","usyd","um")) quotes.put(new JSONObject().put("forumKey",forum).put("price",fixturePrice).put("posts",0).put("replies",0).put("likes",0).put("dayChange",0).put("candles",new JSONArray().put(new JSONObject().put("at","2026-10-05T00:00:00Z").put("open",100).put("high",100).put("low",100).put("close",fixturePrice))));
        return new JSONObject().put("quotes",quotes).put("portfolio",new JSONObject().put("cash",marketCash).put("credits",1000).put("marketValue",marketUnits*fixturePrice).put("totalAssets",marketCash+marketUnits*fixturePrice).put("openPnl",0).put("returnPercent",0).put("positions",marketUnits==0?new JSONArray():new JSONArray().put(new JSONObject().put("forumKey","anu").put("side","LONG").put("units",marketUnits).put("cost",marketUnits*100).put("price",fixturePrice).put("value",marketUnits*fixturePrice))));
    }
    private void signIn(String username) {
        signIn(username, "admin".equals(username));
    }
    private void signIn(String username, boolean adminMode) {
        onView(withId(adminMode ? R.id.buttonLoginAdmin : R.id.buttonLoginMember)).perform(scrollTo(), click());
        onView(withId(R.id.inputLoginUsername)).perform(scrollTo(), replaceText(username));
        onView(withId(R.id.inputLoginPassword)).perform(scrollTo(), replaceText("fixture-password"), closeSoftKeyboard());
        onView(withId(R.id.buttonLogin)).perform(scrollTo(), click());
    }
    private void serve(Socket socket) {
        try (socket) {
            InputStream in = socket.getInputStream(); ByteArrayOutputStream headers = new ByteArrayOutputStream(); int end = 0, b;
            while ((b = in.read()) != -1) {
                headers.write(b); end = (end << 8) | b;
                if (end == 0x0d0a0d0a) break;
            }
            String header = headers.toString(StandardCharsets.UTF_8.name());
            String[] first = header.split("\r\n")[0].split(" ");
            int length = 0;
            for (String line : header.split("\r\n")) if (line.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(line.split(":", 2)[1].trim());
            byte[] raw = new byte[length]; int offset = 0;
            while (offset < length) { int count = in.read(raw, offset, length - offset); if (count < 0) break; offset += count; }
            JSONObject body = length == 0 ? new JSONObject() : new JSONObject(new String(raw, StandardCharsets.UTF_8));
            String path = first[1], method = first[0]; requests.add(method + " " + path); Object result;
            int responseStatus = 200;
            if(path.equals("/fixture-image.png")) {
                imageReads.incrementAndGet();
                android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(80,40,android.graphics.Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(android.graphics.Color.BLUE);
                ByteArrayOutputStream png=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,png);bitmap.recycle();
                byte[] bytes=png.toByteArray();OutputStream out=socket.getOutputStream();
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nCache-Control: no-store\r\nContent-Length: "+bytes.length+"\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(bytes);out.flush();return;
            }
            if (path.equals("/actuator/health/readiness")) {
                if (readinessStarted != null) { readinessStarted.countDown(); releaseReadiness.await(10, TimeUnit.SECONDS); }
                responseStatus = readinessFailures.getAndUpdate(value -> Math.max(0, value - 1)) > 0 ? 503 : 200;
                result = new JSONObject().put("status", responseStatus == 200 ? "UP" : "DOWN");
            } else if (expiredSession && (path.equals("/api/market") || path.equals("/api/auth/refresh"))) {
                responseStatus=401; result=new JSONObject().put("detail","Session expired");
            } else if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
                user = body.optString("username"); registered = path.endsWith("register");
                result = new JSONObject().put("accessToken", user.equals("admin") ? "fixture-access" : "member-access")
                        .put("refreshToken", "fixture-refresh").put("userId", user.equals("admin") ? ADMIN : MEMBER).put("username", user);
            } else if (path.equals("/api/users/me")) {
                if (method.equals("PATCH")) {
                    profileLanguage=body.optString("languageTag",profileLanguage);
                    profileTheme=body.optString("theme",profileTheme);
                    profileColor=body.optInt("avatarColor",profileColor);
                }
                result = new JSONObject().put("id", user.equals("admin") ? ADMIN : MEMBER)
                    .put("displayName", user).put("role", user.equals("admin") ? "ADMIN" : "MEMBER").put("status", "ACTIVE")
                    .put("languageTag",profileLanguage).put("theme",profileTheme).put("avatarColor",profileColor).put("avatarUrl",JSONObject.NULL);
            }
            else if (path.equals("/api/users/"+ADMIN)) result=new JSONObject().put("id",ADMIN).put("username","not-yet-seen").put("displayName","Unvisited account").put("avatarColor",0);
            else if (path.startsWith("/api/admin/")) {
                if (!header.contains("Bearer fixture-access")) throw new AssertionError("Missing administrator token");
                if(path.equals("/api/admin/translations")) {
                    assertEquals("zh-CN",body.optString("language"));assertEquals(CASE,body.getJSONArray("caseIds").getString(0));
                    boolean pending=evidenceTranslationReads.incrementAndGet()==1;
                    result=new JSONObject().put("language","zh-CN").put("pending",pending).put("cases",pending?new JSONArray():new JSONArray().put(
                        new JSONObject().put("id",CASE).put("sourceTitle","Reported snapshot").put("sourcePreview","Original reported text")
                            .put("title","举报时标题").put("body","原始举报内容")));
                }
                else if (path.startsWith("/api/admin/moderation-cases?")) result = new JSONArray().put(caseView());
                else if (path.endsWith("/assignment")) { assigned = method.equals("DELETE") ? "" : ADMIN; result = detail(); }
                else if (path.startsWith("/api/admin/moderation-cases/") && path.endsWith("/decision")) { caseStatus = "RESOLVED"; finalAction = body.optString("action"); lastNote = body.optString("note"); result = detail(); }
                else if (path.startsWith("/api/admin/moderation-cases/")) result = detail();
                else if (path.startsWith("/api/admin/appeals?")) result = new JSONArray().put(appeal());
                else { appealStatus = "OVERTURNED"; finalAction = "NONE"; lastResponse = body.optString("response"); result = appeal(); }
            } else if (path.startsWith("/api/posts/authors/")) {
                String authorId=path.split("\\?")[0].substring("/api/posts/authors/".length());
                result = new JSONObject().put("items", savedPost == null || !savedPost.getJSONObject("author").optString("id").equals(authorId) ? new JSONArray() : new JSONArray().put(savedPost)).put("hasMore", false);
            } else if (path.startsWith("/api/posts?")) {
                String forum = path.substring(path.indexOf("forum=") + 6).split("&")[0];
                feedForums.add(URLDecoder.decode(forum, StandardCharsets.UTF_8.name()));
                if (releaseFeed != null && AppData.FORUM_ANU.equals(forum)) {
                    feedStarted.countDown();
                    releaseFeed.await(10, TimeUnit.SECONDS);
                }
                responseStatus = failFeed ? 503 : 200;
                result = new JSONObject().put("items", savedPost == null || deleted ? new JSONArray() : new JSONArray().put(savedPost));
            }
            else if (path.equals("/api/posts") && method.equals("POST")) {
                contentToken = header.contains("Bearer member-access") ? "member-access" : "unexpected";
                postWrites++;
                savedPost = new JSONObject().put("id", POST).put("forumKey", body.optString("forumKey"))
                        .put("title", body.optString("title")).put("body", body.optString("body"))
                        .put("author", new JSONObject().put("id", MEMBER).put("username", user).put("displayName", user))
                        .put("createdAt", "2026-10-04T00:00:00Z"); result = savedPost;
            } else if (path.equals("/api/posts/" + POST) && method.equals("PATCH")) {
                savedPost.put("title", body.optString("title")).put("body", body.optString("body")); result = savedPost;
            } else if (path.equals("/api/posts/" + POST) && method.equals("DELETE")) {
                deleted = true; result = new JSONObject();
            } else if (path.equals("/api/posts/" + POST + "/comments") && method.equals("POST")) {
                savedComment = new JSONObject().put("id", COMMENT).put("body", body.optString("body"))
                        .put("author", new JSONObject().put("id", MEMBER).put("username", user).put("displayName", user))
                        .put("createdAt", "2026-10-04T00:00:00Z"); result = savedComment;
            } else if (path.startsWith("/api/posts/" + POST + "/comments")) {
                result = new JSONObject().put("items", savedComment == null ? new JSONArray() : new JSONArray().put(savedComment));
            }
            else if (path.equals("/api/reports")) {
                reportedTarget = body.optString("targetId");
                responseStatus = deleted ? 404 : 201;
                result = deleted ? new JSONObject().put("detail", "Post was deleted") : new JSONObject().put("id", CASE);
            }
            else if (path.startsWith("/api/community/users/"+MEMBER+"/comments")) {
                if (!header.contains("Bearer member-access")) throw new AssertionError("Missing member token");
                JSONObject thread=new JSONObject().put("id","88888888-8888-8888-8888-888888888888").put("forumKey","anu")
                        .put("title","An unvisited thread").put("body","Persisted original post").put("category","Life")
                        .put("author",new JSONObject().put("id",MEMBER).put("username","member").put("displayName","member"))
                        .put("createdAt","2026-10-04T00:00:00Z");
                JSONObject comment=new JSONObject().put("id","99999999-9999-9999-9999-999999999999")
                        .put("body","A comment from an unvisited thread").put("post",thread)
                        .put("author",thread.getJSONObject("author")).put("createdAt","2026-10-04T00:00:00Z");
                result=new JSONObject().put("items",new JSONArray().put(comment)).put("hasMore",false);
            }
            else if (path.startsWith("/api/community/") || path.startsWith("/api/market")) {
                if (!header.contains(user.equals("admin") ? "Bearer fixture-access" : "Bearer member-access")) throw new AssertionError("Missing acting account token");
                if (path.equals("/api/community/state") && stateStarted != null && stateStarted.getCount() > 0) {
                    JSONObject stale = socialState();
                    stateStarted.countDown(); releaseState.await(10, TimeUnit.SECONDS);
                    byte[] bytes = stale.toString().getBytes(StandardCharsets.UTF_8);
                    OutputStream out = socket.getOutputStream();
                    out.write(("HTTP/1.1 200 Fixture\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                    out.write(bytes); out.flush(); return;
                }
                if (path.startsWith("/api/community/posts/") && path.endsWith("/vote")) {
                    if(voteStarted!=null) { voteStarted.countDown();releaseVote.await(10,TimeUnit.SECONDS); }
                    if(failVote) responseStatus=503; else socialVote=body.optInt("value");
                }
                if (path.endsWith("/bookmark")) socialBookmark=method.equals("PUT");
                if (path.endsWith("/follow")) socialFollow=method.equals("PUT");
                if (path.equals("/api/market")) result=marketState();
                else if (path.startsWith("/api/market/leaderboard")) result=new JSONObject().put("items",new JSONArray().put(new JSONObject().put("id",MEMBER).put("username","member").put("displayName","ServerRank").put("forumKey","anu").put("totalAssets",marketCash+marketUnits*100).put("credits",1000).put("returnPercent",0).put("activeDays",1)));
                else if(path.equals("/api/market/trades") && method.equals("POST")) {
                    String key=body.optString("requestId");
                    JSONObject old=tradeReceipts.get(key);
                    if(old!=null) result=old;
                    else {
                        int before=marketCash, units=body.optInt("units"), price=body.optInt("expectedPrice");marketWrites++;
                        if(body.optString("action").equals("BUY")){marketCash-=units*price;marketUnits+=units;}else{marketCash+=units*price;marketUnits-=units;}
                        JSONObject receipt=new JSONObject().put("id",key).put("units",units).put("price",price).put("cashBefore",before).put("cashAfter",marketCash).put("amount",units*price);
                        tradeReceipts.put(key,receipt); result=receipt;
                        if(dropFirstReceipt){dropFirstReceipt=false;fixturePrice=101;return;}
                        if(firstReceiptStatus>0){responseStatus=firstReceiptStatus;firstReceiptStatus=0;fixturePrice=101;result=new JSONObject().put("detail","Gateway response lost");}
                    }
                 } else if(path.startsWith("/api/community/users/") && (path.contains("/following?")||path.contains("/followers?")))
                    result=new JSONObject().put("items",new JSONArray().put(new JSONObject().put("id",ADMIN).put("username","not-yet-seen").put("displayName","Unvisited account"))).put("hasMore",false);
                else if(path.startsWith("/api/community/posts?")) result=new JSONObject().put("items",socialBookmark || socialVote==1?new JSONArray().put(savedPost):new JSONArray());
                else result=socialState();
            }
            else if (path.startsWith("/api/notifications")) {
                responseStatus=failNotifications?503:200;
                result=new JSONArray().put(new JSONObject().put("id",CASE).put("type","LIKE")
                    .put("title","Persisted like notification").put("body","member liked your post")
                    .put("referenceType","POST").put("referenceId",POST).put("createdAt","2026-10-04T00:00:00Z"));
            }
            else if (path.equals("/api/moderation/status")) result = new JSONObject().put("activeEngine", "fixture");
            else result = new JSONObject().put("accessToken", "fixture-access").put("id", ADMIN);
            byte[] bytes = result.toString().getBytes(StandardCharsets.UTF_8);
            OutputStream out = socket.getOutputStream();
            out.write(("HTTP/1.1 " + responseStatus + " Fixture\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8)); out.write(bytes); out.flush();
        } catch (Exception error) { if (running) throw new RuntimeException(error); }
    }
    private JSONObject caseView() throws JSONException {
        return new JSONObject().put("id", CASE).put("targetType", "POST").put("reportCount", 2).put("status", caseStatus)
                .put("engine", "Gemini fixture").put("recommendedDecision", "HIDE").put("confidence", 0.88).put("rationale", "Review in context")
                .put("createdAt", "2026-10-04T00:00:00Z").put("finalAction", finalAction).put("assignedTo", assigned)
                .put("contentTitle", "Reported snapshot").put("contentPreview", "Original reported text");
    }
    private JSONObject detail() throws JSONException {
        JSONArray audit = new JSONArray().put(new JSONObject().put("action", "VERDICT_RECORDED"));
        if (!lastNote.isEmpty()) audit.put(new JSONObject().put("action", "CASE_RESOLVED").put("payload", new JSONObject().put("note", lastNote)));
        if (appealStatus.equals("OVERTURNED")) audit.put(new JSONObject().put("action", "APPEAL_DECIDED"));
        return new JSONObject().put("moderationCase", caseView()).put("content", new JSONObject().put("title", "Reported snapshot").put("body", "Original reported text"))
                .put("currentContent", new JSONObject().put("body", "Edited current text")).put("contentChanged", true).put("auditTrail", audit);
    }
    private JSONObject appeal() throws JSONException {
        return new JSONObject().put("id", APPEAL).put("caseId", CASE).put("status", appealStatus).put("reason", "Evidence has context").put("response", lastResponse);
    }
    private static void await(Runnable assertion) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        Throwable last = null;
        while (System.currentTimeMillis() < deadline) {
            try { assertion.run(); return; } catch (AssertionError | NoMatchingViewException error) { last = error; Thread.sleep(100); }
        }
        throw new AssertionError("UI did not reach expected state", last);
    }
}
