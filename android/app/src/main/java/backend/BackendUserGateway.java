package backend;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/** Asynchronous member registration and authentication. */
public final class BackendUserGateway {
    private final BackendUserSession session;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicLong generation = new AtomicLong();
    BackendUserGateway(BackendUserSession session) { this.session = session; }
    public BackendUserSession session() { return session; }
    public void clear() { generation.incrementAndGet(); session.clear(); }
    public void login(String username, String password, boolean register, BackendModerationGateway.Callback<Void> callback) {
        long attempt = generation.get();
        executor.execute(() -> {
            if (attempt != generation.get()) return;
            try {
                if (register) session.register(username, password); else session.login(username, password);
                main.post(() -> { if (attempt == generation.get()) callback.onSuccess(null); });
            } catch (Exception error) {
                BackendException wrapped = error instanceof BackendException ? (BackendException) error
                        : new BackendException(BackendText.integrationFailed(), error);
                main.post(() -> { if (attempt == generation.get()) callback.onError(wrapped); });
            }
        });
    }
}
