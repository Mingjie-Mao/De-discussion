package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import backend.BackendRuntime;
import backend.BackendUserSession;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

/** Opt-in read-only UI tour, using a disposable application ID and real persisted forum content. */
@RunWith(AndroidJUnit4.class)
public class LiveUiAuditDeviceTest {
    private Context context;
    private BackendRuntime runtime;
    @Before public void prepare() {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue("Explicit live permission required", "true".equals(InstrumentationRegistry.getArguments().getString("allowLive")));
        assertTrue("UI tours may change local preferences only in the disposable app", context.getPackageName().endsWith(".reviewtest"));
        runtime=BackendRuntime.from(context);
        assertEquals(InstrumentationRegistry.getArguments().getString("liveOrigin"),runtime.config().baseUrl());
        UiPreferences.clearLoginSession(context);
    }
    @After public void finish() { if(context!=null) UiPreferences.clearLoginSession(context); }
    private BackendUserSession login(boolean admin) {
        BackendUserSession session=admin?runtime.admin().session():runtime.user().session();
        session.login(admin?"12345":"1234",admin?"12345":"1234");
        UiPreferences.applyServerProfile(context,session.profile());
        UiPreferences.setLoginSession(context,admin);
        UiPreferences.setLanguageTag(context,"en");UiPreferences.setDarkTheme(context,false);
        return session;
    }
    private void screenshot(String name) throws Exception {
        var bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(bitmap);
        var dir=new java.io.File(context.getExternalCacheDir(),"screen-audit");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(var out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        }
        bitmap.recycle();
    }
    private void await(Runnable assertion) throws Exception {
        Throwable last=null;long until=System.currentTimeMillis()+20000;
        while(System.currentTimeMillis()<until) {
            try { assertion.run();return; } catch(AssertionError|androidx.test.espresso.NoMatchingViewException e) { last=e;Thread.sleep(150); }
        }
        throw new AssertionError("Screen did not reach its expected state",last);
    }
    private void nonEmpty(int id) {
        onView(withId(id)).check((view,error)-> { if(error!=null)throw error;
            var adapter=((androidx.recyclerview.widget.RecyclerView)view).getAdapter();assertNotNull(adapter);assertTrue(adapter.getItemCount()>0); });
    }
    @Test public void memberTabsAndThemesShowPersistedContent() throws Exception {
        var session=login(false);
        for(boolean dark:new boolean[]{false,true}) {
            var preview=session.profile().put("theme",dark?"dark":"light").put("languageTag",dark?"zh-CN":"en");
            session.acceptProfile(preview,session.generation());
            UiPreferences.applyServerProfile(context,preview);
            String suffix=dark?"zh-dark":"en-light";
            try(var main=ActivityScenario.launch(MainActivity.class)) {
                await(() -> main.onActivity(activity -> {
                    assertEquals(dark?android.content.res.Configuration.UI_MODE_NIGHT_YES:android.content.res.Configuration.UI_MODE_NIGHT_NO,
                        activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK);
                    assertEquals(dark?"zh":"en",activity.getResources().getConfiguration().getLocales().get(0).getLanguage());
                }));
                await(() -> nonEmpty(R.id.recyclerPosts));screenshot("feed-"+suffix);
                if(!dark) {
                    onView(withId(R.id.inputChannelSearch)).perform(replaceText("gym"),closeSoftKeyboard());
                    onView(withId(R.id.layoutSearchAssistant)).check(matches(isDisplayed()));screenshot("search-highlights");
                    onView(withId(R.id.inputChannelSearch)).perform(replaceText(""),closeSoftKeyboard());
                }
                onView(withId(R.id.buttonChannelsDrawer)).perform(click());screenshot("drawer-"+suffix);pressBack();
                onView(withId(R.id.tabNotifications)).perform(click());
                await(() -> nonEmpty(R.id.recyclerNotifications));screenshot("notifications-"+suffix);
                onView(withId(R.id.tabMarket)).perform(click());await(() -> assertTrue(ServerFeatures.marketReady()));
                onView(withId(R.id.viewMarketChart)).perform(scrollTo());screenshot("market-"+suffix);
                onView(withId(R.id.tabLeaderboard)).perform(click());
                await(() -> assertTrue(CampusMarketRepository.getLeaderboard().size()>=8));screenshot("board-"+suffix);
                onView(withId(R.id.tabYou)).perform(click());await(() -> nonEmpty(R.id.recyclerYouPosts));
                await(() -> onView(withId(R.id.textYouFollowingCount)).check(matches(withText("3"))));screenshot("you-"+suffix);
                onView(withId(R.id.buttonYouTabComments)).perform(scrollTo(),click());
                await(() -> {
                    onView(withId(R.id.layoutYouComments)).check(matches(isDisplayed()));
                    onView(withId(R.id.layoutYouComments)).check((view,error)->assertTrue(((android.widget.LinearLayout)view).getChildCount()>0));
                    onView(withId(R.id.buttonProfileMore)).check(matches(isEnabled()));
                });
                screenshot("my-comments-"+suffix);
                onView(withId(R.id.buttonYouTabSaved)).perform(scrollTo(),click());await(() -> nonEmpty(R.id.recyclerYouPosts));
                onView(withId(R.id.buttonYouTabLiked)).perform(scrollTo(),click());await(() -> nonEmpty(R.id.recyclerYouPosts));
                onView(withId(R.id.layoutYouFollowing)).perform(scrollTo(),click());
                await(() -> onView(withClassName(org.hamcrest.Matchers.is("android.widget.ListView"))).check((view,error)->assertTrue(((android.widget.ListView)view).getCount()>0)));
                screenshot("following-"+suffix);pressBack();
            }
        }
    }
    @Test public void threadProfilesComposerAndSettingsRemainUsable() throws Exception {
        var session=login(false);
        JSONObject response=session.withClient((client,token)->new JSONObject(client.request("GET","/api/posts?forum=anu&size=30",token,null)));
        var posts=response.getJSONArray("items");assertTrue(posts.length()>=11);
        String id=posts.getJSONObject(0).getString("id");String author=posts.getJSONObject(0).getJSONObject("author").getString("id");
        try(var main=ActivityScenario.launch(MainActivity.class)) {
            await(() -> nonEmpty(R.id.recyclerPosts));
            try(var thread=ActivityScenario.launch(new Intent(context,PostViewerActivity.class).putExtra(PostViewerActivity.EXTRA_POST_ID,id))) {
                onView(withId(R.id.textPostViewerTitle)).check(matches(isDisplayed()));screenshot("thread");
                onView(withId(R.id.textPostViewerComments)).perform(scrollTo(),click());screenshot("reply-composer");pressBack();
            }
            try(var profile=ActivityScenario.launch(new Intent(context,UserProfileActivity.class).putExtra(UserProfileActivity.EXTRA_USER_ID,author))) {
                onView(withId(R.id.textProfileName)).check(matches(isDisplayed()));screenshot("public-profile");
                onView(withId(R.id.buttonProfileTabComments)).perform(scrollTo(),click());
                await(() -> onView(withId(R.id.buttonPublicProfileMore)).check((view,error)->assertTrue(view.isEnabled())));
            }
            try(var own=ActivityScenario.launch(new Intent(context,UserProfileActivity.class).putExtra(UserProfileActivity.EXTRA_USER_ID,session.userId()))) {
                onView(withId(R.id.buttonProfileTabSaved)).perform(scrollTo(),click());await(() -> nonEmpty(R.id.recyclerProfilePosts));
                onView(withId(R.id.buttonProfileTabLiked)).perform(scrollTo(),click());await(() -> nonEmpty(R.id.recyclerProfilePosts));
                screenshot("own-profile-liked");
            }
            try(var composer=ActivityScenario.launch(CreatePostActivity.class)) {
                onView(withId(R.id.inputPostTitle)).perform(replaceText("Unpublished UI inspection"),closeSoftKeyboard());
                onView(withId(R.id.buttonPublishPost)).check(matches(isEnabled()));screenshot("create-post");
                onView(withId(R.id.buttonAddEmoji)).perform(click());screenshot("emoji-picker");pressBack();
            }
            try(var settings=ActivityScenario.launch(SettingsActivity.class)) {
                onView(withId(R.id.layoutSettingsAdmin)).check(matches(withEffectiveVisibility(Visibility.GONE)));
                onView(withId(R.id.rowSettingsLanguage)).check(matches(isDisplayed()));screenshot("settings-member");
                onView(withId(R.id.rowSettingsLanguage)).perform(click());screenshot("language-picker");pressBack();
                onView(withId(R.id.rowSettingsTheme)).perform(click());screenshot("theme-picker");pressBack();
            }
        }
    }
    @Test public void administratorQueuesEvidenceAndAuditLoadFromTheSameServer() throws Exception {
        var session=login(true);
        var cases=session.withClient((client,token)->client.getArray("/api/admin/moderation-cases?status=AWAITING_REVIEW&size=30",token));
        assertTrue(cases.length()>0);
        for(int queue:new int[]{0,1,4,5}) {
            try(var screen=ActivityScenario.launch(new Intent(context,AdminReviewActivity.class).putExtra("queue",queue))) {
                await(() -> onView(withText(org.hamcrest.Matchers.containsString("Page 1"))).check(matches(isDisplayed())));
                screenshot("admin-queue-"+queue);
            }
        }
        try(var screen=ActivityScenario.launch(new Intent(context,AdminCaseActivity.class).putExtra("id",cases.getJSONObject(0).getString("id")))) {
            await(() -> onView(withText(org.hamcrest.Matchers.containsString("CASE_OPENED"))).perform(scrollTo()).check(matches(isDisplayed())));
            screenshot("admin-case-audit");
        }
    }
}
