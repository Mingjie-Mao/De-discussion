package com.example.myapplication;

import android.content.Context;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CampusMarketRepository {
  private static final ArrayList<SchoolMarket> MARKETS = new ArrayList<>();
  private static final ArrayList<LeaderboardEntry> LEADERBOARD = new ArrayList<>();

  static {
    clearServerData();
  }

  public static void clearServerData() {
    MARKETS.clear();
    LEADERBOARD.clear();
    for (String forum : List.of("anu", "unsw", "usyd", "um"))
      MARKETS.add(serverMarket(forum, null));
  }

  private static SchoolMarket serverMarket(String forum, org.json.JSONObject value) {
    int pitch =
        forum.equals("unsw")
            ? R.string.market_unsw_pitch
            : forum.equals("usyd")
                ? R.string.market_usyd_pitch
                : forum.equals("um") ? R.string.market_um_pitch : R.string.market_anu_pitch;
    ArrayList<MarketCandle> history = new ArrayList<>();
    ArrayList<Long> times = new ArrayList<>();
    org.json.JSONArray candles = value == null ? null : value.optJSONArray("candles");
    if (candles != null)
      for (int i = 0; i < candles.length(); i++) {
        var c = candles.optJSONObject(i);
        history.add(
            new MarketCandle(
                c.optInt("open"), c.optInt("high"), c.optInt("low"), c.optInt("close"),
                java.time.Instant.parse(c.optString("at")).toEpochMilli(), c.optString("source", "LIVE")));
        times.add(java.time.Instant.parse(c.optString("at")).toEpochMilli());
      }
    SchoolMarket market =
        new SchoolMarket(
            forum,
            value == null ? 0 : value.optInt("posts"),
            value == null ? 0 : value.optInt("replies"),
            value == null ? 0 : value.optInt("likes"),
            pitch,
            listOf(forum.equals("unsw")?R.string.market_unsw_trigger_1:forum.equals("usyd")?R.string.market_usyd_trigger_1:forum.equals("um")?R.string.market_um_trigger_1:R.string.market_anu_trigger_1,
                   forum.equals("unsw")?R.string.market_unsw_trigger_2:forum.equals("usyd")?R.string.market_usyd_trigger_2:forum.equals("um")?R.string.market_um_trigger_2:R.string.market_anu_trigger_2,
                   forum.equals("unsw")?R.string.market_unsw_trigger_3:forum.equals("usyd")?R.string.market_usyd_trigger_3:forum.equals("um")?R.string.market_um_trigger_3:R.string.market_anu_trigger_3),
            history);
    market.price = value == null ? 0 : value.optInt("price");
    market.dayChange = value == null ? 0 : value.optDouble("dayChange");
    market.times = times;
    return market;
  }

  public static void applyQuotes(org.json.JSONArray quotes) {
    if (quotes == null) return;
    MARKETS.clear();
    for (int i = 0; i < quotes.length(); i++) {
      var q = quotes.optJSONObject(i);
      MARKETS.add(serverMarket(q.optString("forumKey"), q));
    }
  }

  public static void applyBoard(org.json.JSONArray rows, boolean more) {
    if (!more) LEADERBOARD.clear();
    if (rows == null) return;
    for (int i = 0; i < rows.length(); i++) {
      var u = rows.optJSONObject(i);
      LeaderboardEntry entry =
          new LeaderboardEntry(
              u.optString("displayName", u.optString("username")),
              u.optString("forumKey"),
              u.optInt("totalAssets"),
              u.optInt("activeDays"));
      entry.returnPercent = u.optDouble("returnPercent");
      entry.profit = u.optInt("totalAssets") - u.optInt("credits");
      LEADERBOARD.add(entry);
    }
  }

  private CampusMarketRepository() {}

  @NonNull
  public static List<SchoolMarket> getMarkets() {
    return Collections.unmodifiableList(MARKETS);
  }

  @NonNull
  public static SchoolMarket getMarket(String forumKey) {
    for (SchoolMarket market : MARKETS) {
      if (market.getForumKey().equals(forumKey)) {
        return market;
      }
    }
    return MARKETS.get(0);
  }

  @NonNull
  public static List<LeaderboardEntry> getLeaderboard() {
    return Collections.unmodifiableList(LEADERBOARD);
  }

  private static ArrayList<Integer> listOf(int... values) {
    ArrayList<Integer> list = new ArrayList<>();
    for (int value : values) {
      list.add(value);
    }
    return list;
  }

  private static ArrayList<MarketCandle> candles(MarketCandle... candles) {
    ArrayList<MarketCandle> items = new ArrayList<>();
    Collections.addAll(items, candles);
    return items;
  }

  private static MarketCandle candle(int open, int high, int low, int close) {
    return new MarketCandle(open, high, low, close);
  }

  public static final class SchoolMarket {
    private final String forumKey;
    private int price;
    private double dayChange;
    private ArrayList<Long> times = new ArrayList<>();

    public List<Long> getTimes() {
      return times;
    }

    private final int postsToday;
    private final int repliesToday;
    private final int likesToday;
    private final int pitchResId;
    private final ArrayList<Integer> triggerResIds;
    private final ArrayList<MarketCandle> candles;

    private SchoolMarket(
        String forumKey,
        int postsToday,
        int repliesToday,
        int likesToday,
        int pitchResId,
        ArrayList<Integer> triggerResIds,
        ArrayList<MarketCandle> candles) {
      this.forumKey = forumKey;
      this.postsToday = postsToday;
      this.repliesToday = repliesToday;
      this.likesToday = likesToday;
      this.pitchResId = pitchResId;
      this.triggerResIds = triggerResIds;
      this.candles = candles;
    }

    public String getForumKey() {
      return forumKey;
    }

    public int getPostsToday() {
      return postsToday;
    }

    public int getRepliesToday() {
      return repliesToday;
    }

    public int getLikesToday() {
      return likesToday;
    }

    public int getCurrentPrice() {
      return price;
    }

    /**
     * Heat Index = 100 + 2×posts + 1.5×replies + 0.8×netVotes + 5×activityMomentum activityMomentum
     * = max(0, (posts + replies/5 - 30) / 10)
     */
    public int getHeatIndex() {
      float momentum = Math.max(0f, (postsToday + repliesToday / 5f - 30f) / 10f);
      return Math.round(
          100 + 2f * postsToday + 1.5f * repliesToday + 0.8f * likesToday + 5f * momentum);
    }

    public double getDayChangePercent() {
      return dayChange;
    }

    @NonNull
    public List<MarketCandle> getCandles() {
      return Collections.unmodifiableList(candles);
    }

    @NonNull
    public String getPitch(Context context) {
      return context.getString(pitchResId);
    }

    @NonNull
    public List<String> getTriggers(Context context) {
      ArrayList<String> values = new ArrayList<>();
      for (int resId : triggerResIds) {
        values.add(context.getString(resId));
      }
      return values;
    }
  }

  public static final class MarketCandle {
    public final int open;
    public final int high;
    public final int low;
    public final int close;

    public final long at;
    public final String source;

    public MarketCandle(int open, int high, int low, int close) {
      this(open, high, low, close, 0, "LIVE");
    }

    public MarketCandle(int open, int high, int low, int close, long at, String source) {
      this.at = at;
      this.source = source;
      this.open = open;
      this.high = high;
      this.low = low;
      this.close = close;
    }
  }

  public static final class LeaderboardEntry {
    private final String username;
    private final String forumKey;
    private final int totalAssets;
    private final int streakDays;
    private double returnPercent;
    private int profit;

    private LeaderboardEntry(String username, String forumKey, int totalAssets, int streakDays) {
      this.username = username;
      this.forumKey = forumKey;
      this.totalAssets = totalAssets;
      this.streakDays = streakDays;
    }

    public String getUsername() {
      return username;
    }

    public String getForumKey() {
      return forumKey;
    }

    public int getTotalAssets() {
      return totalAssets;
    }

    public int getProfit() {
      return profit;
    }

    public double getReturnPercent() {
      return returnPercent;
    }

    public int getStreakDays() {
      return streakDays;
    }
  }
}
