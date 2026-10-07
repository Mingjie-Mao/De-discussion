package backend;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Polls a public readiness endpoint; never retries a login or registration write. */
public final class BackendStartupWaiter {
    public static final long WAIT_MS = 300_000;
    private static final long POLL_MS = 5_000;
    interface Probe { boolean ready(); }
    interface Pause { void sleep(long millis) throws InterruptedException; }
    private final Probe probe;
    private final LongSupplier clock;
    private final Pause pause;

    public BackendStartupWaiter(String origin) {
        this(() -> "UP".equals(new BackendClient(origin)
                .getObject("/actuator/health/readiness", null).optString("status")),
                () -> System.nanoTime() / 1_000_000, Thread::sleep);
    }

    BackendStartupWaiter(Probe probe, LongSupplier clock, Pause pause) {
        this.probe = probe; this.clock = clock; this.pause = pause;
    }

    public void await(BooleanSupplier cancelled, java.util.function.LongConsumer progress) {
        long start = clock.getAsLong();
        while (true) {
            checkCancelled(cancelled);
            long elapsed = clock.getAsLong() - start;
            if (elapsed >= WAIT_MS) throw new BackendException(0, "Server readiness timed out.");
            progress.accept(elapsed / 1000);
            try {
                boolean ready = probe.ready();
                checkCancelled(cancelled);
                if (clock.getAsLong() - start >= WAIT_MS)
                    throw new BackendException(0, "Server readiness timed out.");
                if (ready) return;
            } catch (BackendException error) {
                if (error.status() != 0 && error.status() != 502
                        && error.status() != 503 && error.status() != 504) throw error;
            }
            checkCancelled(cancelled);
            long remaining = WAIT_MS - (clock.getAsLong() - start);
            if (remaining <= 0) throw new BackendException(0, "Server readiness timed out.");
            try { pause.sleep(Math.min(POLL_MS, remaining)); }
            catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new CancellationException("Login cancelled.");
            }
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new CancellationException("Login cancelled.");
    }
}
