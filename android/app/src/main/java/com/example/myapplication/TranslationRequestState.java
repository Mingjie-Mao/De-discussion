package com.example.myapplication;

/** Main-thread state: an obsolete callback cannot complete a newer request. */
final class TranslationRequestState {
    private String scope = "";
    private long version;
    private boolean inFlight;

    boolean selectScope(String next) {
        if (scope.equals(next)) return false;
        scope = next;
        version++;
        inFlight = false;
        return true;
    }

    boolean inFlight() { return inFlight; }
    long begin() {
        if (inFlight) throw new IllegalStateException("Translation already in progress");
        inFlight = true;
        return ++version;
    }
    boolean finish(long request) {
        if (!inFlight || request != version) return false;
        inFlight = false;
        return true;
    }
}
