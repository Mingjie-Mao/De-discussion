package com.example.myapplication;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import backend.BackendReviewCase;
import dao.model.Message;
import dao.model.Post;

/** Server-backed case cards with the actual engine recommendation. */
public class BackendReportedCaseAdapter
        extends RecyclerView.Adapter<BackendReportedCaseAdapter.ViewHolder> {
    private final List<BackendReviewCase> cases;
    private OnCaseListener onOpen;
    private OnCaseListener onReview;

    public BackendReportedCaseAdapter(List<BackendReviewCase> cases) {
        this.cases = cases;
    }

    public void setOnOpen(OnCaseListener listener) {
        this.onOpen = listener;
    }

    public void setOnReview(OnCaseListener listener) {
        this.onReview = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reported_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BackendReviewCase item = cases.get(position);
        holder.display(item);
        holder.itemView.setOnClickListener(v -> {
            if (onOpen != null) {
                onOpen.onCase(item);
            }
        });
        holder.action.setOnClickListener(v -> {
            if (onReview != null) {
                onReview.onCase(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return cases.size();
    }

    public interface OnCaseListener {
        void onCase(BackendReviewCase item);
    }

    static final class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView thread;
        private final TextView author;
        private final TextView content;
        private final TextView meta;
        private final Button action;

        ViewHolder(View view) {
            super(view);
            thread = view.findViewById(R.id.textReportedThread);
            author = view.findViewById(R.id.textReportedAuthor);
            content = view.findViewById(R.id.textReportedContent);
            meta = view.findViewById(R.id.textReportedMeta);
            action = view.findViewById(R.id.buttonReportedAction);
        }

        void display(BackendReviewCase item) {
            // The server stores whatever text was mirrored when the report was
            // filed, which is fixed in one language. When the reported item is
            // still in the local feed, show the local copy so the queue reads in
            // the language the app is currently in; the server's copy is the
            // fallback for content this install no longer has.
            Message local = AppData.findModerationMessage(item.localTargetId());
            Post localPost = local == null ? null : AppData.getPostForMessage(local);

            thread.setText(localPost != null ? AppData.getPostTitle(localPost) : item.title());
            author.setText(itemView.getContext().getString(
                    R.string.backend_case_engine,
                    item.engine(),
                    ModerationLabels.decision(itemView.getContext(), item.recommendedDecision()),
                    Math.round(item.confidence() * 100)));
            content.setText(local != null ? local.message() : item.body());

            // A decided case leads with the outcome that is actually in force.
            // The engine's recommendation is above it, so showing the rationale
            // again here would bury the one line that says what happened.
            meta.setText(item.isDecided()
                    ? itemView.getContext().getString(
                            R.string.backend_case_decided_meta,
                            ModerationLabels.action(itemView.getContext(), item.finalAction()),
                            item.reportCount())
                    : itemView.getContext().getString(
                            R.string.backend_case_meta,
                            item.reportCount(),
                            item.rationale()));
            action.setText(item.isDecided() ? R.string.action_change_decision : R.string.action_review);

            MaterialButton material = (MaterialButton) action;
            material.setIcon(ContextCompat.getDrawable(
                    itemView.getContext(), R.drawable.ic_shield_outline_24));
            material.setIconTint(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), R.color.nav_active)));
        }
    }
}
