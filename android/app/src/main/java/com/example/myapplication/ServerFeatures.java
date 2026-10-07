package com.example.myapplication;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import backend.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Account/origin scoped, memory-only projections of server social and market data. */
public final class ServerFeatures {
  private static Context context;
  private static String scope = "";
  private static final Handler main = new Handler(Looper.getMainLooper());
  private static final ExecutorService worker = Executors.newSingleThreadExecutor();
  private static final ExecutorService socialReadWorker = Executors.newSingleThreadExecutor();
  private static long socialWriteVersion;
  // A slow market refresh must not delay a user's vote or bookmark.
  private static final ExecutorService marketWorker = Executors.newSingleThreadExecutor();
  // A translation can wait seconds on the model; it must not delay votes either.
  private static final ExecutorService translationWorker = Executors.newSingleThreadExecutor();
  private static final Map<Object, Runnable> listeners = new LinkedHashMap<>();
  private static final Map<String, JSONObject> states = new HashMap<>();
  private static final Map<String, Long> fetched = new HashMap<>();
  private static final Map<String, LinkedHashSet<String>> pending = new LinkedHashMap<>();
  private static final Set<String> mutations = new HashSet<>();
  private static final Map<String, Integer> pendingVotes = new HashMap<>();
  private static boolean queued, marketBusy, marketReady;
  private static long marketAt;
  private static JSONObject portfolio = new JSONObject();
  private static String marketError;
  private static final Map<String, ArrayList<UUID>> collections = new HashMap<>();
  private static final Map<String, String> cursors = new HashMap<>();
  private static int boardPage;
  private static boolean boardMore,boardBusy,boardReady;
  private static String boardError;

  private ServerFeatures() {}

  public static void init(Context value) {
    context = value.getApplicationContext();
    checkScope();
  }

  public static void observe(Object owner, Runnable listener) {
    listeners.put(owner, listener);
  }

  public static void remove(Object owner) {
    listeners.remove(owner);
  }

  private static String checkScope() {
    if (context == null) return "";
    String next =
        AppData.getCurrentUserId() + "|" + BackendRuntime.from(context).config().baseUrl();
    if (!scope.equals(next)) {
      scope = next;
      states.clear();
      fetched.clear();
      pending.clear();
      mutations.clear();
      pendingVotes.clear();
      socialWriteVersion++;
      collections.clear();
      cursors.clear();
      portfolio = new JSONObject();
      marketAt = 0;
      marketBusy = false;
      marketReady = false;
      marketError = null;
      boardPage = 0;
      boardMore = false;boardBusy=false;boardReady=false;boardError=null;
      CampusMarketRepository.clearServerData();
    }
    return scope;
  }

  private static void changed() {
    for (Runnable listener : new ArrayList<>(listeners.values())) listener.run();
  }

  /** Redraws every observing screen, for data held outside this class. */
  static void refreshObservers() {
    changed();
  }

  static boolean hasObservers() { return !listeners.isEmpty(); }

  public static void call(
      String method,
      String path,
      JSONObject body,
      BackendModerationGateway.Callback<JSONObject> callback) {
    if (context == null || !UiPreferences.isLoggedIn(context)) {
      BackendException error = new BackendException(401, "Please sign in.");
      if (context != null) UiPreferences.handleExpiredSession(context, error);
      callback.onError(error);
      return;
    }
    String requestScope = checkScope();
    UUID me = AppData.getCurrentUserId();
    BackendRuntime runtime = BackendRuntime.from(context);
    long queuedAt = android.os.SystemClock.elapsedRealtime();
    (path.startsWith("/api/market")
            ? marketWorker
            : path.startsWith("/api/translations") ? translationWorker
            : path.equals("/api/community/state") ? socialReadWorker : worker)
        .execute(
        () -> {
          long queuedMs = android.os.SystemClock.elapsedRealtime() - queuedAt;
          if (queuedMs >= 1000) android.util.Log.w("BackendQueue", "kind="
                  + (path.startsWith("/api/market") ? "market" : path.startsWith("/api/translations") ? "translation" : "community")
                  + " waitMs=" + queuedMs);
          try {
            JSONObject result =
                runtime
                    .accounts()
                    .authenticated(
                        me,
                        (client, token) ->
                            new JSONObject(client.request(method, path, token, body)));
            main.post(
                () -> {
                  if (requestScope.equals(checkScope())) callback.onSuccess(result);
                });
          } catch (Exception error) {
            BackendException e =
                error instanceof BackendException
                    ? (BackendException) error
                    : new BackendException("Request failed.", error);
            main.post(
                () -> {
                  if (requestScope.equals(checkScope())) {
                    UiPreferences.handleExpiredSession(context, e);
                    callback.onError(e);
                  }
                });
          }
        });
  }

