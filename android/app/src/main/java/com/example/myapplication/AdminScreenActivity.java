package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import backend.BackendAdminGateway;
import backend.BackendException;
import backend.BackendModerationGateway;
import backend.BackendRuntime;
import org.json.JSONObject;
import java.util.function.Consumer;

/** Shared session guard and layout for the in-app administrator workspace. */
abstract class AdminScreenActivity extends AppCompatActivity {
    protected BackendAdminGateway admin;
    protected LinearLayout content;
    protected TextView status;
    protected long revision;

    @Override protected void onCreate(Bundle state) {
        UiPreferences.applyAppearance(this);
        super.onCreate(state);
        admin = BackendRuntime.from(this).admin();
    }
    protected boolean requireAdmin() {
        if (!UiPreferences.isAdminSession(this) || !admin.session().hasSession()) {
            signInAgain();
            return false;
        }
        return true;
    }
    protected void screen(int title) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.page_background));
        int padding = AppSheet.dp(this, 18);
        root.setPadding(padding, padding, padding, padding);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(padding + bars.left, padding + bars.top, padding + bars.right, padding + bars.bottom);
            return insets;
        });
        root.addView(AppSheet.button(this, getString(R.string.action_back), false, this::finish));
        TextView heading = text(getString(title), 24);
        root.addView(heading);
        status = text("", 13);
        root.addView(status);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }
    protected TextView text(CharSequence value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(ContextCompat.getColor(this, R.color.ink_primary));
        view.setTextSize(size);
        view.setPadding(0, AppSheet.dp(this, 10), 0, AppSheet.dp(this, 8));
        view.setTextIsSelectable(true);
        return view;
    }
    protected void note(int label, String value) {
        content.addView(text(getString(label) + "\n" + value, 15));
    }
    protected View action(int label, Runnable callback) {
        View button = AppSheet.button(this, getString(label), false, callback);
        content.addView(button);
        return button;
    }
    protected <T> BackendModerationGateway.Callback<T> callback(long request, Consumer<T> success) {
        return new BackendModerationGateway.Callback<>() {
            public void onSuccess(T value) {
                if (!isFinishing() && !isDestroyed() && request == revision && requireAdmin()) success.accept(value);
            }
            public void onError(BackendException error) {
                if (isFinishing() || isDestroyed() || request != revision) return;
                if (!admin.session().hasSession()) { signInAgain(); return; }
                status.setText(error.getMessage());
                onRequestFailed(error);
            }
        };
    }
    protected void onRequestFailed(BackendException error) {}
    protected void signInAgain() {
        admin.clear();
        UiPreferences.clearLoginSession(this);
        AppData.setAdminMode(false);
        Intent intent = new Intent(this, LoginActivity.class).putExtra("admin", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    protected static String value(JSONObject object, String key) {
        return object == null || object.isNull(key) ? "" : object.optString(key, "");
    }
    @Override protected void onResume() { super.onResume(); requireAdmin(); }
    @Override protected void onDestroy() { revision++; super.onDestroy(); }
}
