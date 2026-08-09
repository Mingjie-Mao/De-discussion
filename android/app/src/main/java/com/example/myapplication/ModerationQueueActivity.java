package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

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


public class ModerationQueueActivity extends AppCompatActivity {
    private String strategy = AppData.STRATEGY_OLDEST;

    private TextView textQueueSubtitle;
    private TextView textQueueEmpty;
    private Button buttonQueueBack;
    private Button buttonOldest;
    private Button buttonMost;
    private RecyclerView recyclerReportedMessages;
    private int loadGeneration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        UiPreferences.applyAppearance(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_moderation_queue);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.queueRoot), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    v.getPaddingLeft(),
                    systemBars.top + v.getPaddingTop(),
                    v.getPaddingRight(),
                    systemBars.bottom + v.getPaddingBottom()
            );
            return insets;
        });

        textQueueSubtitle = findViewById(R.id.textQueueSubtitle);
        textQueueEmpty = findViewById(R.id.textQueueEmpty);
        buttonQueueBack = findViewById(R.id.buttonQueueBack);
        buttonOldest = findViewById(R.id.buttonOldest);
        buttonMost = findViewById(R.id.buttonMost);
        recyclerReportedMessages = findViewById(R.id.recyclerReportedMessages);

        recyclerReportedMessages.setLayoutManager(new LinearLayoutManager(this));

        buttonQueueBack.setOnClickListener(v -> finish());
        buttonOldest.setOnClickListener(v -> {
            strategy = AppData.STRATEGY_OLDEST;
            refreshUi();
        });
        buttonMost.setOnClickListener(v -> {
            strategy = AppData.STRATEGY_MOST;
            refreshUi();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    private void refreshUi() {
        if (!AppData.isAdminMode()) {
            finish();
            return;
        }

        BackendRuntime runtime = BackendRuntime.from(this);
        if (runtime.config().isEnabled()) {
            refreshOnlineUi(runtime);
        } else {
            refreshOfflineUi();
        }
    }

    private void refreshOfflineUi() {
        ArrayList<Message> messages = AppData.getReportedMessages(strategy);
        textQueueSubtitle.setText(AppData.getQueueSubtitle(this, strategy, messages.size()));
        textQueueEmpty.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);

        buttonOldest.setEnabled(!AppData.STRATEGY_OLDEST.equals(strategy));
        buttonOldest.setAlpha(AppData.STRATEGY_OLDEST.equals(strategy) ? 1.0f : 0.7f);
        buttonMost.setEnabled(!AppData.STRATEGY_MOST.equals(strategy));
        buttonMost.setAlpha(AppData.STRATEGY_MOST.equals(strategy) ? 1.0f : 0.7f);

        ReportedMessageAdapter adapter = new ReportedMessageAdapter(messages);
        adapter.setOnOpenThreadListener(this::openThread);
        adapter.setOnToggleHiddenListener(message -> {
            AppData.toggleHidden(message);
            refreshUi();
        });
        recyclerReportedMessages.setAdapter(adapter);
    }

    private void refreshOnlineUi(BackendRuntime runtime) {
        int generation = ++loadGeneration;
        textQueueSubtitle.setText(R.string.backend_queue_loading);
        textQueueEmpty.setVisibility(View.GONE);
        recyclerReportedMessages.setAdapter(new BackendReportedCaseAdapter(List.of()));
        updateSortButtons();

        runtime.moderation().fetchReviewCases(new BackendModerationGateway.Callback<>() {
            @Override
            public void onSuccess(List<BackendReviewCase> cases) {
                if (generation != loadGeneration || isFinishing() || isDestroyed()) {
                    return;
                }

                ArrayList<BackendReviewCase> sorted = new ArrayList<>(cases);
                if (AppData.STRATEGY_MOST.equals(strategy)) {
                    sorted.sort(Comparator
                            .comparingInt(BackendReviewCase::reportCount)
                            .reversed()
                            .thenComparing(BackendReviewCase::createdAt));
                } else {
                    sorted.sort(Comparator.comparing(BackendReviewCase::createdAt));
                }

                textQueueSubtitle.setText(getString(
                        R.string.backend_queue_count, sorted.size()));
                textQueueEmpty.setText(R.string.backend_queue_empty);
                textQueueEmpty.setVisibility(sorted.isEmpty() ? View.VISIBLE : View.GONE);

                BackendReportedCaseAdapter adapter = new BackendReportedCaseAdapter(sorted);
                adapter.setOnOpen(item -> {
                    Message local = AppData.findModerationMessage(item.localTargetId());
                    if (local == null) {
                        showReviewDialog(item);
                    } else {
                        openThread(local);
                    }
                });
                adapter.setOnReview(ModerationQueueActivity.this::showReviewDialog);
                recyclerReportedMessages.setAdapter(adapter);
            }

            @Override
            public void onError(BackendException error) {
                if (generation != loadGeneration || isFinishing() || isDestroyed()) {
                    return;
                }
                textQueueSubtitle.setText(
                        getString(R.string.backend_queue_error, error.getMessage()));
                textQueueEmpty.setText(R.string.backend_queue_configure);
                textQueueEmpty.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateSortButtons() {
        buttonOldest.setEnabled(!AppData.STRATEGY_OLDEST.equals(strategy));
        buttonOldest.setAlpha(AppData.STRATEGY_OLDEST.equals(strategy) ? 1.0f : 0.7f);
        buttonMost.setEnabled(!AppData.STRATEGY_MOST.equals(strategy));
        buttonMost.setAlpha(AppData.STRATEGY_MOST.equals(strategy) ? 1.0f : 0.7f);
    }

    private void showReviewDialog(BackendReviewCase item) {
        String details = getString(
                R.string.backend_review_details,
                item.engine(),
                ModerationLabels.decision(this, item.recommendedDecision()),
                Math.round(item.confidence() * 100),
                item.rationale(),
                localBodyOf(item));

        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout content = AppSheet.sheet(this, localTitleOf(item), null, () -> holder[0].dismiss());
        content.addView(AppSheet.note(this, details));

        // Full-width rows rather than the dialog's own buttons: three outcomes do
        // not fit as Material actions without one of them becoming the "neutral"
        // button, which reads as less important than it is.
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

        content.addView(AppSheet.optionRow(
                this,
                R.drawable.ic_shield_outline_24,
                getString(labelResId),
                false,
                () -> {
                    holder[0].dismiss();
                    submitDecision(item, action);
                }));
    }

    private void submitDecision(BackendReviewCase item, String action) {
        textQueueSubtitle.setText(R.string.backend_decision_submitting);
        BackendRuntime.from(this).moderation().decide(
                item.id(),
                action,
                getString(R.string.backend_decision_note),
                new BackendModerationGateway.Callback<>() {
                    @Override
                    public void onSuccess(Void ignored) {
                        Message local = AppData.findModerationMessage(item.localTargetId());
                        if (local != null) {
                            AppData.setHidden(local, !"NONE".equals(action));
                        }
                        android.widget.Toast.makeText(
                                ModerationQueueActivity.this,
                                R.string.backend_decision_saved,
                                android.widget.Toast.LENGTH_SHORT).show();
                        refreshUi();
                    }

                    @Override
                    public void onError(BackendException error) {
                        android.widget.Toast.makeText(
                                ModerationQueueActivity.this,
                                getString(R.string.backend_decision_failed, error.getMessage()),
                                android.widget.Toast.LENGTH_LONG).show();
                        refreshUi();
                    }
                });
    }

    private void openThread(Message message) {
        Post post = AppData.getPostForMessage(message);
        if (post == null) {
            return;
        }

        Intent intent = new Intent(getApplicationContext(), PostViewerActivity.class);
        intent.putExtra(PostViewerActivity.EXTRA_POST_ID, post.id.toString());
        startActivity(intent);
    }
}
