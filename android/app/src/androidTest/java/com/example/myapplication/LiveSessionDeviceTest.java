package com.example.myapplication;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import backend.BackendRuntime;
import backend.BackendUserSession;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Explicit opt-in: run each method in a separate process against the public demo. */
@RunWith(AndroidJUnit4.class)
public class LiveSessionDeviceTest {
    private BackendRuntime runtime() {
        var args = InstrumentationRegistry.getArguments();
        assumeTrue("Live verification must be explicitly enabled.", "true".equals(args.getString("allowLive")));
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var runtime = BackendRuntime.from(context);
        assertTrue("Configured origin must match the explicitly supplied live origin.",
                runtime.config().baseUrl().equals(args.getString("liveOrigin")));
        return runtime;
    }

    @Test public void signInAndSaveRealSessions() throws Exception {
        var runtime = runtime();
        runtime.admin().session().login("12345", "12345");
        assertTrue(runtime.admin().session().hasSession());
        runtime.user().session().login("1234", "1234");
        var member = runtime.user().session();
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiPreferences.applyServerProfile(context, member.profile());
        UiPreferences.setLoginSession(context, false);
        assertTrue(UiPreferences.isLoggedIn(context));
        var file = new java.io.File(context.getNoBackupFilesDir(), "de-discussion-session-member.bin");
        String encrypted = new String(Files.readAllBytes(file.toPath()), StandardCharsets.ISO_8859_1);
        assertFalse("Access token must not be plaintext on disk.", encrypted.contains(member.accessToken()));
        verifyLiveAccountAndPosts(member);
    }

    @Test public void restoreAfterRealProcessStop() throws Exception {
        var runtime = runtime();
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("A fresh process must retain the signed-in member.", UiPreferences.isLoggedIn(context));
        assertFalse(UiPreferences.isAdminSession(context));
        assertTrue("Administrator session must also survive process death.", runtime.admin().session().hasSession());
        verifyLiveAccountAndPosts(runtime.user().session());
    }

    /** Keeps the account selected by the user; does not replace it with a fixture login. */
    @Test public void restoreSelectedSessionAfterRealProcessStop() throws Exception {
        var runtime = runtime();
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("The selected account must survive process death.", UiPreferences.isLoggedIn(context));
        boolean admin = UiPreferences.isAdminSession(context);
        BackendUserSession session = admin ? runtime.admin().session() : runtime.user().session();
        assertTrue("The selected session must be restored from encrypted storage.", session.hasSession());
        JSONObject profile = session.withClient((client, token) ->
                new JSONObject(client.request("GET", "/api/users/me", token, null)));
        assertEquals(admin ? "ADMIN" : "MEMBER", profile.optString("role"));
        assertEquals(session.userId(), profile.optString("id"));
        JSONObject posts = session.withClient((client, token) ->
                new JSONObject(client.request("GET", "/api/posts?forum=anu&size=20", token, null)));
        assertTrue("Real server posts must remain available.", posts.getJSONArray("items").length() >= 11);
    }

