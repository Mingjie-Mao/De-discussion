package com.example.myapplication;

import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import dao.model.Message;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    private final List<Message> messages;
    private final Set<UUID> expandedTopLevelComments;
    private OnMessageActionListener onMessageActionListener;
    private OnMessageVoteListener onMessageVoteListener;
    private OnMessageReplyListener onMessageReplyListener;
    private OnReplyThreadToggleListener onReplyThreadToggleListener;
    private OnUserClickListener onUserClickListener;

    public MessageAdapter(List<Message> messages) {
        this(messages, java.util.Collections.emptySet());
    }

    public MessageAdapter(List<Message> messages, Set<UUID> expandedTopLevelComments) {
        this.messages = new java.util.ArrayList<>(messages);
        this.expandedTopLevelComments = expandedTopLevelComments;
    }

    public void updateMessages(List<Message> next) {
        List<Message> old = new java.util.ArrayList<>(messages);
        androidx.recyclerview.widget.DiffUtil.DiffResult diff =
            androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
                public int getOldListSize() { return old.size(); }
                public int getNewListSize() { return next.size(); }
                public boolean areItemsTheSame(int before, int after) {
                    return old.get(before).id().equals(next.get(after).id());
                }
                // Vote/expansion projections are outside Message: rebind retained
                // rows while preserving the adapter and scroll position.
                public boolean areContentsTheSame(int before, int after) { return false; }
            });
        messages.clear(); messages.addAll(next); diff.dispatchUpdatesTo(this);
    }

    public void setOnMessageActionListener(OnMessageActionListener onMessageActionListener) {
        this.onMessageActionListener = onMessageActionListener;
    }

    public void setOnMessageVoteListener(OnMessageVoteListener onMessageVoteListener) {
        this.onMessageVoteListener = onMessageVoteListener;
    }

    public void setOnMessageReplyListener(OnMessageReplyListener onMessageReplyListener) {
        this.onMessageReplyListener = onMessageReplyListener;
    }

    public void setOnReplyThreadToggleListener(OnReplyThreadToggleListener onReplyThreadToggleListener) {
        this.onReplyThreadToggleListener = onReplyThreadToggleListener;
    }

    public void setOnUserClickListener(OnUserClickListener onUserClickListener) {
        this.onUserClickListener = onUserClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.fragment_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = messages.get(position);
        UUID topLevelId = AppData.getTopLevelCommentId(message);
        boolean expanded = topLevelId != null && expandedTopLevelComments.contains(topLevelId);
        boolean showReplyToggle = shouldShowReplyToggle(message, position, topLevelId);
        holder.display(message, shouldShowTopSeparator(message, position), showReplyToggle, expanded);
        holder.buttonMessageUpvote.setOnClickListener(v -> vote(holder, message, 1));
        holder.buttonMessageDownvote.setOnClickListener(v -> vote(holder, message, -1));
        holder.buttonMessageReply.setOnClickListener(v -> {
            if (onMessageReplyListener != null) {
                onMessageReplyListener.onReply(message);
            }
        });
        holder.buttonMessageMenu.setOnClickListener(v -> {
            if (onMessageActionListener != null) {
                onMessageActionListener.onPrimaryAction(message);
            }
        });
        holder.textMessageAvatar.setOnClickListener(v -> openUser(message.poster()));
        holder.textMessageAuthor.setOnClickListener(v -> openUser(message.poster()));
        holder.textMessageReplyToggle.setOnClickListener(v -> {
            if (onReplyThreadToggleListener != null && topLevelId != null) {
                onReplyThreadToggleListener.onToggle(topLevelId);
            }
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    private boolean shouldShowTopSeparator(Message message, int position) {
        return false;
    }

    private boolean shouldShowReplyToggle(Message message, int position, UUID topLevelId) {
        if (topLevelId == null || AppData.getTopLevelReplyCount(message) <= 1) {
            return false;
        }
        if (AppData.getDisplayCommentDepth(message) == 0) {
            return false;
        }
        if (position == messages.size() - 1) {
            return true;
        }
        UUID nextTopLevelId = AppData.getTopLevelCommentId(messages.get(position + 1));
        return !topLevelId.equals(nextTopLevelId);
    }

    public interface OnMessageActionListener {
        void onPrimaryAction(Message message);
    }

    public interface OnMessageVoteListener {
        void onVote(Message message, int direction);
    }

    public interface OnMessageReplyListener {
        void onReply(Message message);
    }

    public interface OnReplyThreadToggleListener {
        void onToggle(UUID topLevelCommentId);
    }

    public interface OnUserClickListener {
        void onUserClick(UUID userId);
    }

    private void openUser(UUID userId) {
        if (onUserClickListener != null) {
            onUserClickListener.onUserClick(userId);
        }
    }

    private void vote(ViewHolder holder, Message message, int direction) {
        if (onMessageVoteListener != null) {
            onMessageVoteListener.onVote(message, direction);
            UUID topLevelId = AppData.getTopLevelCommentId(message);
            boolean expanded = topLevelId != null && expandedTopLevelComments.contains(topLevelId);
            holder.display(
                    message,
                    holder.viewMessageTopSeparator.getVisibility() == View.VISIBLE,
                    holder.textMessageReplyToggle.getVisibility() == View.VISIBLE,
                    expanded
            );
            animateVote(direction > 0 ? holder.buttonMessageUpvote : holder.buttonMessageDownvote);
        }
    }

    private void animateVote(View view) {
        view.animate().cancel();
        view.setScaleX(1.0f);
        view.setScaleY(1.0f);
        view.animate()
                .scaleX(1.16f)
                .scaleY(1.16f)
                .setDuration(110L)
                .withEndAction(() -> view.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(160L)
                        .start())
                .start();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final View messageRoot;
        private final View layoutMessageRow;
        private final View viewMessageTopSeparator;
        private final TextView textMessageAvatar;
        private final TextView textMessageAuthor;
        private final TextView textMessageTimestamp;
        private final TextView textMessageState;
        private final TextView textMessageContent;
        private final ImageView imageMessageAttachment;
        private final TextView textMessageScore;
        private final TextView textMessageReplyCount;
        private final TextView textMessageReplyToggle;
        private final ImageView imageMessageReplyIcon;
        private final ImageButton buttonMessageUpvote;
        private final ImageButton buttonMessageDownvote;
        private final View buttonMessageReply;
        private final ImageButton buttonMessageMenu;

        ViewHolder(View view) {
            super(view);
            messageRoot = view.findViewById(R.id.messageRoot);
            layoutMessageRow = view.findViewById(R.id.layoutMessageRow);
            viewMessageTopSeparator = view.findViewById(R.id.viewMessageTopSeparator);
            textMessageAvatar = view.findViewById(R.id.textMessageAvatar);
            textMessageAuthor = view.findViewById(R.id.textMessageAuthor);
            textMessageTimestamp = view.findViewById(R.id.textMessageTimestamp);
            textMessageState = view.findViewById(R.id.textMessageState);
            textMessageContent = view.findViewById(R.id.textMessageContent);
            imageMessageAttachment = view.findViewById(R.id.imageMessageAttachment);
            textMessageScore = view.findViewById(R.id.textMessageScore);
            textMessageReplyCount = view.findViewById(R.id.textMessageReplyCount);
            textMessageReplyToggle = view.findViewById(R.id.textMessageReplyToggle);
            imageMessageReplyIcon = view.findViewById(R.id.imageMessageReplyIcon);
            buttonMessageUpvote = view.findViewById(R.id.buttonMessageUpvote);
            buttonMessageDownvote = view.findViewById(R.id.buttonMessageDownvote);
            buttonMessageReply = view.findViewById(R.id.buttonMessageReply);
            buttonMessageMenu = view.findViewById(R.id.buttonMessageMenu);
        }

        void display(Message message, boolean showTopSeparator, boolean showReplyToggle, boolean expanded) {
            int upvoteColor = ContextCompat.getColor(itemView.getContext(), R.color.vote_up);
            int neutralColor = ContextCompat.getColor(itemView.getContext(), R.color.ink_faint);
            int downvoteColor = ContextCompat.getColor(itemView.getContext(), R.color.vote_down);
            int primaryColor = ContextCompat.getColor(itemView.getContext(), R.color.ink_primary);
            int reportColor = ContextCompat.getColor(itemView.getContext(), R.color.danger_ink);

            int depth = AppData.getDisplayCommentDepth(message);
            viewMessageTopSeparator.setVisibility(showTopSeparator ? View.VISIBLE : View.GONE);
            int indent = Math.round(depth * 40 * itemView.getResources().getDisplayMetrics().density);
            layoutMessageRow.setPaddingRelative(indent, layoutMessageRow.getPaddingTop(), layoutMessageRow.getPaddingEnd(), layoutMessageRow.getPaddingBottom());

            textMessageAvatar.setText(AppData.getAvatarLetter(itemView.getContext(), message.poster()));
            int avatarSize = Math.round((depth > 0 ? 24 : 32) * itemView.getResources().getDisplayMetrics().density);
            ViewGroup.LayoutParams avatarParams = textMessageAvatar.getLayoutParams();
            avatarParams.width = avatarSize;
            avatarParams.height = avatarSize;
            textMessageAvatar.setLayoutParams(avatarParams);
            textMessageAvatar.setTextSize(depth > 0 ? 11 : 14);
            GradientDrawable avatarBackground = (GradientDrawable) ContextCompat.getDrawable(itemView.getContext(), R.drawable.bg_avatar_circle).mutate();
            avatarBackground.setColor(AppData.getAvatarColor(itemView.getContext(), message.poster()));
            textMessageAvatar.setBackground(avatarBackground);
            AvatarRenderer.display(textMessageAvatar, AppData.getAvatarUrl(itemView.getContext(), message.poster()),
                    AppData.getAvatarLetter(itemView.getContext(), message.poster()), AppData.getAvatarColor(itemView.getContext(), message.poster()));

            textMessageAuthor.setText(AppData.getMessageAuthorDisplayName(itemView.getContext(), message));
            textMessageTimestamp.setText(AppData.formatTimestamp(message.timestamp()));
            String status = AppData.getMessageStatus(itemView.getContext(), message);
            textMessageState.setText(status);
            textMessageState.setVisibility(status.isEmpty() ? View.GONE : View.VISIBLE);
            String content = AppData.getMessageDisplayContent(itemView.getContext(), message);
            textMessageContent.setText(content);
            textMessageContent.setVisibility(content == null || content.isEmpty() ? View.GONE : View.VISIBLE);
            String imageUri = AppData.getMessageImageUri(message);
            if (imageUri == null || imageUri.isEmpty()) {
                imageMessageAttachment.setImageDrawable(null);
                imageMessageAttachment.setOnClickListener(null);
                imageMessageAttachment.setVisibility(View.GONE);
            } else {
                Uri attachmentUri = Uri.parse(imageUri);
                RemoteImageLoader.display(imageMessageAttachment, attachmentUri);
                imageMessageAttachment.setOnClickListener(v ->
                        ImageAttachmentViewer.show(itemView.getContext(), attachmentUri, R.string.message_image_attachment));
                imageMessageAttachment.setVisibility(View.VISIBLE);
            }
            textMessageScore.setText(String.valueOf(AppData.getMessageVoteScore(message)));
            boolean votePending = ServerFeatures.votePending("comments", message.id());
            buttonMessageUpvote.setEnabled(!votePending);
            buttonMessageDownvote.setEnabled(!votePending);
            textMessageScore.setAlpha(votePending ? .6f : 1f);

            int replyCount = AppData.getMessageReplyCount(message);
            textMessageReplyCount.setText(String.valueOf(replyCount));
            if (showReplyToggle) {
                if (expanded) {
                    textMessageReplyToggle.setText(R.string.action_collapse_replies);
                } else {
                    int hiddenReplies = Math.max(1, AppData.getTopLevelReplyCount(message) - 1);
                    textMessageReplyToggle.setText(itemView.getContext().getString(R.string.action_expand_replies, hiddenReplies));
                }
                textMessageReplyToggle.setVisibility(View.VISIBLE);
                textMessageReplyToggle.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.ink_secondary));
            } else {
                textMessageReplyToggle.setVisibility(View.GONE);
            }

            int voteDirection = AppData.getCurrentUserMessageVote(message);
            buttonMessageUpvote.setImageResource(voteDirection > 0
                    ? R.drawable.ic_message_vote_up_filled_24
                    : R.drawable.ic_message_vote_up_outline_24);
            buttonMessageDownvote.setImageResource(voteDirection < 0
                    ? R.drawable.ic_message_vote_down_filled_24
                    : R.drawable.ic_message_vote_down_outline_24);
            boolean activeAction = AppData.isAdminMode() ? AppData.isHidden(message) : AppData.hasCurrentUserReported(message);
            if (AppData.isAdminMode()) {
                buttonMessageMenu.setImageResource(R.drawable.ic_hidden_24);
            } else {
                buttonMessageMenu.setImageResource(R.drawable.ic_flag_outline_24);
            }
            buttonMessageMenu.setColorFilter(activeAction ? reportColor : neutralColor);
            imageMessageReplyIcon.setColorFilter(primaryColor);
            textMessageReplyCount.setTextColor(primaryColor);
            textMessageScore.setTextColor(voteDirection > 0
                    ? upvoteColor
                    : voteDirection < 0 ? downvoteColor : primaryColor);
        }

    }
}
