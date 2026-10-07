package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class TranslationRequestStateTest {
    @Test public void switchingAccountReleasesARequestWhoseCallbackWasDropped() {
        var state = new TranslationRequestState();
        state.selectScope("origin|member-A|1");
        long old = state.begin();
        state.selectScope("origin|member-B|1");
        assertFalse(state.inFlight());
        long current = state.begin();
        assertFalse(state.finish(old));
        assertTrue(state.inFlight());
        assertTrue(state.finish(current));
    }
    @Test public void sameAccountAfterReloginIsANewScope() {
        var state = new TranslationRequestState();
        state.selectScope("origin|member|1");
        long old = state.begin();
        assertTrue(state.selectScope("origin|member|2"));
        assertFalse(state.finish(old));
        assertFalse(state.inFlight());
    }
    @Test public void backendChangeAndLogoutInvalidatePendingCallbacks() {
        var state = new TranslationRequestState();
        state.selectScope("origin-A|admin|1");
        long old = state.begin();
        state.selectScope("origin-B|admin|1");
        assertFalse(state.finish(old));
        long current = state.begin();
        state.selectScope("");
        assertFalse(state.finish(current));
        assertFalse(state.inFlight());
    }
    @Test public void repaintDoesNotReleaseTheCurrentRequest() {
        var state = new TranslationRequestState();
        state.selectScope("origin|member|1");
        long current = state.begin();
        assertFalse(state.selectScope("origin|member|1"));
        assertTrue(state.inFlight());
        assertTrue(state.finish(current));
        assertFalse(state.finish(current));
    }
}
