package com.example.myapplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class RemoteImageLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // Only share requests already in flight. Public media is no-store because
    // moderators can revoke visibility; do not keep a cross-screen image cache.
    private static final Map<String, ArrayList<WeakReference<ImageView>>> IN_FLIGHT = new HashMap<>();
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_DIMENSION = 1600;

    private RemoteImageLoader() {}

    static void display(ImageView view, Uri uri) {
        String key = uri == null ? null : uri.toString();
        // A social-state refresh rebinds the same visible item. Keep its image
        // instead of blanking it and starting another download on every vote.
        if (key != null && key.equals(view.getTag()) && view.getDrawable() != null) return;
        view.setTag(uri == null ? null : uri.toString());
        if (uri == null) { view.setImageDrawable(null); return; }
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            view.setImageURI(uri);
            return;
        }
        view.setImageDrawable(null);
        ArrayList<WeakReference<ImageView>> waiting = IN_FLIGHT.get(key);
        if (waiting != null) {
            if (waiting.stream().noneMatch(ref -> ref.get() == view)) waiting.add(new WeakReference<>(view));
            return;
        }
        waiting = new ArrayList<>();
        waiting.add(new WeakReference<>(view));
        IN_FLIGHT.put(key, waiting);
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            Bitmap result = null;
            try {
                connection = (HttpURLConnection) new URL(key).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(10000);
                try (InputStream input = connection.getInputStream(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (bytes.size() + count > MAX_BYTES) throw new java.io.IOException("Image exceeds limit.");
                        bytes.write(buffer, 0, count);
                    }
                    byte[] data = bytes.toByteArray();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(data, 0, data.length, options);
                    options.inSampleSize = 1;
                    while (options.outWidth / options.inSampleSize > MAX_DIMENSION
                            || options.outHeight / options.inSampleSize > MAX_DIMENSION) options.inSampleSize *= 2;
                    options.inJustDecodeBounds = false;
                    result = BitmapFactory.decodeByteArray(data, 0, data.length, options);
                }
            } catch (Exception ignored) {
                // Keep failures retryable on the next bind.
            } finally {
                if (connection != null) connection.disconnect();
            }
            Bitmap bitmap = result;
            MAIN.post(() -> {
                ArrayList<WeakReference<ImageView>> targets = IN_FLIGHT.remove(key);
                if (targets == null) return;
                for (WeakReference<ImageView> target : targets) {
                    ImageView image = target.get();
                    if (image != null && key.equals(image.getTag())) image.setImageBitmap(bitmap);
                }
            });
        });
    }
}
