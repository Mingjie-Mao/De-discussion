package backend;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

/** Network work stays off the UI thread; clearing a session invalidates queued work and callbacks. */
public final class BackendAdminGateway {
    private final BackendAdminSession session;
    private final BackendAdminApi api;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService translationExecutor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicLong generation = new AtomicLong();
    BackendAdminGateway(BackendAdminSession session) { this.session = session; api = new BackendAdminApi(session); }
    public BackendAdminSession session() { return session; }
    public void clear() { generation.incrementAndGet(); session.clear(); }
    public void login(String user, String password, BackendModerationGateway.Callback<Void> callback) {
        long attempt = generation.get();
        run(() -> { session.login(user, password); if (attempt != generation.get()) session.clear(); return null; }, callback);
    }
    public void cases(String status, int page, BackendModerationGateway.Callback<JSONArray> callback) { run(() -> api.cases(status, page), callback); }
    public void detail(String id, BackendModerationGateway.Callback<JSONObject> callback) { run(() -> api.detail(id), callback); }
    public void translations(JSONArray ids, BackendModerationGateway.Callback<JSONObject> callback) {
        run(translationExecutor, () -> api.translations(ids), callback);
    }
    public void claim(String id, BackendModerationGateway.Callback<JSONObject> callback) { run(() -> api.claim(id), callback); }
    public void release(String id, BackendModerationGateway.Callback<JSONObject> callback) { run(() -> api.release(id), callback); }
    public void decide(String id, String action, String note, BackendModerationGateway.Callback<JSONObject> callback) { run(() -> api.decide(id, action, note), callback); }
    public void appeals(String status, int page, BackendModerationGateway.Callback<JSONArray> callback) { run(() -> api.appeals(status, page), callback); }
    public void decideAppeal(String id, String decision, String response, BackendModerationGateway.Callback<JSONObject> callback) { run(() -> api.decideAppeal(id, decision, response), callback); }
    public void image(String path, BackendModerationGateway.Callback<android.graphics.Bitmap> callback) {
        run(() -> {
            byte[] bytes = api.image(path);
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (options.outWidth <= 0 || options.outHeight <= 0 || (long) options.outWidth * options.outHeight > 20_000_000)
                throw new BackendException(0, BackendText.malformedResponse());
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 1200 || options.outHeight / options.inSampleSize > 1200) options.inSampleSize *= 2;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (bitmap == null) throw new BackendException(0, BackendText.malformedResponse());
            return bitmap;
        }, callback);
    }
    private <T> void run(Task<T> task, BackendModerationGateway.Callback<T> callback) {
        run(executor, task, callback);
    }
    private <T> void run(ExecutorService worker, Task<T> task, BackendModerationGateway.Callback<T> callback) {
        long before = generation.get();
        worker.execute(() -> {
            if (before != generation.get()) return;
            try {
                T result = task.run();
                main.post(() -> { if (before == generation.get()) callback.onSuccess(result); });
            } catch (Exception error) {
                BackendException wrapped = error instanceof BackendException ? (BackendException) error
                        : new BackendException(BackendText.integrationFailed(), error);
                main.post(() -> { if (before == generation.get()) callback.onError(wrapped); });
            }
        });
    }
    private interface Task<T> { T run() throws Exception; }
}