  private static void error(BackendException error) {
    Toast.makeText(context, error.getMessage(), Toast.LENGTH_LONG).show();
  }

  public static boolean hasState(String kind,UUID id) {
    checkScope();return states.containsKey(kind+":"+id);
  }

  public static JSONObject state(String kind, UUID id) {
    checkScope();
    String key = kind + ":" + id;
    if (context != null
        && id != null
        && UiPreferences.isLoggedIn(context)
        && System.currentTimeMillis() - fetched.getOrDefault(key, 0L) > 15000) {
      fetched.put(key, System.currentTimeMillis());
      pending.computeIfAbsent(kind, k -> new LinkedHashSet<>()).add(id.toString());
      if (!queued) {
        queued = true;
        main.post(ServerFeatures::flush);
      }
    }
    JSONObject current = states.getOrDefault(key, new JSONObject());
    Integer desired = pendingVotes.get(kind + id);
    if (desired == null) return current;
    try {
      JSONObject preview = new JSONObject(current.toString());
      preview.put("score", current.optInt("score") + desired - current.optInt("vote"));
      preview.put("vote", desired);
      return preview;
    } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
  }

  private static void flush() {
    queued = false;
    if (pending.values().stream().allMatch(Set::isEmpty)) return;
    JSONObject body = new JSONObject();
    try {
      for (String kind : List.of("posts", "comments", "users")) {
        JSONArray ids = new JSONArray();
        var set = pending.computeIfAbsent(kind, k -> new LinkedHashSet<>());
        var it = set.iterator();
        while (it.hasNext() && ids.length() < 100) {
          ids.put(it.next());
          it.remove();
        }
        body.put(
            kind.equals("posts") ? "postIds" : kind.equals("comments") ? "commentIds" : "userIds",
            ids);
      }
    } catch (JSONException impossible) {
      throw new IllegalStateException(impossible);
    }
    long readVersion = socialWriteVersion;
    call(
        "POST",
        "/api/community/state",
        body,
        new BackendModerationGateway.Callback<>() {
          public void onSuccess(JSONObject value) {
            if (readVersion != socialWriteVersion) {
              // A read started before/during a write cannot replace its newer result.
              fetched.clear();
              changed();
              flush();
              return;
            }
            for (String kind : List.of("posts", "comments", "users")) {
              JSONArray ids =
                  body.optJSONArray(
                      kind.equals("posts")
                          ? "postIds"
                          : kind.equals("comments") ? "commentIds" : "userIds");
              if (ids != null)
                for (int i = 0; i < ids.length(); i++) states.remove(kind + ":" + ids.optString(i));
            }
            apply(value);
            changed();
            flush();
          }

          public void onError(BackendException e) {
            pending.clear();
            error(e);
          }
        });
  }

  private static void apply(JSONObject value) {
    for (String kind : List.of("posts", "comments", "users")) {
      JSONArray rows = value.optJSONArray(kind);
      if (rows != null)
        for (int i = 0; i < rows.length(); i++) {
          JSONObject row = rows.optJSONObject(i);
          states.put(kind + ":" + row.optString("id"), row);
          fetched.put(kind + ":" + row.optString("id"), System.currentTimeMillis());
        }
    }
  }

  private static boolean mutate(String key, String method, String path, JSONObject body) {
    if (context == null || mutations.contains(key)) return false;
    mutations.add(key);
    socialWriteVersion++;
    changed();
    call(
        method,
        path,
        body,
        new BackendModerationGateway.Callback<>() {
          public void onSuccess(JSONObject result) {
            socialWriteVersion++;
            mutations.remove(key);
            pendingVotes.remove(key);
            apply(result);
            for (String kind : List.of("BOOKMARKED", "LIKED"))
              if (collections.containsKey(kind)) {
                ArrayList<UUID> ids = collections.get(kind);
                JSONArray rows = result.optJSONArray("posts");
                if (rows != null)
                  for (int i = 0; i < rows.length(); i++) {
                    var p = rows.optJSONObject(i);
                    UUID id = UUID.fromString(p.optString("id"));
                    boolean selected =
                        kind.equals("BOOKMARKED")
                            ? p.optBoolean("bookmarked")
                            : p.optInt("vote") == 1;
                    if (selected && !ids.contains(id)) ids.add(id);
                    if (!selected) ids.remove(id);
                  }
              }
            fetched.keySet().removeIf(k -> k.startsWith("users:"));
            marketAt = 0;
            changed();
          }

          public void onError(BackendException e) {
            socialWriteVersion++;
            mutations.remove(key);
            pendingVotes.remove(key);
            // An interrupted response may still have committed: roll back the
            // preview now, then re-read authoritative state on the next render.
            fetched.clear();
            changed();
            error(e);
          }
        });
    return true;
  }

