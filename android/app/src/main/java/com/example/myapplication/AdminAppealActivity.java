package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.widget.EditText;
import org.json.JSONObject;
import androidx.appcompat.app.AlertDialog;
import backend.BackendException;
import java.util.ArrayList;
import java.util.List;

/** A pending appeal is decided by the server, which also revises the original case and its audit. */
public final class AdminAppealActivity extends AdminScreenActivity {
    private JSONObject appeal;
    private EditText response;
    private String draft = "";
    private final List<android.view.View> controls = new ArrayList<>();
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (!requireAdmin()) return;
        try {
            appeal = new JSONObject(saved == null ? getIntent().getStringExtra("appeal") : saved.getString("appeal"));
        } catch (Exception error) { finish(); return; }
        if (saved != null) draft = saved.getString("draft", "");
        screen(R.string.admin_appeal_detail);
        render();
    }
    private void render() {
        content.removeAllViews(); controls.clear();
        status.setText(value(appeal, "id"));
        note(R.string.admin_case_status, value(appeal, "status") + "\n" + value(appeal, "createdAt"));
        note(R.string.admin_appellant, value(appeal, "appellantId"));
        note(R.string.admin_appeal_reason, value(appeal, "reason"));
        action(R.string.admin_open_case, () -> startActivity(new Intent(this, AdminCaseActivity.class).putExtra("id", value(appeal, "caseId"))));
        if ("PENDING".equals(value(appeal, "status"))) {
            response = new EditText(this);
            response.setHint(R.string.admin_appeal_response);
            response.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            response.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
            response.setText(draft);
            content.addView(response); controls.add(response);
            controls.add(action(R.string.admin_uphold, () -> confirm("UPHOLD")));
            controls.add(action(R.string.admin_overturn, () -> confirm("OVERTURN")));
        } else {
            note(R.string.admin_appeal_response, value(appeal, "response"));
            note(R.string.admin_audit, value(appeal, "decidedBy") + "\n" + value(appeal, "decidedAt"));
        }
    }
    private void confirm(String decision) {
        new AlertDialog.Builder(this).setTitle(R.string.admin_confirm_decision)
                .setMessage(getString(R.string.admin_confirm_action, decision))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> {
                    draft = response.getText().toString();
                    controls.forEach(v -> v.setEnabled(false));
                    status.setText(R.string.admin_saving);
                    admin.decideAppeal(value(appeal, "id"), decision, draft, callback(++revision, result -> { appeal = result; render(); }));
                }).show();
    }
    @Override protected void onRequestFailed(BackendException error) { controls.forEach(v -> v.setEnabled(true)); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("appeal", appeal == null ? "{}" : appeal.toString());
        state.putString("draft", response == null ? draft : response.getText().toString()); super.onSaveInstanceState(state);
    }
}
