package com.example.myapplication;

import android.os.Bundle;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AlertDialog;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import backend.BackendException;
import backend.BackendModerationGateway;

/** Report-time evidence, model advice, review ownership, decisions and the server audit trail. */
public final class AdminCaseActivity extends AdminScreenActivity {
    private String id;
    private EditText note;
    private String draft = "";
    private final List<android.view.View> controls = new ArrayList<>();
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!requireAdmin()) return;
        id = getIntent().getStringExtra("id");
        if (id == null) { finish(); return; }
        if (saved != null) draft = saved.getString("draft", "");
        screen(R.string.admin_case_detail);
        load();
    }
    private void load() {
        controls.forEach(v -> v.setEnabled(false));
        status.setText(R.string.admin_loading);
        admin.detail(id, callback(++revision, this::render));
    }
    private void render(JSONObject detail) {
        if (note != null) draft = note.getText().toString();
        content.removeAllViews(); controls.clear();
        JSONObject item = detail.optJSONObject("moderationCase");
        if (item == null) { status.setText(R.string.backend_error_malformed); return; }
        status.setText(ModerationLabels.status(this, value(item, "status")));
        evidence(R.string.admin_reported_content, detail.optJSONObject("content"));
        note(R.string.admin_case_status, ModerationLabels.status(this, value(item, "status")) + " · " + item.optInt("reportCount") + " " + getString(R.string.admin_reports)
                + "\n" + value(item, "createdAt") + "\n" + ModerationLabels.action(this, value(item, "finalAction")) + " " + value(item, "decidedAt"));
        String confidence = item.isNull("confidence") ? "—" : String.format(java.util.Locale.getDefault(), "%.1f%%", item.optDouble("confidence") * 100);
        note(R.string.admin_model_advice, value(item, "engine") + " → " + ModerationLabels.decision(this, value(item, "recommendedDecision")) + " · " + confidence
                + "\n" + value(item, "rationale") + "\n" + value(item, "ruleCodes"));
        if (detail.optBoolean("contentChanged")) {
            content.addView(text(getString(R.string.admin_content_changed), 17));
            evidence(R.string.admin_current_content, detail.optJSONObject("currentContent"));
        }
        String owner = value(item, "assignedTo");
        note(R.string.admin_assignment, owner.isEmpty() ? getString(R.string.admin_unassigned) : owner + "\n" + value(item, "assignedAt"));
        boolean waiting = "AWAITING_REVIEW".equals(value(item, "status"));
        boolean ours = owner.equals(admin.session().userId());
        if (waiting && owner.isEmpty()) controls.add(action(R.string.admin_claim, () -> mutate(cb -> admin.claim(id, cb))));
        if (waiting && ours) controls.add(action(R.string.admin_release, () -> mutate(cb -> admin.release(id, cb))));
        if (waiting || "RESOLVED".equals(value(item, "status"))) {
            if (owner.isEmpty() || ours) {
                note = new EditText(this);
                note.setHint(R.string.admin_decision_note);
                note.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                note.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
                note.setText(draft);
                content.addView(note);
                controls.add(note);
                String[] actions = {"NONE", "HIDE", "DELETE", "BAN"};
                int[] labels = {R.string.admin_none, R.string.admin_hide, R.string.admin_delete, R.string.admin_ban};
                for (int i = 0; i < actions.length; i++) {
                    String action = actions[i];
                    controls.add(action(labels[i], () -> new AlertDialog.Builder(this)
                            .setTitle(R.string.admin_confirm_decision)
                            .setMessage(getString(R.string.admin_confirm_action, ModerationLabels.action(this, action)))
                            .setNegativeButton(R.string.action_cancel, null)
                            .setPositiveButton(R.string.action_confirm, (dialog, which) -> mutate(cb -> admin.decide(id, action, note.getText().toString(), cb)))
                            .show()));
                }
            } else content.addView(text(getString(R.string.admin_other_reviewer), 15));
        }
        controls.add(action(R.string.admin_refresh, this::load));
        note(R.string.admin_audit, "");
        JSONArray audit = detail.optJSONArray("auditTrail");
        if (audit != null) for (int i = 0; i < audit.length(); i++) {
            JSONObject entry = audit.optJSONObject(i);
            content.addView(text(value(entry, "at") + " · " + value(entry, "actorType") + " " + value(entry, "actorId")
                    + "\n" + value(entry, "action") + "\n" + value(entry, "payload"), 13));
        }
    }
    private void evidence(int label, JSONObject evidence) {
        if (evidence == null) { note(label, getString(R.string.admin_missing_content)); return; }
        note(label, value(evidence, "title") + "\n" + value(evidence, "body"));
        String media = value(evidence, "mediaUrl");
        if (media.isEmpty()) return;
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setContentDescription(getString(label));
        content.addView(image, new LinearLayout.LayoutParams(-1, AppSheet.dp(this, 260)));
        android.widget.TextView mediaStatus = text(getString(R.string.admin_loading_image), 13);
        content.addView(mediaStatus);
        long screen = revision;
        admin.image(media, new BackendModerationGateway.Callback<>() {
            public void onSuccess(android.graphics.Bitmap bitmap) {
                if (!isDestroyed() && !isFinishing() && screen == revision && requireAdmin()) {
                    image.setImageBitmap(bitmap); mediaStatus.setVisibility(android.view.View.GONE);
                }
            }
            public void onError(BackendException error) {
                if (isDestroyed() || isFinishing() || screen != revision) return;
                if (!admin.session().hasSession()) signInAgain(); else mediaStatus.setText(error.getMessage());
            }
        });
    }
    private void mutate(Consumer<BackendModerationGateway.Callback<JSONObject>> operation) {
        controls.forEach(v -> v.setEnabled(false));
        status.setText(R.string.admin_saving);
        operation.accept(callback(++revision, result -> { draft = note == null ? draft : note.getText().toString(); render(result); }));
    }
    @Override protected void onRequestFailed(BackendException error) { controls.forEach(v -> v.setEnabled(true)); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("draft", note == null ? draft : note.getText().toString()); super.onSaveInstanceState(state);
    }
}