  public static boolean vote(String kind, UUID id, int value) {
    checkScope();
    String key = kind + id;
    if (context == null || id == null || !UiPreferences.isLoggedIn(context)
        || mutations.contains(key) || (value < -1 || value > 1)) return false;
    try {
      pendingVotes.put(key, value);
      return mutate(
          kind + id,
          "PUT",
          "/api/community/" + kind + "/" + id + "/vote",
          new JSONObject().put("value", value));
    } catch (JSONException e) {
      pendingVotes.remove(key);
      return false;
    }
  }

  public static boolean votePending(String kind, UUID id) {
    checkScope();
    return mutations.contains(kind + id);
  }

  public static boolean bookmark(UUID id, boolean selected) {
    checkScope();
    return mutate(
        "bookmark" + id,
        selected ? "PUT" : "DELETE",
        "/api/community/posts/" + id + "/bookmark",
        null);
  }

  public static boolean follow(UUID id, boolean selected) {
    checkScope();
    return mutate(
        "follow" + id, selected ? "PUT" : "DELETE", "/api/community/users/" + id + "/follow", null);
  }

  public static ArrayList<UUID> collection(String kind) {
    checkScope();
    return new ArrayList<>(collections.getOrDefault(kind, new ArrayList<>()));
  }

  public static boolean collectionMore(String kind) {
    return !collections.containsKey(kind) || cursors.get(kind) != null;
  }

  public static void loadCollection(
      String kind, boolean more, BackendModerationGateway.Callback<Void> callback) {
    String cursor = more ? cursors.get(kind) : null;
    call(
        "GET",
        "/api/community/posts?kind="
            + kind
            + "&size=30"
            + (cursor == null ? "" : "&cursor=" + cursor),
        null,
        new BackendModerationGateway.Callback<>() {
          public void onSuccess(JSONObject result) {
            ArrayList<UUID> ids = more ? collection(kind) : new ArrayList<>();
            JSONArray items = result.optJSONArray("items");
            BackendRuntime runtime = BackendRuntime.from(context);
            if (items != null)
              for (int i = 0; i < items.length(); i++) {
                var p = runtime.forum().post(items.optJSONObject(i));
                AppData.upsertRemotePost(p, runtime.config().baseUrl());
                if (!ids.contains(p.id())) ids.add(p.id());
              }
            collections.put(kind, ids);
            cursors.put(kind, result.optBoolean("hasMore") ? result.optString("nextCursor") : null);
            callback.onSuccess(null);
            changed();
          }

          public void onError(BackendException e) {
            callback.onError(e);
          }
        });
  }

