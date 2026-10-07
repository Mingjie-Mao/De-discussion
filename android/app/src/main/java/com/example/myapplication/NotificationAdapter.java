package com.example.myapplication;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {
    private final List<AppData.AppNotification> notifications;
    private OnClickListener onClickListener;

    public NotificationAdapter(List<AppData.AppNotification> notifications) {
        this.notifications = notifications;
    }

    public void setOnClickListener(OnClickListener onClickListener) {
        this.onClickListener = onClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppData.AppNotification notification = notifications.get(position);
        holder.display(notification);
        holder.itemView.setOnClickListener(v -> {
            if (onClickListener != null) {
                onClickListener.onClick(notification);
            }
        });
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    public interface OnClickListener {
        void onClick(AppData.AppNotification notification);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textNotificationAvatar;
        private final ImageView imageNotificationType;
        private final TextView textNotificationTitle;
        private final TextView textNotificationTime;
        private final TextView textNotificationBody;

        ViewHolder(View view) {
            super(view);
            textNotificationAvatar = view.findViewById(R.id.textNotificationAvatar);
            imageNotificationType = view.findViewById(R.id.imageNotificationType);
            textNotificationTitle = view.findViewById(R.id.textNotificationTitle);
            textNotificationTime = view.findViewById(R.id.textNotificationTime);
            textNotificationBody = view.findViewById(R.id.textNotificationBody);
        }

        void display(AppData.AppNotification notification) {
            boolean remote=notification.remoteNotificationId()!=null;
            boolean social=notification.type()==AppData.NotificationType.LIKE||notification.type()==AppData.NotificationType.BOOKMARK
                    ||notification.type()==AppData.NotificationType.COMMENT||notification.type()==AppData.NotificationType.MENTION||notification.type()==AppData.NotificationType.FOLLOW;
            String source=notification.title()==null||notification.title().isBlank()?"?":notification.title().trim().split("\\s+",2)[0];
            textNotificationAvatar.setText(remote?(social?source.substring(0,source.offsetByCodePoints(0,1)).toUpperCase(java.util.Locale.ROOT):"M"):AppData.getAvatarLetter(itemView.getContext(),notification.actorId()));
            GradientDrawable avatarBackground = (GradientDrawable) ContextCompat
                    .getDrawable(itemView.getContext(), R.drawable.bg_avatar_circle)
                    .mutate();
            avatarBackground.setColor(remote?UiPreferences.getGoogleColor(Math.floorMod(source.hashCode(),UiPreferences.getGoogleColorCount())):AppData.getAvatarColor(itemView.getContext(),notification.actorId()));
            textNotificationAvatar.setBackground(avatarBackground);
            textNotificationTitle.setText(notification.title());
            textNotificationTime.setText(AppData.formatTimestamp(notification.timestamp()));
            textNotificationBody.setText(notification.body());
            textNotificationBody.setVisibility(java.util.Objects.equals(notification.title(),notification.body())?View.GONE:View.VISIBLE);

            int iconRes = R.drawable.ic_comment_outline_24;
            if (notification.type() == AppData.NotificationType.LIKE) {
                iconRes = R.drawable.ic_vote_up_filled_24;
            } else if (notification.type() == AppData.NotificationType.BOOKMARK) {
                iconRes = R.drawable.ic_bookmark_filled_24;
            } else if (notification.type() == AppData.NotificationType.MENTION) {
                iconRes = R.drawable.ic_user_24;
            } else if (notification.type() == AppData.NotificationType.MODERATION
                    || notification.type() == AppData.NotificationType.MODERATION_APPEALABLE
                    || notification.type() == AppData.NotificationType.APPEAL) {
                iconRes = R.drawable.ic_shield_outline_24;
            }
            imageNotificationType.setImageResource(iconRes);
            imageNotificationType.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.ink_primary));
            itemView.setAlpha(notification.read() ? 0.72f : 1.0f);
        }
    }
}
