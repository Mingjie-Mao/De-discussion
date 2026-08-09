package com.example.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import backend.BackendException;
import backend.BackendModerationGateway;
import backend.BackendReviewCase;
import backend.BackendRuntime;
import dao.model.Message;
import dao.model.Post;

/**
 * What the engine judged and a person has already ruled on.
 *
 * <p>Separate from the review queue because the two answer different questions:
 * the queue is work outstanding, this is the record of work done. It is
 * editable, since a reviewer who hid the wrong comment needs somewhere to put
 * it back — the backend treats a second decision as a correction and appends it
 * to the audit trail rather than overwriting the first.
 */
public class ModerationRecordsActivity extends AppCompatActivity {

    private static final String SORT_RECENT = "RECENT";
    private static final String SORT_ACTION = "ACTION";

    private String sort = SORT_RECENT;
    private int loadGeneration;

    private TextView subtitle;
    private TextView empty;
    private Button buttonRecent;
    private Button buttonByAction;
    private RecyclerView recycler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiPreferences.applyAppearance(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_moderation_records);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recordsRoot), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    bars.top + v.getPaddingTop(),
                    v.getPaddingRight(),
                    bars.bottom + v.getPaddingBottom());
            return insets;
        });

        subtitle = findViewById(R.id.textRecordsSubtitle);
        empty = findViewById(R.id.textRecordsEmpty);
        buttonRecent = findViewById(R.id.buttonRecordsRecent);
        buttonByAction = findViewById(R.id.buttonRecordsByAction);
        recycler = findViewById(R.id.recyclerRecords);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        findViewById(R.id.buttonRecordsBack).setOnClickListener(v -> finish());
        buttonRecent.setOnClickListener(v -> {
            sort = SORT_RECENT;
            refresh();
        });
        buttonByAction.setOnClickListener(v -> {
            sort = SORT_ACTION;
            refresh();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (!AppData.isAdminMode()) {
            finish();
            return;
        }

        BackendRuntime runtime = BackendRuntime.from(this);
        if (!runtime.config().isEnabled()) {
            // There is no local equivalent of this screen: the record of who
            // decided what only exists on the server.
            subtitle.setText(R.string.backend_status_offline);
            empty.setText(R.string.backend_queue_configure);
            empty.setVisibility(View.VISIBLE);
            recycler.setAdapter(new BackendReportedCaseAdapter(List.of()));
            return;
        }

        int generation = ++loadGeneration;
        subtitle.setText(R.string.backend_records_loading);
        empty.setVisibility(View.GONE);
        updateSortButtons();

        runtime.moderation().fetchResolvedCases(new BackendModerationGateway.Callback<>() {
            @Override
            public void onSuccess(List<BackendReviewCase> cases) {
                if (isStale(generation)) {
                    return;
                }

                ArrayList<BackendReviewCase> sorted = new ArrayList<>(cases);
                if (SORT_ACTION.equals(sort)) {
                    sorted.sort(Comparator
                            .comparing(BackendReviewCase::finalAction)
                            .thenComparing(Comparator.comparing(BackendReviewCase::decidedAt).reversed()));
                } else {
                    sorted.sort(Comparator.comparing(BackendReviewCase::decidedAt).reversed());
                }

                subtitle.setText(getString(R.string.backend_records_count, sorted.size()));
                empty.setText(R.string.backend_records_empty);
                empty.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);

                BackendReportedCaseAdapter adapter = new BackendReportedCaseAdapter(sorted);
                adapter.setOnOpen(ModerationRecordsActivity.this::showRecordDialog);
                adapter.setOnReview(ModerationRecordsActivity.this::showRecordDialog);
                recycler.setAdapter(adapter);
            }

            @Override
            public void onError(BackendException error) {
                if (isStale(generation)) {
                    return;
                }
                subtitle.setText(getString(R.string.backend_records_error, error.getMessage()));
                empty.setText(R.string.backend_queue_configure);
                empty.setVisibility(View.VISIBLE);
            }
        });
    }

    private boolean isStale(int generation) {
        return generation != loadGeneration || isFinishing() || isDestroyed();
    }

    private void updateSortButtons() {
        buttonRecent.setEnabled(!SORT_RECENT.equals(sort));
        buttonRecent.setAlpha(SORT_RECENT.equals(sort) ? 1.0f : 0.7f);
        buttonByAction.setEnabled(!SORT_ACTION.equals(sort));
        buttonByAction.setAlpha(SORT_ACTION.equals(sort) ? 1.0f : 0.7f);
    }

    /**
     * The case as decided, with the three outcomes available as a correction.
     *
     * <p>The outcome already in force is shown selected and simply closes the
     * sheet if chosen again — the backend would refuse it as a no-op change, and
     * an error is the wrong answer to someone confirming what is already true.
     */
    private void showRecordDialog(BackendReviewCase item) {
        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout content = AppSheet.sheet(
                this, localTitleOf(item), null, () -> holder[0].dismiss());

        content.addView(AppSheet.note(this, getString(
                R.string.backend_record_details,
                item.engine(),
                ModerationLabels.decision(this, item.recommendedDecision()),
                Math.round(item.confidence() * 100),
                ModerationLabels.action(this, item.finalAction()),
                item.rationale(),
                localBodyOf(item))));

        addOutcome(content, holder, item, "NONE", R.string.backend_decision_keep);
        addOutcome(content, holder, item, "HIDE", R.string.backend_decision_hide);
        addOutcome(content, holder, item, "DELETE", R.string.backend_decision_delete);
        addOutcome(content, holder, item, "BAN", R.string.backend_decision_ban);

        holder[0] = AppSheet.show(this, content);
    }

    private String localTitleOf(BackendReviewCase item) {
        Message local = AppData.findModerationMessage(item.localTargetId());
        Post post = local == null ? null : AppData.getPostForMessage(local);
        return post != null ? AppData.getPostTitle(post) : item.title();
    }

    /** Prefer the local copy so the sheet reads in the app's language. */
    private String localBodyOf(BackendReviewCase item) {
        Message local = AppData.findModerationMessage(item.localTargetId());
        return local != null ? local.message() : item.body();
    }

    private void addOutcome(
            LinearLayout content,
            AlertDialog[] holder,
            BackendReviewCase item,
            String action,
            int labelResId) {

        boolean current = action.equals(item.finalAction());
        content.addView(AppSheet.optionRow(
                this,
                R.drawable.ic_shield_outline_24,
                getString(labelResId),
                current,
                () -> {
                    holder[0].dismiss();
                    if (current) {
                        Toast.makeText(this, R.string.backend_record_unchanged, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    submitRevision(item, action);
                }));
    }

    private void submitRevision(BackendReviewCase item, String action) {
        subtitle.setText(R.string.backend_decision_submitting);

        BackendRuntime.from(this).moderation().decide(
                item.id(),
                action,
                getString(R.string.backend_record_note),
                new BackendModerationGateway.Callback<>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        // Keep the local feed consistent with the outcome that is
                        // now in force, including putting a comment back when the
                        // correction was to stop hiding it.
                        Message local = AppData.findModerationMessage(item.localTargetId());
                        if (local != null) {
                            AppData.setHidden(local, !"NONE".equals(action));
                        }
                        Toast.makeText(
                                ModerationRecordsActivity.this,
                                R.string.backend_decision_saved,
                                Toast.LENGTH_SHORT).show();
                        refresh();
                    }

                    @Override
                    public void onError(BackendException error) {
                        Toast.makeText(
                                ModerationRecordsActivity.this,
                                getString(R.string.backend_decision_failed, error.getMessage()),
                                Toast.LENGTH_LONG).show();
                        refresh();
                    }
                });
    }
}
