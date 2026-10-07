package com.example.myapplication;
import java.util.LinkedHashMap;
import java.util.UUID;
/** Freshness/cursor metadata for comments already held in AppData; never persisted. */
final class ThreadRefreshCache {
    record Entry(long refreshedAt, String nextCursor) {}
    private static final long FRESH_MS = 15_000;
    private static String scope = "";
    private static final LinkedHashMap<UUID, Entry> entries = new LinkedHashMap<>(16, .75f, true);
    static Entry fresh(String requestedScope, UUID post, long now) {
        selectScope(requestedScope);
        Entry entry = entries.get(post);
        return entry != null && now >= entry.refreshedAt() && now-entry.refreshedAt()<FRESH_MS ? entry : null;
    }
    static void refreshed(String requestedScope, UUID post, String cursor, long now) {
        selectScope(requestedScope); entries.put(post, new Entry(now,cursor));
        while(entries.size()>30) entries.remove(entries.keySet().iterator().next());
    }
    static void paged(String requestedScope, UUID post, String cursor) {
        selectScope(requestedScope); Entry old=entries.get(post);
        if(old!=null) entries.put(post,new Entry(old.refreshedAt(),cursor));
    }
    static void clear() { entries.clear(); scope=""; }
    private static void selectScope(String value) {
        if(!scope.equals(value)) { entries.clear(); scope=value; }
    }
}