    /** Read-only acceptance of the real admin queue after a deployment. */
    @Test public void realReviewQueueShowsContentInsteadOfDebugMetadata() throws Exception {
        var runtime = runtime();
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue("Keep the user's selected role; do not switch a member to admin.", UiPreferences.isAdminSession(context));
        var rows = runtime.admin().session().withClient((client, token) -> client.getArray(
                "/api/admin/moderation-cases?status=AWAITING_REVIEW&size=30", token));
        assertTrue("Existing reported cases must be readable.", rows.length() > 0);
        JSONObject first = rows.getJSONObject(0);
        String title = AdminQueueContent.title(first);
        String preview = AdminQueueContent.preview(first);
        String expected = title.isEmpty() ? preview : title;
        assertFalse("The queue needs content, not an identifier.", expected.isEmpty());
        try (var scenario = androidx.test.core.app.ActivityScenario.launch(AdminReviewActivity.class)) {
            long deadline = System.currentTimeMillis() + 15000;
            Throwable last = null;
            while (System.currentTimeMillis() < deadline) {
                try {
                    androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(expected))
                            .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
                    last = null; break;
                } catch (AssertionError | androidx.test.espresso.NoMatchingViewException error) { last = error; Thread.sleep(100); }
            }
            if (last != null) throw new AssertionError("Live content did not appear in the queue.", last);
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(
                    org.hamcrest.Matchers.containsString(first.getString("id"))))
                    .check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(
                    org.hamcrest.Matchers.containsString(first.optString("engine"))))
                    .check(androidx.test.espresso.assertion.ViewAssertions.doesNotExist());
            if ("true".equals(InstrumentationRegistry.getArguments().getString("capturePreview"))) {
                var screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                assertNotNull(screenshot);
                try (var out = new java.io.FileOutputStream(new java.io.File(context.getExternalCacheDir(), "review-queue-live.png"))) {
                    assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
                }
                screenshot.recycle();
            }
        }
    }

    /** Read-only Chinese queue acceptance; keeps the selected account and language. */
    @Test public void realChineseReviewQueueUsesCachedEvidenceTranslations() throws Exception {
        var runtime = runtime();
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assumeTrue("Preserve the user's selected administrator session.", UiPreferences.isAdminSession(context));
        assumeTrue("Verify the user's selected Chinese mode.", UiPreferences.getLanguageTag(context).startsWith("zh"));
        var rows = runtime.admin().session().withClient((client, token) -> client.getArray(
                "/api/admin/moderation-cases?status=AWAITING_REVIEW&size=30", token));
        assertTrue(rows.length() > 0);
        var ids = new org.json.JSONArray();
        for (int i=0; i<rows.length(); i++) ids.put(rows.getJSONObject(i).getString("id"));
        var result = new backend.BackendAdminApi(runtime.admin().session()).translations(ids);
        assertFalse("Pre-warmed real model translations must be persisted.", result.getBoolean("pending"));
        JSONObject translated = null;
        for (int i=0; i<result.getJSONArray("cases").length(); i++) {
            var item = result.getJSONArray("cases").getJSONObject(i);
            if (item.getString("id").equals(rows.getJSONObject(0).getString("id"))) translated = item;
        }
        assertNotNull(translated);
        assertTrue(AdminQueueContent.matchesTranslation(rows.getJSONObject(0), translated));
        String expected = AdminQueueContent.translatedTitle(translated);
        if (expected.isEmpty()) expected = AdminQueueContent.translatedPreview(translated);
        assertTrue(expected.codePoints().anyMatch(c -> c >= 0x3400 && c <= 0x9fff));
        try (var scenario = androidx.test.core.app.ActivityScenario.launch(AdminReviewActivity.class)) {
            long deadline = System.currentTimeMillis() + 20000;
            Throwable last = null;
            while (System.currentTimeMillis() < deadline) {
                try {
                    androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(expected))
                            .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
                    last = null; break;
                } catch (AssertionError | androidx.test.espresso.NoMatchingViewException error) { last = error; Thread.sleep(150); }
            }
            if (last != null) throw new AssertionError("Real Chinese evidence did not appear.", last);
            var screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
            assertNotNull(screenshot);
            try (var out = new java.io.FileOutputStream(new java.io.File(context.getExternalCacheDir(), "review-queue-chinese-live.png"))) {
                assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
            }
            screenshot.recycle();
        }
    }

    /** Read-only Market acceptance using the user's saved account and database history. */
    @Test public void realMarketRestoresOriginalCandlesAndPeriodTabs() throws Exception {
        var runtime = runtime();
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(UiPreferences.isLoggedIn(context));
        BackendUserSession session = UiPreferences.isAdminSession(context)
                ? runtime.admin().session() : runtime.user().session();
        var payload = session.withClient((client, token) ->
                new JSONObject(client.request("GET", "/api/market", token, null)));
        var quotes = payload.getJSONArray("quotes");
        assertEquals(4, quotes.length());
        for (int i = 0; i < quotes.length(); i++) {
            var rows = quotes.getJSONObject(i).getJSONArray("candles");
            int imported = 0;
            for (int j = 0; j < rows.length(); j++)
                if ("IMPORTED_DEMO".equals(rows.getJSONObject(j).optString("source"))) imported++;
            assertEquals("Each original school needs its six persisted candles.", 6, imported);
        }
        try (var scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity.class)) {
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.tabMarket))
                    .perform(androidx.test.espresso.action.ViewActions.click());
            long deadline = System.currentTimeMillis() + 20000;
            while (!ServerFeatures.marketReady() && System.currentTimeMillis() < deadline) Thread.sleep(100);
            assertTrue("Real market data must load.", ServerFeatures.marketReady());
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.viewMarketChart))
                    .perform(androidx.test.espresso.action.ViewActions.scrollTo())
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            scenario.onActivity(activity -> {
                android.widget.LinearLayout periods = activity.findViewById(R.id.layoutChartPeriod);
                assertEquals(5, periods.getChildCount());
                String key = AppData.getSelectedForumKey();
                assertEquals(6, MarketChartSeries.select(CampusMarketRepository.getMarket(key).getCandles(), "history").candles().size());
                android.widget.TextView note = activity.findViewById(R.id.textMarketChartNote);
                assertEquals(activity.getString(R.string.market_chart_history_note), note.getText().toString());
            });
            scenario.onActivity(activity -> {
                android.view.View hero = activity.findViewById(R.id.layoutMarketHero);
                android.view.ViewParent parent = hero.getParent();
                while (parent != null && !(parent instanceof android.widget.ScrollView)) parent = parent.getParent();
                if (parent instanceof android.widget.ScrollView scroll) scroll.scrollTo(0, hero.getTop());
            });
            androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withId(R.id.viewMarketChart))
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
            if ("true".equals(InstrumentationRegistry.getArguments().getString("capturePreview"))) {
                var screenshot = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                assertNotNull(screenshot);
                try (var out = new java.io.FileOutputStream(new java.io.File(context.getExternalCacheDir(), "market-candles-live.png"))) {
                    assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
                }
                screenshot.recycle();
            }
            // Exercise actual on-screen period controls rather than only data aggregation.
            int[] periodLabels={R.string.chart_period_intraday,R.string.chart_period_day,R.string.chart_period_week,R.string.chart_period_month};
            String[] periodKeys={"intraday","day","week","month"};
            for (int index=0;index<periodLabels.length;index++) {
                int label=periodLabels[index];String period=periodKeys[index];
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(context.getString(label)))
                        .perform(androidx.test.espresso.action.ViewActions.scrollTo(), androidx.test.espresso.action.ViewActions.click());
                scenario.onActivity(activity -> {
                    android.widget.TextView note = activity.findViewById(R.id.textMarketChartNote);
                    var series=MarketChartSeries.select(CampusMarketRepository.getMarket(AppData.getSelectedForumKey()).getCandles(),period);
                    assertEquals(activity.getString("intraday".equals(period)?R.string.market_chart_intraday_note:R.string.market_chart_period_note,
                            series.candles().size()),note.getText().toString());
                });
                if ("true".equals(InstrumentationRegistry.getArguments().getString("capturePreview"))) {
                    var screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                    try(var out=new java.io.FileOutputStream(new java.io.File(context.getExternalCacheDir(),"market-"+period+".png"))) {
                        screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);
                    }
                    screenshot.recycle();
                }
            }
        }
    }

    private void verifyLiveAccountAndPosts(BackendUserSession session) throws Exception {
        JSONObject profile = session.withClient((client, token) -> new JSONObject(client.request("GET", "/api/users/me", token, null)));
        assertTrue("Server must authenticate the saved real member.", "MEMBER".equals(profile.optString("role"))
                && session.userId().equals(profile.optString("id")));
        JSONObject posts = session.withClient((client, token) -> new JSONObject(client.request("GET", "/api/posts?forum=anu&size=20", token, null)));
        assertTrue("Persisted forum posts must remain available.", posts.getJSONArray("items").length() >= 11);
    }
}
