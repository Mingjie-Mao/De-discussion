package backend;

import org.junit.Test;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class BackendStartupWaiterTest {
    @Test public void coldServerRetriesReadsUntilReady() {
        AtomicLong time = new AtomicLong(); AtomicInteger calls = new AtomicInteger();
        BackendStartupWaiter waiter = new BackendStartupWaiter(() -> {
            int call = calls.incrementAndGet();
            if (call == 1) throw new BackendException(503, "Starting");
            if (call == 2) throw new BackendException("Read timed out", new java.io.IOException());
            return call > 3;
        }, time::get, time::addAndGet);
        waiter.await(() -> false, seconds -> {});
        assertEquals(4, calls.get()); assertEquals(15_000, time.get());
    }
    @Test public void permanentErrorsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();
        BackendStartupWaiter waiter = new BackendStartupWaiter(() -> {
            calls.incrementAndGet(); throw new BackendException(404, "Wrong server URL");
        }, () -> 0, ms -> fail("Should not wait"));
        assertEquals(404, assertThrows(BackendException.class,
                () -> waiter.await(() -> false, seconds -> {})).status());
        assertEquals(1, calls.get());
    }
    @Test public void unavailableServerStopsAtDeadline() {
        AtomicLong time = new AtomicLong();
        BackendStartupWaiter waiter = new BackendStartupWaiter(() -> false, time::get, time::addAndGet);
        assertThrows(BackendException.class, () -> waiter.await(() -> false, seconds -> {}));
        assertEquals(BackendStartupWaiter.WAIT_MS, time.get());
    }
    @Test public void cancellationRejectsEvenALateReadyResponse() {
        AtomicBoolean cancelled = new AtomicBoolean();
        BackendStartupWaiter waiter = new BackendStartupWaiter(() -> {
            cancelled.set(true); return true;
        }, () -> 0, ms -> fail("Should not wait"));
        assertThrows(CancellationException.class, () -> waiter.await(cancelled::get, seconds -> {}));
    }
    @Test public void lateReadyResponseCannotExceedDeadline() {
        AtomicLong time = new AtomicLong();
        BackendStartupWaiter waiter = new BackendStartupWaiter(() -> {
            time.set(BackendStartupWaiter.WAIT_MS + 1); return true;
        }, time::get, time::addAndGet);
        assertThrows(BackendException.class, () -> waiter.await(() -> false, seconds -> {}));
    }
}