  public static void showPeople(Context host, UUID user, String relation) {
    android.widget.LinearLayout layout = new android.widget.LinearLayout(host);
    layout.setOrientation(android.widget.LinearLayout.VERTICAL);
    android.widget.TextView message = new android.widget.TextView(host);
    message.setPadding(30, 20, 30, 20);
    layout.addView(message);
    android.widget.ListView peopleList = new android.widget.ListView(host);
    layout.addView(peopleList, new android.widget.LinearLayout.LayoutParams(-1, AppSheet.dp(host,300)));
    android.widget.Button more = new android.widget.Button(host);
    more.setText(R.string.feed_load_more);
    layout.addView(more);
    androidx.appcompat.app.AlertDialog dialog =
        new androidx.appcompat.app.AlertDialog.Builder(host)
            .setTitle(
                relation.equals("following") ? R.string.you_following : R.string.you_followers)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .create();
    ArrayList<String> names = new ArrayList<>();
    ArrayList<UUID> peopleIds = new ArrayList<>();
    var adapter = new android.widget.ArrayAdapter<>(host, android.R.layout.simple_list_item_1,names);
    peopleList.setAdapter(adapter);
    peopleList.setOnItemClickListener((parent,view,position,id) -> {
        android.content.Intent intent = new android.content.Intent(host,UserProfileActivity.class);
        intent.putExtra(UserProfileActivity.EXTRA_USER_ID,peopleIds.get(position).toString());
        host.startActivity(intent);dialog.dismiss();
    });
    String[] cursor = {null};
    Runnable[] load = {null};
    load[0] =
        () -> {
          more.setEnabled(false);
          message.setText(R.string.feed_sync_loading);
          call(
              "GET",
              "/api/community/users/"
                  + user
                  + "/"
                  + relation
                  + "?size=30"
                  + (cursor[0] == null ? "" : "&cursor=" + cursor[0]),
              null,
              new BackendModerationGateway.Callback<>() {
                public void onSuccess(JSONObject value) {
                  if (!dialog.isShowing()) return;
                  JSONArray items = value.optJSONArray("items");
                  if (items != null)
                    for (int i = 0; i < items.length(); i++) {
                      JSONObject u = items.optJSONObject(i);
                      UUID id=UUID.fromString(u.optString("id"));
                      if(!peopleIds.contains(id)) {
                        peopleIds.add(id);names.add(u.optString("displayName") + " (@" + u.optString("username") + ")");
                        AppData.ensureRemoteUser(id,u.optString("displayName",u.optString("username")));
                      }
                    }
                  adapter.notifyDataSetChanged();
                  message.setText(names.isEmpty() ? host.getString(R.string.profile_list_empty) : "");
                  message.setVisibility(names.isEmpty()?android.view.View.VISIBLE:android.view.View.GONE);
                  cursor[0] = value.optBoolean("hasMore") ? value.optString("nextCursor") : null;
                  more.setEnabled(true);
                  more.setVisibility(
                      cursor[0] == null ? android.view.View.GONE : android.view.View.VISIBLE);
                }

                public void onError(BackendException e) {
                  if (dialog.isShowing()) {
                    message.setText(e.getMessage());
                    more.setText(R.string.feed_retry);
                    more.setEnabled(true);
                  }
                }
              });
        };
    more.setOnClickListener(v -> load[0].run());
    dialog.show();
    load[0].run();
  }

  /** Mention suggestions come from the real follow relation, one page per tap. */
  public static void pickFollowing(Context host, java.util.function.Consumer<String> selected) {
    android.widget.LinearLayout layout = new android.widget.LinearLayout(host);
    layout.setOrientation(android.widget.LinearLayout.VERTICAL);
    android.widget.ListView list = new android.widget.ListView(host);
    layout.addView(list, new android.widget.LinearLayout.LayoutParams(-1, 500));
    android.widget.Button more = new android.widget.Button(host);
    more.setText(R.string.feed_load_more);
    layout.addView(more);
    ArrayList<String> names = new ArrayList<>();
    android.widget.ArrayAdapter<String> adapter =
        new android.widget.ArrayAdapter<>(host, android.R.layout.simple_list_item_1, names);
    list.setAdapter(adapter);
    var dialog =
        new androidx.appcompat.app.AlertDialog.Builder(host)
            .setTitle(R.string.action_mention_friend)
            .setView(layout)
            .setPositiveButton(android.R.string.cancel, null)
            .create();
    String[] cursor = {null};
    Runnable[] load = {null};
    load[0] =
        () -> {
          more.setEnabled(false);
          call(
              "GET",
              "/api/community/users/"
                  + AppData.getCurrentUserId()
                  + "/following?size=30"
                  + (cursor[0] == null ? "" : "&cursor=" + cursor[0]),
              null,
              new BackendModerationGateway.Callback<>() {
                public void onSuccess(JSONObject value) {
                  if (!dialog.isShowing()) return;
                  JSONArray items = value.optJSONArray("items");
                  if (items != null)
                    for (int i = 0; i < items.length(); i++)
                      names.add("@" + items.optJSONObject(i).optString("username"));
                  adapter.notifyDataSetChanged();
                  cursor[0] = value.optBoolean("hasMore") ? value.optString("nextCursor") : null;
                  more.setEnabled(true);
                  more.setVisibility(
                      cursor[0] == null ? android.view.View.GONE : android.view.View.VISIBLE);
                  if (names.isEmpty())
                    Toast.makeText(host, R.string.toast_follow_someone_first, Toast.LENGTH_SHORT)
                        .show();
                }

                public void onError(BackendException e) {
                  more.setEnabled(true);
                  more.setText(R.string.feed_retry);
                  error(e);
                }
              });
        };
    more.setOnClickListener(v -> load[0].run());
    list.setOnItemClickListener(
        (parent, view, position, id) -> {
          selected.accept(names.get(position));
          dialog.dismiss();
        });
    dialog.show();
    load[0].run();
  }

  public static String marketError() {
    return marketError;
  }

