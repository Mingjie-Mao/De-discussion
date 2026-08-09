package com.example.myapplication;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;


import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dao.model.User;

final class ProfileInsightDialog {
    interface UserClickListener {
        void onUserClick(UUID userId);
    }

    static final class StatItem {
        private final int iconResId;
        private final int labelResId;
        private final int value;

        StatItem(@DrawableRes int iconResId, @StringRes int labelResId, int value) {
            this.iconResId = iconResId;
            this.labelResId = labelResId;
            this.value = value;
        }
    }

    private ProfileInsightDialog() {
    }

    static void showUsers(
            Context context,
            @StringRes int titleResId,
            ArrayList<User> users,
            UserClickListener listener
    ) {
        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout content = buildSheet(context, titleResId, users.size(), true, () -> {
            if (holder[0] != null) {
                holder[0].dismiss();
            }
        });

        if (users.isEmpty()) {
            content.addView(makeEmptyState(context, R.drawable.ic_user_24, R.string.profile_list_empty));
        } else {
            ScrollView scrollView = new ScrollView(context);
            scrollView.setFillViewport(false);
            scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

            LinearLayout list = new LinearLayout(context);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setPadding(0, 0, 0, dp(context, 2));
            scrollView.addView(list);

            for (User user : users) {
                View row = makeUserRow(context, user, () -> {
                    if (holder[0] != null) {
                        holder[0].dismiss();
                    }
                    listener.onUserClick(user.id());
                });
                list.addView(row);
            }

            LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.min(dp(context, 430), Math.max(dp(context, 108), users.size() * dp(context, 92)))
            );
            scrollParams.setMargins(0, dp(context, 8), 0, 0);
            content.addView(scrollView, scrollParams);
        }

        holder[0] = show(context, content);
    }

    static void showStats(Context context, @StringRes int titleResId, List<StatItem> stats) {
        AlertDialog[] holder = new AlertDialog[1];
        LinearLayout content = buildSheet(context, titleResId, stats.size(), false, () -> {
            if (holder[0] != null) {
                holder[0].dismiss();
            }
        });

        LinearLayout grid = new LinearLayout(context);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        gridParams.setMargins(0, dp(context, 10), 0, 0);
        content.addView(grid, gridParams);

        for (StatItem stat : stats) {
            grid.addView(makeStatCard(context, stat));
        }

        holder[0] = show(context, content);
    }

    /** Delegates to {@link AppSheet} so every dialog in the app shares one card style. */
    private static LinearLayout buildSheet(
            Context context,
            @StringRes int titleResId,
            int count,
            boolean showCount,
            Runnable onClose
    ) {
        return AppSheet.sheet(context, context.getString(titleResId), showCount ? count : null, onClose);
    }

    private static View makeUserRow(Context context, User user, Runnable onClick) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 14), dp(context, 12), dp(context, 12), dp(context, 12));
        row.setBackground(makeRoundRect(context, R.color.surface_alt, R.color.surface_border, 20, 1));
        row.setOnClickListener(v -> onClick.run());

        TextView avatar = new TextView(context);
        avatar.setText(AppData.getAvatarLetter(context, user.id()));
        avatar.setTextColor(ContextCompat.getColor(context, R.color.white));
        avatar.setTextSize(16);
        avatar.setTypeface(Typeface.DEFAULT_BOLD);
        avatar.setGravity(Gravity.CENTER);
        GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(AppData.getAvatarColor(context, user.id()));
        avatar.setBackground(avatarBg);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44));
        avatarParams.setMargins(0, 0, dp(context, 12), 0);
        row.addView(avatar, avatarParams);

        LinearLayout copy = new LinearLayout(context);
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        );
        row.addView(copy, copyParams);

        TextView name = new TextView(context);
        name.setText(AppData.getDisplayName(context, user.id()));
        name.setTextColor(ContextCompat.getColor(context, R.color.ink_primary));
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setIncludeFontPadding(false);
        copy.addView(name);

        TextView meta = new TextView(context);
        meta.setText(user.username());
        meta.setTextColor(ContextCompat.getColor(context, R.color.ink_secondary));
        meta.setTextSize(13);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        metaParams.setMargins(0, dp(context, 5), 0, 0);
        copy.addView(meta, metaParams);

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.ic_chevron_right_24);
        chevron.setColorFilter(ContextCompat.getColor(context, R.color.ink_tertiary));
        row.addView(chevron, new LinearLayout.LayoutParams(dp(context, 24), dp(context, 24)));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, 0, 0, dp(context, 10));
        row.setLayoutParams(rowParams);
        return row;
    }

    private static View makeStatCard(Context context, StatItem stat) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 14), dp(context, 13), dp(context, 14), dp(context, 13));
        card.setBackground(makeRoundRect(context, R.color.surface_alt, R.color.surface_border, 20, 1));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout iconWrap = new FrameLayout(context);
        iconWrap.setBackground(makeRoundRect(context, R.color.surface, R.color.surface_border, 14, 1));
        ImageView icon = new ImageView(context);
        icon.setImageResource(stat.iconResId);
        icon.setColorFilter(ContextCompat.getColor(context, R.color.ink_primary));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(context, 22), dp(context, 22));
        iconParams.gravity = Gravity.CENTER;
        iconWrap.addView(icon, iconParams);
        LinearLayout.LayoutParams iconWrapParams = new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44));
        iconWrapParams.setMargins(0, 0, dp(context, 12), 0);
        row.addView(iconWrap, iconWrapParams);

        TextView label = new TextView(context);
        label.setText(stat.labelResId);
        label.setTextColor(ContextCompat.getColor(context, R.color.ink_primary));
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView value = new TextView(context);
        value.setText(String.valueOf(stat.value));
        value.setTextColor(ContextCompat.getColor(context, R.color.ink_primary));
        value.setTextSize(26);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setIncludeFontPadding(false);
        row.addView(value);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(context, 10));
        card.setLayoutParams(cardParams);
        return card;
    }

    private static View makeEmptyState(Context context, int iconResId, int labelResId) {
        LinearLayout empty = new LinearLayout(context);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(context, 16), dp(context, 28), dp(context, 16), dp(context, 28));
        empty.setBackground(makeRoundRect(context, R.color.surface_alt, R.color.surface_border, 20, 1));

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconResId);
        icon.setColorFilter(ContextCompat.getColor(context, R.color.ink_tertiary));
        empty.addView(icon, new LinearLayout.LayoutParams(dp(context, 32), dp(context, 32)));

        TextView label = new TextView(context);
        label.setText(labelResId);
        label.setTextColor(ContextCompat.getColor(context, R.color.ink_secondary));
        label.setTextSize(15);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(0, dp(context, 10), 0, 0);
        empty.addView(label, labelParams);

        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        emptyParams.setMargins(0, dp(context, 14), 0, 0);
        empty.setLayoutParams(emptyParams);
        return empty;
    }

    private static AlertDialog show(Context context, View content) {
        return AppSheet.show(context, content);
    }

    private static GradientDrawable makeRoundRect(
            Context context,
            int fillResId,
            int strokeResId,
            int radiusDp,
            int strokeWidthDp
    ) {
        return AppSheet.roundRect(context, fillResId, strokeResId, radiusDp, strokeWidthDp);
    }

    private static int dp(Context context, int value) {
        return AppSheet.dp(context, value);
    }
}
