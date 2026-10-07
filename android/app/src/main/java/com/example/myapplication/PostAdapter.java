package com.example.myapplication;

import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.UUID;

import dao.model.Post;

public class PostAdapter extends RecyclerView.Adapter<PostAdapter.ViewHolder> {
    private final java.util.ArrayList<Post> posts;
    private final java.util.Map<UUID,Long> stableIds=new java.util.HashMap<>();
    private long nextStableId;
    private OnClickListener onClickListener;
    private OnVoteClickListener onVoteClickListener;
    private OnUserClickListener onUserClickListener;
    private OnBookmarkClickListener onBookmarkClickListener;

    public PostAdapter(List<Post> posts) {
        this.posts = new java.util.ArrayList<>(posts);
        setHasStableIds(true);
    }

    @Override public long getItemId(int position) {
        return stableIds.computeIfAbsent(posts.get(position).id,key -> nextStableId++);
    }

    /** Keep visible holders and their loaded images across social and translation updates. */
    public void replacePosts(List<Post> values) {
        var old=new java.util.ArrayList<>(posts);
        var next=new java.util.ArrayList<>(values);
        var diff=androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
            public int getOldListSize(){return old.size();}
            public int getNewListSize(){return next.size();}
            public boolean areItemsTheSame(int a,int b){return old.get(a).id.equals(next.get(b).id);}
            public boolean areContentsTheSame(int a,int b){return false;}
            public Object getChangePayload(int a,int b){return "refresh";}
        });
        posts.clear();posts.addAll(next);diff.dispatchUpdatesTo(this);
    }

    public void setOnClickListener(OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    public void setOnVoteClickListener(OnVoteClickListener onVoteClickListener) {
        this.onVoteClickListener = onVoteClickListener;
    }

    public void setOnUserClickListener(OnUserClickListener onUserClickListener) {
        this.onUserClickListener = onUserClickListener;
    }

    public void setOnBookmarkClickListener(OnBookmarkClickListener onBookmarkClickListener) {
        this.onBookmarkClickListener = onBookmarkClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_post, parent, false);
        view.setForeground(null);
        view.setFocusable(false);
        view.setLongClickable(false);
        view.setSelected(false);
        view.setPressed(false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = posts.get(position);
        holder.itemView.setForeground(null);
        holder.itemView.setFocusable(false);
        holder.itemView.setLongClickable(false);
        holder.display(post);
        holder.itemView.setSelected(false);
        holder.itemView.setPressed(false);
        holder.itemView.setOnClickListener(v -> openPost(post));
        holder.layoutPostCommunity.setOnClickListener(v -> openUser(post.poster));
        holder.buttonPostComments.setOnClickListener(v -> openPost(post));
        holder.buttonPostUpvote.setOnClickListener(v -> vote(holder, post, 1));
        holder.buttonPostDownvote.setOnClickListener(v -> vote(holder, post, -1));
        holder.buttonPostBookmark.setOnClickListener(v -> bookmark(holder, post));
    }

    @Override
    public int getItemCount() {
        return posts.size();
    }

    public interface OnClickListener {
        void onClick(Post post);
    }

    public interface OnVoteClickListener {
        void onVote(Post post, int direction);
    }

    public interface OnUserClickListener {
        void onUserClick(UUID userId);
    }

    public interface OnBookmarkClickListener {
        void onBookmark(Post post);
    }

    private void openPost(Post post) {
        if (onClickListener != null) {
            onClickListener.onClick(post);
        }
    }

    private void vote(ViewHolder holder, Post post, int direction) {
        if (onVoteClickListener != null) {
            onVoteClickListener.onVote(post, direction);
            holder.display(post);
            animateVote(direction > 0 ? holder.buttonPostUpvote : holder.buttonPostDownvote);
        }
    }

    private void bookmark(ViewHolder holder, Post post) {
        if (onBookmarkClickListener != null) {
            onBookmarkClickListener.onBookmark(post);
            holder.display(post);
            animateVote(holder.imagePostBookmarkIcon);
        }
    }

    private void openUser(UUID userId) {
        if (onUserClickListener != null) {
            onUserClickListener.onUserClick(userId);
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
        private final TextView textPostMeta;
        private final TextView textPostCommunity;
        private final TextView textPostCategory;
        private final TextView textPostPosterAvatar;
        private final TextView textPostTitle;
        private final TextView textPostBody;
        private final ImageView imagePostAttachment;
        private final TextView textPostScore;
        private final ImageButton buttonPostUpvote;
        private final ImageButton buttonPostDownvote;
        private final LinearLayout buttonPostBookmark;
        private final LinearLayout layoutPostCommunity;
        private final LinearLayout buttonPostComments;
        private final TextView textPostCommentsCount;
        private final TextView textPostBookmarkCount;
        private final ImageView imagePostBookmarkIcon;

        ViewHolder(View view) {
            super(view);
            textPostMeta = view.findViewById(R.id.textPostMeta);
            textPostCommunity = view.findViewById(R.id.textPostCommunity);
            textPostCategory = view.findViewById(R.id.textPostCategory);
            textPostPosterAvatar = view.findViewById(R.id.textPostPosterAvatar);
            textPostTitle = view.findViewById(R.id.textPostTitle);
            textPostBody = view.findViewById(R.id.textPostBody);
            imagePostAttachment = view.findViewById(R.id.imagePostAttachment);
            textPostScore = view.findViewById(R.id.textPostScore);
            buttonPostUpvote = view.findViewById(R.id.buttonPostUpvote);
            buttonPostDownvote = view.findViewById(R.id.buttonPostDownvote);
            buttonPostBookmark = view.findViewById(R.id.buttonPostBookmark);
            layoutPostCommunity = view.findViewById(R.id.layoutPostCommunity);
            buttonPostComments = view.findViewById(R.id.buttonPostComments);
            textPostCommentsCount = view.findViewById(R.id.textPostCommentsCount);
            textPostBookmarkCount = view.findViewById(R.id.textPostBookmarkCount);
            imagePostBookmarkIcon = view.findViewById(R.id.imagePostBookmarkIcon);
        }

        void display(Post post) {
            int upvoteColor = ContextCompat.getColor(itemView.getContext(), R.color.vote_up);
            int neutralColor = ContextCompat.getColor(itemView.getContext(), R.color.ink_secondary);
            int downvoteColor = ContextCompat.getColor(itemView.getContext(), R.color.vote_down);

            textPostCommunity.setText(AppData.getPostCommunityLabel(itemView.getContext(), post));
            textPostMeta.setText(AppData.getPostFeedByline(itemView.getContext(), post));
            textPostCategory.setText(AppData.getPostCategory(itemView.getContext(), post));
            GradientDrawable avatarBg = new GradientDrawable();
            avatarBg.setShape(GradientDrawable.OVAL);
            avatarBg.setColor(AppData.getAvatarColor(itemView.getContext(), post.poster));
            textPostPosterAvatar.setBackground(avatarBg);
            textPostPosterAvatar.setText(AppData.getAvatarLetter(post.poster));
            AvatarRenderer.display(textPostPosterAvatar, AppData.getAvatarUrl(itemView.getContext(), post.poster),
                    AppData.getAvatarLetter(post.poster), AppData.getAvatarColor(itemView.getContext(), post.poster));
            textPostTitle.setText(AppData.getPostDisplayTitle(itemView.getContext(), post));
            textPostBody.setText(AppData.getPostDisplayBodyPreview(itemView.getContext(), post));
            String imageUri = AppData.getPostImageUri(post);
            if (imageUri == null || imageUri.isEmpty()) {
                imagePostAttachment.setImageDrawable(null);
                imagePostAttachment.setOnClickListener(null);
                imagePostAttachment.setVisibility(View.GONE);
            } else {
                Uri attachmentUri = Uri.parse(imageUri);
                RemoteImageLoader.display(imagePostAttachment, attachmentUri);
                imagePostAttachment.setOnClickListener(v ->
                        ImageAttachmentViewer.show(itemView.getContext(), attachmentUri, R.string.post_image_attachment));
                imagePostAttachment.setVisibility(View.VISIBLE);
            }
            textPostScore.setText(String.valueOf(AppData.getPostVoteScore(post)));
            boolean votePending = ServerFeatures.votePending("posts", post.id);
            buttonPostUpvote.setEnabled(!votePending);
            buttonPostDownvote.setEnabled(!votePending);
            textPostScore.setAlpha(votePending ? .6f : 1f);

            textPostCommentsCount.setText(AppData.getPostReplyCountLabel(itemView.getContext(), post));
            textPostBookmarkCount.setText(AppData.getPostBookmarkCountLabel(itemView.getContext(), post));

            int voteDirection = AppData.getCurrentUserPostVote(post);
            buttonPostUpvote.setImageResource(voteDirection > 0
                    ? R.drawable.ic_vote_up_filled_24
                    : R.drawable.ic_vote_up_outline_24);
            buttonPostDownvote.setImageResource(voteDirection < 0
                    ? R.drawable.ic_vote_down_filled_24
                    : R.drawable.ic_vote_down_outline_24);
            boolean bookmarked = AppData.hasBookmarkedPost(post);
            imagePostBookmarkIcon.setImageResource(bookmarked
                    ? R.drawable.ic_bookmark_filled_24
                    : R.drawable.ic_bookmark_24);
            imagePostBookmarkIcon.setColorFilter(bookmarked
                    ? ContextCompat.getColor(itemView.getContext(), R.color.rank_gold)
                    : ContextCompat.getColor(itemView.getContext(), R.color.ink_primary));
            textPostScore.setTextColor(voteDirection > 0
                    ? upvoteColor
                    : voteDirection < 0 ? downvoteColor : ContextCompat.getColor(itemView.getContext(), R.color.ink_primary));
        }
    }
}