  public static boolean marketReady() {
    checkScope();
    return marketReady;
  }

  public static JSONObject portfolio() {
    checkScope();
    return portfolio;
  }

  public static void refreshMarket(Context host, boolean force) {
    init(host);
    if (marketBusy
        || (!force && System.currentTimeMillis() - marketAt < 30000)
        || !UiPreferences.isLoggedIn(context)) return;
    marketBusy = true;
    marketAt = System.currentTimeMillis();
    call(
        "GET",
        "/api/market",
        null,
        new BackendModerationGateway.Callback<>() {
          public void onSuccess(JSONObject value) {
            if (value.optJSONObject("portfolio") == null || value.optJSONArray("quotes") == null) {
              marketBusy = false;
              error(new BackendException(0, "Invalid market response."));
              return;
            }
            portfolio = value.optJSONObject("portfolio");
            CampusMarketRepository.applyQuotes(value.optJSONArray("quotes"));
            marketReady = true;
            marketBusy = false;
            marketError = null;
            loadBoard(false);
            changed();
          }

          public void onError(BackendException e) {
            marketBusy = false;
            marketError = e.getMessage();
            error(e);
            changed();
          }
        });
  }

  public static void loadBoard(boolean more) {
    if(boardBusy)return;
    int page = more ? boardPage + 1 : 0;
    boardBusy=true;boardError=null;changed();
    call(
        "GET",
        "/api/market/leaderboard?page=" + page + "&size=30",
        null,
        new BackendModerationGateway.Callback<>() {
          public void onSuccess(JSONObject result) {
            CampusMarketRepository.applyBoard(result.optJSONArray("items"), more);
            boardPage = page;
            boardMore = result.optBoolean("hasMore");
            boardBusy=false;boardReady=true;boardError=null;
            changed();
          }

          public void onError(BackendException e) {
            boardBusy=false;boardError=e.getMessage();error(e);changed();
          }
        });
  }

  public static boolean boardBusy() { return boardBusy; }
  public static boolean boardReady() { return boardReady; }
  public static String boardError() { return boardError; }

  public static boolean boardMore() {
    return boardMore;
  }

  public static void history(Context host) {
    android.widget.LinearLayout layout = new android.widget.LinearLayout(host);
    layout.setOrientation(android.widget.LinearLayout.VERTICAL);
    android.widget.TextView text = new android.widget.TextView(host);
    text.setPadding(25, 15, 25, 15);
    android.widget.ScrollView scroll = new android.widget.ScrollView(host);
    scroll.addView(text);
    layout.addView(scroll, new android.widget.LinearLayout.LayoutParams(-1, 500));
    android.widget.Button more = new android.widget.Button(host);
    more.setText(R.string.feed_load_more);
    layout.addView(more);
    var dialog =
        new androidx.appcompat.app.AlertDialog.Builder(host)
            .setTitle(R.string.server_trade_history)
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .create();
    String[] cursor = {null};
    StringBuilder lines = new StringBuilder();
    Runnable[] load = {null};
    load[0] =
        () -> {
          more.setEnabled(false);
          call(
              "GET",
              "/api/market/trades?size=30" + (cursor[0] == null ? "" : "&cursor=" + cursor[0]),
              null,
              new BackendModerationGateway.Callback<>() {
                public void onSuccess(JSONObject value) {
                  if (!dialog.isShowing()) return;
                  JSONArray items = value.optJSONArray("items");
                  if (items != null)
                    for (int i = 0; i < items.length(); i++) {
                      var t = items.optJSONObject(i);
                      lines
                          .append(t.optString("createdAt"))
                          .append("\n")
                          .append(t.optString("forumKey"))
                          .append(" · ")
                          .append(t.optString("action"))
                          .append(" · ")
                          .append(t.optInt("units"))
                          .append(" @ ")
                          .append(t.optInt("price"))
                          .append("\n\n");
                    }
                  text.setText(lines.length() == 0 ? "—" : lines.toString());
                  cursor[0] = value.optBoolean("hasMore") ? value.optString("nextCursor") : null;
                  more.setEnabled(true);
                  more.setVisibility(
                      cursor[0] == null ? android.view.View.GONE : android.view.View.VISIBLE);
                }

                public void onError(BackendException e) {
                  if (dialog.isShowing()) {
                    text.setText(e.getMessage());
                    more.setEnabled(true);
                    more.setText(R.string.feed_retry);
                  }
                }
              });
        };
    more.setOnClickListener(v -> load[0].run());
    dialog.show();
    load[0].run();
  }
}
